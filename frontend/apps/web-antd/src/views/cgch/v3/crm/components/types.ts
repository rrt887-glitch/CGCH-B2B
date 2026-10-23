/** 左侧筛选栏的一项：filter 是点选后附加的查询参数；有 children 的是可展开的分类 */
export interface RailItem {
  key: string;
  label: string;
  filter?: Record<string, any>;
  icon?: string;
  /** 在这一项上方画分隔线 */
  divider?: boolean;
  children?: RailItem[];
}

/** 列表的一列 */
export interface CrmColumn {
  key: string;
  title: string;
  width?: number;
  fixed?: 'left' | 'right';
  /** 不允许在表格设置里隐藏 */
  required?: boolean;
  /** 默认隐藏（没有数据来源的列），可在表格设置里打开 */
  hidden?: boolean;
  ellipsis?: boolean;
}
