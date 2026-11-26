<script lang="ts" setup>
/**
 * CRM 智枢 · 智能问答（Apple Intelligence / iMessage 双栏工作台）：
 * 用自然语言查询 CRM 客户、跟进、邮件、WhatsApp 与经营数据，只读安全检索，按本人权限过滤。
 */
import type { ChannelAssistantApi } from '#/api/channel/assistant';

import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';
import { MarkdownIt } from '@vben/plugins/markmap';
import { useUserStore } from '@vben/stores';

import { message, Popconfirm, Spin, Tooltip } from 'ant-design-vue';

import {
  askAssistant,
  deleteAssistantChat,
  getAssistantChats,
  getAssistantMessages,
} from '#/api/channel/assistant';
import { fmtDateTime } from '#/utils/datetime';
import { crmAvatarBg, crmInitial } from '#/views/cgch/v3/crm/components/shared';

defineOptions({ name: 'CgchClawChat' });

const TOOL_LABEL: Record<string, { icon: string; label: string }> = {
  search_customers: { icon: 'lucide:users', label: '搜索客户档案' },
  get_customer: { icon: 'lucide:user-check', label: '读取客户详情' },
  followups_due: { icon: 'lucide:calendar-clock', label: '检查待跟进日程' },
  search_messages: { icon: 'lucide:messages-square', label: '检索往来消息' },
  get_stats: { icon: 'lucide:bar-chart-3', label: '汇总经营统计' },
  pending_drafts: { icon: 'lucide:file-pen-line', label: '读取待处理 AI 草稿' },
};

const SUGGESTIONS = [
  {
    icon: 'lucide:calendar-clock',
    tint: 'blue',
    title: '本周跟进清单',
    prompt: '这周有哪些客户需要跟进？',
  },
  {
    icon: 'lucide:trending-up',
    tint: 'purple',
    title: '近 30 天转化概览',
    prompt: '最近 30 天的询盘和回复率怎么样？',
  },
  {
    icon: 'lucide:shield-alert',
    tint: 'amber',
    title: '客诉与退款预警',
    prompt: '有哪些客户提到了退款或投诉？',
  },
  {
    icon: 'lucide:sparkles',
    tint: 'indigo',
    title: '待确认 AI 草稿',
    prompt: 'AI 还有哪些草稿等我处理？',
  },
  {
    icon: 'lucide:file-search',
    tint: 'green',
    title: '重点客户往来复盘',
    prompt: '帮我总结一下最近活跃客户的沟通进展',
  },
  {
    icon: 'lucide:globe',
    tint: 'teal',
    title: '高意向沉睡盘点',
    prompt: '有哪些久未联系但之前意向较高的客户值得唤醒？',
  },
];

const router = useRouter();
const userStore = useUserStore();
const userLabel = computed(
  () => userStore.userInfo?.realName || userStore.userInfo?.username || '我',
);

// 助手回答按 Markdown 显示（表格、列表、加粗）；html: false 把原始 HTML 当文字处理
const md = new MarkdownIt({ html: false, linkify: true, breaks: true });
const defaultLink =
  md.renderer.rules.link_open ??
  ((tokens: any, idx: number, options: any, _env: any, self: any) =>
    self.renderToken(tokens, idx, options));
md.renderer.rules.link_open = (
  tokens: any,
  idx: number,
  options: any,
  env: any,
  self: any,
) => {
  tokens[idx]!.attrSet('target', '_blank');
  tokens[idx]!.attrSet('rel', 'noopener noreferrer');
  return defaultLink(tokens, idx, options, env, self);
};
const renderMd = (text: string) => md.render(text || '');

const chats = ref<ChannelAssistantApi.Chat[]>([]);
const chatSearch = ref('');
const chatId = ref<number>();
const msgs = ref<
  (ChannelAssistantApi.Message & { error?: boolean; pending?: boolean })[]
>([]);
const waited = ref(0);
let waitTimer: ReturnType<typeof setInterval> | undefined;
const input = ref('');
const sending = ref(false);
const loading = ref(false);
const listRef = ref<HTMLElement>();

const filteredChats = computed(() => {
  const kw = chatSearch.value.trim().toLowerCase();
  if (!kw) return chats.value;
  return chats.value.filter((c) => (c.title || '').toLowerCase().includes(kw));
});

