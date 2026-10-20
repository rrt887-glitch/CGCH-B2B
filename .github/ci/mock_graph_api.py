"""CI 用的 Meta Graph API 模拟服务，只实现 CGCH 用到的几个接口。

- GET  /{ver}/{phone_number_id}            号码信息；令牌不是 good-token 时返回 190 错误
- POST /{ver}/{phone_number_id}/messages   记录请求体，返回 wamid
- GET  /{ver}/{waba_id}/message_templates  返回一个已审核模板
- GET  /{ver}/{media_id}                   媒体信息（media_id 以 MEDIA 开头）
- GET  /_media/{media_id}                  媒体内容（需要令牌）
- POST /{ver}/{phone_number_id}/media      记录上传，返回媒体编号
- POST /v1/chat/completions                模拟 OpenAI 兼容的大模型，按规则返回固定回答
- GET  /_sent、/_uploads、/_llm             查看已记录的发送、上传与大模型请求
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SENT = []
UPLOADS = []
LLM = []
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
        if path == ['_uploads']:
            return self._json(200, UPLOADS)
        if path == ['_llm']:
            return self._json(200, LLM)
        if not self._auth_ok():
            return
        if len(path) == 2 and path[0] == '_media':
            # 带真实 JPEG 文件头，文件服务按内容识别类型
            data = bytes.fromhex('ffd8ffe000104a46494600010100000100010000') + f'JPEGDATA-{path[1]}'.encode() + b'\xff\xd9'
            self.send_response(200)
            self.send_header('Content-Type', 'image/jpeg')
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        if len(path) == 2 and path[1].startswith('MEDIA'):
            host = self.headers.get('Host')
            return self._json(200, {'id': path[1], 'mime_type': 'image/jpeg', 'url': f'http://{host}/_media/{path[1]}'})
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
        length = int(self.headers.get('Content-Length') or 0)
        raw = self.rfile.read(length)
        if path == ['v1', 'chat', 'completions']:
            return self._llm(json.loads(raw or b'{}'))
        if not self._auth_ok():
            return
        if len(path) == 3 and path[2] == 'media':
            ctype = self.headers.get('Content-Type', '')
            UPLOADS.append({'contentType': ctype, 'size': len(raw),
                            'hasWhatsapp': b'name="messaging_product"' in raw and b'whatsapp' in raw,
                            'fileName': raw.split(b'filename="')[1].split(b'"')[0].decode() if b'filename="' in raw else None,
                            'payload': raw.split(b'\r\n\r\n')[-1].split(b'\r\n--')[0].decode(errors='ignore')})
            return self._json(200, {'id': f'UP{len(UPLOADS)}'})
        body = json.loads(raw or b'{}')
        if len(path) == 3 and path[2] == 'messages':
            SENT.append(body)
            return self._json(200, {'messaging_product': 'whatsapp',
                                    'contacts': [{'input': body.get('to'), 'wa_id': body.get('to')}],
                                    'messages': [{'id': f'wamid.OUT{len(SENT)}'}]})
        self._json(404, {'error': {'message': 'Unknown path', 'code': 100}})

    def _llm(self, body):
        if self.headers.get('Authorization') != 'Bearer llm-key':
            return self._json(401, {'error': {'message': 'Incorrect API key provided', 'type': 'invalid_request_error'}})
        msgs = body.get('messages', [])
        LLM.append(body)
        system = msgs[0]['content'] if msgs and msgs[0]['role'] == 'system' else ''
        last = next((m['content'] for m in reversed(msgs) if m['role'] == 'user'), '').lower()
        if 'Return JSON only' in system:  # 线索信息提取
            if 'mueller' in last or 'hans' in last:
                answer = 'Sure: {"company": "Müller Haushaltsgeräte GmbH", "country_code": "de", "contact_name": "Hans Müller", "product": "hair dryer", "quantity": "2000 pcs"}'
            elif 'pierre' in last:
                answer = '{"company": null, "country_code": null, "contact_name": "Pierre", "product": "curling iron", "quantity": null}'
            else:
                answer = '{"company": null, "country_code": null, "contact_name": null, "product": null, "quantity": null}'
        elif 'single word' in last:
            answer = 'OK'
        elif 'complaint' in last or 'refund' in last:
            answer = '[HANDOFF]'
        elif 'guarantee' in last:
            answer = 'Yes, we guarantee 100% quality on every unit.'
        elif 'moq' in last:
            answer = ('Thanks for asking. Our MOQ is 500 units.' if 'MOQ is 500' in system
                      else 'I will confirm with my colleague and get back to you soon.')
        elif 'lead time' in last:
            answer = 'Our lead time is 25 days after deposit.' if '25 days' in system else 'I will confirm and get back to you.'
        else:
            answer = 'Thank you for your message. We will get back to you shortly.'
        self._json(200, {'id': 'chatcmpl-ci', 'object': 'chat.completion', 'model': body.get('model'),
                         'choices': [{'index': 0, 'message': {'role': 'assistant', 'content': answer}, 'finish_reason': 'stop'}],
                         'usage': {'prompt_tokens': len(system) // 4 + 10, 'completion_tokens': len(answer) // 4 + 1}})

    def log_message(self, *args):
        pass


if __name__ == '__main__':
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 18080
    ThreadingHTTPServer(('127.0.0.1', port), Handler).serve_forever()
