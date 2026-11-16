#!/usr/bin/env bash
# 一键打包：./build.sh [debug|release]
set -e
cd "$(dirname "$0")"
MODE="${1:-debug}"
if command -v gradle >/dev/null 2>&1; then
  GRADLE=gradle
else
  GRADLE=./gradlew
fi
$GRADLE ":app:assemble${MODE^}"
echo
echo "APK: app/build/outputs/apk/${MODE}/"
ls -l "app/build/outputs/apk/${MODE}"
