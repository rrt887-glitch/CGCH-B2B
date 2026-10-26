import { requestClient } from '#/api/request';

/** 消息分流设置：新询盘按渠道 + 客户国家，自动分配给指定业务员（多人轮流） */
export namespace RoutingApi {
  export interface Rule {
    id?: number;
    name: string;
    enabled: boolean;
    sort?: number;
    /** 逗号分隔，为空 = 全部渠道 */
    sources: string;
    /** in 属于 / not_in 不属于；为空 = 不限国家 */
    countryOp?: '' | 'in' | 'not_in';
    countries: string;
    assigneeIds: number[];
  }
}

export const ROUTING_SOURCES = [
  { value: 'whatsapp', label: 'WhatsApp' },
  { value: 'messenger', label: 'Facebook 主页' },
  { value: 'instagram', label: 'Instagram' },
  { value: 'web', label: '官网IM' },
  { value: 'youtube', label: 'YouTube 评论' },
  { value: 'tiktok', label: 'TikTok 评论' },
  { value: 'mail', label: '邮箱' },
  { value: 'platform', label: '留资邮箱（B2B 平台询盘）' },
];

export const getRoutingRules = () => requestClient.get<RoutingApi.Rule[]>('/channel/routing-rule/list');

export const saveRoutingRule = (data: RoutingApi.Rule) => requestClient.post<number>('/channel/routing-rule/save', data);

export const setRoutingRuleEnabled = (id: number, enabled: boolean) =>
  requestClient.put(`/channel/routing-rule/enabled?id=${id}&enabled=${enabled}`);

export const deleteRoutingRule = (id: number) => requestClient.delete(`/channel/routing-rule/delete?id=${id}`);
