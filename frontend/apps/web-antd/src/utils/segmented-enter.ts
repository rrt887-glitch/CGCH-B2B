/**
 * 让 ant-design-vue 的 Segmented 支持回车选中。
 *
 * Segmented 的每一项是一个原生 <input type="radio">，浏览器只认方向键和空格，
 * 按回车什么都不会发生。于是出现「Tab 把焦点移到『经营分析』，按回车却还是
 * 『数据分析』」——用户以为回车确认了，实际选中的还是原来那个。
 *
 * 这里在 document 上统一拦截：焦点在 Segmented 的单选项上按回车时，点一下它，
 * 触发原生 change，v-model 正常更新。所有用到 Segmented 的地方一起生效。
 */
const SEGMENTED_INPUT_CLASS = 'ant-segmented-item-input';

export function handleSegmentedEnter(event: KeyboardEvent): boolean {
  if (event.key !== 'Enter') return false;
  const target = event.target as HTMLElement | null;
  if (!target?.classList?.contains(SEGMENTED_INPUT_CLASS)) return false;
  event.preventDefault();
  (target as HTMLInputElement).click();
  return true;
}

let installed = false;

/** 在 app 挂载后调用一次即可 */
export function setupSegmentedKeyboard() {
  if (installed || typeof document === 'undefined') return;
  installed = true;
  document.addEventListener('keydown', (event) => {
    handleSegmentedEnter(event);
  });
}
