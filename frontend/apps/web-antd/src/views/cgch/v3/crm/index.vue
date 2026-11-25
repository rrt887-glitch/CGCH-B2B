<script lang="ts" setup>
/**
 * 管理客户（CRM）：左侧分组二级侧栏（线索 / 客户 / 销售与营销 / 配置）。
 * CRM 智枢已提升为左侧主导航独立入口，访问 ?tab=salesmind 时直接全宽展示。
 */
import type { HubGroup } from '../components/types';

import { computed, defineAsyncComponent, defineComponent, h } from 'vue';
import { useRoute } from 'vue-router';

import { ApSkeleton } from '#/components/apple';

import SideHub from '../components/side-hub.vue';

defineOptions({ name: 'CgchV3Crm' });

const route = useRoute();
const isSalesmind = computed(() => route.query.tab === 'salesmind');
const SalesmindView = defineAsyncComponent({
  loader: () => import('./claw.vue'),
  loadingComponent: ApSkeleton,
  delay: 140,
});

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
  <SalesmindView v-if="isSalesmind" />
  <SideHub v-else :groups="groups" menu-label="管理客户" />
</template>
