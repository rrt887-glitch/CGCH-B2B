<script lang="ts" setup>
/** CRM 设置：标签管理、客户分组、客户公海规则、线索流转规则（苹果磨砂胶囊切换） */
import { defineAsyncComponent, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import TreeManager from '#/views/cgch/crm/settings/tree-manager.vue';

const PoolConfig = defineAsyncComponent(
  () => import('#/views/crm/customer/poolConfig/index.vue'),
);
const LeadFlow = defineAsyncComponent(() => import('./lead-flow.vue'));

const active = ref<'group' | 'lead' | 'pool' | 'tag'>('tag');

const TABS = [
  { key: 'tag' as const, label: '标签管理', icon: 'lucide:tags' },
  { key: 'group' as const, label: '客户分组', icon: 'lucide:folder-tree' },
  { key: 'pool' as const, label: '客户公海规则', icon: 'lucide:globe' },
  { key: 'lead' as const, label: '线索流转规则', icon: 'lucide:git-branch' },
];
</script>

<template>
  <div class="crm-set-page">
    <div class="pb-3.5">
      <h2 class="m-0 text-[22px] font-bold tracking-tight text-[#1D1D1F]">
        CRM 设置
      </h2>
      <p class="m-0 mt-1 text-[13px] text-[#6E6E73]">
        统一管理客户标签体系、客户分组树、客户公海自动回收规则与进线流转策略。
      </p>
    </div>

    <div class="crm-set-nav">
      <button
        v-for="t in TABS"
        :key="t.key"
        type="button"
        class="crm-set-tab"
        :class="{ on: active === t.key }"
        @click="active = t.key"
      >
        <IconifyIcon :icon="t.icon" class="size-4" />
        <span>{{ t.label }}</span>
      </button>
    </div>

    <div :key="active" class="crm-set-card">
      <TreeManager v-if="active === 'tag'" kind="tag" label="标签" with-color />
      <TreeManager
        v-else-if="active === 'group'"
        kind="customer-group"
        label="分组"
      />
      <PoolConfig v-else-if="active === 'pool'" />
      <LeadFlow v-else />
    </div>
  </div>
</template>

<style scoped>
.crm-set-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: 100%;
  padding: 20px 24px 24px;
  overflow: auto;
}

.crm-set-nav {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  align-self: flex-start;
  padding: 4px;
  background: rgb(255 255 255 / 72%);
  border-radius: 13px;
  box-shadow: var(--ap-shadow-rest);
  backdrop-filter: blur(20px);
}

.crm-set-tab {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  height: 34px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 500;
  color: #6e6e73;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 10px;
  transition: all var(--ap-dur-fast);
}

.crm-set-tab:hover:not(.on) {
  color: #1d1d1f;
  background: rgb(0 0 0 / 3.5%);
}

.crm-set-tab.on {
  font-weight: 600;
  color: #1d1d1f;
  background: #fff;
  box-shadow: 0 2px 6px rgb(0 0 0 / 8%);
}

.crm-set-card {
  padding: 20px 22px;
  background: var(--ap-surface);
  border-radius: 18px;
  box-shadow: var(--ap-shadow-rest);
  animation: ap-fade-up 180ms var(--ap-ease-out) both;
}
</style>
