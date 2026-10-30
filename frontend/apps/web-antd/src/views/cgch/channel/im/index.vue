<script lang="ts" setup>
/**
 * 消息工作台（WhatsApp、Facebook 主页私信、Instagram 私信）：左侧会话列表，右侧聊天记录与输入框。
 * 客户 24 小时内发过消息才能发自由文本，窗口关闭后只能发已审核的模板。
 */
import { fmtDateTime } from '#/utils/datetime';
import type { ChannelWaApi } from '#/api/channel/whatsapp';
import type { ChannelAiApi } from '#/api/channel/ai';
import { loadTranslatePrefs, translateTexts } from '#/api/channel/translate';
import { closeDraft, DRAFT_REASON, draftWhatsApp, getPendingDraft } from '#/api/channel/ai';

import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import {
  Alert,
  Avatar,
  Badge,
  Button,
  Empty,
  Input,
  InputSearch,
  message,
  Modal,
  Dropdown,
  Menu,
  MenuItem,
  Popover,
  Segmented,
  Select,
  Space,
  Spin,
  Tag,
  Textarea,
  Tooltip,
} from 'ant-design-vue';

import { getSimpleUserList } from '#/api/system/user';
import { getQuickReplies, markQuickReplyUsed } from '#/api/channel/quick-reply';
import type { QuickReplyApi } from '#/api/channel/quick-reply';
import { onChannelRealtime } from '#/utils/channel-realtime';
import { requestClient } from '#/api/request';
import { levelLabel, stageLabel, stageOptions } from '#/views/cgch/v3/crm/components/shared';
import { countryName } from '#/views/cgch/crm/shared/countries';
import { getCustomerGroupList, getTagList, updateCustomerTags } from '#/api/crm/cgch';
import type { CgchCrmApi } from '#/api/crm/cgch';
import {
  archiveWaConversation,
  getWaAccountList,
  getWaConversation,
  getWaConversationPage,
  getWaMedia,
  getWaMessages,
  getWaTemplates,
  sendWaMedia,
  sendWaMessage,
  remarkWaConversation,
  starWaConversation,
  templateParamCount,
  transferWaConversation,
  templatePreview,
} from '#/api/channel/whatsapp';

defineOptions({ name: 'CgchChannelIm' });

/** 嵌入「会话」页时：账号由左栏选择，隐藏本页的账号下拉 */
const props = defineProps<{ accountId?: number; embedded?: boolean; level?: number; openId?: number }>();

const router = useRouter();
const route = useRoute();
const POLL_MS = 10_000; // 实时推送没连上时的定时刷新间隔
const POLL_MS_LIVE = 60_000; // 实时推送连上后只做兜底刷新

const accounts = ref<ChannelWaApi.Account[]>([]);
const accountId = ref<number | undefined>(props.accountId);
const keyword = ref('');
/** 列表筛选：全部、未读、未回复（客户最后发言）、收藏、已归档 */
type ListFilter = 'all' | 'archived' | 'starred' | 'team' | 'unread' | 'unreplied';
const listFilter = ref<ListFilter>('all');
const FILTER_OPTIONS = [
  { label: '全部', value: 'all' },
  { label: '未读', value: 'unread' },
  { label: '未回复', value: 'unreplied' },
  { label: '收藏', value: 'starred' },
  { label: '归档', value: 'archived' },
  { label: '团队', value: 'team' },
];
const live = ref(false);
const conversations = ref<ChannelWaApi.Conversation[]>([]);
const listLoading = ref(false);

const current = ref<ChannelWaApi.Conversation>();
const messages = ref<ChannelWaApi.Message[]>([]);
const chatLoading = ref(false);
const chatBox = ref<HTMLElement>();

