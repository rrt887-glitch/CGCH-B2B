/**
 * 给 ant-design-vue 表格的行选择复选框补中文可访问名称。
 *
 * 线上问题：客户表格「全选」复选框的 aria-label 是英文 "Select all"，
 * 行内复选框干脆没有名称，读屏用户听到的要么是英文、要么是"复选框"。
 */

/** 强制覆盖：antd 自己会给 header 一个英文 "Select all"，必须替换而不是只在缺失时补 */
function setLabel(input: HTMLElement, label: string) {
  if (input.getAttribute('aria-label') !== label) {
    input.setAttribute('aria-label', label);
  }
}

function rowLabel(cell: HTMLElement) {
  const row = cell.closest('tr');
  // 取该行第一个非勾选列的文本，作为「选择「某某客户」」的名称
  const first =
    row?.querySelector<HTMLElement>('td:not(.ant-table-selection-column)') ??
    row?.querySelector<HTMLElement>('td');
  const text = (first?.textContent ?? '').replace(/\s+/g, ' ').trim();
  return text ? `选择「${text.slice(0, 30)}」` : '选择这一行';
}

/** 就地给表格（或包含表格的子树）补名称 */
export function labelTableSelection(root: ParentNode) {
  if (!root.querySelectorAll) return;
  for (const cell of root.querySelectorAll<HTMLElement>(
    '.ant-table-selection-column',
  )) {
    const input = cell.querySelector<HTMLInputElement>(
      'input[type="checkbox"]',
    );
    if (!input) continue;
    setLabel(
      input,
      cell.tagName === 'TH' ? '全选当前页' : rowLabel(cell),
    );
  }
}

let observer: MutationObserver | undefined;

/** 表格异步渲染、翻页后都会重建 DOM，挂观察器持续补 */
export function setupTableA11y() {
  if (typeof window === 'undefined' || typeof document === 'undefined') return;

  labelTableSelection(document);

  observer?.disconnect();
  observer = new MutationObserver((records) => {
    for (const record of records) {
      for (const node of record.addedNodes) {
        if (node.nodeType !== Node.ELEMENT_NODE) continue;
        const el = node as HTMLElement;
        const wrapper =
          el.closest?.('.ant-table-wrapper') ??
          (el.querySelector?.('.ant-table-wrapper') ? el : null);
        if (wrapper) labelTableSelection(wrapper);
        else if (el.matches?.('.ant-table-selection-column')) {
          labelTableSelection(el.closest('.ant-table-wrapper') ?? el);
        }
      }
    }
  });
  observer.observe(document.body, { childList: true, subtree: true });
}
