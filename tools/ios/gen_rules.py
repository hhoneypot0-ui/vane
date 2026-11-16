#!/usr/bin/env python3
"""把 Android 端的 hosts.txt 规则转成 iOS WKContentRuleList 的 JSON。

输出：ios/Vane/Rules/rules-0.json ... 每片 15000 条（Apple 单条规则列表上限 150000，
分片可以并行编译、失败重试也便宜）。
"""
import json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
HOSTS = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'hosts.txt')
OUT_DIR = os.path.join(ROOT, 'ios', 'Vane', 'Rules')
PER_FILE = 15000

def hosts():
    seen = set()
    with open(HOSTS, encoding='utf-8', errors='ignore') as f:
        for line in f:
            s = line.strip()
            if not s or s.startswith('#'):
                continue
            parts = s.split()
            host = parts[1] if len(parts) > 1 else parts[0]
            host = host.lower().strip('.')
            if not host or '.' not in host:
                continue
            if re.fullmatch(r'[0-9.]+', host):     # 纯 IP 行
                continue
            seen.add(host)
    return sorted(seen)

def main():
    hosts_list = hosts()
    os.makedirs(OUT_DIR, exist_ok=True)
    for old in os.listdir(OUT_DIR):
        if old.endswith('.json'):
            os.remove(os.path.join(OUT_DIR, old))
    # 去掉已有索引文件
    idx = os.path.join(OUT_DIR, 'index.json')
    if os.path.exists(idx):
        os.remove(idx)

    chunks = [hosts_list[i:i + PER_FILE] for i in range(0, len(hosts_list), PER_FILE)]
    names = []
    for n, chunk in enumerate(chunks):
        rules = []
        for h in chunk:
            esc = re.escape(h).replace('\\-', '-')
            rules.append({
                "trigger": {"url-filter": f"^https?://([^/]+\\.)?{esc}([/:]|$)",
                            "resource-type": ["document", "image", "style-sheet", "script",
                                              "font", "raw", "svg-document", "media", "popup"]},
                "action": {"type": "block"}
            })
        path = os.path.join(OUT_DIR, f'rules-{n}.json')
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(rules, f, ensure_ascii=False, separators=(',', ':'))
        names.append(f'rules-{n}.json')
        print(f'{names[-1]}: {len(rules)} 条，{os.path.getsize(path)//1024} KB')
    with open(idx, 'w', encoding='utf-8') as f:
        json.dump(names, f)
    print(f'共 {len(hosts_list)} 个域名，{len(names)} 个分片 -> {OUT_DIR}')

if __name__ == '__main__':
    sys.exit(main())
