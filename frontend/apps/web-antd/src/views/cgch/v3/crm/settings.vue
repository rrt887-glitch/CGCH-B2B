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
  <div class="px-4 pb-4 pt-[18px]">
    <h2 class="m-0 text-xl font-bold text-gray-900">CRM设置</h2>
    <p class="mb-2 mt-1 text-sm text-gray-500">统一配置标签、客户分组与客户公海规则。</p>
    <Tabs v-model:active-key="active">
      <TabPane key="tag" tab="标签管理"><TreeManager kind="tag" label="标签" with-color /></TabPane>
      <TabPane key="group" tab="客户分组"><TreeManager kind="customer-group" label="分组" /></TabPane>
      <TabPane key="pool" tab="客户公海规则"><PoolConfig /></TabPane>
      <TabPane key="lead" tab="线索流转规则"><LeadFlow /></TabPane>
    </Tabs>
  </div>
</template>
