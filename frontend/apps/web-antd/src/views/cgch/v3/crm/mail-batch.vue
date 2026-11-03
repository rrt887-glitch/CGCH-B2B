<script lang="ts" setup>
/**
 * 邮件群发：选客户（或粘贴邮箱）→ 写邮件（可选模板，支持 {name} {company}）→ 选发件邮箱和速率 → 验证收件人 → 开始发送。
 * 每封带退订链接，退订的人以后不会再收到；发送进度、失败原因、回复情况都在任务详情里。
 */
import type { ChannelMailApi } from '#/api/channel/mail';
import type { MailBatchApi } from '#/api/channel/mail-batch';
import type { MailTemplateApi } from '#/api/channel/mail-template';

import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import {
  Alert,
  Button,
  Drawer,
  Empty,
  Input,
  InputNumber,
  InputSearch,
  message,
  Modal,
  Progress,
  Segmented,
  Select,
  Spin,
  Steps,
  Table,
  Tag,
  Textarea,
} from 'ant-design-vue';

import { getCustomerPage } from '#/api/crm/customer';
import { getMailAccountList } from '#/api/channel/mail';
import {
  BATCH_STATUS,
  batchDraftCustomers,
  cancelMailBatch,
  createMailBatch,
  deleteMailBatch,
  getMailBatch,
  getMailBatchPage,
  ITEM_STATUS,
  startMailBatch,
} from '#/api/channel/mail-batch';
import { getMailTemplates } from '#/api/channel/mail-template';
import { fmtDateTime } from '#/utils/datetime';

defineOptions({ name: 'CgchCrmMailBatch' });

const route = useRoute();
const router = useRouter();

// ===== 任务列表 =====
const loading = ref(false);
const list = ref<MailBatchApi.Batch[]>([]);
async function load(silent = false) {
  if (!silent) loading.value = true;
  try {
    list.value = (await getMailBatchPage({ pageNo: 1, pageSize: 50 })).list;
  } finally {
    loading.value = false;
  }
}
const sentOf = (b: MailBatchApi.Batch) => b.counts.sent ?? 0;
const doneOf = (b: MailBatchApi.Batch) =>
  (b.counts.sent ?? 0) + (b.counts.failed ?? 0) + (b.counts.invalid ?? 0) + (b.counts.suppressed ?? 0) + (b.counts.cancelled ?? 0);
const percent = (b: MailBatchApi.Batch) => (b.total ? Math.round((doneOf(b) / b.total) * 100) : 0);
let timer: ReturnType<typeof setInterval> | undefined;
onBeforeUnmount(() => timer && clearInterval(timer));

// ===== 新建：四步 =====
const createOpen = ref(false);
const step = ref(0);
const accounts = ref<ChannelMailApi.Account[]>([]);
const templates = ref<MailTemplateApi.Template[]>([]);
const form = ref<MailBatchApi.CreateReq & { templateId?: number }>(blankForm());
function blankForm(): MailBatchApi.CreateReq & { templateId?: number } {
  return {
    name: `邮件群发 ${new Date().toLocaleDateString('zh-CN')}`,
    subject: '',
    content: '',
    contentType: 'text',
    customerIds: [],
    emails: [],
    accountIds: [],
    ratePerMinute: 5,
  };
}

