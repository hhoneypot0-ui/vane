import os, re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
PATH='/tmp/range_big.bin'
PATH2='/tmp/range_big2.bin'
SIZE=os.path.getsize(PATH)
class H(BaseHTTPRequestHandler):
    protocol_version='HTTP/1.1'
    def do_GET(self):
        global PATH, SIZE
        if 'slow2' in self.path:
            PATH=PATH2
        SIZE=os.path.getsize(PATH)
        rng=self.headers.get('Range')
        print('GET', self.path, 'Range=', rng, flush=True)
        f=open(PATH,'rb')
        if rng:
            m=re.match(r'bytes=(\d+)-(\d*)', rng)
            start=int(m.group(1)); end=int(m.group(2)) if m.group(2) else SIZE-1
            end=min(end, SIZE-1)
            f.seek(start); body=f.read(end-start+1)
            self.send_response(206)
            self.send_header('Content-Range', f'bytes {start}-{end}/{SIZE}')
            self.send_header('Accept-Ranges','bytes')
            self.send_header('Content-Length', str(len(body)))
            self.send_header('Content-Type','application/octet-stream')
            self.end_headers()
            # 限速约 350KB/s，方便测暂停/续传
            chunk=65536
            for i in range(0, len(body), chunk):
                self.wfile.write(body[i:i+chunk]); self.wfile.flush()
                import time; time.sleep(0.65)
        else:
            body=f.read()
            self.send_response(200)
            self.send_header('Accept-Ranges','bytes')
            self.send_header('Content-Length', str(len(body)))
            self.send_header('Content-Type','application/octet-stream')
            self.end_headers()
            # 限速约 350KB/s，方便测暂停/续传
            chunk=65536
            for i in range(0, len(body), chunk):
                self.wfile.write(body[i:i+chunk]); self.wfile.flush()
                import time; time.sleep(0.65)
        f.close()
    def do_HEAD(self):
        global PATH
        if 'slow2' in self.path: PATH=PATH2
        rng=self.headers.get('Range')
        print('HEAD', self.path, 'Range=', rng, flush=True)
        if rng:
            m=re.match(r'bytes=(\d+)-(\d*)', rng)
            start=int(m.group(1)); end=int(m.group(2)) if m.group(2) else SIZE-1
            end=min(end, SIZE-1)
            self.send_response(206)
            self.send_header('Content-Range', f'bytes {start}-{end}/{SIZE}')
            self.send_header('Accept-Ranges','bytes')
            self.send_header('Content-Length', str(end-start+1))
            self.send_header('Content-Type','application/octet-stream')
            self.end_headers()
        else:
            self.send_response(200)
            self.send_header('Accept-Ranges','bytes')
            self.send_header('Content-Length', str(SIZE))
            self.send_header('Content-Type','application/octet-stream')
            self.end_headers()

    def log_message(self,*a): pass
ThreadingHTTPServer(('127.0.0.1',8782),H).serve_forever()
