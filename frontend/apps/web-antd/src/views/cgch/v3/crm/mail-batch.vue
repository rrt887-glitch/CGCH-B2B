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
  Button,
  Drawer,
  Input,
  InputNumber,
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
  <div class="mb-page">
    <div class="flex flex-wrap items-start justify-between gap-3 pb-3.5">
      <div class="min-w-0 flex-1 basis-[240px]">
        <h2 class="m-0 text-[21px] font-bold tracking-tight text-[#1D1D1F]">邮件群发</h2>
        <p class="m-0 mt-1 text-[13px] text-[#6E6E73]">
          自动校验收件邮箱、按设定速率从多个企业邮箱轮流发送，并自动附带退订保护链接。
        </p>
      </div>
      <Button type="primary" shape="round" @click="openCreate()">
        <IconifyIcon icon="lucide:plus" class="mr-1 inline size-4 align-[-3px]" />新建群发
      </Button>
    </div>

    <Spin :spinning="loading">
      <div
        v-if="!loading && list.length === 0"
        class="flex flex-col items-center justify-center gap-3 rounded-[18px] bg-white px-6 py-16 text-center shadow-[0_1px_4px_rgba(0,0,0,0.04),0_0_0_0.5px_rgba(0,0,0,0.05)]"
      >
        <div class="grid size-12 place-items-center rounded-2xl bg-[#EEF4FF] text-[#007AFF]">
          <IconifyIcon icon="lucide:send" class="size-6" />
        </div>
        <div class="text-[15px] font-semibold text-[#1D1D1F]">还没有邮件群发任务</div>
        <div class="max-w-sm text-[13px] text-[#86868B]">
          可从 CRM 客户库勾选收件人或批量粘贴邮箱，支持个性化变量与多邮箱轮发。
        </div>
        <Button type="primary" shape="round" @click="openCreate()">新建第一个群发</Button>
      </div>
      <div v-else class="flex flex-col gap-2.5">
        <div
          v-for="b in list"
          :key="b.id"
          class="mb-task-card grid cursor-pointer grid-cols-1 items-center gap-2 sm:gap-4 md:grid-cols-[minmax(0,2fr)_minmax(0,1.6fr)_120px_150px]"
          @click="openDetail(b)"
        >
          <div class="min-w-0">
            <div class="flex items-center gap-2">
              <span class="truncate text-[14.5px] font-semibold text-[#1D1D1F]">{{ b.name }}</span>
              <Tag :color="BATCH_STATUS[b.status].color" class="m-0 !rounded-full">{{ BATCH_STATUS[b.status].label }}</Tag>
            </div>
            <div class="mt-0.5 truncate text-xs text-[#86868B]">{{ b.subject }} · {{ fmtDateTime(b.createTime, 'MM-DD HH:mm') }}</div>
          </div>
          <div>
            <Progress :percent="percent(b)" size="small" :status="b.status === 'sending' ? 'active' : 'normal'" :show-info="false" />
            <div class="text-xs text-[#6E6E73] tabular-nums">
              已发 {{ sentOf(b) }} / {{ b.total }}
              <span v-if="b.counts.failed" class="ml-2 text-[#D70015]">失败 {{ b.counts.failed }}</span>
              <span v-if="b.counts.invalid" class="ml-2 text-[#C93400]">无效 {{ b.counts.invalid }}</span>
              <span v-if="b.counts.suppressed" class="ml-2 text-[#86868B]">退订跳过 {{ b.counts.suppressed }}</span>
            </div>
          </div>
          <div class="text-xs text-[#86868B] tabular-nums">每分钟 {{ b.ratePerMinute }} 封</div>
          <div class="flex flex-wrap gap-2 md:justify-end" @click.stop>
            <Button v-if="b.status === 'draft'" size="small" type="primary" shape="round" @click="startMailBatch(b.id).then(() => load(true))">开始发送</Button>
            <Button v-if="b.status === 'sending' || b.status === 'draft'" size="small" shape="round" @click="cancel(b)">取消</Button>
            <Button v-else size="small" shape="round" danger @click="remove(b)">删除</Button>
          </div>
        </div>
      </div>
    </Spin>

    <!-- 新建 -->
    <Drawer v-model:open="createOpen" title="新建邮件群发" width="min(760px, 100vw)" destroy-on-close>
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
          <div class="mb-search-box mb-2.5">
            <IconifyIcon icon="lucide:search" class="size-4 text-[#8E8E93]" />
            <input
              v-model="keyword"
              type="search"
              class="mb-search-input"
              placeholder="搜索客户名称，回车查询"
              @keydown.enter="searchCustomers"
              @search="searchCustomers"
            />
          </div>
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
              <span v-if="column.dataIndex === 'email' && !record.email" class="text-xs text-[#C93400]">没有邮箱</span>
            </template>
          </Table>
        </template>
        <template v-else>
          <Textarea v-model:value="pastedEmails" :auto-size="{ minRows: 8, maxRows: 14 }" placeholder="每行一个邮箱，也可以用逗号、分号隔开" />
        </template>
        <div class="mt-3 text-sm text-[#424245]">
          已选 <b class="tabular-nums">{{ recipientCount }}</b> 个收件人
          <span v-if="pickedNoEmail" class="ml-2 text-xs text-[#C93400]">其中 {{ pickedNoEmail }} 位客户没有邮箱，会被跳过</span>
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
          <div v-if="form.contentType === 'html'" class="rounded-2xl bg-[#F5F5F7] p-3.5">
            <div class="mb-2 flex items-center justify-between text-xs text-[#86868B]">
              <span>模板正文预览</span>
              <a @click="form.contentType = 'text'; form.templateId = undefined; form.content = ''">改为自己写</a>
            </div>
            <iframe :srcdoc="form.content" class="h-72 w-full rounded-xl border-0 bg-white" sandbox="" title="邮件预览"></iframe>
          </div>
          <Textarea
            v-else
            v-model:value="form.content"
            :auto-size="{ minRows: 10, maxRows: 18 }"
            placeholder="Hi {name},&#10;&#10;我们推出了新款……"
          />
          <div class="ap-sheet-tip">
            <IconifyIcon icon="lucide:sparkles" class="mt-0.5 size-4 shrink-0 text-[#007AFF]" />
            <span>可用个性化变量：<code>{name}</code> 客户称呼、<code>{company}</code> 公司名称。系统会在邮件末尾自动附上合规退订链接。</span>
          </div>
        </div>
      </template>

      <template v-else-if="step === 2">
        <div class="flex flex-col gap-4">
          <div>
            <div class="mb-1.5 text-sm font-medium text-[#424245]">发件邮箱（选多个时轮流发送）</div>
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
            <div class="mb-1.5 text-sm font-medium text-[#424245]">发送速度</div>
            <div class="flex flex-wrap items-center gap-3">
              <Segmented v-model:value="form.ratePerMinute" :options="RATE_OPTIONS" />
              <span class="text-sm text-[#6E6E73]">或自定义每分钟</span>
              <InputNumber v-model:value="form.ratePerMinute" :min="1" :max="60" class="w-20" />
              <span class="text-sm text-[#6E6E73]">封</span>
            </div>
            <div class="mt-2 text-xs leading-relaxed text-[#86868B]">
              {{ recipientCount }} 封大约需要 {{ minutes }} 分钟。发太快容易被对方邮箱判为垃圾邮件；还会受「发送策略」里每个邮箱每小时 / 每天的上限限制，达到上限会自动暂停一会儿再继续。
            </div>
          </div>
          <div>
            <div class="mb-1.5 text-sm font-medium text-[#424245]">任务名称</div>
            <Input v-model:value="form.name" :maxlength="100" />
          </div>
        </div>
      </template>

      <template v-else-if="draft">
        <div class="flex flex-col gap-4">
          <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <div class="rounded-2xl bg-[#EEFBF3] p-3.5">
              <div class="text-xs text-[#248A3D]">将发送</div>
              <div class="mt-1 text-2xl font-bold tabular-nums text-[#248A3D]">{{ draft.counts.pending ?? 0 }}</div>
            </div>
            <div class="rounded-2xl bg-[#FFF8EB] p-3.5">
              <div class="text-xs text-[#B25E00]">邮箱无效</div>
              <div class="mt-1 text-2xl font-bold tabular-nums text-[#C93400]">{{ draft.counts.invalid ?? 0 }}</div>
            </div>
            <div class="rounded-2xl bg-[#F5F5F7] p-3.5">
              <div class="text-xs text-[#6E6E73]">已退订跳过</div>
              <div class="mt-1 text-2xl font-bold tabular-nums text-[#6E6E73]">{{ draft.counts.suppressed ?? 0 }}</div>
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
              <Tag v-if="column.dataIndex === 'status'" :color="ITEM_STATUS[record.status as MailBatchApi.ItemStatus].color" class="!rounded-full">
                {{ ITEM_STATUS[record.status as MailBatchApi.ItemStatus].label }}
              </Tag>
            </template>
          </Table>
        </div>
      </template>

      <template #footer>
        <div class="flex justify-between">
          <Button v-if="step > 0 && step < 3" shape="round" @click="step--">上一步</Button>
          <span v-else></span>
          <div class="flex gap-2">
            <template v-if="step < 3">
              <Button type="primary" shape="round" :loading="saving" @click="next">{{ step === 2 ? '检查收件人' : '下一步' }}</Button>
            </template>
            <template v-else>
              <Button shape="round" @click="createOpen = false">先存为草稿</Button>
              <Button type="primary" shape="round" :disabled="!(draft?.counts.pending ?? 0)" @click="startDraft">开始发送 {{ draft?.counts.pending ?? 0 }} 封</Button>
            </template>
          </div>
        </div>
      </template>
    </Drawer>

    <!-- 详情 -->
    <Drawer v-model:open="detailOpen" :title="detail?.name" width="min(820px, 100vw)">
      <template v-if="detail">
        <div class="mb-4 grid grid-cols-2 gap-2.5 sm:grid-cols-5">
          <div v-for="s in [
              { k: 'sent', label: '已发送', color: '#248A3D', bg: '#EEFBF3' },
              { k: 'pending', label: '待发送', color: '#007AFF', bg: '#EEF4FF' },
              { k: 'failed', label: '失败', color: '#D70015', bg: '#FFECEB' },
              { k: 'invalid', label: '邮箱无效', color: '#C93400', bg: '#FFF8EB' },
              { k: 'suppressed', label: '退订跳过', color: '#6E6E73', bg: '#F5F5F7' },
            ]" :key="s.k" class="rounded-2xl p-3" :style="{ background: s.bg }"
          >
            <div class="text-xs text-[#6E6E73]">{{ s.label }}</div>
            <div class="mt-0.5 text-xl font-bold tabular-nums" :style="{ color: s.color }">{{ (detail.counts as any)[s.k] ?? 0 }}</div>
          </div>
        </div>
        <div class="mb-3 flex flex-wrap items-center gap-3 text-sm text-[#6E6E73]">
          <Tag :color="BATCH_STATUS[detail.status].color" class="m-0 !rounded-full">{{ BATCH_STATUS[detail.status].label }}</Tag>
          <span>已回复 <b class="text-[#1D1D1F] tabular-nums">{{ detail.replied ?? 0 }}</b></span>
          <span>主题：{{ detail.subject }}</span>
          <span class="flex-1"></span>
          <Button v-if="detail.status === 'draft'" size="small" type="primary" shape="round" @click="startMailBatch(detail.id).then(() => openDetail(detail!)).then(() => load(true))">开始发送</Button>
          <Button v-if="detail.status === 'sending' || detail.status === 'draft'" size="small" shape="round" @click="cancel(detail)">取消发送</Button>
        </div>
        <Segmented v-model:value="detailFilter" :options="ITEM_FILTERS" size="small" class="mb-3" />
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
              <div class="truncate font-medium text-[#1D1D1F]">{{ record.email }}</div>
              <div v-if="record.name" class="truncate text-xs text-[#86868B]">{{ record.name }}</div>
            </template>
            <template v-else-if="column.dataIndex === 'accountId'">{{ accountEmail(record.accountId) }}</template>
            <template v-else-if="column.dataIndex === 'status'">
              <Tag :color="ITEM_STATUS[record.status as MailBatchApi.ItemStatus].color" class="m-0 !rounded-full">{{ ITEM_STATUS[record.status as MailBatchApi.ItemStatus].label }}</Tag>
              <Tag v-if="record.replied" color="green" class="ml-1 !rounded-full">已回复</Tag>
              <Tag v-if="record.unsubscribed" class="ml-1 !rounded-full">已退订</Tag>
            </template>
            <template v-else-if="column.dataIndex === 'sentTime'">
              <span class="tabular-nums text-[#6E6E73]">{{ fmtDateTime(record.sentTime, 'MM-DD HH:mm', '-') }}</span>
            </template>
          </template>
        </Table>
      </template>
    </Drawer>
  </div>
</template>

<style scoped>
.mb-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 20px 24px 16px;
  overflow: auto;
}

.mb-task-card {
  padding: 14px 18px;
  background: var(--ap-surface);
  border-radius: 16px;
  box-shadow: var(--ap-shadow-rest);
  transition:
    transform var(--ap-dur-fast) var(--ap-ease),
    box-shadow var(--ap-dur-fast) var(--ap-ease);
}

.mb-task-card:hover {
  transform: translateY(-1.5px);
  box-shadow: var(--ap-shadow-lift);
}

.mb-search-box {
  display: flex;
  gap: 7px;
  align-items: center;
  height: 36px;
  padding: 0 12px;
  background: #f2f2f7;
  border-radius: 10px;
}

.mb-search-box:focus-within {
  background: #fff;
  box-shadow: 0 0 0 2px rgb(0 122 255 / 22%);
}

.mb-search-input {
  flex: 1;
  min-width: 0;
  height: 100%;
  font-size: 13px;
  background: transparent;
  border: 0;
  outline: none;
}
</style>