// 第一步：选客户
interface Row {
  id: number;
  name: string;
  companyName?: string;
  email?: string;
}
const pickMode = ref<'crm' | 'paste'>('crm');
const keyword = ref('');
const customers = ref<Row[]>([]);
const customerLoading = ref(false);
const picked = ref<Map<number, Row>>(new Map());
const pastedEmails = ref('');
async function searchCustomers() {
  customerLoading.value = true;
  try {
    const res: any = await getCustomerPage({ pageNo: 1, pageSize: 50, name: keyword.value.trim() || undefined, sceneType: 1 } as any);
    customers.value = res.list ?? [];
  } finally {
    customerLoading.value = false;
  }
}
const selectedRowKeys = computed(() => [...picked.value.keys()]);
function onSelect(keys: (number | string)[]) {
  const visible = new Set(customers.value.map((c) => c.id));
  const next = new Map([...picked.value].filter(([id]) => !visible.has(id)));
  for (const k of keys) {
    const c = customers.value.find((x) => x.id === Number(k));
    if (c) next.set(c.id, c);
  }
  picked.value = next;
}
const pastedList = computed(() =>
  pastedEmails.value
    .split(/[\s,;，；]+/)
    .map((s) => s.trim())
    .filter(Boolean),
);
const recipientCount = computed(() => picked.value.size + pastedList.value.length);
const pickedNoEmail = computed(() => [...picked.value.values()].filter((c) => !c.email).length);

// 第二步：写邮件
function useTemplate(id?: number) {
  const t = templates.value.find((x) => x.id === id);
  if (!t) {
    form.value.contentType = 'text';
    return;
  }
  form.value.subject = t.subject;
  form.value.content = t.html;
  form.value.contentType = 'html';
}

// 第三步：发件邮箱与速率
const RATE_OPTIONS = [
  { label: '慢（每分钟 2 封）', value: 2 },
  { label: '标准（每分钟 5 封）', value: 5 },
  { label: '快（每分钟 10 封）', value: 10 },
];
const minutes = computed(() => Math.ceil(recipientCount.value / Math.max(1, form.value.ratePerMinute || 1)));

const saving = ref(false);
function next() {
  if (step.value === 0 && recipientCount.value === 0) {
    message.warning('请选择客户或粘贴邮箱');
    return;
  }
  if (step.value === 1 && (!form.value.subject.trim() || !form.value.content.trim())) {
    message.warning('请填写邮件主题和正文');
    return;
  }
  if (step.value === 2) {
    if (!form.value.accountIds.length) {
      message.warning('请选择发件邮箱');
      return;
    }
    void createDraft();
    return;
  }
  step.value++;
}

// 第四步：验证结果，确认后开始发送
const draft = ref<MailBatchApi.Batch>();
async function createDraft() {
  saving.value = true;
  try {
    const id = await createMailBatch({
      ...form.value,
      customerIds: [...picked.value.keys()],
      emails: pastedList.value,
    });
    draft.value = await getMailBatch(id);
    step.value = 3;
    load(true);
  } finally {
    saving.value = false;
  }
}
async function startDraft() {
  if (!draft.value) return;
  await startMailBatch(draft.value.id);
  message.success('已开始发送，可在任务详情里看进度');
  createOpen.value = false;
  await load(true);
}

async function openCreate(preset?: { id: number; name: string }[]) {
  form.value = blankForm();
  step.value = 0;
  draft.value = undefined;
  picked.value = new Map((preset ?? []).map((c) => [c.id, { id: c.id, name: c.name }]));
  pickMode.value = 'crm';
  pastedEmails.value = '';
  createOpen.value = true;
  const [acc, tpl] = await Promise.all([getMailAccountList().catch(() => []), getMailTemplates({ enabled: true }).catch(() => [])]);
  accounts.value = acc.filter((a) => a.enabled !== false);
  templates.value = tpl;
  if (accounts.value.length === 1) form.value.accountIds = [accounts.value[0]!.id!];
  searchCustomers();
}

