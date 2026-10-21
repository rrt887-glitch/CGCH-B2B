import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace ChannelReactivateApi {
  export interface Candidate {
    id: number;
    name: string;
    companyName?: string;
    countryCode?: string;
    ownerUserId?: number;
    email?: string;
    whatsapp?: string;
    lastContactTime?: string;
    inactiveDays?: number;
    contacted: boolean;
    inTask: boolean;
  }

  export type ItemStatus = 'approved' | 'draft' | 'failed' | 'generating' | 'sent' | 'skipped';

  export interface Item {
    id: number;
    customerId: number;
    customerName: string;
    ownerUserId?: number;
    lastContactTime?: string;
    channel: 'mail' | 'whatsapp';
    recipient?: string;
    subject?: string;
    content?: string;
    status: ItemStatus;
    reasonDetail?: string;
    credits?: number;
    sentTime?: string;
    replied: boolean;
  }

  export interface Task {
    id: number;
    name: string;
    inactiveDays?: number;
    channel: 'auto' | 'mail' | 'whatsapp';
    instructions?: string;
    waTemplateName?: string;
    waTemplateLanguage?: string;
    status: 'done' | 'generating' | 'ready' | 'sending';
    createTime: string;
    total: number;
    counts: Record<ItemStatus, number>;
    replied: number;
    items?: Item[];
  }

  export interface CreateReq {
    name?: string;
    inactiveDays?: number;
    customerIds: number[];
    channel: string;
    instructions?: string;
    waTemplateName?: string;
    waTemplateLanguage?: string;
  }
}

export const getReactivateCandidates = (params: { inactiveDays: number; keyword?: string; ownerUserId?: number }) =>
  requestClient.get<ChannelReactivateApi.Candidate[]>('/channel/reactivate/candidates', { params });
export const createReactivateTask = (data: ChannelReactivateApi.CreateReq) =>
  requestClient.post<number>('/channel/reactivate/task/create', data);
export const getReactivateTaskPage = (params: PageParam) =>
  requestClient.get<PageResult<ChannelReactivateApi.Task>>('/channel/reactivate/task/page', { params });
export const getReactivateTask = (id: number) =>
  requestClient.get<ChannelReactivateApi.Task>(`/channel/reactivate/task/get?id=${id}`);
export const deleteReactivateTask = (id: number) => requestClient.delete(`/channel/reactivate/task/delete?id=${id}`);
export const sendReactivateTask = (id: number) => requestClient.post<number>(`/channel/reactivate/task/send?id=${id}`);
export const updateReactivateItem = (data: { content?: string; id: number; subject?: string }) =>
  requestClient.put('/channel/reactivate/item/update', data);
export const changeReactivateItems = (action: 'approve' | 'draft' | 'skip', ids: number[]) =>
  requestClient.post<number>(`/channel/reactivate/item/status?action=${action}`, ids);
export const regenerateReactivateItem = (id: number) =>
  requestClient.post(`/channel/reactivate/item/regenerate?id=${id}`);
