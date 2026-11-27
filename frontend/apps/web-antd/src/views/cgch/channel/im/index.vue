<script lang="ts" setup>
/**
 * 消息工作台（WhatsApp、Facebook 主页私信、Instagram 私信）：左侧会话列表，右侧聊天记录与输入框。
 * 客户 24 小时内发过消息才能发自由文本，窗口关闭后只能发已审核的模板。
 */
import { fmtDateTime } from '#/utils/datetime';
import type { ChannelWaApi } from '#/api/channel/whatsapp';
import type { ChannelAiApi } from '#/api/channel/ai';
import { loadTranslatePrefs, translateTexts } from '#/api/channel/translate';
import {
  closeDraft,
  DRAFT_REASON,
  draftWhatsApp,
  getPendingDraft,
} from '#/api/channel/ai';

import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
} from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';
import { useUserStore } from '@vben/stores';

import {
  Button,
  Empty,
  Input,
  InputNumber,
  InputSearch,
  message,
  Modal,
  Dropdown,
  Menu,
  MenuItem,
  Popover,
  Segmented,
  Select,
  Spin,
  Switch,
  Tag,
  Textarea,
  Tooltip,
} from 'ant-design-vue';

import { getSimpleUserList } from '#/api/system/user';
import { getQuickReplies, markQuickReplyUsed } from '#/api/channel/quick-reply';
import type { QuickReplyApi } from '#/api/channel/quick-reply';
import { onChannelRealtime } from '#/utils/channel-realtime';
import { requestClient } from '#/api/request';
import {
  levelLabel,
  stageLabel,
  stageOptions,
} from '#/views/cgch/v3/crm/components/shared';
import { countryName } from '#/views/cgch/crm/shared/countries';
import { saveCustomerDealBoard } from '#/views/cgch/v3/crm/workbench/crm-actions';
import type { CurrencyCode } from '#/views/cgch/v3/crm/workbench/deal-meta';
import {
  countryFlag,
  CURRENCIES,
  formatDealMoney,
  getBuyerLocalClock,
  parseDealBoard,
} from '#/views/cgch/v3/crm/workbench/deal-meta';
import {
  getCustomerGroupList,
  getTagList,
  updateCustomerTags,
} from '#/api/crm/cgch';
import type { CgchCrmApi } from '#/api/crm/cgch';
import {
  addWaConversationNote,
  archiveWaConversation,
  CONVERSATION_STATUS,
  deleteWaConversationNote,
  getWaAccountList,
  getWaConversationEvents,
  PRIORITY_OPTIONS,
  setWaConversationPriority,
  setWaConversationStatus,
  getWaConversation,
  getWaConversationPage,
  getWaMedia,
  getWaMessages,
  getWaTemplates,
  sendWaMedia,
  sendWaMessage,
  remarkWaConversation,
  setWaGroupAi,
  setWaPrivateAi,
  setWaTeamPublic,
  createWaGroup,
  starWaConversation,
  templateParamCount,
  transferWaConversation,
  templatePreview,
} from '#/api/channel/whatsapp';

import MessageImage from './message-image.vue';

defineOptions({ name: 'CgchChannelIm' });

/** 嵌入「会话」页时：账号由左栏选择，隐藏本页的账号下拉 */
const props = defineProps<{
  accountId?: number;
  embedded?: boolean;
  level?: number;
  levels?: number[];
  group?: boolean;
  highPriority?: boolean;
  openId?: number;
  view?: 'mine' | 'team';
}>();
const emit = defineEmits<{ changed: [] }>();

const router = useRouter();
const route = useRoute();
const myUserId = useUserStore().userInfo?.id;
const POLL_MS = 10_000; // 实时推送没连上时的定时刷新间隔
const POLL_MS_LIVE = 60_000; // 实时推送连上后只做兜底刷新

const accounts = ref<ChannelWaApi.Account[]>([]);
/** 账号列表没回来之前不能判定“还没绑定账号”，否则首屏会误报（和邮件页 BIZ-01 同类） */
const accountsLoading = ref(true);
const accountId = ref<number | undefined>(props.accountId);
// Watch sources are evaluated synchronously during setup.
const text = ref('');
const keyword = ref('');
/** 最近一次实际提交给后端的关键词，用于空结果提示（避免边打字边改提示） */
const searchedKeyword = ref('');
/** 列表筛选：全部、未读、未回复（客户最后发言）、收藏、已归档 */
type ListFilter =
  | 'all'
  | 'archived'
  | 'starred'
  | 'team'
  | 'unread'
  | 'unreplied';
const listFilter = ref<ListFilter>('all');
const FILTER_OPTIONS = computed(() => [
  { label: '全部', value: 'all' },
  { label: '未读', value: 'unread' },
  { label: '未回复', value: 'unreplied' },
  { label: '收藏', value: 'starred' },
  { label: '归档', value: 'archived' },
  ...(props.embedded ? [] : [{ label: '团队', value: 'team' }]),
]);
/** 会话状态筛选（参考 Chatwoot）：默认只看进行中的 */
const statusFilter = ref<'' | ChannelWaApi.ConversationStatus>('open');
const STATUS_FILTER = [
  ...CONVERSATION_STATUS,
  { label: '全部状态', value: '' as const },
];
const teamView = computed(
  () => props.view === 'team' || listFilter.value === 'team',
);
/**
 * 列表为空时的提示。有搜索词时必须说“没有匹配结果”，
 * 否则会显示“没有进行中的会话”，让人误以为收件箱本身是空的。
 */
const emptyDescription = computed(() => {
  if (searchedKeyword.value)
    return `没有找到与“${searchedKeyword.value}”匹配的会话`;
  if (teamView.value)
    return '没有同事负责的会话（需要部门数据权限才能看到下属的会话）';
  return statusFilter.value === 'open' ? '没有进行中的会话' : '暂无会话';
});
const listTitle = computed(() => {
  if (props.embedded) return '会话';
  if (props.accountId) {
    const a = accounts.value.find((x) => x.id === props.accountId);
    if (a) return a.name || a.displayPhone || a.phoneNumberId;
  }
  return teamView.value ? '团队会话' : '我的会话';
});
const accountName = (id?: number) => {
  const a = accounts.value.find((x) => x.id === id);
  return a ? a.name || a.displayPhone || a.phoneNumberId : '';
};
const PLATFORM_DOT: Record<string, string> = {
  whatsapp: '#16A34A',
  messenger: '#0071E3',
  instagram: '#DB2777',
  web: '#4F46E5',
  youtube: '#DC2626',
  tiktok: '#1D1D1F',
};
const priorityMeta = (p?: number) =>
  PRIORITY_OPTIONS.find((o) => o.value === (p ?? 0));
const statusLabel = (s?: string) =>
  CONVERSATION_STATUS.find((o) => o.value === (s || 'open'))?.label;
const live = ref(false);
const conversations = ref<ChannelWaApi.Conversation[]>([]);
const listLoading = ref(false);

const current = ref<ChannelWaApi.Conversation>();
// 窄屏（小于 1024px）只显示一栏：没选会话时显示列表，选了会话显示聊天，聊天顶部有返回按钮
const narrowMq =
  typeof window === 'undefined'
    ? undefined
    : window.matchMedia('(max-width: 1023px)');
const narrow = ref(!!narrowMq?.matches);
const onNarrow = (e: MediaQueryListEvent) => (narrow.value = e.matches);
narrowMq?.addEventListener('change', onNarrow);
onBeforeUnmount(() => narrowMq?.removeEventListener('change', onNarrow));
function backToList() {
  current.value = undefined;
}
const messages = ref<ChannelWaApi.Message[]>([]);
const chatLoading = ref(false);
const chatBox = ref<HTMLElement>();
/** 内部备注与会话动态，按时间穿插到聊天记录里 */
const events = ref<ChannelWaApi.ConversationEvent[]>([]);
type TimelineItem =
  | {
      at: number;
      ev: ChannelWaApi.ConversationEvent;
      key: string;
      kind: 'event';
    }
  | { at: number; key: string; kind: 'msg'; m: ChannelWaApi.Message };
const toMs = (t?: number | string) =>
  typeof t === 'number' ? t : t ? new Date(t).getTime() : 0;
const timeline = computed<TimelineItem[]>(() =>
  [
    ...messages.value.map((m) => ({
      kind: 'msg' as const,
      m,
      at: toMs(m.messageTime),
      key: `m${m.id}`,
    })),
    // 只显示这批消息时间范围之后、或范围之内的记录，避免翻页前的旧备注堆在最上面
    ...events.value
      .filter(
        (e) =>
          !messages.value.length ||
          toMs(e.createTime) >= toMs(messages.value[0]!.messageTime),
      )
      .map((ev) => ({
        kind: 'event' as const,
        ev,
        at: toMs(ev.createTime),
        key: `e${ev.id}`,
      })),
  ].sort((a, b) => a.at - b.at),
);

/** 平台：WhatsApp 显示号码、可发模板；Facebook / Instagram 只能在客户来信后 24 小时内回复 */
const PLATFORM_META: Record<
  string,
  { avatar: string; color: string; label: string }
> = {
  whatsapp: { label: 'WhatsApp', color: 'green', avatar: '!bg-green-600' },
  messenger: { label: 'Facebook', color: 'blue', avatar: '!bg-blue-600' },
  instagram: { label: 'Instagram', color: 'magenta', avatar: '!bg-pink-600' },
  web: { label: '官网IM', color: 'geekblue', avatar: '!bg-indigo-600' },
  youtube: { label: 'YouTube 评论', color: 'red', avatar: '!bg-red-600' },
  tiktok: { label: 'TikTok 评论', color: 'default', avatar: '!bg-gray-900' },
};
const plat = (c?: { platform?: string }) =>
  PLATFORM_META[c?.platform || 'whatsapp'] ?? PLATFORM_META.whatsapp!;
const isWa = (c?: { platform?: string }) =>
  !c?.platform || c.platform === 'whatsapp';
const waAccounts = computed(() => accounts.value.filter((a) => isWa(a)));
/** 扫码接入的号码（WhatsApp 网页版协议）没有模板和 24 小时窗口限制，可以直接发普通消息 */
const isScanAccount = (id?: number) =>
  accounts.value.find((a) => a.id === id)?.authType === 'evolution';

const accountOptions = computed(() => [
  { label: '全部账号', value: undefined as any },
  ...accounts.value.map((a) => ({
    label:
      `${plat(a).label} · ${a.name || ''} ${a.displayPhone || a.phoneNumberId}`.trim(),
    value: a.id,
  })),
]);

let listRequest = 0;
async function loadConversations(silent = false) {
  const request = ++listRequest;
  if (!silent) listLoading.value = true;
  try {
    const res = await getWaConversationPage({
      pageNo: 1,
      pageSize: 100,
      accountId: accountId.value,
      keyword: keyword.value || undefined,
      unreadOnly: listFilter.value === 'unread' || undefined,
      unreplied: listFilter.value === 'unreplied' || undefined,
      starred: listFilter.value === 'starred' || undefined,
      archived: listFilter.value === 'archived' || undefined,
      team: teamView.value || undefined,
      level: props.level,
      levels: props.levels?.join(',') || undefined,
      group: props.group,
      highPriority: props.highPriority || undefined,
      status: statusFilter.value || undefined,
    });
    if (request === listRequest) {
      conversations.value = res.list;
      searchedKeyword.value = (keyword.value || '').trim();
    }
  } finally {
    if (request === listRequest) listLoading.value = false;
  }
}

async function scrollBottom() {
  await nextTick();
  if (chatBox.value) chatBox.value.scrollTop = chatBox.value.scrollHeight;
}

async function openConversation(c: ChannelWaApi.Conversation) {
  current.value = c;
  chatLoading.value = true;
  try {
    [messages.value, events.value] = await Promise.all([
      getWaMessages(c.id),
      getWaConversationEvents(c.id).catch(() => []),
    ]);
    c.unreadCount = 0;
    emit('changed');
    current.value = await getWaConversation(c.id);
    aiDraft.value = (await getPendingDraft('whatsapp', c.id)) ?? undefined;
    await scrollBottom();
  } finally {
    chatLoading.value = false;
  }
}

async function refreshCurrent() {
  if (!current.value) return;
  const [latest, evs] = await Promise.all([
    getWaMessages(current.value.id),
    getWaConversationEvents(current.value.id).catch(() => events.value),
  ]);
  const grew =
    latest.length !== messages.value.length ||
    latest.at(-1)?.status !== messages.value.at(-1)?.status ||
    evs.length !== events.value.length;
  messages.value = latest;
  events.value = evs;
  current.value = await getWaConversation(current.value.id);
  if (grew) await scrollBottom();
}

watch(
  [
    accountId,
    listFilter,
    statusFilter,
    () => props.level,
    () => props.levels?.join(','),
    () => props.group,
    () => props.highPriority,
    () => props.view,
  ],
  () => loadConversations(),
);
watch(
  () => props.accountId,
  (v) => {
    accountId.value = v;
  },
);

let timer: ReturnType<typeof setTimeout> | undefined;
let stopRealtime: (() => void) | undefined;
let refreshQueued = false;
/** 收到推送：合并 300 毫秒内的多条，刷新列表；是当前会话就刷新消息 */
function onRealtime(e: { channel: string; refId: number }) {
  if (e.channel !== 'whatsapp') return;
  const isCurrent = current.value?.id === e.refId;
  if (refreshQueued && !isCurrent) return;
  refreshQueued = true;
  setTimeout(() => {
    refreshQueued = false;
    loadConversations(true);
    if (current.value) refreshCurrent();
  }, 300);
}
function schedulePoll() {
  timer = setTimeout(
    () => {
      if (!document.hidden) {
        loadConversations(true);
        refreshCurrent();
      }
      schedulePoll();
    },
    live.value ? POLL_MS_LIVE : POLL_MS,
  );
}
onMounted(async () => {
  try {
    accounts.value = await getWaAccountList();
  } finally {
    accountsLoading.value = false;
  }
  await loadConversations(); // 没有自己的号码也可能有同事转交过来的会话
  // 从工作报告等页面跳转过来时，直接打开指定会话：/channel-msg/im?conversationId=123
  const cid = props.openId || Number(route.query.conversationId);
  if (cid) await openConversation(await getWaConversation(cid));
  stopRealtime = onChannelRealtime(onRealtime, (v) => (live.value = v));
  schedulePoll();
});
onBeforeUnmount(() => {
  if (timer) clearTimeout(timer);
  stopRealtime?.();
});

