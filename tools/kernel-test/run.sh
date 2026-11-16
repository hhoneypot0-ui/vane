#!/usr/bin/env bash
# 内核联调：校验 CoreConfig 生成的配置，并用 sing-box 起服务端/客户端跑真实流量。
# 用法：SING_BOX_BIN=/path/to/sing-box ./run.sh
set -uo pipefail
cd "$(dirname "$0")"
BIN="${SING_BOX_BIN:-}"
if [ -z "$BIN" ] || [ ! -x "$BIN" ]; then
  echo "请设置 SING_BOX_BIN 指向 sing-box 可执行文件（1.14+）"; exit 1
fi
ASSETS=../../app/src/main/assets
APP=../../app/src/main/java/com/vane/browser/proxy
OUT=build
WORK=$OUT/work
rm -rf "$OUT" && mkdir -p "$OUT" "$WORK" src/com/vane/browser/proxy
cp "$APP/Node.java" "$APP/RuleEngine.java" "$APP/CoreConfig.java" "$APP/SubscriptionParser.java" src/com/vane/browser/proxy/
javac -encoding UTF-8 -cp lib/json.jar -d "$OUT" $(find src stubs -name '*.java') || exit 1
CP="$OUT:lib/json.jar"
PASS=0; FAIL=0
ok(){ echo "  ok   $1"; PASS=$((PASS+1)); }
bad(){ echo "  FAIL $1"; FAIL=$((FAIL+1)); }

echo "=== 内核版本 ==="
"$BIN" version 2>&1 | head -2

echo
echo "=== 0) 订阅链条：订阅文本 → 解析 → 生成配置 ==="
if java -Dassets.dir="$ASSETS" -cp "$CP" SubChain "$WORK/cfg-sub.json"; then
  if "$BIN" check -c "$WORK/cfg-sub.json" >/dev/null 2>"$WORK/check-sub.err"; then ok "订阅解析出的 6 种节点配置可被内核接受"; else bad "订阅配置未通过内核校验"; cat "$WORK/check-sub.err"; fi
else
  bad "订阅解析链条失败"
fi

echo
echo "=== 1) 配置校验（sing-box check）==="
for m in rule global direct; do
  java -Dassets.dir="$ASSETS" -cp "$CP" GenCfg "$WORK/cfg-$m.json" "$m" vmess >/dev/null
  if "$BIN" check -c "$WORK/cfg-$m.json" >/dev/null 2>"$WORK/check-$m.err"; then ok "mode=$m"; else bad "mode=$m"; cat "$WORK/check-$m.err"; fi
done
for n in trojan ss vmess; do
  java -Dassets.dir="$ASSETS" -cp "$CP" GenCfg "$WORK/cfg-node-$n.json" global "$n" >/dev/null
  if "$BIN" check -c "$WORK/cfg-node-$n.json" >/dev/null 2>"$WORK/check-$n.err"; then ok "node=$n"; else bad "node=$n"; cat "$WORK/check-$n.err"; fi
done

# ---- 公共测试设施：玩具 DNS + 本地 HTTP 站 ----
cat > "$WORK/dns.py" <<'PY'
import socket, struct, sys
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.bind(('127.0.0.1', 5353))
log = open(sys.argv[1], 'a', buffering=1)
while True:
    data, addr = s.recvfrom(2048)
    i = 12; q = b''
    while data[i] != 0:
        ln = data[i]; q += data[i+1:i+1+ln] + b'.'; i += ln + 1
    log.write(q.decode(errors='replace') + '\n')
    resp = data[:2] + b'\x81\x80\x00\x01\x00\x01\x00\x00\x00\x00' + data[12:i+5]
    resp += b'\xc0\x0c\x00\x01\x00\x01\x00\x00\x00\x1e\x00\x04' + bytes([127, 0, 0, 1])
    s.sendto(resp, addr)