const currentChatTitle = computed(() => {
  if (!chatId.value) return '新对话';
  return chats.value.find((c) => c.id === chatId.value)?.title || '智能对话';
});

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
  nextTick(() =>
    listRef.value?.scrollTo({
      top: listRef.value.scrollHeight,
      behavior: 'smooth',
    }),
  );
}

async function send(text?: string) {
  const q = (text ?? input.value).trim();
  if (!q || sending.value) return;
  input.value = '';
  msgs.value.push({ id: -Date.now(), role: 'user', content: q, steps: [] });
  msgs.value.push({
    id: -Date.now() - 1,
    role: 'assistant',
    content: '',
    steps: [],
    pending: true,
  });
  scrollDown();
  sending.value = true;
  waited.value = 0;
  waitTimer = setInterval(() => waited.value++, 1000);
  try {
    const r = await askAssistant(q, chatId.value);
    msgs.value.pop();
    msgs.value.push({
      id: r.messageId,
      role: 'assistant',
      content: r.content,
      steps: r.steps ?? [],
    });
    if (!chatId.value) {
      chatId.value = r.chatId;
    }
    await loadChats();
  } catch (error: any) {
    msgs.value.pop();
    const reason =
      error?.response?.data?.msg ||
      error?.msg ||
      error?.message ||
      '查询失败，请稍后重试';
    msgs.value.push({
      id: -Date.now() - 2,
      role: 'assistant',
      content: String(reason),
      steps: [],
      error: true,
    });
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
  message.success('对话已删除');
  if (chatId.value === id) openChat(undefined);
  await loadChats();
}

function argText(s: ChannelAssistantApi.Step) {
  try {
    const a = JSON.parse(s.args || '{}');
    return Object.values(a)
      .filter((v) => v !== null && v !== '')
      .join(' · ');
  } catch {
    return '';
  }
}

onBeforeUnmount(() => clearInterval(waitTimer));

onMounted(() => {
  loadChats();
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
  <div class="cc">
    <!-- 左侧历史会话栏 -->
    <aside class="cc__side">
      <button
        type="button"
        class="cc__new-btn"
        :class="{ 'is-fresh': !chatId && !msgs.length }"
        @click="openChat(undefined)"
      >
        <IconifyIcon icon="lucide:plus" class="size-4" />
        <span>新建对话</span>
      </button>

      <div v-if="chats.length > 3" class="cc__search">
        <IconifyIcon icon="lucide:search" class="cc__search-icon" />
        <input
          v-model="chatSearch"
          type="text"
          class="cc__search-input"
          placeholder="搜索历史提问…"
        />
        <button
          v-if="chatSearch"
          type="button"
          class="cc__search-clear"
          @click="chatSearch = ''"
        >
          <IconifyIcon icon="lucide:x" class="size-3" />
        </button>
      </div>

      <div class="cc__group-title">
        <span>历史会话</span>
        <span v-if="chats.length" class="cc__count">{{ chats.length }}</span>
      </div>

      <div class="cc__history">
        <div v-if="filteredChats.length === 0" class="cc__empty-side">
          <div class="cc__empty-icon">
            <IconifyIcon icon="lucide:messages-square" class="size-4" />
          </div>
          <div>{{ chatSearch ? '没有匹配的会话' : '暂无历史提问记录' }}</div>
        </div>

        <div
          v-for="c in filteredChats"
          :key="c.id"
          class="cc__chat-item"
          :class="{ on: c.id === chatId }"
          @click="openChat(c.id)"
        >
          <span class="cc__chat-dot"></span>
          <div class="min-w-0 flex-1">
            <div class="cc__chat-title">{{ c.title || '未命名提问' }}</div>
            <div class="cc__chat-time">
              {{ fmtDateTime(c.updateTime, 'MM-DD HH:mm') }}
            </div>
          </div>
          <Popconfirm
            title="确认删除这段对话记录？"
            ok-text="删除"
            cancel-text="取消"
            @confirm="remove(c.id)"
          >
            <button
              type="button"
              class="cc__chat-del"
              title="删除对话"
              @click.stop
            >
              <IconifyIcon icon="lucide:trash-2" class="size-3.5" />
            </button>
          </Popconfirm>
        </div>
      </div>
    </aside>

    <!-- 右侧对话主舞台 -->
    <section class="cc__stage">
      <header class="cc__topbar">
        <div class="flex min-w-0 items-center gap-2.5">
          <span class="cc__stage-badge">
            <IconifyIcon icon="lucide:sparkles" class="size-3.5" />
          </span>
          <span class="cc__stage-title">{{ currentChatTitle }}</span>
        </div>

        <div class="flex items-center gap-2">
          <span class="cc__safe-pill">
            <IconifyIcon icon="lucide:shield-check" class="size-3.5" />
            <span>只读查询 · 不修改客户数据</span>
          </span>
          <button
            v-if="chatId || msgs.length"
            type="button"
            class="cc__top-new"
            @click="openChat(undefined)"
          >
            <IconifyIcon icon="lucide:plus" class="size-3.5" />
            <span>新对话</span>
          </button>
        </div>
      </header>

      <div ref="listRef" class="cc__scroll">
        <Spin :spinning="loading">
          <!-- 零状态欢迎页 -->
          <div v-if="msgs.length === 0" class="cc__hero">
            <div class="cc__hero-orb">
              <IconifyIcon icon="lucide:sparkles" class="size-7" />
            </div>
            <h2 class="cc__hero-title">有什么可以帮你？</h2>
            <p class="cc__hero-sub">
              直接用自然语言提问，我会实时检索你的 CRM 客户、跟进记录、邮件、WhatsApp 会话与统计报表再给出结论。
            </p>

            <div class="cc__grid">
              <button
                v-for="s in SUGGESTIONS"
                :key="s.prompt"
                type="button"
                class="cc__prompt-card"
                :disabled="sending"
                @click="send(s.prompt)"
              >
                <span class="cc__prompt-icon" :class="`is-${s.tint}`">
                  <IconifyIcon :icon="s.icon" class="size-4" />
                </span>
                <div class="min-w-0 flex-1">
                  <div class="cc__prompt-head">
                    <span>{{ s.title }}</span>
                    <IconifyIcon
                      icon="lucide:arrow-up-right"
                      class="cc__prompt-arrow"
                    />
                  </div>
                  <div class="cc__prompt-text">{{ s.prompt }}</div>
                </div>
              </button>
            </div>
          </div>

          <!-- 对话流 -->
          <div v-else class="cc__stream">
            <div
              v-for="m in msgs"
              :key="m.id"
              class="cc__row"
              :class="m.role === 'user' ? 'is-user' : 'is-ai'"
            >
              <!-- AI 头像 -->
              <span v-if="m.role === 'assistant'" class="cc__ai-avatar">
                <IconifyIcon icon="lucide:sparkles" class="size-4" />
              </span>

              <div class="cc__msg-wrap">
                <!-- AI 工具调用步骤胶囊 -->
                <div
                  v-if="m.role === 'assistant' && m.steps?.length"
                  class="cc__steps"
                >
                  <Tooltip
                    v-for="(s, i) in m.steps"
                    :key="i"
                    :title="argText(s) ? `检索条件：${argText(s)}` : undefined"
                  >
                    <span class="cc__step-chip">
                      <IconifyIcon
                        :icon="
                          TOOL_LABEL[s.tool]?.icon ?? 'lucide:check-circle-2'
                        "
                        class="size-3"
                      />
                      <span>{{ TOOL_LABEL[s.tool]?.label ?? s.tool }}</span>
                    </span>
                  </Tooltip>
                </div>

                <!-- 气泡主体 -->
                <div
                  class="cc__bubble"
                  :class="{
                    'is-error': m.error,
                    'is-pending': m.pending,
                  }"
                >
                  <template v-if="m.pending">
                    <div class="cc__thinking">
                      <span class="cc__dots">
                        <i></i><i></i><i></i>
                      </span>
                      <span>
                        {{
                          waited < 8
                            ? '正在检索 CRM 与往来记录…'
                            : `正在汇总分析…已等待 ${waited} 秒`
                        }}
                      </span>
                    </div>
                  </template>

                  <template v-else-if="m.error">
                    <div class="flex items-start gap-2">
                      <IconifyIcon
                        icon="lucide:circle-alert"
                        class="mt-0.5 size-4 shrink-0"
                      />
                      <div>
                        <div>查询失败：{{ m.content }}</div>
                        <button
                          v-if="m.content.includes('大模型')"
                          type="button"
                          class="cc__err-link"
                          @click="
                            router.push({
                              path: '/v3/settings',
                              query: { tab: 'ai' },
                            })
                          "
                        >
                          前往「AI 接待设置」配置大模型 ›
                        </button>
                      </div>
                    </div>
                  </template>

                  <!-- eslint-disable-next-line vue/no-v-html -->
                  <div
                    v-else-if="m.role === 'assistant'"
                    class="cg-md"
                    v-html="renderMd(m.content)"
                  ></div>

                  <div v-else class="whitespace-pre-wrap break-words">
                    {{ m.content }}
                  </div>
                </div>
              </div>

              <!-- 用户通讯录单字头像 -->
              <span
                v-if="m.role === 'user'"
                class="ap-crm-avatar cc__user-avatar"
                :style="{ background: crmAvatarBg(userLabel) }"
              >
                {{ crmInitial(userLabel) }}
              </span>
            </div>
          </div>
        </Spin>
      </div>

      <!-- 底部悬浮输入框 -->
      <footer class="cc__foot">
        <div class="cc__composer">
          <textarea
            v-model="input"
            rows="1"
            class="cc__textarea"
            placeholder="输入想查询的客户、跟进、消息或经营指标…（Enter 发送，Shift + Enter 换行）"
            :disabled="sending"
            @keydown="onKey"
          ></textarea>
          <button
            type="button"
            class="cc__send"
            :disabled="!input.trim() || sending"
            title="发送"
            @click="send()"
          >
            <IconifyIcon
              :icon="sending ? 'lucide:loader-2' : 'lucide:arrow-up'"
              class="size-4"
              :class="{ 'animate-spin': sending }"
            />
          </button>
        </div>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.cc {
  display: flex;
  gap: 16px;
  height: 100%;
  padding: 16px 18px;
}

/* 左侧历史面板 */
.cc__side {
  display: flex;
  flex: 0 0 256px;
  flex-direction: column;
  min-height: 0;
  padding: 14px 12px;
  background: rgb(255 255 255 / 76%);
  border-radius: 18px;
  box-shadow: var(--ap-shadow-rest);
  backdrop-filter: blur(22px) saturate(165%);
}

.cc__new-btn {
  display: flex;
  gap: 7px;
  align-items: center;
  justify-content: center;
  height: 38px;
  font-size: 13.5px;
  font-weight: 600;
  color: #fff;
  cursor: pointer;
  background: linear-gradient(180deg, #1a88ff 0%, #007aff 100%);
  border: 0;
  border-radius: 11px;
  box-shadow:
    inset 0 0.5px 0 rgb(255 255 255 / 28%),
    0 3px 10px rgb(0 122 255 / 24%);
  transition:
    transform var(--ap-dur-fast) var(--ap-ease),
    box-shadow var(--ap-dur-fast);
}

.cc__new-btn:hover {
  box-shadow:
    inset 0 0.5px 0 rgb(255 255 255 / 32%),
    0 5px 14px rgb(0 122 255 / 30%);
}

.cc__new-btn:active {
  transform: scale(0.98);
}

.cc__search {
  display: flex;
  gap: 6px;
  align-items: center;
  height: 32px;
  padding: 0 10px;
  margin-top: 10px;
  background: #f2f3f7;
  border-radius: 9px;
}

.cc__search-icon {
  flex: none;
  width: 13.5px;
  height: 13.5px;
  color: #8e8e93;
}

.cc__search-input {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  color: #1d1d1f;
  background: transparent;
  border: 0;
  outline: none;
}

.cc__search-clear {
  display: grid;
  place-items: center;
  width: 16px;
  height: 16px;
  color: #fff;
  cursor: pointer;
  background: #c7c7cc;
  border: 0;
  border-radius: 50%;
}

.cc__group-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 6px 6px;
  font-size: 11.5px;
  font-weight: 600;
  color: #86868b;
}

.cc__count {
  padding: 0 6px;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  color: #6e6e73;
  background: rgb(0 0 0 / 5%);
  border-radius: 999px;
}

.cc__history {
  flex: 1;
  min-height: 0;
  padding-right: 2px;
  overflow-y: auto;
}

.cc__empty-side {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  padding: 36px 12px;
  font-size: 12.5px;
  color: #8e8e93;
  text-align: center;
}

.cc__empty-icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  color: #8e8e93;
  background: #f2f3f7;
  border-radius: 10px;
}

.cc__chat-item {
  display: flex;
  gap: 9px;
  align-items: center;
  padding: 9px 10px;
  margin-bottom: 3px;
  cursor: pointer;
  border-radius: 11px;
  transition:
    background-color var(--ap-dur-fast),
    box-shadow var(--ap-dur-fast);
}

.cc__chat-item:hover:not(.on) {
  background: rgb(0 0 0 / 3.5%);
}

.cc__chat-item.on {
  background: #fff;
  box-shadow:
    0 2px 8px rgb(15 23 42 / 7%),
    0 0 0 0.5px rgb(0 122 255 / 18%);
}

.cc__chat-dot {
  flex: none;
  width: 6px;
  height: 6px;
  background: #d1d1d6;
  border-radius: 50%;
}

.cc__chat-item.on .cc__chat-dot {
  background: #007aff;
  box-shadow: 0 0 0 3px rgb(0 122 255 / 16%);
}

.cc__chat-title {
  overflow: hidden;
  font-size: 13px;
  font-weight: 500;
  color: #1d1d1f;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cc__chat-item.on .cc__chat-title {
  font-weight: 600;
  color: #007aff;
}

.cc__chat-time {
  margin-top: 2px;
  font-size: 11px;
  color: #8e8e93;
}

.cc__chat-del {
  display: grid;
  flex: none;
  place-items: center;
  width: 24px;
  height: 24px;
  color: #8e8e93;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 7px;
  opacity: 0;
  transition:
    opacity var(--ap-dur-fast),
    background-color var(--ap-dur-fast),
    color var(--ap-dur-fast);
}

.cc__chat-item:hover .cc__chat-del {
  opacity: 1;
}

.cc__chat-del:hover {
  color: #ff3b30;
  background: rgb(255 59 48 / 10%);
}

/* 右侧主对话舞台 */
.cc__stage {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: rgb(255 255 255 / 82%);
  border-radius: 20px;
  box-shadow: var(--ap-shadow-rest);
  backdrop-filter: blur(24px) saturate(170%);
}

.cc__topbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  min-height: 54px;
  padding: 10px 20px;
  background: rgb(255 255 255 / 72%);
  border-bottom: 0.5px solid var(--ap-separator);
}

