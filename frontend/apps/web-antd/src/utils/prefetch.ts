/**
 * 鼠标移到菜单上时，提前把对应页面的分包下载下来。
 *
 * 跨境访问时每个分包要 1 个往返，点进去再等就明显卡；悬停时预取能把这段等待藏起来。
 * 只预取用户真正指向的那一个页面，不会一次性下载所有页面（按流量计费/慢网下很重要）。
 */
import { router } from '#/router';

const prefetched = new Set<string>();

function isLazyLoadable(value: unknown): value is () => Promise<unknown> {
  return typeof value === 'function';
}

export function setupHoverPrefetch() {
  if (typeof document === 'undefined') {
    return;
  }
  document.addEventListener(
    'mouseover',
    (event) => {
      const target = event.target as HTMLElement | null;
      const link = target?.closest?.('[data-prefetch-route], a[href]');
      const href = link?.getAttribute('href') || '';
      const path =
        link?.getAttribute('data-prefetch-route') ||
        (/^(?:\/[^/][^?#]*|\/)?#\//.test(href)
          ? href.slice(href.indexOf('#') + 1)
          : href);
      if (
        !path.startsWith('/') ||
        path.startsWith('//') ||
        path === '/' ||
        prefetched.has(path)
      ) {
        return;
      }
      prefetched.add(path);
      try {
        const resolved = router.resolve(path);
        for (const record of resolved.matched) {
          for (const component of Object.values(record.components ?? {})) {
            if (isLazyLoadable(component)) {
              component().catch(() => {
                // 预取失败无所谓，真正点击时会重新加载
              });
            }
          }
        }
      } catch {
        // 解析失败忽略
      }
    },
    { passive: true },
  );
}
