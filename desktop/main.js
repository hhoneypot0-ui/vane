// Vane 桌面版：多标签浏览器 + 本地广告拦截 + 代理设置。
// 设计上尽量贴近 Android 端的取舍：一个程序干完，界面走深色，规则本地跑。
const { app, BrowserWindow, BrowserView, ipcMain, session, Menu, dialog } = require('electron');
const path = require('path');
const fs = require('fs');
const os = require('os');

// 资源由 scripts/sync-assets.js 从 Android 端同步到 desktop/assets
const ASSETS = path.join(__dirname, 'assets');
const HOSTS = path.join(ASSETS, 'hosts.txt');
const HOME = 'vane://home';
const TOOLBAR_H = 56;
const CONFIG = path.join(app.getPath('userData'), 'config.json');
const LOG = path.join(app.getPath('userData'), 'vane-error.log');

function logError(where, err) {
  const line = `[${new Date().toISOString()}] ${where}: ${err && err.stack ? err.stack : err}\n`;
  try { fs.appendFileSync(LOG, line); } catch {}
  console.error(line);
}

process.on('uncaughtException', (e) => {
  logError('uncaughtException', e);
  try {
    dialog.showErrorBox('Vane 出错了', `${e && e.message ? e.message : e}\n\n日志：${LOG}`);
  } catch {}
});
process.on('unhandledRejection', (e) => logError('unhandledRejection', e));

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
  win.setTopBrowserView(toolbar);
}

function pushState() {
  if (!toolbar) return;
  try { pushStateInner(); } catch (e) { logError('pushState', e); }
}

function pushStateInner() {
  const t = tabs[active];
  toolbar.webContents.send('vane:state', {
    version: app.getVersion(),
    url: t ? t.url : '',
    title: t ? t.title : 'Vane',
    canBack: canGo(t, 'back'),
    canForward: canGo(t, 'forward'),
    tabs: tabs.map((x, i) => ({ title: x.title || '新标签页', active: i === active })),
    rules: blocklist.size
  });
}

// Electron 不同版本这块 API 变过三次：navigationHistory 有时没有、有时方法名不同，
// 所以一律先看 typeof，再退回 webContents 上的老方法，最后返回 false。
function wcOf(t) {
  try { return t && t.view && t.view.webContents ? t.view.webContents : null; } catch { return null; }
}
function canGo(t, dir) {
  const wc = wcOf(t);
  if (!wc) return false;
  const h = wc.navigationHistory;
  try {
    if (h) {
      const f = dir === 'back' ? h.canGoBack : h.canGoForward;
      if (typeof f === 'function') return !!f.call(h);
    }
    const legacy = dir === 'back' ? wc.canGoBack : wc.canGoForward;
    if (typeof legacy === 'function') return !!legacy.call(wc);
  } catch (e) { logError('canGo/' + dir, e); }
  return false;
}
function go(t, dir) {
  const wc = wcOf(t);
  if (!wc) return;
  const h = wc.navigationHistory;
  try {
    if (h) {
      const f = dir === 'back' ? h.goBack : h.goForward;
      if (typeof f === 'function') { f.call(h); return; }
    }
    const legacy = dir === 'back' ? wc.goBack : wc.goForward;
    if (typeof legacy === 'function') { legacy.call(wc); return; }
  } catch (e) { logError('go/' + dir, e); }
}

function normalize(input) {
  const s = (input || '').trim();
  if (!s) return HOME;
  if (/^https?:\/\//i.test(s)) return s;
  if (s.includes('.') && !s.includes(' ')) return 'https://' + s;
  return 'https://www.bing.com/search?q=' + encodeURIComponent(s);
}

function newTab(url) {
  const view = new BrowserView({
    webPreferences: { contextIsolation: true, nodeIntegration: false, partition: 'persist:vane' }
  });
  win.addBrowserView(view);
  const wc = view.webContents;
  const tab = { view, title: '新标签页', url: url || HOME };
  tabs.push(tab);
  active = tabs.length - 1;

  wc.on('page-title-updated', (_e, title) => { tab.title = title; pushState(); });
  wc.on('did-navigate', (_e, u) => { tab.url = u; pushState(); });
  wc.on('did-navigate-in-page', (_e, u) => { tab.url = u; pushState(); });
  wc.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('vane:')) { handleVaneUrl(wc, url); return { action: 'deny' }; }
    newTab(url);
    return { action: 'deny' };
  });
  wc.on('will-navigate', (e, url) => {
    if (url.startsWith('vane:')) { e.preventDefault(); handleVaneUrl(wc, url); }
  });
  wc.on('will-frame-navigate', (e) => {
    const url = e.url || '';
    if (url.startsWith('vane:')) { e.preventDefault(); handleVaneUrl(wc, url); }
  });

  if (url === HOME || !url) loadHome(wc);
  else wc.loadURL(normalize(url));

  layout();
  pushState();
  return tab;
}

