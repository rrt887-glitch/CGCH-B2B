import { requestClient } from '#/api/request';

/** CGCH 外贸 CRM 扩展接口：标签、客户分组、查重 */
export namespace CgchCrmApi {
  /** 树形节点（标签、客户分组通用） */
  export interface TreeNode {
    id?: number;
    name: string;
    parentId?: number;
    sort?: number;
    color?: string;
    createTime?: Date;
  }

  /** 查重结果 */
  export interface DedupItem {
    type: 'clue' | 'customer';
    id: number;
    name: string;
    companyName?: string;
    email?: string;
    mobile?: string;
    countryCode?: string;
    ownerUserId?: number;
    matchedFields: string[];
  }
}

type TreeKind = 'customer-group' | 'tag';

export function getTreeList(kind: TreeKind) {
  return requestClient.get<CgchCrmApi.TreeNode[]>(`/crm/${kind}/list`);
}

export function createTreeNode(kind: TreeKind, data: CgchCrmApi.TreeNode) {
  return requestClient.post<number>(`/crm/${kind}/create`, data);
}

export function updateTreeNode(kind: TreeKind, data: CgchCrmApi.TreeNode) {
  return requestClient.put(`/crm/${kind}/update`, data);
}

export function deleteTreeNode(kind: TreeKind, id: number) {
  return requestClient.delete(`/crm/${kind}/delete?id=${id}`);
}

/** 标签列表 */
export function getTagList() {
  return getTreeList('tag');
}

/** 客户分组列表 */
export function getCustomerGroupList() {
  return getTreeList('customer-group');
}

/** 查重搜索 */
export function searchDedup(keyword: string, scope: 'all' | 'clue' | 'customer' = 'all') {
  return requestClient.get<CgchCrmApi.DedupItem[]>('/crm/dedup/search', {
    params: { keyword, scope },
  });
}

/** 修改客户的标签和分组（会话页直接改）；groupId 不传表示不分组 */
export function updateCustomerTags(id: number, tagIds: number[], groupId?: number) {
  return requestClient.put<boolean>('/crm/customer/update-tags', { id, tagIds, groupId: groupId ?? null });
}
