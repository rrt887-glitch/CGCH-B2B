<script lang="ts" setup>
/**
 * 邮件工作台：左侧邮箱与文件夹，中间邮件列表，右侧正文、往来记录与回复。
 * 邮件 HTML 放在禁用脚本的 iframe 里展示，避免来信中的脚本执行。
 */
import { fmtDateTime } from '#/utils/datetime';
import type { ChannelMailApi } from '#/api/channel/mail';
import type { ChannelAiApi } from '#/api/channel/ai';
import type { MailTemplateApi } from '#/api/channel/mail-template';
import { getMailTemplates } from '#/api/channel/mail-template';
import { Tinymce } from '#/components/tinymce';
import {
  closeDraft,
  draftMail,
  DRAFT_REASON,
  getPendingDraft,
} from '#/api/channel/ai';

import { createMailDraftStore } from './mail-drafts';

import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import {
  Badge,
  Button,
  Empty,
  Input,
  message,
  Modal,
  Pagination,
  Popconfirm,
  Select,
  Spin,
  Tag,
  Textarea,
  Tooltip,
} from 'ant-design-vue';

import {
  formatSize,
  getMail,
  getMailAccountList,
  getMailPage,
  getMailThread,
  sendMail,
  syncMailAccount,
  trashMail,
  uploadMailAttachment,
} from '#/api/channel/mail';

defineOptions({ name: 'CgchChannelMail' });

/** 嵌入「会话」页时：邮箱由左栏选择，隐藏本页的邮箱下拉 */
const props = defineProps<{
  accountId?: number;
  embedded?: boolean;
  openId?: number;
}>();

const router = useRouter();
const route = useRoute();

const VIEWS: { key: ChannelMailApi.View; label: string }[] = [
  { key: 'inbox', label: '收件箱' },
  { key: 'unreplied', label: '未回复' },
  { key: 'sent', label: '已发送' },
  { key: 'trash', label: '已删除' },
];

const accounts = ref<ChannelMailApi.Account[]>([]);
const accountId = ref<number | undefined>(props.accountId); // undefined = 全部邮箱
watch(
  () => props.accountId,
  (v) => {
    accountId.value = v;
  },
);
const view = ref<ChannelMailApi.View>('inbox');
const keyword = ref('');
const pageNo = ref(1);
const pageSize = 20;
const total = ref(0);
const list = ref<ChannelMailApi.Message[]>([]);
const listLoading = ref(false);

const current = ref<ChannelMailApi.Message>();
const thread = ref<ChannelMailApi.Message[]>([]);
const detailLoading = ref(false);

const accountOptions = computed(() => [
  { label: '全部邮箱', value: undefined as any },
  ...accounts.value.map((a) => ({ label: a.email, value: a.id })),
]);
const unrepliedTotal = computed(() =>
  accounts.value.reduce((n, a) => n + (a.unrepliedCount ?? 0), 0),
);

const accountsLoading = ref(true);

async function loadAccounts() {
  accountsLoading.value = true;
  try {
    accounts.value = await getMailAccountList();
  } finally {
    accountsLoading.value = false;
  }
}

async function loadList() {
  listLoading.value = true;
  try {
    const res = await getMailPage({
      pageNo: pageNo.value,
      pageSize,
      accountId: accountId.value,
      view: view.value,
      keyword: keyword.value || undefined,
    });
    list.value = res.list;
    total.value = res.total;
  } finally {
    listLoading.value = false;
  }
}

watch([accountId, view], () => {
  pageNo.value = 1;
  if (current.value && hasReplyContent()) {
    stashReply(current.value.id);
    message.info('这封邮件的回复已暂存，重新打开可以接着写');
  }
  current.value = undefined;
  loadList();
});

async function open(item: ChannelMailApi.Message) {
  // 同一封邮件只是刷新详情时，保留正在编辑的内容，不走暂存/恢复
  const sameMail = current.value?.id === item.id;
  // 切到别的邮件前，先把当前这封没发出去的回复存起来，重开时能接着写
  if (current.value && !sameMail) stashReply(current.value.id);
  detailLoading.value = true;
  try {
    const [detail, history] = await Promise.all([
      getMail(item.id),
      getMailThread(item.id),
    ]);
    current.value = detail;
    thread.value = history;
    item.readStatus = true;
    if (!sameMail) restoreReply(detail.id);
    // 已经填过内容（或已经采用过草稿）就不再弹 AI 草稿卡片，避免重复
    aiDraft.value =
      detail.direction === 1 && !replyText.value.trim()
        ? ((await getPendingDraft('mail', detail.id)) ?? undefined)
        : undefined;
  } finally {
    detailLoading.value = false;
  }
}

const syncing = ref(false);
async function syncAll() {
  syncing.value = true;
  try {
    const ids = accountId.value
      ? [accountId.value]
      : accounts.value.map((a) => a.id!);
    let n = 0;
    for (const id of ids) n += await syncMailAccount(id);
    message.success(n > 0 ? `收到 ${n} 封新邮件` : '没有新邮件');
    await Promise.all([loadAccounts(), loadList()]);
  } finally {
    syncing.value = false;
  }
}

