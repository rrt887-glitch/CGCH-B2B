import { describe, expect, it } from 'vitest';

import { isChunkLoadError } from '../chunk-error';

/**
 * 线上白屏就是分包加载失败引起的，这里把常见报错文案都钉住，
 * 避免只匹配 Chrome 一种、到了 Safari / Firefox 又变成没提示的空白页。
 */
describe('isChunkLoadError', () => {
  it('识别 Chrome 的动态导入失败', () => {
    expect(
      isChunkLoadError(
        new TypeError(
          'Failed to fetch dynamically imported module: https://x/assets/lead-abc.js',
        ),
      ),
    ).toBe(true);
  });

  it('识别 Safari / Firefox 的说法', () => {
    expect(isChunkLoadError(new Error('Importing a module script failed.'))).toBe(
      true,
    );
    expect(
      isChunkLoadError(new Error('error loading dynamically imported module')),
    ).toBe(true);
  });

  it('识别 webpack 风格的 ChunkLoadError', () => {
    const error = new Error('Loading chunk 12 failed.');
    error.name = 'ChunkLoadError';
    expect(isChunkLoadError(error)).toBe(true);
  });

  it('普通业务错误不要误判', () => {
    expect(isChunkLoadError(new Error('请求失败：网络异常'))).toBe(false);
    expect(isChunkLoadError(undefined)).toBe(false);
    expect(isChunkLoadError(null)).toBe(false);
  });
});
