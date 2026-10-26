import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace ChannelWaApi {
  export interface Account {
    id?: number;
    name?: string;
    /** whatsapp（默认）、messenger（Facebook 主页私信）、instagram */
    platform?: 'instagram' | 'messenger' | 'tiktok' | 'web' | 'whatsapp' | 'youtube';
    /** manual 手动填写令牌、oauth Facebook 登录授权 */
    authType?: string;
    /** 扫码接入的连接状态：open 已连接、connecting 连接中、close 已断开 */
    connState?: string;
    phoneNumberId: string;
    wabaId?: string;
    displayPhone?: string;
    accessToken?: string;
    appSecret?: string;
    verifyToken?: string;
    apiBase?: string;
    apiVersion?: string;
    enabled?: boolean;
    lastWebhookTime?: string;
    webhookPath?: string;
    hasAccessToken?: boolean;
    hasAppSecret?: boolean;
  }

  export interface Conversation {
    id: number;
    platform?: 'instagram' | 'messenger' | 'tiktok' | 'web' | 'whatsapp' | 'youtube';
    accountId: number;
    waId: string;
    profileName?: string;
    customerId?: number;
    customerName?: string;
    clueId?: number;
    clueName?: string;
    lastMessageTime?: string;
    lastInboundTime?: string;
    lastMessagePreview?: string;
    unreadCount?: number;
    windowOpen?: boolean;
    windowExpireTime?: string;
  }

  export interface Message {
    id: number;
    conversationId: number;
    direction: 1 | 2;
    waMessageId?: string;
    type: string;
    textBody?: string;
    mediaId?: string;
    mimeType?: string;
    fileName?: string;
    fileUrl?: string;
    fileSize?: number;
    templateName?: string;
    status?: 'delivered' | 'failed' | 'read' | 'received' | 'sent';
    errorMessage?: string;
    messageTime?: string;
    aiGenerated?: boolean;
  }

  export interface Template {
    name: string;
    language: string;
    status: string;
    category?: string;
    components?: { format?: string; text?: string; type: string }[];
  }

  export interface ConversationPageReq extends PageParam {
    accountId?: number;
    keyword?: string;
    unreadOnly?: boolean;
  }

  export interface SendReq {
    conversationId?: number;
    accountId?: number;
    to?: string;
    text?: string;
    templateName?: string;
    templateLanguage?: string;
    templateParams?: string[];
    templatePreview?: string;
  }
}

// ===== 号码 =====
export const getWaAccountList = () => requestClient.get<ChannelWaApi.Account[]>('/channel/wa-account/list');
export const createWaAccount = (data: ChannelWaApi.Account) =>
  requestClient.post<number>('/channel/wa-account/create', data);
export const updateWaAccount = (data: ChannelWaApi.Account) =>
  requestClient.put('/channel/wa-account/update', data);
export const deleteWaAccount = (id: number) => requestClient.delete(`/channel/wa-account/delete?id=${id}`);
export const getWaTemplates = (id: number) =>
  requestClient.get<ChannelWaApi.Template[]>(`/channel/wa-account/templates?id=${id}`);

// ===== 会话与消息 =====
export const getWaConversationPage = (params: ChannelWaApi.ConversationPageReq) =>
  requestClient.get<PageResult<ChannelWaApi.Conversation>>('/channel/wa/conversation/page', { params });
export const getWaConversation = (id: number) =>
  requestClient.get<ChannelWaApi.Conversation>(`/channel/wa/conversation/get?id=${id}`);
export const getWaMessages = (conversationId: number, beforeId?: number) =>
  requestClient.get<ChannelWaApi.Message[]>('/channel/wa/message/list', { params: { conversationId, beforeId } });
export const sendWaMessage = (data: ChannelWaApi.SendReq) =>
  requestClient.post<ChannelWaApi.Message>('/channel/wa/message/send', data);
/** 取媒体地址（收到的媒体首次查看时由后端从 Meta 下载） */
export const getWaMedia = (id: number) =>
  requestClient.get<ChannelWaApi.Message>(`/channel/wa/message/media?id=${id}`);
export const sendWaMedia = (conversationId: number, file: File, caption?: string) =>
  requestClient.upload<ChannelWaApi.Message>('/channel/wa/message/send-media', {
    conversationId,
    file,
    ...(caption ? { caption } : {}),
  });

/** 模板正文里的变量个数：{{1}}、{{2}}… */
export function templateParamCount(t?: ChannelWaApi.Template) {
  const body = t?.components?.find((c) => c.type === 'BODY')?.text ?? '';
  const nums = [...body.matchAll(/\{\{(\d+)\}\}/g)].map((m) => Number(m[1]));
  return nums.length ? Math.max(...nums) : 0;
}

/** 用变量值替换模板正文，作为聊天记录里的预览 */
export function templatePreview(t: ChannelWaApi.Template, params: string[]) {
  const body = t.components?.find((c) => c.type === 'BODY')?.text ?? `[模板] ${t.name}`;
  return body.replaceAll(/\{\{(\d+)\}\}/g, (_, n) => params[Number(n) - 1] || `{{${n}}}`);
}
