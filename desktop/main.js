// Vane 桌面原型：把 Android 端的主页与广告规则搬过来跑在 Electron 里。
// 说明：桌面版目前只做了「能开网页 + 本地规则拦截 + 极简工具条」，
// 代理、隐私空间、用户脚本、同步等仍是 Android 端能力，见 docs/PLATFORMS.md。
const { app, BrowserWindow, session, ipcMain } = require('electron');
const path = require('path');
const fs = require('fs');

const RULES_HOSTS = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'hosts.txt');
const HOME = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'home.html');

function loadBlockList() {
  const set = new Set();
  try {
    const txt = fs.readFileSync(RULES_HOSTS, 'utf8');
    for (const line of txt.split('\n')) {
      const s = line.trim();
      if (!s || s.startsWith('#')) continue;
      // hosts 格式：0.0.0.0 example.com
      const parts = s.split(/\s+/);
      const host = parts.length > 1 ? parts[1] : parts[0];
      if (host && host.includes('.') && host !== '0.0.0.0') set.add(host.toLowerCase());
    }
  } catch (e) {
    console.warn('[vane] 规则读取失败：', e.message);
  }
  return set;
}

const BLOCK = loadBlockList();
console.log(`[vane] 已载入 ${BLOCK.size} 条域名规则`);

function hostOf(url) {
  try { return new URL(url).hostname.toLowerCase(); } catch (e) { return ''; }
}

function blocked(url) {
  const h = hostOf(url);
  if (!h) return false;
  if (BLOCK.has(h)) return true;
  for (let p = h.indexOf('.'); p !== -1; p = h.indexOf('.', p + 1)) {
    if (BLOCK.has(h.slice(p + 1))) return true;
  }
  return false;
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1200, height: 800, minWidth: 720, minHeight: 480,
    title: 'Vane',
    backgroundColor: '#101114',
    webPreferences: { contextIsolation: true, nodeIntegration: false }
  });
  win.loadFile(HOME);
  win.webContents.setWindowOpenHandler(({ url }) => {
    win.loadURL(url);
    return { action: 'deny' };
  });
  return win;
}

app.whenReady().then(() => {
  session.defaultSession.webRequest.onBeforeRequest((details, cb) => {
    if (blocked(details.url)) return cb({ cancel: true });
    cb({});
  });
  const win = createWindow();
  ipcMain.on('vane:navigate', (_e, url) => { if (url) win.loadURL(url); });
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