.cc__stage-badge {
  display: grid;
  flex: none;
  place-items: center;
  width: 26px;
  height: 26px;
  color: #fff;
  background: linear-gradient(135deg, #af52de 0%, #007aff 100%);
  border-radius: 8px;
}

.cc__stage-title {
  overflow: hidden;
  font-size: 14.5px;
  font-weight: 700;
  color: #1d1d1f;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cc__safe-pill {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 26px;
  padding: 0 10px;
  font-size: 11.5px;
  font-weight: 500;
  color: #1e8e3e;
  background: #eaf9ee;
  border-radius: 999px;
}

.cc__top-new {
  display: none;
  gap: 4px;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 600;
  color: #007aff;
  cursor: pointer;
  background: #e8f2ff;
  border: 0;
  border-radius: 999px;
}

.cc__scroll {
  flex: 1;
  min-height: 0;
  padding: 24px 26px 16px;
  overflow-y: auto;
}

/* 零状态欢迎区 */
.cc__hero {
  max-width: 680px;
  margin: 24px auto 12px;
  text-align: center;
}

.cc__hero-orb {
  display: inline-grid;
  place-items: center;
  width: 56px;
  height: 56px;
  margin-bottom: 14px;
  color: #fff;
  background: linear-gradient(135deg, #af52de 0%, #5856d6 50%, #007aff 100%);
  border-radius: 18px;
  box-shadow:
    inset 0 1px 0 rgb(255 255 255 / 35%),
    0 10px 26px rgb(88 86 214 / 28%);
}

.cc__hero-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #1d1d1f;
  letter-spacing: -0.02em;
}

.cc__hero-sub {
  max-width: 520px;
  margin: 8px auto 24px;
  font-size: 13.5px;
  line-height: 1.6;
  color: #6e6e73;
}

.cc__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  text-align: left;
}

