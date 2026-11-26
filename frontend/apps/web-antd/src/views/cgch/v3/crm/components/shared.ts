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

/** 客户阶段字典（新询盘 → 报价 → 样品 → 谈判 → 成交 → 复购），可在「系统 → 字典」里增改 */
export const STAGE_DICT = 'crm_customer_stage';
export const stageOptions = () => getDictOptions(STAGE_DICT, 'number');
export const stageLabel = (v?: number) => (v === undefined || v === null ? '-' : getDictLabel(STAGE_DICT, v) || '-');

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

/** 苹果通讯录风格 8 色渐变单字头像（CRM 客户 / 线索通用） */
const CRM_AVATAR_GRADIENTS = [
  'linear-gradient(180deg, #3395FF 0%, #007AFF 100%)',
  'linear-gradient(180deg, #FFAB2E 0%, #FF9500 100%)',
  'linear-gradient(180deg, #4CD964 0%, #28CD41 100%)',
  'linear-gradient(180deg, #C678F0 0%, #AF52DE 100%)',
  'linear-gradient(180deg, #7472E8 0%, #5856D6 100%)',
  'linear-gradient(180deg, #2AD4CB 0%, #00B5AD 100%)',
  'linear-gradient(180deg, #FF6259 0%, #FF3B30 100%)',
  'linear-gradient(180deg, #FF5C85 0%, #FF2D55 100%)',
];

export function crmInitial(name?: string) {
  const s = (name || '?').trim().replace(/^[+\s<"']+/, '');
  return (s.slice(0, 1) || '?').toUpperCase();
}

export function crmAvatarBg(seedStr?: number | string) {
  const s = String(seedStr || '');
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0;
  return CRM_AVATAR_GRADIENTS[h % CRM_AVATAR_GRADIENTS.length]!;
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

/**
 * 取全部数据：后端每页最多 200 条（不接受 pageSize = -1），这里逐页取完再合并；最多取 maxPages 页。
 */
export async function fetchAll<T>(
  fn: (p: Record<string, any>) => Promise<{ list: T[]; total: number } | undefined>,
  params: Record<string, any> = {},
  maxPages = 50,
): Promise<T[]> {
  const out: T[] = [];
  for (let pageNo = 1; pageNo <= maxPages; pageNo++) {
    const r = await fn({ ...params, pageNo, pageSize: 200 });
    const list = r?.list ?? [];
    out.push(...list);
    if (list.length < 200 || out.length >= (r?.total ?? 0)) break;
  }
  return out;
}
