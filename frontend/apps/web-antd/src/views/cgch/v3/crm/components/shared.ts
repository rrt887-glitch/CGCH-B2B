import type { RailItem } from './types';

import { DICT_TYPE } from '@vben/constants';
import { getDictLabel, getDictOptions } from '@vben/hooks';

import dayjs from 'dayjs';

export { countryName } from '#/views/cgch/crm/shared/countries';

/** 字典项变成筛选栏的子项 */
export function dictRail(dictType: string, field: string, prefix: string): RailItem[] {
  return getDictOptions(dictType, 'number').map((o: any) => ({
    key: `${prefix}-${o.value}`,
    label: String(o.label),
    filter: { [field]: o.value },
  }));
}

export const sourceRail = (prefix = 'source') => dictRail(DICT_TYPE.CRM_CUSTOMER_SOURCE, 'source', prefix);
export const levelRail = (prefix = 'level') => dictRail(DICT_TYPE.CRM_CUSTOMER_LEVEL, 'level', prefix);

export const sourceLabel = (v?: number) => (v === undefined || v === null ? '-' : getDictLabel(DICT_TYPE.CRM_CUSTOMER_SOURCE, v) || '-');
export const levelLabel = (v?: number) => (v === undefined || v === null ? '-' : getDictLabel(DICT_TYPE.CRM_CUSTOMER_LEVEL, v) || '-');
export const industryLabel = (v?: number) => (v === undefined || v === null ? '-' : getDictLabel(DICT_TYPE.CRM_CUSTOMER_INDUSTRY, v) || '-');

/** 时间显示为 2026-10-23 14:05；空值显示 - */
export function fmtTime(v?: any) {
  if (!v) return '-';
  const d = dayjs(v);
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm') : '-';
}

export function fmtMoney(v?: number) {
  return `¥${Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

/** 渠道状态：这条记录能通过哪些渠道联系 */
export function channelTags(r: { email?: string; whatsapp?: string }) {
  const tags: string[] = [];
  if (r.whatsapp) tags.push('WhatsApp');
  if (r.email) tags.push('邮箱');
  return tags;
}

/** 后端分页接口统一包装成 { list, total } */
export async function page<T>(p: Promise<{ list: T[]; total: number }>) {
  const r = await p;
  return { list: r?.list ?? [], total: r?.total ?? 0 };
}
