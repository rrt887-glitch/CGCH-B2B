<script lang="ts" setup>
/**
 * 消息工作台（WhatsApp）：左侧会话列表，右侧聊天记录与输入框。
 * 客户 24 小时内发过消息才能发自由文本，窗口关闭后只能发已审核的模板。
 */
import type { ChannelWaApi } from '#/api/channel/whatsapp';
import type { ChannelAiApi } from '#/api/channel/ai';
import { closeDraft, DRAFT_REASON, draftWhatsApp, getPendingDraft } from '#/api/channel/ai';

import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

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
  Select,
  Space,
  Spin,
  Switch,
  Tag,
  Textarea,
  Tooltip,
} from 'ant-design-vue';

import {
  getWaAccountList,
  getWaConversation,
  getWaConversationPage,
  getWaMedia,
  getWaMessages,
  getWaTemplates,
  sendWaMedia,
  sendWaMessage,
  templateParamCount,
  templatePreview,
} from '#/api/channel/whatsapp';

defineOptions({ name: 'CgchChannelIm' });

const router = useRouter();
const POLL_MS = 10_000; // Webhook 写库后前端轮询刷新

const accounts = ref<ChannelWaApi.Account[]>([]);
const accountId = ref<number>();
const keyword = ref('');
const unreadOnly = ref(false);
const conversations = ref<ChannelWaApi.Conversation[]>([]);
const listLoading = ref(false);

const current = ref<ChannelWaApi.Conversation>();
const messages = ref<ChannelWaApi.Message[]>([]);
const chatLoading = ref(false);
const chatBox = ref<HTMLElement>();

const accountOptions = computed(() => [
  { label: '全部号码', value: undefined as any },
  ...accounts.value.map((a) => ({ label: `${a.name || ''} ${a.displayPhone || a.phoneNumberId}`.trim(), value: a.id })),
]);

