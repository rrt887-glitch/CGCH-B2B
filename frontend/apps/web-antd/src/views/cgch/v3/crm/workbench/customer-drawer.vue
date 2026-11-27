<script lang="ts" setup>
/**
 * 右侧滑出式 360° 客户全景抽屉（Twenty CRM / Apple Inspector 风格）：
 * - 点击看板卡片或表格任意行丝滑滑出，零跳页打断，支持「↑ 上一位 / ↓ 下一位」连续批阅
 * - 顶部集成：姓名/公司直改、星标关注、国旗与买家当地实时日夜时钟、AI 托管开关、快捷发消息/邮件/登记订单/暂缓归档
 * - 中部常驻：6 步可点击阶段推进条 + 4 格「📌 商务黑板」（支持一键从沟通记录智能提取）
 * - 下部四栏合一：沟通与跟进融合时间轴（含 5 秒极速写跟进） / AI 深度背调 / 档案与标签 / 成交订单
 */
import type { ChannelResearchApi } from '#/api/channel/research';
import type { CgchCrmApi } from '#/api/crm/cgch';

import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { IconifyIcon } from '@vben/icons';

import {
  DatePicker,
  Drawer,
  Empty,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Popover,
  Select,
  Spin,
  Switch,
  Tooltip,
} from 'ant-design-vue';
import dayjs from 'dayjs';

import {
  applyResearch,
  createResearch,
  getResearch,
  getResearchPage,
} from '#/api/channel/research';
import { updateCustomerTags } from '#/api/crm/cgch';
import { getContractPageByCustomer } from '#/api/crm/contract';
import {
  getCustomer,
  putCustomerPool,
  transferCustomer,
  updateCustomer,
  updateCustomerStage,
} from '#/api/crm/customer';
import { deleteFollowUpRecord, getFollowUpRecordPage } from '#/api/crm/followup';
import { BizTypeEnum } from '#/api/crm/permission';
import { requestClient } from '#/api/request';
import { getSimpleUserList } from '#/api/system/user';
import { COUNTRY_OPTIONS } from '#/views/cgch/crm/shared/countries';
import {
  countryName,
  crmAvatarBg,
  crmInitial,
  levelLabel,
  sourceLabel,
} from '#/views/cgch/v3/crm/components/shared';

import {
  buildSafeCustomerPayload,
  quickCreateFollowUp,
  saveCustomerDealBoard,
  toggleCustomerStarred,
} from './crm-actions';
import type { CurrencyCode } from './deal-meta';
import {
  analyzeCustomerHealth,
  corporateDomain,
  countryFlag,
  CURRENCIES,
  extractDealHintsFromTexts,
  formatDealMoney,
  getBuyerLocalClock,
  getStageMeta,
  LOST_REASONS,
  parseDealBoard,
  PIPELINE_STAGES,
} from './deal-meta';

interface TimelineChannelItem {
  ai?: boolean;
  channel: 'im' | 'mail';
  contact?: string;
  conversationId?: number;
  direction: number;
  fileName?: string;
  mailId?: number;
  platform: string;
  subject?: string;
  text?: string;
  time: number | string;
  type?: string;
}

const props = defineProps<{
  customerId: null | number;
  groups?: CgchCrmApi.TreeNode[];
  hasNext?: boolean;
  hasPrev?: boolean;
  open: boolean;
  tags?: CgchCrmApi.TreeNode[];
}>();

const emit = defineEmits<{
  changed: [];
  next: [];
  'open-order-modal': [customer: Record<string, any>];
  prev: [];
  'update:open': [val: boolean];
}>();

const router = useRouter();

const loading = ref(false);
const detail = ref<null | Record<string, any>>(null);
const subTab = ref<'orders' | 'profile' | 'research' | 'timeline'>('timeline');

// 商务黑板编辑态
const boardEditing = ref(false);
const boardSaving = ref(false);
const boardForm = reactive({
  currency: 'USD' as CurrencyCode,
  amount: undefined as number | undefined,
  productQty: '',
  quoteTerms: '',
  sampleInfo: '',
});

// 暂缓归档弹窗态
const archivePopOpen = ref(false);
const selectedLostReason = ref(LOST_REASONS[0]!);

// 融合时间轴 & 5 秒极速跟进
const timelineLoading = ref(false);
const channelItems = ref<TimelineChannelItem[]>([]);
const followUpItems = ref<any[]>([]);
const streamFilter = ref<'all' | 'followup' | 'message'>('all');
const followNote = ref('');
const followQuickKey = ref<'2w' | '3d' | 'custom' | 'mon' | 'tmr'>('3d');
const followCustomDate = ref(dayjs().add(3, 'day').format('YYYY-MM-DD'));
const submittingFollow = ref(false);

const FOLLOW_TEMPLATES = [
  '已发送产品目录与阶梯报价单，待买家反馈',
  '买家询问定制 Logo 与包装起订量，已回复方案',
  '样品已安排寄出并同步单号，提醒留意签收',
  '已发送形式发票 PI，跟进首笔定金水单',
  'WhatsApp 语音沟通顺畅，确认下周复盘采购计划',
];

// AI 背调
const researchLoading = ref(false);
const researchRunning = ref(false);
const latestResearch = ref<ChannelResearchApi.Research | null>(null);
let researchTimer: null | ReturnType<typeof setInterval> = null;

// 客户档案编辑
const profileSaving = ref(false);
const profileForm = reactive({
  name: '',
  companyName: '',
  countryCode: undefined as string | undefined,
  email: '',
  whatsapp: '',
  website: '',
  level: undefined as number | undefined,
  source: undefined as number | undefined,
  groupId: undefined as number | undefined,
  tagIds: [] as number[],
  note: '',
});

// 订单列表
const ordersLoading = ref(false);
const customerOrders = ref<any[]>([]);

// 转交负责人弹窗
const transferModalOpen = ref(false);
const transferTargetId = ref<number | undefined>();
const transferring = ref(false);
const simpleUsers = ref<{ id: number; nickname: string }[]>([]);

const board = computed(() => parseDealBoard(detail.value?.remark));
const stageMeta = computed(() =>
  getStageMeta(detail.value?.stage, detail.value?.dealStatus),
);
const localClock = computed(() =>
  getBuyerLocalClock(detail.value?.countryCode),
);
const health = computed(() =>
  detail.value ? analyzeCustomerHealth(detail.value, board.value) : undefined,
);
const corpDomain = computed(() =>
  corporateDomain(detail.value?.email, detail.value?.website),
);

const levelOptions = computed(() =>
  getDictOptions(DICT_TYPE.CRM_CUSTOMER_LEVEL, 'number'),
);
const sourceOptions = computed(() =>
  getDictOptions(DICT_TYPE.CRM_CUSTOMER_SOURCE, 'number'),
);

const latestImConvId = computed(
  () => channelItems.value.find((i) => i.channel === 'im' && i.conversationId)?.conversationId,
);
const latestMailId = computed(
  () => channelItems.value.find((i) => i.channel === 'mail' && i.mailId)?.mailId,
);

