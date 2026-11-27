from http.server import BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
import json

ALLOWED = ('facebook.com', 'fb.watch', 'instagram.com', 'reddit.com', 'redd.it')


def host_ok(u):
    h = (urlparse(u).hostname or '').lower()
    return any(h == d or h.endswith('.' + d) for d in ALLOWED)


class handler(BaseHTTPRequestHandler):
    def _send(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header('content-type', 'application/json; charset=utf-8')
        self.send_header('cache-control', 'no-store')
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        q = parse_qs(urlparse(self.path).query)
        url = (q.get('url') or [''])[0]
        if not url or not host_ok(url) or urlparse(url).scheme not in ('http', 'https'):
            return self._send(400, {'error': 'bad_url'})
        try:
            import yt_dlp
            opts = {'quiet': True, 'no_warnings': True, 'skip_download': True, 'noplaylist': True,
                    'socket_timeout': 15, 'cachedir': False}
            with yt_dlp.YoutubeDL(opts) as y:
                info = y.extract_info(url, download=False)
            fmts = [f for f in info.get('formats', []) if f.get('url') and f.get('protocol') in ('https', 'http')
                    and f.get('vcodec') not in (None, 'none') and f.get('acodec') not in (None, 'none')]
            fmts.sort(key=lambda f: (f.get('height') or 0), reverse=True)
            items = []
            for f in fmts[:2]:
                h = f.get('height')
                items.append({'label': ('جودة ' + str(h) + 'p') if h else 'فيديو', 'url': f['url'], 'ext': 'mp4'})
            if not items and info.get('url'):
                items.append({'label': 'فيديو', 'url': info['url'], 'ext': 'mp4'})
            if not items:
                return self._send(502, {'error': 'failed', 'detail': 'no progressive format'})
            return self._send(200, {'platform': info.get('extractor_key') or 'Video', 'title': (info.get('title') or 'Video')[:100],
                                    'thumb': info.get('thumbnail'), 'items': items})
        except Exception as e:
            return self._send(502, {'error': 'failed', 'detail': str(e)[:160]})
