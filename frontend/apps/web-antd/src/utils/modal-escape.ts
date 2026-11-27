/**
 * 让弹窗支持按 ESC 关闭（确定行为，不依赖弹窗内部焦点）。
 *
 * 线上问题：设置里的几个弹窗（快捷回复 / 邮件模板 / 成员 / 云盘）按 ESC、点遮罩都关不掉，
 * 只能点 × 或取消。ant-design-vue 的 ESC 是挂在弹窗 wrapper 的 keydown 上的，
 * 一旦打开瞬间焦点没落在弹窗内，事件就到不了那里。
 *
 * 这里在该弹窗打开期间监听 document 的 Escape（捕获阶段，先于组件处理）：
 * - 下拉框 / 气泡卡展开时不抢 ESC，避免"想关下拉却把弹窗关了"
 * - 关闭时移除监听，不影响别的页面
 */
import { onBeforeUnmount, watch } from 'vue';
import type { Ref } from 'vue';

const OPEN_POPUP_SELECTOR = [
  '.ant-select-dropdown:not(.ant-select-dropdown-hidden)',
  '.ant-dropdown:not(.ant-dropdown-hidden)',
  '.ant-popover:not(.ant-popover-hidden)',
].join(', ');

export function useEscapeToClose(isOpen: Ref<boolean>, close: () => void) {
  function onKeydown(event: KeyboardEvent) {
    if (event.key !== 'Escape') return;
    if (document.querySelector(OPEN_POPUP_SELECTOR)) return;
    event.stopPropagation();
    close();
  }

  watch(
    isOpen,
    (open) => {
      if (open) document.addEventListener('keydown', onKeydown, true);
      else document.removeEventListener('keydown', onKeydown, true);
    },
    { immediate: true },
  );

  onBeforeUnmount(() => {
    document.removeEventListener('keydown', onKeydown, true);
  });
}
