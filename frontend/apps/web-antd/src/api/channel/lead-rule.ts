import { requestClient } from '#/api/request';

export namespace ChannelLeadRuleApi {
  export interface Rule {
    id?: number;
    name: string;
    /** from：按发件人匹配；subject：按主题匹配 */
    matchField: 'from' | 'subject';
    keyword: string;
    /** CRM 客户来源字典值 */
    source?: number;
    enabled?: boolean;
    sort?: number;
  }
}

export function getLeadRuleList() {
  return requestClient.get<ChannelLeadRuleApi.Rule[]>('/channel/lead-rule/list');
}

export function saveLeadRule(data: ChannelLeadRuleApi.Rule) {
  return requestClient.post<number>('/channel/lead-rule/save', data);
}

export function deleteLeadRule(id: number) {
  return requestClient.delete<boolean>(`/channel/lead-rule/delete?id=${id}`);
}
