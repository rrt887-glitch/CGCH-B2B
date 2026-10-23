<script lang="ts" setup>
/**
 * 数据看板：询盘量、回复率、AI 处理量、智点消耗、线索转化；与上一个同等周期对比。
 */
import type { EChartsOption, EchartsUIType } from '@vben/plugins/echarts';

import type { ChannelDashboardApi } from '#/api/channel/dashboard';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { EchartsUI, useEcharts } from '@vben/plugins/echarts';
import { usePreferences } from '@vben/preferences';

import { Button, Card, Col, Empty, Radio, RadioGroup, Row, Select, Spin, Table, Tooltip } from 'ant-design-vue';

import { DRAFT_REASON } from '#/api/channel/ai';
import { getDashboardOverview } from '#/api/channel/dashboard';
import { getSimpleUserList } from '#/api/system/user';

defineOptions({ name: 'CgchBoardOverview' });

const router = useRouter();
const { isDark } = usePreferences();

// 分类色（固定顺序）：1 蓝、2 橙；深色模式用为深色背景校准过的同色相阶
const palette = computed(() =>
  isDark.value
    ? { s1: '#3987e5', s2: '#d95926', grid: '#2e2e2c', axis: '#8a8984', text: '#c3c2b7', surface: '#1a1a19' }
    : { s1: '#2a78d6', s2: '#eb6834', grid: '#ecebe8', axis: '#9a9993', text: '#52514e', surface: '#ffffff' },
);

const days = ref(30);
const userId = ref<number>();
const loading = ref(false);
const data = ref<ChannelDashboardApi.Overview>();
const users = ref<{ id: number; nickname: string }[]>([]);
const userName = (id: number) => users.value.find((u) => u.id === id)?.nickname ?? `#${id}`;

const cur = computed(() => data.value?.current);
// 可选的业务员：能看全部时列出所有人；否则只列数据权限内的人（只有自己时不显示筛选）
const userOptions = computed(() => {
  const s = data.value?.scope;
  if (!s) return [];
  const list = s.all ? users.value : users.value.filter((u) => s.userIds.includes(u.id));
  return list.map((u) => ({ value: u.id, label: u.nickname }));
});
const scopeLabel = computed(() => {
  const s = data.value?.scope;
  if (!s) return '';
  if (userId.value) return `仅 ${userName(userId.value)}`;
  if (s.all) return '全部业务员';
  return s.userIds.length > 1 ? `本部门 ${s.userIds.length} 人` : '仅本人';
});
const prev = computed(() => data.value?.previous);

const pct = (v?: number) => `${((v ?? 0) * 100).toFixed(1)}%`;
/** 环比：比率类显示百分点差，数量类显示增减百分比 */
function delta(key: keyof ChannelDashboardApi.Totals, rate = false) {
  const a = Number(cur.value?.[key] ?? 0);
  const b = Number(prev.value?.[key] ?? 0);
  if (rate) {
    const d = (a - b) * 100;
    return { text: `${d >= 0 ? '+' : ''}${d.toFixed(1)} 个百分点`, up: d >= 0, flat: Math.abs(d) < 0.05 };
  }
  if (b === 0) return { text: a === 0 ? '与上期持平' : '上期无数据', up: true, flat: true };
  const d = ((a - b) / b) * 100;
  return { text: `${d >= 0 ? '+' : ''}${d.toFixed(0)}%`, up: d >= 0, flat: Math.abs(d) < 0.5 };
}

const tiles = computed(() => {
  const c = cur.value;
  if (!c) return [];
  return [
    { label: '新询盘', value: c.inquiries.toLocaleString(), sub: `邮件 ${c.mailIn} · WhatsApp 会话 ${c.waConversations}`, d: delta('inquiries'), tip: '收到的邮件数 + 有客户来信的 WhatsApp 会话数' },
    { label: '回复率', value: pct(c.replyRate), sub: `已回复 ${c.mailReplied + c.waRepliedConversations} / ${c.inquiries}`, d: delta('replyRate', true), tip: '已回复的邮件和会话占新询盘的比例' },
    { label: 'AI 发送占比', value: pct(c.aiShare), sub: `AI 发出 ${c.mailAiOut + c.waAiOut} / 全部 ${c.mailOut + c.waOut}`, d: delta('aiShare', true), tip: '发出的邮件和 WhatsApp 消息中，由 AI 自动回复或 AI 起草后发出的比例' },
    { label: '新线索', value: c.cluesCreated.toLocaleString(), sub: `询盘自动建档 ${c.cluesFromMail + c.cluesFromWhatsApp} · 已转客户 ${c.cluesConverted}`, d: delta('cluesCreated'), tip: '本期新建的 CRM 线索；自动建档来源为邮件或 WhatsApp' },
    { label: '智点消耗', value: c.creditsConsumed.toLocaleString(), sub: `AI 草稿 ${c.aiDrafts} 份`, d: delta('creditsConsumed'), tip: 'AI 起草、自动回复、信息提取和知识库检索消耗的智点', neutral: true },
  ];
});

