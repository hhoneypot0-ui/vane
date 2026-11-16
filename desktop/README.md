# Vane 桌面版（原型）

Electron 外壳，复用 Android 端的 `home.html` 与 `assets/hosts.txt` 规则库。

```bash
cd desktop
npm install          # 需要联网拉 electron
npm start
```

## 现在有什么

- 一个窗口开网页，外链在当前窗口打开
- 载入 `hosts.txt`（约 7.8 万条）做域名拦截，`onBeforeRequest` 直接 cancel
- 深色底、最小尺寸限制

## 还没有

- 地址栏 / 前进后退 / 标签（Electron 里要重做一套 chrome）
- 代理与分流（桌面端要走系统代理或 PAC，或用 TUN，需另写一层）
- 隐私空间、用户脚本、WebDAV 同步、AI 面板

路线与移植映射见 [`docs/PLATFORMS.md`](../docs/PLATFORMS.md)。
