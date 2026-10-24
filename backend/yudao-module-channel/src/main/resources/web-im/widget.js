/*
 * 草根出海 · 官网 IM 聊天窗口。嵌入方式：
 *   <script src="https://你的域名/admin-api/channel/web-im/widget.js?k=站点密钥" async></script>
 * 右下角显示聊天按钮；访客消息进入「会话」工作台，由 AI 或业务员回复。窗口每隔几秒拉取一次新消息。
 */
(function () {
  'use strict';
  var script = document.currentScript || (function () {
    var all = document.querySelectorAll('script[src*="/channel/web-im/widget.js"]');
    return all[all.length - 1];
  })();
  if (!script || window.__cgchWebIm) return;
  window.__cgchWebIm = true;
  var src = script.src;
  var base = src.replace(/\/widget\.js.*$/, '');
  var key = (src.match(/[?&]k=([^&]+)/) || [])[1];
  if (!key) return;
  key = decodeURIComponent(key);

  function store(k, v) {
    try {
      if (v === undefined) return localStorage.getItem(k);
      localStorage.setItem(k, v);
    } catch (e) { /* 隐私模式下不可用 */ }
    return null;
  }
  var vKey = 'cgch_im_v_' + key;
  var visitor = store(vKey);
  if (!visitor || !/^[A-Za-z0-9_-]{8,64}$/.test(visitor)) {
    visitor = 'v';
    var chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
    var rnd = new Uint8Array(24);
    (window.crypto || window.msCrypto).getRandomValues(rnd);
    for (var i = 0; i < rnd.length; i++) visitor += chars[rnd[i] % chars.length];
    store(vKey, visitor);
  }

  function api(method, path, body) {
    return fetch(base + path, {
      method: method,
      headers: body ? { 'Content-Type': 'application/json' } : {},
      body: body ? JSON.stringify(body) : undefined,
      credentials: 'omit'
    }).then(function (r) { return r.json(); }).then(function (r) {
      if (r.code !== 0) throw new Error(r.msg || 'error');
      return r.data;
    });
  }

  var host = document.createElement('div');
  host.style.cssText = 'position:fixed;right:20px;bottom:20px;z-index:2147483000;';
  var root = host.attachShadow ? host.attachShadow({ mode: 'open' }) : host;
  root.innerHTML =
    '<style>' +
    '*{box-sizing:border-box;font-family:-apple-system,"Segoe UI",Roboto,Arial,sans-serif}' +
    '.btn{width:56px;height:56px;border-radius:50%;border:0;background:#155dfc;color:#fff;cursor:pointer;box-shadow:0 6px 20px rgba(21,93,252,.35);display:flex;align-items:center;justify-content:center;position:relative}' +
    '.btn svg{width:26px;height:26px}' +
    '.dot{position:absolute;top:2px;right:2px;min-width:18px;height:18px;padding:0 5px;border-radius:9px;background:#ef4444;color:#fff;font-size:11px;line-height:18px;text-align:center;display:none}' +
    '.box{position:absolute;right:0;bottom:70px;width:360px;max-width:calc(100vw - 40px);height:520px;max-height:calc(100vh - 110px);background:#fff;border-radius:14px;box-shadow:0 12px 40px rgba(0,0,0,.18);display:none;flex-direction:column;overflow:hidden}' +
    '.box.on{display:flex}' +
    '.hd{padding:16px 18px;background:#155dfc;color:#fff}' +
    '.hd b{display:block;font-size:16px}.hd span{font-size:12px;opacity:.85}' +
    '.x{position:absolute;right:12px;top:12px;background:none;border:0;color:#fff;font-size:22px;cursor:pointer;line-height:1}' +
    '.list{flex:1;overflow-y:auto;padding:14px;background:#f6f7f9}' +
    '.m{max-width:80%;margin:0 0 10px;padding:8px 12px;border-radius:12px;font-size:14px;line-height:1.5;white-space:pre-wrap;word-break:break-word}' +
    '.m.a{background:#fff;color:#1f2937;border:1px solid #eceef3;border-top-left-radius:4px}' +
    '.m.v{background:#155dfc;color:#fff;margin-left:auto;border-top-right-radius:4px}' +
    '.m a{color:inherit;text-decoration:underline}.m img{max-width:100%;border-radius:8px;display:block}' +
    '.ft{display:flex;gap:8px;padding:10px;border-top:1px solid #eceef3;background:#fff}' +
    'textarea{flex:1;resize:none;height:40px;max-height:110px;padding:9px 10px;border:1px solid #e5e7eb;border-radius:10px;font-size:14px;outline:none}' +
    'textarea:focus{border-color:#155dfc}' +
    '.send{border:0;background:#155dfc;color:#fff;border-radius:10px;padding:0 14px;cursor:pointer;font-size:14px}' +
    '.send:disabled{opacity:.5;cursor:default}' +
    '.pw{text-align:center;font-size:11px;color:#9ca3af;padding:0 0 8px;background:#fff}' +
    '</style>' +
    '<div class="box"><div class="hd"><b class="title">Chat with us</b><span>We usually reply within minutes</span><button class="x" aria-label="Close">&times;</button></div>' +
    '<div class="list"></div>' +
    '<div class="ft"><textarea placeholder="Type a message…" maxlength="2000"></textarea><button class="send">Send</button></div>' +
    '<div class="pw">Powered by AI B2B</div></div>' +
    '<button class="btn" aria-label="Chat"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg><span class="dot"></span></button>';

  var $ = function (s) { return root.querySelector(s); };
  var box = $('.box'), list = $('.list'), input = $('textarea'), sendBtn = $('.send'), dot = $('.dot');
  var lastId = 0, open = false, unread = 0, seen = {}, timer = null, greeted = false, initial = true;

  function add(m) {
    if (m.id && seen[m.id]) return;
    if (m.id) seen[m.id] = 1;
    if (m.id && m.id > lastId) lastId = m.id;
    var el = document.createElement('div');
    el.className = 'm ' + (m.from === 'visitor' ? 'v' : 'a');
    if (m.fileUrl) {
      if (m.type === 'image') {
        var img = document.createElement('img');
        img.src = m.fileUrl;
        img.alt = m.fileName || '';
        el.appendChild(img);
      } else {
        var a = document.createElement('a');
        a.href = m.fileUrl;
        a.target = '_blank';
        a.rel = 'noopener';
        a.textContent = '📎 ' + (m.fileName || 'File');
        el.appendChild(a);
      }
      if (m.text) {
        var t = document.createElement('div');
        t.textContent = m.text;
        el.appendChild(t);
      }
    } else {
      el.textContent = m.text || '';
    }
    list.appendChild(el);
    list.scrollTop = list.scrollHeight;
    if (!open && !initial && m.from !== 'visitor' && m.id) {
      unread++;
      dot.textContent = unread > 9 ? '9+' : String(unread);
      dot.style.display = 'block';
    }
  }

  function greet() {
    if (greeted) return;
    greeted = true;
    add({ from: 'agent', text: 'Hi there 👋 How can we help you today?' });
  }

  function poll() {
    return api('GET', '/messages?k=' + encodeURIComponent(key) + '&v=' + visitor + '&after=' + lastId)
      .then(function (ms) { (ms || []).forEach(add); initial = false; })
      .catch(function () { /* 网络波动，下次再拉 */ });
  }

  function schedule() {
    clearTimeout(timer);
    timer = setTimeout(function () { poll().then(schedule); }, open ? 3000 : 15000);
  }

  function toggle(v) {
    open = v;
    box.classList.toggle('on', v);
    if (v) {
      unread = 0;
      dot.style.display = 'none';
      greet();
      setTimeout(function () { input.focus(); }, 50);
      poll();
    }
    schedule();
  }

  function send() {
    var text = input.value.trim();
    if (!text) return;
    sendBtn.disabled = true;
    api('POST', '/send', { k: key, visitorId: visitor, text: text, page: location.href })
      .then(function (m) {
        input.value = '';
        add(m);
        schedule();
      })
      .catch(function (e) { add({ from: 'agent', text: '⚠ ' + e.message }); })
      .then(function () { sendBtn.disabled = false; });
  }

  $('.btn').addEventListener('click', function () { toggle(!open); });
  $('.x').addEventListener('click', function () { toggle(false); });
  sendBtn.addEventListener('click', send);
  input.addEventListener('keydown', function (e) {
    if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
      e.preventDefault();
      send();
    }
  });

  api('GET', '/config?k=' + encodeURIComponent(key)).then(function (c) {
    if (c && c.name) $('.title').textContent = c.name;
    (document.body || document.documentElement).appendChild(host);
    poll().then(schedule);
  }).catch(function () { /* 站点未绑定或已停用时不显示 */ });
})();
