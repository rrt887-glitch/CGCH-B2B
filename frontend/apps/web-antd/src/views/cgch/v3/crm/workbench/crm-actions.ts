/**
 * CRM 工作台统一业务动作封装：
 * 1. 安全构建 CrmCustomerSaveReqVO（自动规避国内 @Mobile/@Telephone/@Email 格式校验陷阱）
 * 2. 4 格「📌 商务黑板」无损读写持久化
 * 3. 星标关注、AI 托管开关、5 秒极速写跟进统一调用
 */
import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { useUserStore } from '@vben/stores';

import dayjs from 'dayjs';

import { updateCustomer } from '#/api/crm/customer';
import { createFollowUpRecord } from '#/api/crm/followup';
import { BizTypeEnum } from '#/api/crm/permission';
import { requestClient } from '#/api/request';

import type { DealBoard } from './deal-meta';
import { parseDealBoard, serializeDealBoard } from './deal-meta';

const CN_MOBILE_RE = /^1[3-9]\d{9}$/;
const CN_TEL_RE = /^(\d{3,4}-?)?\d{7,8}$|^1[3-9]\d{9}$/;

/**
 * 组装后端 CrmCustomerSaveReqVO 安全载荷：
 * - 海外号码统一落入 whatsapp 字段，避免触发国内 @Mobile / @Telephone 校验报错
 * - 始终保留 ownerUserId、tagIds、groupId、aiHosted 等已有字段，防止局部编辑冲掉数据
 */
export function buildSafeCustomerPayload(
  base: Record<string, any>,
  patch: Record<string, any> = {},
  fallbackOwnerId?: number,
): Record<string, any> {
  const userStore = useUserStore();
  const merged = { ...base, ...patch };
  const ownerUserId =
    merged.ownerUserId ?? fallbackOwnerId ?? userStore.userInfo?.id ?? 1;

  const rawMobile = String(merged.mobile ?? '').trim();
  const rawTel = String(merged.telephone ?? '').trim();
  const rawWhatsapp = String(merged.whatsapp ?? '').trim();
  const rawEmail = String(merged.email ?? '').trim();

  // 若用户在电话框填了海外号码（非国内格式），自动归入 whatsapp
  let whatsapp = rawWhatsapp;
  const mobile = CN_MOBILE_RE.test(rawMobile) ? rawMobile : undefined;
  const telephone = CN_TEL_RE.test(rawTel) ? rawTel : undefined;
  if (!whatsapp && rawMobile && !mobile) whatsapp = rawMobile;
  if (!whatsapp && rawTel && !telephone) whatsapp = rawTel;

  let contactNextTime = merged.contactNextTime;
  if (contactNextTime) {
    const d = dayjs(contactNextTime);
    contactNextTime = d.isValid() ? d.valueOf() : undefined;
  } else {
    contactNextTime = undefined;
  }

  return {
    id: merged.id,
    name: String(merged.name || merged.companyName || '未命名客户').trim(),
    ownerUserId,
    companyName: merged.companyName ? String(merged.companyName).trim() : undefined,
    countryCode: merged.countryCode ? String(merged.countryCode).trim().toUpperCase() : undefined,
    email: rawEmail && rawEmail.includes('@') ? rawEmail : undefined,
    whatsapp: whatsapp || undefined,
    mobile,
    telephone,
    website: merged.website ? String(merged.website).trim() : undefined,
    detailAddress: merged.detailAddress ? String(merged.detailAddress).trim() : undefined,
    areaId: merged.areaId ?? undefined,
    industryId: merged.industryId ?? undefined,
    level: merged.level ?? undefined,
    source: merged.source ?? undefined,
    groupId: merged.groupId ?? undefined,
    tagIds: Array.isArray(merged.tagIds) ? merged.tagIds : undefined,
    aiHosted: merged.aiHosted ?? undefined,
    contactNextTime,
    remark: merged.remark ?? '',
  };
}

/** 更新客户 4 格商务黑板（合并写回 customer.remark） */
export async function saveCustomerDealBoard(
  customer: Record<string, any>,
  boardPatch: Partial<DealBoard>,
): Promise<string> {
  const current = parseDealBoard(customer.remark);
  const nextBoard: DealBoard = {
    ...current,
    ...boardPatch,
  };
  const nextRemark = serializeDealBoard(nextBoard, current.collisionHint);
  const payload = buildSafeCustomerPayload(customer, { remark: nextRemark });
  await updateCustomer(payload as any);
  customer.remark = nextRemark;
  return nextRemark;
}

/** 切换星标重点关注 */
export async function toggleCustomerStarred(
  id: number,
  starred: boolean,
): Promise<boolean> {
  return requestClient.put<boolean>(
    `/crm/customer/update-starred?id=${id}&starred=${starred}`,
  );
}

/** 5 秒极速记录客户跟进（自动补齐默认跟进方式字典与下次联系毫秒时间戳） */
export async function quickCreateFollowUp(params: {
  content: string;
  customerId: number;
  nextTime?: Date | number | string;
  type?: number;
}) {
  const dictOpts = getDictOptions(DICT_TYPE.CRM_FOLLOW_UP_TYPE, 'number');
  const defaultType = Number(dictOpts?.[0]?.value ?? 1);
  const nextMoment = params.nextTime
    ? dayjs(params.nextTime)
    : dayjs().add(3, 'day').hour(10).minute(0).second(0);

  return createFollowUpRecord({
    bizType: BizTypeEnum.CRM_CUSTOMER,
    bizId: params.customerId,
    type: params.type ?? defaultType,
    content: params.content.trim(),
    nextTime: (nextMoment.isValid()
      ? nextMoment.valueOf()
      : dayjs().add(3, 'day').valueOf()) as any,
    picUrls: [],
    fileUrls: [],
    businessIds: [],
    contactIds: [],
  } as any);
}