const pendingRows = computed(() => {
  const m = data.value?.pendingReasons ?? {};
  const total = Object.values(m).reduce((s, n) => s + n, 0);
  return Object.entries(m)
    .sort((a, b) => b[1] - a[1])
    .map(([r, n]) => ({ reason: DRAFT_REASON[r] ?? r, n, w: total ? (n / total) * 100 : 0 }));
});

const userColumns = [
  { title: '业务员', key: 'name' },
  { title: '收到邮件', dataIndex: 'mailIn', key: 'mailIn', align: 'right' as const },
  { title: 'WhatsApp 来信', dataIndex: 'waIn', key: 'waIn', align: 'right' as const },
  { title: '发出消息', dataIndex: 'out', key: 'out', align: 'right' as const },
  { title: '其中 AI', key: 'ai', align: 'right' as const },
];

// ===== 图表 =====
const inqRef = ref<EchartsUIType>();
const outRef = ref<EchartsUIType>();
const creditRef = ref<EchartsUIType>();
const { renderEcharts: renderInq } = useEcharts(inqRef);
const { renderEcharts: renderOut } = useEcharts(outRef);
const { renderEcharts: renderCredit } = useEcharts(creditRef);

const label = (d: string) => d.slice(5);

function barOption(series: { name: string; color: string; values: number[] }[], showLegend: boolean): EChartsOption {
  const p = palette.value;
  const x = (data.value?.daily ?? []).map((d) => label(d.date));
  const last = series.length - 1;
  return {
    backgroundColor: 'transparent',
    grid: { left: 8, right: 8, top: showLegend ? 36 : 12, bottom: 4, containLabel: true },
    legend: showLegend
      ? { top: 0, left: 0, icon: 'roundRect', itemWidth: 10, itemHeight: 10, textStyle: { color: p.text } }
      : undefined,
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow', shadowStyle: { color: isDark.value ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.04)' } },
    },
    xAxis: {
      type: 'category',
      data: x,
      axisLine: { lineStyle: { color: p.grid } },
      axisTick: { show: false },
      axisLabel: { color: p.axis, hideOverlap: true },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: p.grid } },
      axisLabel: { color: p.axis },
    },
    series: series.map((s, i) => ({
      name: s.name,
      type: 'bar',
      stack: series.length > 1 ? 'total' : undefined,
      data: s.values,
      barMaxWidth: 16,
      itemStyle: {
        color: s.color,
        borderColor: p.surface,
        borderWidth: series.length > 1 ? 1 : 0,
        borderRadius: i === last ? [4, 4, 0, 0] : 0,
      },
      emphasis: { focus: 'series' },
    })),
  };
}

async function renderCharts() {
  const d = data.value?.daily ?? [];
  const p = palette.value;
  await nextTick();
  renderInq(barOption([
    { name: '邮件', color: p.s1, values: d.map((x) => x.mailIn) },
    { name: 'WhatsApp', color: p.s2, values: d.map((x) => x.waIn) },
  ], true));
  renderOut(barOption([
    { name: 'AI 发出', color: p.s1, values: d.map((x) => x.aiOut) },
    { name: '人工发出', color: p.s2, values: d.map((x) => x.humanOut) },
  ], true));
  renderCredit(barOption([{ name: '智点', color: p.s1, values: d.map((x) => x.credits) }], false));
}

async function load() {
  loading.value = true;
  try {
    data.value = await getDashboardOverview(days.value, userId.value);
  } finally {
    loading.value = false;
  }
  await renderCharts();
}

watch([days, userId], load);
watch(isDark, renderCharts);
onMounted(async () => {
  users.value = (await getSimpleUserList()) as any;
  await load();
});
</script>