// ===== 详情 =====
const detailOpen = ref(false);
const detail = ref<MailBatchApi.Batch>();
const detailFilter = ref<string>('');
async function openDetail(b: MailBatchApi.Batch) {
  detailFilter.value = '';
  detail.value = await getMailBatch(b.id);
  detailOpen.value = true;
}
watch(detailFilter, async (v) => {
  if (detail.value) detail.value = await getMailBatch(detail.value.id, v || undefined);
});
async function cancel(b: MailBatchApi.Batch) {
  Modal.confirm({
    title: `取消「${b.name}」？`,
    content: '还没发的邮件不再发送，已发出的保留记录。',
    okText: '取消发送',
    cancelText: '再想想',
    onOk: async () => {
      await cancelMailBatch(b.id);
      message.success('已取消');
      await load(true);
      if (detail.value?.id === b.id) detail.value = await getMailBatch(b.id);
    },
  });
}
async function remove(b: MailBatchApi.Batch) {
  Modal.confirm({
    title: `删除「${b.name}」？`,
    content: '删除任务和发送明细，已发出的邮件仍在邮箱的已发送里。',
    okText: '删除',
    okButtonProps: { danger: true },
    onOk: async () => {
      await deleteMailBatch(b.id);
      await load(true);
    },
  });
}
const accountEmail = (id?: number) => accounts.value.find((a) => a.id === id)?.email ?? '';

onMounted(async () => {
  await load();
  accounts.value = await getMailAccountList().catch(() => []);
  timer = setInterval(() => {
    if (!document.hidden && list.value.some((b) => b.status === 'sending')) {
      load(true);
      if (detailOpen.value && detail.value?.status === 'sending') getMailBatch(detail.value.id, detailFilter.value || undefined).then((d) => (detail.value = d));
    }
  }, 5000);
  // 从客户列表勾选客户后跳过来
  if (route.query.new === '1' || batchDraftCustomers.value.length) {
    const preset = batchDraftCustomers.value;
    batchDraftCustomers.value = [];
    router.replace({ query: { ...route.query, new: undefined } });
    openCreate(preset);
  }
});

const ITEM_FILTERS = [
  { label: '全部', value: '' },
  { label: '已发送', value: 'sent' },
  { label: '待发送', value: 'pending' },
  { label: '失败', value: 'failed' },
  { label: '邮箱无效', value: 'invalid' },
  { label: '已退订跳过', value: 'suppressed' },
];
</script>

