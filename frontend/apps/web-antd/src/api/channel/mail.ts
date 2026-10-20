import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace ChannelMailApi {
  export interface Account {
    id?: number;
    provider?: string;
    email: string;
    displayName?: string;
    imapHost: string;
    imapPort: number;
    imapSsl?: boolean;
    smtpHost: string;
    smtpPort: number;
    smtpSsl?: boolean;
    username?: string;
    password?: string;
    enabled?: boolean;
    lastSyncTime?: string;
    lastSyncError?: string;
    unrepliedCount?: number;
  }

  export interface Attachment {
    id: number;
    messageId?: number;
    fileName: string;
    contentType?: string;
    fileSize?: number;
    fileUrl: string;
  }

  export interface Message {
    id: number;
    accountId: number;
    folder: string;
    direction: 1 | 2;
    fromAddress?: string;
    fromName?: string;
    toAddresses?: string;
    ccAddresses?: string;
    subject?: string;
    snippet?: string;
    textBody?: string;
    htmlBody?: string;
    sentTime?: string;
    readStatus?: boolean;
    replied?: boolean;
    customerId?: number;
    customerName?: string;
    clueId?: number;
    clueName?: string;
    hasAttachments?: boolean;
    aiGenerated?: boolean;
    attachments?: Attachment[];
  }

  export type View = 'draft' | 'inbox' | 'sent' | 'trash' | 'unreplied';

  export interface PageReq extends PageParam {
    accountId?: number;
    view?: View;
    keyword?: string;
    customerId?: number;
  }

  export interface SendReq {
    accountId: number;
    to: string[];
    cc?: string[];
    subject?: string;
    text?: string;
    html?: string;
    replyToId?: number;
    attachmentIds?: number[];
  }
}

// ===== 邮箱账号 =====
export const getMailAccountList = () =>
  requestClient.get<ChannelMailApi.Account[]>('/channel/mail-account/list');
export const createMailAccount = (data: ChannelMailApi.Account) =>
  requestClient.post<number>('/channel/mail-account/create', data);
export const updateMailAccount = (data: ChannelMailApi.Account) =>
  requestClient.put('/channel/mail-account/update', data);
export const deleteMailAccount = (id: number) =>
  requestClient.delete(`/channel/mail-account/delete?id=${id}`);
export const testMailAccount = (data: ChannelMailApi.Account) =>
  requestClient.post('/channel/mail-account/test', data);
export const syncMailAccount = (id: number) =>
  requestClient.post<number>(`/channel/mail-account/sync?id=${id}`);

// ===== 邮件 =====
export const getMailPage = (params: ChannelMailApi.PageReq) =>
  requestClient.get<PageResult<ChannelMailApi.Message>>('/channel/mail-message/page', { params });
export const getMail = (id: number) =>
  requestClient.get<ChannelMailApi.Message>(`/channel/mail-message/get?id=${id}`);
export const getMailThread = (id: number) =>
  requestClient.get<ChannelMailApi.Message[]>(`/channel/mail-message/thread?id=${id}`);
export const sendMail = (data: ChannelMailApi.SendReq) =>
  requestClient.post<number>('/channel/mail-message/send', data);
export const trashMail = (id: number) =>
  requestClient.delete(`/channel/mail-message/trash?id=${id}`);
export const uploadMailAttachment = (file: File) =>
  requestClient.upload<ChannelMailApi.Attachment>('/channel/mail-message/upload-attachment', { file });

/** 文件大小显示 */
export function formatSize(n?: number) {
  if (!n) return '';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}