async function loadConversations(silent = false) {
  if (!silent) listLoading.value = true;
  try {
    const res = await getWaConversationPage({
      pageNo: 1,
      pageSize: 100,
      accountId: accountId.value,
      keyword: keyword.value || undefined,
      unreadOnly: unreadOnly.value || undefined,
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

watch([accountId, unreadOnly], () => loadConversations());

let timer: ReturnType<typeof setInterval> | undefined;
onMounted(async () => {
  accounts.value = await getWaAccountList();
  if (accounts.value.length > 0) await loadConversations();
  timer = setInterval(() => {
    if (accounts.value.length === 0 || document.hidden) return;
    loadConversations(true);
    refreshCurrent();
  }, POLL_MS);
});
onBeforeUnmount(() => timer && clearInterval(timer));

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

async function openTemplate(newChat = false) {
  const acc = newChat ? (accountId.value ?? accounts.value[0]?.id) : current.value?.accountId;
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
const hm = (t?: string) => t?.slice(11, 16) ?? '';
const md = (t?: string) => t?.slice(5, 10) ?? '';
</script>

<template>
  <Page auto-content-height>
    <div v-if="accounts.length === 0" class="flex h-full items-center justify-center rounded bg-white">
      <Empty description="还没有绑定 WhatsApp 号码">
        <Button type="primary" @click="router.push('/config/channels')">去绑定号码</Button>
      </Empty>
    </div>

    <div v-else class="flex h-full gap-3">
      <!-- 会话列表 -->
      <div class="flex w-80 shrink-0 flex-col rounded bg-white">
        <div class="flex flex-col gap-2 border-b p-3">
          <div class="flex gap-2">
            <Select v-model:value="accountId" :options="accountOptions" class="min-w-0 flex-1" />
            <Button type="primary" @click="openTemplate(true)">新会话</Button>
          </div>
          <InputSearch v-model:value="keyword" allow-clear placeholder="号码或昵称" @search="() => loadConversations()" />
          <label class="flex items-center gap-2 text-sm text-gray-500">
            <Switch v-model:checked="unreadOnly" size="small" /> 只看未读
          </label>
        </div>
        <div class="flex-1 overflow-auto">
          <Spin :spinning="listLoading">
            <div v-if="conversations.length === 0" class="p-8"><Empty description="暂无会话" /></div>
            <div
              v-for="c in conversations"
              :key="c.id"
              class="flex cursor-pointer gap-3 border-b px-3 py-3"
              :class="current?.id === c.id ? 'bg-green-50' : 'hover:bg-gray-50'"
              @click="openConversation(c)"
            >
              <Badge :count="c.unreadCount" :offset="[-4, 4]">
                <Avatar class="!bg-green-600">{{ initials(c) }}</Avatar>
              </Badge>
              <div class="min-w-0 flex-1">
                <div class="flex justify-between gap-2">
                  <span class="truncate font-medium">{{ c.customerName || c.profileName || `+${c.waId}` }}</span>
                  <span class="shrink-0 text-xs text-gray-400">{{ md(c.lastMessageTime) }} {{ hm(c.lastMessageTime) }}</span>
                </div>
                <div class="truncate text-xs text-gray-500">{{ c.lastMessagePreview }}</div>
              </div>
            </div>
          </Spin>
        </div>
      </div>

      <!-- 聊天区 -->
      <div class="flex min-w-0 flex-1 flex-col rounded bg-white">
        <div v-if="!current" class="flex h-full items-center justify-center"><Empty description="选择一个会话" /></div>
        <template v-else>
          <div class="flex items-center justify-between border-b px-4 py-3">
            <div>
              <div class="font-medium">{{ current.profileName || `+${current.waId}` }} <span class="text-sm text-gray-400">+{{ current.waId }}</span></div>
              <div class="mt-1 flex gap-2">
                <Tag v-if="current.customerName" color="blue" class="cursor-pointer" @click="router.push(`/crm/customer/detail/${current.customerId}`)">
                  客户：{{ current.customerName }}
                </Tag>
                <Tag v-else-if="current.clueName" color="cyan" class="cursor-pointer" @click="router.push(`/crm/clue/detail/${current.clueId}`)">
                  线索：{{ current.clueName }}
                </Tag>
                <Tag v-else>未关联客户</Tag>
                <Tooltip v-if="current.windowOpen" :title="`窗口在 ${current.windowExpireTime} 关闭`">
                  <Tag color="green">24 小时窗口内</Tag>
                </Tooltip>
                <Tag v-else color="orange">窗口已关闭，仅可发模板</Tag>
              </div>
            </div>
          </div>

          <div ref="chatBox" class="flex-1 overflow-auto bg-[#efeae2] p-4">
            <Spin :spinning="chatLoading">
              <div v-for="m in messages" :key="m.id" class="mb-2 flex" :class="m.direction === 2 ? 'justify-end' : 'justify-start'">
                <div
                  class="max-w-[70%] rounded-lg px-3 py-2 text-sm shadow-sm"
                  :class="m.direction === 2 ? 'bg-[#d9fdd3]' : 'bg-white'"
                >
                  <template v-if="MEDIA_LABEL[m.type]">
                    <a v-if="isImage(m)" :href="m.fileUrl" target="_blank" rel="noopener">
                      <img :src="m.fileUrl" class="mb-1 max-h-60 max-w-full rounded" alt="" />
                    </a>
                    <div v-else class="mb-1 flex items-center gap-2 text-xs text-gray-500">
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
                  <div v-if="m.type === 'template'" class="mb-1 text-xs text-gray-500">[模板 {{ m.templateName }}]</div>
                  <div class="whitespace-pre-wrap break-words">{{ m.textBody }}</div>
                  <div class="mt-1 flex items-center justify-end gap-1 text-[11px] text-gray-400">
                    <span v-if="m.aiGenerated" class="text-purple-500">AI</span>
                    <span>{{ md(m.messageTime) }} {{ hm(m.messageTime) }}</span>
                    <Tooltip v-if="m.direction === 2 && m.status" :title="m.errorMessage || STATUS_ICON[m.status]?.label">
                      <span :class="m.status === 'read' ? 'text-sky-500' : m.status === 'failed' ? 'font-bold text-red-500' : ''">
                        {{ STATUS_ICON[m.status]?.text }}
                      </span>
                    </Tooltip>
                  </div>
                </div>
              </div>
            </Spin>
          </div>

          <div class="border-t p-3">
            <div v-if="aiDraft" class="mb-2 rounded border border-orange-200 bg-orange-50 p-3 text-sm">
              <div class="mb-1 font-medium text-orange-700">
                AI 已准备回复，未自动发出：{{ DRAFT_REASON[aiDraft.reason || ''] ?? aiDraft.reason }}
                <span v-if="aiDraft.reasonDetail">（{{ aiDraft.reasonDetail }}）</span>
              </div>
              <div v-if="aiDraft.content" class="line-clamp-3 whitespace-pre-wrap text-gray-700">{{ aiDraft.content }}</div>
              <div class="mt-2 flex gap-2">
                <Button v-if="aiDraft.content && current.windowOpen" size="small" type="primary" ghost @click="useDraft">填入输入框</Button>
                <Button size="small" @click="discardDraft">忽略</Button>
              </div>
            </div>
            <template v-if="current.windowOpen">
              <Textarea
                v-model:value="text"
                :auto-size="{ minRows: 2, maxRows: 6 }"
                :maxlength="4096"
                placeholder="输入消息，Enter 发送，Shift+Enter 换行；发送文件时这里的文字会作为说明"
                @keydown="onKeydown"
              />
              <div class="mt-2 flex justify-between">
                <Space>
                  <Button :loading="fileSending" @click="pickAndSend">发送文件</Button>
                  <Button @click="openTemplate()">发送模板</Button>
                  <Button :loading="aiLoading" @click="aiCompose">AI 起草</Button>
                </Space>
                <Button type="primary" :loading="sending" :disabled="!text.trim()" @click="sendText">发送</Button>
              </div>
            </template>
            <Alert
              v-else
              type="warning"
              show-icon
              message="客户超过 24 小时没有回复，WhatsApp 规定此时只能发送已审核的模板消息。"
            >
              <template #action><Button type="primary" size="small" @click="openTemplate()">发送模板</Button></template>
            </Alert>
          </div>
        </template>
      </div>
    </div>

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