// ===== 客户阶段：会话关联了客户时，在顶部直接推进阶段 =====
const customerStage = ref<number>();
const customerTags = ref<number[]>([]);
const customerGroup = ref<number>();
/** 右侧客户信息栏用到的客户字段 */
interface PanelCustomer {
  id?: number;
  name?: string;
  level?: number;
  levels?: number[];
  group?: boolean;
  highPriority?: boolean;
  countryCode?: string;
  email?: string;
  mobile?: string;
  whatsapp?: string;
  remark?: string;
  ownerUserId?: number;
  contactNextTime?: number | string;
  contactLastTime?: number | string;
  contactLastContent?: string;
  ownerUserName?: string;
  companyName?: string;
}
const customer = ref<PanelCustomer>();
const customerBoard = computed(() => parseDealBoard(customer.value?.remark));
const customerClock = computed(() =>
  getBuyerLocalClock(customer.value?.countryCode),
);
const imBoardOpen = ref(false);
const imBoardSaving = ref(false);
const imBoardForm = reactive({
  currency: 'USD' as CurrencyCode,
  amount: undefined as number | undefined,
  productQty: '',
  quoteTerms: '',
  sampleInfo: '',
});
function openImBoardEdit(open: boolean) {
  imBoardOpen.value = open;
  if (open && customer.value) {
    const b = parseDealBoard(customer.value.remark);
    imBoardForm.currency = b.currency || 'USD';
    imBoardForm.amount = b.amount;
    imBoardForm.productQty = b.productQty || '';
    imBoardForm.quoteTerms = b.quoteTerms || '';
    imBoardForm.sampleInfo = b.sampleInfo || '';
  }
}
async function saveImBoard() {
  if (!customer.value?.id) return;
  imBoardSaving.value = true;
  try {
    await saveCustomerDealBoard(customer.value as any, {
      currency: imBoardForm.currency,
      amount: imBoardForm.amount ?? undefined,
      productQty: imBoardForm.productQty,
      quoteTerms: imBoardForm.quoteTerms,
      sampleInfo: imBoardForm.sampleInfo,
    });
    imBoardOpen.value = false;
    message.success('已同步至客户商务黑板');
  } finally {
    imBoardSaving.value = false;
  }
}
/** 客户阶段进度条：已走过的阶段标蓝，点击可直接改阶段 */
const stageSteps = computed(() =>
  stageOptions().map((o) => ({
    label: String(o.label),
    value: Number(o.value),
    done: (customerStage.value ?? 0) >= Number(o.value),
    on: customerStage.value === Number(o.value),
  })),
);
const tagOptions = ref<CgchCrmApi.TreeNode[]>([]);
const groupOptions = ref<CgchCrmApi.TreeNode[]>([]);
const tagName = (id: number) => tagOptions.value.find((t) => t.id === id)?.name;
watch(
  () => current.value?.customerId,
  async (id) => {
    customerStage.value = undefined;
    customerTags.value = [];
    customerGroup.value = undefined;
    customer.value = undefined;
    if (!id) return;
    try {
      const c = await requestClient.get<
        PanelCustomer & { groupId?: number; stage?: number; tagIds?: number[] }
      >(`/crm/customer/get?id=${id}`);
      if (current.value?.customerId !== id) return;
      customer.value = c ?? undefined;
      customerStage.value = c?.stage;
      customerTags.value = c?.tagIds ?? [];
      customerGroup.value = c?.groupId ?? undefined;
      if (!tagOptions.value.length) {
        [tagOptions.value, groupOptions.value] = await Promise.all([
          getTagList().catch(() => []),
          getCustomerGroupList().catch(() => []),
        ]);
      }
    } catch {
      // 没有查看这个客户的权限时不显示阶段
    }
  },
);
const tagEditOpen = ref(false);
const tagDraft = ref<number[]>([]);
const groupDraft = ref<number>();
function openTagEdit(v: boolean) {
  tagEditOpen.value = v;
  if (v) {
    tagDraft.value = [...customerTags.value];
    groupDraft.value = customerGroup.value;
  }
}
async function saveTags() {
  const id = current.value?.customerId;
  if (!id) return;
  await updateCustomerTags(id, tagDraft.value, groupDraft.value);
  customerTags.value = [...tagDraft.value];
  customerGroup.value = groupDraft.value;
  tagEditOpen.value = false;
  message.success('已保存客户标签');
}

// ===== 写跟进：记下这次沟通并约下次联系时间 =====
const followOpen = ref(false);
const followSaving = ref(false);
const followForm = ref<{ content: string; days: number }>({
  content: '',
  days: 3,
});
const FOLLOW_DAYS = [
  { label: '明天', value: 1 },
  { label: '3 天后', value: 3 },
  { label: '一周后', value: 7 },
  { label: '两周后', value: 14 },
];
function openFollow() {
  followForm.value = { content: '', days: 3 };
  followOpen.value = true;
}
async function saveFollow() {
  const id = current.value?.customerId;
  if (!id) return;
  if (!followForm.value.content.trim()) {
    message.warning('请填写跟进内容');
    return;
  }
  followSaving.value = true;
  try {
    const next = new Date();
    next.setDate(next.getDate() + followForm.value.days);
    next.setHours(10, 0, 0, 0);
    await requestClient.post('/crm/follow-up-record/create', {
      bizType: 2,
      bizId: id,
      type: isWa(current.value) ? 5 : 4,
      content: followForm.value.content.trim(),
      nextTime: next.getTime(),
      businessIds: [],
      contactIds: [],
      picUrls: [],
      fileUrls: [],
    });
    customer.value = {
      ...customer.value,
      contactLastContent: followForm.value.content.trim(),
      contactLastTime: Date.now(),
      contactNextTime: next.getTime(),
    };
    followOpen.value = false;
    message.success('已记录跟进');
  } finally {
    followSaving.value = false;
  }
}

async function changeStage(v: number) {
  const id = current.value?.customerId;
  if (!id || v === customerStage.value) return;
  await requestClient.put(`/crm/customer/update-stage?id=${id}&stage=${v}`);
  customerStage.value = v;
  message.success(`客户阶段已改为「${stageLabel(v)}」`);
}

// ===== 会话的 AI 自动回复开关 =====
const aiSaving = ref(false);
const aiReplyLabel = computed(() =>
  current.value?.group ? 'AI 自动回复这个群' : 'AI 自动回复此会话',
);
async function toggleConversationAi(on: boolean) {
  const c = current.value;
  if (!c || !isWa(c) || readOnly.value || aiSaving.value) return;
  aiSaving.value = true;
  try {
    const save = c.group ? setWaGroupAi : setWaPrivateAi;
    await save(c.id, on);
    if (current.value?.id === c.id) current.value.aiEnabled = on;
    const row = conversations.value.find((x) => x.id === c.id);
    if (row) row.aiEnabled = on;
    message.success(
      on
        ? '已开启：AI 会按「AI 接待设置」处理此会话'
        : '已关闭此会话的 AI 自动回复',
    );
  } finally {
    aiSaving.value = false;
  }
}

// ===== 公开给团队：负责人或管理员开关；同事查看公开的会话时只能看、写内部备注 =====
const readOnly = computed(() => current.value?.canHandle === false);
const teamPublicSaving = ref(false);
async function toggleTeamPublic(on: boolean) {
  const c = current.value;
  if (!c || readOnly.value) return;
  teamPublicSaving.value = true;
  try {
    await setWaTeamPublic(c.id, on);
    c.teamPublic = on;
    const row = conversations.value.find((x) => x.id === c.id);
    if (row) row.teamPublic = on;
    message.success(
      on
        ? '已公开给团队：本公司成员可在「团队会话」里查看（不能回复客户）'
        : '已取消公开，团队成员不再能看到',
    );
  } finally {
    teamPublicSaving.value = false;
  }
}

// ===== 会话整理：收藏、归档、备注名、转交 =====
async function toggleStar(c: ChannelWaApi.Conversation) {
  await starWaConversation(c.id, !c.starred);
  c.starred = !c.starred;
  if (current.value?.id === c.id) current.value.starred = c.starred;
  await loadConversations(true);
}
async function toggleArchive(c: ChannelWaApi.Conversation) {
  await archiveWaConversation(c.id, !c.archived);
  message.success(
    c.archived ? '已移出归档' : '已归档，客户再发消息时会自动回到列表',
  );
  if (current.value?.id === c.id) current.value = undefined;
  await loadConversations(true);
}
const remarkOpen = ref(false);
const remarkText = ref('');
function openRemark() {
  remarkText.value = current.value?.remarkName || '';
  remarkOpen.value = true;
}
async function saveRemark() {
  if (!current.value) return;
  await remarkWaConversation(current.value.id, remarkText.value.trim());
  current.value.remarkName = remarkText.value.trim() || undefined;
  remarkOpen.value = false;
  await loadConversations(true);
}
const groupOpen = ref(false);
const groupSending = ref(false);
const groupForm = reactive({
  accountId: undefined as number | undefined,
  subject: '',
  participants: '',
  message: '',
});
/** 只有扫码接入（Evolution）的号码能建群 */
const evolutionAccounts = computed(() =>
  waAccounts.value.filter((a) => a.authType === 'evolution'),
);
/** 改名的标题：群聊改群名，单聊改客户名 */
const remarkTitle = computed(() =>
  current.value?.group ? '修改群聊名称' : '修改客户名称',
);
function onNewMenu({ key }: { key: number | string }) {
  if (key === 'group') openGroup();
  else openNewChat();
}
function openGroup() {
  groupForm.accountId = evolutionAccounts.value[0]?.id;
  groupForm.subject = '';
  groupForm.participants = '';
  groupForm.message = '';
  groupOpen.value = true;
}
async function sendGroup() {
  if (!groupForm.accountId) {
    message.warning('请选择用于建群的号码');
    return;
  }
  if (!groupForm.subject.trim()) {
    message.warning('请填写群名称');
    return;
  }
  const participants = groupForm.participants
    .split(/[,，;；\s]+/)
    .map((x) => x.trim())
    .filter(Boolean);
  if (participants.length === 0) {
    message.warning('请至少填写一个成员号码');
    return;
  }
  groupSending.value = true;
  try {
    const id = await createWaGroup({
      accountId: groupForm.accountId,
      subject: groupForm.subject.trim(),
      participants,
      message: groupForm.message.trim() || undefined,
    });
    groupOpen.value = false;
    await loadConversations(true);
    const conv = conversations.value.find((x) => x.id === id);
    if (conv) current.value = conv;
    message.success('群已建好，可以开始说话了');
  } finally {
    groupSending.value = false;
  }
}
const transferOpen = ref(false);
const transferTo = ref<number>();
const users = ref<{ id?: number; nickname: string }[]>([]);
async function openTransfer() {
  transferTo.value = undefined;
  transferOpen.value = true;
  if (users.value.length === 0) users.value = await getSimpleUserList();
}
async function doTransfer() {
  if (!current.value || !transferTo.value) {
    message.warning('请选择同事');
    return;
  }
  await transferWaConversation(current.value.id, transferTo.value);
  message.success('已转交，对方会在会话列表里看到');
  transferOpen.value = false;
  current.value = undefined;
  await loadConversations(true);
}
// ===== 会话状态、优先级（参考 Chatwoot） =====
async function setStatus(st: ChannelWaApi.ConversationStatus) {
  if (!current.value) return;
  await setWaConversationStatus(current.value.id, st);
  message.success(
    st === 'resolved'
      ? '已标记为已解决，客户再来消息时会自动回到进行中'
      : st === 'pending'
        ? '已标记为待处理'
        : '已重新打开',
  );
  current.value.status = st;
  await Promise.all([refreshCurrent(), loadConversations(true)]);
  emit('changed');
}
async function setPriority(p: number) {
  if (!current.value) return;
  await setWaConversationPriority(current.value.id, p);
  current.value.priority = p;
  await Promise.all([refreshCurrent(), loadConversations(true)]);
}

function onMenu(key: string) {
  if (!current.value) return;
  if (key === 'star') toggleStar(current.value);
  else if (key === 'archive') toggleArchive(current.value);
  else if (key === 'remark') openRemark();
  else if (key === 'transfer') openTransfer();
  else if (key === 'team-public') toggleTeamPublic(!current.value.teamPublic);
}

// ===== 快捷回复：选中后插入输入框，{name} 换成客户称呼 =====
const quickOpen = ref(false);
const quickKeyword = ref('');
const quickList = ref<QuickReplyApi.Item[]>([]);
async function loadQuick() {
  quickList.value = await getQuickReplies(
    quickKeyword.value.trim() || undefined,
  );
}
watch(quickOpen, (v) => v && loadQuick());
function quickBody(q: QuickReplyApi.Item) {
  const name = current.value
    ? current.value.remarkName || current.value.profileName || ''
    : '';
  return q.content.replaceAll('{name}', name).trim();
}
function useQuick(q: QuickReplyApi.Item) {
  const body = quickBody(q);
  text.value = text.value ? `${text.value.trimEnd()}\n${body}` : body;
  quickOpen.value = false;
  if (q.id) markQuickReplyUsed(q.id);
}

/** 输入框里打「/关键词」直接搜快捷回复（参考 Chatwoot），回车或点击插入 */
const slashList = ref<QuickReplyApi.Item[]>([]);
const slashIndex = ref(0);
const slashOpen = computed(
  () => /^\/[^\n]*$/.test(text.value) && slashList.value.length > 0,
);
let slashTimer: ReturnType<typeof setTimeout> | undefined;
watch(
  () => text.value,
  (v) => {
    clearTimeout(slashTimer);
    if (!/^\/[^\n]*$/.test(v)) {
      slashList.value = [];
      return;
    }
    slashTimer = setTimeout(async () => {
      const kw = v.slice(1).trim();
      const list = await getQuickReplies(kw || undefined).catch(() => []);
      if (text.value === v) {
        slashList.value = list.slice(0, 8);
        slashIndex.value = 0;
      }
    }, 200);
  },
);
function useSlash(q?: QuickReplyApi.Item) {
  if (!q) return;
  text.value = quickBody(q);
  slashList.value = [];
  if (q.id) markQuickReplyUsed(q.id);
}

