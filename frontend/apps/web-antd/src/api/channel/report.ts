import type { PageParam, PageResult } from '@vben/request';

import type { ChannelDashboardApi } from '#/api/channel/dashboard';

import { requestClient } from '#/api/request';

export namespace ChannelReportApi {
  export interface Item {
    draftId: number;
    channel: 'mail' | 'whatsapp';
    refId: number;
    customer: string;
    question: string;
    detail?: string;
    time?: string;
  }

  export interface Stats {
    current: ChannelDashboardApi.Totals;
    previous: ChannelDashboardApi.Totals;
    pendingReasons: Record<string, number>;
    handoffs: Item[];
    gaps: Item[];
  }

  export interface Report {
    id: number;
    periodType: 'daily' | 'weekly';
    periodStart: string;
    periodEnd: string;
    summary: string;
    aiGenerated: boolean;
    credits: number;
    generateMode: 'auto' | 'manual';
    createTime: string;
    stats?: Stats;
  }
}

export const getReportPage = (params: PageParam & { periodType?: string }) =>
  requestClient.get<PageResult<ChannelReportApi.Report>>('/channel/ai/report/page', { params });
export const getReport = (id: number) => requestClient.get<ChannelReportApi.Report>(`/channel/ai/report/get?id=${id}`);
export const generateReport = (periodType: string, date?: string) =>
  requestClient.post<number>('/channel/ai/report/generate', null, { params: { periodType, date } });
export const deleteReport = (id: number) => requestClient.delete(`/channel/ai/report/delete?id=${id}`);
