<script lang="ts" setup>
/**
 * 通知中心：需要人处理的事——AI 转人工、超时 AI 接手、新询盘建档、唤醒客户回复、智点不足。
 * 通知以站内信发送，顶部铃铛同步显示；管理员可按类型关闭。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { useAccess } from '@vben/access';

import { Badge, Button, Card, Empty, message, Pagination, Radio, RadioGroup, Space, Spin, Switch, Tag } from 'ant-design-vue';

import { getAiConfig, saveAiConfig } from '#/api/channel/ai';
import { getMyNotifyMessagePage, updateAllNotifyMessageRead, updateNotifyMessageRead } from '#/api/system/notify/message';

defineOptions({ name: 'CgchBoardNotice' });

const router = useRouter();
const { hasAccessByCodes } = useAccess();
const canConfig = computed(() => hasAccessByCodes(['channel:ai:config']));

const TYPES: Record<string, { color: string; desc: string; label: string }> = {
  handoff: { color: 'red', label: '需人工处理', desc: 'AI 转人工、回复含违禁词被拦截、自动发送失败' },
  takeover: { color: 'orange', label: 'AI 已接手', desc: '工作时间业务员优先，超时无人回复由 AI 接手' },
  new_lead: { color: 'blue', label: '新询盘', desc: '陌生人来信，已自动建线索' },
  reactivate_reply: { color: 'green', label: '客户回复', desc: '客户唤醒后客户回复了' },
  credit_low: { color: 'purple', label: '智点不足', desc: '余额跌破 100 或用完（发给租户联系人）' },
};

const filter = ref<'all' | 'unread'>('unread');
const list = ref<any[]>([]);
const total = ref(0);
const pageNo = ref(1);
const loading = ref(false);
async function load() {
  loading.value = true;
  try {
    const res = await getMyNotifyMessagePage({ pageNo: pageNo.value, pageSize: 20, readStatus: filter.value === 'unread' ? false : undefined } as any);
    list.value = res.list;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
}
const typeOf = (x: any) => (x.templateParams?.type as string) ?? '';
async function handle(x: any) {
  if (!x.readStatus) {
    await updateNotifyMessageRead([x.id]);
    x.readStatus = true;
  }
  const link = x.templateParams?.link as string | undefined;
  if (link) router.push(link);
}
async function markRead(x: any) {
  await updateNotifyMessageRead([x.id]);
  x.readStatus = true;
  if (filter.value === 'unread') load();
}
async function markAll() {
  await updateAllNotifyMessageRead();
  message.success('已全部标记为已读');
  load();
}

// ===== 通知设置 =====
const muted = reactive<Record<string, boolean>>({});
async function loadSettings() {
  if (!canConfig.value) return;
  const cfg = await getAiConfig();
  const set = new Set((cfg.notifyMuted ?? '').split(',').filter(Boolean));
  Object.keys(TYPES).forEach((k) => (muted[k] = set.has(k)));
}
async function toggle(type: string, on: boolean) {
  muted[type] = !on;
  const cfg = await getAiConfig();
  await saveAiConfig({ ...cfg, llmApiKey: undefined, notifyMuted: Object.keys(muted).filter((k) => muted[k]).join(',') });
  message.success(on ? '已开启' : '已关闭，之后不再发送这类通知');
}

onMounted(async () => {
  await Promise.all([load(), loadSettings()]);
});
</script>

<template>
  <Page auto-content-height>
    <div class="flex gap-4">
      <Card title="通知中心" :bordered="false" class="min-w-0 flex-1">
        <template #extra>
          <Space>
            <RadioGroup v-model:value="filter" size="small" button-style="solid" @change="() => { pageNo = 1; load(); }">
              <Radio.Button value="unread">未读</Radio.Button>
              <Radio.Button value="all">全部</Radio.Button>
            </RadioGroup>
            <Button size="small" @click="markAll">全部已读</Button>
          </Space>
        </template>
        <Spin :spinning="loading">
          <Empty v-if="list.length === 0" :description="filter === 'unread' ? '没有未读通知' : '还没有通知'" />
          <div v-for="x in list" :key="x.id" class="flex items-start justify-between gap-3 border-b py-3 last:border-b-0">
            <div class="min-w-0">
              <div class="flex items-center gap-2">
                <Badge v-if="!x.readStatus" status="processing" />
                <Tag v-if="TYPES[typeOf(x)]" :color="TYPES[typeOf(x)]!.color">{{ TYPES[typeOf(x)]!.label }}</Tag>
                <span class="text-xs text-gray-400">{{ x.createTime ? new Date(x.createTime).toLocaleString() : '' }}</span>
              </div>
              <div class="mt-1" :class="x.readStatus ? 'text-gray-500' : ''">{{ x.templateContent }}</div>
            </div>
            <Space class="shrink-0">
              <Button v-if="x.templateParams?.link" size="small" type="primary" ghost @click="handle(x)">去处理</Button>
              <Button v-if="!x.readStatus" size="small" type="link" @click="markRead(x)">已读</Button>
            </Space>
          </div>
          <div v-if="total > 20" class="mt-3 text-right">
            <Pagination v-model:current="pageNo" :total="total" :page-size="20" size="small" @change="load" />
          </div>
        </Spin>
      </Card>

      <Card v-if="canConfig" title="通知设置" :bordered="false" class="w-80 shrink-0" size="small">
        <div class="mb-3 text-xs text-gray-500">通知发给对应的业务员（渠道账号或客户的负责人），顶部铃铛同步显示。同一会话 30 分钟内只提醒一次。</div>
        <div v-for="(t, k) in TYPES" :key="k" class="mb-3 flex items-start justify-between gap-2">
          <div>
            <div class="text-sm">{{ t.label }}</div>
            <div class="text-xs text-gray-500">{{ t.desc }}</div>
          </div>
          <Switch :checked="!muted[k]" size="small" @change="(v: any) => toggle(k as string, !!v)" />
        </div>
      </Card>
    </div>
  </Page>
</template>
