<script lang="ts" setup>
/**
 * 客户唤醒（Apple 营销激活工作台）：
 * 筛出沉睡老客户 → AI 先做深度背调 → 结合往来记录匹配痛点与产品 → 逐客定制唤醒邮件 / WhatsApp 模板
 * → 逐条确认或自动发送 → 实时追踪打开与回复。
 */
import type { ChannelReactivateApi } from '#/api/channel/reactivate';
import type { ChannelWaApi } from '#/api/channel/whatsapp';

import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import {
  Button,
  Drawer,
  Input,
  message,
  Modal,
  Popconfirm,
  Progress,
  Select,
  Switch,
  Table,
  Textarea,
  Tooltip,
} from 'ant-design-vue';

import {
  changeReactivateItems,
  createReactivateTask,
  deleteReactivateTask,
  getReactivateCandidates,
  getReactivateTask,
  getReactivateTaskPage,
  regenerateReactivateItem,
  sendReactivateTask,
  updateReactivateItem,
} from '#/api/channel/reactivate';
import { getWaAccountList, getWaTemplates } from '#/api/channel/whatsapp';
import { getSimpleUserList } from '#/api/system/user';
import { fmtDateTime } from '#/utils/datetime';
import {
  countryName,
  crmAvatarBg,
  crmInitial,
} from '#/views/cgch/v3/crm/components/shared';

import { approvedCount, draftCount, ITEM_STATUS, taskTag } from './task-status';

defineOptions({ name: 'CgchClawReactivate' });

const router = useRouter();
const users = ref<{ id: number; nickname: string }[]>([]);
const userName = (id?: number) =>
  users.value.find((u) => u.id === id)?.nickname ?? (id ? `#${id}` : '—');

const FLOW_STEPS = [
  { icon: 'lucide:filter', label: '筛选沉睡老客' },
  { icon: 'lucide:radar', label: 'AI 深度背调' },
  { icon: 'lucide:sparkles', label: '千人千面写信' },
  { icon: 'lucide:send', label: '确认发送并追回' },
];

const TAG_PILL: Record<string, string> = {
  processing: 'ap-crm-pill--blue',
  blue: 'ap-crm-pill--blue',
  orange: 'ap-crm-pill--amber',
  green: 'ap-crm-pill--green',
  red: 'ap-crm-pill--red',
  default: 'ap-crm-pill--gray',
};

// ===== 任务列表 =====
const tasks = ref<ChannelReactivateApi.Task[]>([]);
const loading = ref(false);
async function loadTasks() {
  loading.value = true;
  try {
    tasks.value = (
      await getReactivateTaskPage({ pageNo: 1, pageSize: 50 })
    ).list;
  } finally {
    loading.value = false;
  }
}

const totalSent = computed(() =>
  tasks.value.reduce((acc, t) => acc + (t.counts?.sent || 0), 0),
);
const totalReplied = computed(() =>
  tasks.value.reduce((acc, t) => acc + (t.replied || 0), 0),
);

function taskSentPct(t: ChannelReactivateApi.Task) {
  if (!t.total) return 0;
  return Math.min(100, Math.round(((t.counts?.sent || 0) / t.total) * 100));
}

// ===== 新建任务 =====
const createOpen = ref(false);
const step = ref(0);
const filter = reactive({
  inactiveDays: 90,
  keyword: '',
  ownerUserId: undefined as number | undefined,
});
const candidates = ref<ChannelReactivateApi.Candidate[]>([]);
const candLoading = ref(false);
const selected = ref<number[]>([]);
const form = reactive({
  name: '',
  channel: 'auto',
  instructions: '',
  waTemplateName: undefined as string | undefined,
  waTemplateLanguage: 'en_US',
  autoResearch: true,
  autoSend: false,
});
const templates = ref<ChannelWaApi.Template[]>([]);
const creating = ref(false);

async function searchCandidates() {
  candLoading.value = true;
  try {
    candidates.value = await getReactivateCandidates({
      inactiveDays: filter.inactiveDays,
      keyword: filter.keyword || undefined,
      ownerUserId: filter.ownerUserId,
    });
    selected.value = candidates.value
      .filter((c) => !c.inTask)
      .map((c) => c.id);
  } finally {
    candLoading.value = false;
  }
}

async function openCreate() {
  step.value = 0;
  Object.assign(form, {
    name: '',
    channel: 'auto',
    instructions: '',
    waTemplateName: undefined,
    waTemplateLanguage: 'en_US',
    autoResearch: true,
    autoSend: false,
  });
  createOpen.value = true;
  await searchCandidates();
  try {
    const accounts = (await getWaAccountList()).filter(
      (a) => !a.platform || a.platform === 'whatsapp',
    );
    templates.value = accounts[0]?.id
      ? (await getWaTemplates(accounts[0].id)).filter(
          (t) => t.status === 'APPROVED',
        )
      : [];
  } catch {
    templates.value = [];
  }
}

const candColumns = [
  { title: '客户', key: 'name' },
  { title: '国家/地区', dataIndex: 'countryCode', key: 'country', width: 110 },
  { title: '可达渠道', key: 'contact' },
  { title: '负责人', key: 'owner', width: 100 },
  { title: '上次联系', key: 'last', width: 165 },
];

