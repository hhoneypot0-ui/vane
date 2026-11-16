// Vane 桌面版：多标签浏览器 + 本地广告拦截 + 代理设置。
// 设计上尽量贴近 Android 端的取舍：一个程序干完，界面走深色，规则本地跑。
const { app, BrowserWindow, WebContentsView, ipcMain, session, Menu } = require('electron');
const path = require('path');
const fs = require('fs');
const os = require('os');

// 资源由 scripts/sync-assets.js 从 Android 端同步到 desktop/assets
const ASSETS = path.join(__dirname, 'assets');
const HOSTS = path.join(ASSETS, 'hosts.txt');
const HOME = 'vane://home';
const TOOLBAR_H = 56;
const CONFIG = path.join(app.getPath('userData'), 'config.json');

let win = null;
let toolbar = null;
let tabs = [];          // [{ view, title, url }]
let active = 0;
let blocklist = new Set();

// ---------- 规则 ----------
function loadRules() {
  try {
    const text = fs.readFileSync(HOSTS, 'utf8');
    for (const line of text.split('\n')) {
      const s = line.trim();
      if (!s || s.startsWith('#')) continue;
      const parts = s.split(/\s+/);
      const host = (parts.length > 1 ? parts[1] : parts[0]).toLowerCase().replace(/\.$/, '');
      if (host && host.includes('.') && !/^[0-9.]+$/.test(host)) blocklist.add(host);
    }
  } catch (e) { console.warn('[vane] 规则读取失败：', e.message); }
  console.log(`[vane] 规则 ${blocklist.size} 条`);
}

function isBlocked(url) {
  let host;
  try { host = new URL(url).hostname.toLowerCase(); } catch { return false; }
  if (blocklist.has(host)) return true;
  for (let i = host.indexOf('.'); i !== -1; i = host.indexOf('.', i + 1)) {
    if (blocklist.has(host.slice(i + 1))) return true;
  }
  return false;
}

// ---------- 配置（代理） ----------
function readConfig() {
  try { return JSON.parse(fs.readFileSync(CONFIG, 'utf8')); } catch { return {}; }
}
function writeConfig(cfg) {
  try { fs.mkdirSync(path.dirname(CONFIG), { recursive: true }); fs.writeFileSync(CONFIG, JSON.stringify(cfg, null, 2)); } catch {}
}
async function applyProxy(rules) {
  const cfg = readConfig();
  cfg.proxy = rules || '';
  writeConfig(cfg);
  try {
    if (!rules) await session.defaultSession.setProxy({ mode: 'direct' });
    else await session.defaultSession.setProxy({ proxyRules: rules, proxyBypassRules: '<local>' });
    console.log('[vane] 代理：', rules || '直连');
  } catch (e) { console.warn('[vane] 代理设置失败：', e.message); }
}

// ---------- 标签 ----------
function layout() {
  if (!win || !toolbar) return;
  const { width, height } = win.getContentBounds();
  toolbar.setBounds({ x: 0, y: 0, width, height: TOOLBAR_H });
  const t = tabs[active];
  if (t) t.view.setBounds({ x: 0, y: TOOLBAR_H, width, height: Math.max(0, height - TOOLBAR_H) });
}

function pushState() {
  if (!toolbar) return;
  const t = tabs[active];
  toolbar.webContents.send('vane:state', {
    url: t ? t.url : '',
    title: t ? t.title : 'Vane',
    canBack: t ? t.view.webContents.navigationHistory.canGoBack() : false,
    canForward: t ? t.view.webContents.navigationHistory.canGoForward() : false,
    tabs: tabs.map((x, i) => ({ title: x.title || '新标签页', active: i === active })),
    rules: blocklist.size
  });
}