/** 打开内置主页，并补上 Android 端由原生注入的那几个接口。 */
function tabOf(wc) {
  return tabs.find((t) => t.view.webContents === wc) || null;
}

function loadHome(wc) {
  wc.loadFile(path.join(ASSETS, 'home.html')).then(() => {
    wc.executeJavaScript(
      "window.setDark && window.setDark(true);" +
      "window.setEngine && window.setEngine('Bing', '');" +
      "window.setCards && window.setCards([]);", true).catch(() => {});
  }).catch(() => {});
}

/** 主页上的 vane:// 链接（搜索、卡片、动作）在桌面端要自己接。 */
function handleVaneUrl(wc, raw) {
  let u;
  try { u = new URL(raw); } catch { return false; }
  if (u.protocol !== 'vane:') return false;
  const host = u.hostname || u.pathname.replace(/^\/+/, '');
  const q = (k) => u.searchParams.get(k) || '';
  if (host === 'open') {
    const target = q('url');
    if (target) wc.loadURL(target);
    return true;
  }
  if (host === 'search') {
    wc.loadURL('https://www.bing.com/search?q=' + encodeURIComponent(q('q')));
    return true;
  }
  if (host === 'action') {
    const id = (u.pathname || '').replace(/^\/+/, '') || q('id');
    switch (id) {
      case 'focusAddress': toolbar.webContents.focus(); toolbar.webContents.executeJavaScript("document.getElementById('addr').focus()"); break;
      case 'home': loadHome(wc); break;
      case 'reload': wc.reload(); break;
      case 'back': if (canGo(tabOf(wc), 'back')) go(tabOf(wc), 'back'); break;
      case 'forward': if (canGo(tabOf(wc), 'forward')) go(tabOf(wc), 'forward'); break;
      case 'newTab': newTab(HOME); break;
      case 'closeTab': closeTab(); break;
      default: console.log('[vane] 桌面端暂未实现动作：', id);
    }
    return true;
  }
  return true;   // 其它 vane:// 一律吞掉，避免跳成错误页
}

function closeTab() {
  if (tabs.length <= 1) return;
  const t = tabs[active];
  win.removeBrowserView(t.view);
  t.view.webContents.destroy();
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
  toolbar = new BrowserView({
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true }
  });
  win.addBrowserView(toolbar);
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
  if (target === HOME) loadHome(t.view.webContents);
  else t.view.webContents.loadURL(target);
});
ipcMain.on('vane:back', () => { const t = tabs[active]; if (canGo(t, 'back')) go(t, 'back'); });
ipcMain.on('vane:forward', () => { const t = tabs[active]; if (canGo(t, 'forward')) go(t, 'forward'); });
ipcMain.on('vane:reload', () => { const t = tabs[active]; if (t) t.view.webContents.reload(); });
ipcMain.on('vane:home', () => { const t = tabs[active]; if (t) loadHome(t.view.webContents); });
ipcMain.on('vane:new-tab', () => newTab(HOME));
ipcMain.on('vane:close-tab', closeTab);
ipcMain.on('vane:set-active', (_e, i) => { if (i >= 0 && i < tabs.length) { active = i; layout(); pushState(); } });
ipcMain.on('vane:set-proxy', (_e, rules) => applyProxy(rules));
ipcMain.handle('vane:rules', () => blocklist.size);

// ---------- 启动 ----------
app.whenReady().then(async () => {
  try { fs.appendFileSync(LOG, `[${new Date().toISOString()}] start v${app.getVersion()} ${process.versions.electron}\n`); } catch {}
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
  try {
    createWindow();
  } catch (e) {
    logError('createWindow', e);
    dialog.showErrorBox('Vane 启动失败', `${e && e.message}\n\n日志：${LOG}`);
    throw e;
  }
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
}).catch((e) => logError('whenReady', e));

app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
