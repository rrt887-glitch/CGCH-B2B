<script lang="ts" setup>
/**
 * 模块页的常驻左侧子菜单（与原网站 CRM 一致）：宽屏时菜单固定在左侧，窄屏收起为「页面菜单」按钮。
 * 子页面通过地址参数 ?tab= 记住，刷新或分享链接时停留在同一页。
 */
import type { Component } from 'vue';

import type { HubGroup } from './types';

import { computed, defineAsyncComponent, markRaw, provide } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { IconifyIcon } from '@vben/icons';

import { Dropdown, Spin } from 'ant-design-vue';

const props = defineProps<{ groups: HubGroup[]; menuLabel?: string }>();

const route = useRoute();
const router = useRouter();

// 没有权限的项不显示（例如业务员看不到「成员」「AI 接待设置」）
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
          loadingComponent: Spin,
          delay: 120,
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
/** 子页面之间跳转（例如线索转客户后打开客户列表） */
provide('cgchHubSelect', select);
</script>

<template>
  <div class="side-hub" :class="{ 'side-hub--crm': route.path === '/v3/crm' }">
    <aside class="side-hub__menu">
      <template v-for="(g, gi) in visibleGroups" :key="gi">
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
              <template v-for="(g, gi) in visibleGroups" :key="gi">
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
  height: calc(100vh - 32px);
}

.side-hub__menu {
  flex: 0 0 188px;
  padding: 12px 10px;
  overflow: auto;
  background: #fff;
  border-radius: 18px;
  box-shadow: var(--cg-shadow);
}

.side-hub__group {
  padding: 14px 10px 6px;
  font-size: 12px;
  color: #86868b;
}

.side-hub__sep {
  height: 1px;
  margin: 10px 8px;
  background: #e5e5ea;
}

.side-hub__item {
  transition:
    background-color 0.25s var(--cg-ease),
    color 0.25s var(--cg-ease);
  padding: 8px 10px;
  font-size: 13px;
  color: #424245;
  cursor: pointer;
  border-radius: 8px;
}

.side-hub__item:hover {
  color: #1d1d1f;
  background: #f2f2f7;
}

.side-hub__item.on {
  font-weight: 600;
  color: #fff;
  background: #0071e3;
}

.side-hub__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e5e5ea;
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
  color: #6e6e73;
  cursor: pointer;
  background: #f2f2f7;
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
