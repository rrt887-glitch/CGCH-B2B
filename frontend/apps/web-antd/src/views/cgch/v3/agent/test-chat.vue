<script lang="ts" setup>
/**
 * AI 助手右侧实时对话模拟器（iMessage / Apple Intelligence 风格）：
 * 顶栏单行不折行、可点击提示词胶囊、紫蓝 AI 标识气泡、底部悬浮胶囊输入框 + 圆形 ↑ 发送按钮。
 */
import { nextTick, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { message, Segmented, Tooltip } from 'ant-design-vue';

import { getAiConfig, testAgentReply } from '#/api/channel/ai';

const props = defineProps<{ suggestions: string[] }>();

interface Msg {
  role: 'assistant' | 'user';
  content: string;
  note?: string;
}

const channel = ref<'mail' | 'whatsapp'>('whatsapp');
const name = ref('Sales Assistant');
const msgs = ref<Msg[]>([]);
const text = ref('');
const sending = ref(false);
const box = ref<HTMLElement>();

getAiConfig()
  .then((c) => {
    if (c?.agentName) name.value = c.agentName;
  })
  .catch(() => {});

watch(channel, () => (msgs.value = []));

async function send(q?: string) {
  const content = (q ?? text.value).trim();
  if (!content || sending.value) return;
  msgs.value.push({ role: 'user', content });
  text.value = '';
  sending.value = true;
  await scroll();
  try {
    const r = await testAgentReply(
      channel.value,
      msgs.value
        .filter((m) => m.content)
        .map((m) => ({ role: m.role, content: m.content })),
    );
    if (r.handoff) {
      msgs.value.push({
        role: 'assistant',
        content: '',
        note: '这个问题会转给人工处理（命中转人工规则）',
      });
    } else {
      msgs.value.push({
        role: 'assistant',
        content: r.content,
        note: r.forbidden
          ? `回复中含有违禁内容「${r.forbidden}」，实际不会自动发出`
          : r.files?.length
            ? `附件：${r.files.join('、')}`
            : undefined,
      });
    }
  } catch (error: any) {
    msgs.value.pop();
    if (error?.message) message.error(error.message);
  } finally {
    sending.value = false;
    await scroll();
  }
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    send();
  }
}

async function scroll() {
  await nextTick();
  if (box.value) box.value.scrollTop = box.value.scrollHeight;
}
</script>

