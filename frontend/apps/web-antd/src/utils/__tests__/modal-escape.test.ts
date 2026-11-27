import { describe, expect, it } from 'vitest';
import { createApp, defineComponent, h, ref } from 'vue';

import { useEscapeToClose } from '../modal-escape';

/**
 * 线上：设置里的弹窗按 ESC 关不掉（antd 的 ESC 依赖焦点在弹窗内）。
 * 这里锁住我们的显式实现：ESC 关闭；下拉展开时不抢 ESC；卸载后不再响应。
 */
function mountHost(initial = true) {
  const state = { closeCalls: 0 };
  const Host = defineComponent({
    setup() {
      const open = ref(initial);
      useEscapeToClose(open, () => {
        state.closeCalls += 1;
        open.value = false;
      });
      return () => h('div', open.value ? 'open' : 'closed');
    },
  });
  const el = document.createElement('div');
  document.body.append(el);
  const app = createApp(Host);
  app.mount(el);
  return { app, state };
}

function pressEscape() {
  document.dispatchEvent(
    new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }),
  );
}

describe('useEscapeToClose', () => {
  it('按 ESC 关闭', () => {
    const host = mountHost();
    pressEscape();
    expect(host.state.closeCalls).toBe(1);
    host.app.unmount();
  });

  it('下拉框展开时不抢 ESC（先关下拉，不关弹窗）', () => {
    const host = mountHost();
    const dropdown = document.createElement('div');
    dropdown.className = 'ant-select-dropdown';
    document.body.append(dropdown);

    pressEscape();
    expect(host.state.closeCalls).toBe(0);

    dropdown.remove();
    pressEscape();
    expect(host.state.closeCalls).toBe(1);
    host.app.unmount();
  });

  it('卸载后不再响应 ESC', () => {
    const host = mountHost();
    host.app.unmount();
    pressEscape();
    expect(host.state.closeCalls).toBe(0);
  });

  it('弹窗关闭时不监听', () => {
    const host = mountHost(false);
    pressEscape();
    expect(host.state.closeCalls).toBe(0);
    host.app.unmount();
  });
});
