# Vane · 极简浏览器

一个用起来很安静的浏览器：无广告、无统计、不后台唤醒。
界面按 Via 那套极简思路重做，但每个按键、手势、菜单项都是数据，能自己改。

日常该有的都有：多标签、书签/历史、下载（支持断点和分片）、离线页面、长截图、
阅读模式、翻译、广告拦截、隐私空间（视频加密存放、30 秒回锁）、用户脚本、云同步（WebDAV）。

代理是一部分，不是全部：填机场订阅或手贴节点，常见协议开箱能连；
需要的话可以把整机流量也接进分流，规则按域名/IP 走。

- 安装包按 ABI 拆分，两个都只有 31–32 MB：
  - `app-arm64-v8a-debug.apk` —— 现在绝大多数手机
  - `app-armeabi-v7a-debug.apk` —— 老 32 位机型
- 最新已拷到手机：`/sdcard/Download/DSH-Go/vane-1.86.0-arm64.apk`


### 仓库里没有的东西

`app/src/main/jniLibs/*/libsingbox.so`（79 MB + 74 MB）和测试素材没进仓库。
要自己编译出能连代理的包，把 sing-box 的 Android 产物按 ABI 放到：

```
app/src/main/jniLibs/arm64-v8a/libsingbox.so
app/src/main/jniLibs/armeabi-v7a/libsingbox.so
```

纯浏览器功能（标签、下载、隐私空间、脚本、同步等）不依赖它。

---

## 一、代理与分流（这版的重点）

### 能力矩阵

| 节点类型 | 谁来跑 | 说明 |
|---|---|---|
| `http` / `socks5` | 内置纯 Java 分流器 | 零依赖，秒开 |
| `ss` `vmess` `vless` `trojan` `hysteria2` `tuic` | 内置 sing-box 1.14.4 | 随 APK 分发，装完即用 |
| 想让别的 App 也走分流 | 可选 VPN 服务 | 放 `libhev-socks5-tunnel.so` 到 jniLibs 即启用 |

### 协议参数支持（机场常见写法都能直接粘）

| 参数 | 适用协议 | 说明 |
|---|---|---|
| `sni` / `peer` / `servername` | 全部 TLS 类 | 握手域名 |
| `alpn` | 全部 TLS 类 | 如 `h3`、`h2,http/1.1` |
| `fp` / `fingerprint` | vless reality / vmess / trojan | uTLS 指纹，reality 默认 `chrome` |
| `pbk` / `sid` | vless | REALITY 公钥与 shortId |
| `flow=xtls-rprx-vision` | vless | 原样透传 |
| `security=reality` | vless | 自动生成 `tls.reality` + `utls` |
| `obfs` / `obfs-password` | hysteria2 | salamander 混淆 |
| `up` / `down` | hysteria2 | 带宽（Mbps） |
| `mport` / `ports` | hysteria2 | 端口跳跃，如 `443,8443` 或 `443:8443` |
| `congestion_control` / `udp_relay_mode` | tuic | 如 `bbr` / `native` |
| `allowInsecure` / `insecure` / `skip-cert-verify` | 全部 TLS 类 | 跳过证书校验 |
| `type` / `path` / `host` / `serviceName` | vmess / vless / trojan | ws / grpc / h2 传输 |

`tuic://uuid:password@host:port?...` 与 `hysteria2://password@host:port?...` 都支持。

### 分流逻辑
1. 用户自定义「强制代理 / 强制直连」优先；
2. 国内域名后缀（`assets/geosite_cn.txt`，含 `.cn` 与 300+ 常见站）→ 直连；
3. 国内 IPv4 段（`assets/geoip_cn.txt`）→ 直连；
4. 私有地址（192.168/10.x 等）→ 直连；
5. 其余 → 走节点。

WebView 流量通过 `ProxyController` 指到 `127.0.0.1`，本地分流器/内核按 host 决策，所以「国内直连、境外走节点」是按域名/IP 真分流，不是全局开关。

### 内核是怎么塞进 App 的
Android 10+ 禁止 App 从私有目录 `exec` 二进制。做法：
- 内核以 `libsingbox.so` 的形式放进 `jniLibs/arm64-v8a/`，安装时被系统解压到 `nativeLibraryDir`（可执行、不受 W^X 限制）；
- `AndroidManifest` 开 `android:extractNativeLibs="true"`，`build.gradle` 开 `jniLibs.useLegacyPackaging = true`；
- `ProxyCore` 优先用 `nativeLibraryDir/libsingbox.so`，启动参数 `run -c config.json -D <私有目录>`，并轮询 2081 端口确认就绪。