<template>
  <div class="tc">
    <!-- 顶栏：单行对齐，绝不折行 -->
    <header class="tc__head">
      <div class="tc__brand">
        <span class="tc__badge">
          <IconifyIcon icon="lucide:sparkles" class="size-3.5" />
        </span>
        <span class="tc__name" :title="name">{{ name }}</span>
      </div>
      <div class="tc__controls">
        <Segmented
          v-model:value="channel"
          size="small"
          :options="[
            { label: '会话', value: 'whatsapp' },
            { label: '邮件', value: 'mail' },
          ]"
        />
        <Tooltip title="清空预览对话">
          <button
            type="button"
            class="tc__reset"
            :disabled="!msgs.length"
            aria-label="清除聊天"
            @click="msgs = []"
          >
            <IconifyIcon icon="lucide:rotate-ccw" class="size-3.5" />
          </button>
        </Tooltip>
      </div>
    </header>

    <!-- 对话区 -->
    <div ref="box" class="tc__body">
      <div class="tc__welcome">
        <div class="tc__welcome-head">
          <span class="tc__live"></span>
          <span>实时效果预览</span>
        </div>
        <p class="tc__welcome-text">
          你好，我是 <b>{{ name }}</b>。扮演海外买家发一句话，立即测试当前配置下的回复效果。
        </p>
        <div class="tc__prompts">
          <div class="tc__prompts-label">试试点击提问</div>
          <button
            v-for="s in props.suggestions"
            :key="s"
            type="button"
            class="tc__prompt"
            :disabled="sending"
            @click="send(s)"
          >
            <span>{{ s }}</span>
            <IconifyIcon icon="lucide:arrow-up-right" class="tc__prompt-icon" />
          </button>
        </div>
      </div>

      <div
        v-for="(m, i) in msgs"
        :key="i"
        class="tc__row"
        :class="m.role === 'user' ? 'is-user' : 'is-ai'"
      >
        <div class="tc__bubble">
          <div v-if="m.role === 'assistant'" class="tc__ai-tag">
            <IconifyIcon icon="lucide:sparkles" class="size-3" />
            <span>{{ name }}</span>
          </div>
          <div v-if="m.content" class="whitespace-pre-wrap break-words">
            {{ m.content }}
          </div>
          <div
            v-if="m.note"
            class="tc__note"
            :class="{ 'mt-1.5 pt-1.5 border-t border-black/[0.06]': m.content }"
          >
            {{ m.note }}
          </div>
        </div>
      </div>

      <div v-if="sending" class="tc__typing">
        <span></span><span></span><span></span>
      </div>
    </div>

    <!-- 底部悬浮胶囊输入框 -->
    <footer class="tc__foot">
      <div class="tc__composer">
        <input
          v-model="text"
          type="text"
          class="tc__input"
          placeholder="输入买家的提问…"
          :disabled="sending"
          @keydown="onKeydown"
        />
        <button
          type="button"
          class="tc__send"
          :disabled="!text.trim() || sending"
          aria-label="发送"
          @click="send()"
        >
          <IconifyIcon icon="lucide:arrow-up" class="size-4" />
        </button>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.tc {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.tc__head {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  height: 54px;
  padding: 0 14px;
  background: rgb(255 255 255 / 72%);
  border-bottom: 0.5px solid var(--ap-separator);
}

.tc__brand {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.tc__badge {
  display: grid;
  flex: none;
  place-items: center;
  width: 26px;
  height: 26px;
  color: #fff;
  background: linear-gradient(135deg, #7455e6, #007aff);
  border-radius: 8px;
  box-shadow: 0 2px 6px rgb(92 86 230 / 24%);
}

.tc__name {
  overflow: hidden;
  font-size: 13.5px;
  font-weight: 700;
  color: var(--ap-label);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tc__controls {
  display: flex;
  flex: none;
  gap: 6px;
  align-items: center;
}

.tc__reset {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  color: var(--ap-label-3);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 7px;
  transition:
    background-color var(--ap-dur-fast),
    color var(--ap-dur-fast);
}

.tc__reset:hover:not(:disabled) {
  color: var(--ap-label);
  background: rgb(0 0 0 / 5%);
}

.tc__reset:disabled {
  cursor: default;
  opacity: 0.35;
}

.tc__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  overflow: auto;
}

.tc__welcome {
  padding: 14px;
  background: #fff;
  border-radius: 14px;
  box-shadow: var(--ap-shadow-rest);
}

.tc__welcome-head {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 11px;
  font-weight: 600;
  color: var(--ap-label-3);
}

.tc__live {
  width: 6px;
  height: 6px;
  background: #34c759;
  border-radius: 50%;
  box-shadow: 0 0 0 2.5px rgb(52 199 89 / 18%);
}

.tc__welcome-text {
  margin: 8px 0 12px;
  font-size: 12.5px;
  line-height: 1.55;
  color: var(--ap-label-2);
}

.tc__prompts {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tc__prompts-label {
  font-size: 11px;
  font-weight: 500;
  color: var(--ap-label-3);
}

.tc__prompt {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 8px 10px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--ap-label);
  text-align: left;
  cursor: pointer;
  background: #f5f6f9;
  border: 0;
  border-radius: 9px;
  transition:
    background-color var(--ap-dur-fast),
    color var(--ap-dur-fast),
    transform var(--ap-dur-fast) var(--ap-ease);
}

.tc__prompt:hover:not(:disabled) {
  color: var(--ap-accent);
  background: var(--ap-accent-soft);
}

.tc__prompt:active:not(:disabled) {
  transform: scale(0.985);
}

.tc__prompt-icon {
  flex: none;
  width: 13px;
  height: 13px;
  opacity: 0.45;
}

.tc__row {
  display: flex;
  animation: ap-bubble-in 280ms var(--ap-spring) both;
}

.tc__row.is-user {
  justify-content: flex-end;
}

.tc__row.is-ai {
  justify-content: flex-start;
}

.tc__bubble {
  max-width: 86%;
  padding: 9px 13px;
  font-size: 13px;
  line-height: 1.55;
}

.is-user .tc__bubble {
  color: #fff;
  background: #007aff;
  border-radius: 16px 16px 4px 16px;
  box-shadow: 0 2px 8px rgb(0 122 255 / 20%);
}

.is-ai .tc__bubble {
  color: var(--ap-label);
  background: #fff;
  border-radius: 16px 16px 16px 4px;
  box-shadow: var(--ap-shadow-rest);
}

.tc__ai-tag {
  display: flex;
  gap: 4px;
  align-items: center;
  margin-bottom: 4px;
  font-size: 11px;
  font-weight: 600;
  color: #7c5ce6;
}

.tc__note {
  font-size: 11.5px;
  color: var(--ap-orange-text);
}

.tc__typing {
  display: inline-flex;
  gap: 4px;
  align-self: flex-start;
  padding: 10px 14px;
  background: #fff;
  border-radius: 14px;
  box-shadow: var(--ap-shadow-rest);
}

.tc__typing span {
  width: 6px;
  height: 6px;
  background: #aeaeb2;
  border-radius: 50%;
  animation: tc-bounce 1.2s ease-in-out infinite;
}

.tc__typing span:nth-child(2) {
  animation-delay: 0.15s;
}

.tc__typing span:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes tc-bounce {
  0%,
  80%,
  100% {
    opacity: 0.4;
    transform: translateY(0);
  }

  40% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

.tc__foot {
  padding: 10px 12px 14px;
}

.tc__composer {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 6px 6px 6px 13px;
  background: #fff;
  border-radius: 999px;
  box-shadow:
    0 2px 10px rgb(0 0 0 / 5%),
    0 0 0 0.5px rgb(0 0 0 / 7%);
  transition: box-shadow var(--ap-dur-fast) var(--ap-ease);
}

.tc__composer:focus-within {
  box-shadow:
    0 0 0 2.5px var(--ap-accent-ring),
    0 4px 14px rgb(0 0 0 / 7%);
}

.tc__input {
  flex: 1;
  min-width: 0;
  height: 28px;
  font-size: 13px;
  color: var(--ap-label);
  background: transparent;
  border: 0;
  outline: none;
}

.tc__input::placeholder {
  color: var(--ap-label-4);
}

.tc__send {
  display: grid;
  flex: none;
  place-items: center;
  width: 30px;
  height: 30px;
  color: #fff;
  cursor: pointer;
  background: #007aff;
  border: 0;
  border-radius: 50%;
  transition:
    background-color var(--ap-dur-fast),
    transform var(--ap-dur-fast) var(--ap-ease),
    opacity var(--ap-dur-fast);
}

.tc__send:hover:not(:disabled) {
  background: #0a84ff;
}

.tc__send:active:not(:disabled) {
  transform: scale(0.92);
}

.tc__send:disabled {
  cursor: not-allowed;
  opacity: 0.32;
}
</style>
