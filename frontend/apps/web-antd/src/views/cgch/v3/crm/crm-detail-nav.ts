/**
 * 打开客户详情。
 *
 * 线上出现过：从「跟进日程 → 已逾期」点客户名称，点了两次页面都留在日程列表，
 * 浏览器也没有新标签页——原来的写法是 `router.push({ name })` 发了不管，
 * 一旦路由没解析成功（名称缺失、被守卫改写、部署版本不一致）就静默什么都不发生。
 *
 * 这里改成：推送后校验当前路由是否真的停在详情页，不成功就用整页跳转兜底，
 * 保证「点客户名能看到详情」这件事一定发生。
 */
export interface DetailRouterLike {
  currentRoute: { value: { path: string } };
  push: (to: string) => Promise<unknown>;
}

export function customerDetailPath(id: number | string) {
  return `/crm/customer/detail/${id}`;
}

export async function openCustomerDetail(
  router: DetailRouterLike,
  id: number | string,
  fallback: (href: string) => void,
): Promise<'fallback' | 'pushed'> {
  const href = customerDetailPath(id);
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
