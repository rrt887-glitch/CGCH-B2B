<script lang="ts" setup>
/**
 * 模块页的「页面菜单 / 设置菜单」：左上角按钮展开子页面列表，右侧显示所选子页面。
 * 子页面通过地址参数 ?tab= 记住，刷新或分享链接时停留在同一页。
 */
import type { Component } from 'vue';

import type { HubGroup } from './types';

import { computed, defineAsyncComponent, markRaw } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import { Dropdown, Spin } from 'ant-design-vue';

const props = defineProps<{ groups: HubGroup[]; menuLabel: string }>();

const route = useRoute();
const router = useRouter();

const items = computed(() => props.groups.flatMap((g) => g.items));
const activeKey = computed(() => {
  const k = String(route.query.tab || '');
  return items.value.some((i) => i.key === k) ? k : items.value[0]?.key;
});
const active = computed(() => items.value.find((i) => i.key === activeKey.value));

const cache = new Map<string, Component>();
const view = computed(() => {
  const item = active.value;
  if (!item) return undefined;
  if (!cache.has(item.key)) {
    cache.set(item.key, markRaw(defineAsyncComponent({ loader: item.load, loadingComponent: Spin, delay: 120 })));
  }
  return cache.get(item.key);
});

function select(key: string) {
  router.replace({ query: { ...route.query, tab: key } });
}
</script>

<template>
  <div class="v3-hub">
    <div class="v3-hub__bar">
      <Dropdown :trigger="['click']" placement="bottomLeft">
        <button type="button" class="v3-hub__btn">
          <IconifyIcon icon="lucide:panel-left" class="size-4" />
          <span>{{ menuLabel }}</span>
        </button>
        <template #overlay>
          <div class="v3-hub__menu">
            <div class="v3-hub__menu-title">{{ menuLabel }}</div>
            <template v-for="(g, gi) in groups" :key="gi">
              <div v-if="g.label" class="v3-hub__group">{{ g.label }}</div>
              <div
                v-for="it in g.items"
                :key="it.key"
                class="v3-hub__item"
                :class="{ on: it.key === activeKey, sub: !!g.label }"
                @click="select(it.key)"
              >
                {{ it.label }}
              </div>
            </template>
          </div>
        </template>
      </Dropdown>
      <span class="ml-3 text-base font-semibold">{{ active?.label }}</span>
      <slot name="extra"></slot>
    </div>
    <div class="v3-hub__body">
      <component :is="view" v-if="view" :key="activeKey" />
    </div>
  </div>
</template>

<style scoped>
.v3-hub {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 16px);
  overflow: hidden;
  background: #fff;
  border-radius: 12px;
}

.v3-hub__bar {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  padding: 12px 16px 8px;
}

.v3-hub__btn {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 4px 10px;
  font-size: 13px;
  color: #4b5563;
  cursor: pointer;
  background: #f3f5fa;
  border: 0;
  border-radius: 8px;
}

.v3-hub__btn:hover {
  color: #155dfc;
}

.v3-hub__menu {
  min-width: 180px;
  padding: 8px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgb(15 23 42 / 12%);
}

.v3-hub__menu-title {
  padding: 4px 8px 8px;
  font-size: 12px;
  color: #9ca3af;
}

.v3-hub__group {
  padding: 8px 8px 4px;
  font-size: 12px;
  color: #6b7280;
}

.v3-hub__item {
  padding: 7px 10px;
  font-size: 14px;
  color: #1f2937;
  cursor: pointer;
  border-radius: 6px;
}

.v3-hub__item.sub {
  padding-left: 18px;
}

.v3-hub__item:hover {
  background: #f3f6fd;
}

.v3-hub__item.on {
  color: #155dfc;
  background: #eaf0fe;
}

.v3-hub__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

/* 子页面自带的 Page 外边距收紧，与原网站一致 */
.v3-hub__body :deep(> .relative.flex.h-full) {
  height: 100%;
}
</style>
