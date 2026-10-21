import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace ChannelResearchApi {
  export interface Report {
    companyName?: string;
    countryCode?: string;
    summary?: string;
    businessType?: string;
    mainProducts?: string[];
    markets?: string[];
    scale?: string;
    certifications?: string[];
    companyContacts?: string[];
    purchasingSignals?: string[];
    talkingPoints?: string[];
    risks?: string[];
    confidence?: 'high' | 'low' | 'medium';
  }

  export interface Research {
    id: number;
    companyName?: string;
    website?: string;
    customerId?: number;
    clueId?: number;
    status: 'done' | 'failed' | 'running';
    reasonDetail?: string;
    credits?: number;
    userId?: number;
    createTime: string;
    report?: Report;
    sources?: { snippet: string; title: string; url: string }[];
  }

  export interface CreateReq {
    website?: string;
    companyName?: string;
    customerId?: number;
    clueId?: number;
  }
}

export const createResearch = (data: ChannelResearchApi.CreateReq) => requestClient.post<number>('/channel/research/create', data);
export const getResearchPage = (params: PageParam & { clueId?: number; customerId?: number }) =>
  requestClient.get<PageResult<ChannelResearchApi.Research>>('/channel/research/page', { params });
export const getResearch = (id: number) => requestClient.get<ChannelResearchApi.Research>(`/channel/research/get?id=${id}`);
export const applyResearch = (id: number) => requestClient.post<string[]>(`/channel/research/apply?id=${id}`);
export const deleteResearch = (id: number) => requestClient.delete(`/channel/research/delete?id=${id}`);