### 配置生成的三个坑（都已踩过并修掉）
1. **DNS 不能 `detour: direct`**：1.14 会报 `detour to an empty direct outbound makes no sense`。改为 `route.rules` 里加 `{"protocol":"dns","outbound":"direct"}`，DNS 才真正直连，节点域名解析不再回环。
2. **DNS 端口不能写在 server 里**：`"server":"127.0.0.1:5353"` 会被当成域名，必须 `server` + `server_port`。
3. **tcp 节点不能带 `transport`**：给 `net=tcp` 的 vmess 硬塞 `{"type":"http"}` 会让服务端直接判 `bad request`，所有 tcp 节点全废。

---

## 二、浏览器本体

- WebView 全功能封装：多标签、`target=_blank` 自动开新页、文件上传、网页权限询问、全屏视频、系统下载器；地址栏自动识别网址/搜索词。
- 菜单是**一屏底部面板**（8 个格子），不再一层套一层；长按「全部功能」可在精简/完整之间切换。
- 设置默认只显示 5 组常用项（搜索 / 外观 / 代理 / 隐私 / 高级），要点「显示全部设置」才展开栏高、圆角、UA、规则这些细节。
- 首页只有：logo、搜索框、四个入口（书签/历史/代理/设置）。
- 后退/前进不可用时自动置灰；底栏与页面之间有分隔线。
- 地址栏左侧有状态标识：`⌂` 首页 / `🔒` HTTPS / `⚠` HTTP 明文。点它打开**站点信息**：这个站走直连还是代理、用哪个引擎、UA 模式、广告规则状态、内核版本，还能一键复制域名、切 UA、加白名单。
- 夜间模式、阅读模式、桌面版 UA、页内查找、缩放、网页截图、书签/历史。

### 搜索引擎（内置 50 个，可无限加）
国际：Google（含港/日）、Bing 国际、Yahoo、Yahoo! JAPAN、Yandex、DuckDuckGo、Ecosia、Startpage、Brave、Qwant、Mojeek、Marginalia、Yep、Presearch、SearXNG（两个实例）、Naver、Daum、Seznam、goo、Wikipedia。
国内：百度（桌面/移动）、搜狗、360、神马、夸克、头条搜索、必应国内。
垂直：知乎、微博、B 站、豆瓣、淘宝、京东、优酷、爱奇艺、喜马拉雅、有道翻译、知网、百度学术、Google 学术、GitHub、Stack Overflow、MDN、npm、PyPI、arXiv。
默认百度（国内开箱可用），长按地址栏切换，也可自定义模板。

### 广告拦截
- 约 **78,000 条**域名规则（自建精选 + Peter Lowe + StevenBlack），本地匹配子域名，不联网、不上报。
- 7.8 万条解析量较大，改成**后台线程加载**，加载完成前放行请求，不卡启动。
- 用户规则：`files/rules/hosts.txt`；白名单在设置里。

### 自定义（任何按键、任何手势）
- 57 个可绑定动作，编辑器里可上移下移、改点按/长按动作、改字符、改宽度、显隐、删除、新增。
- 手势：边缘滑动、工具栏长按、地址栏长按、长按网页链接 —— 都能改成任意动作。
- 主题：跟随系统/浅色/深色/纯黑，强调色任意十六进制，栏高、圆角、显隐可调。

### 订阅与节点
- 粘贴机场订阅链接即解析（自动 base64）：`ss / vmess / vless / trojan / hysteria2 / socks / http`。
- 手动粘贴单条节点；节点可单选、可刷新、可删除。
- **新增：一键测速**——对所有节点做 TCP 握手测延迟，连不上的直接标「连不上」。
- 分流模式：规则 / 全局 / 直连；日志区能看到每条请求走了直连还是节点。

---

## 三、播放器