/** 合并沟通记录与人工跟进记录，按时间倒序展示 */
const unifiedStream = computed(() => {
  const merged: Array<{
    ai?: boolean;
    channel?: 'im' | 'mail';
    content: string;
    conversationId?: number;
    creator?: string;
    direction?: number;
    followupId?: number;
    id: string;
    kind: 'followup' | 'message';
    mailId?: number;
    nextTime?: any;
    platformLabel: string;
    subject?: string;
    ts: number;
  }> = [];

  if (streamFilter.value !== 'followup') {
    const PLATFORM_NAME: Record<string, string> = {
      whatsapp: 'WhatsApp',
      messenger: 'Facebook',
      instagram: 'Instagram',
      web: '官网在线客服',
      youtube: 'YouTube',
      tiktok: 'TikTok',
      mail: '邮件往来',
    };
    channelItems.value.forEach((m, idx) => {
      const ts = dayjs(m.time as any).isValid() ? dayjs(m.time as any).valueOf() : 0;
      const body =
        m.text ||
        (m.fileName ? `[附件] ${m.fileName}` : m.type && m.type !== 'text' ? `[${m.type}]` : '');
      merged.push({
        id: `msg-${m.channel}-${m.conversationId ?? m.mailId ?? idx}-${idx}`,
        kind: 'message',
        ts,
        platformLabel: PLATFORM_NAME[m.platform] || m.platform || '消息',
        channel: m.channel,
        direction: m.direction,
        ai: m.ai,
        subject: m.subject,
        content: body,
        conversationId: m.conversationId,
        mailId: m.mailId,
      });
    });
  }

  if (streamFilter.value !== 'message') {
    followUpItems.value.forEach((f) => {
      const ts = dayjs(f.createTime).isValid() ? dayjs(f.createTime).valueOf() : 0;
      merged.push({
        id: `fu-${f.id}`,
        kind: 'followup',
        followupId: f.id,
        ts,
        platformLabel: '业务跟进记录',
        creator: f.creatorName || '我方跟进',
        content: f.content || '',
        nextTime: f.nextTime,
      });
    });
  }

  return merged.sort((a, b) => b.ts - a.ts);
});

const totalOrderAmount = computed(() =>
  customerOrders.value.reduce((sum, o) => sum + Number(o.totalPrice || 0), 0),
);

function syncFormsFromDetail(d: Record<string, any>) {
  const b = parseDealBoard(d.remark);
  boardForm.currency = b.currency || 'USD';
  boardForm.amount = b.amount;
  boardForm.productQty = b.productQty || '';
  boardForm.quoteTerms = b.quoteTerms || '';
  boardForm.sampleInfo = b.sampleInfo || '';

  profileForm.name = d.name || '';
  profileForm.companyName = d.companyName || '';
  profileForm.countryCode = d.countryCode || undefined;
  profileForm.email = d.email || '';
  profileForm.whatsapp = d.whatsapp || d.mobile || d.telephone || '';
  profileForm.website = d.website || '';
  profileForm.level = d.level ?? undefined;
  profileForm.source = d.source ?? undefined;
  profileForm.groupId = d.groupId ?? undefined;
  profileForm.tagIds = Array.isArray(d.tagIds) ? [...d.tagIds] : [];
  profileForm.note = b.note || '';
}

async function loadCustomerAll(id: number) {
  loading.value = true;
  stopResearchPoll();
  try {
    const cust: any = await getCustomer(id);
    detail.value = cust;
    if (cust) {
      syncFormsFromDetail(cust);
    }
    await Promise.all([
      loadTimeline(id),
      loadResearch(id),
      loadOrders(id),
    ]);
  } finally {
    loading.value = false;
  }
}

async function loadTimeline(id: number) {
  timelineLoading.value = true;
  try {
    const [chRes, fuRes] = await Promise.all([
      requestClient
        .get<{ list: TimelineChannelItem[] }>('/channel/customer-timeline/list', {
          params: { customerId: id, limit: 60 },
        })
        .catch(() => ({ list: [] as TimelineChannelItem[] })),
      getFollowUpRecordPage({
        bizType: BizTypeEnum.CRM_CUSTOMER,
        bizId: id,
        pageNo: 1,
        pageSize: 50,
      } as any).catch(() => ({ list: [] as any[] })),
    ]);
    channelItems.value = chRes?.list ?? [];
    followUpItems.value = fuRes?.list ?? [];
  } finally {
    timelineLoading.value = false;
  }
}

async function loadResearch(id: number) {
  researchLoading.value = true;
  try {
    const pageRes = await getResearchPage({
      pageNo: 1,
      pageSize: 1,
      customerId: id,
    } as any).catch(() => ({ list: [] as ChannelResearchApi.Research[] }));
    const first = pageRes?.list?.[0];
    if (first?.id) {
      const full = await getResearch(first.id).catch(() => first);
      latestResearch.value = full;
      researchRunning.value = full.status === 'running';
      if (researchRunning.value) startResearchPoll(full.id);
    } else {
      latestResearch.value = null;
      researchRunning.value = false;
    }
  } finally {
    researchLoading.value = false;
  }
}

function stopResearchPoll() {
  if (researchTimer) {
    clearInterval(researchTimer);
    researchTimer = null;
  }
}

function startResearchPoll(researchId: number) {
  stopResearchPoll();
  researchTimer = setInterval(async () => {
    try {
      const r = await getResearch(researchId);
      latestResearch.value = r;
      if (r.status !== 'running') {
        researchRunning.value = false;
        stopResearchPoll();
        if (r.status === 'done') {
          message.success('AI 深度背调已完成');
          emit('changed');
        }
      }
    } catch {
      stopResearchPoll();
    }
  }, 4000);
}

async function loadOrders(id: number) {
  ordersLoading.value = true;
  try {
    const res: any = await getContractPageByCustomer({
      customerId: id,
      pageNo: 1,
      pageSize: 50,
    } as any).catch(() => ({ list: [] }));
    customerOrders.value = res?.list ?? [];
  } finally {
    ordersLoading.value = false;
  }
}

/** 切换阶段 */
async function handleStageSelect(stageVal: number) {
  if (!detail.value) return;
  if (board.value.archived) {
    await saveCustomerDealBoard(detail.value, { archived: false, lostReason: '' });
  }
  await updateCustomerStage(detail.value.id, stageVal);
  detail.value.stage = stageVal;
  const meta = getStageMeta(stageVal);
  message.success(`客户阶段已更新为「${meta.label}」`);
  emit('changed');
  void loadTimeline(detail.value.id);
}

/** 星标重点关注 */
async function handleToggleStar() {
  if (!detail.value) return;
  const next = !detail.value.starred;
  await toggleCustomerStarred(detail.value.id, next);
  detail.value.starred = next;
  message.success(next ? '已设为星标重点客户' : '已取消星标');
  emit('changed');
}

/** AI 托管接待开关 */
async function handleToggleAiHosted(checked: any) {
  if (!detail.value) return;
  const val = Boolean(checked);
  const payload = buildSafeCustomerPayload(detail.value, { aiHosted: val });
  await updateCustomer(payload as any);
  detail.value.aiHosted = val;
  message.success(val ? '已开启 AI 数字员工自动接待' : '已切换为人工优先接待');
  emit('changed');
}

/** 保存 4 格商务黑板 */
async function handleSaveBoard() {
  if (!detail.value) return;
  boardSaving.value = true;
  try {
    await saveCustomerDealBoard(detail.value, {
      currency: boardForm.currency,
      amount: boardForm.amount ?? undefined,
      productQty: boardForm.productQty,
      quoteTerms: boardForm.quoteTerms,
      sampleInfo: boardForm.sampleInfo,
    });
    boardEditing.value = false;
    message.success('商务黑板已保存');
    emit('changed');
  } finally {
    boardSaving.value = false;
  }
}

/** ✦ 一键从沟通记录与跟进中智能提取报价/数量/快递单号 */
async function handleAutoExtractBoard() {
  if (!detail.value) return;
  const texts: string[] = [
    detail.value.contactLastContent || '',
    ...channelItems.value.map((i) => `${i.subject || ''} ${i.text || ''}`),
    ...followUpItems.value.map((f) => f.content || ''),
  ];
  const hints = extractDealHintsFromTexts(texts);
  if (
    !hints.amount &&
    !hints.productQty &&
    !hints.quoteTerms &&
    !hints.sampleInfo
  ) {
    boardEditing.value = true;
    message.info('暂未在沟通文本中识别到明确报价/数量格式，您可直接手动填写');
    return;
  }
  if (hints.currency) boardForm.currency = hints.currency;
  if (hints.amount && !boardForm.amount) boardForm.amount = hints.amount;
  if (hints.productQty && !boardForm.productQty) boardForm.productQty = hints.productQty;
  if (hints.quoteTerms) boardForm.quoteTerms = hints.quoteTerms;
  if (hints.sampleInfo) boardForm.sampleInfo = hints.sampleInfo;
  boardEditing.value = true;
  message.success('已从沟通记录提取关键线索，请核对后点保存');
}

