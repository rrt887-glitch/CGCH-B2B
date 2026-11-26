/**
 * 滑块验证码的两个纯逻辑，单独拎出来是为了能测：
 *
 * 1. 提示文案：`explain` 默认是空串，刷新后要回退到默认提示，
 *    否则「请按住滑块拖动」会消失（线上 AUTH-02）。
 * 2. 键盘位置钳制：键盘用户按方向键移动滑块时，不能越过两端（线上 AUTH-01）。
 */

/** 空 explain 回退到默认提示，非空则用自定义说明 */
export function sliderHintText(
  explain: string | undefined,
  fallback: string,
): string {
  return !explain ? fallback : explain;
}

/** 键盘移动一步后滑块的位置，限制在 [0, max] 之间 */
export function clampSliderPosition(
  current: number,
  delta: number,
  max: number,
): number {
  // max 可能是 NaN（图片尺寸还没量出来），当成 0 处理，避免算出 NaN 样式
  const upper = Number.isFinite(max) ? Math.max(0, max) : 0;
  const next = (Number.isFinite(current) ? current : 0) + delta;
  return Math.min(Math.max(0, next), upper);
}