.cc__prompt-card {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 13px 14px;
  cursor: pointer;
  background: #f7f8fb;
  border: 0;
  border-radius: 14px;
  box-shadow: inset 0 0 0 0.5px rgb(0 0 0 / 5%);
  transition:
    background-color var(--ap-dur-fast),
    transform var(--ap-dur-fast) var(--ap-ease),
    box-shadow var(--ap-dur-fast);
}

.cc__prompt-card:hover:not(:disabled) {
  background: #fff;
  transform: translateY(-1.5px);
  box-shadow:
    0 6px 18px rgb(15 23 42 / 7%),
    0 0 0 1px rgb(0 122 255 / 22%);
}

.cc__prompt-card:active:not(:disabled) {
  transform: scale(0.985);
}

.cc__prompt-icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
}

.cc__prompt-icon.is-blue {
  color: #007aff;
  background: #e8f2ff;
}

.cc__prompt-icon.is-purple {
  color: #af52de;
  background: #f5ebfc;
}

.cc__prompt-icon.is-amber {
  color: #d97706;
  background: #fff4e0;
}

.cc__prompt-icon.is-indigo {
  color: #5856d6;
  background: #ededfc;
}

.cc__prompt-icon.is-green {
  color: #1f9d4c;
  background: #eaf9ee;
}