/** 暂缓归档 / 恢复跟进 */
async function handleArchiveConfirm() {
  if (!detail.value) return;
  await saveCustomerDealBoard(detail.value, {
    archived: true,
    lostReason: selectedLostReason.value,
  });
  archivePopOpen.value = false;
  message.success(`已归入「暂缓/流失」（原因：${selectedLostReason.value}）`);
  emit('changed');
}

async function handleRestoreActive() {
  if (!detail.value) return;
  await saveCustomerDealBoard(detail.value, {
    archived: false,
    lostReason: '',
  });
  message.success('已恢复到活跃跟进管道');
  emit('changed');
}

/** 计算下次跟进时间 */
function resolveNextFollowTime(): dayjs.Dayjs {
  const base = dayjs().hour(10).minute(0).second(0);
  if (followQuickKey.value === 'tmr') return base.add(1, 'day');
  if (followQuickKey.value === '3d') return base.add(3, 'day');
  if (followQuickKey.value === '2w') return base.add(14, 'day');
  if (followQuickKey.value === 'mon') {
    const d = base.day();
    const diff = d === 0 ? 1 : 8 - d;
    return base.add(diff, 'day');
  }
  const custom = dayjs(followCustomDate.value).hour(10).minute(0).second(0);
  return custom.isValid() ? custom : base.add(3, 'day');
}

/** 5 秒极速提交跟进记录 */
async function handleAddFollowUp() {
  if (!detail.value) return;
  if (!followNote.value.trim()) {
    message.warning('请输入简短跟进进展');
    return;
  }
  submittingFollow.value = true;
  try {
    const nextMoment = resolveNextFollowTime();
    await quickCreateFollowUp({
      customerId: detail.value.id,
      content: followNote.value.trim(),
      nextTime: nextMoment.toDate(),
    });
    followNote.value = '';
    detail.value.contactNextTime = nextMoment.toISOString();
    detail.value.contactLastTime = new Date().toISOString();
    message.success(`跟进已记录，下次提醒：${nextMoment.format('MM-DD')}`);
    await loadTimeline(detail.value.id);
    emit('changed');
  } finally {
    submittingFollow.value = false;
  }
}

async function handleDeleteFollowUp(fuId?: number) {
  if (!fuId || !detail.value) return;
  await deleteFollowUpRecord(fuId);
  message.success('已删除跟进记录');
  await loadTimeline(detail.value.id);
}

/** 发起与同步 AI 背调 */
async function handleTriggerResearch() {
  if (!detail.value) return;
  researchRunning.value = true;
  try {
    const rid = await createResearch({
      customerId: detail.value.id,
      companyName: detail.value.companyName || detail.value.name,
      website: detail.value.website || undefined,
    });
    message.success('已启动 AI 深度背调，预计 15~30 秒完成');
    if (rid) startResearchPoll(rid);
  } catch {
    researchRunning.value = false;
  }
}

async function handleApplyResearch() {
  if (!latestResearch.value?.id || !detail.value) return;
  const updated = await applyResearch(latestResearch.value.id);
  message.success(
    updated?.length ? `已补全字段：${updated.join('、')}` : '档案已是最新信息',
  );
  await loadCustomerAll(detail.value.id);
  emit('changed');
}

/** 保存客户档案与标签 */
async function handleSaveProfile() {
  if (!detail.value) return;
  if (!profileForm.name.trim()) {
    message.warning('客户名称不能为空');
    return;
  }
  profileSaving.value = true;
  try {
    const currentBoard = parseDealBoard(detail.value.remark);
    currentBoard.note = profileForm.note;
    const payload = buildSafeCustomerPayload(detail.value, {
      name: profileForm.name.trim(),
      companyName: profileForm.companyName.trim() || undefined,
      countryCode: profileForm.countryCode,
      email: profileForm.email.trim() || undefined,
      whatsapp: profileForm.whatsapp.trim() || undefined,
      website: profileForm.website.trim() || undefined,
      level: profileForm.level,
      source: profileForm.source,
      groupId: profileForm.groupId,
      tagIds: profileForm.tagIds,
    });
    await updateCustomer(payload as any);
    await saveCustomerDealBoard(
      { ...detail.value, ...payload },
      { note: profileForm.note },
    );
    await updateCustomerTags(
      detail.value.id,
      profileForm.tagIds,
      profileForm.groupId,
    ).catch(() => undefined);

    const fresh: any = await getCustomer(detail.value.id);
    if (fresh) {
      detail.value = fresh;
      syncFormsFromDetail(fresh);
    }
    message.success('客户档案与标签已保存');
    emit('changed');
  } finally {
    profileSaving.value = false;
  }
}

/** 快捷跳转消息 / 邮件 / AI 唤醒 */
function jumpToIm() {
  if (latestImConvId.value) {
    router.push({
      path: '/v3/conversation',
      query: { channel: 'whatsapp', ref: String(latestImConvId.value) },
    });
  } else {
    router.push({ path: '/v3/conversation', query: { channel: 'whatsapp' } });
  }
}

function jumpToMail() {
  if (latestMailId.value) {
    router.push({
      path: '/v3/conversation',
      query: { channel: 'mail', ref: String(latestMailId.value) },
    });
  } else {
    router.push({ path: '/v3/conversation', query: { channel: 'mail' } });
  }
}

function jumpToMessageItem(item: {
  channel?: 'im' | 'mail';
  conversationId?: number;
  mailId?: number;
}) {
  if (item.channel === 'im' && item.conversationId) {
    router.push({
      path: '/v3/conversation',
      query: { channel: 'whatsapp', ref: String(item.conversationId) },
    });
  } else if (item.mailId) {
    router.push({
      path: '/v3/conversation',
      query: { channel: 'mail', ref: String(item.mailId) },
    });
  }
}

/** 放入公海 & 转交负责人 */
async function handlePutPool() {
  if (!detail.value) return;
  await putCustomerPool(detail.value.id);
  message.success(`已将「${detail.value.name}」放回公海池`);
  emit('update:open', false);
  emit('changed');
}

async function openTransferModal() {
  if (!simpleUsers.value.length) {
    simpleUsers.value = ((await getSimpleUserList().catch(() => [])) as any[]) || [];
  }
  transferTargetId.value = undefined;
  transferModalOpen.value = true;
}

async function handleConfirmTransfer() {
  if (!detail.value || !transferTargetId.value) {
    message.warning('请选择接收负责人');
    return;
  }
  transferring.value = true;
  try {
    await transferCustomer({
      id: detail.value.id,
      newOwnerUserId: transferTargetId.value,
    } as any);
    message.success('客户已成功转交');
    transferModalOpen.value = false;
    await loadCustomerAll(detail.value.id);
    emit('changed');
  } finally {
    transferring.value = false;
  }
}

function fmtStreamTime(ts: number) {
  if (!ts) return '—';
  const d = dayjs(ts);
  return d.year() === dayjs().year()
    ? d.format('MM-DD HH:mm')
    : d.format('YYYY-MM-DD HH:mm');
}

watch(
  () => [props.open, props.customerId] as const,
  ([isOpen, cid]) => {
    if (isOpen && cid) {
      boardEditing.value = false;
      void loadCustomerAll(cid);
    } else if (!isOpen) {
      stopResearchPoll();
    }
  },
  { immediate: true },
);

onBeforeUnmount(() => stopResearchPoll());

defineExpose({
  reloadOrders: () => {
    if (detail.value?.id) void loadOrders(detail.value.id);
    if (detail.value?.id) void loadCustomerAll(detail.value.id);
  },
});
</script>

