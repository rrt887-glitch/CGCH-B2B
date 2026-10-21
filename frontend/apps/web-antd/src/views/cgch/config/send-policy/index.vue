<script lang="ts" setup>
/**
 * 发送策略：每个邮箱 / WhatsApp 号码的发送频率上限、退订与退信自动处理、退订与黑名单。
 * 目的是保护发信信誉，避免账号被邮件服务商或 WhatsApp 限制。
 */
import type { ChannelPolicyApi } from '#/api/channel/policy';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Alert,
  Button,
  Card,
  Col,
  Form,
  FormItem,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Progress,
  Radio,
  RadioGroup,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Textarea,
} from 'ant-design-vue';

import {
  createSuppression,
  deleteSuppression,
  getSendPolicy,
  getSendUsage,
  getSuppressionPage,
  saveSendPolicy,
} from '#/api/channel/policy';

defineOptions({ name: 'CgchConfigSendPolicy' });

const REASON: Record<string, { color: string; label: string }> = {
  unsubscribe: { color: 'orange', label: '客户退订' },
  bounce: { color: 'red', label: '退信（地址无效）' },
  manual: { color: 'default', label: '手动加入' },
};

const policy = ref<ChannelPolicyApi.Policy>();
const usage = ref<ChannelPolicyApi.Usage[]>([]);
const saving = ref(false);
async function loadPolicy() {
  [policy.value, usage.value] = await Promise.all([getSendPolicy(), getSendUsage()]);
}
async function save() {
  if (!policy.value) return;
  saving.value = true;
  try {
    await saveSendPolicy(policy.value);
    message.success('已保存');
  } finally {
    saving.value = false;
  }
}
function limitOf(u: ChannelPolicyApi.Usage, kind: 'day' | 'hour') {
  const p = policy.value;
  if (!p) return 0;
  return u.channel === 'mail' ? (kind === 'hour' ? p.mailHourlyLimit : p.mailDailyLimit) : (kind === 'hour' ? p.waHourlyLimit : p.waDailyLimit);
}
function pct(u: ChannelPolicyApi.Usage, kind: 'day' | 'hour') {
  const max = limitOf(u, kind);
  return max ? Math.min(100, Math.round((u[kind] / max) * 100)) : 0;
}