const selectedWithoutMail = computed(
  () =>
    candidates.value.filter(
      (c) => selected.value.includes(c.id) && !c.email,
    ).length,
);

async function create() {
  if (selected.value.length === 0) {
    message.warning('请至少选择一位客户');
    return;
  }
  creating.value = true;
  try {
    const t = templates.value.find((x) => x.name === form.waTemplateName);
    const id = await createReactivateTask({
      ...form,
      waTemplateLanguage: t?.language ?? form.waTemplateLanguage,
      inactiveDays: filter.inactiveDays,
      customerIds: selected.value,
    });
    message.success(
      form.autoResearch
        ? '任务已创建，AI 正在逐个背调并撰写定制信件'
        : '任务已创建，AI 正在逐个撰写定制信件',
    );
    createOpen.value = false;
    await loadTasks();
    await openTask(id);
  } finally {
    creating.value = false;
  }
}

// ===== 任务详情 =====
const detailOpen = ref(false);
const task = ref<ChannelReactivateApi.Task>();
const itemSel = ref<number[]>([]);
let timer: ReturnType<typeof setInterval> | undefined;

async function refreshTask() {
  if (!task.value) return;
  task.value = await getReactivateTask(task.value.id);
  const busy =
    task.value.status === 'generating' ||
    task.value.status === 'sending' ||
    (task.value.items ?? []).some((i) => i.status === 'generating');
  if (!busy && timer) {
    clearInterval(timer);
    timer = undefined;
    loadTasks();
  }
}

async function openTask(id: number) {
  task.value = await getReactivateTask(id);
  itemSel.value = [];
  detailOpen.value = true;
  if (timer) clearInterval(timer);
  timer = setInterval(refreshTask, 2000);
}
onBeforeUnmount(() => timer && clearInterval(timer));

const progress = computed(() => {
  const t = task.value;
  if (!t || !t.total) return 0;
  return Math.round(((t.total - t.counts.generating) / t.total) * 100);
});

const itemColumns = [
  { title: '客户', key: 'customer', width: 210 },
  { title: '渠道', key: 'channel', width: 95 },
  { title: 'AI 洞察与定制内容', key: 'content' },
  { title: '状态', key: 'status', width: 115 },
  { title: '操作', key: 'action', width: 165 },
];

const ACTION_TEXT: Record<'approve' | 'draft' | 'skip', string> = {
  approve: '确认',
  draft: '撤回确认',
  skip: '跳过',
};

async function batch(action: 'approve' | 'draft' | 'skip', ids?: number[]) {
  const list = ids ?? itemSel.value;
  if (list.length === 0) {
    message.warning('请先勾选客户');
    return;
  }
  const n = await changeReactivateItems(action, list);
  const verb = ACTION_TEXT[action];
  if (!n) {
    message.info(
      `勾选的 ${list.length} 条都不符合「${verb}」的条件，没有处理任何一条`,
    );
    return;
  }
  if (n < list.length) {
    message.success(
      `已${verb} ${n} 条，另有 ${list.length - n} 条状态不符、未处理`,
    );
  } else {
    message.success(`已${verb} ${n} 条`);
  }
  itemSel.value = [];
  await refreshTask();
}

async function approveAll() {
  const ids = (task.value?.items ?? [])
    .filter((i) => i.status === 'draft')
    .map((i) => i.id);
  await batch('approve', ids);
}

async function send() {
  if (!task.value) return;
  const n = await sendReactivateTask(task.value.id);
  message.success(`开始发送 ${n} 条，系统自动平滑间隔避免触发反垃圾策略`);
  if (timer) clearInterval(timer);
  timer = setInterval(refreshTask, 2000);
  await refreshTask();
}

async function regenerate(i: ChannelReactivateApi.Item) {
  await regenerateReactivateItem(i.id);
  if (!timer) timer = setInterval(refreshTask, 2000);
  await refreshTask();
}

async function removeTask(id: number) {
  await deleteReactivateTask(id);
  message.success('唤醒任务已删除');
  detailOpen.value = false;
  await loadTasks();
}

function openResearch(id: number) {
  detailOpen.value = false;
  router.replace({
    name: 'CgchV3Crm',
    query: { tab: 'salesmind', sub: 'research', researchId: String(id) },
  });
}

// ===== 编辑单封信件 =====
const editOpen = ref(false);
const edit = reactive({
  id: 0,
  subject: '',
  content: '',
  channel: 'mail' as string,
});

function openEdit(i: ChannelReactivateApi.Item) {
  Object.assign(edit, {
    id: i.id,
    subject: i.subject ?? '',
    content: i.content ?? '',
    channel: i.channel,
  });
  editOpen.value = true;
}

async function saveEdit() {
  await updateReactivateItem({
    id: edit.id,
    subject: edit.subject,
    content: edit.content,
  });
  message.success('内容已保存');
  editOpen.value = false;
  await refreshTask();
}

onMounted(async () => {
  users.value = (await getSimpleUserList()) as any;
  await loadTasks();
});
</script>

