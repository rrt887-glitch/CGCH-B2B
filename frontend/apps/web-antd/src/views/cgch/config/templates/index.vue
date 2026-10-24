<script lang="ts" setup>
/**
 * 模板管理：实时从 Meta 查询 WhatsApp 消息模板及审核状态。
 * 模板的新建与审核在 Meta 的 WhatsApp 管理器中完成。
 */
import type { ChannelWaApi } from '#/api/channel/whatsapp';

import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { Alert, Button, Card, Empty, RadioButton, RadioGroup, Select, Space, Table, Tag } from 'ant-design-vue';

import { getWaAccountList, getWaTemplates, templateParamCount } from '#/api/channel/whatsapp';

defineOptions({ name: 'CgchConfigTemplates' });

const router = useRouter();
const accounts = ref<ChannelWaApi.Account[]>([]);
const accountId = ref<number>();
const list = ref<ChannelWaApi.Template[]>([]);
const loading = ref(false);
const status = ref<'all' | 'APPROVED' | 'PENDING' | 'REJECTED'>('all');

const STATUS: Record<string, { color: string; label: string }> = {
  APPROVED: { color: 'green', label: '已通过' },
  PENDING: { color: 'blue', label: '审核中' },
  IN_APPEAL: { color: 'blue', label: '申诉中' },
  REJECTED: { color: 'red', label: '已拒绝' },
  PAUSED: { color: 'orange', label: '已暂停' },
  DISABLED: { color: 'default', label: '已停用' },
};
const CATEGORY: Record<string, string> = { MARKETING: '营销', UTILITY: '通知', AUTHENTICATION: '验证码' };

const rows = computed(() =>
  status.value === 'all'
    ? list.value
    : list.value.filter((t) => (status.value === 'PENDING' ? ['IN_APPEAL', 'PENDING'].includes(t.status) : t.status === status.value)),
);
const columns = [
  { title: '模板名称', dataIndex: 'name', key: 'name', width: 200 },
  { title: '语言', dataIndex: 'language', key: 'language', width: 90 },
  { title: '类别', key: 'category', width: 90 },
  { title: '状态', key: 'status', width: 100 },
  { title: '变量', key: 'params', width: 70 },
  { title: '正文', key: 'body' },
];
const body = (t: ChannelWaApi.Template) => t.components?.find((c) => c.type === 'BODY')?.text ?? '';

async function load() {
  if (!accountId.value) return;
  loading.value = true;
  try {
    list.value = await getWaTemplates(accountId.value);
  } finally {
    loading.value = false;
  }
}
watch(accountId, load);

onMounted(async () => {
  accounts.value = (await getWaAccountList()).filter((a) => !a.platform || a.platform === 'whatsapp');
  accountId.value = accounts.value[0]?.id;
});
</script>

<template>
  <Page auto-content-height>
    <Card title="WhatsApp 消息模板" :bordered="false">
      <template #extra>
        <Space>
          <Select
            v-model:value="accountId"
            class="w-64"
            placeholder="选择号码"
            :options="accounts.map((a) => ({ label: `${a.name || ''} ${a.displayPhone || a.phoneNumberId}`.trim(), value: a.id }))"
          />
          <Button :loading="loading" :disabled="!accountId" @click="load">刷新</Button>
          <Button type="primary" href="https://business.facebook.com/wa/manage/message-templates/" target="_blank">
            去 Meta 新建模板
          </Button>
        </Space>
      </template>

      <Empty v-if="accounts.length === 0" description="还没有绑定 WhatsApp 号码">
        <Button type="primary" @click="router.push('/v3/channel')">去绑定号码</Button>
      </Empty>
      <template v-else>
        <Alert
          class="mb-4"
          type="info"
          show-icon
          message="客户 24 小时内没有回复时，只能发送「已通过」的模板。模板在 Meta 的 WhatsApp 管理器中新建，一般几分钟到一天内审核完成，这里会自动同步状态。"
        />
        <RadioGroup v-model:value="status" class="mb-3" button-style="solid">
          <RadioButton value="all">全部 {{ list.length }}</RadioButton>
          <RadioButton value="APPROVED">已通过</RadioButton>
          <RadioButton value="PENDING">审核中</RadioButton>
          <RadioButton value="REJECTED">已拒绝</RadioButton>
        </RadioGroup>
        <Table :columns="columns" :data-source="rows" :loading="loading" :pagination="false" :row-key="(t: any) => `${t.name}|${t.language}`">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'category'">{{ CATEGORY[record.category] ?? record.category }}</template>
            <template v-else-if="column.key === 'status'">
              <Tag :color="STATUS[record.status]?.color">{{ STATUS[record.status]?.label ?? record.status }}</Tag>
            </template>
            <template v-else-if="column.key === 'params'">{{ templateParamCount(record as any) }}</template>
            <template v-else-if="column.key === 'body'">
              <div class="whitespace-pre-wrap text-sm">{{ body(record as any) }}</div>
            </template>
          </template>
          <template #emptyText>该号码下还没有模板</template>
        </Table>
      </template>
    </Card>
  </Page>
</template>
