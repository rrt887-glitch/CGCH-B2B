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

export interface LatestResearch {
  id: number;
  customerId: number;
  status: 'done' | 'failed' | 'running';
  summary?: string;
  confidence?: string;
}

/** 每个客户最近一次背调（客户列表「背调结果」列） */
export const getLatestResearch = (customerIds: number[]) =>
  requestClient.get<LatestResearch[]>('/channel/research/latest-by-customers', {
    params: { customerIds: customerIds.join(',') },
  });
