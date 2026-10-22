import { requestClient } from '#/api/request';

export namespace CustomerOverviewApi {
  export interface Dist {
    key: string;
    count: number;
    dealt: number;
  }

  export interface Overview {
    days: number;
    scope: { all: boolean; userId?: number; userIds: number[] };
    totals: {
      active30: number;
      aiHosted: number;
      created: number;
      dealRate: number;
      dealt: number;
      dormant: number;
      overdueFollowUp: number;
      pool: number;
      total: number;
    };
    recency: { d30: number; d7: number; d90: number; never: number; older: number };
    byCountry: Dist[];
    bySource: Dist[];
    byLevel: Dist[];
    monthly: Record<string, number>;
    funnel: { amount: number; count: number; id: number; name: string; percent: number }[];
    ended: Record<string, { amount: number; count: number }>;
    topActive: {
      company?: string;
      country?: string;
      dealt: boolean;
      id: number;
      lastContact?: string;
      mails: number;
      messages: number;
      name: string;
      ownerUserId?: number;
      whatsapp: number;
    }[];
  }
}

export const getCustomerOverview = (days: number, userId?: number) =>
  requestClient.get<CustomerOverviewApi.Overview>('/channel/customer-overview/get', { params: { days, userId } });