<template>
  <Drawer
    :open="open"
    :width="680"
    placement="right"
    :closable="false"
    root-class-name="crm-360-drawer"
    @close="emit('update:open', false)"
  >
    <Spin :spinning="loading" wrapper-class-name="cd-spin-root">
      <div v-if="detail" class="cd-shell">
        <!-- ==================== 1. 顶部毛玻璃名片头 ==================== -->
        <header class="cd-header">
          <div class="flex items-start justify-between gap-3">
            <div class="flex min-w-0 items-center gap-3">
              <span
                class="cd-avatar"
                :style="{ background: crmAvatarBg(detail.name || detail.id) }"
              >
                {{ crmInitial(detail.name) }}
              </span>

              <div class="min-w-0">
                <div class="flex flex-wrap items-center gap-1.5">
                  <h2 class="m-0 truncate text-[18px] font-bold tracking-tight text-[#1D1D1F]">
                    {{ detail.name }}
                  </h2>
                  <button
                    type="button"
                    class="cd-star-btn"
                    :class="{ on: detail.starred }"
                    :title="detail.starred ? '取消星标关注' : '设为星标重点客户'"
                    @click="handleToggleStar"
                  >
                    <IconifyIcon
                      :icon="detail.starred ? 'lucide:star' : 'lucide:star'"
                      class="size-4"
                    />
                  </button>
                  <span
                    v-if="board.archived"
                    class="rounded-full bg-[#F2F2F7] px-2.5 py-0.5 text-[11px] font-semibold text-[#6E6E73]"
                  >
                    ⏸ 已暂缓 · {{ board.lostReason || '归档' }}
                  </span>
                  <span
                    v-else-if="health"
                    class="cd-health-pill"
                    :class="`cd-health-pill--${health.nextTone}`"
                  >
                    {{ health.nextLabel }}
                  </span>
                </div>

                <div class="mt-1 flex flex-wrap items-center gap-2 text-[12px] text-[#6E6E73]">
                  <span v-if="detail.companyName" class="font-medium text-[#3A3A3C]">
                    {{ detail.companyName }}
                  </span>
                  <span v-if="detail.countryCode" class="cd-clock-chip">
                    <span>{{ countryFlag(detail.countryCode) }}</span>
                    <span>{{ countryName(detail.countryCode) }}</span>
                    <template v-if="localClock">
                      <span class="opacity-40">·</span>
                      <span>{{ localClock.icon }} {{ localClock.time }}</span>
                      <span class="text-[11px] opacity-80">({{ localClock.label }})</span>
                    </template>
                  </span>
                  <span v-if="detail.ownerUserName" class="text-[#86868B]">
                    负责人：{{ detail.ownerUserName }}
                  </span>
                </div>
              </div>
            </div>

            <!-- 右上角翻页器 & 关闭 -->
            <div class="flex shrink-0 items-center gap-1.5">
              <Tooltip title="上一位客户">
                <button
                  type="button"
                  class="cd-icon-btn"
                  :disabled="!hasPrev"
                  @click="emit('prev')"
                >
                  <IconifyIcon icon="lucide:chevron-up" class="size-4" />
                </button>
              </Tooltip>
              <Tooltip title="下一位客户">
                <button
                  type="button"
                  class="cd-icon-btn"
                  :disabled="!hasNext"
                  @click="emit('next')"
                >
                  <IconifyIcon icon="lucide:chevron-down" class="size-4" />
                </button>
              </Tooltip>
              <button
                type="button"
                class="cd-icon-btn"
                title="关闭抽屉"
                @click="emit('update:open', false)"
              >
                <IconifyIcon icon="lucide:x" class="size-4" />
              </button>
            </div>
          </div>

          <!-- 同企业撞单预警横幅 -->
          <div v-if="board.collisionHint" class="cd-collision-alert">
            <IconifyIcon icon="lucide:shield-alert" class="size-4 shrink-0" />
            <span>防撞单提醒：{{ board.collisionHint }}（企业域名 {{ corpDomain || '相同' }}）</span>
          </div>

          <!-- 快捷动作胶囊条 -->
          <div class="mt-3 flex flex-wrap items-center gap-1.5">
            <button type="button" class="cd-act cd-act--primary" @click="jumpToIm">
              <IconifyIcon icon="lucide:message-circle" class="size-3.5" />
              <span>WhatsApp / 会话</span>
            </button>
            <button type="button" class="cd-act" @click="jumpToMail">
              <IconifyIcon icon="lucide:mail" class="size-3.5" />
              <span>发邮件</span>
            </button>
            <button
              type="button"
              class="cd-act cd-act--green"
              @click="emit('open-order-modal', detail)"
            >
              <IconifyIcon icon="lucide:receipt" class="size-3.5" />
              <span>登记订单</span>
            </button>
            <button
              type="button"
              class="cd-act cd-act--purple"
              @click="router.push({ path: '/v3/crm', query: { tab: 'salesmind', sub: 'wake' } })"
            >
              <IconifyIcon icon="lucide:sparkles" class="size-3.5" />
              <span>AI 追单唤醒</span>
            </button>

            <!-- 暂缓归档 / 恢复 -->
            <button
              v-if="board.archived"
              type="button"
              class="cd-act"
              @click="handleRestoreActive"
            >
              <IconifyIcon icon="lucide:rotate-ccw" class="size-3.5" />
              <span>恢复跟进</span>
            </button>
            <Popover
              v-else
              v-model:open="archivePopOpen"
              trigger="click"
              placement="bottomRight"
            >
              <template #content>
                <div class="w-64 p-1">
                  <div class="mb-2 text-[12px] font-semibold text-[#1D1D1F]">
                    选择暂缓/未成交原因（便于后续 AI 唤醒）
                  </div>
                  <div class="flex flex-col gap-1">
                    <button
                      v-for="r in LOST_REASONS"
                      :key="r"
                      type="button"
                      class="rounded-lg px-2.5 py-1.5 text-left text-[12px] transition"
                      :class="
                        selectedLostReason === r
                          ? 'bg-[#E8F2FF] font-semibold text-[#0071E3]'
                          : 'text-[#3A3A3C] hover:bg-[#F2F2F7]'
                      "
                      @click="selectedLostReason = r"
                    >
                      {{ r }}
                    </button>
                  </div>
                  <div class="mt-2.5 flex justify-end gap-1.5">
                    <button
                      type="button"
                      class="cd-act"
                      @click="archivePopOpen = false"
                    >
                      取消
                    </button>
                    <button
                      type="button"
                      class="cd-act cd-act--primary"
                      @click="handleArchiveConfirm"
                    >
                      确认归档
                    </button>
                  </div>
                </div>
              </template>
              <button type="button" class="cd-act">
                <IconifyIcon icon="lucide:pause-circle" class="size-3.5" />
                <span>暂缓归档</span>
              </button>
            </Popover>

            <button type="button" class="cd-act" @click="openTransferModal">
              <IconifyIcon icon="lucide:arrow-right-left" class="size-3.5" />
              <span>转交</span>
            </button>

            <Popconfirm
              title="确定将该客户放回公海池吗？"
              ok-text="放回公海"
              cancel-text="取消"
              @confirm="handlePutPool"
            >
              <button type="button" class="cd-act cd-act--subtle">
                <IconifyIcon icon="lucide:globe" class="size-3.5" />
                <span>放入公海</span>
              </button>
            </Popconfirm>

            <div class="ml-auto flex items-center gap-1.5 text-[11.5px] text-[#6E6E73]">
              <span>AI 托管</span>
              <Switch
                size="small"
                :checked="Boolean(detail.aiHosted)"
                @change="handleToggleAiHosted"
              />
            </div>
          </div>

          <!-- 6 步可点击阶段进度条 -->
          <div class="cd-stage-track">
            <button
              v-for="(st, idx) in PIPELINE_STAGES"
              :key="st.value"
              type="button"
              class="cd-stage-step"
              :class="{
                current: !board.archived && stageMeta.value === st.value,
                passed: !board.archived && stageMeta.value > st.value,
              }"
              :style="
                !board.archived && stageMeta.value === st.value
                  ? { background: st.color, color: '#fff', borderColor: st.color }
                  : undefined
              "
              @click="handleStageSelect(st.value)"
            >
              <span class="cd-stage-idx">{{ idx + 1 }}</span>
              <span>{{ st.label }}</span>
            </button>
          </div>
        </header>

        <!-- ==================== 2. 滚动主体区 ==================== -->
        <div class="cd-scroll">
          <!-- 📌 4 格商务黑板（Deal Snapshot） -->
          <section class="cd-board-card">
            <div class="mb-2 flex items-center justify-between">
              <div class="flex items-center gap-1.5">
                <IconifyIcon icon="lucide:layout-grid" class="size-3.5 text-[#0071E3]" />
                <span class="text-[12.5px] font-bold text-[#1D1D1F]">商务黑板</span>
                <span class="text-[11px] text-[#86868B]">核心交易要素一眼掌握</span>
              </div>
              <div class="flex items-center gap-1.5">
                <button
                  type="button"
                  class="cd-mini-link"
                  @click="handleAutoExtractBoard"
                >
                  <IconifyIcon icon="lucide:wand-sparkles" class="size-3" />
                  <span>从聊天智能提取</span>
                </button>
                <button
                  v-if="!boardEditing"
                  type="button"
                  class="cd-mini-btn"
                  @click="boardEditing = true"
                >
                  编辑
                </button>
                <template v-else>
                  <button
                    type="button"
                    class="cd-mini-btn"
                    @click="boardEditing = false"
                  >
                    取消
                  </button>
                  <button
                    type="button"
                    class="cd-mini-btn cd-mini-btn--primary"
                    :disabled="boardSaving"
                    @click="handleSaveBoard"
                  >
                    保存
                  </button>
                </template>
              </div>
            </div>

            <div v-if="!boardEditing" class="grid grid-cols-2 gap-2" @click="boardEditing = true">
              <div class="cd-tile">
                <span class="cd-tile-label">💰 预估单值</span>
                <span class="cd-tile-val" :class="{ 'text-[#0071E3] font-bold': board.amount }">
                  {{ board.amount ? formatDealMoney(board.amount, board.currency) : '点击填写预估单值…' }}
                </span>
              </div>
              <div class="cd-tile">
                <span class="cd-tile-label">📦 意向产品与数量</span>
                <span class="cd-tile-val">
                  {{ board.productQty || '点击填写产品与采购量…' }}
                </span>
              </div>
              <div class="cd-tile">
                <span class="cd-tile-label">📄 最新报价条款</span>
                <span class="cd-tile-val">
                  {{ board.quoteTerms || '例如 FOB Shenzhen $18.5…' }}
                </span>
              </div>
              <div class="cd-tile">
                <span class="cd-tile-label">🚚 寄样进展与单号</span>
                <span class="cd-tile-val">
                  {{ board.sampleInfo || '例如 DHL 单号或测款反馈…' }}
                </span>
              </div>
            </div>

            <div v-else class="grid grid-cols-2 gap-2.5 pt-1">
              <div>
                <div class="mb-1 text-[11px] font-medium text-[#6E6E73]">预估单值与币种</div>
                <div class="flex gap-1.5">
                  <Select
                    v-model:value="boardForm.currency"
                    size="small"
                    class="w-24"
                    :options="CURRENCIES.map((c) => ({ label: c.code, value: c.code }))"
                  />
                  <InputNumber
                    v-model:value="boardForm.amount"
                    size="small"
                    :min="0"
                    placeholder="金额"
                    class="flex-1"
                  />
                </div>
              </div>
              <div>
                <div class="mb-1 text-[11px] font-medium text-[#6E6E73]">意向产品与数量</div>
                <Input
                  v-model:value="boardForm.productQty"
                  size="small"
                  placeholder="如：高速吹风机 · 2000 pcs"
                />
              </div>
              <div>
                <div class="mb-1 text-[11px] font-medium text-[#6E6E73]">最新报价条款</div>
                <Input
                  v-model:value="boardForm.quoteTerms"
                  size="small"
                  placeholder="如：FOB Shenzhen $18.5 · 30% T/T"
                />
              </div>
              <div>
                <div class="mb-1 text-[11px] font-medium text-[#6E6E73]">寄样进展与单号</div>
                <Input
                  v-model:value="boardForm.sampleInfo"
                  size="small"
                  placeholder="如：DHL 5829103812 已签收"
                />
              </div>
            </div>
          </section>

          <!-- 子页签切换器 -->
          <nav class="cd-subnav">
            <button
              type="button"
              class="cd-subtab"
              :class="{ on: subTab === 'timeline' }"
              @click="subTab = 'timeline'"
            >
              <IconifyIcon icon="lucide:history" class="size-3.5" />
              <span>沟通与跟进</span>
              <span class="cd-subcount">{{ unifiedStream.length }}</span>
            </button>
            <button
              type="button"
              class="cd-subtab"
              :class="{ on: subTab === 'research' }"
              @click="subTab = 'research'"
            >
              <IconifyIcon icon="lucide:radar" class="size-3.5" />
              <span>AI 深度背调</span>
              <span
                v-if="latestResearch?.status === 'done'"
                class="size-1.5 rounded-full bg-[#34C759]"
              />
            </button>
            <button
              type="button"
              class="cd-subtab"
              :class="{ on: subTab === 'profile' }"
              @click="subTab = 'profile'"
            >
              <IconifyIcon icon="lucide:contact-round" class="size-3.5" />
              <span>档案与标签</span>
            </button>
            <button
              type="button"
              class="cd-subtab"
              :class="{ on: subTab === 'orders' }"
              @click="subTab = 'orders'"
            >
              <IconifyIcon icon="lucide:receipt" class="size-3.5" />
              <span>成交订单</span>
              <span v-if="customerOrders.length" class="cd-subcount">
                {{ customerOrders.length }}
              </span>
            </button>
          </nav>

          <!-- ========== Tab 1: 沟通与跟进时间轴 ========== -->
          <div v-if="subTab === 'timeline'" class="flex flex-col gap-3">
            <!-- 5 秒极速写跟进框 -->
            <div class="cd-composer">
              <Input.TextArea
                v-model:value="followNote"
                :rows="2"
                placeholder="一句话记下跟进要点（例如：客户确认样品颜色，等周五内部会议批预算）…"
              />
              <div class="mt-2 flex flex-wrap gap-1">
                <button
                  v-for="tpl in FOLLOW_TEMPLATES"
                  :key="tpl"
                  type="button"
                  class="cd-tpl-chip"
                  @click="followNote = tpl"
                >
                  + {{ tpl.slice(0, 12) }}…
                </button>
              </div>
              <div class="mt-2.5 flex flex-wrap items-center justify-between gap-2 border-t border-[#F2F2F7] pt-2">
                <div class="flex flex-wrap items-center gap-1 text-[11.5px] text-[#6E6E73]">
                  <span>下次提醒：</span>
                  <button
                    v-for="opt in [
                      { k: 'tmr', l: '明天' },
                      { k: '3d', l: '3天后' },
                      { k: 'mon', l: '下周一' },
                      { k: '2w', l: '两周后' },
                      { k: 'custom', l: '自选日期' },
                    ]"
                    :key="opt.k"
                    type="button"
                    class="cd-date-pill"
                    :class="{ on: followQuickKey === opt.k }"
                    @click="followQuickKey = opt.k as any"
                  >
                    {{ opt.l }}
                  </button>
                  <DatePicker
                    v-if="followQuickKey === 'custom'"
                    v-model:value="followCustomDate"
                    size="small"
                    value-format="YYYY-MM-DD"
                    format="MM-DD"
                    :allow-clear="false"
                    class="w-28"
                  />
                </div>
                <button
                  type="button"
                  class="cd-act cd-act--primary"
                  :disabled="submittingFollow"
                  @click="handleAddFollowUp"
                >
                  <IconifyIcon icon="lucide:send" class="size-3.5" />
                  <span>记录跟进</span>
                </button>
              </div>
            </div>

            <!-- 时间轴筛选器 -->
            <div class="flex items-center justify-between">
              <div class="flex gap-1">
                <button
                  v-for="f in [
                    { k: 'all', l: '全部动态' },
                    { k: 'message', l: `渠道往来 (${channelItems.length})` },
                    { k: 'followup', l: `跟进小记 (${followUpItems.length})` },
                  ]"
                  :key="f.k"
                  type="button"
                  class="cd-filter-chip"
                  :class="{ on: streamFilter === f.k }"
                  @click="streamFilter = f.k as any"
                >
                  {{ f.l }}
                </button>
              </div>
              <button
                type="button"
                class="cd-mini-btn"
                @click="loadTimeline(detail.id)"
              >
                刷新
              </button>
            </div>

            <!-- 融合流列表 -->
            <Spin :spinning="timelineLoading">
              <Empty
                v-if="!timelineLoading && unifiedStream.length === 0"
                :image="Empty.PRESENTED_IMAGE_SIMPLE"
                description="暂无沟通与跟进记录"
              />
              <div v-else class="cd-stream">
                <div
                  v-for="item in unifiedStream"
                  :key="item.id"
                  class="cd-stream-item"
                >
                  <span
                    class="cd-stream-dot"
                    :class="
                      item.kind === 'followup'
                        ? 'bg-[#FF9500]'
                        : item.direction === 1
                          ? 'bg-[#34C759]'
                          : 'bg-[#0071E3]'
                    "
                  />
                  <div class="cd-stream-card">
                    <div class="flex flex-wrap items-center gap-1.5 text-[11.5px]">
                      <span
                        class="cd-stream-badge"
                        :class="
                          item.kind === 'followup'
                            ? 'cd-stream-badge--amber'
                            : item.channel === 'mail'
                              ? 'cd-stream-badge--purple'
                              : 'cd-stream-badge--green'
                        "
                      >
                        {{ item.platformLabel }}
                      </span>
                      <span v-if="item.kind === 'message'" class="font-medium text-[#3A3A3C]">
                        {{ item.direction === 1 ? '买家来信' : item.ai ? 'AI 数字员工回复' : '我方发出' }}
                      </span>
                      <span v-else class="font-medium text-[#3A3A3C]">
                        {{ item.creator }}
                      </span>
                      <span class="text-[#86868B]">{{ fmtStreamTime(item.ts) }}</span>

                      <button
                        v-if="item.kind === 'message' && (item.conversationId || item.mailId)"
                        type="button"
                        class="ml-auto text-[11.5px] font-medium text-[#0071E3] hover:underline"
                        @click="jumpToMessageItem(item)"
                      >
                        {{ item.channel === 'mail' ? '打开邮件 ›' : '打开会话 ›' }}
                      </button>
                      <Popconfirm
                        v-else-if="item.kind === 'followup' && item.followupId"
                        title="确定删除这条跟进记录吗？"
                        ok-text="删除"
                        cancel-text="取消"
                        @confirm="handleDeleteFollowUp(item.followupId)"
                      >
                        <button
                          type="button"
                          class="ml-auto text-[11px] text-[#86868B] hover:text-[#FF3B30]"
                        >
                          删除
                        </button>
                      </Popconfirm>
                    </div>

                    <div v-if="item.subject" class="mt-1 text-[12.5px] font-semibold text-[#1D1D1F]">
                      主题：{{ item.subject }}
                    </div>
                    <div class="mt-1 whitespace-pre-wrap break-words text-[13px] leading-relaxed text-[#2C2C2E]">
                      {{ item.content || '—' }}
                    </div>
                    <div
                      v-if="item.nextTime && dayjs(item.nextTime).isValid()"
                      class="mt-1.5 text-[11px] text-[#0071E3]"
                    >
                      📅 约定下次联系：{{ dayjs(item.nextTime).format('YYYY-MM-DD') }}
                    </div>
                  </div>
                </div>
              </div>
            </Spin>
          </div>

          <!-- ========== Tab 2: AI 深度背调 ========== -->
          <div v-else-if="subTab === 'research'" class="cd-card-box">
            <Spin :spinning="researchLoading">
              <div class="mb-3 flex items-center justify-between">
                <div>
                  <h4 class="m-0 text-[14px] font-bold text-[#1D1D1F]">
                    AI 买家背景与采购力情报
                  </h4>
                  <p class="m-0 text-[12px] text-[#86868B]">
                    自动抓取官网、社媒与公开贸易线索，判断终端/贸易商属性并给出报价策略
                  </p>
                </div>
                <div class="flex gap-2">
                  <button
                    v-if="latestResearch?.status === 'done'"
                    type="button"
                    class="cd-act"
                    @click="handleApplyResearch"
                  >
                    <IconifyIcon icon="lucide:file-down" class="size-3.5" />
                    <span>补全客户档案</span>
                  </button>
                  <button
                    type="button"
                    class="cd-act cd-act--primary"
                    :disabled="researchRunning"
                    @click="handleTriggerResearch"
                  >
                    <IconifyIcon icon="lucide:sparkles" class="size-3.5" />
                    <span>
                      {{ researchRunning ? '背调分析中…' : latestResearch ? '重新背调' : '发起 AI 背调' }}
                    </span>
                  </button>
                </div>
              </div>

              <div v-if="researchRunning" class="rounded-xl bg-[#F0F7FF] p-4 text-[13px] text-[#0071E3]">
                正在检索买家官网与全球公开商业情报并由大模型研判，预计 15~30 秒自动呈现结果…
              </div>

              <div
                v-else-if="latestResearch?.status === 'done' && latestResearch.report"
                class="flex flex-col gap-3"
              >
                <div class="rounded-xl bg-[#F8F8FA] p-3.5">
                  <div class="mb-1.5 flex flex-wrap items-center gap-2">
                    <span class="rounded-md bg-[#E8F2FF] px-2 py-0.5 text-[11.5px] font-semibold text-[#0071E3]">
                      {{
                        latestResearch.report.buyerKind?.kind === 'end_user'
                          ? '🏭 终端品牌 / 制造客户'
                          : latestResearch.report.buyerKind?.kind === 'trader'
                            ? '🚢 进口批发 / 贸易分销商'
                            : latestResearch.report.businessType || '海外采购商'
                      }}
                    </span>
                    <span
                      v-if="latestResearch.report.scale"
                      class="rounded-md bg-[#F2F2F7] px-2 py-0.5 text-[11.5px] text-[#3A3A3C]"
                    >
                      规模：{{ latestResearch.report.scale }}
                    </span>
                  </div>
                  <p class="m-0 text-[13px] leading-relaxed text-[#2C2C2E]">
                    {{ latestResearch.report.summary || '已完成企业背调分析。' }}
                  </p>
                </div>

                <div class="grid grid-cols-2 gap-2.5">
                  <div
                    v-if="latestResearch.report.quoteAdvice"
                    class="rounded-xl border border-[#E5E5EA] p-3"
                  >
                    <div class="mb-1 text-[11.5px] font-bold text-[#0071E3]">💡 报价切入建议</div>
                    <div class="text-[12.5px] text-[#3A3A3C]">
                      {{ latestResearch.report.quoteAdvice }}
                    </div>
                  </div>
                  <div
                    v-if="latestResearch.report.paymentAdvice"
                    class="rounded-xl border border-[#E5E5EA] p-3"
                  >
                    <div class="mb-1 text-[11.5px] font-bold text-[#248A3D]">🛡️ 付款风控建议</div>
                    <div class="text-[12.5px] text-[#3A3A3C]">
                      {{ latestResearch.report.paymentAdvice }}
                    </div>
                  </div>
                </div>

                <div
                  v-if="latestResearch.report.talkingPoints?.length"
                  class="rounded-xl border border-[#E5E5EA] p-3"
                >
                  <div class="mb-1.5 text-[12px] font-bold text-[#1D1D1F]">🎯 破冰与跟进话术要点</div>
                  <ul class="m-0 list-disc space-y-1 pl-4 text-[12.5px] text-[#3A3A3C]">
                    <li v-for="(tp, idx) in latestResearch.report.talkingPoints" :key="idx">
                      {{ tp }}
                    </li>
                  </ul>
                </div>
              </div>

              <Empty
                v-else
                :image="Empty.PRESENTED_IMAGE_SIMPLE"
                :description="
                  latestResearch?.status === 'failed'
                    ? `上次背调未完成：${latestResearch.reasonDetail || '请检查公司名或官网后重试'}`
                    : '尚未对该客户执行深度背调，点击右上角一键启动'
                "
              />
            </Spin>
          </div>

          <!-- ========== Tab 3: 档案与标签 ========== -->
          <div v-else-if="subTab === 'profile'" class="cd-card-box">
            <div class="grid grid-cols-2 gap-3">
              <div>
                <label class="cd-lbl">联系人 / 客户名称 *</label>
                <Input v-model:value="profileForm.name" placeholder="客户姓名或称呼" />
              </div>
              <div>
                <label class="cd-lbl">海外公司名称</label>
                <Input v-model:value="profileForm.companyName" placeholder="如 Acme Global Trading Ltd." />
              </div>
              <div>
                <label class="cd-lbl">国家 / 地区（自动联动时区时钟）</label>
                <Select
                  v-model:value="profileForm.countryCode"
                  show-search
                  allow-clear
                  option-filter-prop="label"
                  :options="COUNTRY_OPTIONS"
                  placeholder="选择买家国家/地区"
                  class="w-full"
                />
              </div>
              <div>
                <label class="cd-lbl">WhatsApp / 国际电话</label>
                <Input v-model:value="profileForm.whatsapp" placeholder="如 +1 415 555 0199" />
              </div>
              <div>
                <label class="cd-lbl">买家邮箱</label>
                <Input v-model:value="profileForm.email" placeholder="buyer@company.com" />
              </div>
              <div>
                <label class="cd-lbl">公司官网</label>
                <Input v-model:value="profileForm.website" placeholder="https://www.example.com" />
              </div>
              <div>
                <label class="cd-lbl">客户意向等级</label>
                <Select
                  v-model:value="profileForm.level"
                  allow-clear
                  :options="levelOptions"
                  placeholder="选择等级"
                  class="w-full"
                />
              </div>
              <div>
                <label class="cd-lbl">询盘渠道来源</label>
                <Select
                  v-model:value="profileForm.source"
                  allow-clear
                  :options="sourceOptions"
                  placeholder="选择来源渠道"
                  class="w-full"
                />
              </div>
              <div>
                <label class="cd-lbl">所属客户分组</label>
                <Select
                  v-model:value="profileForm.groupId"
                  allow-clear
                  :options="(groups || []).map((g) => ({ label: g.name, value: g.id }))"
                  placeholder="选择分组"
                  class="w-full"
                />
              </div>
              <div>
                <label class="cd-lbl">客户标签（可多选）</label>
                <Select
                  v-model:value="profileForm.tagIds"
                  mode="multiple"
                  allow-clear
                  :options="(tags || []).map((t) => ({ label: t.name, value: t.id }))"
                  placeholder="打上业务标签"
                  class="w-full"
                />
              </div>
              <div class="col-span-2">
                <label class="cd-lbl">客户长期背景备注</label>
                <Input.TextArea
                  v-model:value="profileForm.note"
                  :rows="3"
                  placeholder="记录客户采购偏好、账期习惯、决策人性格等长期档案…"
                />
              </div>
            </div>

            <div class="mt-4 flex items-center justify-between border-t border-[#F2F2F7] pt-3">
              <span class="text-[11.5px] text-[#86868B]">
                建档时间：{{ detail.createTime ? dayjs(detail.createTime).format('YYYY-MM-DD HH:mm') : '—' }}
                · 等级：{{ levelLabel(detail.level) }} · 来源：{{ sourceLabel(detail.source) }}
              </span>
              <button
                type="button"
                class="cd-act cd-act--primary"
                :disabled="profileSaving"
                @click="handleSaveProfile"
              >
                <IconifyIcon icon="lucide:check" class="size-3.5" />
                <span>保存档案修改</span>
              </button>
            </div>
          </div>

          <!-- ========== Tab 4: 成交订单 ========== -->
          <div v-else class="cd-card-box">
            <div class="mb-3 flex items-center justify-between">
              <div>
                <div class="text-[14px] font-bold text-[#1D1D1F]">
                  历史成交订单（{{ customerOrders.length }} 笔）
                </div>
                <div v-if="customerOrders.length" class="text-[12px] text-[#248A3D]">
                  累计签约金额：{{ formatDealMoney(totalOrderAmount, board.currency) }}
                </div>
              </div>
              <button
                type="button"
                class="cd-act cd-act--green"
                @click="emit('open-order-modal', detail)"
              >
                <IconifyIcon icon="lucide:plus" class="size-3.5" />
                <span>登记新订单 / PI</span>
              </button>
            </div>

            <Spin :spinning="ordersLoading">
              <Empty
                v-if="!ordersLoading && customerOrders.length === 0"
                :image="Empty.PRESENTED_IMAGE_SIMPLE"
                description="该客户暂未登记成交订单"
              />
              <div v-else class="flex flex-col gap-2">
                <div
                  v-for="ord in customerOrders"
                  :key="ord.id"
                  class="flex items-center justify-between rounded-xl border border-[#E5E5EA] p-3 transition hover:border-[#0071E3]/40"
                >
                  <div class="min-w-0">
                    <div class="truncate text-[13.5px] font-semibold text-[#1D1D1F]">
                      {{ ord.name || ord.no }}
                    </div>
                    <div class="mt-0.5 text-[11.5px] text-[#6E6E73]">
                      单号：{{ ord.no || '—' }} · 签单日：{{
                        ord.orderDate ? dayjs(ord.orderDate).format('YYYY-MM-DD') : '—'
                      }}
                    </div>
                    <div v-if="ord.remark" class="mt-0.5 truncate text-[11.5px] text-[#86868B]">
                      {{ ord.remark }}
                    </div>
                  </div>
                  <div class="shrink-0 text-right">
                    <div class="text-[15px] font-bold text-[#248A3D]">
                      {{
                        formatDealMoney(
                          ord.totalPrice,
                          ord.remark?.match(/\[币种:([A-Z]{3})\]/)?.[1] || board.currency,
                        )
                      }}
                    </div>
                  </div>
                </div>
              </div>
            </Spin>
          </div>
        </div>
      </div>
    </Spin>

    <!-- 转交客户弹窗 -->
    <Modal
      v-model:open="transferModalOpen"
      title="转交客户负责人"
      :width="420"
      centered
      :confirm-loading="transferring"
      ok-text="确认转交"
      cancel-text="取消"
      wrap-class-name="ap-modal-sheet"
      @ok="handleConfirmTransfer"
    >
      <div class="py-2">
        <p class="mb-2 text-[12.5px] text-[#6E6E73]">
          将「{{ detail?.name }}」移交给其他业务同事跟进：
        </p>
        <Select
          v-model:value="transferTargetId"
          show-search
          option-filter-prop="label"
          placeholder="选择新的负责人"
          :options="simpleUsers.map((u) => ({ label: u.nickname, value: u.id }))"
          class="w-full"
        />
      </div>
    </Modal>
  </Drawer>
