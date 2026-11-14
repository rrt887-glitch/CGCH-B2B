import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

/** 平台运营「进入客户后台」记录 */
export interface TenantVisitLog {
  id: number;
  userId: number;
  username?: string;
  nickname?: string;
  visitTenantId: number;
  visitTenantName?: string;
  reason: string;
  ip?: string;
  enterTime: number | string;
  expireTime: number | string;
  exitTime?: number | string;
}

export interface TenantVisitEnterResult {
  tenantId: number;
  tenantName: string;
  expireTime: number | string;
}

/** 平台客户管理列表里的一行 */
export interface TenantConsoleRow {
  id: number;
  name: string;
  contactName?: string;
  contactMobile?: string;
  status: number;
  packageId: number;
  packageName?: string;
  expireTime: number | string;
  accountCount: number;
  usedAccountCount?: number;
  adminUsername?: string;
  adminNickname?: string;
  platform?: boolean;
  createTime?: number | string;
}

// 这些接口都在平台自己的公司下调用，不带「进入客户后台」的请求头
const PLATFORM = { headers: { isVisitTenant: false } };

/** 客户管理列表 */
export const getTenantConsolePage = (params: PageParam & { name?: string; status?: number }) =>
  requestClient.get<PageResult<TenantConsoleRow>>('/system/tenant/console-page', { params, ...PLATFORM });

/** 开户：创建客户和它的管理员账号 */
export const createTenant = (data: any) => requestClient.post<number>('/system/tenant/create', data, PLATFORM);

/** 修改客户：账号数、到期时间、套餐、启用 / 停用 */
export const updateTenant = (data: any) => requestClient.put('/system/tenant/update', data, PLATFORM);

/** 套餐列表 */
export const getTenantPackageSimpleList = () =>
  requestClient.get<{ id: number; name: string }[]>('/system/tenant-package/get-simple-list', PLATFORM);

/** 进入客户后台：登记原因，返回有效期 */
export const enterTenant = (tenantId: number, reason: string) =>
  requestClient.post<TenantVisitEnterResult>('/system/tenant-visit/enter', { tenantId, reason }, PLATFORM);

/** 退出客户后台 */
export const exitTenant = (tenantId: number) =>
  requestClient.post(`/system/tenant-visit/exit?tenantId=${tenantId}`, {}, PLATFORM);

/** 进入记录（平台运营） */
export const getVisitLogPage = (params: PageParam & { visitTenantId?: number }) =>
  requestClient.get<PageResult<TenantVisitLog>>('/system/tenant-visit/page', { params, ...PLATFORM });

/** 平台进入过本公司后台的记录（客户管理员查看） */
export const getMyVisitLogPage = (params: PageParam) =>
  requestClient.get<PageResult<TenantVisitLog>>('/system/tenant-visit/my-page', { params });
