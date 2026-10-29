import { requestClient } from '#/api/request';

export namespace QuickReplyApi {
  export interface Item {
    id?: number;
    /** enterprise 全员可用（管理员维护）、personal 仅自己 */
    scope: 'enterprise' | 'personal';
    ownerUserId?: number;
    title: string;
    /** 可含 {name}：发送时替换成客户称呼 */
    content: string;
    sort?: number;
    useCount?: number;
  }
}

export const getQuickReplies = (keyword?: string) =>
  requestClient.get<QuickReplyApi.Item[]>('/channel/quick-reply/list', { params: { keyword } });
export const saveQuickReply = (data: QuickReplyApi.Item) => requestClient.post<number>('/channel/quick-reply/save', data);
export const deleteQuickReply = (id: number) => requestClient.delete(`/channel/quick-reply/delete?id=${id}`);
export const markQuickReplyUsed = (id: number) => requestClient.post(`/channel/quick-reply/used?id=${id}`);
