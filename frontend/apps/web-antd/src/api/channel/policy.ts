import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace ChannelPolicyApi {
  export interface Policy {
    mailHourlyLimit: number;
    mailDailyLimit: number;
    waHourlyLimit: number;
    waDailyLimit: number;
    applyToManual: boolean;
    autoUnsubscribe: boolean;
    autoBounce: boolean;
    unsubscribeFooter: boolean;
    footerText: string;
  }

  export interface Usage {
    channel: 'mail' | 'whatsapp';
    accountId: number;
    name: string;
    userId: number;
    hour: number;
    day: number;
  }

  export interface Suppression {
    id: number;
    channel: 'mail' | 'whatsapp';
    value: string;
    reason: 'bounce' | 'manual' | 'unsubscribe';
    remark?: string;
    createTime: string;
  }
}

export const getSendPolicy = () => requestClient.get<ChannelPolicyApi.Policy>('/channel/send-policy/get');
export const saveSendPolicy = (data: ChannelPolicyApi.Policy) => requestClient.put('/channel/send-policy/save', data);
export const getSendUsage = () => requestClient.get<ChannelPolicyApi.Usage[]>('/channel/send-policy/usage');
export const getSuppressionPage = (params: PageParam & { channel?: string; keyword?: string; reason?: string }) =>
  requestClient.get<PageResult<ChannelPolicyApi.Suppression>>('/channel/suppression/page', { params });
export const createSuppression = (data: { channel: string; remark?: string; value: string }) =>
  requestClient.post<number>('/channel/suppression/create', data);
export const deleteSuppression = (id: number) => requestClient.delete(`/channel/suppression/delete?id=${id}`);
