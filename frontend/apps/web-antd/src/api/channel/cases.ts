import { requestClient } from '#/api/request';

export namespace ChannelCaseApi {
  export interface Case {
    id?: number;
    title: string;
    customerId?: number;
    customerName?: string;
    industry?: string;
    product?: string;
    countryCode?: string;
    summary?: string;
    keyPoints: string[];
    objections: string[];
    phrases: string[];
    enabled: boolean;
    source?: 'ai' | 'manual';
    credits?: number;
    indexed?: boolean;
    createTime?: string;
  }

  export interface Candidate {
    id: number;
    name: string;
    companyName?: string;
    countryCode?: string;
    ownerUserId?: number;
    hasCase: boolean;
  }
}

export const getCaseCandidates = () => requestClient.get<ChannelCaseApi.Candidate[]>('/channel/ai/case/candidates');
export const generateCase = (customerId: number) =>
  requestClient.post<number>(`/channel/ai/case/generate?customerId=${customerId}`, null, { timeout: 120_000 });
export const getCaseList = () => requestClient.get<ChannelCaseApi.Case[]>('/channel/ai/case/list');
export const saveCase = (data: ChannelCaseApi.Case) => requestClient.post<number>('/channel/ai/case/save', data);
export const enableCase = (id: number, enabled: boolean) =>
  requestClient.put(`/channel/ai/case/enable?id=${id}&enabled=${enabled}`);
export const deleteCase = (id: number) => requestClient.delete(`/channel/ai/case/delete?id=${id}`);
