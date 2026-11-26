/**
 * 打开 CRM 详情页（客户 / 线索）。
 *
 * 线上出现过：客户名、线索名是**没有 href 的 `<a>`**，键盘用户 Tab 十几下都到不了，
 * 只能绕到行尾点「查看」；而「跟进日程」里点客户名则因为 router.push 发了不管，
 * 路由没解析成功时就静默什么都不发生。
 *
 * 统一做法：给出真实的 href（可键盘聚焦、可中键新开、浏览器默认导航兜底），
 * 左键仍走 SPA；推送后校验当前路由，没换页就用整页跳转兜底。
 */
export type CrmDetailKind = 'clue' | 'customer';

export interface DetailRouterLike {
  currentRoute: { value: { path: string } };
  push: (to: string) => Promise<unknown>;
}

export function crmDetailPath(kind: CrmDetailKind, id: number | string) {
  return kind === 'clue'
    ? `/crm/clue/detail/${id}`
    : `/crm/customer/detail/${id}`;
}

export async function openCrmDetail(
  router: DetailRouterLike,
  href: string,
  fallback: (href: string) => void,
): Promise<'fallback' | 'pushed'> {
  try {
    await router.push(href);
  } catch {
    fallback(href);
    return 'fallback';
  }
  // 路由守卫可能把地址改写回列表页，这时也要兜底，否则还是“点了没反应”
  if (router.currentRoute.value.path !== href) {
    fallback(href);
    return 'fallback';
  }
  return 'pushed';
}

/** 兼容既有调用（跟进日程） */
export const customerDetailPath = (id: number | string) =>
  crmDetailPath('customer', id);

export const openCustomerDetail = (
  router: DetailRouterLike,
  id: number | string,
  fallback: (href: string) => void,
) => openCrmDetail(router, customerDetailPath(id), fallback);