/** 同一客户的其他会话（其他号码、其他平台），在右侧栏快速切换 */
const otherConversations = ref<ChannelWaApi.Conversation[]>([]);
watch(
  () => [current.value?.id, current.value?.customerId] as const,
  async ([id, customerId]) => {
    otherConversations.value = [];
    if (!id || !customerId) return;
    const res = await getWaConversationPage({
      pageNo: 1,
      pageSize: 10,
      customerId,
    }).catch(() => ({ list: [] as ChannelWaApi.Conversation[] }));
    if (current.value?.id === id)
      otherConversations.value = res.list.filter((c) => c.id !== id);
  },
);

// ===== AI 起草 =====
const aiDraft = ref<ChannelAiApi.Draft>();
const aiLoading = ref(false);
const usedDraftId = ref<number>();
async function aiCompose() {
  if (!current.value) return;
  aiLoading.value = true;
  try {
    const d = await draftWhatsApp(current.value.id);
    if (d.reason === 'handoff') {
      aiDraft.value = d;
      message.warning('AI 判断这个会话需要人工处理');
      return;
    }
    text.value = d.content;
    usedDraftId.value = d.id;
    aiDraft.value = undefined;
  } finally {
    aiLoading.value = false;
  }
}
function useDraft() {
  if (!aiDraft.value) return;
  text.value = aiDraft.value.content;
  usedDraftId.value = aiDraft.value.id;
  aiDraft.value = undefined;
}
async function sendDraftNow() {
  if (!aiDraft.value?.content || !current.value || sending.value) return;
  const draft = aiDraft.value;
  sending.value = true;
  try {
    await sendWaMessage({
      conversationId: current.value.id,
      text: draft.content.trim(),
    });
    await closeDraft(draft.id, true);
    aiDraft.value = undefined;
    await refreshCurrent();
    loadConversations(true);
  } finally {
    sending.value = false;
  }
}
async function discardDraft() {
  if (!aiDraft.value) return;
  await closeDraft(aiDraft.value.id, false);
  aiDraft.value = undefined;
}

// ===== 翻译（设置 → 翻译设置） =====
const tPrefs = loadTranslatePrefs();
const translations = ref<Record<number, string>>({});
async function translateMessages() {
  if (!tPrefs.chat) return;
  const todo = messages.value
    .filter(
      (m) =>
        m.direction === 1 &&
        m.textBody?.trim() &&
        translations.value[m.id] === undefined,
    )
    .slice(-30);
  if (!todo.length) return;
  todo.forEach((m) => (translations.value[m.id] = ''));
  try {
    const out = await translateTexts(todo.map((m) => m.textBody!));
    todo.forEach((m, i) => {
      // 已经是中文的不重复显示
      translations.value[m.id] =
        out[i] && out[i]!.trim() !== m.textBody!.trim() ? out[i]! : '';
    });
  } catch {
    todo.forEach((m) => delete translations.value[m.id]);
  }
}
watch(messages, translateMessages);

/** 输入框双语模式：一边输入一边译成客户使用的语言 */
const inputTranslation = ref('');
const inputTranslating = ref(false);
let inputTimer: ReturnType<typeof setTimeout> | undefined;
const customerSample = computed(
  () =>
    [...messages.value]
      .reverse()
      .find((m) => m.direction === 1 && m.textBody?.trim())?.textBody ?? '',
);
function onInputChange() {
  if (!tPrefs.input) return;
  clearTimeout(inputTimer);
  const v = text.value.trim();
  if (!v) {
    inputTranslation.value = '';
    return;
  }
  inputTimer = setTimeout(async () => {
    inputTranslating.value = true;
    try {
      const [t] = await translateTexts(
        [v],
        customerSample.value
          ? { sample: customerSample.value }
          : { target: 'English' },
      );
      if (text.value.trim() === v) inputTranslation.value = t ?? '';
    } finally {
      inputTranslating.value = false;
    }
  }, 700);
}
function useTranslation() {
  if (!inputTranslation.value) return;
  text.value = inputTranslation.value;
  inputTranslation.value = '';
}

// ===== 发送文本 =====
const sending = ref(false);
async function sendText() {
  if (!current.value || !text.value.trim()) return;
  sending.value = true;
  try {
    await sendWaMessage({
      conversationId: current.value.id,
      text: text.value.trim(),
    });
    if (usedDraftId.value) await closeDraft(usedDraftId.value, true);
    usedDraftId.value = undefined;
    text.value = '';
    inputTranslation.value = '';
    await refreshCurrent();
    loadConversations(true);
  } finally {
    sending.value = false;
  }
}
/** 输入区：回复客户 / 内部备注（只给团队看） */
const composeMode = ref<'note' | 'reply'>('reply');
// 查看同事公开给团队的会话：不能回复客户，只能写内部备注
watch(
  readOnly,
  (v) => {
    composeMode.value = v ? 'note' : 'reply';
  },
  { immediate: true },
);
const noteText = ref('');
const noteSaving = ref(false);
async function sendNote() {
  if (!current.value || !noteText.value.trim()) return;
  noteSaving.value = true;
  try {
    const ev = await addWaConversationNote(
      current.value.id,
      noteText.value.trim(),
    );
    events.value = [...events.value, ev];
    noteText.value = '';
    await scrollBottom();
  } finally {
    noteSaving.value = false;
  }
}
async function removeNote(ev: ChannelWaApi.ConversationEvent) {
  await deleteWaConversationNote(ev.id);
  events.value = events.value.filter((e) => e.id !== ev.id);
}
function onNoteKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault();
    sendNote();
  }
}
function onKeydown(e: KeyboardEvent) {
  if (slashOpen.value && !e.isComposing) {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault();
      const n = slashList.value.length;
      slashIndex.value =
        (slashIndex.value + (e.key === 'ArrowDown' ? 1 : n - 1)) % n;
      return;
    }
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      useSlash(slashList.value[slashIndex.value]);
      return;
    }
    if (e.key === 'Escape') {
      slashList.value = [];
      return;
    }
  }
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault();
    sendText();
  }
}

// ===== 媒体：查看与发送 =====
const mediaLoading = ref<number>();
async function loadMedia(m: ChannelWaApi.Message) {
  mediaLoading.value = m.id;
  try {
    const res = await getWaMedia(m.id);
    m.fileUrl = res.fileUrl;
    m.fileSize = res.fileSize;
    m.mimeType = res.mimeType;
    if (m.type !== 'image' && m.type !== 'sticker')
      window.open(res.fileUrl, '_blank');
  } finally {
    mediaLoading.value = undefined;
  }
}
const isImage = (m: ChannelWaApi.Message) =>
  (m.type === 'image' || m.type === 'sticker') && !!m.fileUrl;

const fileSending = ref(false);
function pickAndSend() {
  if (!current.value) return;
  const input = document.createElement('input');
  input.type = 'file';
  input.accept =
    'image/jpeg,image/png,video/mp4,audio/*,application/pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.zip';
  input.onchange = async () => {
    const file = input.files?.[0];
    if (!file || !current.value) return;
    const limit =
      file.type === 'image/jpeg' || file.type === 'image/png' ? 5 : 16;
    if (file.size > limit * 1024 * 1024) {
      message.warning(
        `文件过大：${limit === 5 ? '图片' : '该类文件'}不能超过 ${limit}MB`,
      );
      return;
    }
    fileSending.value = true;
    try {
      await sendWaMedia(current.value.id, file, text.value.trim() || undefined);
      text.value = '';
      await refreshCurrent();
      loadConversations(true);
    } finally {
      fileSending.value = false;
    }
  };
  input.click();
}

// ===== 新会话：扫码号码直接输入号码和消息；官方 API 号码只能先发已审核的模板 =====
const newOpen = ref(false);
const newSending = ref(false);
const newForm = ref({
  accountId: undefined as number | undefined,
  to: '',
  text: '',
});
const newAccountOptions = computed(() =>
  waAccounts.value.map((a) => ({
    value: a.id,
    label: `${a.displayPhone || a.name || a.phoneNumberId}${a.authType === 'evolution' ? '（扫码）' : '（官方 API）'}`,
  })),
);
function openNewChat() {
  const selected = accounts.value.find((a) => a.id === accountId.value);
  newForm.value = {
    accountId:
      selected && isWa(selected) ? selected.id : waAccounts.value[0]?.id,
    to: '',
    text: '',
  };
  if (!isScanAccount(newForm.value.accountId)) {
    openTemplate(true);
    return;
  }
  newOpen.value = true;
}
function onNewAccountChange(id: number) {
  if (!isScanAccount(id)) {
    newOpen.value = false;
    const keepTo = newForm.value.to;
    openTemplate(true, id).then(() => (newTo.value = keepTo));
  }
}
async function sendNewChat() {
  const f = newForm.value;
  const to = f.to.replaceAll(/[^\d]/g, '');
  if (to.length < 8 || to.length > 15) {
    message.warning('请填写正确的号码（含国家码，如 +8613800001111）');
    return;
  }
  if (!f.text.trim()) {
    message.warning('请填写消息内容');
    return;
  }
  newSending.value = true;
  try {
    const sent = await sendWaMessage({
      accountId: f.accountId,
      to,
      text: f.text.trim(),
    });
    message.success('消息已发送');
    newOpen.value = false;
    await loadConversations(true);
    const conv = conversations.value.find((c) => c.id === sent.conversationId);
    if (conv) await openConversation(conv);
  } finally {
    newSending.value = false;
  }
}

// ===== 发送模板（窗口外，或主动联系新号码） =====
const tplOpen = ref(false);
const tplLoading = ref(false);
const templates = ref<ChannelWaApi.Template[]>([]);
const tplKey = ref<string>();
const tplParams = ref<string[]>([]);
const newTo = ref(''); // 主动联系新号码时填写
const newAccountId = ref<number>();
const approved = computed(() =>
  templates.value.filter((t) => t.status === 'APPROVED'),
);
const chosen = computed(() =>
  approved.value.find((t) => `${t.name}|${t.language}` === tplKey.value),
);
watch(chosen, (t) => {
  tplParams.value = Array.from({ length: templateParamCount(t) }, () => '');
});

async function openTemplate(newChat = false, forAccount?: number) {
  const selected = accounts.value.find((a) => a.id === accountId.value);
  const officialFirst = waAccounts.value.find(
    (a) => a.authType !== 'evolution',
  )?.id;
  const acc = newChat
    ? (forAccount ??
      (selected && isWa(selected) && !isScanAccount(selected.id)
        ? selected.id
        : officialFirst))
    : current.value?.accountId;
  if (!acc) return;
  newAccountId.value = acc;
  newTo.value = newChat ? '' : current.value!.waId;
  tplKey.value = undefined;
  tplOpen.value = true;
  tplLoading.value = true;
  try {
    templates.value = await getWaTemplates(acc);
  } finally {
    tplLoading.value = false;
  }
}

async function sendTemplate() {
  const t = chosen.value;
  if (!t) {
    message.warning('请选择模板');
    return;
  }
  if (tplParams.value.some((p) => !p.trim())) {
    message.warning('请填写全部模板变量');
    return;
  }
  const isNew = !current.value || newTo.value !== current.value.waId;
  const sent = await sendWaMessage({
    ...(isNew
      ? { accountId: newAccountId.value, to: newTo.value }
      : { conversationId: current.value!.id }),
    templateName: t.name,
    templateLanguage: t.language,
    templateParams: tplParams.value,
    templatePreview: templatePreview(t, tplParams.value),
  });
  message.success('模板消息已发送');
  tplOpen.value = false;
  await loadConversations(true);
  const conv = conversations.value.find((c) => c.id === sent.conversationId);
  if (conv) await openConversation(conv);
}

// ===== 展示 =====
const STATUS_ICON: Record<string, { label: string; text: string }> = {
  sent: { label: '已发送', text: '✓' },
  delivered: { label: '已送达', text: '✓✓' },
  read: { label: '已读', text: '✓✓' },
  failed: { label: '发送失败', text: '!' },
};
const MEDIA_LABEL: Record<string, string> = {
  image: '图片',
  video: '视频',
  audio: '语音',
  document: '文件',
  sticker: '表情',
};
const initials = (c: ChannelWaApi.Conversation) => {
  const raw = (c.remarkName || c.customerName || c.profileName || c.waId || '?').trim();
  const ch = raw.replace(/^[+\s]+/, '').slice(0, 1);
  return (ch || '?').toUpperCase();
};
const AVATAR_GRADIENTS = [
  'linear-gradient(180deg, #FFAB2E 0%, #FF9500 100%)', // 琥珀橙
  'linear-gradient(180deg, #FF6259 0%, #FF3B30 100%)', // 珊瑚红
  'linear-gradient(180deg, #3395FF 0%, #007AFF 100%)', // 苹果蓝
  'linear-gradient(180deg, #4CD964 0%, #28CD41 100%)', // 翡翠绿
  'linear-gradient(180deg, #C678F0 0%, #AF52DE 100%)', // 鸢尾紫
  'linear-gradient(180deg, #7472E8 0%, #5856D6 100%)', // 靛青蓝
  'linear-gradient(180deg, #2AD4CB 0%, #00B5AD 100%)', // 湖水青
  'linear-gradient(180deg, #FF5C85 0%, #FF2D55 100%)', // 玫瑰粉
];
const avatarBg = (c: ChannelWaApi.Conversation) => {
  const seed = String(c.profileName || c.waId || c.id || '');
  let h = 0;
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) >>> 0;
  return AVATAR_GRADIENTS[h % AVATAR_GRADIENTS.length];
};
const smartTime = (t?: number | string) => {
  if (!t) return '';
  const ms = toMs(t);
  if (!ms) return '';
  const d = new Date(ms);
  const now = new Date();
  if (
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  ) {
    return fmtDateTime(t, 'HH:mm');
  }
  const yest = new Date(now);
  yest.setDate(now.getDate() - 1);
  if (
    d.getFullYear() === yest.getFullYear() &&
    d.getMonth() === yest.getMonth() &&
    d.getDate() === yest.getDate()
  ) {
    return '昨天';
  }
  return fmtDateTime(t, 'MM-DD');
};
/** 隐藏了手机号的 WhatsApp 联系人：waId 是「隐藏编号@lid」，不是号码 */
const hiddenNumber = (c?: { waId?: string }) => !!c?.waId?.endsWith('@lid');
/** 隐藏号码联系人很多时用隐藏编号末 4 位区分 */
const waNumber = (c: ChannelWaApi.Conversation) =>
  c.group
    ? 'WhatsApp 群聊'
    : hiddenNumber(c)
      ? `隐藏号码联系人 ·${c.waId.replace('@lid', '').slice(-4)}`
      : `+${c.waId}`;
