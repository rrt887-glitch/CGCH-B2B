import { describe, expect, it } from 'vitest';

import { setupVxeTableA11y } from '../vxe-a11y';

/** 造一个「图标 + title」的 vxe 按钮，和 vxe-table 实际渲染一致 */
function iconButton(className: string, title?: string, iconClass = '') {
  const btn = document.createElement('button');
  btn.className = className;
  if (title) btn.setAttribute('title', title);
  const icon = document.createElement('i');
  icon.className = iconClass;
  btn.append(icon);
  return btn;
}

describe('setupVxeTableA11y', () => {
  it('把工具栏按钮的 title 变成 aria-label', () => {
    const toolbar = document.createElement('div');
    toolbar.className = 'vxe-toolbar';
    toolbar.append(
      iconButton('vxe-button', '刷新', 'vxe-icon-refresh'),
      iconButton('vxe-button', '全屏', 'vxe-icon-zoom-in'),
    );
    document.body.append(toolbar);

    setupVxeTableA11y();

    const [refresh, zoom] = toolbar.querySelectorAll('button');
    expect(refresh!.getAttribute('aria-label')).toBe('刷新');
    expect(zoom!.getAttribute('aria-label')).toBe('全屏');
  });

  it('没有 title 时按图标类名兜底', () => {
    const toolbar = document.createElement('div');
    toolbar.className = 'vxe-toolbar';
    toolbar.append(iconButton('vxe-button', undefined, 'vxe-icon-custom-column'));
    document.body.append(toolbar);

    setupVxeTableA11y();

    expect(toolbar.querySelector('button')!.getAttribute('aria-label')).toBe(
      '自定义列',
    );
  });

  it('分页按钮补名称，当前页标记 aria-current', () => {
    const pager = document.createElement('div');
    pager.className = 'vxe-pager';
    const prev = iconButton('vxe-pager--prev-btn', '上一页');
    const next = iconButton('vxe-pager--next-btn', undefined);
    const current = document.createElement('button');
    current.className = 'vxe-pager--num-btn is--active';
    current.textContent = '1';
    pager.append(prev, next, current);
    document.body.append(pager);

    setupVxeTableA11y();

    expect(prev.getAttribute('aria-label')).toBe('上一页');
    // 没有 title 时按分页按钮的类名兜底
    expect(next.getAttribute('aria-label')).toBe('下一页');
    expect(current.getAttribute('aria-current')).toBe('page');
    // 有文字的页码按钮不能被 aria-label 覆盖
    expect(current.hasAttribute('aria-label')).toBe(false);
  });

  it('有文字的按钮不覆盖原有可读名称', () => {
    const toolbar = document.createElement('div');
    toolbar.className = 'vxe-toolbar';
    const btn = document.createElement('button');
    btn.className = 'vxe-button';
    btn.setAttribute('title', '写跟进');
    btn.textContent = '写跟进';
    toolbar.append(btn);
    document.body.append(toolbar);

    setupVxeTableA11y();

    expect(btn.hasAttribute('aria-label')).toBe(false);
  });

  it('异步插入的（如「跟进记录」标签页）按钮同样会被补名称', async () => {
    setupVxeTableA11y();

    const later = document.createElement('div');
    later.className = 'vxe-toolbar';
    later.append(iconButton('vxe-button', '刷新'));
    document.body.append(later);

    await new Promise((resolve) => setTimeout(resolve, 50));

    expect(later.querySelector('button')!.getAttribute('aria-label')).toBe(
      '刷新',
    );
  });
});
