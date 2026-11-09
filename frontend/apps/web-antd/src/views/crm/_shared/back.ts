import type { Router } from 'vue-router';

/**
 * CRM 详情页「返回」：回到进入详情前的页面（新版 CRM 列表，保留原来的筛选和搜索）；
 * 直接打开详情、没有来源页时，回到新版 CRM 对应的列表。
 */
export function crmBackTarget(router: Router, tab: string) {
  const back = typeof window === 'undefined' ? undefined : (window.history.state?.back as string | undefined);
  if (back && !back.includes('/detail')) {
    return back;
  }
  return router.resolve({ name: 'CgchV3Crm', query: { tab } }).fullPath;
}