<template>
  <div class="flex h-full flex-col gap-4 overflow-auto rounded-xl bg-white p-5">
    <div class="flex flex-wrap items-start gap-3">
      <div class="min-w-[240px] flex-1">
        <h2 class="m-0 text-lg font-semibold text-[#111827]">邮件群发</h2>
        <p class="m-0 mt-1 text-[13px] text-[#4B5563]">
          给一批客户发同一封邮件：自动验证邮箱、按速率从多个邮箱轮流发出，每封带退订链接，退订的人以后不会再收到。
        </p>
      </div>
      <Button type="primary" @click="openCreate()"><IconifyIcon icon="lucide:plus" class="mr-1 inline size-4 align-[-3px]" />新建群发</Button>
    </div>

    <Spin :spinning="loading">
      <Empty v-if="!loading && list.length === 0" class="py-16" description="还没有群发任务">
        <Button type="primary" @click="openCreate()">新建第一个群发</Button>
      </Empty>
      <div v-else class="flex flex-col gap-2.5">
        <div
          v-for="b in list"
          :key="b.id"
          class="grid cursor-pointer grid-cols-[minmax(0,2fr)_minmax(0,1.6fr)_120px_150px] items-center gap-4 rounded-xl border border-solid border-[#E5E7EB] px-4 py-3 hover:border-[#BFDBFE]"
          @click="openDetail(b)"
        >
          <div class="min-w-0">
            <div class="flex items-center gap-2">
              <span class="truncate font-semibold text-[#111827]">{{ b.name }}</span>
              <Tag :color="BATCH_STATUS[b.status].color" class="m-0">{{ BATCH_STATUS[b.status].label }}</Tag>
            </div>
            <div class="mt-0.5 truncate text-xs text-[#6B7280]">{{ b.subject }} · {{ fmtDateTime(b.createTime, 'MM-DD HH:mm') }}</div>
          </div>
          <div>
            <Progress :percent="percent(b)" size="small" :status="b.status === 'sending' ? 'active' : 'normal'" :show-info="false" />
            <div class="text-xs text-[#4B5563]">
              已发 {{ sentOf(b) }} / {{ b.total }}
              <span v-if="b.counts.failed" class="ml-2 text-[#B91C1C]">失败 {{ b.counts.failed }}</span>
              <span v-if="b.counts.invalid" class="ml-2 text-[#B45309]">无效 {{ b.counts.invalid }}</span>
              <span v-if="b.counts.suppressed" class="ml-2 text-[#6B7280]">退订跳过 {{ b.counts.suppressed }}</span>
            </div>
          </div>
          <div class="text-xs text-[#6B7280]">每分钟 {{ b.ratePerMinute }} 封</div>
          <div class="flex justify-end gap-2" @click.stop>
            <Button v-if="b.status === 'draft'" size="small" type="primary" @click="startMailBatch(b.id).then(() => load(true))">开始发送</Button>
            <Button v-if="b.status === 'sending' || b.status === 'draft'" size="small" @click="cancel(b)">取消</Button>
            <Button v-else size="small" danger @click="remove(b)">删除</Button>
          </div>
        </div>
      </div>
    </Spin>

    <!-- 新建 -->
    <Drawer v-model:open="createOpen" title="新建邮件群发" :width="760" destroy-on-close>
      <Steps :current="step" size="small" class="mb-6" :items="[{ title: '选收件人' }, { title: '写邮件' }, { title: '发送设置' }, { title: '确认发送' }]" />

      <template v-if="step === 0">
        <Segmented
          v-model:value="pickMode"
          :options="[
            { label: '从 CRM 客户里选', value: 'crm' },
            { label: '粘贴邮箱', value: 'paste' },
          ]"
          class="mb-3"
        />
        <template v-if="pickMode === 'crm'">
          <InputSearch v-model:value="keyword" allow-clear placeholder="搜索客户名称" class="mb-2" @search="searchCustomers" />
          <Table
            size="small"
            row-key="id"
            :loading="customerLoading"
            :data-source="customers"
            :pagination="false"
            :scroll="{ y: 360 }"
            :row-selection="{ selectedRowKeys, onChange: onSelect }"
            :columns="[
              { title: '客户', dataIndex: 'name', ellipsis: true },
              { title: '公司', dataIndex: 'companyName', ellipsis: true },
              { title: '邮箱', dataIndex: 'email', ellipsis: true },
            ]"
          >
            <template #bodyCell="{ column, record }">
              <span v-if="column.dataIndex === 'email' && !record.email" class="text-xs text-[#B45309]">没有邮箱</span>
            </template>
          </Table>
        </template>
        <template v-else>
          <Textarea v-model:value="pastedEmails" :auto-size="{ minRows: 8, maxRows: 14 }" placeholder="每行一个邮箱，也可以用逗号、分号隔开" />
        </template>
        <div class="mt-3 text-sm text-[#374151]">
          已选 <b>{{ recipientCount }}</b> 个收件人
          <span v-if="pickedNoEmail" class="ml-2 text-xs text-[#B45309]">其中 {{ pickedNoEmail }} 位客户没有邮箱，会被跳过</span>
        </div>
      </template>

      <template v-else-if="step === 1">
        <div class="flex flex-col gap-3">
          <Select
            v-model:value="form.templateId"
            allow-clear
            placeholder="使用邮件模板（可选）"
            :options="templates.map((t) => ({ label: t.name, value: t.id }))"
            @change="(v: any) => useTemplate(v)"
          />
          <Input v-model:value="form.subject" :maxlength="250" placeholder="邮件主题，例如：New hair dryer models for {company}" />
          <div v-if="form.contentType === 'html'" class="rounded-lg border border-solid border-[#E5E7EB] p-3">
            <div class="mb-2 flex items-center justify-between text-xs text-[#6B7280]">
              <span>模板正文预览</span>
              <a @click="form.contentType = 'text'; form.templateId = undefined; form.content = ''">改为自己写</a>
            </div>
            <iframe :srcdoc="form.content" class="h-72 w-full rounded border-0 bg-white" sandbox="" title="邮件预览"></iframe>
          </div>
          <Textarea
            v-else
            v-model:value="form.content"
            :auto-size="{ minRows: 10, maxRows: 18 }"
            placeholder="Hi {name},&#10;&#10;我们推出了新款……"
          />
          <Alert type="info" show-icon message="可用变量：{name} 客户称呼、{company} 公司名称。系统会在邮件末尾自动加上退订链接。" />
        </div>
      </template>

      <template v-else-if="step === 2">
        <div class="flex flex-col gap-4">
          <div>
            <div class="mb-1.5 text-sm font-medium text-[#374151]">发件邮箱（选多个时轮流发送）</div>
            <Select
              v-model:value="form.accountIds"
              mode="multiple"
              class="w-full"
              placeholder="选择发件邮箱"
              :options="accounts.map((a) => ({ label: a.email, value: a.id }))"
              :not-found-content="'还没有绑定邮箱，请先到「渠道」添加'"
            />
          </div>
          <div>
            <div class="mb-1.5 text-sm font-medium text-[#374151]">发送速度</div>
            <div class="flex flex-wrap items-center gap-3">
              <Segmented v-model:value="form.ratePerMinute" :options="RATE_OPTIONS" />
              <span class="text-sm text-[#4B5563]">或自定义每分钟</span>
              <InputNumber v-model:value="form.ratePerMinute" :min="1" :max="60" class="w-20" />
              <span class="text-sm text-[#4B5563]">封</span>
            </div>
            <div class="mt-2 text-xs text-[#6B7280]">
              {{ recipientCount }} 封大约需要 {{ minutes }} 分钟。发太快容易被对方邮箱判为垃圾邮件；还会受「发送策略」里每个邮箱每小时 / 每天的上限限制，达到上限会自动暂停一会儿再继续。
            </div>
          </div>
          <div>
            <div class="mb-1.5 text-sm font-medium text-[#374151]">任务名称</div>
            <Input v-model:value="form.name" :maxlength="100" />
          </div>
        </div>
      </template>

      <template v-else-if="draft">
        <div class="flex flex-col gap-4">
          <div class="grid grid-cols-3 gap-3">
            <div class="rounded-xl border border-solid border-[#E5E7EB] p-3">
              <div class="text-xs text-[#6B7280]">将发送</div>
              <div class="text-2xl font-semibold text-[#15803D]">{{ draft.counts.pending ?? 0 }}</div>
            </div>
            <div class="rounded-xl border border-solid border-[#E5E7EB] p-3">
              <div class="text-xs text-[#6B7280]">邮箱无效</div>
              <div class="text-2xl font-semibold text-[#B45309]">{{ draft.counts.invalid ?? 0 }}</div>
            </div>
            <div class="rounded-xl border border-solid border-[#E5E7EB] p-3">
              <div class="text-xs text-[#6B7280]">已退订跳过</div>
              <div class="text-2xl font-semibold text-[#6B7280]">{{ draft.counts.suppressed ?? 0 }}</div>
            </div>
          </div>
          <Table
            size="small"
            row-key="id"
            :data-source="(draft.items ?? []).filter((i) => i.status !== 'pending')"
            :pagination="{ pageSize: 8 }"
            :columns="[
              { title: '邮箱', dataIndex: 'email', ellipsis: true },
              { title: '状态', dataIndex: 'status', width: 110 },
              { title: '原因', dataIndex: 'reason', ellipsis: true },
            ]"
            :locale="{ emptyText: '所有收件人都通过了检查' }"
          >
            <template #bodyCell="{ column, record }">
              <Tag v-if="column.dataIndex === 'status'" :color="ITEM_STATUS[record.status as MailBatchApi.ItemStatus].color">
                {{ ITEM_STATUS[record.status as MailBatchApi.ItemStatus].label }}
              </Tag>
            </template>
          </Table>
        </div>
      </template>

      <template #footer>
        <div class="flex justify-between">
          <Button v-if="step > 0 && step < 3" @click="step--">上一步</Button>
          <span v-else></span>
          <div class="flex gap-2">
            <template v-if="step < 3">
              <Button type="primary" :loading="saving" @click="next">{{ step === 2 ? '检查收件人' : '下一步' }}</Button>
            </template>
            <template v-else>
              <Button @click="createOpen = false">先存为草稿</Button>
              <Button type="primary" :disabled="!(draft?.counts.pending ?? 0)" @click="startDraft">开始发送 {{ draft?.counts.pending ?? 0 }} 封</Button>
            </template>
          </div>
        </div>
      </template>
    </Drawer>

    <!-- 详情 -->
    <Drawer v-model:open="detailOpen" :title="detail?.name" :width="820">
      <template v-if="detail">
        <div class="mb-4 grid grid-cols-5 gap-2.5">
          <div v-for="s in [
              { k: 'sent', label: '已发送', color: '#15803D' },
              { k: 'pending', label: '待发送', color: '#2563EB' },
              { k: 'failed', label: '失败', color: '#B91C1C' },
              { k: 'invalid', label: '邮箱无效', color: '#B45309' },
              { k: 'suppressed', label: '退订跳过', color: '#6B7280' },
            ]" :key="s.k" class="rounded-xl border border-solid border-[#E5E7EB] p-3"
          >
            <div class="text-xs text-[#6B7280]">{{ s.label }}</div>
            <div class="text-xl font-semibold" :style="{ color: s.color }">{{ (detail.counts as any)[s.k] ?? 0 }}</div>
          </div>
        </div>
        <div class="mb-3 flex flex-wrap items-center gap-3 text-sm text-[#4B5563]">
          <Tag :color="BATCH_STATUS[detail.status].color" class="m-0">{{ BATCH_STATUS[detail.status].label }}</Tag>
          <span>已回复 <b class="text-[#111827]">{{ detail.replied ?? 0 }}</b></span>
          <span>主题：{{ detail.subject }}</span>
          <span class="flex-1"></span>
          <Button v-if="detail.status === 'draft'" size="small" type="primary" @click="startMailBatch(detail.id).then(() => openDetail(detail!)).then(() => load(true))">开始发送</Button>
          <Button v-if="detail.status === 'sending' || detail.status === 'draft'" size="small" @click="cancel(detail)">取消发送</Button>
        </div>
        <Segmented v-model:value="detailFilter" :options="ITEM_FILTERS" size="small" class="mb-2" />
        <Table
          size="small"
          row-key="id"
          :data-source="detail.items ?? []"
          :pagination="{ pageSize: 20 }"
          :columns="[
            { title: '收件人', dataIndex: 'email', ellipsis: true },
            { title: '发件邮箱', dataIndex: 'accountId', ellipsis: true, width: 180 },
            { title: '状态', dataIndex: 'status', width: 120 },
            { title: '发送时间', dataIndex: 'sentTime', width: 110 },
            { title: '说明', dataIndex: 'reason', ellipsis: true },
          ]"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'email'">
              <div class="truncate">{{ record.email }}</div>
              <div v-if="record.name" class="truncate text-xs text-[#6B7280]">{{ record.name }}</div>
            </template>
            <template v-else-if="column.dataIndex === 'accountId'">{{ accountEmail(record.accountId) }}</template>
            <template v-else-if="column.dataIndex === 'status'">
              <Tag :color="ITEM_STATUS[record.status as MailBatchApi.ItemStatus].color" class="m-0">{{ ITEM_STATUS[record.status as MailBatchApi.ItemStatus].label }}</Tag>
              <Tag v-if="record.replied" color="green" class="ml-1">已回复</Tag>
              <Tag v-if="record.unsubscribed" class="ml-1">已退订</Tag>
            </template>
            <template v-else-if="column.dataIndex === 'sentTime'">{{ fmtDateTime(record.sentTime, 'MM-DD HH:mm', '-') }}</template>
          </template>
        </Table>
      </template>
    </Drawer>
  </div>
</template>
