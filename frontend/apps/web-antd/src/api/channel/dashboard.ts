import { requestClient } from '#/api/request';

export namespace ChannelDashboardApi {
  export interface Totals {
    mailIn: number;
    mailReplied: number;
    mailOut: number;
    mailAiOut: number;
    waIn: number;
    waConversations: number;
    waRepliedConversations: number;
    waOut: number;
    waAiOut: number;
    aiDrafts: number;
    aiSent: number;
    aiPending: number;
    aiDiscarded: number;
    aiHandoff: number;
    creditsConsumed: number;
    cluesCreated: number;
    cluesFromMail: number;
    cluesFromWhatsApp: number;
    cluesConverted: number;
    customersCreated: number;
    inquiries: number;
    replyRate: number;
    aiShare: number;
  }

  export interface Day {
    date: string;
    mailIn: number;
    waIn: number;
    humanOut: number;
    aiOut: number;
    credits: number;
  }

  export interface UserRow {
    userId: number;
    mailIn: number;
    waIn: number;
    out: number;
    aiOut: number;
  }

  export interface Overview {
    days: number;
    startDate: string;
    current: Totals;
    previous: Totals;
    daily: Day[];
    pendingReasons: Record<string, number>;
    users: UserRow[];
  }
}

export const getDashboardOverview = (days: number) =>
  requestClient.get<ChannelDashboardApi.Overview>('/channel/dashboard/overview', { params: { days } });
