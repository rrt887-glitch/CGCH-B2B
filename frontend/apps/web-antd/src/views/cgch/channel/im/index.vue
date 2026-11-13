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
  ref,
  watch,
} from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';
import { useUserStore } from '@vben/stores';

import {
  Alert,
  Avatar,
  Button,
  Empty,
  Input,
  InputSearch,
  message,
  Modal,
  Dropdown,
  DropdownButton,
  Menu,
  MenuItem,
  Popover,
  Segmented,
  Select,
  Space,
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
  starWaConversation,
  templateParamCount,
  transferWaConversation,
  templatePreview,
} from '#/api/channel/whatsapp';

defineOptions({ name: 'CgchChannelIm' });

/** 嵌入「会话」页时：账号由左栏选择，隐藏本页的账号下拉 */
const props = defineProps<{
  accountId?: number;
  embedded?: boolean;
  level?: number;
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
const accountId = ref<number | undefined>(props.accountId);
const keyword = ref('');
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
const listTitle = computed(() => {
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

async function loadConversations(silent = false) {
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
      status: statusFilter.value || undefined,
    });
    conversations.value = res.list;
  } finally {
    listLoading.value = false;
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
  [accountId, listFilter, statusFilter, () => props.level, () => props.view],
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
  accounts.value = await getWaAccountList();
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
  name?: string;
  level?: number;
  countryCode?: string;
  email?: string;
  mobile?: string;
  contactNextTime?: number | string;
  contactLastTime?: number | string;
  contactLastContent?: string;
  ownerUserName?: string;
  companyName?: string;
}
const customer = ref<PanelCustomer>();
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

// ===== 群聊的 AI 自动回复开关 =====
const groupAiSaving = ref(false);
async function toggleGroupAi(on: boolean) {
  const c = current.value;
  if (!c) return;
  groupAiSaving.value = true;
  try {
    await setWaGroupAi(c.id, on);
    c.aiEnabled = on;
    const row = conversations.value.find((x) => x.id === c.id);
    if (row) row.aiEnabled = on;
    message.success(
      on
        ? '已开启：AI 会按「AI 接待设置」回复这个群的消息'
        : '已关闭：AI 不再回复这个群',
    );
  } finally {
    groupAiSaving.value = false;
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
const text = ref('');
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
const initials = (c: ChannelWaApi.Conversation) =>
  (c.profileName || c.waId).slice(0, 1).toUpperCase();
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
        <div class="flex flex-col gap-2.5 px-4 pt-3.5">
          <div class="flex items-center gap-2">
            <h2
              class="m-0 min-w-0 flex-1 truncate text-base font-semibold text-[#1D1D1F]"
            >
              {{ listTitle }}
            </h2>
            <Select
              v-model:value="statusFilter"
              size="small"
              class="w-[92px]"
              :options="STATUS_FILTER"
              aria-label="会话状态"
            />
            <Tooltip v-if="waAccounts.length > 0" title="新会话">
              <Button size="small" aria-label="新会话" @click="openNewChat"
                ><IconifyIcon icon="lucide:plus" class="size-3.5"
              /></Button>
            </Tooltip>
          </div>
          <Select
            v-if="!embedded"
            v-model:value="accountId"
            size="small"
            :options="accountOptions"
          />
          <InputSearch
            v-model:value="keyword"
            allow-clear
            placeholder="搜索客户、号码、备注名"
            @search="() => loadConversations()"
          />
          <div
            class="flex gap-4 overflow-x-auto border-0 border-b border-solid border-[#E5E5EA]"
            role="tablist"
            aria-label="会话筛选"
          >
            <button
              v-for="f in FILTER_OPTIONS"
              :key="f.value"
              type="button"
              role="tab"
              :aria-selected="listFilter === f.value"
              class="-mb-px shrink-0 cursor-pointer border-0 border-b-2 border-solid bg-transparent px-0 pb-2 pt-1 text-[13px]"
              :class="
                listFilter === f.value
                  ? 'border-[#0071E3] font-semibold text-[#1D1D1F]'
                  : 'border-transparent text-[#86868B] hover:text-[#1D1D1F]'
              "
              @click="listFilter = f.value as any"
            >
              {{ f.label }}
            </button>
          </div>
        </div>
        <div class="flex-1 overflow-auto">
          <Spin :spinning="listLoading">
            <div v-if="conversations.length === 0" class="p-8">
              <Empty
                :description="
                  teamView
                    ? '没有同事负责的会话（需要部门数据权限才能看到下属的会话）'
                    : statusFilter === 'open'
                      ? '没有进行中的会话'
                      : '暂无会话'
                "
              />
            </div>
            <div
              v-for="c in conversations"
              :key="c.id"
              class="im-contact flex cursor-pointer gap-2.5 border-0 border-b border-solid border-[#F2F2F7] px-4 py-3"
              :class="
                current?.id === c.id
                  ? 'im-selected bg-[#E8F2FC] shadow-[inset_3px_0_0_#0071E3]'
                  : 'hover:bg-[#F5F5F7]'
              "
              @click="openConversation(c)"
            >
              <Avatar
                :size="36"
                :src="c.avatarUrl || undefined"
                class="shrink-0"
                :class="c.avatarUrl ? '' : plat(c).avatar"
                >{{ initials(c) }}</Avatar
              >
              <div class="flex min-w-0 flex-1 flex-col gap-0.5">
                <div
                  class="flex items-center gap-1.5 text-[11px] text-[#86868B]"
                >
                  <span
                    class="inline-block size-2 shrink-0 rounded-sm"
                    :style="{
                      background: PLATFORM_DOT[c.platform || 'whatsapp'],
                    }"
                  ></span>
                  <span class="min-w-0 flex-1 truncate">{{
                    accountName(c.accountId) || plat(c).label
                  }}</span>
                  <span class="shrink-0"
                    >{{ md(c.lastMessageTime) }}
                    {{ hm(c.lastMessageTime) }}</span
                  >
                </div>
                <div class="flex items-center gap-1.5">
                  <span
                    class="min-w-0 flex-1 truncate font-semibold text-[#1D1D1F]"
                  >
                    <span v-if="c.starred" class="mr-1 text-amber-500">★</span>
                    <IconifyIcon
                      v-if="c.group"
                      icon="lucide:users"
                      class="mr-0.5 inline size-3.5 align-[-2px] text-[#6E6E73]"
                    />{{ c.remarkName || c.customerName || contact(c) }}
                    <span
                      v-if="c.group && c.aiEnabled"
                      class="ml-1 rounded bg-[#E8F2FC] px-1 text-[10px] text-[#0071E3]"
                      >AI</span
                    >
                  </span>
                  <span
                    v-if="c.unreadCount"
                    class="flex h-[18px] min-w-[18px] shrink-0 items-center justify-center rounded-full bg-[#0071E3] px-1.5 text-[11px] font-semibold text-white"
                  >
                    {{ c.unreadCount > 99 ? '99+' : c.unreadCount }}
                  </span>
                </div>
                <div class="truncate text-[13px] text-[#6E6E73]">
                  <span
                    v-if="teamView && c.handlerName"
                    class="mr-1 text-[#0071E3]"
                    >[{{ c.handlerName }}]</span
                  >{{ c.lastMessagePreview }}
                </div>
                <div
                  v-if="
                    (c.priority ?? 0) > 0 ||
                    c.customerLevel ||
                    (!statusFilter && c.status && c.status !== 'open')
                  "
                  class="mt-0.5 flex flex-wrap gap-1"
                >
                  <span
                    v-if="(c.priority ?? 0) > 0"
                    class="rounded px-1.5 text-[11px] font-medium leading-[18px]"
                    :style="{
                      color: priorityMeta(c.priority)?.color,
                      background: '#F5F5F7',
                      border: `1px solid ${priorityMeta(c.priority)?.color}33`,
                    }"
                  >
                    {{ priorityMeta(c.priority)?.label }}
                  </span>
                  <span
                    v-if="c.customerLevel"
                    class="rounded bg-[#FFF4E0] px-1.5 text-[11px] leading-[18px] text-[#A05A00]"
                    >{{ levelLabel(c.customerLevel) }}</span
                  >
                  <span
                    v-if="!statusFilter && c.status && c.status !== 'open'"
                    class="rounded bg-[#F2F2F7] px-1.5 text-[11px] leading-[18px] text-[#424245]"
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
        <div v-if="!current" class="flex h-full items-center justify-center">
          <Empty description="选择一个会话" />
        </div>
        <template v-else>
          <div
            class="im-toolbar flex items-center justify-between border-0 border-b border-solid border-[#E5E5EA] px-5 py-3"
            :class="narrow ? 'flex-wrap gap-2 !px-3' : ''"
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
              <Avatar
                :size="36"
                :src="current.avatarUrl || undefined"
                :class="
                  current.avatarUrl
                    ? 'shrink-0'
                    : `shrink-0 ${plat(current).avatar}`
                "
                >{{ initials(current) }}</Avatar
              >
              <div class="min-w-0 flex-1">
                <div
                  class="truncate whitespace-nowrap text-[17px] font-semibold tracking-tight"
                >
                  <span v-if="current.starred" class="mr-1 text-amber-500"
                    >★</span
                  >{{ current.remarkName || contact(current) }}
                  <span v-if="current.remarkName" class="text-sm text-gray-400"
                    >（{{ contact(current) }}）</span
                  >
                  <Tooltip
                    v-if="isWa(current) && hiddenNumber(current)"
                    title="对方在 WhatsApp 上隐藏了手机号，可以照常收发消息；对方之后带上号码时会自动合并到号码会话"
                  >
                    <span class="text-sm text-gray-400">隐藏号码</span>
                  </Tooltip>
                  <Tag v-else-if="current.group" color="green" class="ml-1"
                    >群聊</Tag
                  >
                  <span v-else-if="isWa(current)" class="text-sm text-gray-400"
                    >+{{ current.waId }}</span
                  >
                  <Tag v-else :color="plat(current).color" class="ml-1">{{
                    plat(current).label
                  }}</Tag>
                </div>
                <div class="mt-1 flex flex-wrap gap-1.5">
                  <Tag
                    v-if="current.customerName"
                    color="blue"
                    class="cursor-pointer"
                    @click="
                      router.push(`/crm/customer/detail/${current.customerId}`)
                    "
                  >
                    客户：{{ current.customerName }}
                  </Tag>
                  <Tag
                    v-else-if="current.clueName"
                    color="cyan"
                    class="cursor-pointer"
                    @click="router.push(`/crm/clue/detail/${current.clueId}`)"
                  >
                    线索：{{ current.clueName }}
                  </Tag>
                  <!-- 群聊：AI 自动回复默认关闭，在这里按群开启 -->
                  <span
                    v-else-if="current.group"
                    class="inline-flex items-center gap-1.5 text-xs text-[#6E6E73]"
                  >
                    AI 自动回复
                    <Switch
                      size="small"
                      :checked="!!current.aiEnabled"
                      :loading="groupAiSaving"
                      @change="(v: any) => toggleGroupAi(!!v)"
                    />
                  </span>
                  <Tag v-else>未关联客户</Tag>
                  <!-- 窄屏没有右侧客户栏时，阶段和标签在这里改 -->
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
                    <Tag
                      v-for="t in customerTags.slice(0, 3)"
                      :key="t"
                      class="m-0"
                      >{{ tagName(t) || t }}</Tag
                    >
                  </span>
                  <Tooltip
                    v-if="current.windowOpen"
                    :title="`窗口在 ${fmtDateTime(current.windowExpireTime)} 关闭`"
                  >
                    <Tag color="green">24 小时窗口内</Tag>
                  </Tooltip>
                  <Tag v-else color="orange">{{
                    isWa(current)
                      ? '窗口已关闭，仅可发模板'
                      : '窗口已关闭，等待客户来信'
                  }}</Tag>
                </div>
              </div>
            </div>
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
                  :class="live ? 'bg-green-500' : 'bg-gray-300'"
                ></span>
              </Tooltip>
              <Tooltip :title="current.starred ? '取消收藏' : '收藏'">
                <Button
                  size="small"
                  :aria-label="current.starred ? '取消收藏' : '收藏'"
                  @click="toggleStar(current)"
                >
                  <span
                    :class="
                      current.starred ? 'text-amber-500' : 'text-[#86868B]'
                    "
                    >★</span
                  >
                </Button>
              </Tooltip>
              <Button size="small" @click="openTransfer">
                <IconifyIcon
                  icon="lucide:user-round-plus"
                  class="mr-1 inline size-3.5 align-[-2px]"
                />{{ current.handlerName || '指派' }}
              </Button>
              <Dropdown :trigger="['click']">
                <Button size="small">更多</Button>
                <template #overlay>
                  <Menu @click="(e: any) => onMenu(String(e.key))">
                    <MenuItem key="archive">{{
                      current.archived ? '移出归档' : '归档'
                    }}</MenuItem>
                    <MenuItem key="remark">设置备注名</MenuItem>
                    <MenuItem key="transfer">转交给同事</MenuItem>
                  </Menu>
                </template>
              </Dropdown>
              <Button
                v-if="current.status === 'resolved'"
                size="small"
                @click="setStatus('open')"
                >重新打开</Button
              >
              <DropdownButton
                v-else
                size="small"
                type="primary"
                @click="setStatus('resolved')"
              >
                标记已解决
                <template #overlay>
                  <Menu @click="(e: any) => setStatus(e.key as any)">
                    <MenuItem v-if="current.status === 'pending'" key="open"
                      >回到进行中</MenuItem
                    >
                    <MenuItem v-else key="pending">标为待处理</MenuItem>
                  </Menu>
                </template>
              </DropdownButton>
            </div>
          </div>

          <div
            ref="chatBox"
            class="im-messages flex-1 overflow-auto bg-white px-7 py-6"
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
                  class="mb-3 flex"
                  :class="
                    it.m.direction === 2 ? 'justify-end' : 'justify-start'
                  "
                >
                  <div
                    class="cg-bubble max-w-[70%] px-3.5 py-2.5 text-[15px] leading-[21px]"
                    :class="
                      it.m.direction === 2
                        ? 'rounded-[18px] bg-[#0071E3] text-white [&_a]:text-white [&_a]:underline'
                        : 'rounded-[18px] bg-[#E9E9EB] text-[#1D1D1F]'
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
                    <template v-if="MEDIA_LABEL[it.m.type]">
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
                    <div
                      class="mt-1 flex items-center justify-end gap-1 text-[11px]"
                      :class="
                        it.m.direction === 2
                          ? 'text-white/80'
                          : 'text-[#86868B]'
                      "
                    >
                      <span
                        v-if="it.m.aiGenerated"
                        class="rounded px-1 font-semibold"
                        :class="
                          it.m.direction === 2
                            ? 'bg-white/20'
                            : 'bg-[#E8F2FC] text-[#0066CC]'
                        "
                        >AI</span
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
                              ? 'rounded bg-white px-1 font-bold text-[#D70015]'
                              : it.m.status === 'read'
                                ? 'font-semibold'
                                : ''
                          "
                        >
                          {{ STATUS_ICON[it.m.status]?.text }}
                        </span>
                      </Tooltip>
                    </div>
                  </div>
                </div>
              </template>
            </Spin>
          </div>

          <div
            class="im-composer border-0 border-t border-solid border-[#E5E5EA] px-5 pb-4 pt-3"
          >
            <div
              v-if="aiDraft"
              class="im-draft mb-3 rounded-xl border border-solid border-[#B8D7F5] bg-white p-3.5 text-sm"
            >
              <div class="mb-1.5 text-xs font-semibold text-[#0066CC]">
                <IconifyIcon
                  icon="lucide:sparkles"
                  class="mr-1 inline size-3.5 align-[-2px]"
                />
                AI 已准备回复，未自动发出：{{
                  DRAFT_REASON[aiDraft.reason || ''] ?? aiDraft.reason
                }}
                <span v-if="aiDraft.reasonDetail"
                  >（{{ aiDraft.reasonDetail }}）</span
                >
              </div>
              <div
                v-if="aiDraft.content"
                class="line-clamp-4 whitespace-pre-wrap leading-[22px] text-[#1D1D1F]"
              >
                {{ aiDraft.content }}
              </div>
              <div class="mt-2.5 flex justify-end gap-2">
                <Button size="small" @click="discardDraft">不用</Button>
                <Button
                  v-if="aiDraft.content && current.windowOpen"
                  size="small"
                  type="primary"
                  @click="useDraft"
                  >填入输入框修改后发送</Button
                >
              </div>
            </div>
            <div
              class="mb-2 flex items-center gap-4"
              role="tablist"
              aria-label="输入方式"
            >
              <button
                v-for="t in [
                  { k: 'reply', label: '回复' },
                  { k: 'note', label: '内部备注' },
                ]"
                :key="t.k"
                type="button"
                role="tab"
                :aria-selected="composeMode === t.k"
                class="cursor-pointer border-0 border-b-2 border-solid bg-transparent px-0 pb-1.5 text-[13px]"
                :class="
                  composeMode === t.k
                    ? t.k === 'note'
                      ? 'border-[#FF9F0A] font-semibold text-[#A05A00]'
                      : 'border-[#0071E3] font-semibold text-[#1D1D1F]'
                    : 'border-transparent text-[#86868B]'
                "
                @click="composeMode = t.k as any"
              >
                {{ t.label }}
              </button>
              <span class="flex-1"></span>
              <span v-if="composeMode === 'note'" class="text-xs text-[#A05A00]"
                >只有团队能看到，不会发给客户</span
              >
            </div>
            <template v-if="composeMode === 'note'">
              <Textarea
                v-model:value="noteText"
                :auto-size="{ minRows: 2, maxRows: 6 }"
                :maxlength="1990"
                class="!border-[#FFE7A3] !bg-[#FFF8E0]"
                placeholder="写给同事看的备注，例如：报价前先确认起订量。Enter 保存，Shift+Enter 换行"
                @keydown="onNoteKeydown"
              />
              <div class="mt-2 flex justify-end">
                <Button
                  type="primary"
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
                class="mb-2 overflow-hidden rounded-lg border border-solid border-[#E5E5EA] bg-white shadow-sm"
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
                :auto-size="{ minRows: 2, maxRows: 6 }"
                :maxlength="4096"
                placeholder="输入消息，Enter 发送，Shift+Enter 换行；输入 / 搜索快捷回复"
                @keydown="onKeydown"
                @change="onInputChange"
              />
              <div
                v-if="tPrefs.input && (inputTranslation || inputTranslating)"
                class="mt-2 rounded bg-gray-50 px-3 py-2 text-sm"
              >
                <div
                  class="mb-1 flex items-center justify-between text-xs text-gray-500"
                >
                  <span>{{
                    inputTranslating ? '正在翻译…' : '译文（客户的语言）'
                  }}</span>
                  <a v-if="inputTranslation" @click="useTranslation"
                    >用译文替换</a
                  >
                </div>
                <div class="whitespace-pre-wrap text-gray-700">
                  {{ inputTranslation }}
                </div>
              </div>
              <div class="mt-2 flex justify-between">
                <Space>
                  <Button :loading="fileSending" @click="pickAndSend"
                    >发送文件</Button
                  >
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
                    <Button>快捷回复</Button>
                  </Popover>
                  <Button
                    v-if="isWa(current) && !isScanAccount(current.accountId)"
                    @click="openTemplate()"
                    >发送模板</Button
                  >
                  <Button :loading="aiLoading" @click="aiCompose"
                    >AI 起草</Button
                  >
                </Space>
                <Button
                  type="primary"
                  :loading="sending"
                  :disabled="!text.trim()"
                  @click="sendText"
                  >发送</Button
                >
              </div>
            </template>
            <Alert
              v-else
              type="warning"
              show-icon
              :message="
                isWa(current)
                  ? '客户超过 24 小时没有回复，WhatsApp 规定此时只能发送已审核的模板消息。'
                  : `客户超过 24 小时没有回复，${plat(current).label} 规定此时不能再发消息，等客户再次来信后即可回复。`
              "
            >
              <template v-if="isWa(current)" #action
                ><Button type="primary" size="small" @click="openTemplate()"
                  >发送模板</Button
                ></template
              >
            </Alert>
          </div>
        </template>
      </div>

      <!-- 客户信息栏（设计稿「会话工作台」右侧）：关联了客户时显示，窄屏隐藏 -->
      <aside
        v-if="current"
        class="im-inspector hidden w-80 shrink-0 flex-col gap-5 overflow-auto rounded-xl border border-solid border-[#E5E5EA] bg-white p-5 xl:flex"
      >
        <!-- 联系人 -->
        <div
          class="flex flex-col gap-2 border-0 border-b border-solid border-[#F2F2F7] pb-4"
        >
          <Avatar
            :size="48"
            :src="current.avatarUrl || undefined"
            :class="current.avatarUrl ? '' : plat(current).avatar"
            >{{ initials(current) }}</Avatar
          >
          <div>
            <div class="text-base font-semibold text-[#1D1D1F]">
              {{ current.remarkName || contact(current) }}
            </div>
            <div class="text-[13px] text-[#6E6E73]">
              {{ plat(current).label }} ·
              {{
                isWa(current)
                  ? waNumber(current)
                  : accountName(current.accountId)
              }}
            </div>
          </div>
          <!-- 群聊：AI 自动回复按群手动开启 -->
          <div
            v-if="current.group"
            class="flex flex-col gap-1.5 rounded-[10px] bg-[#F5F5F7] p-3"
          >
            <div
              class="flex items-center justify-between text-[13px] font-semibold text-[#1D1D1F]"
            >
              AI 自动回复这个群
              <Switch
                :checked="!!current.aiEnabled"
                :loading="groupAiSaving"
                @change="(v: any) => toggleGroupAi(!!v)"
              />
            </div>
            <div class="text-xs leading-5 text-[#86868B]">
              群聊默认不由 AI 回复。开启后，群里有新消息时 AI 会按「AI
              接待设置」回复（设为「AI
              起草」时只生成草稿）。群聊不会自动建线索。
            </div>
          </div>
          <div
            v-else-if="!current.customerId"
            class="flex items-center gap-2 text-[13px] text-[#86868B]"
          >
            <span>{{
              current.clueName
                ? `线索：${current.clueName}`
                : '还没有关联 CRM 客户'
            }}</span>
            <a
              v-if="current.clueId"
              @click="router.push(`/crm/clue/detail/${current.clueId}`)"
              >查看</a
            >
          </div>
        </div>

        <!-- 会话操作 -->
        <section class="flex flex-col gap-3">
          <div class="text-[13px] font-semibold text-[#1D1D1F]">会话操作</div>
          <div
            class="grid grid-cols-[64px_1fr] items-center gap-x-3 gap-y-2.5 text-[13px]"
          >
            <span class="text-[#86868B]">处理人</span>
            <span class="flex items-center justify-between gap-2">
              <span class="truncate">{{ current.handlerName || '-' }}</span>
              <a class="shrink-0 text-xs" @click="openTransfer">转交</a>
            </span>
            <span class="text-[#86868B]">状态</span>
            <Select
              :value="current.status || 'open'"
              size="small"
              :options="CONVERSATION_STATUS"
              aria-label="会话状态"
              @change="(v: any) => setStatus(v)"
            />
            <span class="text-[#86868B]">优先级</span>
            <Select
              :value="current.priority ?? 0"
              size="small"
              :options="
                PRIORITY_OPTIONS.map((o) => ({
                  label: o.label,
                  value: o.value,
                }))
              "
              aria-label="优先级"
              @change="(v: any) => setPriority(Number(v))"
            />
            <span class="text-[#86868B]">收件箱</span>
            <span class="truncate">{{
              accountName(current.accountId) || plat(current).label
            }}</span>
          </div>
        </section>

        <template v-if="current.customerId && customerStage !== undefined">
          <div
            class="flex flex-col gap-1 border-0 border-t border-solid border-[#F2F2F7] pt-4"
          >
            <div class="text-xs text-[#86868B]">客户</div>
            <a
              class="text-base font-semibold text-[#1D1D1F]"
              @click="router.push(`/crm/customer/detail/${current.customerId}`)"
              >{{ customer?.name || current.customerName }}</a
            >
            <div class="mt-1 flex flex-wrap gap-1.5">
              <span
                v-if="customer?.level"
                class="rounded bg-[#FFF4E0] px-2 py-0.5 text-xs font-medium text-[#A05A00]"
                >{{ levelLabel(customer.level) }}</span
              >
              <span
                v-if="customer?.countryCode"
                class="rounded bg-[#F2F2F7] px-2 py-0.5 text-xs text-[#424245]"
                >{{
                  countryName(customer.countryCode) || customer.countryCode
                }}</span
              >
              <span
                v-for="t in customerTags"
                :key="t"
                class="rounded bg-[#F2F2F7] px-2 py-0.5 text-xs text-[#424245]"
                >{{ tagName(t) || t }}</span
              >
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
                      <Button size="small" type="primary" @click="saveTags"
                        >保存</Button
                      >
                    </div>
                  </div>
                </template>
                <button
                  type="button"
                  class="cursor-pointer rounded border border-dashed border-[#D2D2D7] bg-white px-2 py-0.5 text-xs text-[#6E6E73]"
                >
                  + 标签
                </button>
              </Popover>
            </div>
          </div>

          <div class="flex flex-col gap-2.5">
            <div class="text-[13px] font-semibold">客户阶段</div>
            <div class="flex gap-1">
              <button
                v-for="s in stageSteps"
                :key="s.value"
                type="button"
                class="flex flex-1 cursor-pointer flex-col items-center gap-1.5 border-0 bg-transparent p-0"
                :title="`改为「${s.label}」`"
                @click="changeStage(s.value)"
              >
                <span
                  class="block h-1.5 w-full rounded-full"
                  :class="s.done ? 'bg-[#0071E3]' : 'bg-[#E5E5EA]'"
                ></span>
                <span
                  class="text-[11px]"
                  :class="
                    s.on ? 'font-semibold text-[#0071E3]' : 'text-[#86868B]'
                  "
                  >{{ s.label }}</span
                >
              </button>
            </div>
          </div>

          <dl
            class="m-0 grid grid-cols-[72px_1fr] gap-x-3 gap-y-2.5 text-[13px]"
          >
            <template v-if="customer?.companyName">
              <dt class="text-[#86868B]">公司</dt>
              <dd class="m-0 break-words">{{ customer.companyName }}</dd>
            </template>
            <template v-if="customer?.email">
              <dt class="text-[#86868B]">邮箱</dt>
              <dd class="m-0 break-all">{{ customer.email }}</dd>
            </template>
            <template v-if="customer?.mobile">
              <dt class="text-[#86868B]">电话</dt>
              <dd class="m-0">{{ customer.mobile }}</dd>
            </template>
            <dt class="text-[#86868B]">下次联系</dt>
            <dd
              class="m-0"
              :class="
                customer?.contactNextTime
                  ? 'font-medium text-[#C93400]'
                  : 'text-[#86868B]'
              "
            >
              {{
                fmtDateTime(customer?.contactNextTime, 'MM-DD HH:mm', '未约')
              }}
            </dd>
            <dt class="text-[#86868B]">负责人</dt>
            <dd class="m-0">{{ customer?.ownerUserName || '-' }}</dd>
          </dl>

          <div class="flex flex-col gap-2 rounded-[10px] bg-[#F5F5F7] p-3">
            <div class="flex items-center justify-between">
              <span class="text-[13px] font-semibold">最近跟进</span>
              <a
                class="text-xs"
                @click="
                  router.push(`/crm/customer/detail/${current.customerId}`)
                "
                >全部记录</a
              >
            </div>
            <div class="text-[13px] leading-5 text-[#424245]">
              {{ customer?.contactLastContent || '还没有跟进记录' }}
            </div>
            <div
              v-if="customer?.contactLastTime"
              class="text-xs text-[#86868B]"
            >
              {{ fmtDateTime(customer.contactLastTime, 'MM-DD HH:mm') }}
            </div>
          </div>

          <div v-if="otherConversations.length" class="flex flex-col gap-1.5">
            <div class="text-[13px] font-semibold">这位客户的其他会话</div>
            <button
              v-for="o in otherConversations"
              :key="o.id"
              type="button"
              class="flex cursor-pointer items-center gap-2 rounded-lg border border-solid border-[#E5E5EA] bg-white px-2.5 py-2 text-left hover:border-[#0071E3]"
              @click="openConversation(o)"
            >
              <span
                class="inline-block size-2 shrink-0 rounded-sm"
                :style="{ background: PLATFORM_DOT[o.platform || 'whatsapp'] }"
              ></span>
              <span class="flex min-w-0 flex-1 flex-col">
                <span class="truncate text-xs text-[#86868B]"
                  >{{ accountName(o.accountId) || plat(o).label }} ·
                  {{ md(o.lastMessageTime) }}</span
                >
                <span class="truncate text-[13px] text-[#1D1D1F]">{{
                  o.lastMessagePreview
                }}</span>
              </span>
              <span
                v-if="o.status === 'resolved'"
                class="shrink-0 text-[11px] text-[#86868B]"
                >已解决</span
              >
            </button>
          </div>

          <div class="flex-1"></div>
          <div class="flex gap-2">
            <Button
              class="flex-1"
              @click="router.push(`/crm/customer/detail/${current.customerId}`)"
              >客户详情</Button
            >
            <Button
              class="flex-1 !border-[#1D1D1F] !bg-[#1D1D1F] !text-white"
              @click="openFollow"
              >写跟进</Button
            >
          </div>
        </template>
      </aside>
    </div>

    <Modal
      v-model:open="followOpen"
      title="写跟进"
      ok-text="保存"
      :confirm-loading="followSaving"
      @ok="saveFollow"
    >
      <div class="flex flex-col gap-3">
        <Textarea
          v-model:value="followForm.content"
          :auto-size="{ minRows: 4, maxRows: 8 }"
          :maxlength="2000"
          placeholder="这次聊了什么、客户的反馈、下一步要做什么"
        />
        <div class="flex flex-wrap items-center gap-2 text-sm">
          <span class="text-[#6E6E73]">下次联系</span>
          <Segmented
            v-model:value="followForm.days"
            :options="FOLLOW_DAYS"
            size="small"
          />
        </div>
      </div>
    </Modal>

    <Modal
      v-model:open="remarkOpen"
      title="设置备注名"
      ok-text="保存"
      @ok="saveRemark"
    >
      <Input
        v-model:value="remarkText"
        :maxlength="100"
        allow-clear
        placeholder="例如：Rashid（迪拜灯具批发商），留空表示清除"
      />
    </Modal>
    <Modal
      v-model:open="transferOpen"
      title="转交给同事"
      ok-text="转交"
      @ok="doTransfer"
    >
      <div class="mb-2 text-sm text-gray-500">
        转交后由对方处理这个会话，你的列表里不再显示；AI 接待设置不变。
      </div>
      <Select
        v-model:value="transferTo"
        show-search
        option-filter-prop="label"
        class="w-full"
        placeholder="选择同事"
        :options="users.map((u) => ({ value: u.id, label: u.nickname }))"
      />
    </Modal>

    <Modal
      v-model:open="newOpen"
      title="新会话"
      ok-text="发送"
      :confirm-loading="newSending"
      :width="520"
      @ok="sendNewChat"
    >
      <div class="flex flex-col gap-3">
        <Select
          v-if="waAccounts.length > 1"
          v-model:value="newForm.accountId"
          :options="newAccountOptions"
          placeholder="用哪个号码发送"
          @change="(v: any) => onNewAccountChange(v)"
        />
        <Input
          v-model:value="newForm.to"
          placeholder="客户号码，含国家码，如 +8613800001111"
        />
        <Textarea
          v-model:value="newForm.text"
          :auto-size="{ minRows: 4, maxRows: 10 }"
          :maxlength="4096"
          placeholder="消息内容"
        />
        <div class="text-xs text-gray-400">
          扫码接入的号码可以直接发送普通消息。请勿短时间内群发陌生号码，以免号码被
          WhatsApp 限制。
        </div>
      </div>
    </Modal>

    <Modal
      v-model:open="tplOpen"
      title="发送模板消息"
      ok-text="发送"
      :width="560"
      @ok="sendTemplate"
    >
      <Spin :spinning="tplLoading">
        <div class="flex flex-col gap-3">
          <Input
            v-model:value="newTo"
            :disabled="!!current && newTo === current.waId"
            placeholder="客户号码，含国家码，如 +14155550100"
          />
          <Select
            v-model:value="tplKey"
            placeholder="选择已审核的模板"
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
          <Input
            v-for="(_, i) in tplParams"
            :key="i"
            v-model:value="tplParams[i]"
            :placeholder="`变量 {{${i + 1}}}`"
          />
          <div
            v-if="chosen"
            class="rounded bg-[#d9fdd3] p-3 text-sm whitespace-pre-wrap"
          >
            {{ templatePreview(chosen, tplParams) }}
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
}
.apple-im .im-list {
  width: 280px;
  background: #f7f7fa;
  border: 0;
  border-right: 0.5px solid #dedee5;
  border-radius: 0;
}
.apple-im .im-list h2 {
  font-size: 25px;
  line-height: 1.4;
}
.apple-im .im-contact {
  margin: 4px 8px;
  padding: 12px 10px;
  border: 0;
  border-radius: 12px;
  transition: background 0.2s;
}
.apple-im .im-selected {
  background: #007aff;
  color: white;
  box-shadow: none;
}
.apple-im .im-selected :deep(.text-\[\#86868B\]),
.apple-im .im-selected :deep(.text-\[\#6E6E73\]),
.apple-im .im-selected :deep(.text-\[\#1D1D1F\]) {
  color: white;
}
.apple-im .im-thread {
  border: 0;
  border-radius: 0;
  min-height: 0;
}
.apple-im .im-toolbar {
  margin: 12px 14px 0;
  padding: 10px 14px;
  border: 1px solid #ffffffb3;
  border-radius: 22px;
  background: #ffffffab;
  box-shadow: 0 6px 24px #00000009;
  backdrop-filter: blur(24px);
  flex-wrap: wrap;
  gap: 8px;
}
.apple-im .im-toolbar :deep(.ant-btn) {
  border-radius: 99px;
}
.apple-im .im-messages {
  min-height: 100px;
  padding: 24px;
  flex: 1;
}
.apple-im .cg-bubble {
  line-height: 1.5;
}
.apple-im .im-composer {
  flex-shrink: 0;
  max-height: 52%;
  overflow: auto;
  margin: 0 16px 16px;
  padding: 13px 15px;
  background: #ffffffb3;
  border: 1px solid #eeeef4;
  border-radius: 22px;
  box-shadow: 0 5px 25px #00000009;
  backdrop-filter: blur(24px);
}
.apple-im .im-composer :deep(.ant-input) {
  border: 0;
  background: transparent;
  box-shadow: none;
}
.apple-im .im-composer :deep(.ant-btn) {
  border-radius: 99px;
}
.apple-im .im-draft {
  border: 1px solid transparent;
  background:
    linear-gradient(white, white) padding-box,
    linear-gradient(120deg, #ff9500, #ff2d92, #af52de, #007aff) border-box;
  border-radius: 18px;
  box-shadow: 0 3px 18px #af52de12;
}
.apple-im .im-inspector {
  width: 274px;
  border: 0;
  border-left: 0.5px solid #e5e5ea;
  border-radius: 0;
  padding: 20px 17px;
  gap: 17px;
}
.apple-im .im-inspector > div:first-child {
  align-items: center;
  text-align: center;
}
.apple-im .im-inspector :deep(.ant-btn) {
  border-radius: 99px;
}
@media (width <= 1450px) {
  .apple-im .im-list {
    width: 250px;
  }
  .apple-im .im-inspector {
    width: 245px;
  }
  .apple-im .im-toolbar {
    padding: 8px 10px;
  }
}
@media (width <= 1100px) {
  .apple-im .im-inspector {
    display: none;
  }
  .apple-im .im-list {
    width: 245px;
  }
}
@media (width <= 760px) {
  .apple-im .im-list {
    width: 100%;
  }
  .apple-im .im-composer {
    margin: 0 8px 8px;
    padding: 10px;
  }
  .apple-im .im-messages {
    padding: 15px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .apple-im .im-contact {
    transition: none;
  }
}
</style>