const contact = (c: ChannelWaApi.Conversation) =>
  c.profileName ||
  (isWa(c)
    ? waNumber(c)
    : c.platform === 'web'
      ? `官网访客 ${c.waId.slice(-4).toUpperCase()}`
      : `${plat(c).label} 用户`);
const hm = (t?: number | string) => fmtDateTime(t, 'HH:mm');
const md = (t?: number | string) => fmtDateTime(t, 'MM-DD');
</script>

<template>
  <Page
    auto-content-height
    :class="{ 'apple-im': embedded }"
    :content-class="embedded ? '!p-0' : undefined"
  >
    <div
      v-if="
        !accountsLoading &&
        accounts.length === 0 &&
        conversations.length === 0 &&
        listFilter === 'all'
      "
      class="flex h-full items-center justify-center rounded bg-white"
    >
      <Empty description="还没有绑定 WhatsApp、Facebook 或 Instagram 账号">
        <Button type="primary" @click="router.push('/v3/channel')"
          >去绑定账号</Button
        >
      </Empty>
    </div>

    <div v-else class="im-panes flex h-full gap-3">
      <!-- 会话列表 -->
      <div
        class="im-list flex shrink-0 flex-col overflow-hidden rounded-xl border border-solid border-[#E5E5EA] bg-white"
        :class="narrow ? (current ? 'hidden' : 'w-full') : 'w-80'"
      >
        <div class="im-list-header flex flex-col gap-2.5 px-3.5 pt-3.5 pb-2">
          <div class="flex items-center gap-2">
            <h2
              class="m-0 min-w-0 flex-1 truncate text-[19px] font-bold tracking-tight text-[#1D1D1F]"
            >
              {{ listTitle }}
            </h2>
            <Dropdown v-if="waAccounts.length > 0" trigger="click">
              <button
                type="button"
                class="im-new-btn"
                aria-label="新建会话或群聊"
                title="新建会话或群聊"
              >
                <IconifyIcon icon="lucide:plus" class="size-4" />
              </button>
              <template #overlay>
                <Menu @click="onNewMenu">
                  <MenuItem key="private">
                    <span class="inline-flex items-center gap-2">
                      <IconifyIcon icon="lucide:message-square-plus" class="size-3.5 text-[#007AFF]" />
                      <span>发起私聊</span>
                    </span>
                  </MenuItem>
                  <MenuItem key="group">
                    <span class="inline-flex items-center gap-2">
                      <IconifyIcon icon="lucide:users" class="size-3.5 text-[#28CD41]" />
                      <span>新建群聊</span>
                    </span>
                  </MenuItem>
                </Menu>
              </template>
            </Dropdown>
          </div>

          <!-- iOS 风格无边框圆角搜索栏 -->
          <div class="im-search-box">
            <IconifyIcon icon="lucide:search" class="im-search-box__icon" />
            <input
              v-model="keyword"
              type="search"
              class="im-search-box__input"
              placeholder="搜索客户、号码、备注名"
              aria-label="搜索会话"
              @keydown.enter="loadConversations()"
              @search="loadConversations()"
            />
            <button
              v-if="keyword"
              type="button"
              class="im-search-box__clear"
              aria-label="清空搜索"
              @click="
                keyword = '';
                loadConversations();
              "
            >
              <IconifyIcon icon="lucide:x" class="size-3" />
            </button>
          </div>

          <Select
            v-if="!embedded"
            v-model:value="accountId"
            size="small"
            :options="accountOptions"
          />
          <slot name="list-controls"></slot>

          <div class="im-status-pills" role="group" aria-label="会话状态">
            <button
              v-for="s in STATUS_FILTER"
              :key="s.value || 'all'"
              type="button"
              class="im-status-pill"
              :class="{ on: statusFilter === s.value }"
              @click="statusFilter = s.value"
            >
              {{ s.value ? s.label : '全部' }}
            </button>
          </div>

          <div
            class="im-list-tabs flex"
            role="tablist"
            aria-label="会话筛选"
          >
            <button
              v-for="f in FILTER_OPTIONS"
              :key="f.value"
              type="button"
              role="tab"
              :aria-selected="listFilter === f.value"
              class="im-list-tab"
              :class="{ 'is-active': listFilter === f.value }"
              @click="listFilter = f.value as any"
            >
              {{ f.label }}
            </button>
          </div>
        </div>

        <div class="flex-1 overflow-auto pb-2">
          <Spin :spinning="listLoading">
            <div
              v-if="conversations.length === 0"
              class="flex flex-col items-center justify-center gap-2 px-6 py-14 text-center"
            >
              <div
                class="grid size-11 place-items-center rounded-2xl bg-black/[0.04] text-[#8E8E93]"
              >
                <IconifyIcon icon="lucide:inbox" class="size-5" />
              </div>
              <div class="text-[13px] text-[#86868B]">
                {{ emptyDescription }}
              </div>
            </div>
            <div
              v-for="c in conversations"
              :key="c.id"
              :aria-label="`打开会话 ${c.remarkName || c.customerName || contact(c)}`"
              class="im-contact flex cursor-pointer items-start gap-3"
              :class="{ 'im-selected': current?.id === c.id }"
              @click="openConversation(c)"
            >
              <img
                v-if="c.avatarUrl"
                :src="c.avatarUrl"
                alt=""
                class="im-avatar im-avatar--md"
              />
              <span
                v-else
                class="im-avatar im-avatar--md"
                :style="{ background: avatarBg(c) }"
                aria-hidden="true"
                >{{ initials(c) }}</span
              >
              <div class="flex min-w-0 flex-1 flex-col gap-0.5">
                <!-- 第一行：联系人名称 + 右侧智能时间 -->
                <div class="flex items-center justify-between gap-2">
                  <span
                    class="min-w-0 flex-1 truncate text-[14px] font-semibold leading-5 text-[#1D1D1F]"
                  >
                    <IconifyIcon
                      v-if="c.starred"
                      icon="lucide:star"
                      class="mr-1 inline size-3.5 align-[-2px] text-amber-500"
                    />
                    <IconifyIcon
                      v-if="c.group"
                      icon="lucide:users"
                      class="mr-1 inline size-3.5 align-[-2px] text-[#6E6E73]"
                    />{{ c.remarkName || c.customerName || contact(c) }}
                  </span>
                  <span
                    class="shrink-0 text-[11.5px] tabular-nums"
                    :class="
                      c.unreadCount
                        ? 'font-semibold text-[#007AFF]'
                        : 'text-[#8E8E93]'
                    "
                    >{{ smartTime(c.lastMessageTime) }}</span
                  >
                </div>

                <!-- 第二行：最新消息摘要 + 蓝色未读角标 -->
                <div class="flex items-center justify-between gap-2">
                  <span class="min-w-0 flex-1 truncate text-[12.5px] leading-[18px] text-[#6E6E73]">
                    <span v-if="c.teamPublic" class="mr-1 font-medium text-[#FF9500]"
                      >[公开]</span
                    ><span
                      v-if="teamView && c.handlerName"
                      class="mr-1 font-medium text-[#007AFF]"
                      >[{{ c.handlerName }}]</span
                    >{{ c.lastMessagePreview || '暂无消息内容' }}
                  </span>
                  <span
                    v-if="c.unreadCount"
                    class="flex h-[18px] min-w-[18px] shrink-0 items-center justify-center rounded-full bg-[#007AFF] px-1.5 text-[11px] font-semibold text-white tabular-nums"
                  >
                    {{ c.unreadCount > 99 ? '99+' : c.unreadCount }}
                  </span>
                </div>

                <!-- 第三行：渠道品牌色标签 + AI 接待状态 + 等级/优先级药丸 -->
                <div class="mt-0.5 flex flex-wrap items-center gap-1.5">
                  <span
                    class="text-[11px] font-semibold leading-4"
                    :style="{
                      color: PLATFORM_DOT[c.platform || 'whatsapp'] || '#007AFF',
                    }"
                  >
                    {{
                      props.accountId
                        ? c.group
                          ? '群聊'
                          : plat(c).label
                        : accountName(c.accountId) || plat(c).label
                    }}
                  </span>
                  <span
                    v-if="c.aiEnabled"
                    class="rounded-full bg-[#F3EEFF] px-1.5 text-[10.5px] font-medium leading-[17px] text-[#7C3AED]"
                    >AI 接待中</span
                  >
                  <span
                    v-if="(c.priority ?? 0) > 0"
                    class="rounded-full px-1.5 text-[10.5px] font-medium leading-[17px]"
                    :style="{
                      color: priorityMeta(c.priority)?.color,
                      background: `${priorityMeta(c.priority)?.color}14`,
                    }"
                  >
                    {{ priorityMeta(c.priority)?.label }}
                  </span>
                  <span
                    v-if="c.customerLevel"
                    class="rounded-full bg-[#FFF4E0] px-1.5 text-[10.5px] font-medium leading-[17px] text-[#B25E00]"
                    >{{ levelLabel(c.customerLevel) }}</span
                  >
                  <span
                    v-if="!statusFilter && c.status && c.status !== 'open'"
                    class="rounded-full bg-black/[0.05] px-1.5 text-[10.5px] leading-[17px] text-[#6E6E73]"
                  >
                    {{ statusLabel(c.status) }}
                  </span>
                </div>
              </div>
            </div>
          </Spin>
        </div>
      </div>

      <!-- 聊天区 -->
      <div
        class="im-thread min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-solid border-[#E5E5EA] bg-white"
        :class="narrow && !current ? 'hidden' : 'flex'"
      >
        <div
          v-if="!current"
          class="flex h-full flex-col items-center justify-center gap-3 px-6 text-center"
        >
          <div class="im-empty-glyph">
            <IconifyIcon icon="lucide:messages-square" class="size-7" />
          </div>
          <div class="text-[16px] font-semibold text-[#1D1D1F]">
            选择一个会话开始沟通
          </div>
          <div class="max-w-xs text-[13px] leading-relaxed text-[#86868B]">
            左侧选择客户消息即可查看上下文，支持 AI 自动接待、多语言互译与知识库辅助起草。
          </div>
        </div>
        <template v-else>
          <div
            class="im-toolbar flex items-center justify-between gap-3 px-5 py-3"
            :class="narrow ? 'flex-wrap !px-3' : ''"
          >
            <div class="flex min-w-0 flex-1 items-center gap-3">
              <Button
                v-if="narrow"
                type="text"
                shape="circle"
                size="small"
                class="shrink-0"
                aria-label="返回会话列表"
                @click="backToList"
              >
                <IconifyIcon icon="lucide:chevron-left" class="size-5" />
              </Button>
              <img
                v-if="current.avatarUrl"
                :src="current.avatarUrl"
                alt=""
                class="im-avatar im-avatar--md"
              />
              <span
                v-else
                class="im-avatar im-avatar--md"
                :style="{ background: avatarBg(current) }"
                aria-hidden="true"
                >{{ initials(current) }}</span
              >
              <div class="min-w-0 flex-1">
                <div class="flex items-center gap-1.5">
                  <span
                    class="truncate text-[16px] font-bold tracking-tight text-[#1D1D1F]"
                  >
                    {{ current.remarkName || contact(current) }}
                  </span>
                  <button
                    type="button"
                    class="im-icon-ghost"
                    :aria-label="remarkTitle"
                    :title="remarkTitle"
                    @click="openRemark"
                  >
                    <IconifyIcon icon="lucide:pencil" class="size-3.5" />
                  </button>
                  <button
                    v-if="current.customerName"
                    type="button"
                    class="im-crm-chip im-crm-chip--blue"
                    @click="
                      router.push({
                        path: '/v3/crm',
                        query: { customerId: String(current.customerId) },
                      })
                    "
                  >
                    客户 · {{ current.customerName }}
                  </button>
                  <button
                    v-else-if="current.clueName"
                    type="button"
                    class="im-crm-chip im-crm-chip--teal"
                    @click="
                      router.push({ path: '/v3/crm', query: { triage: '1' } })
                    "
                  >
                    待甄别 · {{ current.clueName }}
                  </button>
                </div>
                <div
                  class="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-[12px] text-[#86868B]"
                >
                  <span>{{ plat(current).label }}</span>
                  <template v-if="isWa(current) && hiddenNumber(current)">
                    <span>·</span>
                    <Tooltip
                      title="对方在 WhatsApp 上隐藏了手机号，可以照常收发消息；对方之后带上号码时会自动合并到号码会话"
                    >
                      <span class="cursor-help">隐藏号码</span>
                    </Tooltip>
                  </template>
                  <template v-else-if="current.group">
                    <span>·</span>
                    <span>群聊</span>
                  </template>
                  <template v-else-if="isWa(current)">
                    <span>·</span>
                    <span class="tabular-nums">+{{ current.waId }}</span>
                  </template>
                  <span v-if="current.remarkName" class="truncate text-[#A1A1A6]"
                    >（{{ contact(current) }}）</span
                  >
                  <span>·</span>
                  <Tooltip
                    v-if="current.windowOpen"
                    :title="`回复窗口在 ${fmtDateTime(current.windowExpireTime)} 关闭`"
                  >
                    <span class="inline-flex items-center gap-1 text-[#248A3D]">
                      <span class="size-1.5 rounded-full bg-[#34C759]"></span>
                      24h 窗口内
                    </span>
                  </Tooltip>
                  <span
                    v-else
                    class="inline-flex items-center gap-1 text-[#C93400]"
                  >
                    <span class="size-1.5 rounded-full bg-[#FF9500]"></span>
                    {{ isWa(current) ? '仅可发模板' : '等待客户来信' }}
                  </span>
                  <!-- 窄屏没有右侧客户栏时，阶段在这里快捷改 -->
                  <span
                    v-if="current.customerId && customerStage !== undefined"
                    class="contents xl:hidden"
                  >
                    <Select
                      :value="customerStage"
                      size="small"
                      class="w-24"
                      :options="
                        stageOptions().map((o) => ({
                          label: o.label,
                          value: o.value,
                        }))
                      "
                      @change="(v: any) => changeStage(Number(v))"
                    />
                  </span>
                </div>
              </div>
            </div>

            <!-- 顶栏右侧：AI 托管开关 + 转给同事 + 收藏 + 更多 + 标为已解决 -->
            <div class="flex shrink-0 items-center gap-2">
              <Tooltip
                :title="
                  live
                    ? '实时推送已连接，新消息会立即出现'
                    : '实时推送未连接，每 10 秒自动刷新'
                "
              >
                <span
                  class="inline-block size-2 rounded-full"
                  :class="live ? 'bg-[#34C759]' : 'bg-[#D1D1D6]'"
                ></span>
              </Tooltip>

              <label
                v-if="isWa(current)"
                class="im-ai-toggle"
                :title="aiReplyLabel"
              >
                <Switch
                  size="small"
                  :checked="!!current.aiEnabled"
                  :disabled="readOnly"
                  :loading="aiSaving"
                  :aria-label="aiReplyLabel"
                  @change="(v) => toggleConversationAi(!!v)"
                />
                <span>AI 托管</span>
              </label>

              <Tag v-if="readOnly" color="orange" class="m-0 !rounded-full"
                >同事公开 · 只读</Tag
              >

              <button
                v-if="!readOnly"
                type="button"
                class="im-pill-action im-pill-action--soft"
                @click="openTransfer"
              >
                {{ current.handlerName ? `转交 · ${current.handlerName}` : '转给同事' }}
              </button>

              <Tooltip :title="current.starred ? '取消收藏' : '收藏会话'">
                <button
                  type="button"
                  class="im-circle-action"
                  :class="{ 'is-starred': current.starred }"
                  :aria-label="current.starred ? '取消收藏' : '收藏会话'"
                  @click="toggleStar(current)"
                >
                  <IconifyIcon icon="lucide:star" class="size-4" />
                </button>
              </Tooltip>

              <Dropdown v-if="!readOnly" :trigger="['click']">
                <button
                  type="button"
                  class="im-circle-action"
                  aria-label="更多操作"
                >
                  <IconifyIcon icon="lucide:ellipsis" class="size-4" />
                </button>
                <template #overlay>
                  <Menu @click="(e: any) => onMenu(String(e.key))">
                    <MenuItem v-if="current.status === 'pending'" key="status-open" @click="setStatus('open')">
                      回到进行中
                    </MenuItem>
                    <MenuItem v-else-if="current.status !== 'resolved'" key="status-pending" @click="setStatus('pending')">
                      标为待处理
                    </MenuItem>
                    <MenuItem key="archive">{{
                      current.archived ? '移出归档' : '归档会话'
                    }}</MenuItem>
                    <MenuItem key="remark">设置备注名</MenuItem>
                    <MenuItem key="transfer">转交给同事</MenuItem>
                    <MenuItem key="team-public">{{
                      current.teamPublic ? '取消公开给团队' : '公开给团队'
                    }}</MenuItem>
                  </Menu>
                </template>
              </Dropdown>

              <template v-if="readOnly"></template>
              <button
                v-else-if="current.status === 'resolved'"
                type="button"
                class="im-pill-action im-pill-action--soft"
                @click="setStatus('open')"
              >
                重新打开
              </button>
              <button
                v-else
                type="button"
                class="im-pill-action im-pill-action--primary"
                @click="setStatus('resolved')"
              >
                标为已解决
              </button>
            </div>
          </div>

          <div
            ref="chatBox"
            class="im-messages flex-1 overflow-auto bg-[#F5F5F8] px-6 py-5"
          >
            <Spin :spinning="chatLoading">
              <template v-for="it in timeline" :key="it.key">
                <div
                  v-if="it.kind === 'event' && it.ev.kind === 'activity'"
                  class="mb-3 flex justify-center"
                >
                  <span
                    class="rounded-full bg-[#EEF0F3] px-3 py-1 text-xs text-[#6E6E73]"
                    >{{ it.ev.content }} · {{ hm(it.ev.createTime) }}</span
                  >
                </div>
                <div
                  v-else-if="it.kind === 'event'"
                  class="mb-3 flex justify-end"
                >
                  <div
                    class="cg-bubble max-w-[70%] rounded-[18px] bg-[rgba(255,204,0,0.18)] px-3.5 py-2.5 text-sm leading-[22px] text-[#5C3B00]"
                  >
                    <div
                      class="mb-1 flex items-center gap-2 text-[11px] font-semibold text-[#A05A00]"
                    >
                      <IconifyIcon icon="lucide:lock" class="size-3" />内部备注
                      · 客户看不到
                    </div>
                    <div class="whitespace-pre-wrap break-words">
                      {{ it.ev.content }}
                    </div>
                    <div
                      class="mt-1 flex items-center justify-end gap-2 text-[11px] text-[#A05A00]/80"
                    >
                      <span
                        >{{ it.ev.userName }} · {{ md(it.ev.createTime) }}
                        {{ hm(it.ev.createTime) }}</span
                      >
                      <a
                        v-if="String(it.ev.userId) === String(myUserId)"
                        class="!text-[#A05A00] underline"
                        @click="removeNote(it.ev)"
                        >删除</a
                      >
                    </div>
                  </div>
                </div>
                <div
                  v-else-if="it.kind === 'msg'"
                  class="mb-4 flex flex-col"
                  :class="
                    it.m.direction === 2 ? 'items-end' : 'items-start'
                  "
                >
                  <div
                    class="cg-bubble im-bubble max-w-[72%] px-4 py-2.5 text-[14px] leading-[22px]"
                    :class="
                      it.m.direction === 2
                        ? it.m.aiGenerated
                          ? 'im-bubble--ai [&_a]:text-white [&_a]:underline'
                          : 'im-bubble--out [&_a]:text-white [&_a]:underline'
                        : 'im-bubble--in'
                    "
                  >
                    <!-- 群聊：标出是群里哪位成员发的 -->
                    <div
                      v-if="
                        current?.group &&
                        it.m.direction !== 2 &&
                        (it.m.senderName || it.m.senderWaId)
                      "
                      class="mb-0.5 text-xs font-semibold text-[#0071E3]"
                    >
                      {{
                        it.m.senderName ||
                        (it.m.senderWaId?.endsWith('@lid')
                          ? '群成员'
                          : `+${it.m.senderWaId}`)
                      }}
                    </div>
                    <MessageImage
                      v-if="isWa(current) && it.m.type === 'image'"
                      :message="it.m"
                    />
                    <template v-else-if="MEDIA_LABEL[it.m.type]">
                      <a
                        v-if="isImage(it.m)"
                        :href="it.m.fileUrl"
                        target="_blank"
                        rel="noopener"
                      >
                        <img
                          :src="it.m.fileUrl"
                          class="mb-1 max-h-60 max-w-full rounded"
                          alt=""
                        />
                      </a>
                      <div
                        v-else
                        class="mb-1 flex items-center gap-2 text-xs"
                        :class="
                          it.m.direction === 2
                            ? 'text-white/85'
                            : 'text-[#6E6E73]'
                        "
                      >
                        <span
                          >[{{ MEDIA_LABEL[it.m.type]
                          }}{{
                            it.m.fileName ? `：${it.m.fileName}` : ''
                          }}]</span
                        >
                        <a
                          v-if="it.m.fileUrl"
                          :href="it.m.fileUrl"
                          target="_blank"
                          rel="noopener"
                          >下载</a
                        >
                        <Button
                          v-else-if="it.m.mediaId || it.m.direction === 1"
                          size="small"
                          type="link"
                          class="!h-auto !p-0 !text-xs"
                          :loading="mediaLoading === it.m.id"
                          @click="loadMedia(it.m)"
                        >
                          {{
                            it.m.type === 'image' || it.m.type === 'sticker'
                              ? '查看'
                              : '下载'
                          }}
                        </Button>
                      </div>
                    </template>
                    <div
                      v-if="it.m.type === 'template'"
                      class="mb-1 text-xs"
                      :class="
                        it.m.direction === 2
                          ? 'text-white/85'
                          : 'text-[#6E6E73]'
                      "
                    >
                      [模板 {{ it.m.templateName }}]
                    </div>
                    <div class="whitespace-pre-wrap break-words">
                      {{ it.m.textBody }}
                    </div>
                    <div
                      v-if="translations[it.m.id]"
                      class="mt-1.5 whitespace-pre-wrap break-words border-0 border-t border-dashed pt-1.5 text-[13px]"
                      :class="
                        it.m.direction === 2
                          ? 'border-white/40 text-white/90'
                          : 'border-[#D2D2D7] text-[#6E6E73]'
                      "
                    >
                      {{ translations[it.m.id] }}
                    </div>
                  </div>
                  <div
                    class="im-bubble-meta mt-1.5 flex items-center gap-1.5 px-1 text-[11px] text-[#8E8E93]"
                    :class="
                      it.m.direction === 2 ? 'justify-end' : 'justify-start'
                    "
                  >
                    <span v-if="it.m.aiGenerated" class="font-medium text-[#7C5CE6]"
                      >AI 自动回复 ·</span
                    >
                    <span
                      >{{ md(it.m.messageTime) }}
                      {{ hm(it.m.messageTime) }}</span
                    >
                    <Tooltip
                      v-if="it.m.direction === 2 && it.m.status"
                      :title="
                        it.m.errorMessage || STATUS_ICON[it.m.status]?.label
                      "
                    >
                      <span
                        :class="
                          it.m.status === 'failed'
                            ? 'rounded bg-red-50 px-1 font-bold text-[#D70015]'
                            : it.m.status === 'read'
                              ? 'font-semibold text-[#007AFF]'
                              : ''
                        "
                      >
                        {{ STATUS_ICON[it.m.status]?.text }}
                      </span>
                    </Tooltip>
                  </div>
                </div>
              </template>
            </Spin>
          </div>

          <!-- AI 草稿独立悬浮卡片（参考图：紫边白卡置于输入框上方） -->
          <div v-if="aiDraft" class="im-draft-wrap">
            <div class="im-draft p-3.5 text-sm">
              <div
                class="mb-1.5 flex items-center gap-1.5 text-xs font-semibold text-[#8B5CF6]"
              >
                <IconifyIcon icon="lucide:sparkles" class="size-3.5" />
                <span
                  >AI 草稿 ·
                  {{
                    DRAFT_REASON[aiDraft.reason || ''] ?? aiDraft.reason
                  }}</span
                >
                <span
                  v-if="aiDraft.reasonDetail"
                  class="font-normal text-[#86868B]"
                  >（{{ aiDraft.reasonDetail }}）</span
                >
              </div>
              <div
                v-if="aiDraft.content"
                class="line-clamp-4 whitespace-pre-wrap text-[13.5px] leading-[22px] text-[#1D1D1F]"
              >
                {{ aiDraft.content }}
              </div>
              <div class="mt-3 flex flex-wrap items-center gap-2">
                <button
                  v-if="aiDraft.content && current.windowOpen"
                  type="button"
                  class="im-pill-action im-pill-action--primary"
                  :disabled="sending"
                  @click="sendDraftNow"
                >
                  发送
                </button>
                <button
                  v-if="aiDraft.content && current.windowOpen"
                  type="button"
                  class="im-pill-action im-pill-action--soft"
                  @click="useDraft"
                >
                  放进输入框修改
                </button>
                <button
                  type="button"
                  class="im-pill-action im-pill-action--soft"
                  @click="discardDraft"
                >
                  不用
                </button>
              </div>
            </div>
          </div>

          <div class="im-composer">
            <template v-if="composeMode === 'note'">
              <div
                class="mb-1.5 flex items-center justify-between text-xs text-[#A05A00]"
              >
                <span class="font-medium"
                  >内部备注 · 只有团队能看到，不会发给客户</span
                >
                <button
                  v-if="!readOnly"
                  type="button"
                  class="cursor-pointer border-0 bg-transparent text-xs text-[#007AFF]"
                  @click="composeMode = 'reply'"
                >
                  切回回复
                </button>
              </div>
              <Textarea
                v-model:value="noteText"
                :auto-size="{ minRows: 2, maxRows: 5 }"
                :maxlength="1990"
                class="!rounded-lg !bg-[#FFF9E6] !px-2.5 !py-2"
                placeholder="写给同事看的备注，Enter 保存，Shift+Enter 换行"
                @keydown="onNoteKeydown"
              />
              <div class="mt-2 flex justify-end">
                <Button
                  type="primary"
                  size="small"
                  shape="round"
                  class="!border-[#FF9F0A] !bg-[#FF9F0A]"
                  :loading="noteSaving"
                  :disabled="!noteText.trim()"
                  @click="sendNote"
                  >添加备注</Button
                >
              </div>
            </template>
            <template v-else-if="current.windowOpen">
              <div
                v-if="slashOpen"
                class="mb-2 overflow-hidden rounded-xl border border-solid border-black/[0.08] bg-white shadow-md"
                role="listbox"
                aria-label="快捷回复"
              >
                <div
                  class="border-0 border-b border-solid border-[#F2F2F7] px-3 py-1.5 text-xs text-[#86868B]"
                >
                  快捷回复 · ↑↓ 选择，回车插入，Esc 关闭
                </div>
                <div
                  v-for="(q, i) in slashList"
                  :key="q.id"
                  role="option"
                  :aria-selected="i === slashIndex"
                  class="cursor-pointer px-3 py-1.5"
                  :class="
                    i === slashIndex ? 'bg-[#E8F2FC]' : 'hover:bg-[#F5F5F7]'
                  "
                  @mouseenter="slashIndex = i"
                  @mousedown.prevent="useSlash(q)"
                >
                  <div class="text-[13px] font-medium text-[#1D1D1F]">
                    {{ q.title }}
                  </div>
                  <div class="truncate text-xs text-[#86868B]">
                    {{ q.content }}
                  </div>
                </div>
              </div>
              <Textarea
                v-model:value="text"
                :auto-size="{ minRows: 1, maxRows: 5 }"
                :maxlength="4096"
                placeholder="输入消息，/ 调出快捷回复"
                @keydown="onKeydown"
                @change="onInputChange"
              />
              <div
                v-if="tPrefs.input && (inputTranslation || inputTranslating)"
                class="mt-2 rounded-lg bg-[#F5F5F7] px-3 py-2 text-sm"
              >
                <div
                  class="mb-1 flex items-center justify-between text-xs text-[#86868B]"
                >
                  <span>{{
                    inputTranslating ? '正在翻译…' : '译文（客户的语言）'
                  }}</span>
                  <a v-if="inputTranslation" @click="useTranslation"
                    >用译文替换</a
                  >
                </div>
                <div class="whitespace-pre-wrap text-[#1D1D1F]">
                  {{ inputTranslation }}
                </div>
              </div>
              <div class="im-send-tools mt-2 flex items-center justify-between">
                <div class="flex items-center gap-1">
                  <Tooltip title="发送文件">
                    <button
                      type="button"
                      class="im-tool-btn"
                      :disabled="fileSending"
                      aria-label="发送文件"
                      @click="pickAndSend"
                    >
                      <IconifyIcon icon="lucide:paperclip" class="size-4" />
                    </button>
                  </Tooltip>
                  <Popover
                    v-model:open="quickOpen"
                    trigger="click"
                    placement="topLeft"
                  >
                    <template #content>
                      <div class="w-80">
                        <InputSearch
                          v-model:value="quickKeyword"
                          allow-clear
                          size="small"
                          placeholder="搜索快捷回复"
                          @search="loadQuick"
                        />
                        <div class="mt-2 max-h-72 overflow-auto">
                          <Empty
                            v-if="quickList.length === 0"
                            :image="Empty.PRESENTED_IMAGE_SIMPLE"
                            description="还没有快捷回复，可在「设置 → 快捷回复」添加"
                          />
                          <div
                            v-for="q in quickList"
                            :key="q.id"
                            class="cursor-pointer rounded px-2 py-1.5 hover:bg-gray-50"
                            @click="useQuick(q)"
                          >
                            <div class="text-sm font-medium">
                              {{ q.title
                              }}<Tag
                                v-if="q.scope === 'personal'"
                                class="ml-1"
                                color="default"
                                >个人</Tag
                              >
                            </div>
                            <div class="truncate text-xs text-gray-500">
                              {{ q.content }}
                            </div>
                          </div>
                        </div>
                      </div>
                    </template>
                    <Tooltip title="快捷回复">
                      <button
                        type="button"
                        class="im-tool-btn"
                        aria-label="快捷回复"
                      >
                        <IconifyIcon icon="lucide:zap" class="size-4" />
                      </button>
                    </Tooltip>
                  </Popover>
                  <Tooltip
                    v-if="isWa(current) && !isScanAccount(current.accountId)"
                    title="发送模板"
                  >
                    <button
                      type="button"
                      class="im-tool-btn"
                      aria-label="发送模板"
                      @click="openTemplate()"
                    >
                      <IconifyIcon icon="lucide:file-text" class="size-4" />
                    </button>
                  </Tooltip>
                  <Tooltip title="AI 起草">
                    <button
                      type="button"
                      class="im-tool-btn im-tool-btn--ai"
                      :disabled="aiLoading"
                      aria-label="AI 起草"
                      @click="aiCompose"
                    >
                      <IconifyIcon icon="lucide:sparkles" class="size-4" />
                    </button>
                  </Tooltip>
                  <Tooltip title="写内部备注（客户看不到）">
                    <button
                      type="button"
                      class="im-tool-btn"
                      aria-label="内部备注"
                      @click="composeMode = 'note'"
                    >
                      <IconifyIcon icon="lucide:lock" class="size-3.5" />
                    </button>
                  </Tooltip>
                </div>
                <button
                  type="button"
                  class="im-send-circle"
                  :disabled="!text.trim() || sending"
                  aria-label="发送消息"
                  @click="sendText"
                >
                  <IconifyIcon icon="lucide:arrow-up" class="size-4" />
                </button>
              </div>
            </template>
            <div
              v-else
              class="flex items-center justify-between gap-3 rounded-xl bg-[#FFF8EB] px-3.5 py-2.5 text-[13px] text-[#9A5B00]"
            >
              <div class="flex items-center gap-2">
                <IconifyIcon icon="lucide:clock" class="size-4 shrink-0 text-[#FF9500]" />
                <span>{{
                  isWa(current)
                    ? '客户超过 24 小时未回复，当前仅可发送已审核的模板消息。'
                    : `客户超过 24 小时未回复，等对方再次来信后即可直接回复。`
                }}</span>
              </div>
              <button
                v-if="isWa(current)"
                type="button"
                class="im-pill-action im-pill-action--primary shrink-0"
                @click="openTemplate()"
              >
                发送模板
              </button>
            </div>
          </div>
        </template>
      </div>

      <!-- 客户信息栏（设计稿「会话工作台」右侧）：选中会话时显示，窄屏隐藏 -->
      <aside
        v-if="current"
        class="im-inspector hidden w-80 shrink-0 flex-col gap-4 overflow-auto bg-white p-5 xl:flex"
      >
        <!-- 联系人头像名片（居中彩色单字大头像 + 名称 + 公司/账号副标题 + 标签药丸） -->
        <div class="flex flex-col items-center gap-2 pb-1 text-center">
          <img
            v-if="current.avatarUrl"
            :src="current.avatarUrl"
            alt=""
            class="im-avatar im-avatar--lg"
          />
          <span
            v-else
            class="im-avatar im-avatar--lg"
            :style="{ background: avatarBg(current) }"
            aria-hidden="true"
            >{{ initials(current) }}</span
          >
          <div class="w-full min-w-0">
            <div class="truncate text-[16px] font-bold tracking-tight text-[#1D1D1F]">
              {{ customer?.name || current.remarkName || contact(current) }}
            </div>
            <div class="mt-0.5 truncate text-[12px] text-[#86868B]">
              {{
                customer?.companyName ||
                `${plat(current).label} · ${
                  isWa(current)
                    ? waNumber(current)
                    : accountName(current.accountId)
                }`
              }}
            </div>
          </div>

          <!-- 客户标签行（参考图：阶段蓝药丸 + 等级暖黄药丸 + 客户标签 + 添加标签） -->
          <div
            v-if="current.customerId && customerStage !== undefined"
            class="mt-1 flex flex-wrap justify-center gap-1.5"
          >
            <span
              class="rounded-full bg-[#E8F2FF] px-2.5 py-0.5 text-[11px] font-semibold text-[#007AFF]"
            >
              {{ stageLabel(customerStage) }}
            </span>
            <span
              v-if="customer?.level"
              class="rounded-full bg-[#FFF4E0] px-2.5 py-0.5 text-[11px] font-semibold text-[#B25E00]"
            >
              {{ levelLabel(customer.level) }}
            </span>
            <span
              v-for="t in customerTags"
              :key="t"
              class="rounded-full bg-[#F2F2F7] px-2.5 py-0.5 text-[11px] font-medium text-[#424245]"
            >
              {{ tagName(t) || t }}
            </span>
            <Popover
              trigger="click"
              placement="bottomRight"
              :open="tagEditOpen"
              @open-change="openTagEdit"
            >
              <template #content>
                <div class="flex w-72 flex-col gap-2">
                  <div class="text-xs text-gray-500">客户标签</div>
                  <Select
                    v-model:value="tagDraft"
                    mode="multiple"
                    size="small"
                    placeholder="选择标签"
                    :options="
                      tagOptions.map((t) => ({ label: t.name, value: t.id }))
                    "
                    option-filter-prop="label"
                  />
                  <div class="text-xs text-gray-500">客户分组</div>
                  <Select
                    v-model:value="groupDraft"
                    size="small"
                    allow-clear
                    placeholder="不分组"
                    :options="
                      groupOptions.map((g) => ({
                        label: g.name,
                        value: g.id,
                      }))
                    "
                  />
                  <div class="text-right">
                    <Button size="small" type="primary" shape="round" @click="saveTags"
                      >保存</Button
                    >
                  </div>
                </div>
              </template>
              <button
                type="button"
                class="cursor-pointer rounded-full border border-dashed border-[#D2D2D7] bg-white px-2 py-0.5 text-[11px] text-[#6E6E73] hover:border-[#007AFF] hover:text-[#007AFF]"
              >
                + 标签
              </button>
            </Popover>
          </div>
          <div
            v-else-if="!current.group"
            class="mt-0.5 flex items-center gap-1.5 text-[12px] text-[#86868B]"
          >
            <span>{{
              current.clueName
                ? `待甄别来信：${current.clueName}`
                : '尚未关联 CRM 客户'
            }}</span>
            <a
              v-if="current.clueId"
              @click="router.push({ path: '/v3/crm', query: { triage: '1' } })"
              >去立项 ›</a
            >
          </div>
        </div>

        <!-- 客户档案 iOS Inset Grouped 信息卡（关联客户时置顶展示核心业务信息） -->
        <template v-if="current.customerId && customerStage !== undefined">
          <div class="im-info-group">
            <div v-if="customer?.countryCode" class="im-info-row">
              <span class="im-info-label">国家与时区</span>
              <span class="truncate text-[12.5px] font-medium text-[#1D1D1F]">
                {{ countryFlag(customer.countryCode) }}
                {{ countryName(customer.countryCode) || customer.countryCode }}
                <template v-if="customerClock">
                  · {{ customerClock.icon }} {{ customerClock.time }}
                </template>
              </span>
            </div>
            <div class="im-info-row">
              <span class="im-info-label">客户负责人</span>
              <span class="truncate text-[13px] font-medium text-[#1D1D1F]">
                {{ customer?.ownerUserName || '-' }}
              </span>
            </div>
            <div v-if="customer?.mobile" class="im-info-row">
              <span class="im-info-label">联系电话</span>
              <span class="truncate text-[13px] font-medium tabular-nums text-[#1D1D1F]">
                {{ customer.mobile }}
              </span>
            </div>
            <div v-if="customer?.email" class="im-info-row">
              <span class="im-info-label">联系邮箱</span>
              <span class="truncate text-[12.5px] font-medium text-[#1D1D1F]">
                {{ customer.email }}
              </span>
            </div>
            <div class="im-info-row">
              <span class="im-info-label">下次跟进</span>
              <span
                class="text-[13px] font-medium tabular-nums"
                :class="
                  customer?.contactNextTime ? 'text-[#C93400]' : 'text-[#86868B]'
                "
              >
                {{ fmtDateTime(customer?.contactNextTime, 'MM-DD HH:mm', '未预约') }}
              </span>
            </div>
          </div>

          <!-- 客户阶段步进条 -->
          <div class="flex flex-col gap-2">
            <div class="flex items-center justify-between text-[12px]">
              <span class="font-semibold text-[#6E6E73]">跟进阶段</span>
              <span class="font-medium text-[#007AFF]">{{
                stageLabel(customerStage)
              }}</span>
            </div>
            <div class="flex gap-1">
              <button
                v-for="s in stageSteps"
                :key="s.value"
                type="button"
                class="flex flex-1 cursor-pointer flex-col items-center gap-1 border-0 bg-transparent p-0"
                :title="`切换阶段为「${s.label}」`"
                @click="changeStage(s.value)"
              >
                <span
                  class="block h-1.5 w-full rounded-full transition-colors"
                  :class="s.done ? 'bg-[#007AFF]' : 'bg-[#E5E5EA]'"
                ></span>
                <span
                  class="text-[10.5px]"
                  :class="
                    s.on ? 'font-semibold text-[#007AFF]' : 'text-[#8E8E93]'
                  "
                  >{{ s.label }}</span
                >
              </button>
            </div>
          </div>

          <!-- 📌 商务黑板（边聊天边记报价、单值与寄样单号，自动与 CRM 管道同步） -->
          <div class="rounded-2xl border border-[#E5E5EA] bg-white p-3">
            <div class="mb-1.5 flex items-center justify-between">
              <span class="inline-flex items-center gap-1 text-[12px] font-semibold text-[#0071E3]">
                <IconifyIcon icon="lucide:layout-grid" class="size-3.5" />
                商务黑板
              </span>
              <Popover
                :open="imBoardOpen"
                trigger="click"
                placement="left"
                @open-change="openImBoardEdit"
              >
                <template #content>
                  <div class="flex w-64 flex-col gap-2 p-1">
                    <div class="text-[12px] font-bold text-[#1D1D1F]">
                      更新商务黑板（同步至 CRM）
                    </div>
                    <div class="flex gap-1.5">
                      <Select
                        v-model:value="imBoardForm.currency"
                        size="small"
                        class="w-24"
                        :options="CURRENCIES.map((c) => ({ label: c.code, value: c.code }))"
                      />
                      <InputNumber
                        v-model:value="imBoardForm.amount"
                        size="small"
                        :min="0"
                        placeholder="预估单值"
                        class="flex-1"
                      />
                    </div>
                    <Input
                      v-model:value="imBoardForm.productQty"
                      size="small"
                      placeholder="意向产品与数量，如 2000 pcs"
                    />
                    <Input
                      v-model:value="imBoardForm.quoteTerms"
                      size="small"
                      placeholder="报价条款，如 FOB $18.5"
                    />
                    <Input
                      v-model:value="imBoardForm.sampleInfo"
                      size="small"
                      placeholder="样品进展或快递单号"
                    />
                    <div class="mt-1 flex justify-end gap-1.5">
                      <Button size="small" @click="imBoardOpen = false">取消</Button>
                      <Button
                        size="small"
                        type="primary"
                        :loading="imBoardSaving"
                        @click="saveImBoard"
                      >
                        保存
                      </Button>
                    </div>
                  </div>
                </template>
                <a class="text-[11.5px] text-[#0071E3]">编辑</a>
              </Popover>
            </div>
            <div
              v-if="
                customerBoard.amount ||
                customerBoard.productQty ||
                customerBoard.quoteTerms ||
                customerBoard.sampleInfo
              "
              class="flex flex-col gap-1 text-[12px] text-[#3A3A3C]"
            >
              <div v-if="customerBoard.amount" class="font-bold text-[#0071E3]">
                预估单值：{{ formatDealMoney(customerBoard.amount, customerBoard.currency) }}
              </div>
              <div v-if="customerBoard.productQty" class="truncate">
                产品数量：{{ customerBoard.productQty }}
              </div>
              <div v-if="customerBoard.quoteTerms" class="truncate">
                最新报价：{{ customerBoard.quoteTerms }}
              </div>
              <div v-if="customerBoard.sampleInfo" class="truncate">
                寄样进展：{{ customerBoard.sampleInfo }}
              </div>
            </div>
            <div v-else class="text-[11.5px] text-[#8E8E93]">
              聊天中确认的产品、数量与报价可随时记入黑板。
            </div>
          </div>

          <!-- 最近跟进（淡紫柔光摘要卡，呼应参考图「AI 小结 / 跟进摘要」） -->
          <div class="im-summary-card">
            <div class="flex items-center justify-between">
              <span class="inline-flex items-center gap-1 text-[12px] font-semibold text-[#7C3AED]">
                <IconifyIcon icon="lucide:sparkles" class="size-3.5" />
                最近跟进
              </span>
              <span
                v-if="customer?.contactLastTime"
                class="text-[11px] tabular-nums text-[#8E8E93]"
              >
                {{ fmtDateTime(customer.contactLastTime, 'MM-DD HH:mm') }}
              </span>
            </div>
            <div class="mt-1.5 text-[12.5px] leading-[19px] text-[#3A3A3C]">
              {{ customer?.contactLastContent || '暂无跟进记录，可点击下方「写跟进」补充沟通要点。' }}
            </div>
          </div>
        </template>

        <!-- 会话协作与属性 iOS 分组卡 -->
        <div class="im-info-group">
          <div class="im-info-row">
            <span class="im-info-label">会话处理人</span>
            <span class="flex items-center gap-1.5 text-[13px] font-medium text-[#1D1D1F]">
              <span class="truncate">{{ current.handlerName || '-' }}</span>
              <a
                v-if="!readOnly"
                class="shrink-0 text-xs font-normal text-[#007AFF]"
                @click="openTransfer"
                >转交</a
              >
            </span>
          </div>
          <div class="im-info-row">
            <span class="im-info-label">优先级</span>
            <Select
              :value="current.priority ?? 0"
              size="small"
              :bordered="false"
              class="-mr-2 w-24 text-right"
              :options="
                PRIORITY_OPTIONS.map((o) => ({
                  label: o.label,
                  value: o.value,
                }))
              "
              aria-label="优先级"
              @change="(v: any) => setPriority(Number(v))"
            />
          </div>
          <div class="im-info-row">
            <Tooltip
              :title="
                readOnly
                  ? '同事公开给团队的会话：可查看消息、写内部备注'
                  : '打开后公司全部成员都能在「团队会话」中查看此会话'
              "
            >
              <span class="im-info-label cursor-help">公开给团队</span>
            </Tooltip>
            <Switch
              size="small"
              :checked="!!current.teamPublic"
              :disabled="readOnly"
              :loading="teamPublicSaving"
              aria-label="公开给团队"
              @change="(v: any) => toggleTeamPublic(!!v)"
            />
          </div>
          <div class="im-info-row">
            <span class="im-info-label">所属渠道</span>
            <span class="truncate text-[12.5px] font-medium text-[#6E6E73]">{{
              accountName(current.accountId) || plat(current).label
            }}</span>
          </div>
        </div>

        <div v-if="otherConversations.length" class="flex flex-col gap-1.5">
          <div class="text-[12px] font-semibold text-[#6E6E73]">
            该客户的其他会话
          </div>
          <button
            v-for="o in otherConversations"
            :key="o.id"
            type="button"
            class="flex cursor-pointer items-center gap-2 rounded-xl border-0 bg-[#F5F5F7] px-3 py-2 text-left transition-colors hover:bg-[#EBEBF0]"
            @click="openConversation(o)"
          >
            <span
              class="inline-block size-2 shrink-0 rounded-full"
              :style="{ background: PLATFORM_DOT[o.platform || 'whatsapp'] }"
            ></span>
            <span class="flex min-w-0 flex-1 flex-col">
              <span class="truncate text-[11px] text-[#86868B]"
                >{{ accountName(o.accountId) || plat(o).label }} ·
                {{ smartTime(o.lastMessageTime) }}</span
              >
              <span class="truncate text-[12.5px] text-[#1D1D1F]">{{
                o.lastMessagePreview
              }}</span>
            </span>
            <span
              v-if="o.status === 'resolved'"
              class="shrink-0 text-[11px] text-[#8E8E93]"
              >已解决</span
            >
          </button>
        </div>

        <template v-if="current.customerId && customerStage !== undefined">
          <div class="mt-auto flex flex-col gap-2 pt-1">
            <div class="flex gap-2">
              <button
                type="button"
                class="im-pill-action im-pill-action--soft flex-1 justify-center !h-8"
                @click="openFollow"
              >
                写跟进
              </button>
              <button
                type="button"
                class="im-pill-action im-pill-action--link flex-1 justify-center !h-8"
                @click="
                  router.push({
                    path: '/v3/crm',
                    query: { customerId: String(current.customerId) },
                  })
                "
              >
                打开全景档案 ›
              </button>
            </div>
          </div>
        </template>
      </aside>
    </div>

    <Modal
      v-model:open="followOpen"
      title="写客户跟进"
      ok-text="保存记录"
      :width="480"
      wrap-class-name="ap-modal-sheet"
      :confirm-loading="followSaving"
      @ok="saveFollow"
    >
      <div class="ap-sheet-lead">
        <span class="ap-sheet-icon ap-sheet-icon--purple">
          <IconifyIcon icon="lucide:notebook-pen" class="size-5" />
        </span>
        <div class="ap-sheet-subtitle">
          记录本次沟通重点与客户意向，同步沉淀至 CRM 客户档案。
        </div>
      </div>
      <div class="ap-sheet-body">
        <div class="ap-sheet-field">
          <label class="ap-sheet-label">跟进内容</label>
          <Textarea
            v-model:value="followForm.content"
            :auto-size="{ minRows: 4, maxRows: 8 }"
            :maxlength="2000"
            placeholder="这次聊了什么、客户的反馈、下一步要推进的事项…"
          />
        </div>
        <div class="ap-sheet-field">
          <label class="ap-sheet-label">下次联系提醒</label>
          <div>
            <Segmented
              v-model:value="followForm.days"
              :options="FOLLOW_DAYS"
            />
          </div>
        </div>
      </div>
    </Modal>

    <Modal
      v-model:open="remarkOpen"
      :title="remarkTitle"
      ok-text="保存"
      :width="440"
      wrap-class-name="ap-modal-sheet"
      @ok="saveRemark"
    >
      <div class="ap-sheet-body">
        <div class="ap-sheet-field">
          <label class="ap-sheet-label">
            <span>{{ current?.group ? '群聊显示名称' : '客户备注名称' }}</span>
            <span class="ap-sheet-optional">留空恢复默认名称</span>
          </label>
          <Input
            v-model:value="remarkText"
            :maxlength="100"
            allow-clear
            :placeholder="
              current?.group
                ? '例如：迪拜灯具客户群'
                : '例如：Rashid（迪拜灯具批发商）'
            "
          />
        </div>
      </div>
    </Modal>

    <Modal
      v-model:open="transferOpen"
      title="转交给同事"
      ok-text="确认转交"
      :width="460"
      wrap-class-name="ap-modal-sheet"
      @ok="doTransfer"
    >
      <div class="ap-sheet-lead">
        <span class="ap-sheet-icon">
          <IconifyIcon icon="lucide:arrow-right-left" class="size-5" />
        </span>
        <div class="ap-sheet-subtitle">
          转交后由所选同事继续接待此会话，你的「我的会话」列表中将不再显示；当前 AI 托管开关保持不变。
        </div>
      </div>
      <div class="ap-sheet-body">
        <div class="ap-sheet-field">
          <label class="ap-sheet-label">接收同事</label>
          <Select
            v-model:value="transferTo"
            show-search
            option-filter-prop="label"
            class="w-full"
            placeholder="搜索并选择同事姓名"
            :options="users.map((u) => ({ value: u.id, label: u.nickname }))"
          />
        </div>
      </div>
    </Modal>

    <Modal
      v-model:open="newOpen"
      title="发起新会话"
      ok-text="发送消息"
      :confirm-loading="newSending"
      :width="500"
      wrap-class-name="ap-modal-sheet"
      @ok="sendNewChat"
    >
      <div class="ap-sheet-lead">
        <span class="ap-sheet-icon">
          <IconifyIcon icon="lucide:message-square-plus" class="size-5" />
        </span>
        <div class="ap-sheet-subtitle">
          向全球客户主动发送首条 WhatsApp 消息，发出后自动创建并打开会话。
        </div>
      </div>
      <div class="ap-sheet-body">
        <div v-if="waAccounts.length > 1" class="ap-sheet-field">
          <label class="ap-sheet-label">发送账号</label>
          <Select
            v-model:value="newForm.accountId"
            :options="newAccountOptions"
            placeholder="选择用于发送的 WhatsApp 号码"
            @change="(v: any) => onNewAccountChange(v)"
          />
        </div>
        <div class="ap-sheet-field">
          <label class="ap-sheet-label">
            <span>客户号码</span>
            <span class="ap-sheet-optional">需包含国际区号</span>
          </label>
          <Input
            v-model:value="newForm.to"
            allow-clear
            placeholder="例如 +971501234567 或 +8613800001111"
          >
            <template #prefix>
              <IconifyIcon icon="lucide:phone" class="mr-1 size-4 text-[#8E8E93]" />
            </template>
          </Input>
        </div>
        <div class="ap-sheet-field">
          <label class="ap-sheet-label">
            <span>消息内容</span>
            <span class="ap-sheet-optional tabular-nums"
              >{{ newForm.text.length }} / 4096</span
            >
          </label>
          <Textarea
            v-model:value="newForm.text"
            :auto-size="{ minRows: 4, maxRows: 9 }"
            :maxlength="4096"
            placeholder="输入首条问候或业务消息…"
          />
        </div>
        <div class="ap-sheet-tip">
          <IconifyIcon
            icon="lucide:shield-alert"
            class="mt-0.5 size-4 shrink-0 text-[#FF9500]"
          />
          <span>
            扫码接入的号码可直接发送普通消息；请避免短时间内高频群发陌生号码，以防触发
            WhatsApp 风控限制。
          </span>
        </div>
      </div>
    </Modal>

    <Modal
      v-model:open="groupOpen"
      title="新建群聊"
      ok-text="创建群聊"
      :confirm-loading="groupSending"
      :width="520"
      wrap-class-name="ap-modal-sheet"
      @ok="sendGroup"
    >
      <div class="ap-sheet-lead">
        <span class="ap-sheet-icon ap-sheet-icon--green">
          <IconifyIcon icon="lucide:users" class="size-5" />
        </span>
        <div class="ap-sheet-subtitle">
          在你的 WhatsApp 账号上创建专属客户沟通群，并向成员发出入群邀请。
        </div>
      </div>
      <div class="ap-sheet-body">
        <div class="ap-sheet-field">
          <label class="ap-sheet-label">
            <span>建群号码</span>
            <span class="ap-sheet-optional">仅支持扫码接入号码</span>
          </label>
          <Select
            v-model:value="groupForm.accountId"
            :options="
              evolutionAccounts.map((a) => ({
                value: a.id,
                label: a.displayPhone || a.name || '扫码号码',
              }))
            "
            placeholder="选择用于建群的 WhatsApp 号码"
          />
        </div>
        <div class="ap-sheet-field">
          <label class="ap-sheet-label">
            <span>群聊名称</span>
            <span class="ap-sheet-optional tabular-nums"
              >{{ groupForm.subject.length }} / 60</span
            >
          </label>
          <Input
            v-model:value="groupForm.subject"
            :maxlength="60"
            allow-clear
            placeholder="例如：迪拜灯具项目对接群"
          />
        </div>
        <div class="ap-sheet-field">
          <label class="ap-sheet-label">
            <span>成员号码</span>
            <span class="ap-sheet-optional">含国际区号，逗号或换行分隔</span>
          </label>
          <Textarea
            v-model:value="groupForm.participants"
            :auto-size="{ minRows: 2, maxRows: 5 }"
            placeholder="例如：+971501234567, +971509876543"
          />
        </div>
        <div class="ap-sheet-field">
          <label class="ap-sheet-label">
            <span>入群首条消息</span>
            <span class="ap-sheet-optional">选填</span>
          </label>
          <Textarea
            v-model:value="groupForm.message"
            :auto-size="{ minRows: 3, maxRows: 6 }"
            :maxlength="4096"
            placeholder="建群后自动发送的第一句欢迎语（可留空）"
          />
        </div>
        <div class="ap-sheet-tip">
          <IconifyIcon
            icon="lucide:shield-alert"
            class="mt-0.5 size-4 shrink-0 text-[#FF9500]"
          />
          <span>
            成员会收到 WhatsApp 入群邀请；请勿批量拉未联系过的陌生号码建群，以免账号受限。
          </span>
        </div>
      </div>
    </Modal>

    <Modal
      v-model:open="tplOpen"
      title="发送模板消息"
      ok-text="发送模板"
      :width="540"
      wrap-class-name="ap-modal-sheet"
      @ok="sendTemplate"
    >
      <Spin :spinning="tplLoading">
        <div class="ap-sheet-lead">
          <span class="ap-sheet-icon ap-sheet-icon--purple">
            <IconifyIcon icon="lucide:file-text" class="size-5" />
          </span>
          <div class="ap-sheet-subtitle">
            超出 24 小时会话窗口或使用官方 API 主动触达客户时，可发送已通过 Meta 审核的模板消息。
          </div>
        </div>
        <div class="ap-sheet-body">
          <div class="ap-sheet-field">
            <label class="ap-sheet-label">客户号码</label>
            <Input
              v-model:value="newTo"
              :disabled="!!current && newTo === current.waId"
              placeholder="含国家码，例如 +14155550100"
            />
          </div>
          <div class="ap-sheet-field">
            <label class="ap-sheet-label">消息模板</label>
            <Select
              v-model:value="tplKey"
              placeholder="选择已审核通过的模板"
              :options="
                approved.map((t) => ({
                  label: `${t.name}（${t.language}）`,
                  value: `${t.name}|${t.language}`,
                }))
              "
              :not-found-content="
                tplLoading ? '加载中…' : '没有已审核的模板，请先在 Meta 后台创建'
              "
            />
          </div>
          <div
            v-for="(_, i) in tplParams"
            :key="i"
            class="ap-sheet-field"
          >
            <label class="ap-sheet-label">模板变量 #{{ i + 1 }}</label>
            <Input
              v-model:value="tplParams[i]"
              :placeholder="`填写变量 {{${i + 1}}} 的内容`"
            />
          </div>
          <div
            v-if="chosen"
            class="rounded-2xl bg-[#F5F5F8] p-3.5"
          >
            <div class="mb-2 text-[11px] font-semibold text-[#86868B]">
              发送效果预览
            </div>
            <div
              class="ml-auto max-w-[88%] rounded-[16px_16px_4px_16px] bg-[#007AFF] px-3.5 py-2.5 text-[13.5px] leading-relaxed text-white whitespace-pre-wrap shadow-sm"
            >
              {{ templatePreview(chosen, tplParams) }}
            </div>
          </div>
        </div>
      </Spin>
    </Modal>
  </Page>
