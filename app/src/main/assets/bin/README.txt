把可选内核放这里，文件名必须是 sing-box：

  bin/arm64-v8a/sing-box
  bin/armeabi-v7a/sing-box
  bin/x86_64/sing-box

放了内核之后，ss / vmess / vless / trojan / hysteria2 这些协议才会真正可用
（App 会把二进制复制到私有目录、chmod 755，并用自动生成的 config.json 拉起）。

没放内核也能用：
· http / socks5 节点 —— 内置纯 Java 分流器直接支持；
· 国内直连 / 境外代理的分流逻辑同样生效。

同时把 hev-socks5-tunnel 的 libhev-socks5-tunnel.so 放到
app/src/main/jniLibs/<abi>/ 下，可启用「全机 VPN 分流」（可选功能）。
