import { describe, expect, it } from 'vitest';

import { clampSliderPosition, sliderHintText } from '../verify-slide-helpers';

/**
 * 对应两个线上问题：
 * - AUTH-02：刷新验证码后提示消失（explain 默认空串被直接写进提示）
 * - AUTH-01：键盘无法操作滑块（方向键移动不能越界）
 */
describe('sliderHintText', () => {
  it('explain 为空时回退到默认提示', () => {
    expect(sliderHintText('', '请按住滑块拖动')).toBe('请按住滑块拖动');
    expect(sliderHintText(undefined, '请按住滑块拖动')).toBe('请按住滑块拖动');
  });

  it('有自定义说明时用自定义说明', () => {
    expect(sliderHintText('向右拖动滑块', '请按住滑块拖动')).toBe(
      '向右拖动滑块',
    );
  });
});

describe('clampSliderPosition', () => {
  it('正常移动', () => {
    expect(clampSliderPosition(10, 8, 260)).toBe(18);
  });

  it('不能小于 0 或大于最大值', () => {
    expect(clampSliderPosition(4, -8, 260)).toBe(0);
    expect(clampSliderPosition(258, 8, 260)).toBe(260);
  });

  it('最大值为 0 或非法时不越界', () => {
    expect(clampSliderPosition(0, 8, 0)).toBe(0);
    expect(clampSliderPosition(0, 8, Number.NaN)).toBe(0);
  });
});
