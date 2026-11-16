# Vane 桌面版（Windows / Linux）

Electron 实现：多标签、地址栏、后退前进、本地广告拦截、代理设置。
复用 Android 端的 `home.html` 与 `assets/hosts.txt`（约 7.8 万条规则）。

## 跑起来

```bash
cd desktop
npm install
npm start          # Windows / macOS / Linux 都可以
```

## 打 Windows 安装包

```bash
cd desktop
npm install
npm run dist:win   # 产物在 desktop/dist/：NSIS 安装包 + 便携版 exe
```

没有 Windows 机器也行：仓库里的 `.github/workflows/desktop.yml` 会在 GitHub Actions 的
windows-latest 上打包，跑一次 workflow 就能在 Artifacts 里下到 `.exe`。

## 现在有什么

- 多标签（新建 / 切换 / 关闭，`Ctrl+T` / `Ctrl+W`）
- 地址栏：输入网址或直接搜索（默认 Bing），`Ctrl+L` 聚焦，`Alt+←/→` 前进后退，`F5` 刷新
- 本地广告拦截：`hosts.txt` 里 7.8 万条域名，命中直接取消请求，工具条显示规则数
- 代理设置：工具条 ⚙ 填 `socks5://127.0.0.1:1080` 或 `http://127.0.0.1:7890`，留空即直连；
  写进用户配置，下次启动自动生效
- 外链在当前窗口新开标签；深色界面，`home.html` 直接复用

## 还没有

- 订阅导入与分流规则（只支持单一代理地址，没有「国内直连 / 境外走代理」的判断）
- 隐私空间、用户脚本、WebDAV 同步、AI 面板、长截图
- 自动更新

移植映射与各平台差异见 [`../docs/PLATFORMS.md`](../docs/PLATFORMS.md)。
