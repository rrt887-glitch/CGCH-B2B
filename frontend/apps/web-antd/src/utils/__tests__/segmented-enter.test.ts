import { describe, expect, it, vi } from 'vitest';

import { handleSegmentedEnter } from '../segmented-enter';

/**
 * Segmented 的选项是原生 radio，浏览器里回车默认什么都不做，
 * 线上就是「焦点在『经营分析』上按回车，界面还是『数据分析』」。
 *
 * 注意：这里直接测函数，不走 dispatchEvent —— happy-dom 对 radio 的回车有自己的
 * 默认行为，会掩盖真实浏览器里的问题。
 */
function radioEvent(key = 'Enter') {
  const input = document.createElement('input');
  input.type = 'radio';
  input.className = 'ant-segmented-item-input';
  document.body.append(input);
  const event = new KeyboardEvent('keydown', { cancelable: true, key });
  Object.defineProperty(event, 'target', { value: input });
  return { event, input };
}

describe('handleSegmentedEnter', () => {
  it('回车会选中当前焦点的 Segmented 选项', () => {
    const { event, input } = radioEvent();
    const onChange = vi.fn();
    input.addEventListener('change', onChange);

    expect(handleSegmentedEnter(event)).toBe(true);
    expect(event.defaultPrevented).toBe(true);
    expect(input.checked).toBe(true);
    expect(onChange).toHaveBeenCalledTimes(1);
  });

  it('其他按键不处理', () => {
    const { event, input } = radioEvent(' ');
    expect(handleSegmentedEnter(event)).toBe(false);
    expect(event.defaultPrevented).toBe(false);
    expect(input.checked).toBe(false);
  });

  it('普通输入框里的回车不拦截', () => {
    const input = document.createElement('input');
    input.type = 'text';
    const event = new KeyboardEvent('keydown', { cancelable: true, key: 'Enter' });
    Object.defineProperty(event, 'target', { value: input });

    expect(handleSegmentedEnter(event)).toBe(false);
    expect(event.defaultPrevented).toBe(false);
  });
});
