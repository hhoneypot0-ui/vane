# Vane iOS（骨架）

Swift + WKWebView 的最小外壳：地址栏、主页、长按链接回调占位。

```bash
brew install xcodegen      # 或者直接用 Xcode 新建工程把 Vane/ 拖进去
cd ios && xcodegen generate
open Vane.xcodeproj        # 选自己的开发者证书，连真机运行
```

## 现在有什么

- 一个 WKWebView + 地址栏（回车导航；没有 scheme 的按 Bing 搜索）
- 载入 Android 端同一个 `home.html`（通过 bundle 引用 `assets/`）
- 长按链接通过 `WKScriptMessageHandler` 回传给原生（目前只记录）

## 还没有

- 代理：iOS 上必须走 NetworkExtension（Packet Tunnel Provider），需要付费开发者账号、专用 entitlement 与审核说明；现有骨架没有包含
- 广告拦截：可以用 `WKContentRuleList` 把 `hosts.txt` 编译成规则（未接）
- 隐私空间（Keychain + AES-GCM）、用户脚本、WebDAV 同步、AI 面板

移植映射与工作量见 [`../docs/PLATFORMS.md`](../docs/PLATFORMS.md)。
