#!/usr/bin/env bash
# 订阅解析自测：ss / ss(base64) / trojan / vless / socks，以及脏数据不崩。
set -e
cd "$(dirname "$0")"
APP=../../app/src/main/java/com/vane/browser/proxy
OUT=build
rm -rf "$OUT" src && mkdir -p "$OUT" src/com/vane/browser/proxy
cp "$APP/Node.java" "$APP/SubscriptionParser.java" src/com/vane/browser/proxy/
javac -encoding UTF-8 -d "$OUT" $(find src test -name '*.java')
java -cp "$OUT" SubscriptionSelfTest
