<script lang="ts" setup>
/**
 * 留资邮箱：阿里国际站、中国制造网、官网表单等平台会把询盘以通知邮件发到绑定的邮箱，
 * 命中规则的邮件自动解析出买家信息并建立线索。
 */
import type { ChannelLeadRuleApi } from '#/api/channel/lead-rule';

import { reactive, ref } from 'vue';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import {
  Alert,
  Button,
  Form,
  FormItem,
  Input,
  message,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
} from 'ant-design-vue';

import { deleteLeadRule, getLeadRuleList, saveLeadRule } from '#/api/channel/lead-rule';

defineProps<{ mailboxes: string[] }>();

const open = defineModel<boolean>('open', { default: false });

const rules = ref<ChannelLeadRuleApi.Rule[]>([]);
const loading = ref(false);
const sourceOptions = () => getDictOptions(DICT_TYPE.CRM_CUSTOMER_SOURCE, 'number');
function sourceLabel(v?: number) {
  return sourceOptions().find((o) => o.value === v)?.label ?? (v ?? '—');
}

const columns = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '匹配条件', key: 'match' },
  { title: '客户来源', key: 'source', width: 110 },
  { title: '启用', key: 'enabled', width: 70 },
  { title: '操作', key: 'action', width: 120 },
];

async function load() {
  loading.value = true;
  try {
    rules.value = await getLeadRuleList();
  } finally {
    loading.value = false;
  }
}

const editing = ref(false);
const saving = ref(false);
const emptyForm = (): ChannelLeadRuleApi.Rule => ({ name: '', matchField: 'from', keyword: '', source: 14, enabled: true });
const form = reactive<ChannelLeadRuleApi.Rule>(emptyForm());

function edit(row?: ChannelLeadRuleApi.Rule) {
  Object.assign(form, emptyForm(), row ? { ...row } : { id: undefined });
  editing.value = true;
}

async function save() {
  if (!form.name || !form.keyword || form.keyword.trim().length < 3) {
    message.warning('请填写名称，关键词至少 3 个字符');
    return;
  }
  saving.value = true;
  try {
    await saveLeadRule({ ...form, keyword: form.keyword.trim() });
    message.success('已保存');
    editing.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function toggle(row: ChannelLeadRuleApi.Rule, enabled: boolean) {
  await saveLeadRule({ ...row, enabled });
  row.enabled = enabled;
}

async function remove(row: ChannelLeadRuleApi.Rule) {
  await deleteLeadRule(row.id!);
  message.success('已删除');
  await load();
}

defineExpose({ load });
</script>

<template>
  <Modal v-model:open="open" title="设置留资邮箱" :width="760" :footer="null" @after-open-change="(v: boolean) => v && load()">
    <Alert type="info" show-icon class="mb-4">
      <template #message>
        <div>把平台的「询盘通知邮箱」设置为下面任一已绑定的邮箱，或在原邮箱里设置自动转发到它。</div>
        <div class="mt-1">
          已绑定：
          <template v-if="mailboxes.length">
            <Tag v-for="m in mailboxes" :key="m">{{ m }}</Tag>
          </template>
          <span v-else class="text-orange-500">还没有绑定邮箱，请先在下方绑定</span>
        </div>
        <div class="mt-1 text-xs text-gray-500">
          收到通知邮件后，系统按规则识别平台，解析买家姓名、公司、国家、邮箱、电话、需求，自动建线索并通知负责人；同一买家 30 天内不会重复建档。这类邮件不会触发 AI 自动回复。
        </div>
      </template>
    </Alert>

    <div class="mb-2 flex justify-between">
      <span class="font-medium">识别规则</span>
      <Button size="small" type="primary" @click="edit()">新增规则</Button>
    </div>
    <Table :columns="columns" :data-source="rules" :loading="loading" :pagination="false" row-key="id" size="small">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'match'">
          {{ record.matchField === 'subject' ? '主题包含' : '发件人包含' }}
          <code class="ml-1">{{ record.keyword }}</code>
        </template>
        <template v-else-if="column.key === 'source'">{{ sourceLabel(record.source) }}</template>
        <template v-else-if="column.key === 'enabled'">
          <Switch :checked="record.enabled" size="small" @change="(v: any) => toggle(record as any, !!v)" />
        </template>
        <template v-else-if="column.key === 'action'">
          <Space>
            <Button size="small" type="link" @click="edit(record as any)">修改</Button>
            <Popconfirm title="确定删除这条规则吗？" @confirm="remove(record as any)">
              <Button size="small" type="link" danger>删除</Button>
            </Popconfirm>
          </Space>
        </template>
      </template>
    </Table>

    <Form v-if="editing" layout="vertical" class="mt-4 rounded-lg border border-solid border-gray-200 p-4 dark:border-gray-700">
      <div class="grid grid-cols-2 gap-x-4">
        <FormItem label="名称" required><Input v-model:value="form.name" placeholder="例如：官网表单" /></FormItem>
        <FormItem label="客户来源">
          <Select v-model:value="form.source" :options="sourceOptions()" allow-clear />
        </FormItem>
        <FormItem label="匹配">
          <Select
            v-model:value="form.matchField"
            :options="[
              { value: 'from', label: '发件人包含' },
              { value: 'subject', label: '主题包含' },
            ]"
          />
        </FormItem>
        <FormItem label="关键词" required>
          <Input v-model:value="form.keyword" :placeholder="form.matchField === 'from' ? '例如：alibaba.com' : '例如：New Inquiry'" />
        </FormItem>
      </div>
      <div class="flex justify-end gap-2">
        <Button @click="editing = false">取消</Button>
        <Button type="primary" :loading="saving" @click="save">保存</Button>
      </div>
    </Form>
  </Modal>
</template>
