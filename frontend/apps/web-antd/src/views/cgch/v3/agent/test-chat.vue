<script lang="ts" setup>
/**
 * AI 助手右侧实时对话模拟器（iMessage / Apple Intelligence 风格）：
 * 顶栏名称与人设实时联动、胶囊渠道切换、紫蓝 AI 气泡、转人工/违禁词/附件语义提示条、底部胶囊输入框。
 */
import { nextTick, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { message, Tooltip } from 'ant-design-vue';

import { getAiConfig, testAgentReply } from '#/api/channel/ai';

import { sharedAgentName } from './use-settings';

const props = defineProps<{ suggestions: string[] }>();

interface Msg {
  role: 'assistant' | 'user';
  content: string;
  note?: string;
  noteKind?: 'files' | 'forbidden' | 'handoff';
}

const channel = ref<'mail' | 'whatsapp'>('whatsapp');
const msgs = ref<Msg[]>([]);
const text = ref('');
const sending = ref(false);
const box = ref<HTMLElement>();

getAiConfig()
  .then((c) => {
    if (c?.agentName) sharedAgentName.value = c.agentName;
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
        note: '命中转人工规则 · 该会话将交由真人销售接手',
        noteKind: 'handoff',
      });
    } else {
      msgs.value.push({
        role: 'assistant',
        content: r.content,
        note: r.forbidden
          ? `命中违禁词「${r.forbidden}」· 将留为草稿待确认`
          : r.files?.length
            ? `附带云盘资料：${r.files.join('、')}`
            : undefined,
        noteKind: r.forbidden
          ? 'forbidden'
          : r.files?.length
            ? 'files'
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
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
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
    <!-- 顶栏 -->
    <header class="tc__head">
      <div class="tc__brand">
        <span class="tc__badge">
          <IconifyIcon icon="lucide:sparkles" class="size-3.5" />
        </span>
        <div class="min-w-0">
          <div class="tc__name" :title="sharedAgentName">
            {{ sharedAgentName }}
          </div>
          <div class="tc__sub">实时模拟预览</div>
        </div>
      </div>

      <div class="tc__controls">
        <div class="tc__seg">
          <button
            type="button"
            :class="{ on: channel === 'whatsapp' }"
            @click="channel = 'whatsapp'"
          >
            会话
          </button>
          <button
            type="button"
            :class="{ on: channel === 'mail' }"
            @click="channel = 'mail'"
          >
            邮件
          </button>
        </div>
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
          <span>
            {{ channel === 'whatsapp' ? 'WhatsApp / IM 接待模式' : '外贸邮件回复模式' }}
          </span>
        </div>
        <p class="tc__welcome-text">
          你好，我是 <b>{{ sharedAgentName }}</b>。扮演海外买家发一句话，立即检验当前知识库、人设与技能的实际表现。
        </p>
        <div class="tc__prompts">
          <div class="tc__prompts-label">点击快捷买家提问</div>
          <button
            v-for="s in props.suggestions"
            :key="s"
            type="button"
            class="tc__prompt"
            :disabled="sending"
            @click="send(s)"
          >
            <span class="min-w-0 flex-1">{{ s }}</span>
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
            <span>{{ sharedAgentName }}</span>
          </div>
          <div v-if="m.content" class="whitespace-pre-wrap break-words">
            {{ m.content }}
          </div>
          <div
            v-if="m.note"
            class="tc__note"
            :class="[
              `is-${m.noteKind || 'handoff'}`,
              { 'mt-2': m.content },
            ]"
          >
            <IconifyIcon
              :icon="
                m.noteKind === 'forbidden'
                  ? 'lucide:shield-alert'
                  : m.noteKind === 'files'
                    ? 'lucide:paperclip'
                    : 'lucide:user-round-check'
              "
              class="mt-0.5 size-3.5 shrink-0"
            />
            <span>{{ m.note }}</span>
          </div>
        </div>
      </div>

      <div v-if="sending" class="tc__typing">
        <span></span><span></span><span></span>
      </div>
    </div>

    <!-- 底部悬浮输入框 -->
    <footer class="tc__foot">
      <div class="tc__composer">
        <textarea
          v-model="text"
          rows="1"
          class="tc__input"
          placeholder="扮演海外买家输入提问…"
          :disabled="sending"
          @keydown="onKeydown"
        ></textarea>
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
  height: 58px;
  padding: 0 14px;
  background: rgb(255 255 255 / 78%);
  border-bottom: 0.5px solid var(--ap-separator);
}

.tc__brand {
  display: flex;
  gap: 9px;
  align-items: center;
  min-width: 0;
}

.tc__badge {
  display: grid;
  flex: none;
  place-items: center;
  width: 28px;
  height: 28px;
  color: #fff;
  background: linear-gradient(135deg, #af52de 0%, #5856d6 50%, #007aff 100%);
  border-radius: 9px;
  box-shadow: 0 3px 8px rgb(88 86 214 / 24%);
}

.tc__name {
  overflow: hidden;
  font-size: 13.5px;
  font-weight: 700;
  line-height: 1.2;
  color: #1d1d1f;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tc__sub {
  margin-top: 1px;
  font-size: 10.5px;
  color: #8e8e93;
}

.tc__controls {
  display: flex;
  flex: none;
  gap: 6px;
  align-items: center;
}

.tc__seg {
  display: inline-flex;
  padding: 2px;
  background: #eceef3;
  border-radius: 8px;
}

.tc__seg button {
  height: 24px;
  padding: 0 9px;
  font-size: 11.5px;
  font-weight: 500;
  color: #6e6e73;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 6px;
  transition: all var(--ap-dur-fast);
}

.tc__seg button.on {
  font-weight: 600;
  color: #1d1d1f;
  background: #fff;
  box-shadow: 0 1px 3px rgb(0 0 0 / 10%);
}

.tc__reset {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  color: #6e6e73;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 7px;
  transition:
    background-color var(--ap-dur-fast),
    color var(--ap-dur-fast);
}

.tc__reset:hover:not(:disabled) {
  color: #1d1d1f;
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
  padding: 15px;
  background: #fff;
  border-radius: 16px;
  box-shadow: var(--ap-shadow-rest);
}

.tc__welcome-head {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 11px;
  font-weight: 600;
  color: #6e6e73;
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
  color: #48484a;
}

.tc__prompts {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tc__prompts-label {
  font-size: 11px;
  font-weight: 600;
  color: #8e8e93;
}

.tc__prompt {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 8.5px 11px;
  font-size: 12px;
  line-height: 1.4;
  color: #1d1d1f;
  text-align: left;
  cursor: pointer;
  background: #f5f6fa;
  border: 0;
  border-radius: 10px;
  transition:
    background-color var(--ap-dur-fast),
    color var(--ap-dur-fast),
    transform var(--ap-dur-fast) var(--ap-ease);
}

.tc__prompt:hover:not(:disabled) {
  color: #007aff;
  background: #e8f2ff;
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
  max-width: 88%;
  padding: 9.5px 13px;
  font-size: 13px;
  line-height: 1.55;
}

.is-user .tc__bubble {
  color: #fff;
  background: linear-gradient(180deg, #1686ff 0%, #007aff 100%);
  border-radius: 16px 16px 4px 16px;
  box-shadow: 0 2px 8px rgb(0 122 255 / 20%);
}

.is-ai .tc__bubble {
  color: #1d1d1f;
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
  color: #6d4aff;
}

.tc__note {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  padding: 6px 9px;
  font-size: 11.5px;
  line-height: 1.45;
  border-radius: 9px;
}

.tc__note.is-handoff {
  color: #b25e00;
  background: #fff4e0;
}

.tc__note.is-forbidden {
  color: #d70015;
  background: #ffeceb;
}

.tc__note.is-files {
  color: #007aff;
  background: #e8f2ff;
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
  align-items: flex-end;
  padding: 7px 7px 7px 14px;
  background: #fff;
  border-radius: 20px;
  box-shadow:
    0 2px 10px rgb(0 0 0 / 5%),
    0 0 0 0.5px rgb(0 0 0 / 8%);
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
  min-height: 22px;
  max-height: 84px;
  padding: 3px 0;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.45;
  color: #1d1d1f;
  resize: none;
  background: transparent;
  border: 0;
  outline: none;
}

.tc__input::placeholder {
  color: #8e8e93;
}

.tc__send {
  display: grid;
  flex: none;
  place-items: center;
  width: 30px;
  height: 30px;
  color: #fff;
  cursor: pointer;
  background: linear-gradient(180deg, #1a88ff 0%, #007aff 100%);
  border: 0;
  border-radius: 50%;
  transition:
    transform var(--ap-dur-fast) var(--ap-ease),
    opacity var(--ap-dur-fast);
}

.tc__send:active:not(:disabled) {
  transform: scale(0.92);
}

.tc__send:disabled {
  cursor: not-allowed;
  background: #d1d1d6;
  opacity: 0.55;
}
</style>
