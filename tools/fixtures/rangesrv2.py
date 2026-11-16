import os, re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
PATH='/tmp/range_big.bin'
SIZE=os.path.getsize(PATH)
class H(BaseHTTPRequestHandler):
    protocol_version='HTTP/1.1'
    def do_GET(self):
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
            self.end_headers(); self.wfile.write(body)
        else:
            body=f.read()
            self.send_response(200)
            self.send_header('Accept-Ranges','bytes')
            self.send_header('Content-Length', str(len(body)))
            self.send_header('Content-Type','application/octet-stream')
            self.end_headers(); self.wfile.write(body)
        f.close()
    def do_HEAD(self):
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
ThreadingHTTPServer(('127.0.0.1',8780),H).serve_forever()