</template>

<style scoped>
.cd-spin-root {
  height: 100%;
}

.cd-shell {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 0px);
  margin: -24px;
  background: #f5f5f7;
}

.cd-header {
  flex-shrink: 0;
  padding: 18px 22px 14px;
  background: rgb(255 255 255 / 90%);
  backdrop-filter: blur(22px);
  border-bottom: 0.5px solid rgb(60 60 67 / 12%);
}

.cd-avatar {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  font-size: 18px;
  font-weight: 700;
  color: #fff;
  border-radius: 13px;
  box-shadow: 0 4px 12px rgb(0 0 0 / 10%);
}

.cd-star-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  color: #c7c7cc;
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: 6px;
  transition: all 0.15s ease;
}

.cd-star-btn.on,
.cd-star-btn:hover {
  color: #ff9500;
}

.cd-health-pill {
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 600;
  border-radius: 999px;
}

.cd-health-pill--red {
  color: #d70015;
  background: #ffeceb;
}

.cd-health-pill--amber {
  color: #b25000;
  background: #fff4e0;
}

.cd-health-pill--blue {
  color: #0071e3;
  background: #e8f2ff;
}

.cd-health-pill--green {
  color: #248a3d;
  background: #eaf9ee;
}

.cd-health-pill--gray {
  color: #6e6e73;
  background: #f2f2f7;
}

