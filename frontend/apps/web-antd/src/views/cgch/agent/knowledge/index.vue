<script lang="ts" setup>
/**
 * 知识库：企业介绍、产品资料、常见问答。AI 回复时按客户提问挑选最相关的条目作为依据。
 */
import type { ChannelAiApi } from '#/api/channel/ai';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Alert,
  Button,
  Card,
  Form,
  FormItem,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Space,
  Switch,
  Table,
  TabPane,
  Tabs,
  Textarea,
} from 'ant-design-vue';

import { deleteKnowledge, getKnowledgeList, saveKnowledge } from '#/api/channel/ai';

defineOptions({ name: 'CgchAgentKnowledge' });

type Kind = ChannelAiApi.Knowledge['type'];
const TABS: { key: Kind; label: string; titleLabel: string; contentLabel: string; tip: string }[] = [
  { key: 'company', label: '企业介绍', titleLabel: '标题', contentLabel: '内容', tip: '每次回复都会带上，建议写清公司名称、主营产品、工厂规模、出口市场、认证等，控制在 1000 字以内。' },
  { key: 'product', label: '产品资料', titleLabel: '产品 / 主题', contentLabel: '资料', tip: '按产品或主题分条填写：规格、价格区间、起订量、交期、包装、付款方式等。客户问到相关内容时才会带上。' },
  { key: 'faq', label: '常见问答', titleLabel: '问题', contentLabel: '答案', tip: '把客户常问的问题和标准答案逐条录入，AI 会优先按这里的答案回复。' },
];

const active = ref<Kind>('company');
const loading = ref(false);
const all = ref<ChannelAiApi.Knowledge[]>([]);
const rows = computed(() => all.value.filter((k) => k.type === active.value));
const tab = computed(() => TABS.find((t) => t.key === active.value)!);

const columns = computed(() => [
  { title: tab.value.titleLabel, dataIndex: 'title', key: 'title', width: 240 },
  { title: tab.value.contentLabel, key: 'content' },
  { title: '启用', key: 'enabled', width: 80 },
  { title: '操作', key: 'action', width: 140 },
]);

async function load() {
  loading.value = true;
  try {
    all.value = await getKnowledgeList();
  } finally {
    loading.value = false;
  }
}

const open = ref(false);
const saving = ref(false);
const form = reactive<ChannelAiApi.Knowledge>({ type: 'company', title: '', content: '', enabled: true, sort: 0 });
function openForm(row?: ChannelAiApi.Knowledge) {
  Object.assign(form, { id: undefined, type: active.value, title: '', content: '', enabled: true, sort: 0 }, row ?? {});
  open.value = true;
}
async function save() {
  if (!form.content?.trim()) {
    message.warning(`请填写${tab.value.contentLabel}`);
    return;
  }
  saving.value = true;
  try {
    await saveKnowledge({ ...form });
    message.success('已保存');
    open.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function toggle(row: ChannelAiApi.Knowledge, enabled: boolean) {
  await saveKnowledge({ ...row, enabled });
  row.enabled = enabled;
}
async function remove(row: ChannelAiApi.Knowledge) {
  await deleteKnowledge(row.id!);
  message.success('已删除');
  await load();
}

onMounted(load);
</script>

<template>
  <Page auto-content-height>
    <Card title="知识库" :bordered="false">
      <template #extra><Button type="primary" @click="openForm()">新增{{ tab.label }}</Button></template>
      <Alert class="mb-4" type="info" show-icon message="AI 只会依据这里的资料回答价格、起订量、交期等具体问题；资料里没有的，会回复客户「确认后答复」，不会编造。" />
      <Tabs v-model:active-key="active">
        <TabPane v-for="t in TABS" :key="t.key" :tab="`${t.label} ${all.filter((k) => k.type === t.key).length}`" />
      </Tabs>
      <p class="mb-3 text-sm text-gray-500">{{ tab.tip }}</p>
      <Table :columns="columns" :data-source="rows" :loading="loading" :pagination="false" row-key="id">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'content'">
            <div class="line-clamp-3 whitespace-pre-wrap text-sm">{{ record.content }}</div>
          </template>
          <template v-else-if="column.key === 'enabled'">
            <Switch :checked="record.enabled" size="small" @change="(v: any) => toggle(record, !!v)" />
          </template>
          <template v-else-if="column.key === 'action'">
            <Space>
              <Button size="small" type="link" @click="openForm(record)">修改</Button>
              <Popconfirm title="确定删除？" @confirm="remove(record)">
                <Button size="small" type="link" danger>删除</Button>
              </Popconfirm>
            </Space>
          </template>
        </template>
        <template #emptyText>还没有{{ tab.label }}</template>
      </Table>
    </Card>

    <Modal v-model:open="open" :title="form.id ? `修改${tab.label}` : `新增${tab.label}`" :width="720" :confirm-loading="saving" @ok="save">
      <Form layout="vertical">
        <FormItem :label="tab.titleLabel"><Input v-model:value="form.title" :maxlength="200" /></FormItem>
        <FormItem :label="tab.contentLabel" required>
          <Textarea v-model:value="form.content" :rows="10" show-count :maxlength="8000" />
        </FormItem>
        <FormItem label="排序（越小越靠前）"><InputNumber v-model:value="form.sort" :min="0" /></FormItem>
      </Form>
    </Modal>
  </Page>
</template>
