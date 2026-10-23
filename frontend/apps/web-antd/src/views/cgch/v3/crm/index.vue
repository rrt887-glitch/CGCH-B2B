<script lang="ts" setup>
/** CRM：CRM 智枢、线索、客户、商机、订单管理、CRM 设置，通过「页面菜单」切换 */
import type { HubGroup } from '../components/types';

import MenuHub from '../components/menu-hub.vue';

defineOptions({ name: 'CgchV3Crm' });

const groups: HubGroup[] = [
  { items: [{ key: 'salesmind', label: 'CRM 智枢', load: () => import('#/views/cgch/claw/chat/index.vue') }] },
  {
    label: '线索',
    items: [
      { key: 'lead', label: '线索列表', load: () => import('#/views/crm/clue/index.vue') },
      { key: 'lead-pool', label: '公海线索', load: () => import('../components/coming-soon.vue') },
    ],
  },
  {
    label: '客户',
    items: [
      { key: 'customer', label: '客户列表', load: () => import('#/views/crm/customer/index.vue') },
      { key: 'customer-pool', label: '公海客户', load: () => import('#/views/crm/customer/pool/index.vue') },
      { key: 'dedup', label: '查重', load: () => import('#/views/cgch/crm/dedup/index.vue') },
    ],
  },
  {
    items: [
      { key: 'business', label: '商机', load: () => import('#/views/crm/business/index.vue') },
      { key: 'order', label: '订单管理', load: () => import('#/views/crm/contract/index.vue') },
      { key: 'settings', label: 'CRM设置', load: () => import('./settings.vue') },
    ],
  },
];
</script>

<template>
  <MenuHub :groups="groups" menu-label="页面菜单" />
</template>
