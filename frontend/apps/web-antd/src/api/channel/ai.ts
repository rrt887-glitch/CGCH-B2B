import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace ChannelAiApi {
  export type Mode = 'auto' | 'draft' | 'off';

  export interface Config {
    agentName?: string;
    personality?: string;
    defaultLanguage?: string;
    llmBaseUrl?: string;
    llmApiKey?: string;
    llmModel?: string;
    embeddingModel?: string;
    mailMode?: Mode;
    waMode?: Mode;
    mailSignature?: string;
    humanPauseHours?: number;
    extraInstructions?: string;
    leadAutoCreate?: boolean;
    leadAiExtract?: boolean;
    hasApiKey?: boolean;
  }

  export interface Knowledge {
    id?: number;
    type: 'company' | 'faq' | 'product';
    title?: string;
    content: string;
    enabled?: boolean;
    sort?: number;
    /** 生成向量所用模型；与当前向量模型一致才算已建语义索引 */
    embeddingModel?: string;
  }

  export interface SearchHit {
    id: number;
    type: Knowledge['type'];
    title?: string;
    content: string;
    keywordScore: number;
    semanticScore: number;
    score: number;
    semantic: boolean;
  }

  export interface ForbiddenWord {
    id?: number;
    word: string;
    remark?: string;
    enabled?: boolean;
  }

  export interface Draft {
    id: number;
    channel: 'mail' | 'whatsapp';
    refId: number;
    content: string;
    status: 'discarded' | 'pending' | 'sent';
    reason?: string;
    reasonDetail?: string;
    promptTokens?: number;
    completionTokens?: number;
    model?: string;
    createTime?: string;
  }
}

/** 草稿未自动发出的原因 */
export const DRAFT_REASON: Record<string, string> = {
  draft_mode: '当前为「只起草」模式',
  handoff: 'AI 判断需要人工处理（投诉、退款、合同等）',
  forbidden: '回复中含有违禁词',
  human_active: '业务员近期在人工跟进',
  not_hosted: '该客户未开启 AI 托管',
  window_closed: 'WhatsApp 24 小时窗口已关闭',
  send_failed: '自动发送失败',
  manual: '手动起草',
  no_credit: '智点余额不足',
};

export const getAiConfig = () => requestClient.get<ChannelAiApi.Config>('/channel/ai/config/get');
export const saveAiConfig = (data: ChannelAiApi.Config) => requestClient.put('/channel/ai/config/save', data);
export const testAiConfig = (data: ChannelAiApi.Config) => requestClient.post<string>('/channel/ai/config/test', data);

export const getKnowledgeList = (type?: string) =>
  requestClient.get<ChannelAiApi.Knowledge[]>('/channel/ai/knowledge/list', { params: { type } });
export const saveKnowledge = (data: ChannelAiApi.Knowledge) => requestClient.post<number>('/channel/ai/knowledge/save', data);
export const deleteKnowledge = (id: number) => requestClient.delete(`/channel/ai/knowledge/delete?id=${id}`);

export const getForbiddenList = () => requestClient.get<ChannelAiApi.ForbiddenWord[]>('/channel/ai/forbidden/list');
export const saveForbidden = (data: ChannelAiApi.ForbiddenWord) => requestClient.post<number>('/channel/ai/forbidden/save', data);
export const deleteForbidden = (id: number) => requestClient.delete(`/channel/ai/forbidden/delete?id=${id}`);

export const draftMail = (messageId: number) =>
  requestClient.post<ChannelAiApi.Draft>(`/channel/ai/draft/mail?messageId=${messageId}`);
export const draftWhatsApp = (conversationId: number) =>
  requestClient.post<ChannelAiApi.Draft>(`/channel/ai/draft/whatsapp?conversationId=${conversationId}`);
export const getPendingDraft = (channel: 'mail' | 'whatsapp', refId: number) =>
  requestClient.get<ChannelAiApi.Draft | null>('/channel/ai/draft/pending', { params: { channel, refId } });
export const getDraftPage = (params: PageParam & { channel?: string; status?: string }) =>
  requestClient.get<PageResult<ChannelAiApi.Draft>>('/channel/ai/draft/page', { params });
export const closeDraft = (id: number, used: boolean) =>
  requestClient.post(`/channel/ai/draft/close?id=${id}&used=${used}`);
export const reindexKnowledge = () => requestClient.post<number>('/channel/ai/knowledge/reindex');
export const searchKnowledge = (q: string) =>
  requestClient.get<ChannelAiApi.SearchHit[]>('/channel/ai/knowledge/search', { params: { q } });