async function trash() {
  if (!current.value) return;
  await trashMail(current.value.id);
  message.success('已移到已删除');
  current.value = undefined;
  await loadList();
}

// ===== 附件（回复与写邮件共用） =====
const MAX_ATTACHMENT = 25 * 1024 * 1024;
/** 上传中的附件数量：用计数而不是布尔，两个附件任务重叠时不会提前变成“已完成” */
const uploadingCount = ref(0);
const uploading = computed(() => uploadingCount.value > 0);
async function pickFiles(target: ChannelMailApi.Attachment[]) {
  const input = document.createElement('input');
  input.type = 'file';
  input.multiple = true;
  input.onchange = async () => {
    const files = [...(input.files ?? [])];
    const tooBig = files.filter((f) => f.size > MAX_ATTACHMENT);
    if (tooBig.length > 0)
      message.warning(
        `单个附件不能超过 25MB：${tooBig.map((f) => f.name).join('、')}`,
      );
    uploadingCount.value++;
    try {
      for (const f of files.filter((x) => x.size <= MAX_ATTACHMENT))
        target.push(await uploadMailAttachment(f));
    } finally {
      uploadingCount.value--;
    }
  };
  input.click();
}
function removeFile(target: ChannelMailApi.Attachment[], id: number) {
  const i = target.findIndex((a) => a.id === id);
  if (i !== -1) target.splice(i, 1);
}

// ===== AI 起草 =====
const aiDraft = ref<ChannelAiApi.Draft>();
const aiLoading = ref(false);
const usedDraftId = ref<number>();
async function aiCompose() {
  if (!current.value) return;
  aiLoading.value = true;
  try {
    const d = await draftMail(current.value.id);
    if (d.reason === 'handoff') {
      aiDraft.value = d;
      message.warning('AI 判断这封邮件需要人工处理');
      return;
    }
    replyText.value = d.content;
    usedDraftId.value = d.id;
    aiDraft.value = undefined;
  } finally {
    aiLoading.value = false;
  }
}
function useDraft() {
  if (!aiDraft.value) return;
  replyText.value = aiDraft.value.content;
  usedDraftId.value = aiDraft.value.id;
  aiDraft.value = undefined;
}
async function discardDraft() {
  if (!aiDraft.value) return;
  await closeDraft(aiDraft.value.id, false);
  aiDraft.value = undefined;
}

// ===== 回复 =====
const replyText = ref('');
const replySending = ref(false);
const replyFiles = ref<ChannelMailApi.Attachment[]>([]);
/** 每封邮件各自暂存未发送的回复（含该邮件已采用的 AI 草稿） */
const replyDrafts = createMailDraftStore<ChannelMailApi.Attachment>();
function resetReply() {
  replyText.value = '';
  replyFiles.value = [];
  usedDraftId.value = undefined;
}

/** 有没有还没发出去的回复内容 */
function hasReplyContent() {
  return !!replyText.value.trim() || replyFiles.value.length > 0;
}

/** 把当前回复框的内容存到这封邮件名下 */
function stashReply(mailId?: number) {
  if (!mailId) return;
  replyDrafts.stash(mailId, {
    text: replyText.value,
    files: replyFiles.value,
    usedDraftId: usedDraftId.value,
  });
}

/** 重新打开这封邮件时把之前写的回复放回来（AI 草稿归属也跟着这封邮件走） */
function restoreReply(mailId: number) {
  const d = replyDrafts.take(mailId);
  replyText.value = d.text;
  replyFiles.value = [...d.files];
  usedDraftId.value = d.usedDraftId;
}

/** 真正会丢内容的场景（离开页面、刷新、关标签）才打断用户 */
function confirmLeave() {
  return new Promise<boolean>((resolve) => {
    Modal.confirm({
      title: '这封邮件的回复还没发送',
      content: '离开后未发送的内容会丢失，确定要离开吗？',
      okText: '放弃回复',
      okButtonProps: { danger: true },
      cancelText: '继续编辑',
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    });
  });
}

function onBeforeUnload(e: BeforeUnloadEvent) {
  if (!hasReplyContent()) return;
  e.preventDefault();
  // 兼容旧浏览器
  e.returnValue = '';
}

onMounted(() => window.addEventListener('beforeunload', onBeforeUnload));
onUnmounted(() => window.removeEventListener('beforeunload', onBeforeUnload));

