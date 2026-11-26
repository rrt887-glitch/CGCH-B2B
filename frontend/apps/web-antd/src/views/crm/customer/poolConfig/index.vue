<script lang="ts" setup>
import type { CrmCustomerPoolConfigApi } from '#/api/crm/customer/poolConfig';

import { onMounted, ref } from 'vue';

import { Alert, Button, message, Spin } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import {
  getCustomerPoolConfig,
  saveCustomerPoolConfig,
} from '#/api/crm/customer/poolConfig';
import { $t } from '#/locales';

import { schema } from './data';

const loading = ref(true);
const loadFailed = ref(false);
const saving = ref(false);

const [Form, formApi] = useVbenForm({
  commonConfig: {
    labelWidth: 120,
  },
  layout: 'horizontal',
  schema,
  handleSubmit,
});

/** 提交表单 */
async function handleSubmit() {
  if (loading.value || loadFailed.value || saving.value) return;
  saving.value = true;
  try {
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values =
      (await formApi.getValues()) as CrmCustomerPoolConfigApi.CustomerPoolConfig;
    const data = { ...values };
    if (!data.enabled) {
      data.contactExpireDays = undefined;
      data.dealExpireDays = undefined;
      data.notifyEnabled = false;
    }
    if (!data.notifyEnabled) data.notifyDays = undefined;
    await saveCustomerPoolConfig(data);
    await formApi.setValues(data);
    message.success($t('ui.actionMessage.operationSuccess'));
  } catch {
    // 请求层已提示失败，保留表单供重试。
  } finally {
    saving.value = false;
  }
}

/** 获取配置 */
async function getConfigInfo() {
  loading.value = true;
  loadFailed.value = false;
  try {
    const res = await getCustomerPoolConfig();
    await formApi.setValues(res ?? { enabled: false, notifyEnabled: false });
  } catch {
    loadFailed.value = true;
  } finally {
    loading.value = false;
  }
}

/** 初始化 */
onMounted(() => {
  getConfigInfo();
});
</script>

<template>
  <div class="max-w-xl rounded-2xl bg-[#F8F8FB] p-6">
    <div class="mb-4">
      <div class="text-[15px] font-semibold text-[#1D1D1F]">客户公海回收规则</div>
      <div class="mt-0.5 text-xs text-[#86868B]">
        开启后，超期未跟进或未成交的客户将自动退回公海供团队重新领取。
      </div>
    </div>
    <Alert v-if="loadFailed" type="error" message="公海规则加载失败" show-icon class="mb-4">
      <template #action><Button size="small" shape="round" @click="getConfigInfo">重试</Button></template>
    </Alert>
    <Spin :spinning="loading || saving">
      <Form v-show="!loadFailed" class="w-full" />
    </Spin>
  </div>
</template>
