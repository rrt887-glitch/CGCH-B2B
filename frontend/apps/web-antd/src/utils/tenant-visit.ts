/**
 * 平台运营「进入客户后台」的前端状态。
 * 进入后请求头带上 visit-tenant-id（见 api/request.ts），后端会检查这次进入是否已登记且未过期；
 * 这里只额外记住客户名称和有效期，用来在页面顶部提示「正在查看哪家客户」。
 */
import { useAccessStore } from '@vben/stores';

const KEY = 'cgch-visit-tenant';

export interface VisitInfo {
  tenantId: number;
  tenantName: string;
  expireTime: number;
}

export function getVisitInfo(): null | VisitInfo {
  const accessStore = useAccessStore();
  if (!accessStore.visitTenantId) return null;
  try {
    const v = JSON.parse(localStorage.getItem(KEY) || 'null') as null | VisitInfo;
    if (v && v.tenantId === accessStore.visitTenantId) return v;
  } catch {
    // 忽略损坏的缓存
  }
  return { tenantId: accessStore.visitTenantId, tenantName: `客户 #${accessStore.visitTenantId}`, expireTime: 0 };
}

export function startVisit(info: VisitInfo) {
  localStorage.setItem(KEY, JSON.stringify(info));
  useAccessStore().setVisitTenantId(info.tenantId);
}

export function endVisit() {
  localStorage.removeItem(KEY);
  useAccessStore().setVisitTenantId(null);
}
