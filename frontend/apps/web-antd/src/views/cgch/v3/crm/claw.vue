<script lang="ts" setup>
/** CRM 智枢：深度背调、客户唤醒、新建对话（与原网站 CRMClaw 一致），通过 ?sub= 切换 */
import { computed, defineAsyncComponent, markRaw } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import { Spin } from 'ant-design-vue';

const route = useRoute();
const router = useRouter();

const lazy = (loader: () => Promise<any>) => markRaw(defineAsyncComponent({ loader, loadingComponent: Spin, delay: 120 }));

const ITEMS = [
  { key: 'research', label: '深度背调', icon: 'lucide:shield-check', view: lazy(() => import('#/views/cgch/claw/bg-check/index.vue')) },
  { key: 'wake', label: '客户唤醒', icon: 'lucide:mail', view: lazy(() => import('#/views/cgch/claw/reactivate/index.vue')) },
  { key: 'chat', label: '新建对话', icon: 'lucide:circle-plus', view: lazy(() => import('#/views/cgch/claw/chat/index.vue')) },
];

const active = computed(() => ITEMS.find((i) => i.key === route.query.sub) ?? ITEMS[2]!);

function go(key: string) {
  router.replace({ query: { tab: 'salesmind', sub: key } });
}
</script>

<template>
  <div class="claw">
    <aside class="claw__nav">
      <div class="claw__title">CRMClaw</div>
      <div v-for="it in ITEMS" :key="it.key" class="claw__item" :class="{ on: active.key === it.key }" @click="go(it.key)">
        <IconifyIcon :icon="it.icon" class="size-4" />{{ it.label }}
      </div>
    </aside>
    <section class="claw__main">
      <component :is="active.view" :key="active.key" />
    </section>
  </div>
</template>

<style scoped>
.claw {
  display: flex;
  height: 100%;
}

.claw__nav {
  flex: 0 0 140px;
  padding: 14px 8px;
  border-right: 1px solid #f0f1f3;
}

.claw__title {
  padding: 0 10px 12px;
  font-size: 15px;
  font-weight: 700;
  color: #111827;
}

.claw__item {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 7px 10px;
  margin-bottom: 2px;
  font-size: 13px;
  color: #374151;
  cursor: pointer;
  border-radius: 8px;
}

.claw__item:hover {
  background: #f3f6fd;
}

.claw__item.on {
  color: #155dfc;
  background: #eef3fe;
}

.claw__main {
  flex: 1;
  min-width: 0;
  overflow: auto;
}

@media (width <= 760px) {
  .claw {
    flex-direction: column;
  }

  .claw__nav {
    display: flex;
    flex: 0 0 auto;
    gap: 4px;
    border-right: 0;
    border-bottom: 1px solid #f0f1f3;
  }

  .claw__title {
    display: none;
  }
}
</style>
