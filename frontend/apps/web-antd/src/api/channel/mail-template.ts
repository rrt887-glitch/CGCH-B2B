import { requestClient } from '#/api/request';

export namespace MailTemplateApi {
  export type Category = 'notice' | 'promo' | 'transaction';
  export interface Template {
    id?: number;
    name: string;
    category: Category;
    visibility: 'all' | 'self';
    ownerUserId?: number;
    subject: string;
    html: string;
    enabled?: boolean;
    defaulted?: boolean;
    updateTime?: string;
  }
}

export const MAIL_TEMPLATE_CATEGORIES = [
  { value: 'promo', label: '推广邮件' },
  { value: 'notice', label: '通知邮件' },
  { value: 'transaction', label: '事务邮件' },
];

export const getMailTemplates = (params?: { category?: string; enabled?: boolean; keyword?: string }) =>
  requestClient.get<MailTemplateApi.Template[]>('/channel/mail-template/list', { params });
export const saveMailTemplate = (data: MailTemplateApi.Template) => requestClient.post<number>('/channel/mail-template/save', data);
export const setMailTemplateEnabled = (id: number, enabled: boolean) =>
  requestClient.put(`/channel/mail-template/enabled?id=${id}&enabled=${enabled}`);
export const setMailTemplateDefault = (id: number) => requestClient.put(`/channel/mail-template/default?id=${id}`);
export const deleteMailTemplate = (id: number) => requestClient.delete(`/channel/mail-template/delete?id=${id}`);