<template>
  <div class="wk">
    <!-- 顶部标题与概览指标 -->
    <header class="wk__head">
      <div>
        <h2 class="wk__title">老客户智能唤醒</h2>
        <p class="wk__sub">
          盘活久未联系的历史客户资产，AI 先做背调再千人千面撰写跟进邮件或 WhatsApp 模板
        </p>
      </div>

      <div class="flex flex-wrap items-center gap-2.5">
        <div v-if="tasks.length" class="wk__summary-pills">
          <span>
            任务 <b>{{ tasks.length }}</b>
          </span>
          <i class="wk__sep"></i>
          <span>
            已触达 <b>{{ totalSent }}</b>
          </span>
          <i class="wk__sep"></i>
          <span class="text-[#1E8E3E]">
            已回复 <b>{{ totalReplied }}</b>
          </span>
        </div>
        <button type="button" class="wk__create-btn" @click="openCreate">
          <IconifyIcon icon="lucide:plus" class="size-4" />
          <span>新建唤醒任务</span>
        </button>
      </div>
    </header>

    <!-- 四步工作流导览条 -->
    <div class="wk__flow">
      <template v-for="(f, idx) in FLOW_STEPS" :key="f.label">
        <div class="wk__flow-step">
          <span class="wk__flow-icon">
            <IconifyIcon :icon="f.icon" class="size-3.5" />
          </span>
          <span>{{ f.label }}</span>
        </div>
        <IconifyIcon
          v-if="idx < FLOW_STEPS.length - 1"
          icon="lucide:chevron-right"
          class="size-3.5 shrink-0 text-[#C7C7CC]"
        />
      </template>
    </div>

    <!-- 空状态 -->
    <div v-if="!loading && tasks.length === 0" class="wk__empty">
      <div class="wk__empty-orb">
        <IconifyIcon icon="lucide:mail-check" class="size-7" />
      </div>
      <h3>还没有创建客户唤醒任务</h3>
      <p>
        选出超过 30～180 天未联系的沉睡老客户，AI 自动核验最新动态并定制专属唤醒信件，逐条确认后安全发出。
      </p>
      <button type="button" class="wk__create-btn mt-4" @click="openCreate">
        <IconifyIcon icon="lucide:sparkles" class="size-4" />
        <span>创建第一个唤醒任务</span>
      </button>
    </div>

    <!-- 任务卡片矩阵 -->
    <div v-else class="wk__grid">
      <div
        v-for="t in tasks"
        :key="t.id"
        class="wk-card"
        @click="openTask(t.id)"
      >
        <div class="flex items-start justify-between gap-2">
          <div class="min-w-0">
            <div class="wk-card__title">{{ t.name }}</div>
            <div class="wk-card__meta">
              {{ fmtDateTime(t.createTime) }} · 共 {{ t.total }} 位客户
            </div>
          </div>
          <span
            class="ap-crm-pill"
            :class="TAG_PILL[taskTag(t).color] ?? 'ap-crm-pill--gray'"
          >
            {{ taskTag(t).label }}
          </span>
        </div>

        <div class="wk-card__bar">
          <i :style="{ width: `${taskSentPct(t)}%` }"></i>
        </div>

        <div class="wk-card__stats">
          <div class="wk-stat">
            <small>已发送</small>
            <b>{{ t.counts.sent }}</b>
          </div>
          <div class="wk-stat">
            <small>待确认</small>
            <b :class="{ 'text-[#D97706]': draftCount(t) > 0 }">
              {{ draftCount(t) }}
            </b>
          </div>
          <div class="wk-stat">
            <small>{{ approvedCount(t) ? '待发送' : '已跳过' }}</small>
            <Tooltip
              v-if="!approvedCount(t) && t.counts.skipped"
              title="客户缺邮箱 / WhatsApp、负责人无发件账号或未选模板；点开查看明细"
            >
              <b class="cursor-help">{{ t.counts.skipped }}</b>
            </Tooltip>
            <b v-else>{{ approvedCount(t) || t.counts.skipped || 0 }}</b>
          </div>
          <div class="wk-stat">
            <small>已回复</small>
            <b class="text-[#1E8E3E]">{{ t.replied }}</b>
          </div>
        </div>

        <div
          v-if="t.counts.failed"
          class="mt-2.5 flex items-center gap-1 text-xs text-[#D70015]"
        >
          <IconifyIcon icon="lucide:circle-alert" class="size-3.5" />
          <span>{{ t.counts.failed }} 条生成或发送失败，点击查看详情</span>
        </div>
      </div>
    </div>

    <!-- 新建任务抽屉 -->
    <Drawer v-model:open="createOpen" title="新建客户唤醒任务" width="920">
      <div class="wk-steps">
        <button
          type="button"
          class="wk-step-pill"
          :class="{ on: step === 0, done: step > 0 }"
          @click="step = 0"
        >
          <span class="wk-step-num">1</span>
          <span>筛选沉睡客户</span>
        </button>
        <IconifyIcon icon="lucide:chevron-right" class="size-4 text-[#C7C7CC]" />
        <button
          type="button"
          class="wk-step-pill"
          :class="{ on: step === 1 }"
          :disabled="selected.length === 0"
          @click="selected.length && (step = 1)"
        >
          <span class="wk-step-num">2</span>
          <span>配置写作要求与发送策略</span>
        </button>
      </div>

      <!-- Step 1：选人 -->
      <template v-if="step === 0">
        <div class="wk-filter-bar">
          <Select
            v-model:value="filter.inactiveDays"
            class="w-44"
            :options="
              [30, 60, 90, 180, 365].map((d) => ({
                value: d,
                label: `超过 ${d} 天未联系`,
              }))
            "
            @change="searchCandidates"
          />
          <Select
            v-model:value="filter.ownerUserId"
            allow-clear
            placeholder="全部负责人"
            class="w-36"
            :options="users.map((u) => ({ value: u.id, label: u.nickname }))"
            @change="searchCandidates"
          />
          <Input
            v-model:value="filter.keyword"
            allow-clear
            placeholder="搜索客户或公司名称…"
            class="w-56"
            @press-enter="searchCandidates"
          >
            <template #prefix>
              <IconifyIcon icon="lucide:search" class="size-3.5 text-[#8E8E93]" />
            </template>
          </Input>
          <Button type="primary" @click="searchCandidates">筛选</Button>
        </div>

        <div class="wk-hint">
          <IconifyIcon icon="lucide:info" class="mt-0.5 size-3.5 shrink-0 text-[#007AFF]" />
          <span>
            候选范围：距上次联系超过 {{ filter.inactiveDays }} 天（未联系过的按建档时间计算），且留有邮箱或 WhatsApp 的可触达客户。
          </span>
        </div>

        <Table
          :columns="candColumns"
          :data-source="candidates"
          :loading="candLoading"
          row-key="id"
          size="small"
          :pagination="{ pageSize: 10 }"
          :row-selection="{
            selectedRowKeys: selected,
            onChange: (k: any[]) => (selected = k as number[]),
            getCheckboxProps: (r: any) => ({ disabled: r.inTask }),
          }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">
              <div class="flex items-center gap-2.5">
                <span
                  class="ap-crm-avatar"
                  :style="{ background: crmAvatarBg(record.name) }"
                >
                  {{ crmInitial(record.name) }}
                </span>
                <div class="min-w-0">
                  <div class="truncate font-semibold text-[#1D1D1F]">
                    {{ record.name }}
                  </div>
                  <div
                    v-if="record.companyName"
                    class="truncate text-xs text-[#86868B]"
                  >
                    {{ record.companyName }}
                  </div>
                </div>
              </div>
            </template>
            <template v-else-if="column.key === 'country'">
              <span v-if="record.countryCode" class="ap-crm-pill ap-crm-pill--gray">
                {{ countryName(record.countryCode) || record.countryCode }}
              </span>
              <span v-else class="text-[#C7C7CC]">—</span>
            </template>
            <template v-else-if="column.key === 'contact'">
              <div v-if="record.email" class="text-xs text-[#1D1D1F]">
                {{ record.email }}
              </div>
              <div v-if="record.whatsapp" class="text-xs text-[#1E8E3E]">
                WA: {{ record.whatsapp }}
              </div>
              <span v-if="!record.email && !record.whatsapp" class="text-[#C7C7CC]">—</span>
            </template>
            <template v-else-if="column.key === 'owner'">
              {{ userName(record.ownerUserId) }}
            </template>
            <template v-else-if="column.key === 'last'">
              <div class="text-xs font-medium text-[#1D1D1F]">
                {{ fmtDateTime(record.lastContactTime, 'YYYY-MM-DD HH:mm', '—') }}
              </div>
              <div class="mt-0.5 flex items-center gap-1 text-[11px] text-[#86868B]">
                <span>
                  {{
                    record.contacted
                      ? `${record.inactiveDays} 天前`
                      : '建档后未联系'
                  }}
                </span>
                <span
                  v-if="record.inTask"
                  class="ap-crm-pill ap-crm-pill--amber"
                >
                  已在任务中
                </span>
              </div>
            </template>
          </template>
          <template #emptyText>没有符合当前筛选条件的沉睡客户</template>
        </Table>

        <div class="wk-drawer-foot">
          <span class="text-sm text-[#6E6E73]">
            已勾选 <b class="text-[#1D1D1F]">{{ selected.length }}</b> 位客户（单次最多 200 位）
          </span>
          <Button
            type="primary"
            shape="round"
            :disabled="selected.length === 0"
            @click="step = 1"
          >
            下一步：配置策略 ›
          </Button>
        </div>
      </template>

      <!-- Step 2：策略 -->
      <template v-else>
        <div class="wk-config">
          <div class="wk-block">
            <label class="wk-label">
              <span>任务名称</span>
              <small>选填，留空将按日期自动生成</small>
            </label>
            <Input
              v-model:value="form.name"
              placeholder="例如：2027 春季新品老客户唤醒"
            />
          </div>

          <div class="wk-block">
            <label class="wk-label"><span>发送渠道</span></label>
            <div class="wk-choice-grid is-3">
              <button
                v-for="ch in [
                  {
                    v: 'auto',
                    t: '智能优先邮件',
                    d: '有邮箱发邮件，无邮箱改发 WhatsApp',
                    icon: 'lucide:sparkles',
                  },
                  {
                    v: 'mail',
                    t: '仅发邮件',
                    d: 'AI 逐位定制主题与正文',
                    icon: 'lucide:mail',
                  },
                  {
                    v: 'whatsapp',
                    t: '仅发 WhatsApp',
                    d: '使用已审核模板消息触达',
                    icon: 'lucide:message-circle',
                  },
                ]"
                :key="ch.v"
                type="button"
                class="wk-choice"
                :class="{ on: form.channel === ch.v }"
                @click="form.channel = ch.v"
              >
                <IconifyIcon :icon="ch.icon" class="size-4 text-[#007AFF]" />
                <b>{{ ch.t }}</b>
                <small>{{ ch.d }}</small>
              </button>
            </div>
          </div>

          <div class="wk-block wk-block--row">
            <div class="min-w-0 flex-1">
              <div class="text-[13.5px] font-semibold text-[#1D1D1F]">
                写信前自动做深度背调
              </div>
              <div class="mt-0.5 text-xs leading-5 text-[#6E6E73]">
                对近 90 天内无背调报告的客户，先抓取官网与公开情报判断痛点，再结合产品库定制信件。
              </div>
            </div>
            <Switch v-model:checked="form.autoResearch" />
          </div>

          <div class="wk-block">
            <label class="wk-label"><span>确认与发送方式</span></label>
            <div class="wk-choice-grid is-2">
              <button
                type="button"
                class="wk-choice"
                :class="{ on: !form.autoSend }"
                @click="form.autoSend = false"
              >
                <IconifyIcon icon="lucide:user-check" class="size-4 text-[#007AFF]" />
                <b>写好后逐条确认再发（推荐）</b>
                <small>可人工审阅、微调或让 AI 重写后再批量发出</small>
              </button>
              <button
                type="button"
                class="wk-choice"
                :class="{ on: form.autoSend }"
                @click="form.autoSend = true"
              >
                <IconifyIcon icon="lucide:zap" class="size-4 text-[#D97706]" />
                <b>写好后全自动发出</b>
                <small>无需人工确认，AI 撰写完毕直接平滑发送</small>
              </button>
            </div>
            <div v-if="form.autoSend" class="wk-warn">
              <IconifyIcon icon="lucide:triangle-alert" class="size-4 shrink-0" />
              <span>
                全自动模式下 AI 写完即直接发给客户，建议先用少量客户测试效果后再大批量开启。
              </span>
            </div>
          </div>

          <div class="wk-block">
            <label class="wk-label">
              <span>给 AI 的写作要求</span>
              <small>选填 · 不填则由 AI 根据背调与产品库自动找切入点</small>
            </label>
            <Textarea
              v-model:value="form.instructions"
              :rows="4"
              :maxlength="1000"
              show-count
              placeholder="例如：介绍我们新款高速吹风机，比上一代轻 20%，支持免费寄样测试；顺便询问客户下半年的采购计划。"
            />
          </div>

          <div v-if="form.channel !== 'mail'" class="wk-block">
            <label class="wk-label">
              <span>WhatsApp 审核模板</span>
              <small>超过 24 小时未联系客户须使用 Meta 已审核模板</small>
            </label>
            <Select
              v-model:value="form.waTemplateName"
              allow-clear
              placeholder="选择已通过审核的 WhatsApp 模板…"
              class="w-full"
              :options="
                templates.map((t) => ({
                  value: t.name,
                  label: `${t.name}（${t.language}）`,
                }))
              "
            />
            <div
              v-if="selectedWithoutMail && form.channel === 'auto'"
              class="mt-1.5 text-xs text-[#B25E00]"
            >
              已选客户中有 {{ selectedWithoutMail }} 位未留邮箱，将自动改发 WhatsApp 模板。
            </div>
          </div>
        </div>

        <div class="wk-drawer-foot">
          <Button shape="round" @click="step = 0">‹ 返回重选客户</Button>
          <Button
            type="primary"
            shape="round"
            :loading="creating"
            @click="create"
          >
            {{ form.autoSend ? '创建并自动发送' : '创建并让 AI 撰写' }}（{{
              selected.length
            }}
            位）
          </Button>
        </div>
      </template>
    </Drawer>

    <!-- 任务详情抽屉 -->
    <Drawer v-model:open="detailOpen" :title="task?.name" width="1040">
      <template v-if="task" #extra>
        <Popconfirm
          title="删除这个唤醒任务？已发出的消息不受影响"
          ok-text="删除"
          cancel-text="取消"
          @confirm="removeTask(task.id)"
        >
          <Button size="small" danger :disabled="task.status === 'sending'">
            删除任务
          </Button>
        </Popconfirm>
      </template>

      <template v-if="task">
        <!-- 六宫格状态指标 -->
        <div class="wk-kpi-strip">
          <div
            v-for="(s, k) in ITEM_STATUS"
            :key="k"
            class="wk-kpi-tile"
            :class="`is-${s.color}`"
          >
            <div class="wk-kpi-tile__num">
              {{ task.counts[k as ChannelReactivateApi.ItemStatus] }}
            </div>
            <div class="wk-kpi-tile__lbl">{{ s.label }}</div>
          </div>
        </div>

        <div v-if="task.status === 'generating'" class="wk-gen-banner">
          <div class="flex items-center justify-between text-xs font-medium text-[#5856D6]">
            <span>
              {{
                task.autoResearch
                  ? '✦ AI 正在逐客执行深度背调并撰写信件…'
                  : '✦ AI 正在逐客撰写定制信件…'
              }}
              {{ task.autoSend ? '（完成后将自动发出）' : '' }}
            </span>
            <span>{{ progress }}%</span>
          </div>
          <Progress :percent="progress" :show-info="false" size="small" />
        </div>

        <!-- 批量操作栏 -->
        <div class="wk-batch-bar">
          <div class="flex flex-wrap items-center gap-2">
            <Button
              size="small"
              :disabled="!task.counts.draft"
              @click="approveAll"
            >
              全部确认（{{ task.counts.draft }}）
            </Button>
            <Button size="small" @click="batch('approve')">确认所选</Button>
            <Button size="small" @click="batch('draft')">撤回确认</Button>
            <Button size="small" @click="batch('skip')">跳过所选</Button>
          </div>

          <div class="flex items-center gap-3">
            <span class="text-xs text-[#6E6E73]">
              已回复 <b class="text-[#1E8E3E]">{{ task.replied }}</b> / 已发送
              <b>{{ task.counts.sent }}</b>
            </span>
            <Button
              type="primary"
              shape="round"
              :disabled="!task.counts.approved || task.status === 'sending'"
              @click="send"
            >
              <IconifyIcon icon="lucide:send" class="mr-1 inline size-3.5" />
              发送已确认（{{ task.counts.approved }}）
            </Button>
          </div>
        </div>

        <Table
          :columns="itemColumns"
          :data-source="task.items"
          row-key="id"
          size="small"
          :pagination="{ pageSize: 20 }"
          :row-selection="{
            selectedRowKeys: itemSel,
            onChange: (k: any[]) => (itemSel = k as number[]),
            getCheckboxProps: (r: any) => ({
              disabled: ['sent', 'generating'].includes(r.status),
            }),
          }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'customer'">
              <div class="flex items-start gap-2.5">
                <span
                  class="ap-crm-avatar mt-0.5"
                  :style="{ background: crmAvatarBg(record.customerName) }"
                >
                  {{ crmInitial(record.customerName) }}
                </span>
                <div class="min-w-0">
                  <a
                    class="ap-crm-link block truncate font-semibold"
                    @click="
                      router.push(`/crm/customer/detail/${record.customerId}`)
                    "
                  >
                    {{ record.customerName }}
                  </a>
                  <div class="truncate text-xs text-[#6E6E73]">
                    {{ record.recipient }}
                  </div>
                  <div class="text-[11px] text-[#8E8E93]">
                    负责人 {{ userName(record.ownerUserId) }}
                  </div>
                </div>
              </div>
            </template>

            <template v-else-if="column.key === 'channel'">
              <span
                class="ap-crm-pill"
                :class="
                  record.channel === 'mail'
                    ? 'ap-crm-pill--blue'
                    : 'ap-crm-pill--green'
                "
              >
                {{ record.channel === 'mail' ? '邮件' : 'WhatsApp' }}
              </span>
            </template>

            <template v-else-if="column.key === 'content'">
              <div
                v-if="
                  record.insight &&
                  (record.insight.situation || record.insight.painPoint)
                "
                class="wk-insight"
              >
                <div v-if="record.insight.situation">
                  <b>现状：</b>{{ record.insight.situation }}
                </div>
                <div v-if="record.insight.painPoint">
                  <b>痛点：</b>{{ record.insight.painPoint }}
                </div>
                <div v-if="record.insight.products?.length">
                  <b>推荐：</b>{{ record.insight.products.join('、') }}
                </div>
                <a
                  v-if="record.insight.researchId"
                  class="ap-crm-link inline-block mt-0.5 text-xs"
                  @click="openResearch(record.insight.researchId)"
                >
                  查看依据的背调报告 ›
                </a>
              </div>
              <div
                v-if="record.subject"
                class="truncate text-[13px] font-semibold text-[#1D1D1F]"
              >
                {{ record.subject }}
              </div>
              <div class="line-clamp-2 whitespace-pre-wrap text-xs leading-5 text-[#6E6E73]">
                {{ record.content }}
              </div>
              <div v-if="record.reasonDetail" class="mt-1 text-xs text-[#D70015]">
                {{ record.reasonDetail }}
              </div>
            </template>

            <template v-else-if="column.key === 'status'">
              <div class="flex flex-wrap gap-1">
                <span
                  class="ap-crm-pill"
                  :class="
                    TAG_PILL[ITEM_STATUS[record.status]?.color || 'default'] ??
                    'ap-crm-pill--gray'
                  "
                >
                  {{ ITEM_STATUS[record.status]?.label }}
                </span>
                <span
                  v-if="record.replied"
                  class="ap-crm-pill ap-crm-pill--green"
                >
                  已回复
                </span>
              </div>
              <div v-if="record.sentTime" class="mt-1 text-[11px] text-[#8E8E93]">
                {{ fmtDateTime(record.sentTime) }}
              </div>
            </template>

            <template v-else-if="column.key === 'action'">
              <div class="flex items-center gap-2">
                <button
                  type="button"
                  class="ap-crm-link bg-transparent border-0 p-0"
                  :disabled="
                    !['draft', 'approved'].includes(record.status) ||
                    record.channel !== 'mail'
                  "
                  :class="{
                    'opacity-35 cursor-not-allowed':
                      !['draft', 'approved'].includes(record.status) ||
                      record.channel !== 'mail',
                  }"
                  @click="openEdit(record as any)"
                >
                  修改
                </button>
                <Tooltip title="让 AI 重新撰写一封">
                  <button
                    type="button"
                    class="ap-crm-link bg-transparent border-0 p-0"
                    :disabled="
                      record.channel !== 'mail' ||
                      !['draft', 'failed', 'approved'].includes(record.status)
                    "
                    :class="{
                      'opacity-35 cursor-not-allowed':
                        record.channel !== 'mail' ||
                        !['draft', 'failed', 'approved'].includes(record.status),
                    }"
                    @click="regenerate(record as any)"
                  >
                    重写
                  </button>
                </Tooltip>
                <button
                  v-if="record.status === 'draft'"
                  type="button"
                  class="ap-crm-link bg-transparent border-0 p-0 font-semibold"
                  @click="batch('approve', [record.id])"
                >
                  确认
                </button>
              </div>
            </template>
          </template>
        </Table>
      </template>
    </Drawer>

    <!-- 修改信件内容弹窗（Apple Sheet） -->
    <Modal
      v-model:open="editOpen"
      title="微调唤醒信件"
      :width="680"
      ok-text="保存修改"
      cancel-text="取消"
      wrap-class-name="ap-modal-sheet"
      @ok="saveEdit"
    >
      <div class="ap-sheet-lead">
        <span class="ap-sheet-icon">
          <IconifyIcon icon="lucide:mail-plus" class="size-5" />
        </span>
        <div class="ap-sheet-subtitle">
          直接修改为该客户定制的邮件主题与正文，保存后可确认发出。
        </div>
      </div>
      <div class="ap-sheet-body">
        <div v-if="edit.channel === 'mail'" class="ap-sheet-field">
          <div class="ap-sheet-label"><span>邮件主题</span></div>
          <Input v-model:value="edit.subject" :maxlength="200" />
        </div>
        <div class="ap-sheet-field">
          <div class="ap-sheet-label"><span>信件正文</span></div>
          <Textarea v-model:value="edit.content" :rows="11" />
        </div>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.wk {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px 24px 32px;
}