/** 平台：WhatsApp 显示号码、可发模板；Facebook / Instagram 只能在客户来信后 24 小时内回复 */
const PLATFORM_META: Record<string, { avatar: string; color: string; label: string }> = {
  whatsapp: { label: 'WhatsApp', color: 'green', avatar: '!bg-green-600' },
  messenger: { label: 'Facebook', color: 'blue', avatar: '!bg-blue-600' },
  instagram: { label: 'Instagram', color: 'magenta', avatar: '!bg-pink-600' },
  web: { label: '官网IM', color: 'geekblue', avatar: '!bg-indigo-600' },
  youtube: { label: 'YouTube 评论', color: 'red', avatar: '!bg-red-600' },
  tiktok: { label: 'TikTok 评论', color: 'default', avatar: '!bg-gray-900' },
};
const plat = (c?: { platform?: string }) => PLATFORM_META[c?.platform || 'whatsapp'] ?? PLATFORM_META.whatsapp!;
const isWa = (c?: { platform?: string }) => !c?.platform || c.platform === 'whatsapp';
const waAccounts = computed(() => accounts.value.filter((a) => isWa(a)));
/** 扫码接入的号码（WhatsApp 网页版协议）没有模板和 24 小时窗口限制，可以直接发普通消息 */
const isScanAccount = (id?: number) => accounts.value.find((a) => a.id === id)?.authType === 'evolution';