function normalize(input) {
  const s = (input || '').trim();
  if (!s) return HOME;
  if (/^https?:\/\//i.test(s)) return s;
  if (s.includes('.') && !s.includes(' ')) return 'https://' + s;
  return 'https://www.bing.com/search?q=' + encodeURIComponent(s);
}

function newTab(url) {
  const view = new WebContentsView({
    webPreferences: { contextIsolation: true, nodeIntegration: false, partition: 'persist:vane' }
  });
  win.contentView.addChildView(view);
  const wc = view.webContents;
  const tab = { view, title: '新标签页', url: url || HOME };
  tabs.push(tab);
  active = tabs.length - 1;

  wc.on('page-title-updated', (_e, title) => { tab.title = title; pushState(); });
  wc.on('did-navigate', (_e, u) => { tab.url = u; pushState(); });
  wc.on('did-navigate-in-page', (_e, u) => { tab.url = u; pushState(); });
  wc.setWindowOpenHandler(({ url }) => { newTab(url); return { action: 'deny' }; });

  if (url === HOME || !url) wc.loadFile(path.join(ASSETS, 'home.html'));
  else wc.loadURL(normalize(url));

  layout();
  pushState();
  return tab;
}

function closeTab() {
  if (tabs.length <= 1) return;
  const t = tabs[active];
  win.contentView.removeChildView(t.view);
  t.view.webContents.close();
  tabs.splice(active, 1);
  active = Math.max(0, active - 1);
  layout();
  pushState();
}

// ---------- 窗口 ----------
function createWindow() {
  win = new BrowserWindow({
    width: 1280, height: 820, minWidth: 720, minHeight: 460,
    backgroundColor: '#101114', title: 'Vane', show: false,
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true }
  });
  toolbar = new WebContentsView({
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true }
  });
  win.contentView.addChildView(toolbar);
  toolbar.webContents.loadFile(path.join(__dirname, 'toolbar.html'));

  newTab(HOME);
  win.on('resize', layout);
  win.once('ready-to-show', () => win.show());
  win.on('closed', () => { win = null; });
}

// ---------- IPC ----------
ipcMain.on('vane:navigate', (_e, url) => {
  const t = tabs[active]; if (!t) return;
  const target = normalize(url);
  if (target === HOME) t.view.webContents.loadFile(path.join(ASSETS, 'home.html'));
  else t.view.webContents.loadURL(target);
});
ipcMain.on('vane:back', () => { const t = tabs[active]; if (t && t.view.webContents.navigationHistory.canGoBack()) t.view.webContents.navigationHistory.goBack(); });
ipcMain.on('vane:forward', () => { const t = tabs[active]; if (t && t.view.webContents.navigationHistory.canGoForward()) t.view.webContents.navigationHistory.goForward(); });
ipcMain.on('vane:reload', () => { const t = tabs[active]; if (t) t.view.webContents.reload(); });
ipcMain.on('vane:home', () => { const t = tabs[active]; if (t) t.view.webContents.loadFile(path.join(ASSETS, 'home.html')); });
ipcMain.on('vane:new-tab', () => newTab(HOME));
ipcMain.on('vane:close-tab', closeTab);
ipcMain.on('vane:set-active', (_e, i) => { if (i >= 0 && i < tabs.length) { active = i; layout(); pushState(); } });
ipcMain.on('vane:set-proxy', (_e, rules) => applyProxy(rules));
ipcMain.handle('vane:rules', () => blocklist.size);

// ---------- 启动 ----------
app.whenReady().then(async () => {
  loadRules();
  session.defaultSession.webRequest.onBeforeRequest((details, cb) => {
    if (isBlocked(details.url)) return cb({ cancel: true });
    cb({});
  });
  const cfg = readConfig();
  if (cfg.proxy) await applyProxy(cfg.proxy);
  Menu.setApplicationMenu(Menu.buildFromTemplate([
    { label: '文件', submenu: [{ role: 'quit', label: '退出' }] },
    { label: '视图', submenu: [{ role: 'reload' }, { role: 'toggleDevTools' }, { role: 'togglefullscreen' }] }
  ]));
  createWindow();
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});

app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