.wk__head {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: center;
  justify-content: space-between;
}

.wk__title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #1d1d1f;
  letter-spacing: -0.015em;
}

.wk__sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: #6e6e73;
}

.wk__summary-pills {
  display: inline-flex;
  gap: 10px;
  align-items: center;
  height: 36px;
  padding: 0 14px;
  font-size: 12.5px;
  color: #6e6e73;
  background: rgb(255 255 255 / 80%);
  border-radius: 999px;
  box-shadow: var(--ap-shadow-rest);
}

.wk__summary-pills b {
  margin-left: 2px;
  font-variant-numeric: tabular-nums;
  color: #1d1d1f;
}

.wk__sep {
  width: 1px;
  height: 12px;
  background: rgb(0 0 0 / 10%);
}

.wk__create-btn {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  height: 36px;
  padding: 0 16px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  cursor: pointer;
  background: linear-gradient(180deg, #1a88ff 0%, #007aff 100%);
  border: 0;
  border-radius: 999px;
  box-shadow:
    inset 0 0.5px 0 rgb(255 255 255 / 28%),
    0 3px 10px rgb(0 122 255 / 24%);
  transition: transform var(--ap-dur-fast) var(--ap-ease);
}

.wk__create-btn:active {
  transform: scale(0.97);
}

/* 工作流步骤条 */
.wk__flow {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 12px 18px;
  background: rgb(255 255 255 / 78%);
  border-radius: 16px;
  box-shadow: var(--ap-shadow-rest);
}

.wk__flow-step {
  display: inline-flex;
  gap: 7px;
  align-items: center;
  font-size: 12.5px;
  font-weight: 600;
  color: #3a3a3c;
}

.wk__flow-icon {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  color: #5856d6;
  background: #ededfc;
  border-radius: 7px;
}

/* 空状态 */
.wk__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56px 24px;
  text-align: center;
  background: #fff;
  border-radius: 20px;
  box-shadow: var(--ap-shadow-rest);
}

