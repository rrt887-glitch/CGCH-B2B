import type { PageParam, PageResult } from '@vben/request';

import { ref } from 'vue';

import { requestClient } from '#/api/request';

export namespace MailBatchApi {
  export type Status = 'cancelled' | 'done' | 'draft' | 'sending';
  export type ItemStatus = 'cancelled' | 'failed' | 'invalid' | 'pending' | 'sent' | 'suppressed';

  export interface Item {
    id: number;
    customerId?: number;
    name?: string;
    company?: string;
    email: string;
    accountId?: number;
    status: ItemStatus;
    reason?: string;
    sentTime?: number | string;
    replied: boolean;
    unsubscribed: boolean;
  }

  export interface Batch {
    id: number;
    name: string;
    subject: string;
    content?: string;
    contentType: 'html' | 'text';
    ratePerMinute: number;
    accountIds: number[];
    status: Status;
    total: number;
    counts: Partial<Record<ItemStatus, number>>;
    replied?: number;
    createTime?: number | string;
    startTime?: number | string;
    finishTime?: number | string;
    items?: Item[];
  }

  export interface CreateReq {
    name: string;
    subject: string;
    content: string;
    contentType: 'html' | 'text';
    customerIds?: number[];
    emails?: string[];
    accountIds: number[];
    ratePerMinute: number;
  }
}

export const BATCH_STATUS: Record<MailBatchApi.Status, { color: string; label: string }> = {
  draft: { label: '待确认', color: 'default' },
  sending: { label: '发送中', color: 'processing' },
  done: { label: '已完成', color: 'success' },
  cancelled: { label: '已取消', color: 'default' },
};
export const ITEM_STATUS: Record<MailBatchApi.ItemStatus, { color: string; label: string }> = {
  pending: { label: '待发送', color: 'default' },
  sent: { label: '已发送', color: 'success' },
  failed: { label: '发送失败', color: 'error' },
  invalid: { label: '邮箱无效', color: 'warning' },
  suppressed: { label: '已退订跳过', color: 'default' },
  cancelled: { label: '已取消', color: 'default' },
};

/** 从客户列表勾选后跳过来时带的客户 */
export const batchDraftCustomers = ref<{ id: number; name: string }[]>([]);

export const getMailBatchPage = (params: PageParam) =>
  requestClient.get<PageResult<MailBatchApi.Batch>>('/channel/mail-batch/page', { params });
export const getMailBatch = (id: number, status?: string) =>
  requestClient.get<MailBatchApi.Batch>('/channel/mail-batch/get', { params: { id, status } });
export const createMailBatch = (data: MailBatchApi.CreateReq) => requestClient.post<number>('/channel/mail-batch/create', data);
export const startMailBatch = (id: number) => requestClient.put('/channel/mail-batch/start', null, { params: { id } });
export const cancelMailBatch = (id: number) => requestClient.put('/channel/mail-batch/cancel', null, { params: { id } });
export const deleteMailBatch = (id: number) => requestClient.delete('/channel/mail-batch/delete', { params: { id } });
