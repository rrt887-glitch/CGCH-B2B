<script lang="ts" setup>
/** CRM：左侧常驻子菜单（CRM 智枢、线索、客户、跟进日程、商机、订单管理、CRM 设置），右侧为所选页面 */
import type { HubGroup } from '../components/types';

import { defineComponent, h } from 'vue';

import SideHub from '../components/side-hub.vue';

defineOptions({ name: 'CgchV3Crm' });

/** 同一个页面组件带不同参数（例如公海） */
const withProps = (load: () => Promise<any>, props: Record<string, any>) => async () => {
  const m = await load();
  return defineComponent({ name: 'CgchCrmPoolPage', setup: () => () => h(m.default, props) });
};

const groups: HubGroup[] = [
  { items: [{ key: 'salesmind', label: 'CRM 智枢', load: () => import('./claw.vue') }] },
  {
    label: '线索',
    items: [
      { key: 'lead', label: '线索列表', load: () => import('./lead.vue') },
      { key: 'lead-pool', label: '公海线索', load: withProps(() => import('./lead.vue'), { pool: true }) },
    ],
  },
  {
    label: '客户',
    items: [
      { key: 'customer', label: '客户列表', load: () => import('./customer.vue') },
      { key: 'agenda', label: '跟进日程', load: () => import('./agenda.vue') },
      { key: 'customer-pool', label: '公海客户', load: withProps(() => import('./customer.vue'), { pool: true }) },
      { key: 'dedup', label: '查重', load: () => import('./dedup.vue') },
    ],
  },
  {
    items: [
      { key: 'business', label: '商机', load: () => import('./business.vue') },
      { key: 'order', label: '订单管理', load: () => import('./order.vue') },
      { key: 'settings', label: 'CRM设置', load: () => import('./settings.vue') },
    ],
  },
];
</script>

<template>
  <SideHub :groups="groups" />
</template>
