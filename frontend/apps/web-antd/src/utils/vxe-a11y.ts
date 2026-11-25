/**
 * 给 vxe-table 的表格工具栏与分页按钮补上可读名称。
 *
 * vxe 的工具按钮结构与样式是「<button title="刷新"><i class="vxe-icon-refresh"/></button>」。
 * title 在按钮内部有内容时不会被用作可访问名称（accessible name 取的是按钮内容），
 * 而图标是字体字形，于是读屏软件念出来的是私有区字符（例如 "\ue6b8"），
 * 用户完全无法判断这是刷新、全屏还是翻页。这里统一补 aria-label，
 * aria-label 优先级高于内容，读屏即可念出中文名称。
 */

/** 分页按钮没有可用 title 时的兜底名称 */
const CLASS_LABELS: Array<[string, string]> = [
  ['vxe-pager--prev-btn', '上一页'],
  ['vxe-pager--next-btn', '下一页'],
  ['vxe-pager--jump-prev', '向上跳页'],
  ['vxe-pager--jump-next', '向下跳页'],
];

/** 工具栏按钮没有可用 title 时，按图标类名兜底 */
const ICON_LABELS: Array<[RegExp, string]> = [
  [/refresh|rotate/, '刷新'],
  [/zoom-in|fullscreen|maximize/, '全屏'],
  [/zoom-out|minimize|restore/, '退出全屏'],
  [/custom|setting|column/, '自定义列'],
  [/search/, '搜索'],
  [/export|download/, '导出'],
  [/import|upload/, '导入'],
  [/print/, '打印'],
];

function labelFor(btn: HTMLElement): string | undefined {
  const title = (btn.getAttribute('title') || '').trim();
  if (title) return title;

  const className = btn.className || '';
  for (const [cls, label] of CLASS_LABELS) {
    if (className.includes(cls)) return label;
  }

  const icon = btn.querySelector('i, svg');
  const iconClass = icon
    ? `${icon.getAttribute('class') || ''} ${icon.getAttribute('data-icon') || ''}`
    : '';
  for (const [re, label] of ICON_LABELS) {
    if (re.test(iconClass)) return label;
  }
  return undefined;
}

/** 只处理「纯图标」按钮：有文字内容的按钮本来就能被读出来，不要覆盖 */
function labelButton(btn: HTMLElement) {
  if (btn.hasAttribute('aria-label')) return;
  if ((btn.textContent || '').trim() !== '') return;
  const label = labelFor(btn);
  if (!label) return;
  btn.setAttribute('aria-label', label);
}

function labelButtons(root: ParentNode) {
  if (!root.querySelectorAll) return;
  const buttons = root.querySelectorAll<HTMLElement>(
    '.vxe-toolbar button, .vxe-toolbar .vxe-button, .vxe-pager button',
  );
  for (const btn of buttons) labelButton(btn);

  // 当前页码对读屏要可感知
  for (const active of root.querySelectorAll<HTMLElement>(
    '.vxe-pager--num-btn.is--active',
  )) {
    if (!active.hasAttribute('aria-current')) {
      active.setAttribute('aria-current', 'page');
    }
  }
}

let observer: MutationObserver | undefined;

/**
 * 表格是异步渲染、按需插入的（例如客户详情里的「跟进记录」标签页），
 * 所以挂一个 MutationObserver，按钮一出现就补名称。
 */
export function setupVxeTableA11y() {
  if (typeof window === 'undefined' || typeof document === 'undefined') return;

  labelButtons(document);

  observer?.disconnect();
  observer = new MutationObserver((records) => {
    for (const record of records) {
      for (const node of record.addedNodes) {
        if (node.nodeType !== Node.ELEMENT_NODE) continue;
        const el = node as HTMLElement;
        if (el.matches?.('button, .vxe-button')) {
          labelButton(el);
        }
        labelButtons(el);
      }
    }
  });
  observer.observe(document.body, { childList: true, subtree: true });
}