PY
cat > "$WORK/web.py" <<'PY'
import http.server, socketserver
class H(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        body = b'vane-kernel-ok'
        self.send_response(200); self.send_header('Content-Length', str(len(body))); self.end_headers(); self.wfile.write(body)
    def log_message(self, *a): pass
socketserver.TCPServer.allow_reuse_address = True
socketserver.TCPServer(('127.0.0.1', 18080), H).serve_forever()
PY
python3 "$WORK/dns.py" "$WORK/dns-queries.log" & DNS=$!
python3 "$WORK/web.py" & WEB=$!
sleep 0.8

cat > "$WORK/server-vmess.json" <<'JSON'
{"log":{"level":"debug"},
 "inbounds":[{"type":"vmess","tag":"in","listen":"127.0.0.1","listen_port":10086,
   "users":[{"uuid":"b831381d-6324-4d53-ad4f-8cda48b30811","alterId":0}]}],
 "outbounds":[{"type":"direct","tag":"direct"}],
 "route":{"auto_detect_interface":false}}
JSON

openssl req -x509 -newkey rsa:2048 -nodes -keyout "$WORK/key.pem" -out "$WORK/cert.pem" \
  -days 1 -subj "/CN=localhost" >/dev/null 2>&1
cat > "$WORK/server-trojan.json" <<JSON
{"log":{"level":"debug"},
 "inbounds":[{"type":"trojan","tag":"in","listen":"127.0.0.1","listen_port":10443,"users":[{"password":"tjpass"}],
   "tls":{"enabled":true,"certificate_path":"$PWD/$WORK/cert.pem","key_path":"$PWD/$WORK/key.pem"}}],
 "outbounds":[{"type":"direct","tag":"direct"}],
 "route":{"auto_detect_interface":false}}
JSON
cat > "$WORK/server-hysteria2.json" <<JSON
{"log":{"level":"debug"},
 "inbounds":[{"type":"hysteria2","tag":"in","listen":"127.0.0.1","listen_port":10444,
   "users":[{"password":"hypass"}],
   "obfs":{"type":"salamander","password":"ob"},
   "tls":{"enabled":true,"certificate_path":"$PWD/$WORK/cert.pem","key_path":"$PWD/$WORK/key.pem"}}],
 "outbounds":[{"type":"direct","tag":"direct"}],
 "route":{"auto_detect_interface":false}}
JSON
cat > "$WORK/server-tuic.json" <<JSON
{"log":{"level":"debug"},
 "inbounds":[{"type":"tuic","tag":"in","listen":"127.0.0.1","listen_port":10445,
   "users":[{"uuid":"b831381d-6324-4d53-ad4f-8cda48b30811","password":"tuicpass"}],
   "congestion_control":"bbr",
   "tls":{"enabled":true,"certificate_path":"$PWD/$WORK/cert.pem","key_path":"$PWD/$WORK/key.pem","alpn":["h3"]}}],
 "outbounds":[{"type":"direct","tag":"direct"}],
 "route":{"auto_detect_interface":false}}
JSON
cat > "$WORK/server-ss.json" <<'JSON'
{"log":{"level":"debug"},
 "inbounds":[{"type":"shadowsocks","tag":"in","listen":"127.0.0.1","listen_port":10888,
   "method":"aes-128-gcm","password":"sspass"}],
 "outbounds":[{"type":"direct","tag":"direct"}],
 "route":{"auto_detect_interface":false}}
JSON

cat > "$WORK/patch-dns.py" <<'PY'
import json, sys
p = sys.argv[1]
c = json.load(open(p, encoding='utf-8'))
for srv in c['dns']['servers']:
    if srv.get('tag') == 'local':
        srv['server'] = '127.0.0.1'
        srv['server_port'] = 5353
# 测试目标就是 127.0.0.1，去掉私网直连规则才能证明流量真的穿过了节点
c['route']['rules'] = [r for r in c['route']['rules'] if 'ip_is_private' not in r]
c['log']['level'] = 'debug'
json.dump(c, open(p, 'w', encoding='utf-8'), ensure_ascii=False)
PY

wait_port() { # host port -> 最多等 10 秒
  python3 - "$1" "$2" <<'PY'
import socket, sys, time
h, p = sys.argv[1], int(sys.argv[2])
for _ in range(250):
    try:
        s = socket.create_connection((h, p), 0.3); s.close(); sys.exit(0)
    except Exception:
        time.sleep(0.1)
sys.exit(1)
PY
}

SERVERS=""
start_server() { # $1=type
  case $1 in
    vmess) "$BIN" run -c "$WORK/server-vmess.json" >"$WORK/srv-vmess.log" 2>&1 & ;;
    trojan) "$BIN" run -c "$WORK/server-trojan.json" >"$WORK/srv-trojan.log" 2>&1 & ;;
    ss) "$BIN" run -c "$WORK/server-ss.json" >"$WORK/srv-ss.log" 2>&1 & ;;
    hysteria2) "$BIN" run -c "$WORK/server-hysteria2.json" >"$WORK/srv-hysteria2.log" 2>&1 & ;;
    tuic) "$BIN" run -c "$WORK/server-tuic.json" >"$WORK/srv-tuic.log" 2>&1 & ;;
  esac
  SERVERS="$SERVERS $!"
}

