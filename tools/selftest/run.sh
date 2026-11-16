#!/usr/bin/env bash
# 分流代理自测：不需要 Android 设备，纯 JVM 跑通规则判断 + 直连转发 + CONNECT 隧道 + SOCKS5/HTTP 上游。
set -e
cd "$(dirname "$0")"
APP=../../app/src/main/java/com/vane/browser/proxy
ASSETS=../../app/src/main/assets
OUT=build
rm -rf "$OUT" src && mkdir -p "$OUT" src/com/vane/browser/proxy
cp "$APP/Node.java" "$APP/RuleEngine.java" "$APP/SplitProxyServer.java" src/com/vane/browser/proxy/
javac -encoding UTF-8 -d "$OUT" $(find src test -name '*.java')
java -Dassets.dir="$ASSETS" -cp "$OUT" ProxySelfTest