- 注入嗅探脚本（hook `fetch` / `XHR` / `performance` / `<video>` / MutationObserver），抓 `m3u8 / mp4 / flv / mkv / mpd / ts`。
- 内置播放器 = WebView + hls.js（本地打包，不联网拉 CDN）+ 原生 `<video>`。
- 播放请求自动带当前页的 **Referer / UA / Cookie**，透传 `Range` —— 绝大多数「网页能看、复制链接打不开」的防盗链站点直接能播。
- 功能：倍速、**长按 2 倍速**、双击左右各跳 10 秒、**画中画**、**投屏/外部播放器**、**记忆播放进度**、全屏。
- DRM（Widevine）加密流不在范围内。

---

## 四、自测（不用装手机就能跑）

```bash
./tools/selftest/run.sh       # 分流代理 16 项
./tools/parsertest/run.sh     # 订阅解析 18 项（含 reality / hysteria2 / tuic）
SING_BOX_BIN=/path/to/sing-box ./tools/kernel-test/run.sh   # 内核 12 项
```

- **selftest**：起真实 HTTP 服务器 + SOCKS5 服务器 + HTTP 代理服务器，验证规则判断、直连转发、CONNECT 隧道、SOCKS5 上游、HTTP 上游（含认证头）、CONNECT 走上游隧道。
- **parsertest**：`ss / ss(整体 base64) / trojan / vless / socks` 字段解析，脏数据不崩。
- **kernel-test**：先跑「订阅文本 → SubscriptionParser → CoreConfig → sing-box check」整条链（6 种节点）；再校验三种分流模式 + 每种节点的配置；最后起服务端/客户端跑真实流量：**vmess / trojan+TLS / shadowsocks / hysteria2(QUIC) / tuic(QUIC)**，并校验「流量确实穿过了节点」。
  - 需要 sing-box 1.14+ 的 linux/arm64 或 amd64 二进制，官方发布包里带。

最近一次结果：**16 / 18 / 12 全绿**。测出来并修掉的问题：tcp 节点多写 `transport`（vmess 全废）、DNS detour 空 direct（启动失败）、DNS 端口写法、代理分支重复写请求头、ss 节点被误标 TLS、测试脚本残留进程导致端口串台。

---

## 五、目录

```
app/src/main/java/com/vane/browser/
├── MainActivity.java          主界面：栏、标签、菜单、手势、全流程调度
├── SettingsActivity.java      设置
├── UiEditorActivity.java      按键与手势编辑器
├── SubsActivity.java          订阅、节点、测速、分流、日志
├── PlayerActivity.java        播放器（Referer/UA/Cookie 取流、PiP、投屏）
├── core/  BrowserTab · SearchEngines · Actions · AdBlocker · VideoSniffer · JsBridge · Downloads
├── proxy/ ProxyManager · RuleEngine · SplitProxyServer · SubscriptionParser
│          CoreConfig · ProxyCore · WebViewProxyBridge · AuroraVpnService · TunBridge
├── settings/ Prefs · UiConfig
└── ui/ Theme · Widgets

app/src/main/assets/  home.html · player.html · hls.min.js · hosts.txt · geosite_cn.txt · geoip_cn.txt
app/src/main/jniLibs/arm64-v8a/libsingbox.so     ← 内核本体（82 MB，压缩进 APK）
tools/                selftest · parsertest · kernel-test
docs/                 ARCHITECTURE.md · EXTENDING.md
```

## 六、构建

```bash
./build.sh debug
```
环境：JDK 17、Gradle 8.7、Android SDK 34。当前机器 `build-tools` 的 `aapt2` 是 x86_64，跑不起来，所以用 Debian 的 arm64 版：
```
~/.gradle/gradle.properties
android.aapt2FromMavenOverride=/usr/lib/android-sdk/build-tools/debian/aapt2
```
换到普通电脑编译时删掉这一行。内核只放了 `arm64-v8a`；要支持 32 位机型，把对应 ABI 的 sing-box 也放进 `jniLibs/armeabi-v7a/libsingbox.so` 即可。

## 七、已知边界
- 内核只有 `arm64-v8a` 和 `armeabi-v7a` 两版（对应两个 APK）；其他 ABI 上 `ss/vmess/trojan` 会明确提示「未内置内核」，`http/socks5` 照常可用。
- REALITY 只做到「参数正确透传 + 配置通过内核校验」；要真连通还取决于服务端配置与目标站点，属于机场侧的事。
- DRM 加密流不可播。
- WebView 代理依赖 `PROXY_OVERRIDE`（System WebView 76+）。
- 重依赖 JS 的站点是 WebView 能力问题，非本项目可解。
