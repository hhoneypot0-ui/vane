# Vane iOS

Swift + WKWebView。分两块：**浏览器**（可直接跑）与**代理**（需付费开发者账号）。

```bash
brew install xcodegen
cd ios && xcodegen generate
open Vane.xcodeproj      # 选自己的证书，连真机运行
```

## 能跑的：浏览器 + 本地拦截

- `BrowserViewController.swift` — WKWebView、地址栏（回车导航；无 scheme 按 Bing 搜）、深色底
- `RulesLoader.swift` — 启动时把 `Vane/Rules/rules-*.json` 编译成 `WKContentRuleList` 并挂上去
- `Vane/Rules/` — 由 `tools/ios/gen_rules.py` 从 Android 端 `hosts.txt` 生成：
  **78,307 个域名 → 6 个分片**（每片 15,000 条，约 3 MB/片，合计 16 MB）
- 主页复用 Android 端的 `home.html`（通过 `assets/` 引入）

重新生成规则：

```bash
python3 tools/ios/gen_rules.py     # 输出 ios/Vane/Rules/*.json
```

## 要账号的：代理

iOS 不给 App 设「应用内代理」的权限，只能用 NetworkExtension（Packet Tunnel）：

- `VaneProxy/PacketTunnelProvider.swift` — 骨架：建 TUN、下发路由与 DNS，`TODO` 处接真实代理
- `VaneProxy/VaneProxy.entitlements` — `packet-tunnel-provider` + App Group
- 需要的条件：**付费开发者账号**、在 Apple 后台给 App ID 勾选 Network Extensions、
  扩展里启动本地代理并把流量导向它（代理核心需编译成 iOS 可用的静态库/framework）
- 免费账号（7 天重签 / 侧载）**不含**这个 entitlement，能装浏览器部分，代理起不来

## 还没有

隐私空间（Keychain + CryptoKit AES-GCM）、用户脚本 GM 桥、WebDAV 同步、AI 面板。
移植映射见 [`../docs/PLATFORMS.md`](../docs/PLATFORMS.md)。
