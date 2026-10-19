"""CI 用的 Meta Graph API 模拟服务，只实现 CGCH 用到的几个接口。

- GET  /{ver}/{phone_number_id}            号码信息；令牌不是 good-token 时返回 190 错误
- POST /{ver}/{phone_number_id}/messages   记录请求体，返回 wamid
- GET  /{ver}/{waba_id}/message_templates  返回一个已审核模板
- GET  /_sent                              查看已记录的发送请求
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SENT = []
GOOD = 'Bearer good-token'


class Handler(BaseHTTPRequestHandler):
    def _json(self, code, body):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _auth_ok(self):
        if self.headers.get('Authorization') != GOOD:
            self._json(401, {'error': {'message': 'Invalid OAuth access token.', 'type': 'OAuthException', 'code': 190}})
            return False
        return True

    def do_GET(self):
        path = self.path.split('?')[0].strip('/').split('/')
        if path == ['_sent']:
            return self._json(200, SENT)
        if not self._auth_ok():
            return
        if len(path) == 3 and path[2] == 'message_templates':
            return self._json(200, {'data': [{
                'name': 'hello_world', 'language': 'en_US', 'status': 'APPROVED', 'category': 'UTILITY',
                'components': [{'type': 'BODY', 'text': 'Hi {{1}}, thanks for your inquiry.'}]}]})
        if len(path) == 2:
            return self._json(200, {'id': path[1], 'display_phone_number': '+86 138 0000 0000',
                                    'verified_name': 'CGCH Test', 'quality_rating': 'GREEN'})
        self._json(404, {'error': {'message': 'Unknown path', 'code': 100}})

    def do_POST(self):
        path = self.path.split('?')[0].strip('/').split('/')
        if not self._auth_ok():
            return
        length = int(self.headers.get('Content-Length') or 0)
        body = json.loads(self.rfile.read(length) or b'{}')
        if len(path) == 3 and path[2] == 'messages':
            SENT.append(body)
            return self._json(200, {'messaging_product': 'whatsapp',
                                    'contacts': [{'input': body.get('to'), 'wa_id': body.get('to')}],
                                    'messages': [{'id': f'wamid.OUT{len(SENT)}'}]})
        self._json(404, {'error': {'message': 'Unknown path', 'code': 100}})

    def log_message(self, *args):
        pass


if __name__ == '__main__':
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 18080
    ThreadingHTTPServer(('127.0.0.1', port), Handler).serve_forever()
