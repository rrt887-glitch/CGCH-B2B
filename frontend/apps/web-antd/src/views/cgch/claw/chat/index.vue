<script lang="ts" setup>
/**
 * 对话助手：用自然语言问 CRM 和消息里的事，AI 先查数据再回答。只读，按本人数据权限过滤。
 */
import { fmtDateTime } from '#/utils/datetime';
import type { ChannelAssistantApi } from '#/api/channel/assistant';

import { nextTick, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { Button, Card, Empty, message, Popconfirm, Spin, Tag, Textarea, Tooltip } from 'ant-design-vue';

import { askAssistant, deleteAssistantChat, getAssistantChats, getAssistantMessages } from '#/api/channel/assistant';

defineOptions({ name: 'CgchClawChat' });

const TOOL_LABEL: Record<string, string> = {
  search_customers: '搜索客户',
  get_customer: '查看客户详情',
  followups_due: '查看待跟进',
  search_messages: '搜索消息',
  get_stats: '查看统计',
  pending_drafts: '查看待处理草稿',
};
const SUGGESTIONS = [
  '这周有哪些客户需要跟进？',
  '最近 30 天的询盘和回复率怎么样？',
  '有哪些客户提到了退款或投诉？',
  'AI 还有哪些草稿等我处理？',
  '帮我总结一下和 Acme 的往来',
];

const router = useRouter();
const chats = ref<ChannelAssistantApi.Chat[]>([]);
const chatId = ref<number>();
const msgs = ref<(ChannelAssistantApi.Message & { error?: boolean; pending?: boolean })[]>([]);
/** 等待回答的秒数（查询可能要查多张表、调用大模型，给出进度感） */
const waited = ref(0);
let waitTimer: ReturnType<typeof setInterval> | undefined;
const input = ref('');
const sending = ref(false);
const loading = ref(false);
const listRef = ref<HTMLElement>();

async function loadChats() {
  chats.value = await getAssistantChats();
}
async function openChat(id?: number) {
  chatId.value = id;
  msgs.value = [];
  if (!id) return;
  loading.value = true;
  try {
    msgs.value = await getAssistantMessages(id);
  } finally {
    loading.value = false;
  }
  scrollDown();
}
function scrollDown() {
  nextTick(() => listRef.value?.scrollTo({ top: listRef.value.scrollHeight, behavior: 'smooth' }));
}
async function send(text?: string) {
  const q = (text ?? input.value).trim();
  if (!q || sending.value) return;
  input.value = '';
  msgs.value.push({ id: -Date.now(), role: 'user', content: q, steps: [] });
  msgs.value.push({ id: -Date.now() - 1, role: 'assistant', content: '', steps: [], pending: true });
  scrollDown();
  sending.value = true;
  waited.value = 0;
  waitTimer = setInterval(() => waited.value++, 1000);
  try {
    const r = await askAssistant(q, chatId.value);
    msgs.value.pop();
    msgs.value.push({ id: r.messageId, role: 'assistant', content: r.content, steps: r.steps, credits: r.credits });
    if (!chatId.value) {
      chatId.value = r.chatId;
    }
    await loadChats();
  } catch (error: any) {
    msgs.value.pop();
    // 失败原因直接显示在对话里，不只是一闪而过的提示
    const reason = error?.response?.data?.msg || error?.msg || error?.message || '查询失败，请稍后重试';
    msgs.value.push({ id: -Date.now() - 2, role: 'assistant', content: String(reason), steps: [], error: true });
  } finally {
    clearInterval(waitTimer);
    sending.value = false;
    scrollDown();
  }
}
function onKey(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault();
    send();
  }
}
async function remove(id: number) {
  await deleteAssistantChat(id);
  message.success('已删除');
  if (chatId.value === id) openChat(undefined);
  await loadChats();
}
function argText(s: ChannelAssistantApi.Step) {
  try {
    const a = JSON.parse(s.args || '{}');
    return Object.values(a).filter((v) => v !== null && v !== '').join('，');
  } catch {
    return '';
  }
}

onMounted(() => {
  loadChats();
  // 客户列表「一键唤醒 / 一键背调」带过来的问题，填进输入框由用户确认后发送
  try {
    const preset = sessionStorage.getItem('cgch-claw-prompt');
    if (preset) {
      input.value = preset;
      sessionStorage.removeItem('cgch-claw-prompt');
    }
  } catch {
    // 浏览器禁止存储时忽略
  }
});
</script>

