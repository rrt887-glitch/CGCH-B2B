<script lang="ts" setup>
/**
 * 模块页的常驻左侧子菜单（与原网站 CRM 一致）：宽屏时菜单固定在左侧，窄屏收起为「页面菜单」按钮。
 * 子页面通过地址参数 ?tab= 记住，刷新或分享链接时停留在同一页。
 */
import type { Component } from 'vue';

import type { HubGroup } from './types';

import { computed, defineAsyncComponent, markRaw, provide } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import { Dropdown, Spin } from 'ant-design-vue';

const props = defineProps<{ groups: HubGroup[]; menuLabel?: string }>();

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

function select(key: string, extra?: Record<string, string>) {
  if (key !== activeKey.value || extra) router.replace({ query: { tab: key, ...extra } });
}
/** 子页面之间跳转（例如线索转客户后打开客户列表） */
provide('cgchHubSelect', select);
</script>

<template>
  <div class="side-hub">
    <aside class="side-hub__menu">
      <template v-for="(g, gi) in groups" :key="gi">
        <div v-if="gi > 0 && !g.label" class="side-hub__sep"></div>
        <div v-if="g.label" class="side-hub__group">{{ g.label }}</div>
        <div
          v-for="it in g.items"
          :key="it.key"
          class="side-hub__item"
          :class="{ on: it.key === activeKey }"
          @click="select(it.key)"
        >
          {{ it.label }}
        </div>
      </template>
    </aside>

    <section class="side-hub__main">
      <div class="side-hub__mobile">
        <Dropdown :trigger="['click']" placement="bottomLeft">
          <button type="button" class="side-hub__btn">
            <IconifyIcon icon="lucide:menu" class="size-4" />
            <span>{{ menuLabel || '页面菜单' }}</span>
          </button>
          <template #overlay>
            <div class="side-hub__pop">
              <template v-for="(g, gi) in groups" :key="gi">
                <div v-if="g.label" class="side-hub__group">{{ g.label }}</div>
                <div
                  v-for="it in g.items"
                  :key="it.key"
                  class="side-hub__item"
                  :class="{ on: it.key === activeKey }"
                  @click="select(it.key)"
                >
                  {{ it.label }}
                </div>
              </template>
            </div>
          </template>
        </Dropdown>
      </div>
      <div class="side-hub__body">
        <component :is="view" v-if="view" :key="activeKey" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.side-hub {
  display: flex;
  gap: 12px;
  height: calc(100vh - 24px);
}

.side-hub__menu {
  flex: 0 0 188px;
  padding: 12px 10px;
  overflow: auto;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
}

.side-hub__group {
  padding: 14px 10px 6px;
  font-size: 12px;
  color: #6b7280;
}

.side-hub__sep {
  height: 1px;
  margin: 10px 8px;
  background: #e5e7eb;
}

.side-hub__item {
  padding: 8px 10px;
  font-size: 13px;
  color: #374151;
  cursor: pointer;
  border-radius: 8px;
}

.side-hub__item:hover {
  color: #111827;
  background: #f3f4f6;
}

.side-hub__item.on {
  font-weight: 600;
  color: #1d4ed8;
  background: #eff4ff;
}

.side-hub__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
}

.side-hub__mobile {
  display: none;
  padding: 10px 12px 0;
}

.side-hub__btn {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 4px 10px;
  font-size: 13px;
  color: #4b5563;
  cursor: pointer;
  background: #F3F4F6;
  border: 0;
  border-radius: 8px;
}

.side-hub__pop {
  min-width: 180px;
  padding: 8px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgb(15 23 42 / 12%);
}

.side-hub__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

@media (max-width: 960px) {
  .side-hub__menu {
    display: none;
  }

  .side-hub__mobile {
    display: block;
  }
}
</style>