const accountOptions = computed(() => [
  { label: '全部账号', value: undefined as any },
  ...accounts.value.map((a) => ({
    label: `${plat(a).label} · ${a.name || ''} ${a.displayPhone || a.phoneNumberId}`.trim(),
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
      team: listFilter.value === 'team' || undefined,
      level: props.level,
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
    messages.value = await getWaMessages(c.id);
    c.unreadCount = 0;
    current.value = await getWaConversation(c.id);
    aiDraft.value = (await getPendingDraft('whatsapp', c.id)) ?? undefined;
    await scrollBottom();
  } finally {
    chatLoading.value = false;
  }
}

async function refreshCurrent() {
  if (!current.value) return;
  const latest = await getWaMessages(current.value.id);
  const grew = latest.length !== messages.value.length || latest.at(-1)?.status !== messages.value.at(-1)?.status;
  messages.value = latest;
  current.value = await getWaConversation(current.value.id);
  if (grew) await scrollBottom();
}

watch([accountId, listFilter, () => props.level], () => loadConversations());
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
  timer = setTimeout(() => {
    if (!document.hidden) {
      loadConversations(true);
      refreshCurrent();
    }
    schedulePoll();
  }, live.value ? POLL_MS_LIVE : POLL_MS);
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
  stageOptions().map((o) => ({ label: String(o.label), value: Number(o.value), done: (customerStage.value ?? 0) >= Number(o.value), on: customerStage.value === Number(o.value) })),
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
      const c = await requestClient.get<PanelCustomer & { groupId?: number; stage?: number; tagIds?: number[] }>(`/crm/customer/get?id=${id}`);
      if (current.value?.customerId !== id) return;
      customer.value = c ?? undefined;
      customerStage.value = c?.stage;
      customerTags.value = c?.tagIds ?? [];
      customerGroup.value = c?.groupId ?? undefined;
      if (!tagOptions.value.length) {
        [tagOptions.value, groupOptions.value] = await Promise.all([getTagList().catch(() => []), getCustomerGroupList().catch(() => [])]);
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
const followForm = ref<{ content: string; days: number }>({ content: '', days: 3 });
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
    customer.value = { ...customer.value, contactLastContent: followForm.value.content.trim(), contactLastTime: Date.now(), contactNextTime: next.getTime() };
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

// ===== 会话整理：收藏、归档、备注名、转交 =====
async function toggleStar(c: ChannelWaApi.Conversation) {
  await starWaConversation(c.id, !c.starred);
  c.starred = !c.starred;
  if (current.value?.id === c.id) current.value.starred = c.starred;
  await loadConversations(true);
}
async function toggleArchive(c: ChannelWaApi.Conversation) {
  await archiveWaConversation(c.id, !c.archived);
  message.success(c.archived ? '已移出归档' : '已归档，客户再发消息时会自动回到列表');
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
  quickList.value = await getQuickReplies(quickKeyword.value.trim() || undefined);
}
watch(quickOpen, (v) => v && loadQuick());
function useQuick(q: QuickReplyApi.Item) {
  const name = current.value ? current.value.remarkName || current.value.profileName || '' : '';
  const body = q.content.replaceAll('{name}', name).trim();
  text.value = text.value ? `${text.value.trimEnd()}\n${body}` : body;
  quickOpen.value = false;
  if (q.id) markQuickReplyUsed(q.id);
}

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
  const todo = messages.value.filter((m) => m.direction === 1 && m.textBody?.trim() && translations.value[m.id] === undefined).slice(-30);
  if (!todo.length) return;
  todo.forEach((m) => (translations.value[m.id] = ''));
  try {
    const out = await translateTexts(todo.map((m) => m.textBody!));
    todo.forEach((m, i) => {
      // 已经是中文的不重复显示
      translations.value[m.id] = out[i] && out[i]!.trim() !== m.textBody!.trim() ? out[i]! : '';
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
const customerSample = computed(() => [...messages.value].reverse().find((m) => m.direction === 1 && m.textBody?.trim())?.textBody ?? '');
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
      const [t] = await translateTexts([v], customerSample.value ? { sample: customerSample.value } : { target: 'English' });
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
    await sendWaMessage({ conversationId: current.value.id, text: text.value.trim() });
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
function onKeydown(e: KeyboardEvent) {
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
    if (m.type !== 'image' && m.type !== 'sticker') window.open(res.fileUrl, '_blank');
  } finally {
    mediaLoading.value = undefined;
  }
}
const isImage = (m: ChannelWaApi.Message) => (m.type === 'image' || m.type === 'sticker') && !!m.fileUrl;

const fileSending = ref(false);
function pickAndSend() {
  if (!current.value) return;
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = 'image/jpeg,image/png,video/mp4,audio/*,application/pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.zip';
  input.onchange = async () => {
    const file = input.files?.[0];
    if (!file || !current.value) return;
    const limit = file.type === 'image/jpeg' || file.type === 'image/png' ? 5 : 16;
    if (file.size > limit * 1024 * 1024) {
      message.warning(`文件过大：${limit === 5 ? '图片' : '该类文件'}不能超过 ${limit}MB`);
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
const newForm = ref({ accountId: undefined as number | undefined, to: '', text: '' });
const newAccountOptions = computed(() =>
  waAccounts.value.map((a) => ({
    value: a.id,
    label: `${a.displayPhone || a.name || a.phoneNumberId}${a.authType === 'evolution' ? '（扫码）' : '（官方 API）'}`,
  })),
);
function openNewChat() {
  const selected = accounts.value.find((a) => a.id === accountId.value);
  newForm.value = { accountId: selected && isWa(selected) ? selected.id : waAccounts.value[0]?.id, to: '', text: '' };
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
    const sent = await sendWaMessage({ accountId: f.accountId, to, text: f.text.trim() });
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
const approved = computed(() => templates.value.filter((t) => t.status === 'APPROVED'));
const chosen = computed(() => approved.value.find((t) => `${t.name}|${t.language}` === tplKey.value));
watch(chosen, (t) => {
  tplParams.value = Array.from({ length: templateParamCount(t) }, () => '');
});

async function openTemplate(newChat = false, forAccount?: number) {
  const selected = accounts.value.find((a) => a.id === accountId.value);
  const officialFirst = waAccounts.value.find((a) => a.authType !== 'evolution')?.id;
  const acc = newChat
    ? forAccount ?? (selected && isWa(selected) && !isScanAccount(selected.id) ? selected.id : officialFirst)
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
    ...(isNew ? { accountId: newAccountId.value, to: newTo.value } : { conversationId: current.value!.id }),
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
const initials = (c: ChannelWaApi.Conversation) => (c.profileName || c.waId).slice(0, 1).toUpperCase();
const contact = (c: ChannelWaApi.Conversation) =>
  c.profileName ||
  (isWa(c) ? `+${c.waId}` : c.platform === 'web' ? `官网访客 ${c.waId.slice(-4).toUpperCase()}` : `${plat(c).label} 用户`);
const hm = (t?: number | string) => fmtDateTime(t, 'HH:mm');
const md = (t?: number | string) => fmtDateTime(t, 'MM-DD');
</script>

<template>
  <Page auto-content-height>
    <div v-if="accounts.length === 0 && conversations.length === 0 && listFilter === 'all'" class="flex h-full items-center justify-center rounded bg-white">
      <Empty description="还没有绑定 WhatsApp、Facebook 或 Instagram 账号">
        <Button type="primary" @click="router.push('/v3/channel')">去绑定账号</Button>
      </Empty>
    </div>

    <div v-else class="flex h-full gap-3">
      <!-- 会话列表 -->
      <div class="flex w-80 shrink-0 flex-col overflow-hidden rounded-xl border border-solid border-[#E5E7EB] bg-white">
        <div class="flex flex-col gap-2 border-b p-3">
          <div class="flex gap-2">
            <Select v-if="!embedded" v-model:value="accountId" :options="accountOptions" class="min-w-0 flex-1" />
            <span v-else class="flex-1"></span>
            <Button v-if="waAccounts.length > 0" type="primary" @click="openNewChat">新会话</Button>
          </div>
          <InputSearch v-model:value="keyword" allow-clear placeholder="号码或昵称" @search="() => loadConversations()" />
          <Segmented v-model:value="listFilter" :options="FILTER_OPTIONS" block size="small" />
        </div>
        <div class="flex-1 overflow-auto">
          <Spin :spinning="listLoading">
            <div v-if="conversations.length === 0" class="p-8">
              <Empty :description="listFilter === 'team' ? '没有同事负责的会话（需要部门数据权限才能看到下属的会话）' : '暂无会话'" />
            </div>
            <div
              v-for="c in conversations"
              :key="c.id"
              class="flex cursor-pointer gap-3 border-0 border-b border-solid border-[#F3F4F6] px-4 py-3"
              :class="current?.id === c.id ? 'bg-[#EFF4FF] shadow-[inset_3px_0_0_#2563EB]' : 'hover:bg-[#F9FAFB]'"
              @click="openConversation(c)"
            >
              <Badge :count="c.unreadCount" :offset="[-4, 4]">
                <Avatar :class="plat(c).avatar">{{ initials(c) }}</Avatar>
              </Badge>
              <div class="min-w-0 flex-1">
                <div class="flex justify-between gap-2">
                  <span class="truncate font-semibold text-[#111827]">
                    <span v-if="c.starred" class="mr-1 text-amber-500">★</span>{{ c.remarkName || c.customerName || contact(c) }}
                  </span>
                  <span class="shrink-0 text-xs text-[#6B7280]">{{ md(c.lastMessageTime) }} {{ hm(c.lastMessageTime) }}</span>
                </div>
                <div class="mt-0.5 truncate text-[13px] text-[#4B5563]">
                  <span v-if="listFilter === 'team' && c.handlerName" class="mr-1 text-[#2563EB]">[{{ c.handlerName }}]</span>{{ c.lastMessagePreview }}
                </div>
              </div>
            </div>
          </Spin>
        </div>
      </div>

      <!-- 聊天区 -->
      <div class="flex min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-solid border-[#E5E7EB] bg-white">
        <div v-if="!current" class="flex h-full items-center justify-center"><Empty description="选择一个会话" /></div>
        <template v-else>
          <div class="flex items-center justify-between border-0 border-b border-solid border-[#E5E7EB] px-5 py-3">
            <div>
              <div class="text-[15px] font-semibold">
                <span v-if="current.starred" class="mr-1 text-amber-500">★</span>{{ current.remarkName || contact(current) }}
                <span v-if="current.remarkName" class="text-sm text-gray-400">（{{ contact(current) }}）</span>
                <span v-if="isWa(current)" class="text-sm text-gray-400">+{{ current.waId }}</span>
                <Tag v-else :color="plat(current).color" class="ml-1">{{ plat(current).label }}</Tag>
              </div>
              <div class="mt-1 flex gap-2">
                <Tag v-if="current.customerName" color="blue" class="cursor-pointer" @click="router.push(`/crm/customer/detail/${current.customerId}`)">
                  客户：{{ current.customerName }}
                </Tag>
                <Tag v-else-if="current.clueName" color="cyan" class="cursor-pointer" @click="router.push(`/crm/clue/detail/${current.clueId}`)">
                  线索：{{ current.clueName }}
                </Tag>
                <Tag v-else>未关联客户</Tag>
                <!-- 窄屏没有右侧客户栏时，阶段和标签在这里改 -->
                <span v-if="current.customerId && customerStage !== undefined" class="contents xl:hidden">
                  <Select
                    :value="customerStage"
                    size="small"
                    class="w-24"
                    :options="stageOptions().map((o) => ({ label: o.label, value: o.value }))"
                    @change="(v: any) => changeStage(Number(v))"
                  />
                  <Tag v-for="t in customerTags.slice(0, 3)" :key="t" class="m-0">{{ tagName(t) || t }}</Tag>
                </span>
                <Tooltip v-if="current.windowOpen" :title="`窗口在 ${fmtDateTime(current.windowExpireTime)} 关闭`">
                  <Tag color="green">24 小时窗口内</Tag>
                </Tooltip>
                <Tag v-else color="orange">{{ isWa(current) ? '窗口已关闭，仅可发模板' : '窗口已关闭，等待客户来信' }}</Tag>
              </div>
            </div>
            <div class="flex items-center gap-2">
              <Tooltip :title="live ? '实时推送已连接，新消息会立即出现' : '实时推送未连接，每 10 秒自动刷新'">
                <span class="inline-block size-2 rounded-full" :class="live ? 'bg-green-500' : 'bg-gray-300'"></span>
              </Tooltip>
              <Button size="small" @click="toggleStar(current)">{{ current.starred ? '取消收藏' : '收藏' }}</Button>
              <Dropdown :trigger="['click']">
                <Button size="small">更多</Button>
                <template #overlay>
                  <Menu @click="(e: any) => onMenu(String(e.key))">
                    <MenuItem key="archive">{{ current.archived ? '移出归档' : '归档' }}</MenuItem>
                    <MenuItem key="remark">设置备注名</MenuItem>
                    <MenuItem key="transfer">转交给同事</MenuItem>
                  </Menu>
                </template>
              </Dropdown>
            </div>
          </div>

          <div ref="chatBox" class="flex-1 overflow-auto bg-[#F6F7F9] px-7 py-6">
            <Spin :spinning="chatLoading">
              <div v-for="m in messages" :key="m.id" class="mb-3 flex" :class="m.direction === 2 ? 'justify-end' : 'justify-start'">
                <div
                  class="max-w-[70%] px-3.5 py-2.5 text-sm leading-[22px]"
                  :class="
                    m.direction === 2
                      ? 'rounded-[14px_4px_14px_14px] bg-[#2563EB] text-white [&_a]:text-white [&_a]:underline'
                      : 'rounded-[4px_14px_14px_14px] border border-solid border-[#E5E7EB] bg-white text-[#111827]'
                  "
                >
                  <template v-if="MEDIA_LABEL[m.type]">
                    <a v-if="isImage(m)" :href="m.fileUrl" target="_blank" rel="noopener">
                      <img :src="m.fileUrl" class="mb-1 max-h-60 max-w-full rounded" alt="" />
                    </a>
                    <div v-else class="mb-1 flex items-center gap-2 text-xs" :class="m.direction === 2 ? 'text-white/85' : 'text-[#4B5563]'">
                      <span>[{{ MEDIA_LABEL[m.type] }}{{ m.fileName ? `：${m.fileName}` : '' }}]</span>
                      <a v-if="m.fileUrl" :href="m.fileUrl" target="_blank" rel="noopener">下载</a>
                      <Button
                        v-else-if="m.mediaId || m.direction === 1"
                        size="small"
                        type="link"
                        class="!h-auto !p-0 !text-xs"
                        :loading="mediaLoading === m.id"
                        @click="loadMedia(m)"
                      >
                        {{ m.type === 'image' || m.type === 'sticker' ? '查看' : '下载' }}
                      </Button>
                    </div>
                  </template>
                  <div v-if="m.type === 'template'" class="mb-1 text-xs" :class="m.direction === 2 ? 'text-white/85' : 'text-[#4B5563]'">[模板 {{ m.templateName }}]</div>
                  <div class="whitespace-pre-wrap break-words">{{ m.textBody }}</div>
                  <div
                    v-if="translations[m.id]"
                    class="mt-1.5 whitespace-pre-wrap break-words border-0 border-t border-dashed pt-1.5 text-[13px]"
                    :class="m.direction === 2 ? 'border-white/40 text-white/90' : 'border-[#D1D5DB] text-[#4B5563]'"
                  >
                    {{ translations[m.id] }}
                  </div>
                  <div class="mt-1 flex items-center justify-end gap-1 text-[11px]" :class="m.direction === 2 ? 'text-white/80' : 'text-[#6B7280]'">
                    <span v-if="m.aiGenerated" class="rounded px-1 font-semibold" :class="m.direction === 2 ? 'bg-white/20' : 'bg-[#EFF4FF] text-[#1D4ED8]'">AI</span>
                    <span>{{ md(m.messageTime) }} {{ hm(m.messageTime) }}</span>
                    <Tooltip v-if="m.direction === 2 && m.status" :title="m.errorMessage || STATUS_ICON[m.status]?.label">
                      <span :class="m.status === 'failed' ? 'rounded bg-white px-1 font-bold text-[#B91C1C]' : m.status === 'read' ? 'font-semibold' : ''">
                        {{ STATUS_ICON[m.status]?.text }}
                      </span>
                    </Tooltip>
                  </div>
                </div>
              </div>
            </Spin>
          </div>

          <div class="border-0 border-t border-solid border-[#E5E7EB] px-5 pb-4 pt-3">
            <div v-if="aiDraft" class="mb-3 rounded-xl border border-solid border-[#BFDBFE] bg-white p-3.5 text-sm">
              <div class="mb-1.5 text-xs font-semibold text-[#1D4ED8]">
                <IconifyIcon icon="lucide:sparkles" class="mr-1 inline size-3.5 align-[-2px]" />
                AI 已准备回复，未自动发出：{{ DRAFT_REASON[aiDraft.reason || ''] ?? aiDraft.reason }}
                <span v-if="aiDraft.reasonDetail">（{{ aiDraft.reasonDetail }}）</span>
              </div>
              <div v-if="aiDraft.content" class="line-clamp-4 whitespace-pre-wrap leading-[22px] text-[#111827]">{{ aiDraft.content }}</div>
              <div class="mt-2.5 flex justify-end gap-2">
                <Button size="small" @click="discardDraft">不用</Button>
                <Button v-if="aiDraft.content && current.windowOpen" size="small" type="primary" @click="useDraft">填入输入框修改后发送</Button>
              </div>
            </div>
            <template v-if="current.windowOpen">
              <Textarea
                v-model:value="text"
                :auto-size="{ minRows: 2, maxRows: 6 }"
                :maxlength="4096"
                placeholder="输入消息，Enter 发送，Shift+Enter 换行；发送文件时这里的文字会作为说明"
                @keydown="onKeydown"
                @change="onInputChange"
              />
              <div v-if="tPrefs.input && (inputTranslation || inputTranslating)" class="mt-2 rounded bg-gray-50 px-3 py-2 text-sm">
                <div class="mb-1 flex items-center justify-between text-xs text-gray-500">
                  <span>{{ inputTranslating ? '正在翻译…' : '译文（客户的语言）' }}</span>
                  <a v-if="inputTranslation" @click="useTranslation">用译文替换</a>
                </div>
                <div class="whitespace-pre-wrap text-gray-700">{{ inputTranslation }}</div>
              </div>
              <div class="mt-2 flex justify-between">
                <Space>
                  <Button :loading="fileSending" @click="pickAndSend">发送文件</Button>
                  <Popover v-model:open="quickOpen" trigger="click" placement="topLeft">
                    <template #content>
                      <div class="w-80">
                        <InputSearch v-model:value="quickKeyword" allow-clear size="small" placeholder="搜索快捷回复" @search="loadQuick" />
                        <div class="mt-2 max-h-72 overflow-auto">
                          <Empty v-if="quickList.length === 0" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="还没有快捷回复，可在「设置 → 快捷回复」添加" />
                          <div
                            v-for="q in quickList"
                            :key="q.id"
                            class="cursor-pointer rounded px-2 py-1.5 hover:bg-gray-50"
                            @click="useQuick(q)"
                          >
                            <div class="text-sm font-medium">
                              {{ q.title }}<Tag v-if="q.scope === 'personal'" class="ml-1" color="default">个人</Tag>
                            </div>
                            <div class="truncate text-xs text-gray-500">{{ q.content }}</div>
                          </div>
                        </div>
                      </div>
                    </template>
                    <Button>快捷回复</Button>
                  </Popover>
                  <Button v-if="isWa(current) && !isScanAccount(current.accountId)" @click="openTemplate()">发送模板</Button>
                  <Button :loading="aiLoading" @click="aiCompose">AI 起草</Button>
                </Space>
                <Button type="primary" :loading="sending" :disabled="!text.trim()" @click="sendText">发送</Button>
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
              <template v-if="isWa(current)" #action><Button type="primary" size="small" @click="openTemplate()">发送模板</Button></template>
            </Alert>
          </div>
        </template>
      </div>

      <!-- 客户信息栏（设计稿「会话工作台」右侧）：关联了客户时显示，窄屏隐藏 -->
      <aside
        v-if="current?.customerId && customerStage !== undefined"
        class="hidden w-80 shrink-0 flex-col gap-5 overflow-auto rounded-xl border border-solid border-[#E5E7EB] bg-white p-5 xl:flex"
      >
        <div class="flex flex-col gap-1">
          <div class="text-xs text-[#6B7280]">客户</div>
          <a class="text-base font-semibold text-[#111827]" @click="router.push(`/crm/customer/detail/${current.customerId}`)">{{ customer?.name || current.customerName }}</a>
          <div class="mt-1 flex flex-wrap gap-1.5">
            <span v-if="customer?.level" class="rounded bg-[#FEF3C7] px-2 py-0.5 text-xs font-medium text-[#92400E]">{{ levelLabel(customer.level) }}</span>
            <span v-if="customer?.countryCode" class="rounded bg-[#F3F4F6] px-2 py-0.5 text-xs text-[#374151]">{{ countryName(customer.countryCode) || customer.countryCode }}</span>
            <span v-for="t in customerTags" :key="t" class="rounded bg-[#F3F4F6] px-2 py-0.5 text-xs text-[#374151]">{{ tagName(t) || t }}</span>
            <Popover trigger="click" placement="bottomRight" :open="tagEditOpen" @open-change="openTagEdit">
              <template #content>
                <div class="flex w-72 flex-col gap-2">
                  <div class="text-xs text-gray-500">客户标签</div>
                  <Select
                    v-model:value="tagDraft"
                    mode="multiple"
                    size="small"
                    placeholder="选择标签"
                    :options="tagOptions.map((t) => ({ label: t.name, value: t.id }))"
                    option-filter-prop="label"
                  />
                  <div class="text-xs text-gray-500">客户分组</div>
                  <Select
                    v-model:value="groupDraft"
                    size="small"
                    allow-clear
                    placeholder="不分组"
                    :options="groupOptions.map((g) => ({ label: g.name, value: g.id }))"
                  />
                  <div class="text-right"><Button size="small" type="primary" @click="saveTags">保存</Button></div>
                </div>
              </template>
              <button type="button" class="cursor-pointer rounded border border-dashed border-[#D1D5DB] bg-white px-2 py-0.5 text-xs text-[#4B5563]">+ 标签</button>
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
              <span class="block h-1.5 w-full rounded-full" :class="s.done ? 'bg-[#2563EB]' : 'bg-[#E5E7EB]'"></span>
              <span class="text-[11px]" :class="s.on ? 'font-semibold text-[#2563EB]' : 'text-[#6B7280]'">{{ s.label }}</span>
            </button>
          </div>
        </div>

        <dl class="m-0 grid grid-cols-[72px_1fr] gap-x-3 gap-y-2.5 text-[13px]">
          <template v-if="customer?.companyName">
            <dt class="text-[#6B7280]">公司</dt><dd class="m-0 break-words">{{ customer.companyName }}</dd>
          </template>
          <template v-if="customer?.email">
            <dt class="text-[#6B7280]">邮箱</dt><dd class="m-0 break-all">{{ customer.email }}</dd>
          </template>
          <template v-if="customer?.mobile">
            <dt class="text-[#6B7280]">电话</dt><dd class="m-0">{{ customer.mobile }}</dd>
          </template>
          <dt class="text-[#6B7280]">下次联系</dt>
          <dd class="m-0" :class="customer?.contactNextTime ? 'font-medium text-[#B45309]' : 'text-[#6B7280]'">{{ fmtDateTime(customer?.contactNextTime, 'MM-DD HH:mm', '未约') }}</dd>
          <dt class="text-[#6B7280]">负责人</dt><dd class="m-0">{{ customer?.ownerUserName || '-' }}</dd>
        </dl>

        <div class="flex flex-col gap-2 rounded-[10px] bg-[#F9FAFB] p-3">
          <div class="flex items-center justify-between">
            <span class="text-[13px] font-semibold">最近跟进</span>
            <a class="text-xs" @click="router.push(`/crm/customer/detail/${current.customerId}`)">全部记录</a>
          </div>
          <div class="text-[13px] leading-5 text-[#374151]">{{ customer?.contactLastContent || '还没有跟进记录' }}</div>
          <div v-if="customer?.contactLastTime" class="text-xs text-[#6B7280]">{{ fmtDateTime(customer.contactLastTime, 'MM-DD HH:mm') }}</div>
        </div>

        <div class="flex-1"></div>
        <div class="flex gap-2">
          <Button class="flex-1" @click="router.push(`/crm/customer/detail/${current.customerId}`)">客户详情</Button>
          <Button class="flex-1 !border-[#111827] !bg-[#111827] !text-white" @click="openFollow">写跟进</Button>
        </div>
      </aside>
    </div>

    <Modal v-model:open="followOpen" title="写跟进" ok-text="保存" :confirm-loading="followSaving" @ok="saveFollow">
      <div class="flex flex-col gap-3">
        <Textarea v-model:value="followForm.content" :auto-size="{ minRows: 4, maxRows: 8 }" :maxlength="2000" placeholder="这次聊了什么、客户的反馈、下一步要做什么" />
        <div class="flex flex-wrap items-center gap-2 text-sm">
          <span class="text-[#4B5563]">下次联系</span>
          <Segmented v-model:value="followForm.days" :options="FOLLOW_DAYS" size="small" />
        </div>
      </div>
    </Modal>

    <Modal v-model:open="remarkOpen" title="设置备注名" ok-text="保存" @ok="saveRemark">
      <Input v-model:value="remarkText" :maxlength="100" allow-clear placeholder="例如：Rashid（迪拜灯具批发商），留空表示清除" />
    </Modal>
    <Modal v-model:open="transferOpen" title="转交给同事" ok-text="转交" @ok="doTransfer">
      <div class="mb-2 text-sm text-gray-500">转交后由对方处理这个会话，你的列表里不再显示；AI 接待设置不变。</div>
      <Select
        v-model:value="transferTo"
        show-search
        option-filter-prop="label"
        class="w-full"
        placeholder="选择同事"
        :options="users.map((u) => ({ value: u.id, label: u.nickname }))"
      />
    </Modal>

    <Modal v-model:open="newOpen" title="新会话" ok-text="发送" :confirm-loading="newSending" :width="520" @ok="sendNewChat">
      <div class="flex flex-col gap-3">
        <Select
          v-if="waAccounts.length > 1"
          v-model:value="newForm.accountId"
          :options="newAccountOptions"
          placeholder="用哪个号码发送"
          @change="(v: any) => onNewAccountChange(v)"
        />
        <Input v-model:value="newForm.to" placeholder="客户号码，含国家码，如 +8613800001111" />
        <Textarea v-model:value="newForm.text" :auto-size="{ minRows: 4, maxRows: 10 }" :maxlength="4096" placeholder="消息内容" />
        <div class="text-xs text-gray-400">扫码接入的号码可以直接发送普通消息。请勿短时间内群发陌生号码，以免号码被 WhatsApp 限制。</div>
      </div>
    </Modal>

    <Modal v-model:open="tplOpen" title="发送模板消息" ok-text="发送" :width="560" @ok="sendTemplate">
      <Spin :spinning="tplLoading">
        <div class="flex flex-col gap-3">
          <Input v-model:value="newTo" :disabled="!!current && newTo === current.waId" placeholder="客户号码，含国家码，如 +14155550100" />
          <Select
            v-model:value="tplKey"
            placeholder="选择已审核的模板"
            :options="approved.map((t) => ({ label: `${t.name}（${t.language}）`, value: `${t.name}|${t.language}` }))"
            :not-found-content="tplLoading ? '加载中…' : '没有已审核的模板，请先在 Meta 后台创建'"
          />
          <Input v-for="(_, i) in tplParams" :key="i" v-model:value="tplParams[i]" :placeholder="`变量 {{${i + 1}}}`" />
          <div v-if="chosen" class="rounded bg-[#d9fdd3] p-3 text-sm whitespace-pre-wrap">{{ templatePreview(chosen, tplParams) }}</div>
        </div>
      </Spin>
    </Modal>
  </Page>
</template>