<template>
  <Page auto-content-height>
    <div class="flex h-full gap-4">
      <Card :bordered="false" class="w-64 shrink-0" :body-style="{ padding: '12px' }">
        <Button type="primary" block class="mb-3" @click="openChat(undefined)">新对话</Button>
        <Empty v-if="chats.length === 0" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="还没有对话" />
        <div
          v-for="c in chats"
          :key="c.id"
          class="group mb-1 flex cursor-pointer items-center justify-between rounded px-2 py-2 hover:bg-gray-50 dark:hover:bg-gray-800"
          :class="c.id === chatId ? 'bg-blue-50 dark:bg-gray-800' : ''"
          @click="openChat(c.id)"
        >
          <div class="min-w-0">
            <div class="truncate text-sm">{{ c.title }}</div>
            <div class="text-xs text-gray-400">{{ fmtDateTime(c.updateTime, 'MM-DD HH:mm') }}</div>
          </div>
          <Popconfirm title="删除这个对话？" @confirm="remove(c.id)">
            <Button size="small" type="text" danger class="opacity-0 group-hover:opacity-100" @click.stop>删除</Button>
          </Popconfirm>
        </div>
      </Card>

      <Card :bordered="false" class="flex min-w-0 flex-1 flex-col" :body-style="{ display: 'flex', flexDirection: 'column', height: '100%', padding: '0' }">
        <div ref="listRef" class="flex-1 overflow-auto p-4">
          <Spin :spinning="loading">
            <div v-if="msgs.length === 0" class="mx-auto mt-10 max-w-xl text-center">
              <div class="mb-2 text-lg font-medium">有什么可以帮你？</div>
              <div class="mb-6 text-sm text-gray-500">我会先查 CRM、邮件、WhatsApp 和统计数据再回答。只能查询，不会修改数据或发送消息。</div>
              <div class="flex flex-wrap justify-center gap-2">
                <Button v-for="s in SUGGESTIONS" :key="s" size="small" @click="send(s)">{{ s }}</Button>
              </div>
            </div>
            <div v-for="m in msgs" :key="m.id" class="mb-4 flex" :class="m.role === 'user' ? 'justify-end' : 'justify-start'">
              <div class="max-w-[80%]">
                <div v-if="m.role === 'assistant' && m.steps.length" class="mb-1 flex flex-wrap gap-1">
                  <Tooltip v-for="(s, i) in m.steps" :key="i" :title="argText(s) || undefined">
                    <Tag color="blue">已{{ TOOL_LABEL[s.tool] ?? s.tool }}</Tag>
                  </Tooltip>
                </div>
                <div
                  class="whitespace-pre-wrap rounded-lg px-3 py-2 leading-6"
                  :class="m.role === 'user' ? 'bg-blue-500 text-white' : m.error ? 'bg-red-50 text-red-600' : 'bg-gray-100 dark:bg-gray-800'"
                >
                  <template v-if="m.pending">
                    <Spin size="small" class="mr-2" />{{ waited < 8 ? '正在查询 CRM 和消息数据…' : `正在整理回答…已等待 ${waited} 秒` }}
                  </template>
                  <template v-else-if="m.error">
                    查询失败：{{ m.content }}
                    <a v-if="m.content.includes('大模型')" class="ml-1" @click="router.push({ path: '/v3/settings', query: { tab: 'ai' } })">去配置大模型 →</a>
                  </template>
                  <template v-else>{{ m.content }}</template>
                </div>
                <div v-if="m.role === 'assistant' && m.credits" class="mt-1 text-xs text-gray-400">消耗 {{ m.credits }} 智点</div>
              </div>
            </div>
          </Spin>
        </div>
        <div class="border-t p-3 dark:border-gray-700">
          <div class="flex items-end gap-2">
            <Textarea v-model:value="input" :auto-size="{ minRows: 1, maxRows: 5 }" placeholder="问点什么，例如：这周有哪些客户需要跟进？（Enter 发送，Shift+Enter 换行）" @keydown="onKey" />
            <Button type="primary" :loading="sending" @click="send()">发送</Button>
          </div>
        </div>
      </Card>
    </div>
  </Page>
</template>
