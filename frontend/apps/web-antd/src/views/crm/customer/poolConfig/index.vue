<script lang="ts" setup>
import type { CrmCustomerPoolConfigApi } from '#/api/crm/customer/poolConfig';

import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Alert, Button, Card, message, Spin } from 'ant-design-vue';

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
  <Page auto-content-height>
    <Card title="客户公海规则设置">
      <Alert v-if="loadFailed" type="error" message="公海规则加载失败" show-icon>
        <template #action><Button size="small" @click="getConfigInfo">重试</Button></template>
      </Alert>
      <Spin :spinning="loading || saving">
        <Form v-show="!loadFailed" class="w-1/4" />
      </Spin>
    </Card>
  </Page>
</template>