// ===== 名单 =====
const filter = reactive({ channel: undefined as string | undefined, reason: undefined as string | undefined, keyword: '' });
const list = ref<ChannelPolicyApi.Suppression[]>([]);
const total = ref(0);
const pageNo = ref(1);
const loading = ref(false);
async function loadList() {
  loading.value = true;
  try {
    const res = await getSuppressionPage({ pageNo: pageNo.value, pageSize: 20, ...filter, keyword: filter.keyword || undefined });
    list.value = res.list;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
}
const columns = [
  { title: '渠道', key: 'channel', width: 100 },
  { title: '邮箱 / 域名 / 号码', dataIndex: 'value', key: 'value' },
  { title: '原因', key: 'reason', width: 140 },
  { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
  { title: '时间', dataIndex: 'createTime', key: 'time', width: 150 },
  { title: '操作', key: 'action', width: 90 },
];
const addOpen = ref(false);
const add = reactive({ channel: 'mail', value: '', remark: '' });
async function doAdd() {
  if (!add.value.trim()) {
    message.warning('请填写邮箱、域名或号码');
    return;
  }
  await createSuppression({ ...add });
  message.success('已加入，之后不会再给它发送任何消息');
  addOpen.value = false;
  Object.assign(add, { value: '', remark: '' });
  await loadList();
}
async function remove(id: number) {
  await deleteSuppression(id);
  message.success('已移出');
  await loadList();
}

onMounted(async () => {
  await Promise.all([loadPolicy(), loadList()]);
});
</script>

<template>
  <Page auto-content-height>
    <Card v-if="policy" title="发送策略" :bordered="false" class="mb-4">
      <template #extra><Button type="primary" :loading="saving" @click="save">保存</Button></template>
      <Alert class="mb-4" type="info" show-icon
             message="短时间内发送过多、反复给退订或无效地址发信，都会让邮箱和 WhatsApp 号码被服务商判为垃圾发送而限制或封禁。这里的设置对所有邮箱和号码生效。" />
      <Row :gutter="24">
        <Col :xs="24" :lg="12">
          <h4 class="mb-3 font-medium">发送频率上限（每个邮箱 / 号码，0 表示不限）</h4>
          <Form layout="inline" class="mb-3">
            <FormItem label="邮件每小时"><InputNumber v-model:value="policy.mailHourlyLimit" :min="0" :max="100000" /></FormItem>
            <FormItem label="每天"><InputNumber v-model:value="policy.mailDailyLimit" :min="0" :max="100000" /></FormItem>
          </Form>
          <Form layout="inline" class="mb-3">
            <FormItem label="WhatsApp 每小时"><InputNumber v-model:value="policy.waHourlyLimit" :min="0" :max="100000" /></FormItem>
            <FormItem label="每天"><InputNumber v-model:value="policy.waDailyLimit" :min="0" :max="100000" /></FormItem>
          </Form>
          <div class="mb-1 flex items-center gap-2">
            <Switch v-model:checked="policy.applyToManual" size="small" />
            <span class="text-sm">也限制业务员手动发送</span>
          </div>
          <div class="text-xs text-gray-500">默认只限制 AI 自动回复、AI 起草和客户唤醒等批量发送；超出上限的 AI 回复会留为草稿，等业务员处理。</div>
        </Col>
        <Col :xs="24" :lg="12">
          <h4 class="mb-3 font-medium">退订与退信</h4>
          <div class="mb-3 flex items-start gap-2">
            <Switch v-model:checked="policy.autoUnsubscribe" size="small" class="mt-0.5" />
            <div><div class="text-sm">自动识别退订</div><div class="text-xs text-gray-500">客户回复 unsubscribe、STOP、退订等时，自动加入退订名单，AI 不再回复（只看客户本次写的内容，不看引用的旧邮件）</div></div>
          </div>
          <div class="mb-3 flex items-start gap-2">
            <Switch v-model:checked="policy.autoBounce" size="small" class="mt-0.5" />
            <div><div class="text-sm">自动处理退信</div><div class="text-xs text-gray-500">收到「地址不存在」等退信时，把无效地址加入黑名单，避免反复发信拖低信誉</div></div>
          </div>
          <div class="mb-2 flex items-start gap-2">
            <Switch v-model:checked="policy.unsubscribeFooter" size="small" class="mt-0.5" />
            <div><div class="text-sm">批量邮件附退订说明</div><div class="text-xs text-gray-500">客户唤醒等批量邮件末尾加上一句退订说明，符合多数国家的营销邮件要求</div></div>
          </div>
          <Textarea v-model:value="policy.footerText" :rows="2" :maxlength="500" :disabled="!policy.unsubscribeFooter" />
        </Col>
      </Row>

      <h4 class="mb-2 mt-6 font-medium">当前发送量</h4>
      <div class="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
        <div v-for="u in usage" :key="u.channel + u.accountId" class="rounded border p-3 dark:border-gray-700">
          <div class="mb-2 flex items-center gap-2">
            <Tag :color="u.channel === 'mail' ? 'blue' : 'green'">{{ u.channel === 'mail' ? '邮件' : 'WhatsApp' }}</Tag>
            <span class="truncate text-sm">{{ u.name }}</span>
          </div>
          <div class="text-xs text-gray-500">最近 1 小时 {{ u.hour }}{{ limitOf(u, 'hour') ? ` / ${limitOf(u, 'hour')}` : '' }}</div>
          <Progress :percent="pct(u, 'hour')" :show-info="false" size="small" :status="pct(u, 'hour') >= 90 ? 'exception' : 'normal'" />
          <div class="text-xs text-gray-500">最近 24 小时 {{ u.day }}{{ limitOf(u, 'day') ? ` / ${limitOf(u, 'day')}` : '' }}</div>
          <Progress :percent="pct(u, 'day')" :show-info="false" size="small" :status="pct(u, 'day') >= 90 ? 'exception' : 'normal'" />
        </div>
      </div>
    </Card>

    <Card title="退订与黑名单" :bordered="false">
      <template #extra><Button @click="addOpen = true">加入黑名单</Button></template>
      <div class="mb-3 text-sm text-gray-500">名单中的收件人不会收到任何邮件或 WhatsApp 消息，手动发送也会被拦截。</div>
      <Space class="mb-3" wrap>
        <Select v-model:value="filter.channel" allow-clear placeholder="渠道" class="w-32"
                :options="[{ value: 'mail', label: '邮件' }, { value: 'whatsapp', label: 'WhatsApp' }]" />
        <Select v-model:value="filter.reason" allow-clear placeholder="原因" class="w-36"
                :options="Object.entries(REASON).map(([v, r]) => ({ value: v, label: r.label }))" />
        <Input v-model:value="filter.keyword" placeholder="邮箱、域名或号码" class="w-56" @press-enter="() => { pageNo = 1; loadList(); }" />
        <Button type="primary" @click="() => { pageNo = 1; loadList(); }">查询</Button>
      </Space>
      <Table :columns="columns" :data-source="list" :loading="loading" row-key="id" size="small"
             :pagination="{ current: pageNo, pageSize: 20, total, onChange: (p: number) => { pageNo = p; loadList(); } }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'channel'">
            <Tag :color="record.channel === 'mail' ? 'blue' : 'green'">{{ record.channel === 'mail' ? '邮件' : 'WhatsApp' }}</Tag>
          </template>
          <template v-else-if="column.key === 'value'">{{ record.channel === 'whatsapp' ? `+${record.value}` : record.value }}</template>
          <template v-else-if="column.key === 'reason'"><Tag :color="REASON[record.reason]?.color">{{ REASON[record.reason]?.label }}</Tag></template>
          <template v-else-if="column.key === 'action'">
            <Popconfirm title="移出名单后可以再次发送，确定？" @confirm="remove(record.id)">
              <Button size="small" type="link">移出</Button>
            </Popconfirm>
          </template>
        </template>
        <template #emptyText>名单为空</template>
      </Table>
    </Card>

    <Modal v-model:open="addOpen" title="加入黑名单" @ok="doAdd">
      <Form layout="vertical">
        <FormItem label="渠道">
          <RadioGroup v-model:value="add.channel">
            <Radio value="mail">邮件</Radio>
            <Radio value="whatsapp">WhatsApp</Radio>
          </RadioGroup>
        </FormItem>
        <FormItem :label="add.channel === 'mail' ? '邮箱或域名（填域名会拦截整个域名，例如 example.com）' : 'WhatsApp 号码（含国家区号）'">
          <Input v-model:value="add.value" :placeholder="add.channel === 'mail' ? 'someone@example.com 或 example.com' : '+44 7900 000123'" />
        </FormItem>
        <FormItem label="备注"><Input v-model:value="add.remark" :maxlength="200" /></FormItem>
      </Form>
    </Modal>
  </Page>
</template>