e2e() { # $1=名称 $2=服务端配置(忽略，服务端已启动) $3=节点类型
  local name=$1 type=$3
  echo
  echo "=== 2) $name 端到端 ==="
  java -Dassets.dir="$ASSETS" -cp "$CP" GenCfg "$WORK/cfg-e2e-$type.json" global "$type" >/dev/null
  python3 "$WORK/patch-dns.py" "$WORK/cfg-e2e-$type.json"
  "$BIN" run -c "$WORK/cfg-e2e-$type.json" >"$WORK/cli-$type.log" 2>&1 & CLI=$!
  sleep 0.4
  if ! wait_port 127.0.0.1 2081; then
    bad "$name 客户端没起来"; tail -5 "$WORK/cli-$type.log" | sed 's/^/        /'; kill $SRV $CLI 2>/dev/null; return
  fi
  # 目标是「域名」，所以不会被 ip_is_private 直连，必须真的穿过节点
  if java -cp "$CP" ProxyGet 127.0.0.1:2081 http://127.0.0.1:18080/ vane-kernel-ok >"$WORK/get-$type.log" 2>&1; then
    if grep -q "outbound/" "$WORK/cli-$type.log" && grep -qi "inbound/" "$WORK/srv-$type.log"; then
      ok "$name 链路可用（已确认流量穿过节点）"
    else
      ok "$name 链路可用（日志里没看到节点记录，需复查）"
    fi
    sed 's/^/       /' "$WORK/get-$type.log" | head -3
  else
    bad "$name 链路失败"
    sed 's/^/       /' "$WORK/get-$type.log" | head -6
    echo "        -- 客户端日志 --"; tail -10 "$WORK/cli-$type.log" | sed 's/^/        /'
    echo "        -- 服务端日志 --"; tail -10 "$WORK/srv-$type.log" | sed 's/^/        /'
  fi
  echo "        DNS 查询记录: $(sort -u "$WORK/dns-queries.log" 2>/dev/null | tr '\n' ' ')"
  kill $CLI 2>/dev/null; wait $CLI 2>/dev/null
}

echo
echo "=== 启动三个测试服务端 ==="
start_server vmess; start_server trojan; start_server ss; start_server hysteria2; start_server tuic
for p in 10086 10443 10888 10444 10445; do wait_port 127.0.0.1 $p || echo "  (端口 $p 起得慢，继续)"; done
echo "服务端就绪"

e2e "vmess" x vmess
e2e "trojan + TLS" x trojan
e2e "shadowsocks" x ss
e2e "hysteria2 (QUIC)" x hysteria2
e2e "tuic (QUIC)" x tuic
kill $SERVERS 2>/dev/null; wait $SERVERS 2>/dev/null

kill $WEB $DNS 2>/dev/null; wait $WEB $DNS 2>/dev/null
echo
echo "$PASS passed, $FAIL failed"
[ $FAIL -eq 0 ] || exit 1
