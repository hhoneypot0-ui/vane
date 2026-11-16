const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('vane', {
  navigate: (url) => ipcRenderer.send('vane:navigate', url),
  back: () => ipcRenderer.send('vane:back'),
  forward: () => ipcRenderer.send('vane:forward'),
  reload: () => ipcRenderer.send('vane:reload'),
  home: () => ipcRenderer.send('vane:home'),
  newTab: () => ipcRenderer.send('vane:new-tab'),
  closeTab: () => ipcRenderer.send('vane:close-tab'),
  setActive: (i) => ipcRenderer.send('vane:set-active', i),
  setProxy: (rules) => ipcRenderer.send('vane:set-proxy', rules),
  rules: () => ipcRenderer.invoke('vane:rules'),
  onState: (cb) => ipcRenderer.on('vane:state', (_e, s) => cb(s))
});
