# 架构说明

## 一条请求的完整路径

```
地址栏输入
  └─ SearchEngines.url()  判断是网址还是搜索词，套对应引擎模板
      └─ BrowserTab.load()  WebView 加载
          ├─ 广告拦截  WebViewClient.shouldInterceptRequest → AdBlocker.shouldBlock(host)
          ├─ 本地代理  ProxyController → 127.0.0.1:<port>
          │     └─ SplitProxyServer
          │         ├─ RuleEngine.proxyFor(host)
          │         ├─ 直连 → 自己开 socket 打目标站
          │         └─ 代理 → 上游 HTTP / SOCKS5 握手后转发
          └─ 页面完成 → 注入 VideoSniffer（嗅探 + 长按 + 夜间/阅读）
                └─ 抓到媒体 → JsBridge.MEDIA → 「嗅探视频」→ PlayerActivity
```

## 为什么代理要自己做一层本地服务

WebView 只能设置「一个 HTTP 代理」，没法按域名分流。所以：

1. 把 WebView 全部流量指到 `127.0.0.1:port`；
2. 本地这层按 host 决策：国内域名/IP 直接连目标（等于直连），境外转给上游节点；
3. 对 HTTPS 用的是 `CONNECT host:port`，这一层看得到 host，所以分流判断是准确的。

好处：不需要 VPN 权限、不影响其他 App、随时开关。
代价：只覆盖本浏览器流量（正是我们想要的）。

## 三种代理实现的关系

- `SplitProxyServer`：纯 Java，零依赖，处理 `http/socks5` 节点。
- `ProxyCore`：把 `sing-box` 二进制装进私有目录，生成 `config.json`（`CoreConfig`），
  监听 `127.0.0.1:2081` 的 mixed 口，处理 `ss/vmess/vless/trojan/hysteria2`。
- `AuroraVpnService`：可选，建 tun 后交给 `hev-socks5-tunnel`，让全机流量进同一套规则。

`ProxyManager.start()` 会按节点类型自动选，失败会给出明确原因（`lastError`）。

## 界面即数据

`UiConfig` 序列化成 JSON 存在 SharedPreferences：

```json
{
  "theme": "auto", "accent": "#5B8CFF", "corner": 16,
  "showTopBar": true, "showBottomBar": true,
  "items": [
    {"id":"address","glyph":"","action":"focusAddress","long":"engineMenu","flex":true},
    {"id":"back","glyph":"‹","action":"back","long":"history","width":46,"visible":true}
  ],
  "gestures": {"edge_left_swipe":"back","address_long_press":"engineMenu"}
}
```

`MainActivity.buildBottomBar()` 只做一件事：遍历 `items` 生成 TextView，点按 `dispatch(action)`、
长按 `dispatch(longAction)`。所以「任何按键都能调」不是噱头，是真的没有写死的按钮。

`UiConfig.save()` 会自增 `ui_rev`；`MainActivity.onResume()` 比对版本号，变了就重建界面 —— 编辑完返回即可看到效果，不用重启。

## 标签页

每个 `BrowserTab` 持有一个 WebView，全部挂在同一个 `FrameLayout` 里，用 `View.GONE/VISIBLE` 切换。
这样切换标签不掉页面状态，也不用管理多个 Activity。销毁时 `destroy()` 释放。

## 播放器取流

`PlayerActivity` 的 `WebViewClient.shouldInterceptRequest` 会把每个 http(s) 请求重新用
`HttpURLConnection` 发一遍，补上 `Referer`（当前页）、`User-Agent`、`Cookie`，并把 `Range`
透传、把 `Content-Range/Accept-Ranges` 回传，因此：
- 防盗链站点的 m3u8 能拉到；
- 拖动进度条能续传。

## 线程模型

- UI 全在主线程；
- 订阅刷新、代理转发、内核日志各用独立线程（`new Thread` + daemon）；
- `SplitProxyServer` 每个连接一个线程，双向 `pump` 用两个 daemon 线程，任一端断开即回收。
