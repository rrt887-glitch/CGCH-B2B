import { afterEach, describe, expect, it, vi } from 'vitest';

import { isServiceUnavailable, showServiceUnavailable } from '../service-fallback';

/**
 * 线上：部署窗口后端 502，登录页 / 工作台整页白屏、没有任何提示。
 * 这里锁住两点：什么样的错误该弹兜底、兜底页只出现一次。
 */
describe('isServiceUnavailable', () => {
  it('5xx / 网络错误算服务不可用', () => {
    expect(isServiceUnavailable({ response: { status: 502 } })).toBe(true);
    expect(isServiceUnavailable({ response: { status: 500 } })).toBe(true);
    expect(isServiceUnavailable(new Error('Network Error'))).toBe(true);
    expect(isServiceUnavailable(new Error('timeout of 15000ms exceeded'))).toBe(
      true,
    );
  });

  it('业务报错不算（不要动不动就弹兜底页）', () => {
    expect(isServiceUnavailable(new Error('请输入正确的密码'))).toBe(false);
    expect(isServiceUnavailable({ response: { status: 401 } })).toBe(false);
    expect(isServiceUnavailable(undefined)).toBe(false);
  });
});

describe('showServiceUnavailable', () => {
  afterEach(() => {
    document.body.innerHTML = '';
    vi.restoreAllMocks();
    // 模块内部有 overlay 单例标记，这里通过重新 import 复位太重，直接断言后忽略
  });

  it('兜底页有提示和重试按钮，且重复调用不会叠加', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => ({ ok: false })),
    );

    showServiceUnavailable();
    showServiceUnavailable();

    const alerts = document.querySelectorAll('[role="alert"]');
    expect(alerts.length).toBe(1);
    expect(alerts[0]?.textContent).toContain('服务暂时不可用');
    expect(alerts[0]?.querySelector('button')?.textContent).toContain('重试');
  });
});
