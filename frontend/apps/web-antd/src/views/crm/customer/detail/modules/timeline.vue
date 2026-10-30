<script lang="ts" setup>
/**
 * 客户 360 · 沟通记录：这个客户在 WhatsApp、Facebook、Instagram、官网客服和邮件里的所有往来，按时间倒序合在一起。
 * 点「打开会话」到会话工作台接着聊（需要是会话处理人或其主管）。
 */
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { Button, Empty, Segmented, Spin, Tag } from 'ant-design-vue';

import { requestClient } from '#/api/request';
import { fmtDateTime } from '#/utils/datetime';

interface Item {
  channel: 'im' | 'mail';
  platform: string;
  direction: number;
  time: number | string;
  text?: string;
  subject?: string;
  type?: string;
  fileName?: string;
  ai?: boolean;
  conversationId?: number;
  mailId?: number;
  contact?: string;
}

const props = defineProps<{ customerId: number }>();
const router = useRouter();
const items = ref<Item[]>([]);
const counts = ref<Record<string, number>>({});
const more = ref(false);
const loading = ref(false);
const filter = ref<'all' | 'im' | 'mail'>('all');

const PLATFORM: Record<string, { color: string; label: string }> = {
  whatsapp: { label: 'WhatsApp', color: 'green' },
  messenger: { label: 'Facebook', color: 'blue' },
  instagram: { label: 'Instagram', color: 'magenta' },
  web: { label: '官网客服', color: 'geekblue' },
  youtube: { label: 'YouTube', color: 'red' },
  tiktok: { label: 'TikTok', color: 'default' },
  mail: { label: '邮件', color: 'orange' },
};
const shown = computed(() => (filter.value === 'all' ? items.value : items.value.filter((i) => i.channel === filter.value)));
const options = computed(() => [
  { label: '全部', value: 'all' },
  { label: `即时消息 ${counts.value.im ?? 0}`, value: 'im' },
  { label: `邮件 ${counts.value.mail ?? 0}`, value: 'mail' },
]);

async function load(reset = true) {
  loading.value = true;
  try {
    const last = items.value[items.value.length - 1];
    const before = !reset && last ? new Date(last.time as any).getTime() : undefined;
    const r = await requestClient.get<{ counts: Record<string, number>; list: Item[]; more: boolean }>('/channel/customer-timeline/list', {
      params: { customerId: props.customerId, before, limit: 50 },
    });
    items.value = reset ? r.list : [...items.value, ...r.list];
    counts.value = r.counts;
    more.value = r.more;
  } finally {
    loading.value = false;
  }
}

function open(i: Item) {
  if (i.channel === 'im' && i.conversationId) router.push({ path: '/v3/conversation', query: { channel: 'whatsapp', ref: String(i.conversationId) } });
  else if (i.mailId) router.push({ path: '/v3/conversation', query: { channel: 'mail', ref: String(i.mailId) } });
}

watch(() => props.customerId, () => load());
onMounted(() => load());
</script>

<template>
  <div>
    <div class="mb-3 flex items-center gap-3">
      <Segmented v-model:value="filter" :options="options" size="small" />
      <span class="text-xs text-gray-400">共 {{ counts.conversations ?? 0 }} 个即时消息会话</span>
      <div class="flex-1"></div>
      <Button size="small" @click="load()">刷新</Button>
    </div>
    <Spin :spinning="loading">
      <Empty v-if="!loading && shown.length === 0" description="还没有和这个客户的沟通记录" />
      <div v-for="(i, k) in shown" :key="`${i.channel}-${i.conversationId ?? i.mailId}-${k}`" class="flex gap-3 border-b py-3 last:border-b-0">
        <div class="w-28 shrink-0 text-xs text-gray-400">{{ fmtDateTime(i.time, 'YYYY-MM-DD HH:mm') }}</div>
        <div class="min-w-0 flex-1">
          <div class="mb-1 flex flex-wrap items-center gap-2 text-xs">
            <Tag :color="PLATFORM[i.platform]?.color" class="m-0">{{ PLATFORM[i.platform]?.label || i.platform }}</Tag>
            <span :class="i.direction === 1 ? 'text-gray-700' : 'text-[#2563EB]'">{{ i.direction === 1 ? `客户 ${i.contact || ''}` : i.ai ? 'AI 回复' : '我方' }}</span>
            <span v-if="i.subject" class="font-medium text-gray-800">{{ i.subject }}</span>
            <a class="ml-auto" @click="open(i)">{{ i.channel === 'mail' ? '打开邮件' : '打开会话' }}</a>
          </div>
          <div class="whitespace-pre-wrap break-words text-sm text-gray-700 line-clamp-4">
            {{ i.text || (i.fileName ? `[文件] ${i.fileName}` : i.type && i.type !== 'text' ? `[${i.type}]` : '') }}
          </div>
        </div>
      </div>
      <div v-if="more" class="mt-3 text-center"><Button size="small" :loading="loading" @click="load(false)">加载更早的记录</Button></div>
    </Spin>
  </div>
</template>
