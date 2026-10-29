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
    scope: { all: boolean; userIds: number[]; userId?: number };
  }
}

export const getDashboardOverview = (days: number, userId?: number) =>
  requestClient.get<ChannelDashboardApi.Overview>('/channel/dashboard/overview', { params: { days, userId } });

export namespace ChannelDashboardV3Api {
  export interface SupportItem {
    draftId: number;
    channel: 'mail' | 'whatsapp';
    refId: number;
    type: 'compliance' | 'handoff' | 'other';
    reason: string;
    detail?: string;
    question?: string;
    time?: string;
    contact?: string;
    subject?: string;
    platform?: string;
  }

  export interface Overview {
    startDate: string;
    endDate: string;
    updateTime: string;
    traffic: {
      counts: Record<'after_sales' | 'business' | 'invalid' | 'sales', number>;
      effective: number;
      effectiveRate: null | number;
      total: number;
      unclassified: number;
    };
    trend: { date: string; effective: number; rate: null | number; total: number }[];
    ai: {
      crmWrites: number;
      customerBack24hRate: null | number;
      multiRoundRate: null | number;
      replies: number;
      replyIn1hRate: null | number;
      tasks: number;
    };
    /** 客户阶段分布（当前快照，不含公海）：stage 0 为未设置 */
    stages?: { count: number; stage: number }[];
    business: {
      businesses: number;
      businessToWinRate: null | number;
      clues: number;
      cluesChange: null | number;
      inquiryToQuoteRate: null | number;
    };
    support: { im: SupportItem[]; mail: SupportItem[] };
  }
}

export const getDashboardV3 = (startDate: string, endDate: string) =>
  requestClient.get<ChannelDashboardV3Api.Overview>('/channel/dashboard/v3', { params: { startDate, endDate } });
