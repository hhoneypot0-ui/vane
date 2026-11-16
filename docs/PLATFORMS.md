# 各平台现状与移植映射

| 平台 | 状态 | 产物 |
| --- | --- | --- |
| Android arm64-v8a | 已发布 | `vane-1.86.3-arm64.apk`（31.9 MB） |
| Android armeabi-v7a（32 位） | 已发布 | `vane-1.86.3-armv7.apk`（32.5 MB） |
| Windows / Linux | 原型 | `desktop/`（Electron 外壳 + 同一份规则库） |
| iOS | 骨架 | `ios/`（Swift + WKWebView，XcodeGen 工程描述） |

## 为什么不是「同一份代码编四次」

Android 端几乎所有能力都长在三个系统件上，换平台得各自重写：

| 能力 | Android 实现 | Windows / Linux | iOS |
| --- | --- | --- | --- |
| 网页渲染 | 系统 WebView | WebView2 / WebKitGTK（Electron 用 Chromium） | WKWebView |
| 代理 | 本地 HTTP 代理进程 + WebView 指定代理 | 系统代理 / PAC / TUN（需自建） | NetworkExtension（付费账号 + entitlement） |
| 广告拦截 | `FilterEngine` + 本地规则文本 | Electron `webRequest` 取消请求（原型已接） | `WKContentRuleList`（规则需先编译成 JSON） |
| 隐私空间 | AES-GCM + Android Keystore | DPAPI / libsecret | Keychain + CryptoKit |
| 界面自定义 | `UiConfig` 的数据驱动壳 | 需重做一套窗口 chrome | 同左 |
| 用户脚本 | `UserScripts` + GM 桥 | 可注入到 `webContents` | `WKUserScript` + message handler |

## 现状说明

- **32 位（armv7）**：`app-armeabi-v7a-debug.apk` 由同一次构建产出，代理核心有对应 ABI 的二进制，已随 release 发布。
- **Windows / Linux**：`desktop/` 是能跑的最小原型（窗口 + 本地规则拦截），但地址栏、标签、代理、隐私空间都还没做。
- **iOS**：`ios/` 是能在 Xcode 里打开的骨架（WKWebView + 地址栏 + 主页），代理受系统限制必须走 NetworkExtension，不在骨架范围内。

三端的资源（`home.html`、广告规则、播放器页）是共享的，重做的部分是「壳」和「系统能力对接」。