</template>

<style scoped>
.apple-im {
  height: 100%;
  color: #1d1d1f;
}
.apple-im .im-panes {
  gap: 0;
  min-height: 0;
  overflow: hidden;
  background: var(--ap-surface);
  border-radius: 0;
  box-shadow: none;
}
.apple-im .im-list {
  width: 312px;
  background: #f6f6f9;
  border: 0;
  border-right: 0.5px solid var(--ap-separator);
  border-radius: 0;
}
.im-list-header {
  border-bottom: 0.5px solid rgb(0 0 0 / 5%);
}
/* 苹果通讯录彩色渐变单字头像 */
.im-avatar {
  display: inline-grid;
  flex: none;
  place-items: center;
  object-fit: cover;
  color: #fff;
  font-weight: 600;
  border-radius: 50%;
  user-select: none;
  box-shadow:
    inset 0 0.5px 0 rgb(255 255 255 / 35%),
    0 1px 3px rgb(0 0 0 / 10%);
}
.im-avatar--md {
  width: 38px;
  height: 38px;
  font-size: 15px;
}
.im-avatar--lg {
  width: 56px;
  height: 56px;
  font-size: 22px;
  box-shadow:
    inset 0 1px 0 rgb(255 255 255 / 38%),
    0 4px 12px rgb(0 0 0 / 10%);
}
/* iOS 风格无边框圆角搜索栏 */
.im-search-box {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 10px;
  background: rgb(118 118 128 / 11%);
  border-radius: 10px;
  transition:
    background-color var(--ap-dur-fast),
    box-shadow var(--ap-dur-fast);
}
.im-search-box:focus-within {
  background: #fff;
  box-shadow:
    0 0 0 2px rgb(0 122 255 / 24%),
    0 1px 4px rgb(0 0 0 / 5%);
}
.im-search-box__icon {
  flex: none;
  width: 14px;
  height: 14px;
  color: #8e8e93;
}
.im-search-box__input {
  flex: 1;
  min-width: 0;
  height: 100%;
  padding: 0;
  font-size: 13px;
  color: #1d1d1f;
  background: transparent;
  border: 0;
  outline: none;
}
.im-search-box__input::placeholder {
  color: #8e8e93;
}
.im-search-box__input::-webkit-search-cancel-button {
  display: none;
}
.im-search-box__clear {
  display: grid;
  flex: none;
  place-items: center;
  width: 16px;
  height: 16px;
  padding: 0;
  color: #fff;
  cursor: pointer;
  background: #aeaeb2;
  border: 0;
  border-radius: 50%;
}
.im-search-box__clear:hover {
  background: #8e8e93;
}
.im-status-pills {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 2px;
  padding: 2.5px;
  background: rgb(118 118 128 / 10%);
  border-radius: 9px;
}
.im-status-pill {
  height: 26px;
  padding: 0 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--ap-label-2);
  white-space: nowrap;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 7px;
  transition:
    background-color var(--ap-dur-fast) linear,
    color var(--ap-dur-fast) linear,
    box-shadow var(--ap-dur-fast) linear,
    transform var(--ap-dur-fast) var(--ap-ease);
}
.im-status-pill:hover:not(.on) {
  color: var(--ap-label);
}
.im-status-pill:active {
  transform: scale(0.97);
}
.im-status-pill.on {
  font-weight: 600;
  color: var(--ap-label);
  background: #fff;
  box-shadow:
    0 1px 3px rgb(0 0 0 / 8%),
    0 0 0 0.5px rgb(0 0 0 / 4%);
}
.im-list-tabs {
  gap: 4px;
  justify-content: space-between;
}
.im-list-tab {
  padding: 3px 7px;
  font-size: 11.5px;
  font-weight: 500;
  color: #86868b;
  white-space: nowrap;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 999px;
  transition:
    background-color var(--ap-dur-fast),
    color var(--ap-dur-fast);
}
.im-list-tab:hover:not(.is-active) {
  color: #1d1d1f;
  background: rgb(0 0 0 / 4%);
}
.im-list-tab.is-active {
  font-weight: 600;
  color: #007aff;
  background: rgb(0 122 255 / 11%);
}
.im-contact {
  position: relative;
  margin: 3px 8px;
  padding: 10px 11px;
  border: 0;
  border-radius: 12px;
  transition:
    background-color var(--ap-dur-fast) linear,
    box-shadow var(--ap-dur-fast) linear,
    transform var(--ap-dur-fast) var(--ap-ease);
}
.im-contact:hover:not(.im-selected) {
  background: rgb(0 0 0 / 3.5%);
}
.im-contact:active {
  transform: scale(0.992);
}
.im-contact.im-selected {
  background: #fff;
  color: var(--ap-label);
  box-shadow:
    0 2px 10px rgb(0 0 0 / 6%),
    0 0 0 0.5px rgb(0 0 0 / 5%);
}
.apple-im .im-thread {
  border: 0;
  border-radius: 0;
  min-height: 0;
  background: #f5f5f8;
}
.im-empty-glyph {
  display: grid;
  place-items: center;
  width: 60px;
  height: 60px;
  color: #007aff;
  background: linear-gradient(180deg, #fff 0%, #eef4ff 100%);
  border-radius: 20px;
  box-shadow:
    0 4px 16px rgb(0 122 255 / 12%),
    0 0 0 0.5px rgb(0 122 255 / 14%);
}
.apple-im .im-toolbar {
  margin: 0;
  padding: 11px 20px;
  border: 0;
  border-bottom: 0.5px solid var(--ap-separator);
  border-radius: 0;
  background: rgb(255 255 255 / 92%);
  backdrop-filter: blur(18px);
  box-shadow: none;
}
.im-icon-ghost {
  display: inline-grid;
  flex: none;
  place-items: center;
  width: 22px;
  height: 22px;
  padding: 0;
  color: #aeaeb2;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 6px;
  transition:
    color var(--ap-dur-fast),
    background-color var(--ap-dur-fast);
}
.im-icon-ghost:hover {
  color: #1d1d1f;
  background: rgb(0 0 0 / 5%);
}
.im-crm-chip {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  border: 0;
  border-radius: 999px;
}
.im-crm-chip--blue {
  color: #007aff;
  background: rgb(0 122 255 / 10%);
}
.im-crm-chip--blue:hover {
  background: rgb(0 122 255 / 16%);
}
.im-crm-chip--teal {
  color: #00827c;
  background: rgb(0 199 190 / 14%);
}
.im-ai-toggle {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 4px 10px 4px 6px;
  font-size: 12px;
  font-weight: 500;
  color: #3a3a3c;
  cursor: pointer;
  background: #f5f5f7;
  border-radius: 999px;
}
.im-pill-action {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 30px;
  padding: 0 13px;
  font-size: 12.5px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  border: 0;
  border-radius: 999px;
  transition:
    background-color var(--ap-dur-fast),
    color var(--ap-dur-fast),
    transform var(--ap-dur-fast) var(--ap-ease),
    opacity var(--ap-dur-fast);
}
.im-pill-action:active:not(:disabled) {
  transform: scale(0.96);
}
.im-pill-action:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.im-pill-action--primary {
  color: #fff;
  background: #007aff;
  box-shadow: 0 2px 8px rgb(0 122 255 / 24%);
}
.im-pill-action--primary:hover:not(:disabled) {
  background: #006ee6;
}
.im-pill-action--soft {
  color: #3a3a3c;
  background: #f2f2f7;
}
.im-pill-action--soft:hover:not(:disabled) {
  color: #1d1d1f;
  background: #e5e5ea;
}
.im-pill-action--link {
  color: #007aff;
  background: rgb(0 122 255 / 8%);
}
.im-pill-action--link:hover:not(:disabled) {
  background: rgb(0 122 255 / 14%);
}
.im-circle-action {
  display: grid;
  flex: none;
  place-items: center;
  width: 30px;
  height: 30px;
  padding: 0;
  color: #6e6e73;
  cursor: pointer;
  background: #f2f2f7;
  border: 0;
  border-radius: 50%;
  transition:
    background-color var(--ap-dur-fast),
    color var(--ap-dur-fast),
    transform var(--ap-dur-fast) var(--ap-ease);
}
.im-circle-action:hover {
  color: #1d1d1f;
  background: #e5e5ea;
}
.im-circle-action.is-starred {
  color: #ff9500;
  background: #fff4e0;
}
.apple-im .im-messages {
  min-height: 100px;
  padding: 20px 24px;
  flex: 1;
  background: #f5f5f8;
}
.im-bubble {
  word-break: break-word;
  animation: ap-bubble-in 320ms var(--ap-spring) both;
}
.im-bubble--in {
  color: #1d1d1f;
  background: #fff;
  border-radius: 18px 18px 18px 5px;
  box-shadow:
    0 1px 2px rgb(0 0 0 / 5%),
    0 0 0 0.5px rgb(0 0 0 / 4%);
}
.im-bubble--out {
  color: #fff;
  background: #007aff;
  border-radius: 18px 18px 5px 18px;
  box-shadow: 0 2px 8px rgb(0 122 255 / 20%);
}
.im-bubble--ai {
  color: #fff;
  background: linear-gradient(135deg, #7455e6 0%, #007aff 100%);
  border-radius: 18px 18px 5px 18px;
  box-shadow: 0 3px 12px rgb(92 86 230 / 22%);
}
.apple-im .im-composer {
  flex-shrink: 0;
  max-height: 52%;
  overflow: auto;
  margin: 0 20px 18px;
  padding: 12px 16px;
  background: var(--ap-surface);
  border: 0;
  border-radius: 18px;
  box-shadow:
    0 2px 12px rgb(0 0 0 / 5%),
    0 0 0 0.5px rgb(0 0 0 / 6%);
  transition: box-shadow var(--ap-dur-fast) var(--ap-ease);
}
.apple-im .im-composer:focus-within {
  box-shadow:
    0 0 0 2.5px var(--ap-accent-ring),
    0 4px 16px rgb(0 0 0 / 7%);
}
.apple-im .im-composer :deep(.ant-input) {
  border: 0;
  background: transparent !important;
  box-shadow: none !important;
}
.apple-im .im-composer :deep(.ant-btn) {
  border-radius: 8px;
}
.apple-im .im-draft {
  border: 1px solid rgb(175 82 222 / 26%);
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 4px 18px rgb(175 82 222 / 8%);
}
.im-draft-wrap {
  padding: 0 20px 10px;
  background: #f5f5f8;
}
.im-new-btn {
  display: grid;
  flex: none;
  place-items: center;
  width: 28px;
  height: 28px;
  color: #007aff;
  cursor: pointer;
  background: #e8f2ff;
  border: 0;
  border-radius: 50%;
  transition:
    background-color var(--ap-dur-fast),
    transform var(--ap-dur-fast) var(--ap-ease);
}
.im-new-btn:hover {
  background: #d8e9ff;
}
.im-new-btn:active {
  transform: scale(0.92);
}
.im-tool-btn {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  color: #6e6e73;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 50%;
  transition:
    background-color var(--ap-dur-fast),
    color var(--ap-dur-fast),
    transform var(--ap-dur-fast) var(--ap-ease);
}
.im-tool-btn:hover:not(:disabled) {
  color: #1d1d1f;
  background: #f2f2f7;
}
.im-tool-btn--ai {
  color: #8b5cf6;
}
.im-tool-btn--ai:hover:not(:disabled) {
  color: #7c3aed;
  background: rgb(139 92 246 / 10%);
}
.im-tool-btn:active {
  transform: scale(0.92);
}
.im-send-circle {
  display: grid;
  flex: none;
  place-items: center;
  width: 32px;
  height: 32px;
  color: #fff;
  cursor: pointer;
  background: #007aff;
  border: 0;
  border-radius: 50%;
  box-shadow: 0 2px 8px rgb(0 122 255 / 28%);
  transition:
    background-color var(--ap-dur-fast),
    transform var(--ap-dur-fast) var(--ap-ease),
    opacity var(--ap-dur-fast);
}
.im-send-circle:hover:not(:disabled) {
  background: #0a84ff;
}
.im-send-circle:active:not(:disabled) {
  transform: scale(0.92);
}
.im-send-circle:disabled {
  cursor: not-allowed;
  opacity: 0.35;
  box-shadow: none;
}
.im-info-group {
  overflow: hidden;
  background: #f5f5f7;
  border-radius: 14px;
}
.im-info-row {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  min-height: 38px;
  padding: 5px 13px;
}
.im-info-row + .im-info-row {
  border-top: 0.5px solid rgb(0 0 0 / 6%);
}
.im-info-label {
  flex: none;
  font-size: 12.5px;
  color: #86868b;
}
.im-summary-card {
  padding: 11px 13px;
  background: linear-gradient(180deg, #faf7ff 0%, #f5f0ff 100%);
  border: 0.5px solid rgb(139 92 246 / 18%);
  border-radius: 14px;
}
.apple-im .im-inspector {
  width: 276px;
  border: 0;
  border-left: 0.5px solid var(--ap-separator);
  border-radius: 0;
  padding: 18px 16px;
  background: var(--ap-surface);
}
.im-send-tools {
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}
.im-send-tools > :last-child {
  margin-left: auto;
  flex-shrink: 0;
}
@media (width <= 1450px) and (width >= 1024px) {
  .apple-im .im-list {
    width: 288px;
  }
  .apple-im .im-inspector {
    width: 252px;
  }
  .apple-im .im-toolbar {
    padding: 10px 14px;
  }
}
@media (width <= 1100px) {
  .apple-im .im-inspector {
    display: none;
  }
  .apple-im .im-list {
    width: 268px;
  }
}
@media (width <= 1023px) {
  .apple-im .im-list {
    width: 100%;
  }
}
@media (width <= 760px) {
  .apple-im .im-composer {
    margin: 0 10px 10px;
    padding: 10px 12px;
  }
  .apple-im .im-messages {
    padding: 14px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .im-contact {
    transition: none;
  }
}
</style>