.cd-clock-chip {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 1px 8px;
  font-size: 11.5px;
  color: #3a3a3c;
  background: #f2f2f7;
  border-radius: 999px;
}

.cd-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: #6e6e73;
  cursor: pointer;
  background: rgb(118 118 128 / 10%);
  border: none;
  border-radius: 8px;
  transition: all 0.15s ease;
}

.cd-icon-btn:hover:not(:disabled) {
  color: #1d1d1f;
  background: rgb(118 118 128 / 18%);
}

.cd-icon-btn:disabled {
  cursor: not-allowed;
  opacity: 0.35;
}

.cd-collision-alert {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 7px 11px;
  margin-top: 10px;
  font-size: 12px;
  font-weight: 500;
  color: #c93400;
  background: #fff2ed;
  border: 0.5px solid rgb(255 59 48 / 22%);
  border-radius: 9px;
}

.cd-act {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 29px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 600;
  color: #3a3a3c;
  cursor: pointer;
  background: #f2f2f7;
  border: none;
  border-radius: 8px;
  transition: all 0.15s ease;
}

.cd-act:hover {
  background: #e5e5ea;
}

.cd-act--primary {
  color: #fff;
  background: #0071e3;
}

.cd-act--primary:hover {
  background: #0077ed;
}

.cd-act--green {
  color: #1e7e34;
  background: #eaf9ee;
}

