<script lang="ts" setup>
/** 计费开启时显示智点余额与充值，关闭时（用企业自己的大模型 API Key）只显示 AI 用量统计 */
import { onMounted, ref } from 'vue';

import { Spin } from 'ant-design-vue';

import { getCreditSummary } from '#/api/channel/credit';

import Billing from './index.vue';
import Usage from './usage.vue';

defineOptions({ name: 'CgchCreditsEntry' });

const billing = ref<boolean>();
onMounted(async () => {
  billing.value = !!(await getCreditSummary().catch(() => ({ billingEnabled: false }))).billingEnabled;
});
</script>

<template>
  <Spin v-if="billing === undefined" class="block py-20" />
  <Billing v-else-if="billing" />
  <Usage v-else />
</template>
