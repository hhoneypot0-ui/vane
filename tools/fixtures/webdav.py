import base64, os, sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = '/tmp/webdav'
os.makedirs(ROOT, exist_ok=True)
USER, PASS = 'vane', 'vane-test'
EXPECT = 'Basic ' + base64.b64encode(f'{USER}:{PASS}'.encode()).decode()

class H(BaseHTTPRequestHandler):
    def _auth(self):
        got = self.headers.get('Authorization')
        print(self.command, self.path, 'auth=', ('ok' if got == EXPECT else (got or 'none')), flush=True)
        if got != EXPECT:
            self.send_response(401); self.send_header('WWW-Authenticate','Basic realm="dav"')
            self.end_headers(); self.wfile.write(b'auth required'); return False
        return True

    def do_PUT(self):
        if not self._auth(): return
        n = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(n)
        path = os.path.join(ROOT, os.path.basename(self.path.split('?')[0]) or 'sync.json')
        open(path, 'wb').write(body)
        print('PUT', self.path, len(body), flush=True)
        self.send_response(201); self.end_headers()

    def do_GET(self):
        if not self._auth(): return
        path = os.path.join(ROOT, os.path.basename(self.path.split('?')[0]) or 'sync.json')
        if not os.path.exists(path):
            self.send_response(404); self.end_headers(); self.wfile.write(b'no file'); return
        data = open(path, 'rb').read()
        self.send_response(200); self.send_header('Content-Type','application/json')
        self.send_header('Content-Length', str(len(data))); self.end_headers(); self.wfile.write(data)

    def do_HEAD(self):
        self.send_response(200); self.end_headers()

    def log_message(self, *a): pass

ThreadingHTTPServer(('127.0.0.1', 8778), H).serve_forever()
