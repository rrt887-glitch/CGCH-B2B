<script lang="ts" setup>
/** 智能体页右侧的测试对话：输入客户问题，查看数字员工按当前配置给出的回复 */
import { nextTick, ref, watch } from 'vue';

import { Button, Input, message, Segmented } from 'ant-design-vue';

import { getAiConfig, testAgentReply } from '#/api/channel/ai';

const props = defineProps<{ suggestions: string[] }>();

interface Msg {
  role: 'assistant' | 'user';
  content: string;
  note?: string;
}

const channel = ref<'mail' | 'whatsapp'>('whatsapp');
const name = ref('数字员工');
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
      msgs.value.filter((m) => m.content).map((m) => ({ role: m.role, content: m.content })),
    );
    if (r.handoff) {
      msgs.value.push({ role: 'assistant', content: '', note: '这个问题会转给人工处理（命中转人工规则）' });
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

async function scroll() {
  await nextTick();
  if (box.value) box.value.scrollTop = box.value.scrollHeight;
}
</script>

<template>
  <div class="flex h-full flex-col">
    <div class="flex items-center justify-between border-b px-4 py-3">
      <span class="font-semibold">{{ name }}</span>
      <div class="flex items-center gap-2">
        <Segmented
          v-model:value="channel"
          size="small"
          :options="[
            { label: '会话', value: 'whatsapp' },
            { label: '邮件', value: 'mail' },
          ]"
        />
        <Button size="small" type="link" @click="msgs = []">清除聊天</Button>
      </div>
    </div>
    <div ref="box" class="flex-1 space-y-3 overflow-auto p-4">
      <div class="rounded-lg bg-gray-50 p-3 text-sm text-gray-600">
        你好，我是{{ name }}，你可以输入客户问题，测试数字员工的实际回复效果。
        <div class="mt-2 text-xs text-gray-400">你可以这样问：</div>
        <div class="mt-1 flex flex-col items-start gap-1">
          <a v-for="s in props.suggestions" :key="s" class="text-left text-xs" @click="send(s)">{{ s }}</a>
        </div>
      </div>
      <div v-for="(m, i) in msgs" :key="i" class="flex" :class="m.role === 'user' ? 'justify-end' : 'justify-start'">
        <div
          class="max-w-[85%] whitespace-pre-wrap rounded-lg px-3 py-2 text-sm"
          :class="m.role === 'user' ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-800'"
        >
          {{ m.content }}
          <div v-if="m.note" class="mt-1 text-xs" :class="m.content ? 'text-orange-600' : 'text-gray-500'">{{ m.note }}</div>
        </div>
      </div>
      <div v-if="sending" class="text-xs text-gray-400">{{ name }} 正在输入…</div>
    </div>
    <div class="border-t p-3">
      <Input.Search
        v-model:value="text"
        :loading="sending"
        enter-button="发送"
        placeholder="输入客户的问题"
        @search="() => send()"
      />
    </div>
  </div>
</template>