// 只在独立路由下注册：嵌入「会话」页时本组件不是路由组件，注册了也收不到离开事件
if (!props.embedded) {
  onBeforeRouteLeave(async () => {
    if (!hasReplyContent() || !current.value) return true;
    stashReply(current.value.id);
    return await confirmLeave();
  });
}
function quote(m: ChannelMailApi.Message) {
  const body = (m.textBody || '')
    .split('\n')
    .map((l) => `> ${l}`)
    .join('\n');
  return `\n\nOn ${m.sentTime}, ${m.fromName || m.fromAddress} wrote:\n${body}`;
}
async function reply() {
  const m = current.value;
  if (!m || !replyText.value.trim()) return;
  if (uploading.value) {
    message.warning('附件还在上传，请等上传完成再发送');
    return;
  }
  replySending.value = true;
  try {
    const subject = /^re:/i.test(m.subject || '')
      ? m.subject
      : `Re: ${m.subject || ''}`;
    await sendMail({
      accountId: m.accountId,
      to: [m.fromAddress!],
      subject,
      text: replyText.value + quote(m),
      replyToId: m.id,
      attachmentIds: replyFiles.value.map((a) => a.id),
    });
    // 只关闭这封邮件对应的草稿（usedDraftId 随邮件恢复，不会误标别人）
    if (usedDraftId.value) await closeDraft(usedDraftId.value, true);
    replyDrafts.clear(m.id);
    resetReply();
    message.success('已发送');
    await Promise.all([open(m), loadList(), loadAccounts()]);
  } finally {
    replySending.value = false;
  }
}

// ===== 写邮件 =====
const composeOpen = ref(false);
const compose = reactive({
  accountId: undefined as number | undefined,
  to: '',
  cc: '',
  subject: '',
  text: '',
  html: '',
});
const composeFiles = ref<ChannelMailApi.Attachment[]>([]);
/** 邮件模板（设置 → 邮件模板）；套用后正文改为富文本 */
const templates = ref<MailTemplateApi.Template[]>([]);
const templateId = ref<number>();
async function openCompose() {
  Object.assign(compose, {
    accountId: accountId.value ?? accounts.value[0]?.id,
    to: '',
    cc: '',
    subject: '',
    text: '',
    html: '',
  });
  composeFiles.value = [];
  templateId.value = undefined;
  composeOpen.value = true;
  templates.value = await getMailTemplates({ enabled: true }).catch(() => []);
  const def = templates.value.find(
    (t) => t.defaulted && t.category === 'promo',
  );
  if (def) applyTemplate(def.id!);
}
function applyTemplate(id?: number) {
  const t = templates.value.find((x) => x.id === id);
  templateId.value = t?.id;
  if (!t) {
    compose.html = '';
    return;
  }
  compose.subject = t.subject;
  compose.html = t.html;
}
const split = (s: string) =>
  s
    .split(/[,;\s]+/)
    .map((x) => x.trim())
    .filter(Boolean);
async function sendCompose() {
  if (!compose.accountId || !compose.to.trim()) {
    message.warning('请选择发件邮箱并填写收件人');
    return;
  }
  if (uploading.value) {
    message.warning('附件还在上传，请等上传完成再发送');
    return;
  }
  await sendMail({
    accountId: compose.accountId,
    to: split(compose.to),
    cc: split(compose.cc),
    subject: compose.subject,
    text: compose.html ? '' : compose.text,
    html: compose.html || undefined,
    attachmentIds: composeFiles.value.map((a) => a.id),
  });
  message.success('已发送');
  composeOpen.value = false;
  if (view.value === 'sent') await loadList();
}

const iframeDoc = (html: string) =>
  `<!doctype html><html><head><meta charset="utf-8"><base target="_blank"><style>body{font:14px/1.65 -apple-system,BlinkMacSystemFont,'PingFang SC',Segoe UI,Arial,sans-serif;margin:0;color:#1d1d1f;word-break:break-word}img{max-width:100%;border-radius:8px}</style></head><body>${html}</body></html>`;

