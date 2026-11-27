/**
 * 后端不可用时的兜底页面。
 *
 * 线上事故：部署窗口里 admin-api 全线 502 约 2 分钟，此时登录页 / 工作台**整页白屏**，
 * body 里只剩顶部细条和转圈，没有任何错误提示，用户只能干等或反复刷新。
 *
 * 做法：
 * 1. 路由导航失败、未捕获的前端异常、5xx / 网络错误 → 显示全屏提示（服务暂时不可用）；
 * 2. 每 5 秒探测一次后端公开接口，恢复了自动刷新页面，用户不需要手动试。
 */

import type { App } from 'vue';

const PROBE_URL = '/admin-api/public/site/about';
const PROBE_INTERVAL = 5000;

let overlay: HTMLElement | undefined;
let timer: ReturnType<typeof setInterval> | undefined;

function createOverlay() {
  const wrap = document.createElement('div');
  wrap.setAttribute('role', 'alert');
  wrap.style.cssText = [
    'position:fixed',
    'inset:0',
    'z-index:2147483000',
    'display:flex',
    'flex-direction:column',
    'align-items:center',
    'justify-content:center',
    'gap:10px',
    'padding:24px',
    'background:#f5f5f7',
    'font:14px/1.6 -apple-system,BlinkMacSystemFont,"PingFang SC","Microsoft YaHei",sans-serif',
    'color:#1d1d1f',
    'text-align:center',
  ].join(';');

  const title = document.createElement('div');
  title.textContent = '服务暂时不可用';
  title.style.cssText = 'font-size:20px;font-weight:600';

  const desc = document.createElement('div');
  desc.textContent =
    '系统正在更新或后端暂时没有响应。页面会在服务恢复后自动刷新，也可以点下面的按钮手动重试。';
  desc.style.cssText = 'max-width:520px;color:#6e6e73';

  const btn = document.createElement('button');
  btn.type = 'button';
  btn.textContent = '立即重试';
  btn.style.cssText = [
    'margin-top:8px',
    'padding:8px 20px',
    'font-size:14px',
    'color:#fff',
    'background:#0071e3',
    'border:0',
    'border-radius:10px',
    'cursor:pointer',
  ].join(';');
  btn.addEventListener('click', () => window.location.reload());

  wrap.append(title, desc, btn);
  return wrap;
}

async function probeAndReload() {
  try {
    const res = await fetch(PROBE_URL, { cache: 'no-store' });
    if (res.ok) {
      if (timer) clearInterval(timer);
      timer = undefined;
      window.location.reload();
    }
  } catch {
    // 还没恢复，等下一轮
  }
}

/** 展示兜底页（幂等：重复调用不会叠多个） */
export function showServiceUnavailable() {
  if (typeof document === 'undefined' || overlay) return;
  overlay = createOverlay();
  document.body.append(overlay);
  timer = setInterval(probeAndReload, PROBE_INTERVAL);
  void probeAndReload();
}

/** 判断错误是否"后端不可用"这类，避免把表单校验之类的错也弹成兜底页 */
export function isServiceUnavailable(error: unknown): boolean {
  if (!error) return false;
  const anyErr = error as any;
  const status = anyErr?.response?.status ?? anyErr?.statusCode;
  if (typeof status === 'number' && status >= 500) return true;
  const message = String(anyErr?.message ?? anyErr ?? '');
  return /Network Error|Failed to fetch|timeout of|服务异常|502|503|504|Bad Gateway|Service Unavailable/i.test(
    message,
  );
}

/** 在 app 上挂全局兜底；bootstrap 里调用一次 */
export function setupServiceFallback(app: App) {
  const original = app.config.errorHandler;
  app.config.errorHandler = (err, instance, info) => {
    if (isServiceUnavailable(err)) showServiceUnavailable();
    if (typeof original === 'function') original(err, instance, info);
    else console.error(err, info);
  };

  window.addEventListener('unhandledrejection', (event) => {
    if (isServiceUnavailable(event.reason)) {
      event.preventDefault();
      showServiceUnavailable();
    }
  });
}