.wk__empty-orb {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  margin-bottom: 14px;
  color: #fff;
  background: linear-gradient(135deg, #34c759 0%, #007aff 100%);
  border-radius: 18px;
  box-shadow: 0 8px 22px rgb(0 122 255 / 22%);
}

.wk__empty h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #1d1d1f;
}

.wk__empty p {
  max-width: 460px;
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.65;
  color: #6e6e73;
}

/* 卡片矩阵 */
.wk__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.wk-card {
  padding: 18px 20px;
  cursor: pointer;
  background: #fff;
  border-radius: 18px;
  box-shadow: var(--ap-shadow-rest);
  transition:
    transform var(--ap-dur-fast) var(--ap-ease),
    box-shadow var(--ap-dur-fast);
}

.wk-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--ap-shadow-lift);
}

.wk-card__title {
  overflow: hidden;
  font-size: 15px;
  font-weight: 700;
  color: #1d1d1f;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wk-card__meta {
  margin-top: 3px;
  font-size: 12px;
  color: #86868b;
}

.wk-card__bar {
  height: 5px;
  margin: 14px 0 12px;
  overflow: hidden;
  background: #f2f3f7;
  border-radius: 999px;
}

.wk-card__bar i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #007aff 0%, #34c759 100%);
  border-radius: 999px;
  transition: width 400ms var(--ap-ease);
}

