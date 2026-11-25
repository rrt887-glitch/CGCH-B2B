/**
 * 草根出海 · 动效工具
 * - v-reveal：元素进入视口时依次浮现（可传入序号做错峰：v-reveal="i"）
 * - useCountUp：数字从 0 滚动到目标值（缓动），用于 KPI
 * - useSlidingIndicator：导航选中块跟随滑动（FLIP），用于侧栏 / 分段控件
 * - prefersReducedMotion：系统「减少动态效果」时直接给最终状态
 */
import type { App, Directive, Ref } from 'vue';

import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

export const prefersReducedMotion = () =>
  typeof window !== 'undefined' &&
  window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

/* ------------------------------------------------------------------ */
/* v-reveal                                                            */
/* ------------------------------------------------------------------ */
let observer: IntersectionObserver | undefined;
function getObserver() {
  if (observer || typeof IntersectionObserver === 'undefined') return observer;
  observer = new IntersectionObserver(
    (entries) => {
      for (const e of entries) {
        if (e.isIntersecting) {
          (e.target as HTMLElement).dataset.apReveal = 'in';
          observer!.unobserve(e.target);
        }
      }
    },
    { rootMargin: '0px 0px -6% 0px', threshold: 0.04 },
  );
  return observer;
}

export const vReveal: Directive<HTMLElement, number | undefined> = {
  mounted(el, binding) {
    const io = getObserver();
    if (!io || prefersReducedMotion()) return;
    const i = Number(binding.value ?? 0);
    el.style.setProperty('--ap-delay', `${Math.min(i, 12) * 45}ms`);
    el.dataset.apReveal = '';
    io.observe(el);
  },
  beforeUnmount(el) {
    observer?.unobserve(el);
  },
};

export function registerMotionDirectives(app: App) {
  app.directive('reveal', vReveal);
}

/* ------------------------------------------------------------------ */
/* useCountUp                                                          */
/* ------------------------------------------------------------------ */
const easeOutExpo = (t: number) => (t === 1 ? 1 : 1 - 2 ** (-10 * t));

export function useCountUp(
  source: Ref<null | number | undefined>,
  duration = 700,
) {
  const display = ref<number>(0);
  let raf = 0;
  watch(
    source,
    (to, from) => {
      cancelAnimationFrame(raf);
      const target = Number(to ?? 0);
      if (prefersReducedMotion() || !Number.isFinite(target)) {
        display.value = target;
        return;
      }
      const start = Number(from ?? display.value ?? 0) || 0;
      const t0 = performance.now();
      const step = (now: number) => {
        const p = Math.min(1, (now - t0) / duration);
        display.value = start + (target - start) * easeOutExpo(p);
        if (p < 1) raf = requestAnimationFrame(step);
      };
      raf = requestAnimationFrame(step);
    },
    { immediate: true },
  );
  onBeforeUnmount(() => cancelAnimationFrame(raf));
  return display;
}

/* ------------------------------------------------------------------ */
/* useSlidingIndicator                                                 */
/* ------------------------------------------------------------------ */
/**
 * 在容器里放一个绝对定位的指示块，跟随当前选中项滑动。
 * container 内选中项需带 `activeSelector`（默认 .is-active）。
 */
export function useSlidingIndicator(
  container: Ref<HTMLElement | undefined>,
  activeKey: Ref<unknown>,
  activeSelector = '.is-active',
) {
  const style = ref<Record<string, string>>({ opacity: '0' });
  let first = true;
  let ro: ResizeObserver | undefined;

  function measure() {
    const root = container.value;
    const el = root?.querySelector<HTMLElement>(activeSelector);
    if (!root || !el) {
      style.value = { ...style.value, opacity: '0' };
      return;
    }
    const r = root.getBoundingClientRect();
    const a = el.getBoundingClientRect();
    style.value = {
      opacity: '1',
      width: `${a.width}px`,
      height: `${a.height}px`,
      transform: `translate3d(${a.left - r.left + root.scrollLeft}px, ${a.top - r.top + root.scrollTop}px, 0)`,
      transition:
        first || prefersReducedMotion()
          ? 'none'
          : 'transform 520ms var(--ap-spring), width 420ms var(--ap-ease), height 420ms var(--ap-ease), opacity 200ms linear',
    };
    first = false;
  }

  watch(activeKey, () => nextTick(measure));
  onMounted(() => {
    nextTick(measure);
    if (typeof ResizeObserver !== 'undefined' && container.value) {
      // 放到下一帧执行，避免 ResizeObserver loop 警告
      ro = new ResizeObserver(() => requestAnimationFrame(measure));
      ro.observe(container.value);
    }
  });
  onBeforeUnmount(() => ro?.disconnect());
  return { style, measure };
}

/* ------------------------------------------------------------------ */
/* View Transitions：支持的浏览器里页面切换交叉淡化                    */
/* ------------------------------------------------------------------ */
export function withViewTransition(update: () => unknown) {
  const doc = document as Document & {
    startViewTransition?: (cb: () => unknown) => unknown;
  };
  if (!doc.startViewTransition || prefersReducedMotion()) return update();
  return doc.startViewTransition(update);
}