.cd-act--green:hover {
  background: #d6f5df;
}

.cd-act--purple {
  color: #5856d6;
  background: #ededfc;
}

.cd-act--purple:hover {
  background: #dfe0fa;
}

.cd-act--subtle {
  color: #86868b;
  background: transparent;
}

.cd-act--subtle:hover {
  color: #d70015;
  background: #ffeceb;
}

.cd-stage-track {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 5px;
  margin-top: 12px;
}

.cd-stage-step {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  justify-content: center;
  height: 30px;
  padding: 0 6px;
  font-size: 11.5px;
  font-weight: 600;
  color: #6e6e73;
  cursor: pointer;
  background: #f2f2f7;
  border: 1px solid transparent;
  border-radius: 8px;
  transition: all 0.16s ease;
}

.cd-stage-step:hover {
  color: #1d1d1f;
  background: #e5e5ea;
}

.cd-stage-step.passed {
  color: #0071e3;
  background: #eef5ff;
}

.cd-stage-idx {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 15px;
  height: 15px;
  font-size: 10px;
  background: rgb(0 0 0 / 8%);
  border-radius: 999px;
}

.cd-stage-step.current .cd-stage-idx {
  background: rgb(255 255 255 / 26%);
}

.cd-scroll {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  padding: 14px 22px 28px;
  overflow-y: auto;
}

