export interface HubItem {
  key: string;
  label: string;
  /** 异步加载的页面组件 */
  load: () => Promise<any>;
}

export interface HubGroup {
  label?: string;
  items: HubItem[];
}
