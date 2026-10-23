import { requestClient } from '#/api/request';

export namespace NotifyRuleApi {
  export interface Rule {
    id?: number;
    type: string;
    channel: 'email' | 'site';
    enabled: boolean;
  }
  export interface Type {
    value: string;
    label: string;
    sample: string;
  }
}

export const getNotifyRules = () => requestClient.get<{ rules: NotifyRuleApi.Rule[]; types: NotifyRuleApi.Type[] }>('/channel/notify-rule/list');
export const saveNotifyRule = (data: NotifyRuleApi.Rule) => requestClient.post<number>('/channel/notify-rule/save', data);
export const deleteNotifyRule = (id: number) => requestClient.delete(`/channel/notify-rule/delete?id=${id}`);
