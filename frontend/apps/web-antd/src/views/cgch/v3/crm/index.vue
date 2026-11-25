<script lang="ts" setup>
/** CRM：统一分组二级侧栏（智能 / 线索 / 客户 / 销售 / 设置），右侧为所选页面 */
import type { HubGroup } from '../components/types';

import { defineComponent, h } from 'vue';

import SideHub from '../components/side-hub.vue';

defineOptions({ name: 'CgchV3Crm' });

/** 同一个页面组件带不同参数（例如公海） */
const withProps =
  (load: () => Promise<any>, props: Record<string, any>) => async () => {
    const m = await load();
    return defineComponent({
      name: 'CgchCrmPoolPage',
      setup: () => () => h(m.default, props),
    });
  };

const groups: HubGroup[] = [
  {
    label: '智能',
    items: [
      {
        key: 'salesmind',
        label: 'CRM 智枢',
        icon: 'lucide:sparkles',
        load: () => import('./claw.vue'),
      },
    ],
  },
  {
    label: '线索',
    items: [
      {
        key: 'lead',
        label: '线索列表',
        icon: 'lucide:user-plus',
        load: () => import('./lead.vue'),
      },
      {
        key: 'lead-pool',
        label: '公海线索',
        icon: 'lucide:globe',
        load: withProps(() => import('./lead.vue'), { pool: true }),
      },
    ],
  },
  {
    label: '客户',
    items: [
      {
        key: 'customer',
        label: '客户列表',
        icon: 'lucide:users',
        load: () => import('./customer.vue'),
      },
      {
        key: 'agenda',
        label: '跟进日程',
        icon: 'lucide:calendar',
        load: () => import('./agenda.vue'),
      },
      {
        key: 'customer-pool',
        label: '公海客户',
        icon: 'lucide:building-2',
        load: withProps(() => import('./customer.vue'), { pool: true }),
      },
      {
        key: 'dedup',
        label: '查重',
        icon: 'lucide:search',
        load: () => import('./dedup.vue'),
      },
    ],
  },
  {
    label: '销售与营销',
    items: [
      {
        key: 'business',
        label: '商机',
        icon: 'lucide:briefcase',
        load: () => import('./business.vue'),
      },
      {
        key: 'order',
        label: '订单管理',
        icon: 'lucide:file-text',
        load: () => import('./order.vue'),
      },
      {
        key: 'mail-batch',
        label: '邮件群发',
        icon: 'lucide:send',
        load: () => import('./mail-batch.vue'),
      },
    ],
  },
  {
    label: '配置',
    items: [
      {
        key: 'settings',
        label: 'CRM 设置',
        icon: 'lucide:sliders-horizontal',
        load: () => import('./settings.vue'),
      },
    ],
  },
];
</script>

<template>
  <SideHub :groups="groups" menu-label="CRM 菜单" />
</template>