const MAIL_AVATAR_GRADIENTS = [
  'linear-gradient(180deg, #7472E8 0%, #5856D6 100%)',
  'linear-gradient(180deg, #3395FF 0%, #007AFF 100%)',
  'linear-gradient(180deg, #FFAB2E 0%, #FF9500 100%)',
  'linear-gradient(180deg, #4CD964 0%, #28CD41 100%)',
  'linear-gradient(180deg, #C678F0 0%, #AF52DE 100%)',
  'linear-gradient(180deg, #2AD4CB 0%, #00B5AD 100%)',
  'linear-gradient(180deg, #FF6259 0%, #FF3B30 100%)',
];
function mailSenderLabel(m: ChannelMailApi.Message) {
  return m.direction === 2
    ? m.toAddresses || '收件人'
    : m.fromName || m.fromAddress || '发件人';
}
function mailInitial(m: ChannelMailApi.Message) {
  const s = mailSenderLabel(m).trim().replace(/^[<"'\s]+/, '');
  return (s.slice(0, 1) || 'M').toUpperCase();
}
function mailAvatarBg(m: ChannelMailApi.Message) {
  const seed = mailSenderLabel(m);
  let h = 0;
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) >>> 0;
  return MAIL_AVATAR_GRADIENTS[h % MAIL_AVATAR_GRADIENTS.length];
}

onMounted(async () => {
  await loadAccounts();
  await loadList();
  // 从工作报告等页面跳转过来时，直接打开指定邮件：/channel-msg/mail?id=123
  const id = props.openId || Number(route.query.id);
  if (id) await open({ id } as ChannelMailApi.Message);
});
</script>

<template>
  <Page auto-content-height :class="{ 'mail-design': embedded }">
    <!-- 账号列表未加载完之前不能判定“没有绑定邮箱”，否则已绑定邮箱的账号会先闪一下空态 -->
    <div
      v-if="accountsLoading"
      class="flex h-full items-center justify-center rounded bg-white"
      aria-busy="true"
      aria-live="polite"
    >
      <Spin tip="正在加载邮箱…" />
    </div>
    <div
      v-else-if="accounts.length === 0"
      class="flex h-full items-center justify-center rounded bg-white"
    >
      <Empty description="还没有绑定邮箱">
        <Button type="primary" @click="router.push('/v3/channel')"
          >去绑定邮箱</Button
        >
      </Empty>
    </div>

    <div
      v-else
      class="mail-panes flex h-full gap-3"
      :class="{ 'mail-has-current': !!current }"
    >
      <!-- 左：邮箱与文件夹 -->
      <div
        v-if="!embedded"
        class="mail-folders flex w-52 shrink-0 flex-col gap-3 rounded bg-white p-3"
      >
        <Button type="primary" block @click="openCompose">写邮件</Button>
        <Select
          v-if="!embedded"
          v-model:value="accountId"
          :options="accountOptions"
        />
        <div class="flex flex-col">
          <div
            v-for="v in VIEWS"
            :key="v.key"
            class="flex cursor-pointer items-center justify-between rounded px-3 py-2"
            :class="
              view === v.key
                ? 'bg-blue-50 font-medium text-blue-600'
                : 'hover:bg-gray-50'
            "
            @click="view = v.key"
          >
            <span>{{ v.label }}</span>
            <Badge
              v-if="v.key === 'unreplied' && unrepliedTotal"
              :count="unrepliedTotal"
            />
          </div>
        </div>
        <Button block :loading="syncing" @click="syncAll">立即收信</Button>
      </div>

      <!-- 中：邮件列表 -->
      <div class="mail-list flex w-80 shrink-0 flex-col bg-[#F6F6F9]">
        <div
          class="flex flex-col gap-2.5 border-0 border-b border-solid border-black/[0.05] px-3.5 pt-3.5 pb-2.5"
        >
          <div v-if="embedded" class="flex items-center justify-between gap-2">
            <h2 class="m-0 text-[19px] font-bold tracking-tight text-[#1D1D1F]">
              邮件
            </h2>
            <div class="flex items-center gap-1.5">
              <Tooltip title="同步收取新邮件">
                <button
                  type="button"
                  class="mail-head-btn mail-head-btn--ghost"
                  :disabled="syncing"
                  aria-label="收信"
                  @click="syncAll"
                >
                  <IconifyIcon
                    icon="lucide:refresh-cw"
                    class="size-3.5"
                    :class="{ 'animate-spin': syncing }"
                  />
                </button>
              </Tooltip>
              <Tooltip title="写新邮件">
                <button
                  type="button"
                  class="mail-head-btn mail-head-btn--primary"
                  aria-label="写邮件"
                  @click="openCompose"
                >
                  <IconifyIcon icon="lucide:square-pen" class="size-3.5" />
                  <span>写邮件</span>
                </button>
              </Tooltip>
            </div>
          </div>

          <!-- iOS 无边框圆角搜索栏 -->
          <div class="mail-search-box">
            <IconifyIcon icon="lucide:search" class="mail-search-box__icon" />
            <input
              v-model="keyword"
              type="search"
              class="mail-search-box__input"
              placeholder="搜索主题、发件人"
              aria-label="搜索邮件"
              @keydown.enter="
                pageNo = 1;
                loadList();
              "
              @search="
                pageNo = 1;
                loadList();
              "
            />
            <button
              v-if="keyword"
              type="button"
              class="mail-search-box__clear"
              aria-label="清空搜索"
              @click="
                keyword = '';
                pageNo = 1;
                loadList();
              "
            >
              <IconifyIcon icon="lucide:x" class="size-3" />
            </button>
          </div>

          <slot name="list-controls"></slot>

          <!-- 嵌入式文件夹切换条（替代突兀的下拉框） -->
          <div
            v-if="embedded"
            class="mail-folder-pills"
            role="tablist"
            aria-label="邮件文件夹"
          >
            <button
              v-for="v in VIEWS"
              :key="v.key"
              type="button"
              role="tab"
              :aria-selected="view === v.key"
              class="mail-folder-pill"
              :class="{ on: view === v.key }"
              @click="view = v.key"
            >
              <span>{{ v.label }}</span>
              <span
                v-if="v.key === 'unreplied' && unrepliedTotal"
                class="mail-folder-pill__count"
                >{{ unrepliedTotal }}</span
              >
            </button>
          </div>
        </div>

        <div class="flex-1 overflow-auto py-1.5">
          <Spin :spinning="listLoading">
            <div
              v-if="list.length === 0"
              class="flex flex-col items-center justify-center gap-2 px-6 py-14 text-center"
            >
              <div
                class="grid size-11 place-items-center rounded-2xl bg-black/[0.04] text-[#8E8E93]"
              >
                <IconifyIcon icon="lucide:mail" class="size-5" />
              </div>
              <div class="text-[13px] text-[#86868B]">暂无邮件</div>
            </div>
            <div
              v-for="item in list"
              :key="item.id"
              class="mail-row flex cursor-pointer items-start gap-3"
              :class="{ 'is-selected': current?.id === item.id }"
              @click="open(item)"
            >
              <span
                class="mail-avatar"
                :style="{ background: mailAvatarBg(item) }"
                aria-hidden="true"
                >{{ mailInitial(item) }}</span
              >
              <div class="w-full min-w-0 flex-1">
                <div class="flex items-center justify-between gap-2">
                  <span
                    class="flex min-w-0 items-center gap-1.5 truncate text-[13.5px] text-[#1D1D1F]"
                    :class="item.readStatus ? 'font-medium' : 'font-bold'"
                  >
                    <span
                      v-if="!item.readStatus"
                      class="size-2 shrink-0 rounded-full bg-[#007AFF]"
                    ></span>
                    <span class="truncate">{{
                      item.direction === 2
                        ? `发给 ${item.toAddresses}`
                        : item.fromName || item.fromAddress
                    }}</span>
                  </span>
                  <span class="shrink-0 text-[11px] tabular-nums text-[#8E8E93]">{{
                    fmtDateTime(item.sentTime, 'MM-DD HH:mm')
                  }}</span>
                </div>
                <div
                  class="mt-0.5 truncate text-[13px]"
                  :class="
                    item.readStatus
                      ? 'text-[#3A3A3C]'
                      : 'font-semibold text-[#1D1D1F]'
                  "
                >
                  {{ item.subject || '（无主题）' }}
                </div>
                <div class="mt-0.5 truncate text-[12px] text-[#86868B]">
                  {{ item.snippet }}
                </div>
                <div
                  v-if="
                    item.customerName ||
                    item.clueName ||
                    (item.direction === 1 && item.replied) ||
                    item.hasAttachments ||
                    item.aiGenerated
                  "
                  class="mt-1.5 flex flex-wrap items-center gap-1"
                >
                  <span
                    v-if="item.customerName"
                    class="rounded-full bg-[#E8F2FF] px-2 py-0.5 text-[10.5px] font-medium text-[#007AFF]"
                    >{{ item.customerName }}</span
                  >
                  <span
                    v-else-if="item.clueName"
                    class="rounded-full bg-[#E6FAF8] px-2 py-0.5 text-[10.5px] font-medium text-[#00827C]"
                    >线索 · {{ item.clueName }}</span
                  >
                  <span
                    v-if="item.direction === 1 && item.replied"
                    class="rounded-full bg-[#EAF9EE] px-2 py-0.5 text-[10.5px] font-medium text-[#248A3D]"
                    >已回复</span
                  >
                  <span
                    v-if="item.hasAttachments"
                    class="inline-flex items-center gap-0.5 rounded-full bg-black/[0.05] px-2 py-0.5 text-[10.5px] text-[#6E6E73]"
                  >
                    <IconifyIcon icon="lucide:paperclip" class="size-2.5" />附件
                  </span>
                  <span
                    v-if="item.aiGenerated"
                    class="rounded-full bg-[#F3EEFF] px-2 py-0.5 text-[10.5px] font-medium text-[#7C3AED]"
                    >AI 回复</span
                  >
                </div>
              </div>
            </div>
          </Spin>
        </div>
        <div
          v-if="total > pageSize"
          class="flex justify-center border-0 border-t border-solid border-black/[0.05] py-2"
        >
          <Pagination
            v-model:current="pageNo"
            :page-size="pageSize"
            :total="total"
            size="small"
            simple
            @change="loadList"
          />
        </div>
      </div>

      <!-- 右：正文与回复 -->
      <div class="mail-reader flex min-w-0 flex-1 flex-col bg-white">
        <Spin :spinning="detailLoading" class="h-full">
          <div
            v-if="!current"
            class="flex h-full flex-col items-center justify-center gap-3 bg-[#F5F5F8] p-10 text-center"
          >
            <div class="mail-empty-glyph">
              <IconifyIcon icon="lucide:mail-open" class="size-7" />
            </div>
            <div class="text-[16px] font-semibold text-[#1D1D1F]">
              选择一封邮件阅读
            </div>
            <div class="max-w-xs text-[13px] leading-relaxed text-[#86868B]">
              支持客户档案自动识别、历史往来聚合与 AI 智能起草外贸回信。
            </div>
          </div>
          <div v-else class="flex h-full flex-col bg-[#F5F5F8]">
            <div
              class="border-0 border-b border-solid border-black/[0.06] bg-white px-6 py-4"
            >
              <Button
                class="mail-mobile-back mb-2"
                type="text"
                @click="current = undefined"
                >‹ 返回邮件列表</Button
              >
              <div class="flex items-start justify-between gap-3">
                <h3 class="m-0 text-[18px] font-bold leading-snug tracking-tight text-[#1D1D1F]">
                  {{ current.subject || '（无主题）' }}
                </h3>
                <Popconfirm
                  v-if="current.folder !== 'TRASH'"
                  title="移到已删除？"
                  @confirm="trash"
                >
                  <button
                    type="button"
                    class="mail-trash-btn"
                    title="移到已删除"
                  >
                    <IconifyIcon icon="lucide:trash-2" class="size-3.5" />
                    <span>删除</span>
                  </button>
                </Popconfirm>
              </div>
              <div class="mt-3 flex items-center gap-3">
                <span
                  class="mail-avatar mail-avatar--lg"
                  :style="{ background: mailAvatarBg(current) }"
                  aria-hidden="true"
                  >{{ mailInitial(current) }}</span
                >
                <div class="min-w-0 flex-1">
                  <div class="truncate text-[13.5px] font-medium text-[#1D1D1F]">
                    {{ current.fromName || current.fromAddress }}
                    <span class="font-normal text-[#86868B]"
                      >&lt;{{ current.fromAddress }}&gt;</span
                    >
                  </div>
                  <div class="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-[12px] text-[#86868B]">
                    <span>收件人：{{ current.toAddresses }}</span>
                    <span v-if="current.ccAddresses"
                      >· 抄送：{{ current.ccAddresses }}</span
                    >
                    <span>· {{ fmtDateTime(current.sentTime) }}</span>
                    <button
                      v-if="current.customerName"
                      type="button"
                      class="mail-crm-chip"
                      @click="
                        router.push(`/crm/customer/detail/${current.customerId}`)
                      "
                    >
                      客户 · {{ current.customerName }}
                    </button>
                    <button
                      v-else-if="current.clueName"
                      type="button"
                      class="mail-crm-chip"
                      @click="router.push(`/crm/clue/detail/${current.clueId}`)"
                    >
                      线索 · {{ current.clueName }}
                    </button>
                  </div>
                </div>
              </div>
            </div>

            <div class="flex-1 overflow-auto px-6 py-5">
              <div class="rounded-2xl bg-white p-5 shadow-[0_1px_4px_rgba(0,0,0,0.04),0_0_0_0.5px_rgba(0,0,0,0.05)]">
                <iframe
                  v-if="current.htmlBody"
                  :srcdoc="iframeDoc(current.htmlBody)"
                  :title="`邮件正文：${current.subject || '无标题'}`"
                  sandbox="allow-popups allow-popups-to-escape-sandbox"
                  class="h-[420px] w-full border-0"
                ></iframe>
                <pre
                  v-else
                  class="m-0 whitespace-pre-wrap font-sans text-[14px] leading-relaxed text-[#1D1D1F]"
                  >{{ current.textBody }}</pre
                >

                <div
                  v-if="current.attachments?.length"
                  class="mt-4 flex flex-wrap gap-2 border-0 border-t border-solid border-black/[0.06] pt-3.5"
                >
                  <a
                    v-for="a in current.attachments"
                    :key="a.id"
                    :href="a.fileUrl"
                    target="_blank"
                    rel="noopener"
                    class="inline-flex items-center gap-2 rounded-xl bg-[#F5F5F7] px-3 py-2 text-[13px] font-medium text-[#1D1D1F] transition-colors hover:bg-[#EBEBF0]"
                  >
                    <IconifyIcon icon="lucide:paperclip" class="size-3.5 text-[#007AFF]" />
                    <span>{{ a.fileName }}</span>
                    <span class="text-xs font-normal text-[#86868B]">{{
                      formatSize(a.fileSize)
                    }}</span>
                  </a>
                </div>
              </div>

              <div v-if="thread.length > 1" class="mt-5">
                <div class="mb-2.5 px-1 text-[12px] font-semibold text-[#86868B]">
                  往来记录（{{ thread.length }} 封）
                </div>
                <div class="flex flex-col gap-2">
                  <div
                    v-for="t in thread"
                    :key="t.id"
                    class="rounded-xl bg-white px-4 py-3 text-sm shadow-[0_1px_2px_rgba(0,0,0,0.03),0_0_0_0.5px_rgba(0,0,0,0.05)]"
                  >
                    <div class="flex items-center justify-between text-xs text-[#86868B]">
                      <span
                        class="font-semibold"
                        :class="t.direction === 2 ? 'text-[#007AFF]' : 'text-[#1D1D1F]'"
                      >
                        {{
                          t.direction === 2 ? '我方回复' : t.fromName || t.fromAddress
                        }}
                      </span>
                      <span class="tabular-nums">{{ fmtDateTime(t.sentTime) }}</span>
                    </div>
                    <div class="mt-1 truncate text-[13px] text-[#424245]">
                      {{ t.snippet }}
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div
              v-if="current.direction === 1 && current.folder !== 'TRASH'"
              class="px-5 pb-4 pt-1"
            >
              <!-- AI 草稿悬浮紫边白卡（与 IM 保持完全一致的视觉语言） -->
              <div v-if="aiDraft" class="mail-ai-draft mb-2.5 p-3.5 text-sm">
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
                  <span v-if="aiDraft.reasonDetail" class="font-normal text-[#86868B]"
                    >（{{ aiDraft.reasonDetail }}）</span
                  >
                </div>
                <div
                  v-if="aiDraft.content"
                  class="line-clamp-3 whitespace-pre-wrap text-[13.5px] leading-[21px] text-[#1D1D1F]"
                >
                  {{ aiDraft.content }}
                </div>
                <div class="mt-2.5 flex gap-2">
                  <Button
                    v-if="aiDraft.content"
                    size="small"
                    type="primary"
                    shape="round"
                    @click="useDraft"
                    >填入回复框</Button
                  >
                  <Button size="small" shape="round" @click="discardDraft"
                    >忽略</Button
                  >
                </div>
              </div>

              <div class="mail-reply">
                <Textarea
                  v-model:value="replyText"
                  :auto-size="{ minRows: 3, maxRows: 7 }"
                  placeholder="输入回复内容，原邮件会自动引用在下方…"
                />
                <div v-if="replyFiles.length" class="mt-2 flex flex-wrap gap-1.5">
                  <Tag
                    v-for="a in replyFiles"
                    :key="a.id"
                    closable
                    class="!m-0 !rounded-full"
                    @close="removeFile(replyFiles, a.id)"
                  >
                    <IconifyIcon icon="lucide:paperclip" class="mr-1 inline size-3" />{{
                      a.fileName
                    }}
                    {{ formatSize(a.fileSize) }}
                  </Tag>
                </div>
                <div class="mt-2 flex items-center justify-between">
                  <div class="flex items-center gap-1.5">
                    <Tooltip title="添加附件">
                      <button
                        type="button"
                        class="mail-tool-btn"
                        :disabled="uploading"
                        @click="pickFiles(replyFiles)"
                      >
                        <IconifyIcon icon="lucide:paperclip" class="size-4" />
                        <span>附件</span>
                      </button>
                    </Tooltip>
                    <Tooltip title="AI 根据上下文自动起草回信">
                      <button
                        type="button"
                        class="mail-tool-btn mail-tool-btn--ai"
                        :disabled="aiLoading"
                        @click="aiCompose"
                      >
                        <IconifyIcon icon="lucide:sparkles" class="size-3.5" />
                        <span>{{ aiLoading ? '起草中…' : 'AI 起草' }}</span>
                      </button>
                    </Tooltip>
                  </div>
                  <Button
                    type="primary"
                    shape="round"
                    :loading="replySending"
                    :disabled="!replyText.trim() || uploading"
                    @click="reply"
                    >发送回复</Button
                  >
                </div>
              </div>
            </div>
          </div>
        </Spin>
      </div>
    </div>

    <Modal
      v-model:open="composeOpen"
      title="撰写新邮件"
      :width="680"
      ok-text="发送邮件"
      wrap-class-name="ap-modal-sheet"
      :ok-button-props="{ disabled: uploading, loading: uploading }"
      @ok="sendCompose"
    >
      <div class="ap-sheet-lead">
        <span class="ap-sheet-icon ap-sheet-icon--purple">
          <IconifyIcon icon="lucide:mail-plus" class="size-5" />
        </span>
        <div class="ap-sheet-subtitle">
          通过已绑定的企业邮箱向外贸客户发送开发信或商务邮件，支持一键套用邮件模板。
        </div>
      </div>
      <div class="ap-sheet-body">
        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div class="ap-sheet-field">
            <label class="ap-sheet-label">发件邮箱</label>
            <Select
              v-model:value="compose.accountId"
              :options="accounts.map((a) => ({ label: a.email, value: a.id }))"
              placeholder="选择发件邮箱"
            />
          </div>
          <div class="ap-sheet-field">
            <label class="ap-sheet-label">
              <span>邮件模板</span>
              <span class="ap-sheet-optional">选填</span>
            </label>
            <Select
              :value="templateId"
              allow-clear
              placeholder="不套用模板"
              :options="
                templates.map((t) => ({
                  label: `${t.name}${t.defaulted ? '（默认）' : ''}`,
                  value: t.id,
                }))
              "
              @change="(v: any) => applyTemplate(v)"
            />
          </div>
        </div>
        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div class="ap-sheet-field">
            <label class="ap-sheet-label">
              <span>收件人</span>
              <span class="ap-sheet-optional">多个用逗号分隔</span>
            </label>
            <Input
              v-model:value="compose.to"
              placeholder="customer@company.com"
            />
          </div>
          <div class="ap-sheet-field">
            <label class="ap-sheet-label">
              <span>抄送 CC</span>
              <span class="ap-sheet-optional">选填</span>
            </label>
            <Input v-model:value="compose.cc" placeholder="colleague@company.com" />
          </div>
        </div>
        <div class="ap-sheet-field">
          <label class="ap-sheet-label">邮件主题</label>
          <Input v-model:value="compose.subject" placeholder="输入清晰专业的邮件主题" />
        </div>
        <div class="ap-sheet-field">
          <label class="ap-sheet-label">邮件正文</label>
          <Tinymce
            v-if="compose.html || templateId"
            v-model="compose.html"
            :height="300"
          />
          <Textarea
            v-else
            v-model:value="compose.text"
            :rows="8"
            placeholder="输入邮件正文内容…"
          />
        </div>
        <div class="flex flex-wrap items-center gap-2 pt-1">
          <button
            type="button"
            class="mail-tool-btn"
            :disabled="uploading"
            @click="pickFiles(composeFiles)"
          >
            <IconifyIcon icon="lucide:paperclip" class="size-3.5" />
            <span>{{ uploading ? '上传中…' : '添加附件' }}</span>
          </button>
          <Tag
            v-for="a in composeFiles"
            :key="a.id"
            closable
            class="!m-0 !rounded-full"
            @close="removeFile(composeFiles, a.id)"
          >
            <IconifyIcon icon="lucide:paperclip" class="mr-1 inline size-3" />{{
              a.fileName
            }}
            {{ formatSize(a.fileSize) }}
          </Tag>
        </div>
      </div>
    </Modal>
  </Page>
</template>

<style scoped>
.mail-head-btn {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 28px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  border: 0;
  border-radius: 999px;
  transition:
    background-color var(--ap-dur-fast),
    transform var(--ap-dur-fast) var(--ap-ease);
}
.mail-head-btn:active {
  transform: scale(0.95);
}
.mail-head-btn--ghost {
  width: 28px;
  justify-content: center;
  padding: 0;
  color: #6e6e73;
  background: rgb(118 118 128 / 10%);
}
.mail-head-btn--ghost:hover:not(:disabled) {
  color: #1d1d1f;
  background: rgb(118 118 128 / 16%);
}
.mail-head-btn--primary {
  padding: 0 11px;
  color: #007aff;
  background: #e8f2ff;
}
.mail-head-btn--primary:hover {
  background: #d8e9ff;
}
.mail-search-box {
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
.mail-search-box:focus-within {
  background: #fff;
  box-shadow:
    0 0 0 2px rgb(0 122 255 / 24%),
    0 1px 4px rgb(0 0 0 / 5%);
}
.mail-search-box__icon {
  flex: none;
  width: 14px;
  height: 14px;
  color: #8e8e93;
}
.mail-search-box__input {
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
.mail-search-box__input::placeholder {
  color: #8e8e93;
}
.mail-search-box__input::-webkit-search-cancel-button {
  display: none;
}
.mail-search-box__clear {
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
.mail-folder-pills {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 2px;
  padding: 2.5px;
  background: rgb(118 118 128 / 10%);
  border-radius: 9px;
}
.mail-folder-pill {
  display: inline-flex;
  gap: 3px;
  align-items: center;
  justify-content: center;
  height: 26px;
  padding: 0 4px;
  font-size: 12px;
  font-weight: 500;
  color: var(--ap-label-2);
  white-space: nowrap;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 7px;
  transition: all var(--ap-dur-fast) var(--ap-ease);
}
.mail-folder-pill.on {
  font-weight: 600;
  color: #1d1d1f;
  background: #fff;
  box-shadow:
    0 1px 3px rgb(0 0 0 / 8%),
    0 0 0 0.5px rgb(0 0 0 / 4%);
}
.mail-folder-pill__count {
  font-size: 10.5px;
  font-weight: 700;
  color: #007aff;
}
.mail-avatar {
  display: inline-grid;
  flex: none;
  place-items: center;
  width: 36px;
  height: 36px;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  border-radius: 50%;
  user-select: none;
  box-shadow:
    inset 0 0.5px 0 rgb(255 255 255 / 35%),
    0 1px 3px rgb(0 0 0 / 10%);
}
.mail-avatar--lg {
  width: 40px;
  height: 40px;
  font-size: 16px;
}
.mail-empty-glyph {
  display: grid;
  place-items: center;
  width: 60px;
  height: 60px;
  color: #5856d6;
  background: linear-gradient(180deg, #fff 0%, #f1effe 100%);
  border-radius: 20px;
  box-shadow:
    0 4px 16px rgb(88 86 214 / 12%),
    0 0 0 0.5px rgb(88 86 214 / 14%);
}
.mail-trash-btn {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 500;
  color: #6e6e73;
  cursor: pointer;
  background: #f2f2f7;
  border: 0;
  border-radius: 999px;
  transition: all var(--ap-dur-fast);
}
.mail-trash-btn:hover {
  color: #ff3b30;
  background: #ffeceb;
}
.mail-crm-chip {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  font-size: 11px;
  font-weight: 600;
  color: #007aff;
  cursor: pointer;
  background: rgb(0 122 255 / 10%);
  border: 0;
  border-radius: 999px;
}
.mail-reply :deep(.ant-input) {
  border: 0;
  background: transparent !important;
  box-shadow: none !important;
  padding-inline: 2px;
}
.mail-tool-btn {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 500;
  color: #6e6e73;
  cursor: pointer;
  background: #f2f2f7;
  border: 0;
  border-radius: 999px;
  transition: all var(--ap-dur-fast);
}
.mail-tool-btn:hover:not(:disabled) {
  color: #1d1d1f;
  background: #e5e5ea;
}
.mail-tool-btn--ai {
  color: #7c3aed;
  background: #f3eeff;
}
.mail-tool-btn--ai:hover:not(:disabled) {
  background: #ebe2ff;
}
</style>
