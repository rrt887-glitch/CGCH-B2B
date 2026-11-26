<script lang="ts" setup>
/** CRM 设置：标签管理、客户分组、客户公海规则、线索流转规则 */
import { defineAsyncComponent, ref } from 'vue';

import { TabPane, Tabs } from 'ant-design-vue';

import TreeManager from '#/views/cgch/crm/settings/tree-manager.vue';

const PoolConfig = defineAsyncComponent(() => import('#/views/crm/customer/poolConfig/index.vue'));
const LeadFlow = defineAsyncComponent(() => import('./lead-flow.vue'));

const active = ref('tag');
</script>

<template>
  <div class="crm-set-page">
    <div class="pb-3.5">
      <h2 class="m-0 text-[21px] font-bold tracking-tight text-[#1D1D1F]">CRM 设置</h2>
      <p class="m-0 mt-1 text-[13px] text-[#6E6E73]">
        统一管理客户标签体系、客户分组树、客户公海回收规则与进线流转规则。
      </p>
    </div>
    <div class="crm-set-card">
      <Tabs v-model:active-key="active">
        <TabPane key="tag" tab="标签管理"><TreeManager kind="tag" label="标签" with-color /></TabPane>
        <TabPane key="group" tab="客户分组"><TreeManager kind="customer-group" label="分组" /></TabPane>
        <TabPane key="pool" tab="客户公海规则"><PoolConfig /></TabPane>
        <TabPane key="lead" tab="线索流转规则"><LeadFlow /></TabPane>
      </Tabs>
    </div>
  </div>
</template>

<style scoped>
.crm-set-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 20px 24px 16px;
  overflow: auto;
}

.crm-set-card {
  flex: 1;
  padding: 6px 20px 20px;
  background: var(--ap-surface);
  border-radius: 18px;
  box-shadow: var(--ap-shadow-rest);
}
</style>
