import { describe, expect, it } from 'vitest';

import { labelTableSelection } from '../antd-table-a11y';

/**
 * 线上：客户表格「全选」复选框是英文 Select all，行内复选框没有名称。
 * 这里锁住 antd Table 行选择列的 DOM 结构都能被补上中文名称。
 */
function tableDom() {
  const wrap = document.createElement('div');
  wrap.className = 'ant-table-wrapper';
  wrap.innerHTML = `
    <table class="ant-table">
      <thead><tr>
        <th class="ant-table-selection-column">
          <input type="checkbox" class="ant-checkbox-input" aria-label="Select all" />
        </th>
        <th>客户</th>
      </tr></thead>
      <tbody class="ant-table-tbody">
        <tr>
          <td class="ant-table-selection-column"><input type="checkbox" class="ant-checkbox-input" /></td>
          <td>Acme Trading</td>
        </tr>
        <tr>
          <td class="ant-table-selection-column"><input type="checkbox" class="ant-checkbox-input" /></td>
          <td>Global Imports</td>
        </tr>
      </tbody>
    </table>
  `;
  document.body.append(wrap);
  return wrap;
}

describe('labelTableSelection', () => {
  it('全选复选框改成中文（覆盖 antd 的英文默认值）', () => {
    const wrap = tableDom();
    labelTableSelection(wrap);
    const header = wrap.querySelector<HTMLElement>('th input')!;
    expect(header.getAttribute('aria-label')).toBe('全选当前页');
  });

  it('行内复选框带上客户名', () => {
    const wrap = tableDom();
    labelTableSelection(wrap);
    const inputs = wrap.querySelectorAll<HTMLElement>('tbody input');
    expect(inputs[0]!.getAttribute('aria-label')).toBe('选择「Acme Trading」');
    expect(inputs[1]!.getAttribute('aria-label')).toBe('选择「Global Imports」');
  });

  it('行内没有文本时退回「选择这一行」', () => {
    const wrap = document.createElement('div');
    wrap.className = 'ant-table-wrapper';
    wrap.innerHTML = `
      <table><thead><tr><th class="ant-table-selection-column"><input type="checkbox" /></th></tr></thead>
      <tbody><tr><td class="ant-table-selection-column"><input type="checkbox" /></td></tr></tbody></table>
    `;
    document.body.append(wrap);
    labelTableSelection(wrap);
    expect(
      wrap.querySelector<HTMLElement>('tbody input')!.getAttribute('aria-label'),
    ).toBe('选择这一行');
  });
});