.wk-card__stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  padding-top: 10px;
  border-top: 0.5px solid var(--ap-separator);
}

.wk-stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.wk-stat small {
  font-size: 11px;
  color: #8e8e93;
}

.wk-stat b {
  font-size: 15px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: #1d1d1f;
}

/* 抽屉内步骤与筛选 */
.wk-steps {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 10px 14px;
  margin-bottom: 16px;
  background: #f5f6fa;
  border-radius: 14px;
}

.wk-step-pill {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  padding: 6px 12px;
  font-size: 13px;
  font-weight: 500;
  color: #6e6e73;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 999px;
}

.wk-step-pill:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.wk-step-pill.on {
  font-weight: 600;
  color: #1d1d1f;
  background: #fff;
  box-shadow: 0 1px 4px rgb(0 0 0 / 8%);
}

.wk-step-num {
  display: inline-grid;
  place-items: center;
  width: 20px;
  height: 20px;
  font-size: 11px;
  font-weight: 700;
  color: #6e6e73;
  background: #e5e5ea;
  border-radius: 50%;
}

.wk-step-pill.on .wk-step-num {
  color: #fff;
  background: #007aff;
}

.wk-step-pill.done .wk-step-num {
  color: #fff;
  background: #34c759;
}

.wk-filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
}

