# 跨平台现状

| 平台 | 状态 |
| --- | --- |
| Android arm64-v8a | 已发布，`vane-1.86.3-arm64.apk` |
| Android armeabi-v7a（32 位） | 已发布，`vane-1.86.3-armv7.apk` |
| Windows / Linux | 未实现 |
| iOS | 未实现 |

## 走过哪些弯路（给接手的人）

**桌面端（Electron）**
做过两版：先 `WebContentsView`（要求 Electron 30+），再换 `BrowserView`；
Windows 上仍然在启动阶段抛 `A JavaScript error occurred in the main process`。
最后一次已知的堆栈是：

```
TypeError: t.view.webContents.navigationHistory.canGoBack is not a function
```

`navigationHistory` 这个 API 在 30 之后改过形状，即便加了 typeof 探测与旧 API 兜底，
Windows 侧仍未跑通。若重做，建议：

1. 直接用稳定老 API：`webContents.canGoBack() / goBack()`，不碰 `navigationHistory`；
2. 或者干脆不用多视图：单 `BrowserWindow` + 页面内自带地址栏（把工具栏做进注入的 HTML）；
3. CI 里固定 `electron` 精确版本，并在 Windows 真机跑一次 `npm start` 再打包。

**iOS**
WKWebView 外壳能跑，拦截规则也能用（`hosts.txt` → `WKContentRuleList`，约 7.8 万条切成 6 片）。
卡点是代理：iOS 只允许 NetworkExtension（Packet Tunnel），需要付费开发者账号与专用 entitlement，
免费账号侧载只能装浏览器部分。要重做，先确认账号类型再动手。

## 移植映射

| 能力 | Android 实现 | 桌面建议 | iOS 建议 |
| --- | --- | --- | --- |
| 渲染 | 系统 WebView | WebView2 / WebKitGTK | WKWebView |
| 代理 | 本地 HTTP 代理 + WebView 指定代理 | 系统代理 / PAC / TUN | NetworkExtension |
| 广告拦截 | `FilterEngine` + 本地规则文本 | 请求层取消（`webRequest`） | `WKContentRuleList` |
| 隐私空间 | AES-GCM + Keystore | DPAPI / libsecret | Keychain + CryptoKit |
| 界面自定义 | `UiConfig` 数据驱动 | 需重写窗口 chrome | 同左 |
| 用户脚本 | `UserScripts` + GM 桥 | `webContents` 注入 | `WKUserScript` |
