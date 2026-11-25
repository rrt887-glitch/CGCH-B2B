<script lang="ts" setup>
/**
 * 统一二级侧栏（macOS 系统设置风格）：
 * 宽屏左侧毛玻璃分组菜单 + 选中块弹簧滑动，窄屏收起为「页面菜单」按钮；
 * 子页切换带平滑交叉淡入，异步加载显示骨架屏。
 */
import type { Component } from 'vue';

import type { HubGroup } from './types';

import { computed, defineAsyncComponent, markRaw, provide, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { IconifyIcon } from '@vben/icons';

import { Dropdown } from 'ant-design-vue';

import { ApSkeleton } from '#/components/apple';
import { useSlidingIndicator } from '#/utils/motion';

const props = defineProps<{ groups: HubGroup[]; menuLabel?: string }>();

const route = useRoute();
const router = useRouter();

const { hasAccessByCodes } = useAccess();
const allowed = (it: { codes?: string[] }) =>
  !it.codes?.length || hasAccessByCodes(it.codes);
const visibleGroups = computed(() =>
  props.groups
    .map((g) => ({ ...g, items: g.items.filter(allowed) }))
    .filter((g) => g.items.length > 0),
);
const items = computed(() => visibleGroups.value.flatMap((g) => g.items));
const activeKey = computed(() => {
  const k = String(route.query.tab || '');
  return items.value.some((i) => i.key === k) ? k : items.value[0]?.key;
});
const active = computed(() =>
  items.value.find((i) => i.key === activeKey.value),
);

const menuRef = ref<HTMLElement>();
const { style: indicatorStyle } = useSlidingIndicator(menuRef, activeKey, '.on');

const cache = new Map<string, Component>();
const view = computed(() => {
  const item = active.value;
  if (!item) return undefined;
  if (!cache.has(item.key)) {
    cache.set(
      item.key,
      markRaw(
        defineAsyncComponent({
          loader: item.load,
          loadingComponent: ApSkeleton,
          delay: 140,
        }),
      ),
    );
  }
  return cache.get(item.key);
});

function select(key: string, extra?: Record<string, string>) {
  if (key !== activeKey.value || extra)
    router.replace({ query: { tab: key, ...extra } });
}
provide('cgchHubSelect', select);
</script>

<template>
  <div class="side-hub">
    <aside ref="menuRef" class="side-hub__menu">
      <div
        class="side-hub__indicator"
        :style="indicatorStyle"
        aria-hidden="true"
      ></div>
      <template v-for="(g, gi) in visibleGroups" :key="gi">
        <div v-if="gi > 0 && !g.label" class="side-hub__sep"></div>
        <div v-if="g.label" class="side-hub__group">{{ g.label }}</div>
        <button
          v-for="it in g.items"
          :key="it.key"
          type="button"
          class="side-hub__item"
          :class="{ on: it.key === activeKey }"
          @click="select(it.key)"
        >
          <IconifyIcon
            v-if="it.icon"
            :icon="it.icon"
            class="side-hub__icon"
          />
          <span>{{ it.label }}</span>
        </button>
      </template>
    </aside>

    <section class="side-hub__main">
      <div class="side-hub__mobile">
        <Dropdown :trigger="['click']" placement="bottomLeft">
          <button type="button" class="side-hub__btn">
            <IconifyIcon icon="lucide:menu" class="size-4" />
            <span>{{ active?.label || menuLabel || '页面菜单' }}</span>
            <IconifyIcon icon="lucide:chevron-down" class="size-3.5 opacity-60" />
          </button>
          <template #overlay>
            <div class="side-hub__pop">
              <template v-for="(g, gi) in visibleGroups" :key="gi">
                <div v-if="g.label" class="side-hub__group">{{ g.label }}</div>
                <button
                  v-for="it in g.items"
                  :key="it.key"
                  type="button"
                  class="side-hub__item"
                  :class="{ on: it.key === activeKey }"
                  @click="select(it.key)"
                >
                  <IconifyIcon
                    v-if="it.icon"
                    :icon="it.icon"
                    class="side-hub__icon"
                  />
                  <span>{{ it.label }}</span>
                </button>
              </template>
            </div>
          </template>
        </Dropdown>
      </div>
      <div class="side-hub__body">
        <Transition name="ap-swap">
          <component :is="view" v-if="view" :key="activeKey" />
        </Transition>
      </div>
    </section>
  </div>
</template>

<style scoped>
.side-hub {
  display: flex;
  gap: 0;
  height: 100%;
  min-height: 0;
}

.side-hub__menu {
  position: relative;
  display: flex;
  flex: 0 0 192px;
  flex-direction: column;
  gap: 1px;
  padding: 12px 8px;
  overflow: auto;
  background: var(--ap-bg-subbar);
  border-right: 0.5px solid var(--ap-separator);
}

.side-hub__indicator {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 0;
  pointer-events: none;
  background: var(--ap-accent-soft);
  border-radius: 7px;
}

.side-hub__group {
  padding: 12px 10px 4px;
  font-size: 11px;
  font-weight: 500;
  color: var(--ap-label-3);
}

.side-hub__group:first-of-type {
  padding-top: 2px;
}

.side-hub__sep {
  height: 0.5px;
  margin: 8px 10px;
  background: var(--ap-separator);
}

.side-hub__item {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
  height: 31px;
  padding: 0 10px;
  font-size: 13px;
  font-weight: 500;
  color: var(--ap-label-2);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 7px;
  transition:
    color var(--ap-dur-fast) linear,
    background-color var(--ap-dur-fast) linear;
}

.side-hub__item:hover:not(.on) {
  color: var(--ap-label);
  background: rgb(0 0 0 / 4%);
}

.side-hub__item.on {
  font-weight: 600;
  color: var(--ap-accent);
}

.side-hub__icon {
  flex: none;
  width: 15px;
  height: 15px;
  color: var(--ap-label-3);
  transition: color var(--ap-dur-fast) linear;
}

.side-hub__item.on .side-hub__icon {
  color: var(--ap-accent);
}

.side-hub__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.side-hub__mobile {
  display: none;
  padding: 10px 14px 0;
}

.side-hub__btn {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 5px 11px;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--ap-label);
  cursor: pointer;
  background: var(--ap-surface);
  border: 0;
  border-radius: 7px;
  box-shadow: var(--ap-shadow-rest);
}

.side-hub__pop {
  min-width: 188px;
  padding: 4px;
  background: var(--ap-glass-strong);
  border: 0.5px solid var(--ap-separator);
  border-radius: 10px;
  box-shadow: var(--ap-shadow-pop);
  backdrop-filter: var(--ap-blur);
}

.side-hub__pop .side-hub__item.on {
  color: var(--ap-accent);
  background: var(--ap-accent-soft);
}

.side-hub__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

@media (width <= 960px) {
  .side-hub__menu {
    display: none;
  }

  .side-hub__mobile {
    display: block;
  }
}
</style>
