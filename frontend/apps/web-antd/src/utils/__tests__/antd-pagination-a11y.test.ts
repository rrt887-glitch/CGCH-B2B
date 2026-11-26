import { describe, expect, it } from 'vitest';

import { labelPagination } from '../antd-pagination-a11y';

/**
 * 线上：分页的上一页 / 下一页只有图标（英文 left / right），读屏念不出中文。
 * 这里锁住 antd Pagination 的几种节点都能被补上名称。
 */
function paginationDom() {
  const ul = document.createElement('ul');
  ul.className = 'ant-pagination';
  ul.innerHTML = `
    <li class="ant-pagination-prev" tabindex="0"><a class="ant-pagination-item-link"></a></li>
    <li class="ant-pagination-item ant-pagination-item-1"><a>1</a></li>
    <li class="ant-pagination-item ant-pagination-item-2 ant-pagination-item-active"><a>2</a></li>
    <li class="ant-pagination-jump-next" title="向后跳页"><a></a></li>
    <li class="ant-pagination-next" tabindex="0"><button></button></li>
  `;
  document.body.append(ul);
  return ul;
}

describe('labelPagination', () => {
  it('上一页 / 下一页 / 跳页补中文名称（含内部可聚焦元素）', () => {
    const ul = paginationDom();
    labelPagination(ul);

    const prev = ul.querySelector<HTMLElement>('.ant-pagination-prev')!;
    const next = ul.querySelector<HTMLElement>('.ant-pagination-next')!;
    const inner = prev.querySelector('a')!;

    expect(prev.getAttribute('aria-label')).toBe('上一页');
    expect(next.getAttribute('aria-label')).toBe('下一页');
    expect(inner.getAttribute('aria-label')).toBe('上一页');
  });

  it('页码补「第 N 页」，当前页标 aria-current', () => {
    const ul = paginationDom();
    labelPagination(ul);

    const page2 = ul.querySelector<HTMLElement>('.ant-pagination-item-2')!;
    expect(page2.getAttribute('aria-label')).toBe('第 2 页');
    expect(page2.getAttribute('aria-current')).toBe('page');
    expect(
      ul.querySelector<HTMLElement>('.ant-pagination-item-1')!.hasAttribute('aria-current'),
    ).toBe(false);
  });

  it('已有 aria-label 不覆盖', () => {
    const ul = paginationDom();
    ul.querySelector('.ant-pagination-prev')!.setAttribute('aria-label', '自定义');
    labelPagination(ul);
    expect(
      ul.querySelector('.ant-pagination-prev')!.getAttribute('aria-label'),
    ).toBe('自定义');
  });
});
