"""CI 用的 Meta Graph API 模拟服务，只实现 CGCH 用到的几个接口。

- GET  /{ver}/{phone_number_id}            号码信息；令牌不是 good-token 时返回 190 错误
- POST /{ver}/{phone_number_id}/messages   记录请求体，返回 wamid
- GET  /{ver}/{waba_id}/message_templates  返回一个已审核模板
- GET  /{ver}/{media_id}                   媒体信息（media_id 以 MEDIA 开头）
- GET  /_media/{media_id}                  媒体内容（需要令牌）
- POST /{ver}/{phone_number_id}/media      记录上传，返回媒体编号
- POST /v1/chat/completions                模拟 OpenAI 兼容的大模型，按规则返回固定回答
- POST /v1/embeddings                      模拟向量模型：同义词落在同一「概念」维度上，便于验证语义检索
- GET  /_sent、/_uploads、/_llm             查看已记录的发送、上传与大模型请求
"""
import json
import math
import re
import zlib
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SENT = []
UPLOADS = []
LLM = []
SITE_HITS = []
EMBEDS = []
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

    # 模拟一个客户公司官网（虚构企业），用于深度背调测试
    SITE = {
        '': ('Bluewave Home Appliances Ltd',
             '<h1>Bluewave Home Appliances Ltd</h1><p>Distributor of small kitchen and personal care appliances '
             'for retail chains in Australia and New Zealand.</p>'
             '<a href="/about-us">About us</a> <a href="/products">Products</a> <a href="/contact">Contact</a> '
             '<a href="/private/plan">Internal plan</a> <a href="https://other.example/x">Partner</a>'),
        'about-us': ('About Bluewave', '<p>Our story: Bluewave supplies more than 300 retail stores and runs its own '
                     'warehouse in Sydney. ISO 9001 certified.</p>'),
        'catalog': ('Catalog', '<p>Hair dryers, hair straighteners, blenders and kettles. We are looking for new '
                    'OEM suppliers of lightweight hair dryers for 2027.</p>'),
        'contact': ('Contact', '<p>sales@bluewave.example, +61 2 5550 1234, Sydney NSW</p>'),
        'private/plan': ('Internal', '<p>SECRET-INTERNAL do not crawl</p>'),
    }

    def _site(self, path):
        key = '/'.join(path)
        if key == 'robots.txt':
            body = b'User-agent: *\nDisallow: /private\n'
            self.send_response(200); self.send_header('Content-Type', 'text/plain'); self.send_header('Content-Length', str(len(body)))
            self.end_headers(); self.wfile.write(body); return True
        if key == 'products':  # 站内重定向
            self.send_response(301); self.send_header('Location', '/catalog'); self.send_header('Content-Length', '0'); self.end_headers()
            return True
        if key in self.SITE:
            title, html = self.SITE[key]
            body = f'<html><head><title>{title}</title><script>var x=1;</script></head><body>{html}</body></html>'.encode()
            self.send_response(200); self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.send_header('Content-Length', str(len(body))); self.end_headers(); self.wfile.write(body)
            SITE_HITS.append('/' + key)
            return True
        return False

    def do_GET(self):
        path = self.path.split('?')[0].strip('/').split('/')
        if path == ['_site_hits']:
            return self._json(200, SITE_HITS)
        if self._site(path):
            return
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
        if path == ['v1', 'embeddings']:
            return self._embed(json.loads(raw or b'{}'))
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

    # 概念维度：同一行的词语义相近；其余词按哈希落到低权重维度
    CONCEPTS = [
        {'moq', 'minimum', 'smallest', 'least', 'minimal', '起订量', '最少'},
        {'lead', 'delivery', 'deliver', 'ship', 'shipping', 'production', 'weeks', 'days', 'long', '交期'},
        {'company', 'factory', 'manufacturer', 'located', 'dongguan', 'about'},
        {'price', 'cost', 'quote', 'quotation', 'cheap', 'expensive', '价格'},
        {'certificate', 'certified', 'certification', 'ce', 'rohs', 'europe', 'eu', '认证'},
    ]

    def _embed(self, body):
        if self.headers.get('Authorization') != 'Bearer llm-key':
            return self._json(401, {'error': {'message': 'Incorrect API key provided'}})
        if body.get('model') != 'mock-embed':
            return self._json(404, {'error': {'message': f"model {body.get('model')} not found"}})
        inputs = body.get('input') or []
        inputs = [inputs] if isinstance(inputs, str) else inputs
        data, tokens = [], 0
        for i, text in enumerate(inputs):
            words = re.findall(r'[a-z]+|[\u4e00-\u9fff]+', text.lower())
            tokens += len(words)
            v = [0.0] * 64
            for w in words:
                hit = [c for c, ws in enumerate(self.CONCEPTS) if w in ws]
                for c in hit:
                    v[c] += 1.0
                if not hit:
                    v[8 + zlib.crc32(w.encode()) % 56] += 0.3
            n = math.sqrt(sum(x * x for x in v)) or 1.0
            data.append({'object': 'embedding', 'index': i, 'embedding': [round(x / n, 6) for x in v]})
        EMBEDS.append({'model': body.get('model'), 'count': len(inputs)})
        self._json(200, {'object': 'list', 'data': data, 'model': body.get('model'),
                         'usage': {'prompt_tokens': tokens, 'total_tokens': tokens}})

    def _assistant(self, body, msgs, last):
        usage = {'prompt_tokens': 300, 'completion_tokens': 40}
        if msgs[-1]['role'] == 'tool':  # 已拿到工具结果：据此作答
            i = max(k for k, m in enumerate(msgs) if m['role'] == 'user')
            results = [m['content'] for m in msgs[i + 1:] if m['role'] == 'tool']
            answer = '根据查询结果：' + ' | '.join(results)[:8000]
            return self._json(200, {'choices': [{'message': {'role': 'assistant', 'content': answer}}], 'usage': usage})
        call = None
        if body.get('tools'):
            if 'acme' in last:
                call = ('get_customer', {'name': 'Acme Buyer'})
            elif '跟进' in last:
                call = ('followups_due', {'days': 7, 'inactive_days': 30})
            elif '统计' in last or '询盘' in last:
                call = ('get_stats', {'days': 30})
            elif '退款' in last:
                call = ('search_messages', {'keyword': 'refund', 'days': 30})
            elif '草稿' in last:
                call = ('pending_drafts', {})
        if call is None:
            return self._json(200, {'choices': [{'message': {'role': 'assistant', 'content': '你好，我可以帮你查客户、消息和统计。'}}], 'usage': usage})
        tc = {'id': f'call_{len(LLM)}', 'type': 'function', 'function': {'name': call[0], 'arguments': json.dumps(call[1], ensure_ascii=False)}}
        return self._json(200, {'choices': [{'message': {'role': 'assistant', 'content': None, 'tool_calls': [tc]}}], 'usage': usage})

    def _llm(self, body):
        if self.headers.get('Authorization') != 'Bearer llm-key':
            return self._json(401, {'error': {'message': 'Incorrect API key provided', 'type': 'invalid_request_error'}})
        msgs = body.get('messages', [])
        LLM.append(body)
        system = msgs[0]['content'] if msgs and msgs[0]['role'] == 'system' else ''
        last = next((m['content'] for m in reversed(msgs) if m['role'] == 'user'), '').lower()
        if 'CRM 里的工作助手' in system:  # 对话助手：工具调用
            return self._assistant(body, msgs, last)
        if '背调分析师' in system:  # 深度背调报告
            data = msgs[-1]['content'] if msgs else ''
            if 'Bluewave' in data:
                answer = json.dumps({
                    'companyName': 'Bluewave Home Appliances Ltd', 'countryCode': 'au',
                    'summary': 'Bluewave 是澳大利亚的小家电分销商，为零售连锁供货。',
                    'businessType': 'distributor', 'mainProducts': ['Hair dryers', 'Blenders'] if 'Hair dryers' in data else [],
                    'markets': ['Australia', 'New Zealand'], 'scale': '供货 300 多家门店' if '300 retail stores' in data else None,
                    'certifications': ['ISO 9001'] if 'ISO 9001' in data else [],
                    'companyContacts': ['sales@bluewave.example'] if 'sales@bluewave.example' in data else [],
                    'purchasingSignals': ['正在寻找 2027 年轻量吹风机 OEM 供应商'] if 'OEM suppliers' in data else [],
                    'talkingPoints': ['介绍轻量吹风机'], 'risks': [], 'confidence': 'medium'}, ensure_ascii=False)
            else:
                answer = json.dumps({'companyName': None, 'summary': '资料不足', 'confidence': 'low'}, ensure_ascii=False)
        elif '客户唤醒' in system:  # 客户唤醒邮件
            data = msgs[-1]['content'] if msgs else ''
            name = next((l.split('：', 1)[1].strip() for l in data.splitlines() if l.startswith('客户名称：')), 'there')
            subject = 'New 2027 hair dryer line' if '2027' in data else 'Checking in'
            answer = json.dumps({'subject': subject, 'body': f'Hi {name},\n\nIt has been a while since we last talked. '
                                 'We just launched our 2027 hair dryer line. Would you like a sample?'}, ensure_ascii=False)
        elif '工作报告' in system:  # AI 工作报告总结
            data = msgs[-1]['content'] if msgs else ''
            answer = '【概况】数据已收到，询盘处理正常。\n【需要关注】' + ('有转人工对话。' if '转人工对话' in data else '无') \
                     + '\n【建议补充知识库】按缺口清单补充。\n【下一步建议】跟进转人工客户。'
        elif 'Return JSON only' in system:  # 线索信息提取
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
