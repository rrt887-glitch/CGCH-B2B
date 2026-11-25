/**
 * 判断错误是不是「页面分包/chunk 加载失败」。
 *
 * 线上出现过：进入线索列表后只剩菜单，控制台是模块加载失败，十几秒都没有任何提示，
 * 用户只能手动刷新。识别出来后由路由层自动重载一次，仍失败再给明确提示。
 *
 * 不同浏览器 / 打包器的报错文案不一样，这里统一收口，避免只匹配一种而漏掉。
 */
const CHUNK_ERROR_PATTERNS = [
  /Failed to fetch dynamically imported module/i, // Chrome / Edge
  /error loading dynamically imported module/i, // Firefox
  /Importing a module script failed/i, // Safari
  /ChunkLoadError/i, // webpack 风格
  /Loading chunk [\d-]+ failed/i,
];

export function isChunkLoadError(error: unknown): boolean {
  if (!error) return false;
  const message =
    typeof error === 'string'
      ? error
      : `${(error as Error)?.message ?? ''} ${(error as Error)?.name ?? ''}`;
  return CHUNK_ERROR_PATTERNS.some((re) => re.test(message));
}
