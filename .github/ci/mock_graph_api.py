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
- GET  /{ver}/oauth/access_token           Facebook 登录：code 换用户令牌、换长期令牌
- GET  /{ver}/me/accounts                  主页列表（带主页令牌与关联的 Instagram 账号）
- POST /{ver}/{page_id}/subscribed_apps    订阅主页消息推送
- POST /{ver}/me/messages                  Messenger / Instagram 发消息（JSON 或带附件的 multipart）
- GET  /_fbsent、/_subs                     查看 Messenger 发送记录与主页订阅
- /yt/...、/tt/...                         模拟 YouTube Data API 与 TikTok Accounts API；POST /_yt/add、/_tt/add 加评论，GET /_social 看回复
- /evo/...                                  模拟 Evolution API（建实例、二维码 / 配对码、连接状态、发消息、媒体、删除），POST /_evo/state 改状态，GET /_evo 查看
- POST /search                             模拟联网搜索（Tavily 兼容），GET /_search 查看搜索记录
"""
import base64
import json
import math
import re
import zlib
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SENT = []
UPLOADS = []
LLM = []
SEARCH = []
YT_THREADS = []
YT_REPLIES = []
TT_COMMENTS = []
TT_REPLIES = []
SITE_HITS = []
EVO = {}
EVO_SENT = []
EVO_DELETED = []
EVO_JPEG = bytes.fromhex('ffd8ffe000104a46494600010100000100010000') + b'EVOIMG' + b'\xff\xd9'
OAUTH = []
EMBEDS = []
GOOD = 'Bearer good-token'
FB_SENT = []
SUBS = []
TOKENS = {GOOD, 'Bearer page-token-PAGE1', 'Bearer user-long', 'Bearer biz-token'}
REG = []
HIST = {'on': False}  # POST /_hist 打开后，/{page}/conversations 返回历史会话
# Graph 对象：主页、Instagram 账号、Messenger / Instagram 用户
PROFILES = {
    'me': {'id': 'FBU1', 'name': 'Page Owner'},
    'PN2': {'id': 'PN2', 'display_phone_number': '+1 555 0100', 'verified_name': 'Customer Co'},
    'PN3': {'id': 'PN3', 'display_phone_number': '+1 555 0111', 'verified_name': 'Customer Co'},
    'PAGE1': {'id': 'PAGE1', 'name': 'CGCH Fan Page'},
    'IG1': {'id': 'IG1', 'username': 'cgch_ig', 'name': 'CGCH IG'},
    '7001': {'first_name': 'Maria', 'last_name': 'Lopez'},
    'IGU1': {'name': 'Kenji', 'username': 'kenji_buyer'},
}


class Handler(BaseHTTPRequestHandler):
    def _json(self, code, body):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _auth_ok(self):
        if self.headers.get('Authorization') not in TOKENS:
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


    def _evo(self, method, path, raw):
        """模拟 Evolution API v2；apikey 必须是 evo-key"""
        from urllib.parse import parse_qs, urlparse
        if self.headers.get('apikey') != 'evo-key':
            return self._json(401, {'status': 401, 'error': 'Unauthorized', 'response': {'message': 'Unauthorized'}})
        body = json.loads(raw or b'{}') if raw else {}
        q = parse_qs(urlparse(self.path).query)
        def missing(n):
            return self._json(404, {'status': 404, 'error': 'Not Found', 'response': {'message': [f'The "{n}" instance does not exist']}})
        if method == 'POST' and path == ['instance', 'create']:
            n = body['instanceName']
            if n in EVO:
                return self._json(403, {'status': 403, 'error': 'Forbidden', 'response': {'message': [f'This name "{n}" is already in use.']}})
            EVO[n] = {'body': body, 'state': 'connecting', 'count': 1, 'ownerJid': None, 'profileName': None}
            pc = 'ABCD1234' if body.get('number') else None
            return self._json(201, {'instance': {'instanceName': n, 'instanceId': 'id-' + n, 'integration': body.get('integration'), 'status': 'connecting'},
                                    'hash': 'HASH-' + n, 'webhook': {'webhookUrl': body.get('webhook', {}).get('url')},
                                    'qrcode': {'pairingCode': pc, 'code': '2@abc', 'base64': 'data:image/png;base64,QR1', 'count': 1}})
        if len(path) == 3 and path[0] == 'instance':
            n = path[2]
            if n not in EVO:
                return missing(n)
            i = EVO[n]
            if method == 'GET' and path[1] == 'connectionState':
                return self._json(200, {'instance': {'instanceName': n, 'state': i['state']}})
            if method == 'GET' and path[1] == 'connect':
                i['count'] += 1
                if i['state'] == 'open':
                    return self._json(200, {'instance': {'instanceName': n, 'state': 'open'}})
                return self._json(200, {'pairingCode': 'ABCD1234' if i['body'].get('number') else None, 'code': '2@abc',
                                        'base64': f"data:image/png;base64,QR{i['count']}", 'count': i['count']})
            if method == 'DELETE' and path[1] == 'logout':
                if i['state'] == 'close':
                    return self._json(400, {'status': 400, 'error': 'Bad Request', 'response': {'message': [f'The "{n}" instance is not connected']}})
                i['state'] = 'close'
                return self._json(200, {'status': 'SUCCESS', 'error': False, 'response': {'message': 'Instance logged out'}})
            if method == 'DELETE' and path[1] == 'delete':
                EVO_DELETED.append(n)
                del EVO[n]
                return self._json(200, {'status': 'SUCCESS', 'error': False, 'response': {'message': 'Instance deleted'}})
        if method == 'GET' and path == ['instance', 'fetchInstances']:
            n = q.get('instanceName', [''])[0]
            if n not in EVO:
                return missing(n)
            i = EVO[n]
            return self._json(200, [{'id': 'id-' + n, 'name': n, 'connectionStatus': i['state'], 'ownerJid': i['ownerJid'],
                                     'profileName': i['profileName'], 'integration': 'WHATSAPP-BAILEYS', 'token': 'HASH-' + n}])
        if method == 'POST' and len(path) == 3 and path[0] == 'message' and path[1] in ('sendText', 'sendMedia'):
            n = path[2]
            if n not in EVO:
                return missing(n)
            rec = dict(body, instance=n, kind=path[1])
            if 'media' in rec:
                import base64 as b64
                rec['mediaSize'] = len(b64.b64decode(rec.pop('media')))
            EVO_SENT.append(rec)
            return self._json(201, {'key': {'remoteJid': body.get('number', '') + '@s.whatsapp.net', 'fromMe': True, 'id': f'EVOOUT{len(EVO_SENT)}'},
                                    'status': 'PENDING', 'messageType': 'conversation'})
        if method == 'POST' and len(path) == 3 and path[:2] == ['chat', 'getBase64FromMediaMessage']:
            import base64 as b64
            if path[2] not in EVO:
                return missing(path[2])
            return self._json(201, {'mediaType': 'imageMessage', 'fileName': body['message']['key']['id'] + '.jpeg', 'mimetype': 'image/jpeg',
                                    'base64': b64.b64encode(EVO_JPEG).decode()})
        self._json(404, {'status': 404, 'error': 'Not Found', 'response': {'message': ['Unknown Evolution path ' + '/'.join(path)]}})

    def do_DELETE(self):
        path = self.path.split('?')[0].strip('/').split('/')
        if path[:1] == ['evo']:
            return self._evo('DELETE', path[1:], b'')
        self._json(404, {'error': {'message': 'Unknown path', 'code': 100}})

    def _social(self, method, path, raw):
        """模拟 YouTube Data API v3（/yt）与 TikTok API for Business（/tt）"""
        from urllib.parse import parse_qs, urlparse
        q = parse_qs(urlparse(self.path).query)
        p = '/'.join(path)
        if p == 'yt/token':
            form = parse_qs(raw.decode())
            if form.get('grant_type') == ['authorization_code'] and form.get('code') != ['yt-code']:
                return self._json(400, {'error': 'invalid_grant', 'error_description': 'Bad code'})
            return self._json(200, {'access_token': 'yt-access', 'refresh_token': 'yt-refresh', 'expires_in': 3600})
        if p.startswith('yt/v3/'):
            if self.headers.get('Authorization') != 'Bearer yt-access':
                return self._json(401, {'error': {'message': 'Invalid Credentials'}})
            r = p[len('yt/v3/'):]
            if r == 'channels':
                return self._json(200, {'items': [{'id': 'UCCGCH', 'snippet': {'title': 'CGCH Channel'}}]})
            if r == 'commentThreads':
                return self._json(200, {'items': YT_THREADS})
            if r == 'videos':
                return self._json(200, {'items': [{'id': 'vid1', 'snippet': {'title': 'Hair dryer demo'}}]})
            if r == 'comments' and method == 'POST':
                b = json.loads(raw or b'{}')['snippet']
                YT_REPLIES.append(b)
                rid = f'ytr{len(YT_REPLIES)}'
                for t in YT_THREADS:
                    if t['id'] == b['parentId']:
                        t.setdefault('replies', {'comments': []})['comments'].append({'id': rid, 'snippet': {
                            'authorChannelId': {'value': 'UCCGCH'}, 'authorDisplayName': 'CGCH Channel',
                            'textOriginal': b['textOriginal'], 'publishedAt': '2026-10-26T10:00:00Z'}})
                return self._json(200, {'id': rid})
        if p in ('tt/v1.3/tt_user/oauth2/token', 'tt/v1.3/tt_user/oauth2/refresh_token'):
            b = json.loads(raw or b'{}')
            if p.endswith('/token') and b.get('auth_code') != 'tt-code':
                return self._json(200, {'code': 40001, 'message': 'auth_code invalid'})
            return self._json(200, {'code': 0, 'message': 'OK', 'data': {'access_token': 'tt-access', 'refresh_token': 'tt-refresh',
                                                                       'expires_in': 86400, 'open_id': 'OPEN1'}})
        if p.startswith('tt/v1.3/'):
            if self.headers.get('Access-Token') != 'tt-access':
                return self._json(200, {'code': 40105, 'message': 'Access token is invalid'})
            r = p[len('tt/v1.3/'):]
            if r == 'business/get':
                return self._json(200, {'code': 0, 'data': {'username': 'cgch_shop', 'display_name': 'CGCH Shop'}})
            if r == 'business/video/list':
                return self._json(200, {'code': 0, 'data': {'videos': [{'item_id': 'TV1', 'caption': 'New dryer'}], 'has_more': False}})
            if r == 'business/comment/list':
                return self._json(200, {'code': 0, 'data': {'comments': [c for c in TT_COMMENTS if c['video_id'] == q.get('video_id', [''])[0]],
                                                             'has_more': False}})
            if r == 'business/comment/reply/create' and method == 'POST':
                b = json.loads(raw or b'{}')
                TT_REPLIES.append(b)
                cid = f'TR{len(TT_REPLIES)}'
                for c in TT_COMMENTS:
                    if c['comment_id'] == b['comment_id']:
                        c.setdefault('reply_list', []).append({'comment_id': cid, 'text': b['text'], 'owner': True,
                                                               'display_name': 'CGCH Shop', 'create_time': '1793000000'})
                return self._json(200, {'code': 0, 'data': {'comment_id': cid, 'parent_comment_id': b['comment_id']}})
        if p == '_yt/add':
            YT_THREADS.append(json.loads(raw))
            return self._json(200, {})
        if p == '_tt/add':
            TT_COMMENTS.append(json.loads(raw))
            return self._json(200, {})
        if p == '_social':
            return self._json(200, {'yt': YT_REPLIES, 'tt': TT_REPLIES})
        return self._json(404, {'error': {'message': 'Unknown social path ' + p}})

    def do_GET(self):
        path = self.path.split('?')[0].strip('/').split('/')
        if path and path[0] in ('yt', 'tt', '_social'):
            return self._social('GET', path, b'')
        if path[:1] == ['evo']:
            return self._evo('GET', path[1:], b'')
        if path == ['_evo']:
            return self._json(200, {'instances': EVO, 'sent': EVO_SENT, 'deleted': EVO_DELETED})
        if path == ['_site_hits']:
            return self._json(200, SITE_HITS)
        if path == ['_oauth']:
            return self._json(200, OAUTH)
        if path == ['oauth', 'google', 'userinfo']:  # 模拟 Google 取邮箱
            if self.headers.get('Authorization', '').startswith('Bearer ya29.'):
                return self._json(200, {'email': 'Sales.Gmail@cgch.test', 'name': 'Gmail Sales'})
            return self._json(401, {'error': 'invalid_token'})
        if self._site(path):
            return
        if path == ['_sent']:
            return self._json(200, SENT)
        if path == ['_uploads']:
            return self._json(200, UPLOADS)
        if path == ['_search']:
            return self._json(200, SEARCH)
        if path == ['_llm']:
            return self._json(200, LLM)
        if path == ['_fbsent']:
            return self._json(200, FB_SENT)
        if path == ['_subs']:
            return self._json(200, SUBS)
        if path == ['_reg']:
            return self._json(200, REG)
        if len(path) == 3 and path[1:] == ['oauth', 'access_token']:
            return self._fb_token()
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
        if len(path) == 3 and path[1:] == ['me', 'accounts']:
            if self.headers.get('Authorization') != 'Bearer user-long':
                return self._json(401, {'error': {'message': 'Invalid OAuth access token.', 'code': 190}})
            return self._json(200, {'data': [
                {'id': 'PAGE1', 'name': 'CGCH Fan Page', 'access_token': 'page-token-PAGE1',
                 'instagram_business_account': {'id': 'IG1', 'username': 'cgch_ig'}},
                {'id': 'PAGE2', 'name': 'No Token Page'}]})
        if len(path) == 2 and path[1] == 'me' and self.headers.get('Authorization') == 'Bearer page-token-PAGE1':
            return self._json(200, {'id': 'PAGE1', 'name': 'CGCH Fan Page'})
        if len(path) == 3 and path[2] == 'conversations':
            from urllib.parse import parse_qs, urlparse
            if self.headers.get('Authorization') != 'Bearer page-token-PAGE1' or path[1] != 'PAGE1':
                return self._json(400, {'error': {'message': 'Invalid page', 'code': 100}})
            if not HIST['on']:
                return self._json(200, {'data': []})
            host = self.headers.get('Host')
            if parse_qs(urlparse(self.path).query).get('platform', [''])[0] == 'instagram':
                return self._json(200, {'data': [{'id': 't_ig1', 'participants': {'data': [{'id': 'IG1', 'username': 'cgch_ig'}, {'id': 'IGU9', 'username': 'ig_old_buyer'}]},
                    'messages': {'data': [
                        {'id': 'aWdfHIST2', 'created_time': '2026-10-20T09:05:00+0000', 'from': {'id': 'IG1'}, 'message': 'Sure, 500 pcs MOQ.'},
                        {'id': 'aWdfHIST1', 'created_time': '2026-10-20T09:00:00+0000', 'from': {'id': 'IGU9'}, 'message': 'MOQ?'}]}}]})
            return self._json(200, {'data': [{'id': 't_fb1', 'participants': {'data': [{'id': '7002', 'name': 'Old Buyer'}, {'id': 'PAGE1', 'name': 'CGCH Fan Page'}]},
                'messages': {'data': [
                    {'id': 'm_HIST3', 'created_time': '2026-10-20T08:10:00+0000', 'from': {'id': '7002'},
                     'attachments': {'data': [{'mime_type': 'image/jpeg', 'name': 'photo.jpg', 'image_data': {'url': f'http://{host}/_media/MEDIAH1'}}]}},
                    {'id': 'm_HIST2', 'created_time': '2026-10-20T08:05:00+0000', 'from': {'id': 'PAGE1'}, 'message': 'Yes, we ship to Spain.'},
                    {'id': 'm_HIST1', 'created_time': '2026-10-20T08:00:00+0000', 'from': {'id': '7002'}, 'message': 'Do you ship to Spain?'}]}}]})
        if len(path) == 2 and path[1] in PROFILES:
            return self._json(200, PROFILES[path[1]])
        if len(path) == 2:
            return self._json(200, {'id': path[1], 'display_phone_number': '+86 138 0000 0000',
                                    'verified_name': 'CGCH Test', 'quality_rating': 'GREEN'})
        self._json(404, {'error': {'message': 'Unknown path', 'code': 100}})

    def do_POST(self):
        path = self.path.split('?')[0].strip('/').split('/')
        length = int(self.headers.get('Content-Length') or 0)
        raw = self.rfile.read(length)
        if path and path[0] in ('yt', 'tt', '_yt', '_tt'):
            return self._social('POST', path, raw)
        if path[:1] == ['evo']:
            return self._evo('POST', path[1:], raw)
        if path == ['_evo', 'state']:  # 模拟手机确认 / 退出关联设备
            b = json.loads(raw or b'{}')
            EVO[b['instance']].update({k: v for k, v in b.items() if k != 'instance'})
            return self._json(200, {'ok': True})
        if path == ['_hist']:
            HIST['on'] = True
            return self._json(200, {'ok': True})
        if path == ['v1', 'chat', 'completions']:
            return self._llm(json.loads(raw or b'{}'))
        if path == ['v1', 'embeddings']:
            return self._embed(json.loads(raw or b'{}'))
        if path == ['search']:
            body = json.loads(raw or b'{}')
            if self.headers.get('Authorization') != 'Bearer search-key' and body.get('api_key') != 'search-key':
                return self._json(401, {'detail': {'error': 'Unauthorized: missing or invalid API key.'}})
            SEARCH.append(body)
            return self._json(200, {'query': body.get('query'), 'results': [
                {'title': 'Hair dryer wholesale price - Example B2B', 'url': 'https://b2b.example/hair-dryer',
                 'content': 'Wholesale hair dryers from verified suppliers: US$ 6.20 - 9.80 / piece, MOQ 500 pieces.'},
                {'title': 'Ionic hair dryer factory', 'url': 'https://factory.example/ionic',
                 'content': 'Ionic 1800W hair dryer, FOB price US$ 7.5 per unit for 1000 units.'}]})
        if len(path) == 3 and path[0] == 'oauth' and path[2] == 'token':  # 模拟 Google / Microsoft 令牌接口
            return self._oauth_token(path[1], raw)
        if not self._auth_ok():
            return
        if len(path) == 3 and path[1:] == ['me', 'messages']:
            return self._fb_send(raw)
        if len(path) == 3 and path[2] == 'subscribed_apps':
            from urllib.parse import parse_qs, urlparse
            SUBS.append({'page': path[1], 'fields': parse_qs(urlparse(self.path).query).get('subscribed_fields', [''])[0],
                         'auth': self.headers.get('Authorization')})
            return self._json(200, {'success': True})
        if len(path) == 3 and path[2] == 'register':
            REG.append(dict(json.loads(raw or b'{}'), pn=path[1], auth=self.headers.get('Authorization')))
            if path[1] == 'PN3':
                return self._json(400, {'error': {'message': 'Phone number is already registered', 'code': 133005}})
            return self._json(200, {'success': True})
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

    def _fb_token(self):
        from urllib.parse import parse_qs, urlparse
        q = {k: v[0] for k, v in parse_qs(urlparse(self.path).query).items()}
        OAUTH.append({'provider': 'meta', **{k: q.get(k) for k in ('grant_type', 'code', 'client_id', 'redirect_uri', 'fb_exchange_token')}})
        if q.get('client_secret') != 'metasec':
            return self._json(400, {'error': {'message': 'Error validating client secret.', 'code': 1}})
        if q.get('grant_type') == 'fb_exchange_token':
            if q.get('fb_exchange_token') != 'user-short':
                return self._json(400, {'error': {'message': 'Invalid token', 'code': 190}})
            return self._json(200, {'access_token': 'user-long', 'token_type': 'bearer', 'expires_in': 5184000})
        if q.get('code') == 'es-code':  # WhatsApp 嵌入式注册：换企业令牌
            return self._json(200, {'access_token': 'biz-token', 'token_type': 'bearer'})
        if q.get('code') != 'good-code':
            return self._json(400, {'error': {'message': 'This authorization code has expired.', 'code': 100}})
        return self._json(200, {'access_token': 'user-short', 'token_type': 'bearer', 'expires_in': 3600})

    def _fb_send(self, raw):
        auth = self.headers.get('Authorization')
        ctype = self.headers.get('Content-Type', '')
        if ctype.startswith('multipart/'):
            rec = {'multipart': True, 'auth': auth}
            for part in raw.split(b'--' + ctype.split('boundary=')[1].encode()):
                if b'name="' not in part:
                    continue
                name = part.split(b'name="')[1].split(b'"')[0].decode()
                value = part.split(b'\r\n\r\n', 1)[1].rsplit(b'\r\n', 1)[0]
                if b'filename="' in part:
                    rec['fileName'] = part.split(b'filename="')[1].split(b'"')[0].decode()
                    rec['size'] = len(value)
                else:
                    rec[name] = json.loads(value) if value[:1] in (b'{', b'[') else value.decode()
        else:
            rec = dict(json.loads(raw or b'{}'), auth=auth)
        FB_SENT.append(rec)
        rid = (rec.get('recipient') or {}).get('id')
        return self._json(200, {'recipient_id': rid, 'message_id': f'm_OUT{len(FB_SENT)}'})

    def _oauth_token(self, provider, raw):
        from urllib.parse import parse_qs
        f = {k: v[0] for k, v in parse_qs(raw.decode()).items()}
        OAUTH.append({'provider': provider, **{k: f.get(k) for k in ('grant_type', 'code', 'refresh_token', 'client_id', 'redirect_uri')}})
        if f.get('client_secret') not in ('gsec', 'msec'):
            return self._json(401, {'error': 'invalid_client', 'error_description': 'bad client secret'})
        n = len(OAUTH)
        if f.get('grant_type') == 'authorization_code':
            if f.get('code') != 'good-code':
                return self._json(400, {'error': 'invalid_grant', 'error_description': 'code expired'})
            body = {'access_token': f'ya29.mock-{n}', 'refresh_token': f'refresh-{provider}-1', 'expires_in': 1, 'token_type': 'Bearer'}
            if provider == 'microsoft':
                claims = base64.urlsafe_b64encode(json.dumps({'preferred_username': 'sales.outlook@cgch.test', 'name': 'Outlook Sales'}).encode()).decode().rstrip('=')
                body['id_token'] = 'eyJhbGciOiJub25lIn0.' + claims + '.'
            return self._json(200, body)
        if f.get('grant_type') == 'refresh_token':
            if not (f.get('refresh_token') or '').startswith('refresh-'):
                return self._json(400, {'error': 'invalid_grant'})
            body = {'access_token': f'ya29.mock-{n}', 'expires_in': 3600, 'token_type': 'Bearer'}
            if provider == 'microsoft':
                body['refresh_token'] = f'refresh-microsoft-{n}'  # Microsoft 会轮换刷新令牌
            return self._json(200, body)
        return self._json(400, {'error': 'unsupported_grant_type'})

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
        if '销售教练' in system:  # 成交案例总结（故意带上客户名和价格，验证会被脱敏）
            data = msgs[-1]['content'] if msgs else ''
            answer = json.dumps({
                'title': 'Acme Buyer 通过免费样品成交 2027 新款吹风机',
                'industry': '美国家电贸易商', 'product': '2027 lightweight hair dryer',
                'summary': '客户久未联系，收到新品介绍和免费样品后回复并下单。' if 'free samples' in data else '资料不足',
                'keyPoints': ['Offered free samples of the 2027 hair dryer line before Acme Buyer placed the order',
                              'Quoted USD 8.5 per unit with MOQ 500'],
                'objections': ['顾虑：起订量太高；回应：首单可按 500 台起订'],
                'phrases': ['Would you like us to send a sample this week?']}, ensure_ascii=False)
        elif '背调分析师' in system:  # 深度背调报告
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
        elif '专业的外贸翻译' in system:  # 翻译：每项前面加上目标语言标记
            items = json.loads(msgs[-1]['content'])
            tag = '[ZH]' if '翻译成中文' in system else '[XX]'
            answer = json.dumps([f'{tag} {t}' for t in items], ensure_ascii=False)
        elif '可以发给客户的资料文件' in system and 'catalog' in last:  # AI 发送文件
            import re as _re
            ids = _re.findall(r'编号 (\d+)：', system)
            answer = 'Sure, please find our latest catalog attached.' + (f'\n[SEND_FILE:{ids[0]}]' if ids else '')
        elif '公开市场参考价' in system and 'price' in last:  # 允许联网报价
            answer = ('The market reference price is about US$ 6.20 - 9.80 per piece; our formal quotation depends on quantity and specs.'
                      if 'US$ 6.20 - 9.80' in system else 'I will confirm the price and get back to you.')
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