.cc__prompt-icon.is-teal {
  color: #0d9488;
  background: #e6f9f6;
}

.cc__prompt-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  color: #1d1d1f;
}

.cc__prompt-arrow {
  width: 14px;
  height: 14px;
  color: #8e8e93;
  opacity: 0;
  transition:
    opacity var(--ap-dur-fast),
    transform var(--ap-dur-fast);
}

.cc__prompt-card:hover .cc__prompt-arrow {
  color: #007aff;
  opacity: 1;
  transform: translate(1px, -1px);
}

.cc__prompt-text {
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.45;
  color: #6e6e73;
}

/* 对话消息流 */
.cc__stream {
  display: flex;
  flex-direction: column;
  gap: 18px;
  max-width: 820px;
  margin: 0 auto;
}

.cc__row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  animation: ap-bubble-in 260ms var(--ap-spring) both;
}

.cc__row.is-user {
  justify-content: flex-end;
}

.cc__ai-avatar {
  display: grid;
  flex: none;
  place-items: center;
  width: 30px;
  height: 30px;
  margin-top: 2px;
  color: #fff;
  background: linear-gradient(135deg, #af52de 0%, #5856d6 55%, #007aff 100%);
  border-radius: 10px;
  box-shadow: 0 3px 8px rgb(88 86 214 / 22%);
}

.cc__user-avatar {
  width: 30px;
  height: 30px;
  margin-top: 2px;
  font-size: 12.5px;
}

.cc__msg-wrap {
  display: flex;
  flex-direction: column;
  max-width: 78%;
  min-width: 0;
}

.cc__steps {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 6px;
}

.cc__step-chip {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 23px;
  padding: 0 9px;
  font-size: 11.5px;
  font-weight: 500;
  color: #5856d6;
  cursor: default;
  background: #f1f0fe;
  border-radius: 999px;
}

.cc__bubble {
  padding: 11px 15px;
  font-size: 13.5px;
  line-height: 1.65;
}

.is-user .cc__bubble {
  color: #fff;
  background: linear-gradient(180deg, #1686ff 0%, #007aff 100%);
  border-radius: 18px 18px 4px 18px;
  box-shadow: 0 3px 10px rgb(0 122 255 / 22%);
}

.is-ai .cc__bubble {
  color: #1d1d1f;
  background: #f5f6fa;
  border-radius: 18px 18px 18px 4px;
  box-shadow: inset 0 0 0 0.5px rgb(0 0 0 / 5%);
}

.cc__bubble.is-error {
  color: #d70015;
  background: #fff1f0;
  box-shadow: inset 0 0 0 0.5px rgb(255 59 48 / 22%);
}

.cc__err-link {
  display: inline-flex;
  align-items: center;
  padding: 3px 9px;
  margin-top: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #007aff;
  cursor: pointer;
  background: #fff;
  border: 0;
  border-radius: 999px;
  box-shadow: 0 1px 3px rgb(0 0 0 / 8%);
}

.cc__thinking {
  display: inline-flex;
  gap: 10px;
  align-items: center;
  color: #6e6e73;
}

.cc__dots {
  display: inline-flex;
  gap: 4px;
  align-items: center;
}

.cc__dots i {
  display: block;
  width: 6px;
  height: 6px;
  background: #7c3aed;
  border-radius: 50%;
  animation: cc-dot 1.2s ease-in-out infinite;
}

.cc__dots i:nth-child(2) {
  animation-delay: 0.16s;
}

.cc__dots i:nth-child(3) {
  animation-delay: 0.32s;
}

@keyframes cc-dot {
  0%,
  80%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }

  40% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

/* 底部悬浮输入框 */
.cc__foot {
  padding: 10px 22px 18px;
}

.cc__composer {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  max-width: 820px;
  padding: 8px 8px 8px 16px;
  margin: 0 auto;
  background: #fff;
  border-radius: 22px;
  box-shadow:
    0 4px 18px rgb(15 23 42 / 7%),
    0 0 0 0.5px rgb(0 0 0 / 9%);
  transition: box-shadow var(--ap-dur-fast) var(--ap-ease);
}

.cc__composer:focus-within {
  box-shadow:
    0 0 0 3px rgb(0 122 255 / 16%),
    0 6px 22px rgb(15 23 42 / 9%);
}

.cc__textarea {
  flex: 1;
  min-width: 0;
  min-height: 24px;
  max-height: 112px;
  padding: 4px 0;
  font-family: inherit;
  font-size: 13.5px;
  line-height: 1.5;
  color: #1d1d1f;
  resize: none;
  background: transparent;
  border: 0;
  outline: none;
}

.cc__textarea::placeholder {
  color: #8e8e93;
}

.cc__send {
  display: grid;
  flex: none;
  place-items: center;
  width: 34px;
  height: 34px;
  color: #fff;
  cursor: pointer;
  background: linear-gradient(180deg, #1a88ff 0%, #007aff 100%);
  border: 0;
  border-radius: 50%;
  box-shadow: 0 2px 8px rgb(0 122 255 / 26%);
  transition:
    transform var(--ap-dur-fast) var(--ap-ease),
    opacity var(--ap-dur-fast);
}

.cc__send:hover:not(:disabled) {
  transform: scale(1.04);
}

.cc__send:active:not(:disabled) {
  transform: scale(0.92);
}

.cc__send:disabled {
  cursor: not-allowed;
  background: #d1d1d6;
  box-shadow: none;
  opacity: 0.65;
}

/* Markdown 排版精修 */
.cg-md :deep(p) {
  margin: 0 0 8px;
}

.cg-md :deep(p:last-child) {
  margin-bottom: 0;
}

.cg-md :deep(ul),
.cg-md :deep(ol) {
  padding-left: 20px;
  margin: 4px 0 8px;
}

.cg-md :deep(table) {
  display: block;
  max-width: 100%;
  margin: 8px 0 10px;
  overflow-x: auto;
  font-size: 12.5px;
  background: #fff;
  border-collapse: collapse;
  border-radius: 10px;
  box-shadow: inset 0 0 0 0.5px rgb(0 0 0 / 8%);
}

.cg-md :deep(th),
.cg-md :deep(td) {
  padding: 7px 11px;
  text-align: left;
  white-space: nowrap;
  border-bottom: 0.5px solid rgb(0 0 0 / 7%);
}

.cg-md :deep(th) {
  font-size: 12px;
  font-weight: 600;
  color: #6e6e73;
  background: #f8f8fb;
}

.cg-md :deep(code) {
  padding: 2px 6px;
  font-size: 12px;
  color: #5856d6;
  background: #ededfc;
  border-radius: 5px;
}

.cg-md :deep(h1),
.cg-md :deep(h2),
.cg-md :deep(h3),
.cg-md :deep(h4) {
  margin: 10px 0 6px;
  font-size: 15px;
  font-weight: 700;
  color: #1d1d1f;
}

@media (width <= 960px) {
  .cc__side {
    display: none;
  }

  .cc__top-new {
    display: inline-flex;
  }

  .cc__grid {
    grid-template-columns: 1fr;
  }
}
</style>