.wk-hint {
  display: flex;
  gap: 7px;
  align-items: flex-start;
  padding: 9px 12px;
  margin-bottom: 12px;
  font-size: 12px;
  line-height: 1.5;
  color: #6e6e73;
  background: #f5f6fa;
  border-radius: 11px;
}

.wk-drawer-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 16px;
  margin-top: 16px;
  border-top: 0.5px solid var(--ap-separator);
}

/* 策略配置块 */
.wk-config {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.wk-block {
  padding: 15px 16px;
  background: #f7f8fb;
  border-radius: 14px;
}

.wk-block--row {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
}

.wk-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #1d1d1f;
}

.wk-label small {
  font-size: 11.5px;
  font-weight: 400;
  color: #8e8e93;
}

.wk-choice-grid {
  display: grid;
  gap: 10px;
}

.wk-choice-grid.is-3 {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.wk-choice-grid.is-2 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.wk-choice {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
  padding: 12px 14px;
  text-align: left;
  cursor: pointer;
  background: #fff;
  border: 1.5px solid transparent;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgb(0 0 0 / 4%);
  transition: all var(--ap-dur-fast);
}

.wk-choice.on {
  background: rgb(0 122 255 / 5%);
  border-color: #007aff;
}

.wk-choice b {
  font-size: 13px;
  font-weight: 600;
  color: #1d1d1f;
}

.wk-choice small {
  font-size: 11.5px;
  line-height: 1.4;
  color: #6e6e73;
}

.wk-warn {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 9px 12px;
  margin-top: 10px;
  font-size: 12px;
  color: #b25e00;
  background: #fff4e0;
  border-radius: 10px;
}

/* 详情抽屉指标与洞察卡 */
.wk-kpi-strip {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.wk-kpi-tile {
  padding: 10px 12px;
  text-align: center;
  background: #f7f8fb;
  border-radius: 12px;
}

.wk-kpi-tile.is-green {
  background: #eaf9ee;
}

.wk-kpi-tile.is-orange {
  background: #fff4e0;
}

.wk-kpi-tile.is-blue,
.wk-kpi-tile.is-processing {
  background: #e8f2ff;
}

.wk-kpi-tile.is-red {
  background: #ffeceb;
}

.wk-kpi-tile__num {
  font-size: 19px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: #1d1d1f;
}

.wk-kpi-tile__lbl {
  margin-top: 2px;
  font-size: 11.5px;
  color: #6e6e73;
}

.wk-gen-banner {
  padding: 10px 14px;
  margin-bottom: 14px;
  background: #f5f3ff;
  border-radius: 12px;
}

.wk-batch-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  margin-bottom: 12px;
  background: #f7f8fb;
  border-radius: 12px;
}

.wk-insight {
  padding: 7px 10px;
  margin-bottom: 6px;
  font-size: 11.5px;
  line-height: 1.55;
  color: #48484a;
  background: linear-gradient(135deg, #f6f4ff 0%, #f0f7ff 100%);
  border-radius: 10px;
  box-shadow: inset 0 0 0 0.5px rgb(88 86 214 / 12%);
}

@media (width <= 1180px) {
  .wk__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 760px) {
  .wk__grid,
  .wk-choice-grid.is-3,
  .wk-choice-grid.is-2 {
    grid-template-columns: 1fr;
  }

  .wk-kpi-strip {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
