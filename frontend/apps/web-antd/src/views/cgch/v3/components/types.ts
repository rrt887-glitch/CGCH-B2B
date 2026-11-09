export interface HubItem {
  key: string;
  label: string;
  /** 异步加载的页面组件 */
  load: () => Promise<any>;
  /** 需要的权限（任一即可）；没有权限的成员看不到这一项，例如业务员看不到「成员」 */
  codes?: string[];
}

export interface HubGroup {
  label?: string;
  items: HubItem[];
}
