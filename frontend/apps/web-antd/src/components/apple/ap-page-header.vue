<script lang="ts" setup>
/**
 * 统一页头：大标题 + 一行说明 + 右侧操作区。
 * 滚动容器滚动后，标题收缩为吸顶的毛玻璃小标题（同 iOS 大标题行为）。
 */
import { onBeforeUnmount, onMounted, ref } from 'vue';

const props = withDefaults(
  defineProps<{
    compact?: boolean;
    eyebrow?: string;
    sticky?: boolean;
    subtitle?: string;
    title: string;
  }>(),
  { compact: false, sticky: true },
);

const root = ref<HTMLElement>();
const collapsed = ref(false);
let scroller: HTMLElement | null = null;

function onScroll() {
  collapsed.value = (scroller?.scrollTop ?? 0) > 24;
}

function findScroller(el: HTMLElement | null): HTMLElement | null {
  let p = el?.parentElement ?? null;
  while (p) {
    const s = getComputedStyle(p);
    if (/(auto|scroll)/.test(s.overflowY) && p.scrollHeight > p.clientHeight)
      return p;
    p = p.parentElement;
  }
  return null;
}

onMounted(() => {
  if (!props.sticky) return;
  requestAnimationFrame(() => {
    scroller = findScroller(root.value ?? null);
    scroller?.addEventListener('scroll', onScroll, { passive: true });
  });
});
onBeforeUnmount(() => scroller?.removeEventListener('scroll', onScroll));
</script>

<template>
  <header
    ref="root"
    class="ap-page-header"
    :class="{ 'is-collapsed': collapsed, 'is-compact': compact, 'is-sticky': sticky }"
  >
    <div class="ap-page-header__text">
      <small v-if="eyebrow" class="ap-page-header__eyebrow">{{ eyebrow }}</small>
      <h1 class="ap-page-header__title">
        {{ title }}<slot name="title-extra"></slot>
      </h1>
      <p v-if="subtitle || $slots.subtitle" class="ap-page-header__subtitle">
        <slot name="subtitle">{{ subtitle }}</slot>
      </p>
    </div>
    <div v-if="$slots.default" class="ap-page-header__actions">
      <slot></slot>
    </div>
  </header>
</template>

<style scoped>
.ap-page-header {
  z-index: 5;
  display: flex;
  flex-wrap: wrap;
  gap: 12px 16px;
  align-items: flex-end;
  justify-content: space-between;
  padding: 6px 2px 18px;
  transition:
    padding var(--ap-dur) var(--ap-ease),
    background-color var(--ap-dur) linear,
    box-shadow var(--ap-dur) linear;
}

.ap-page-header.is-sticky {
  position: sticky;
  top: 0;
}

.ap-page-header.is-sticky.is-collapsed {
  padding-block: 10px;
  margin-inline: -8px;
  padding-inline: 10px;
  background: var(--ap-glass);
  border-radius: var(--ap-radius-md);
  box-shadow: 0 0.5px 0 var(--ap-separator);
  backdrop-filter: var(--ap-blur);
}

.ap-page-header__text {
  min-width: 0;
}

.ap-page-header__eyebrow {
  display: block;
  margin-bottom: 2px;
  font-size: var(--ap-text-footnote);
  font-weight: 500;
  color: var(--ap-label-3);
}

.ap-page-header__title {
  display: flex;
  gap: 10px;
  align-items: center;
  margin: 0;
  font-size: 30px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--ap-label);
  letter-spacing: -0.022em;
  transform-origin: left center;
  transition:
    font-size var(--ap-dur) var(--ap-ease),
    transform var(--ap-dur) var(--ap-ease);
}

.is-compact .ap-page-header__title {
  font-size: 24px;
}

.is-collapsed .ap-page-header__title {
  font-size: 17px;
}

.ap-page-header__subtitle {
  max-width: 720px;
  margin: 4px 0 0;
  font-size: var(--ap-text-subhead);
  line-height: 1.5;
  color: var(--ap-label-2);
  transition:
    opacity var(--ap-dur) linear,
    max-height var(--ap-dur) var(--ap-ease);
}

.is-collapsed .ap-page-header__subtitle,
.is-collapsed .ap-page-header__eyebrow {
  display: none;
}

.ap-page-header__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

@media (width <= 760px) {
  .ap-page-header__title {
    font-size: 24px;
  }
}
</style>
