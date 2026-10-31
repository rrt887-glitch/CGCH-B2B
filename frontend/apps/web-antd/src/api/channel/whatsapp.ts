import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace ChannelWaApi {
  export interface Account {
    id?: number;
    name?: string;
    /** whatsapp（默认）、messenger（Facebook 主页私信）、instagram */
    platform?: 'instagram' | 'messenger' | 'tiktok' | 'web' | 'whatsapp' | 'youtube';
    /** 接待成员：逗号分隔的用户编号，新客户轮流分给他们；为空时由号码所属的业务员接待 */
    assignUserIds?: string;
    /** 扫码接入：最近一次同步聊天记录的时间、Evolution 里保存的记录数、新导入数、隐藏号码无法导入数、失败原因 */
    historySyncTime?: number | string;
    historyFetched?: number;
    historyImported?: number;
    historyNoNumber?: number;
    historySyncError?: string;
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
    /** 客户头像（存在本系统的地址） */
    avatarUrl?: string;
    customerId?: number;
    customerName?: string;
    clueId?: number;
    clueName?: string;
    lastMessageTime?: number | string;
    lastInboundTime?: number | string;
    lastMessagePreview?: string;
    unreadCount?: number;
    windowOpen?: boolean;
    windowExpireTime?: number | string;
    archived?: boolean;
    starred?: boolean;
    /** 转交后的处理人；为空表示号码所属业务员 */
    ownerUserId?: number;
    remarkName?: string;
    /** 客户等级：1 A 类 … 4 D 类 */
    customerLevel?: number;
    /** 当前处理人及姓名（团队会话里显示） */
    handlerUserId?: number;
    handlerName?: string;
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
    messageTime?: number | string;
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
    archived?: boolean;
    starred?: boolean;
    unreplied?: boolean;
    level?: number;
    /** 团队会话：数据权限范围内其他同事负责的会话 */
    team?: boolean;
    /** 只看关联了这个 CRM 客户的会话 */
    customerId?: number;
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
/** 同步 Facebook 主页 / Instagram 最近的聊天记录，返回新导入的消息数 */
export const syncWaHistory = (id: number) => requestClient.post<number>(`/channel/wa-account/sync-history?id=${id}`);
export const getWaTemplates = (id: number) =>
  requestClient.get<ChannelWaApi.Template[]>(`/channel/wa-account/templates?id=${id}`);

// ===== 会话与消息 =====
export const getWaConversationPage = (params: ChannelWaApi.ConversationPageReq) =>
  requestClient.get<PageResult<ChannelWaApi.Conversation>>('/channel/wa/conversation/page', { params });
export const getWaConversation = (id: number) =>
  requestClient.get<ChannelWaApi.Conversation>(`/channel/wa/conversation/get?id=${id}`);
export const archiveWaConversation = (id: number, archived: boolean) =>
  requestClient.put(`/channel/wa/conversation/archive?id=${id}&archived=${archived}`);
export const starWaConversation = (id: number, starred: boolean) =>
  requestClient.put(`/channel/wa/conversation/star?id=${id}&starred=${starred}`);
export const remarkWaConversation = (id: number, name: string) =>
  requestClient.put('/channel/wa/conversation/remark', null, { params: { id, name } });
export const transferWaConversation = (id: number, userId: number) =>
  requestClient.put(`/channel/wa/conversation/transfer?id=${id}&userId=${userId}`);
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

/** 设置号码的接待成员：这个号码上的新客户在他们之间轮流分配；传空数组恢复为号码所属的业务员接待 */
export const setWaAssignees = (id: number, userIds: number[]) =>
  requestClient.put<boolean>('/channel/wa-account/assignees', { id, userIds });
