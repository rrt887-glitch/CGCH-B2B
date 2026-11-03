<script lang="ts" setup>
/**
 * AI 用量：大模型用的是企业自己在「AI 接待设置」里填的 API Key，费用记在你的大模型账户里；
 * 这里按功能、按成员统计用了多少 token，方便对账和估算费用。
 */
import type { ChannelCreditApi } from '#/api/channel/credit';

import { computed, onMounted, ref } from 'vue';

import { Alert, Table } from 'ant-design-vue';

import { getCreditLedger, getCreditSummary } from '#/api/channel/credit';
import { getSimpleUserList } from '#/api/system/user';
import { fmtDateTime } from '#/utils/datetime';

defineOptions({ name: 'CgchAiUsage' });

const FEATURE: Record<string, string> = {
  whatsapp: '即时消息 AI 回复',
  mail: '邮件 AI 回复 / 提取',
  knowledge: '知识库检索',
  case: '成交案例',
  research: '客户背调',
  report: '工作报告',
  translate: '翻译',
  assistant: 'CRM 智枢问答',
  reactivate: '客户唤醒',
  other: '其他',
};
const feature = (k?: string) => FEATURE[k || 'other'] ?? k ?? '其他';

const summary = ref<ChannelCreditApi.Summary>();
const users = ref<{ id?: number; nickname: string }[]>([]);
const userName = (id?: number | string) => users.value.find((u) => u.id === Number(id))?.nickname ?? (id ? `#${id}` : '—');
const fmt = (n?: number) => (n ?? 0).toLocaleString();

const byFeature = computed(() =>
  Object.entries(summary.value?.monthTokensByChannel ?? {})
    .map(([k, n]) => ({ key: k, name: feature(k), tokens: n }))
    .sort((a, b) => b.tokens - a.tokens),
);
const byUser = computed(() =>
  Object.entries(summary.value?.monthTokensByUser ?? {})
    .map(([id, n]) => ({ key: id, name: userName(id), tokens: n }))
    .sort((a, b) => b.tokens - a.tokens),
);
const top = computed(() => Math.max(1, ...byFeature.value.map((x) => x.tokens)));

const records = ref<ChannelCreditApi.Ledger[]>([]);
const total = ref(0);
const pageNo = ref(1);
const loading = ref(false);
async function loadRecords() {
  loading.value = true;
  try {
    const res = await getCreditLedger({ pageNo: pageNo.value, pageSize: 20, type: 'consume' });
    records.value = res.list;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  [summary.value, users.value] = await Promise.all([getCreditSummary(), getSimpleUserList().catch(() => [])]);
  loadRecords();
});
</script>

<template>
  <div class="flex h-full flex-col gap-4 overflow-auto rounded-xl bg-white p-5">
    <div>
      <h2 class="m-0 text-lg font-semibold text-[#111827]">AI 用量</h2>
      <p class="m-0 mt-1 text-[13px] text-[#4B5563]">
        AI 用的是你在「AI 接待设置」里填的大模型 API Key，费用记在你自己的大模型账户里，这里不扣费。下面按功能、按成员统计本月用了多少 token，方便对账。
      </p>
    </div>

    <div class="grid grid-cols-1 gap-3 md:grid-cols-3">
      <div class="rounded-xl border border-solid border-[#E5E7EB] p-4">
        <div class="text-[13px] text-[#4B5563]">本月 token 用量</div>
        <div class="text-[26px] font-semibold tabular-nums text-[#111827]">{{ fmt(summary?.monthTokens) }}</div>
      </div>
      <div class="rounded-xl border border-solid border-[#E5E7EB] p-4">
        <div class="text-[13px] text-[#4B5563]">用得最多的功能</div>
        <div class="truncate text-lg font-semibold text-[#111827]">{{ byFeature[0]?.name ?? '—' }}</div>
        <div class="text-xs text-[#6B7280]">{{ byFeature[0] ? `${fmt(byFeature[0].tokens)} token` : '本月还没有 AI 调用' }}</div>
      </div>
      <div class="rounded-xl border border-solid border-[#E5E7EB] p-4">
        <div class="text-[13px] text-[#4B5563]">用得最多的成员</div>
        <div class="truncate text-lg font-semibold text-[#111827]">{{ byUser[0]?.name ?? '—' }}</div>
        <div class="text-xs text-[#6B7280]">{{ byUser[0] ? `${fmt(byUser[0].tokens)} token` : '—' }}</div>
      </div>
    </div>

    <Alert
      v-if="summary && !summary.monthTokens"
      type="info"
      show-icon
      message="本月还没有 AI 调用。先到「AI 接待设置」配置大模型并测试连接，AI 接待、翻译、背调等功能就会开始工作。"
    />

    <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
      <section class="rounded-xl border border-solid border-[#E5E7EB] p-4">
        <h3 class="m-0 mb-3 text-sm font-semibold">按功能（本月）</h3>
        <div v-if="!byFeature.length" class="py-6 text-center text-sm text-[#6B7280]">暂无数据</div>
        <div v-for="f in byFeature" :key="f.key" class="mb-2.5">
          <div class="mb-1 flex justify-between text-[13px]"><span>{{ f.name }}</span><span class="tabular-nums text-[#4B5563]">{{ fmt(f.tokens) }}</span></div>
          <div class="h-1.5 rounded-full bg-[#F3F4F6]"><div class="h-1.5 rounded-full bg-[#2563EB]" :style="{ width: `${(f.tokens / top) * 100}%` }"></div></div>
        </div>
      </section>
      <section class="rounded-xl border border-solid border-[#E5E7EB] p-4">
        <h3 class="m-0 mb-3 text-sm font-semibold">按成员（本月）</h3>
        <Table
          size="small"
          row-key="key"
          :pagination="false"
          :data-source="byUser"
          :columns="[
            { title: '成员', dataIndex: 'name' },
            { title: 'token', dataIndex: 'tokens', align: 'right' },
          ]"
          :locale="{ emptyText: '暂无数据' }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'tokens'">{{ fmt(record.tokens) }}</template>
          </template>
        </Table>
      </section>
    </div>

    <section class="rounded-xl border border-solid border-[#E5E7EB] p-4">
      <h3 class="m-0 mb-3 text-sm font-semibold">调用记录</h3>
      <Table
        size="small"
        row-key="id"
        :loading="loading"
        :data-source="records"
        :pagination="{ current: pageNo, pageSize: 20, total, showSizeChanger: false, onChange: (p: number) => { pageNo = p; loadRecords(); } }"
        :columns="[
          { title: '时间', dataIndex: 'createTime', width: 150 },
          { title: '功能', dataIndex: 'channel', width: 170 },
          { title: '成员', dataIndex: 'userId', width: 120 },
          { title: 'token', dataIndex: 'tokens', width: 100, align: 'right' },
          { title: '说明', dataIndex: 'remark', ellipsis: true },
        ]"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'createTime'">{{ fmtDateTime(record.createTime, 'MM-DD HH:mm:ss') }}</template>
          <template v-else-if="column.dataIndex === 'channel'">{{ feature(record.channel) }}</template>
          <template v-else-if="column.dataIndex === 'userId'">{{ userName(record.userId) }}</template>
          <template v-else-if="column.dataIndex === 'tokens'">{{ fmt(record.tokens) }}</template>
        </template>
      </Table>
    </section>
  </div>
</template>
