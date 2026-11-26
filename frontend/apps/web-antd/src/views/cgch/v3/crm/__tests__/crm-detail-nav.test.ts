import { describe, expect, it, vi } from 'vitest';

import { customerDetailPath, openCustomerDetail } from '../crm-detail-nav';

/**
 * 线上问题：点客户名称后页面留在日程列表，两次都没反应。
 * 原因是 router.push 发了不管。这里锁住三种情况：正常跳转、推送失败兜底、被守卫改写兜底。
 */
function fakeRouter(path = '/v3/crm') {
  const state = { currentRoute: { value: { path } } };
  const push = vi.fn(async (to: string) => {
    state.currentRoute.value.path = to;
  });
  return { router: { ...state, push }, state };
}

describe('openCustomerDetail', () => {
  it('正常跳转时不兜底', async () => {
    const { router } = fakeRouter();
    const fallback = vi.fn();

    const result = await openCustomerDetail(router, 42, fallback);

    expect(result).toBe('pushed');
    expect(router.push).toHaveBeenCalledWith('/crm/customer/detail/42');
    expect(fallback).not.toHaveBeenCalled();
  });

  it('push 抛错时用整页打开兜底', async () => {
    const router = {
      currentRoute: { value: { path: '/v3/crm' } },
      push: vi.fn(async () => {
        throw new Error('No match for route');
      }),
    };
    const fallback = vi.fn();

    const result = await openCustomerDetail(router, 7, fallback);

    expect(result).toBe('fallback');
    expect(fallback).toHaveBeenCalledWith('/crm/customer/detail/7');
  });

  it('路由被守卫改写回列表时也兜底', async () => {
    const router = {
      currentRoute: { value: { path: '/v3/crm' } },
      // 假装 push 成功但实际没离开列表
      push: vi.fn(async () => undefined),
    };
    const fallback = vi.fn();

    const result = await openCustomerDetail(router, 9, fallback);

    expect(result).toBe('fallback');
    expect(fallback).toHaveBeenCalledWith('/crm/customer/detail/9');
  });

  it('详情地址按客户 id 生成', () => {
    expect(customerDetailPath(123)).toBe('/crm/customer/detail/123');
  });
});