.cd-board-card {
  padding: 12px 14px;
  background: #fff;
  border: 0.5px solid rgb(60 60 67 / 10%);
  border-radius: 14px;
  box-shadow: 0 1px 3px rgb(0 0 0 / 3%);
}

.cd-mini-link {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 3px 8px;
  font-size: 11px;
  font-weight: 600;
  color: #5856d6;
  cursor: pointer;
  background: #ededfc;
  border: none;
  border-radius: 6px;
}

.cd-mini-link:hover {
  background: #e0dff9;
}

.cd-mini-btn {
  padding: 3px 9px;
  font-size: 11.5px;
  font-weight: 600;
  color: #3a3a3c;
  cursor: pointer;
  background: #f2f2f7;
  border: none;
  border-radius: 6px;
}

.cd-mini-btn--primary {
  color: #fff;
  background: #0071e3;
}

.cd-tile {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  cursor: pointer;
  background: #f8f8fa;
  border: 0.5px solid transparent;
  border-radius: 10px;
  transition: all 0.15s ease;
}

.cd-tile:hover {
  background: #f0f6ff;
  border-color: rgb(0 113 227 / 22%);
}

.cd-tile-label {
  font-size: 11px;
  color: #86868b;
}

.cd-tile-val {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12.5px;
  color: #1d1d1f;
  white-space: nowrap;
}

.cd-subnav {
  display: inline-flex;
  gap: 4px;
  align-self: flex-start;
  padding: 3.5px;
  background: rgb(118 118 128 / 10%);
  border-radius: 11px;
}

.cd-subtab {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 30px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 600;
  color: #6e6e73;
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: 8px;
  transition: all 0.15s ease;
}

.cd-subtab.on {
  color: #1d1d1f;
  background: #fff;
  box-shadow: 0 1px 3px rgb(0 0 0 / 8%);
}

.cd-subcount {
  padding: 0 5px;
  font-size: 10.5px;
  color: #6e6e73;
  background: #f2f2f7;
  border-radius: 999px;
}

.cd-composer {
  padding: 12px;
  background: #fff;
  border: 0.5px solid rgb(60 60 67 / 10%);
  border-radius: 14px;
}

.cd-tpl-chip {
  padding: 2px 8px;
  font-size: 11px;
  color: #6e6e73;
  cursor: pointer;
  background: #f5f5f7;
  border: none;
  border-radius: 999px;
}

.cd-tpl-chip:hover {
  color: #0071e3;
  background: #e8f2ff;
}

.cd-date-pill {
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 500;
  color: #6e6e73;
  cursor: pointer;
  background: #f2f2f7;
  border: none;
  border-radius: 6px;
}

.cd-date-pill.on {
  font-weight: 600;
  color: #0071e3;
  background: #e8f2ff;
}

.cd-filter-chip {
  padding: 3px 10px;
  font-size: 11.5px;
  font-weight: 600;
  color: #6e6e73;
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: 999px;
}

.cd-filter-chip.on {
  color: #0071e3;
  background: #e8f2ff;
}

.cd-stream {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 9px;
  padding-left: 16px;
}

.cd-stream::before {
  position: absolute;
  top: 8px;
  bottom: 8px;
  left: 5px;
  width: 1.5px;
  content: '';
  background: rgb(60 60 67 / 10%);
}

.cd-stream-item {
  position: relative;
}

.cd-stream-dot {
  position: absolute;
  top: 13px;
  left: -14.5px;
  width: 8px;
  height: 8px;
  border: 2px solid #f5f5f7;
  border-radius: 999px;
}

.cd-stream-card {
  padding: 10px 13px;
  background: #fff;
  border: 0.5px solid rgb(60 60 67 / 9%);
  border-radius: 12px;
}

.cd-stream-badge {
  padding: 1px 7px;
  font-size: 10.5px;
  font-weight: 600;
  border-radius: 5px;
}

.cd-stream-badge--green {
  color: #1e7e34;
  background: #eaf9ee;
}

.cd-stream-badge--purple {
  color: #5856d6;
  background: #ededfc;
}

.cd-stream-badge--amber {
  color: #b25000;
  background: #fff4e0;
}

.cd-card-box {
  padding: 16px;
  background: #fff;
  border: 0.5px solid rgb(60 60 67 / 10%);
  border-radius: 14px;
}

.cd-lbl {
  display: block;
  margin-bottom: 4px;
  font-size: 11.5px;
  font-weight: 600;
  color: #6e6e73;
}
</style>