<template>
  <Page auto-content-height>
    <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
      <div>
        <div class="text-lg font-semibold">数据看板</div>
        <div class="text-sm text-gray-500">
          {{ data ? `${data.startDate} 至今，与之前 ${data.days} 天对比 · ${scopeLabel}` : '加载中…' }}
        </div>
      </div>
      <div class="flex flex-wrap items-center gap-3">
      <Select
        v-if="userOptions.length > 1"
        v-model:value="userId"
        allow-clear
        show-search
        option-filter-prop="label"
        :options="userOptions"
        :placeholder="data?.scope.all ? '全部业务员' : '本部门全部'"
        class="w-44"
      />
      <RadioGroup v-model:value="days" button-style="solid">
        <Radio.Button :value="7">近 7 天</Radio.Button>
        <Radio.Button :value="30">近 30 天</Radio.Button>
        <Radio.Button :value="90">近 90 天</Radio.Button>
      </RadioGroup>
      </div>
    </div>

    <Spin :spinning="loading">
      <div class="mb-4 grid grid-cols-2 gap-4 lg:grid-cols-5">
        <Card v-for="t in tiles" :key="t.label" size="small" :bordered="false">
          <Tooltip :title="t.tip"><div class="text-sm text-gray-500">{{ t.label }}</div></Tooltip>
          <div class="my-1 text-2xl font-semibold tabular-nums">{{ t.value }}</div>
          <div class="text-xs text-gray-500">{{ t.sub }}</div>
          <div class="mt-1 text-xs" :class="t.d.flat || t.neutral ? 'text-gray-500' : t.d.up ? 'text-green-600' : 'text-red-500'">
            {{ t.d.flat ? '' : t.d.up ? '▲ ' : '▼ ' }}环比 {{ t.d.text }}
          </div>
        </Card>
      </div>

      <Row :gutter="16">
        <Col :xs="24" :lg="12" class="mb-4">
          <Card title="每日新询盘" size="small" :bordered="false">
            <EchartsUI ref="inqRef" height="260px" />
          </Card>
        </Col>
        <Col :xs="24" :lg="12" class="mb-4">
          <Card title="每日发出消息" size="small" :bordered="false">
            <EchartsUI ref="outRef" height="260px" />
          </Card>
        </Col>
      </Row>

      <Row :gutter="16">
        <Col :xs="24" :lg="12" class="mb-4">
          <Card title="每日智点消耗" size="small" :bordered="false">
            <template #extra><Button type="link" size="small" @click="router.push('/v3/settings?tab=usage')">明细</Button></template>
            <EchartsUI ref="creditRef" height="220px" />
          </Card>
        </Col>
        <Col :xs="24" :lg="12" class="mb-4">
          <Card title="AI 草稿处理" size="small" :bordered="false">
            <div v-if="cur" class="mb-3 grid grid-cols-4 gap-2 text-center">
              <div><div class="text-xl font-semibold tabular-nums">{{ cur.aiSent }}</div><div class="text-xs text-gray-500">已发出</div></div>
              <div><div class="text-xl font-semibold tabular-nums">{{ cur.aiPending }}</div><div class="text-xs text-gray-500">待人工确认</div></div>
              <div><div class="text-xl font-semibold tabular-nums">{{ cur.aiHandoff }}</div><div class="text-xs text-gray-500">转人工</div></div>
              <div><div class="text-xl font-semibold tabular-nums">{{ cur.aiDiscarded }}</div><div class="text-xs text-gray-500">已放弃</div></div>
            </div>
            <div class="mb-1 text-sm text-gray-500">待人工确认的原因</div>
            <Empty v-if="pendingRows.length === 0" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="没有待确认的草稿" />
            <div v-for="r in pendingRows" :key="r.reason" class="py-1">
              <div class="flex justify-between text-sm"><span>{{ r.reason }}</span><span class="tabular-nums">{{ r.n }}</span></div>
              <div class="h-1.5 rounded bg-gray-100 dark:bg-gray-800">
                <div class="h-1.5 rounded" :style="{ width: `${r.w}%`, background: palette.s1 }" />
              </div>
            </div>
          </Card>
        </Col>
      </Row>

      <Card title="按业务员" size="small" :bordered="false">
        <Table :columns="userColumns" :data-source="data?.users ?? []" row-key="userId" size="small" :pagination="false">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">{{ userName(record.userId) }}</template>
            <template v-else-if="column.key === 'ai'">
              {{ record.aiOut }}
              <span class="text-xs text-gray-500">（{{ record.out ? Math.round((record.aiOut / record.out) * 100) : 0 }}%）</span>
            </template>
          </template>
          <template #emptyText>本期没有渠道消息；先在「渠道管理」里接入邮箱或 WhatsApp</template>
        </Table>
      </Card>
    </Spin>
  </Page>
</template>
