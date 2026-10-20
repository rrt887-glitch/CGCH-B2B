import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace ChannelCreditApi {
  export interface Summary {
    balance: number;
    totalIn: number;
    totalOut: number;
    monthConsumed: number;
    monthByChannel: Record<string, number>;
    monthByUser: Record<string, number>;
    tokensPerCredit: number;
  }

  export interface Ledger {
    id: number;
    type: 'adjust' | 'consume' | 'recharge';
    amount: number;
    balanceAfter: number;
    channel?: string;
    refId?: number;
    userId?: number;
    tokens?: number;
    remark?: string;
    createTime: string;
  }

  export interface Package {
    id: number;
    name: string;
    credits: number;
    /** 单位：分 */
    price: number;
    description?: string;
  }

  export interface Order {
    id: number;
    userId: number;
    packageId: number;
    packageName: string;
    credits: number;
    price: number;
    payOrderId: number;
    payStatus: boolean;
    payTime?: string;
    payChannelCode?: string;
    createTime: string;
  }

  export interface LedgerReq extends PageParam {
    type?: string;
    channel?: string;
    userId?: number;
    createTime?: string[];
  }
}

export const getCreditSummary = () => requestClient.get<ChannelCreditApi.Summary>('/channel/credit/summary');
export const getCreditLedger = (params: ChannelCreditApi.LedgerReq) =>
  requestClient.get<PageResult<ChannelCreditApi.Ledger>>('/channel/credit/ledger/page', { params });
export const rechargeCredit = (amount: number, remark?: string) =>
  requestClient.post<number>('/channel/credit/recharge', { amount, remark });
export const getCreditPackages = () => requestClient.get<ChannelCreditApi.Package[]>('/channel/credit/package/list');
export const createCreditOrder = (packageId: number) =>
  requestClient.post<ChannelCreditApi.Order>(`/channel/credit/order/create?packageId=${packageId}`);
export const getCreditOrderPage = (params: PageParam) =>
  requestClient.get<PageResult<ChannelCreditApi.Order>>('/channel/credit/order/page', { params });
