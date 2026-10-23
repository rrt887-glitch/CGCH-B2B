import { requestClient } from '#/api/request';

export namespace CloudApi {
  export type Scope = 'enterprise' | 'personal';
  export interface File {
    id: number;
    scope: Scope;
    parentId: number;
    folder: boolean;
    name: string;
    description?: string;
    url?: string;
    size?: number;
    mimeType?: string;
    aiSendable?: boolean;
    updateTime: string;
  }
}

export const getCloudFiles = (params: { keyword?: string; parentId?: number; scope: CloudApi.Scope }) =>
  requestClient.get<CloudApi.File[]>('/channel/cloud-file/list', { params });

export const getCloudPath = (id: number) => requestClient.get<CloudApi.File[]>(`/channel/cloud-file/path?id=${id}`);

export const createCloudFolder = (data: { name: string; parentId?: number; scope: CloudApi.Scope }) =>
  requestClient.post<number>('/channel/cloud-file/folder', data);

export const uploadCloudFile = (file: File, scope: CloudApi.Scope, parentId?: number) =>
  requestClient.upload<CloudApi.File>('/channel/cloud-file/upload', { file, scope, parentId }, { timeout: 180_000 });

export const updateCloudFile = (data: { aiSendable?: boolean; description?: string; id: number; name?: string }) =>
  requestClient.put('/channel/cloud-file/update', data);

export const deleteCloudFile = (id: number) => requestClient.delete(`/channel/cloud-file/delete?id=${id}`);
