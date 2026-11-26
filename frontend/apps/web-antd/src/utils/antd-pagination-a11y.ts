/**
 * 给 ant-design-vue 的分页器补上中文可访问名称。
 *
 * 线上问题：分页的「上一页 / 下一页」是一个 <li>，里面只有图标（英文 left / right），
 * 既没有 aria-label，读屏也念不出中文；页码的可聚焦项同样没有名字。
 * 邮件列表、平台访问记录、客户 / 知识库 / 云盘都用 antd Pagination，这里统一补。
 */

const ARROW_LABELS: Array<[string, string]> = [
  ['.ant-pagination-prev', '上一页'],
  ['.ant-pagination-next', '下一页'],
  ['.ant-pagination-jump-prev', '向前跳页'],
  ['.ant-pagination-jump-next', '向后跳页'],
];

function ensureLabel(el: HTMLElement, label: string) {
  if (!el.hasAttribute('aria-label')) el.setAttribute('aria-label', label);
}

/** 就地给一个分页器（或包含分页器的子树）补名称 */
export function labelPagination(root: ParentNode) {
  if (!root.querySelectorAll) return;

  for (const [selector, label] of ARROW_LABELS) {
    for (const el of root.querySelectorAll<HTMLElement>(selector)) {
      ensureLabel(el, label);
      // 有的版本把焦点放在内部按钮/链接上，一起补
      for (const inner of el.querySelectorAll<HTMLElement>('a, button')) {
        ensureLabel(inner, label);
      }
    }
  }

  for (const el of root.querySelectorAll<HTMLElement>('.ant-pagination-item')) {
    const page = (el.textContent || '').trim();
    if (/^\d+$/.test(page)) ensureLabel(el, `第 ${page} 页`);
    for (const inner of el.querySelectorAll<HTMLElement>('a, button')) {
      if (/^\d+$/.test(page)) ensureLabel(inner, `第 ${page} 页`);
    }
    if (
      el.classList.contains('ant-pagination-item-active') &&
      !el.hasAttribute('aria-current')
    ) {
      el.setAttribute('aria-current', 'page');
    }
  }
}

let observer: MutationObserver | undefined;

/** 表格异步渲染、翻页后重新生成节点，所以也挂一个观察器 */
export function setupPaginationA11y() {
  if (typeof window === 'undefined' || typeof document === 'undefined') return;

  labelPagination(document);

  observer?.disconnect();
  observer = new MutationObserver((records) => {
    for (const record of records) {
      for (const node of record.addedNodes) {
        if (node.nodeType !== Node.ELEMENT_NODE) continue;
        const el = node as HTMLElement;
        const container =
          el.closest?.('.ant-pagination') ??
          (el.querySelector?.('.ant-pagination') ? el : null);
        if (container) labelPagination(container);
      }
    }
  });
  observer.observe(document.body, { childList: true, subtree: true });
}
