<script lang="ts" setup>
/**
 * 经营分析：询盘量、回复率、AI 处理量、线索/客户/订单转化；按平台、国家与业务员多维拆解。
 */
import type { EChartsOption, EchartsUIType } from '@vben/plugins/echarts';

import type { ChannelDashboardApi } from '#/api/channel/dashboard';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';
import { EchartsUI, useEcharts } from '@vben/plugins/echarts';
import { usePreferences } from '@vben/preferences';

import { Col, Row, Select, Spin, Table, Tooltip } from 'ant-design-vue';

import { DRAFT_REASON } from '#/api/channel/ai';
import { getDashboardOverview } from '#/api/channel/dashboard';
import { getSimpleUserList } from '#/api/system/user';
import { countryName } from '#/views/cgch/crm/shared/countries';
import { crmAvatarBg, crmInitial, fmtMoney } from '#/views/cgch/v3/crm/components/shared';

defineOptions({ name: 'CgchBoardOverview' });

const { isDark } = usePreferences();

const palette = computed(() =>
  isDark.value
    ? { s1: '#0a84ff', s2: '#bf5af2', grid: 'rgba(255,255,255,0.08)', axis: '#8e8e93', text: '#d1d1d6', surface: '#1c1c1e' }
    : { s1: '#007aff', s2: '#af52de', grid: 'rgba(60,60,67,0.08)', axis: '#86868b', text: '#6e6e73', surface: '#ffffff' },
);

const PERIODS = [
  { label: '近 7 天', value: 7 },
  { label: '近 30 天', value: 30 },
  { label: '近 90 天', value: 90 },
];
const days = ref(30);
const userId = ref<number>();
const loading = ref(false);
const data = ref<ChannelDashboardApi.Overview>();
const users = ref<{ id: number; nickname: string }[]>([]);
const userName = (id: number) => users.value.find((u) => u.id === id)?.nickname ?? `#${id}`;

const cur = computed(() => data.value?.current);
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
function delta(key: keyof ChannelDashboardApi.Totals, rate = false) {
  const a = Number(cur.value?.[key] ?? 0);
  const b = Number(prev.value?.[key] ?? 0);
  if (rate) {
    const d = (a - b) * 100;
    return { text: `${d >= 0 ? '+' : ''}${d.toFixed(1)}pt`, up: d >= 0, flat: Math.abs(d) < 0.05 };
  }
  if (b === 0) return { text: a === 0 ? '持平' : '上期无数据', up: true, flat: true };
  const d = ((a - b) / b) * 100;
  return { text: `${d >= 0 ? '+' : ''}${d.toFixed(0)}%`, up: d >= 0, flat: Math.abs(d) < 0.5 };
}

const tiles = computed(() => {
  const c = cur.value;
  if (!c) return [];
  return [
    {
      label: '新询盘',
      value: c.inquiries.toLocaleString(),
      sub: `邮件 ${c.mailIn} · WhatsApp ${c.waConversations}`,
      d: delta('inquiries'),
      tip: '收到的邮件数 + 有客户来信的 WhatsApp 会话数',
      icon: 'lucide:inbox',
      tint: 'is-blue',
    },
    {
      label: '总回复率',
      value: pct(c.replyRate),
      sub: `已回 ${c.mailReplied + c.waRepliedConversations} / ${c.inquiries}`,
      d: delta('replyRate', true),
      tip: '已回复的邮件和会话占新询盘的比例',
      icon: 'lucide:reply',
      tint: 'is-green',
    },
    {
      label: 'AI 发送占比',
      value: pct(c.aiShare),
      sub: `AI ${c.mailAiOut + c.waAiOut} / 总计 ${c.mailOut + c.waOut}`,
      d: delta('aiShare', true),
      tip: '发出的邮件和 WhatsApp 消息中，由 AI 自动回复或 AI 起草后发出的比例',
      icon: 'lucide:sparkles',
      tint: 'is-purple',
    },
    {
      label: '新建线索',
      value: c.cluesCreated.toLocaleString(),
      sub: `自动建档 ${c.cluesFromMail + c.cluesFromWhatsApp} · 转客 ${c.cluesConverted}`,
      d: delta('cluesCreated'),
      tip: '本期新建的 CRM 线索；自动建档来源为邮件或 WhatsApp',
      icon: 'lucide:user-plus',
      tint: 'is-blue',
    },
    {
      label: '新建客户',
      value: c.customersCreated.toLocaleString(),
      sub: `线索转化 ${c.cluesConverted}`,
      d: delta('customersCreated'),
      tip: '本期新建的 CRM 客户',
      icon: 'lucide:building-2',
      tint: 'is-amber',
    },
    {
      label: '成交订单',
      value: (data.value?.orders ?? 0).toLocaleString(),
      sub: `${fmtMoney(data.value?.orderAmount)}`,
      d: { text: '下单口径', up: true, flat: true },
      tip: '本期下单的订单数和金额（CRM → 订单管理）',
      icon: 'lucide:receipt',
      tint: 'is-green',
      neutral: true,
    },
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
  { title: '业务员', key: 'name', width: 150 },
  { title: '收到邮件', dataIndex: 'mailIn', key: 'mailIn', align: 'right' as const },
  { title: '即时消息来信', dataIndex: 'waIn', key: 'waIn', align: 'right' as const },
  { title: '发出消息', dataIndex: 'out', key: 'out', align: 'right' as const },
  { title: '其中 AI', key: 'ai', align: 'right' as const },
  { title: '新线索', dataIndex: 'clues', key: 'clues', align: 'right' as const },
  { title: '新客户', dataIndex: 'customers', key: 'customers', align: 'right' as const },
  { title: '写跟进', dataIndex: 'followUps', key: 'followUps', align: 'right' as const },
  { title: '订单', dataIndex: 'orders', key: 'orders', align: 'right' as const },
  { title: '订单金额', key: 'orderAmount', align: 'right' as const },
];

const PLATFORM_META: Record<string, { color: string; label: string }> = {
  whatsapp: { label: 'WhatsApp', color: '#25D366' },
  messenger: { label: 'Facebook', color: '#007AFF' },
  instagram: { label: 'Instagram', color: '#E1306C' },
  linkedin: { label: 'LinkedIn', color: '#0A66C2' },
  telegram: { label: 'Telegram', color: '#26A5E4' },
  web: { label: '官网客服', color: '#5856D6' },
  youtube: { label: 'YouTube 评论', color: '#FF3B30' },
  tiktok: { label: 'TikTok 评论', color: '#1D1D1F' },
  mail: { label: '企业邮件', color: '#7C3AED' },
};
const platformRows = computed(() => {
  const m = data.value?.platforms ?? {};
  const total = Object.values(m).reduce((a, n) => a + n, 0);
  return Object.entries(m)
    .filter(([, n]) => n > 0)
    .sort((a, b) => b[1] - a[1])
    .map(([k, n]) => ({
      label: PLATFORM_META[k]?.label ?? k,
      color: PLATFORM_META[k]?.color ?? '#007AFF',
      n,
      w: total ? (n / total) * 100 : 0,
    }));
});
const countryColumns = [
  { title: '国家 / 地区', key: 'country' },
  { title: '新线索', dataIndex: 'clues', key: 'clues', align: 'right' as const },
  { title: '新客户', dataIndex: 'customers', key: 'customers', align: 'right' as const },
  { title: '已成交', dataIndex: 'deals', key: 'deals', align: 'right' as const },
];
const countryLabel = (c?: string) => (c ? countryName(c) || c : '未识别');

function exportUsers() {
  const rows = data.value?.users ?? [];
  const head = ['业务员', '收到邮件', '即时消息来信', '发出消息', '其中AI', '新线索', '新客户', '写跟进', '订单', '订单金额'];
  const lines = rows.map((r) =>
    [userName(r.userId), r.mailIn, r.waIn, r.out, r.aiOut, r.clues, r.customers, r.followUps, r.orders, Number(r.orderAmount || 0).toFixed(2)]
      .map((v) => `"${String(v).replaceAll('"', '""')}"`)
      .join(','),
  );
  const blob = new Blob([`\ufeff${[head.join(','), ...lines].join('\n')}`], { type: 'text/csv;charset=utf-8' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = `经营分析-按业务员-${data.value?.startDate ?? ''}.csv`;
  a.click();
  URL.revokeObjectURL(a.href);
}

const inqRef = ref<EchartsUIType>();
const outRef = ref<EchartsUIType>();
const { renderEcharts: renderInq } = useEcharts(inqRef);
const { renderEcharts: renderOut } = useEcharts(outRef);

const label = (d: string) => d.slice(5);

function barOption(series: { color: string; name: string; values: number[] }[], showLegend: boolean): EChartsOption {
  const p = palette.value;
  const x = (data.value?.daily ?? []).map((d) => label(d.date));
  const last = series.length - 1;
  return {
    backgroundColor: 'transparent',
    grid: { left: 6, right: 8, top: showLegend ? 36 : 12, bottom: 4, containLabel: true },
    legend: showLegend
      ? { top: 0, right: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { color: p.text, fontSize: 12 } }
      : undefined,
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: 'rgba(0,0,0,0.06)',
      borderRadius: 10,
      textStyle: { color: '#1d1d1f', fontSize: 12 },
      axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(0,122,255,0.04)' } },
    },
    xAxis: {
      type: 'category',
      data: x,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: p.axis, fontSize: 11, hideOverlap: true },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: p.grid, type: 'dashed' } },
      axisLabel: { color: p.axis, fontSize: 11 },
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
        borderRadius: i === last ? [6, 6, 0, 0] : 0,
      },
      emphasis: { focus: 'series' },
    })),
  };
}

async function renderCharts() {
  const d = data.value?.daily ?? [];
  await nextTick();
  renderInq(barOption([
    { name: '企业邮件', color: '#5856D6', values: d.map((x) => x.mailIn) },
    { name: 'WhatsApp / IM', color: '#34C759', values: d.map((x) => x.waIn) },
  ], true));
  renderOut(barOption([
    { name: 'AI 发出', color: '#AF52DE', values: d.map((x) => x.aiOut) },
    { name: '人工发出', color: '#007AFF', values: d.map((x) => x.humanOut) },
  ], true));
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
  <div class="bo-page">
    <div class="bo-bar">
      <div>
        <h2 class="bo-title">经营分析</h2>
        <div class="bo-sub">
          {{ data ? `${data.startDate} 至今 · 环比前 ${data.days} 天 · ${scopeLabel}` : '正在同步经营统计…' }}
        </div>
      </div>

      <div class="flex flex-wrap items-center gap-2.5">
        <div v-if="userOptions.length > 1" class="bo-user-pill">
          <IconifyIcon icon="lucide:user-round" class="size-3.5 text-[#8E8E93]" />
          <Select
            v-model:value="userId"
            :bordered="false"
            allow-clear
            show-search
            size="small"
            option-filter-prop="label"
            :options="userOptions"
            :placeholder="data?.scope.all ? '全部业务员' : '本部门全部'"
            class="w-36"
          />
        </div>

        <div class="bo-period-seg" role="group" aria-label="统计周期">
          <button
            v-for="p in PERIODS"
            :key="p.value"
            type="button"
            :class="{ on: days === p.value }"
            @click="days = p.value"
          >
            {{ p.label }}
          </button>
        </div>
      </div>
    </div>

    <Spin :spinning="loading">
      <!-- 6 项核心经营指标 -->
      <div class="mb-4 grid grid-cols-2 gap-3.5 md:grid-cols-3 xl:grid-cols-6">
        <div
          v-for="(t, i) in tiles"
          :key="t.label"
          class="bo-kpi ap-lift ap-rise"
          :style="{ animationDelay: `${i * 40}ms` }"
        >
          <div class="flex items-center justify-between gap-2">
            <Tooltip :title="t.tip">
              <span class="cursor-help text-[12.5px] font-semibold text-[#6E6E73]">{{ t.label }}</span>
            </Tooltip>
            <span class="bo-kpi__icon" :class="t.tint">
              <IconifyIcon :icon="t.icon" class="size-3.5" />
            </span>
          </div>
          <div class="my-2 text-[27px] font-bold leading-none tracking-tight text-[#1D1D1F] tabular-nums">
            {{ t.value }}
          </div>
          <div class="truncate text-xs text-[#86868B]">{{ t.sub }}</div>
          <div class="mt-2">
            <span
              class="bo-delta tabular-nums"
              :class="
                t.d.flat || t.neutral
                  ? 'is-flat'
                  : t.d.up
                    ? 'is-up'
                    : 'is-down'
              "
            >
              {{ t.d.flat || t.neutral ? '' : t.d.up ? '▲ ' : '▼ ' }}环比 {{ t.d.text }}
            </span>
          </div>
        </div>
      </div>

      <!-- 每日询盘与每日发出消息柱状图 -->
      <Row :gutter="[14, 14]" class="mb-4">
        <Col :xs="24" :lg="12">
          <section class="bo-card">
            <div class="bo-card__head">
              <h3>每日新询盘</h3>
              <span>邮件 + 即时消息进线</span>
            </div>
            <EchartsUI ref="inqRef" height="256px" />
          </section>
        </Col>
        <Col :xs="24" :lg="12">
          <section class="bo-card">
            <div class="bo-card__head">
              <h3>每日发出消息</h3>
              <span>AI 自动/辅助 vs 人工发出</span>
            </div>
            <EchartsUI ref="outRef" height="256px" />
          </section>
        </Col>
      </Row>

      <!-- AI 草稿处理分布 -->
      <section class="bo-card mb-4">
        <div class="bo-card__head">
          <h3>AI 草稿处理追踪</h3>
          <span>统计 AI 起草回复后的采纳与流转情况</span>
        </div>
        <div v-if="cur" class="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
          <div class="rounded-2xl bg-[#EEFBF3] p-3.5">
            <div class="text-xs font-medium text-[#1E8E3E]">已采纳发出</div>
            <div class="mt-1 text-2xl font-bold text-[#1E8E3E] tabular-nums">{{ cur.aiSent }}</div>
          </div>
          <div class="rounded-2xl bg-[#EEF4FF] p-3.5">
            <div class="text-xs font-medium text-[#007AFF]">待人工确认</div>
            <div class="mt-1 text-2xl font-bold text-[#007AFF] tabular-nums">{{ cur.aiPending }}</div>
          </div>
          <div class="rounded-2xl bg-[#FFF8EB] p-3.5">
            <div class="text-xs font-medium text-[#B25E00]">已转人工</div>
            <div class="mt-1 text-2xl font-bold text-[#C93400] tabular-nums">{{ cur.aiHandoff }}</div>
          </div>
          <div class="rounded-2xl bg-[#F5F5F7] p-3.5">
            <div class="text-xs font-medium text-[#6E6E73]">已忽略放弃</div>
            <div class="mt-1 text-2xl font-bold text-[#424245] tabular-nums">{{ cur.aiDiscarded }}</div>
          </div>
        </div>

        <div class="mb-2 text-xs font-semibold text-[#86868B]">待人工确认原因分布</div>
        <div v-if="pendingRows.length === 0" class="rounded-2xl bg-[#F8F8FB] py-6 text-center text-xs text-[#86868B]">
          当前没有积压待确认的 AI 草稿
        </div>
        <div v-else class="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
          <div v-for="r in pendingRows" :key="r.reason" class="rounded-xl bg-[#F8F8FB] px-3.5 py-2.5">
            <div class="mb-1.5 flex justify-between text-[13px]">
              <span class="font-medium text-[#3A3A3C]">{{ r.reason }}</span>
              <span class="font-semibold text-[#1D1D1F] tabular-nums">{{ r.n }}</span>
            </div>
            <div class="h-1.5 overflow-hidden rounded-full bg-black/[0.06]">
              <div class="h-full rounded-full bg-[#007AFF]" :style="{ width: `${r.w}%` }" />
            </div>
          </div>
        </div>
      </section>

      <!-- 渠道来源与国家分布 -->
      <Row :gutter="[14, 14]" class="mb-4">
        <Col :xs="24" :lg="10">
          <section class="bo-card h-full">
            <div class="bo-card__head">
              <h3>询盘渠道来源</h3>
              <span>按客户来信会话及邮件统计</span>
            </div>
            <div v-if="platformRows.length === 0" class="py-12 text-center text-[13px] text-[#86868B]">
              所选周期暂无询盘记录
            </div>
            <div v-else class="space-y-3">
              <div v-for="r in platformRows" :key="r.label">
                <div class="mb-1 flex items-center justify-between text-[13px]">
                  <span class="flex items-center gap-2 font-medium text-[#1D1D1F]">
                    <span class="size-2.5 rounded-full" :style="{ background: r.color }"></span>
                    {{ r.label }}
                  </span>
                  <span class="font-semibold text-[#1D1D1F] tabular-nums">{{ r.n }}</span>
                </div>
                <div class="h-2 overflow-hidden rounded-full bg-[#F2F2F7]">
                  <div class="h-full rounded-full transition-all duration-500" :style="{ width: `${r.w}%`, background: r.color }" />
                </div>
              </div>
            </div>
          </section>
        </Col>
        <Col :xs="24" :lg="14">
          <section class="bo-card h-full">
            <div class="bo-card__head">
              <h3>国家 / 地区分布</h3>
              <span>新线索、新客户与成交转化</span>
            </div>
            <Table
              :columns="countryColumns"
              :data-source="data?.countries ?? []"
              row-key="countryCode"
              size="small"
              :pagination="{ pageSize: 6, size: 'small' }"
            >
              <template #bodyCell="{ column, record }">
                <span v-if="column.key === 'country'" class="font-medium text-[#1D1D1F]">
                  {{ countryLabel(record.countryCode) }}
                </span>
                <span v-else-if="column.key === 'deals' && record.deals > 0" class="ap-crm-pill ap-crm-pill--green tabular-nums">
                  {{ record.deals }}
                </span>
              </template>
              <template #emptyText>
                <div class="py-8 text-center text-xs text-[#86868B]">本期暂无新增国家分布数据</div>
              </template>
            </Table>
          </section>
        </Col>
      </Row>

      <!-- 按业务员明细 -->
      <section class="bo-card">
        <div class="bo-card__head">
          <div>
            <h3>团队成员接待与业绩</h3>
            <span>按业务员汇总进线、AI 协同与成交产出</span>
          </div>
          <button
            type="button"
            class="bo-export-btn"
            :disabled="!data?.users?.length"
            @click="exportUsers"
          >
            <IconifyIcon icon="lucide:download" class="size-3.5" />
            <span>导出 CSV</span>
          </button>
        </div>

        <Table
          :columns="userColumns"
          :data-source="data?.users ?? []"
          row-key="userId"
          size="middle"
          :pagination="false"
          :scroll="{ x: 960 }"
        >
          <template #bodyCell="{ column, record }">
            <div v-if="column.key === 'name'" class="flex items-center gap-2">
              <span
                class="ap-crm-avatar"
                :style="{ background: crmAvatarBg(userName(record.userId)) }"
              >
                {{ crmInitial(userName(record.userId)) }}
              </span>
              <span class="font-semibold text-[#1D1D1F]">{{ userName(record.userId) }}</span>
            </div>
            <template v-else-if="column.key === 'ai'">
              <span class="font-semibold text-[#7C3AED] tabular-nums">{{ record.aiOut }}</span>
              <span class="ml-1 text-xs text-[#86868B] tabular-nums">
                ({{ record.out ? Math.round((record.aiOut / record.out) * 100) : 0 }}%)
              </span>
            </template>
            <span v-else-if="column.key === 'orderAmount'" class="font-semibold text-[#1D1D1F] tabular-nums">
              {{ fmtMoney(record.orderAmount) }}
            </span>
          </template>
          <template #emptyText>
            <div class="py-10 text-center text-[13px] text-[#86868B]">
              本期暂无成员接待记录；可在「渠道」接入邮箱或 WhatsApp 后自动统计
            </div>
          </template>
        </Table>
      </section>
    </Spin>
  </div>
</template>

<style scoped>
.bo-page {
  padding-top: 6px;
}

.bo-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-end;
  justify-content: space-between;
  padding-bottom: 16px;
}

.bo-title {
  margin: 0;
  font-size: 23px;
  font-weight: 700;
  color: #1d1d1f;
  letter-spacing: -0.02em;
}

.bo-sub {
  margin-top: 3px;
  font-size: 12.5px;
  color: #6e6e73;
}

.bo-user-pill {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  height: 34px;
  padding: 0 4px 0 12px;
  background: rgb(255 255 255 / 86%);
  border-radius: 999px;
  box-shadow:
    0 1px 2px rgb(0 0 0 / 4%),
    inset 0 0 0 0.5px rgb(0 0 0 / 8%);
}

.bo-period-seg {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  background: rgb(255 255 255 / 80%);
  border-radius: 999px;
  box-shadow:
    0 1px 2px rgb(0 0 0 / 4%),
    inset 0 0 0 0.5px rgb(0 0 0 / 8%);
}

.bo-period-seg button {
  height: 28px;
  padding: 0 13px;
  font-size: 12.5px;
  font-weight: 500;
  color: #6e6e73;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 999px;
  transition: all var(--ap-dur-fast);
}

.bo-period-seg button.on {
  font-weight: 600;
  color: #fff;
  background: #1d1d1f;
}

.bo-kpi {
  display: flex;
  flex-direction: column;
  padding: 16px 18px;
  background: var(--ap-surface);
  border-radius: 18px;
  box-shadow: var(--ap-shadow-rest);
}

.bo-kpi__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 9px;
}

.bo-kpi__icon.is-blue {
  color: #007aff;
  background: #e8f2ff;
}

.bo-kpi__icon.is-green {
  color: #1e8e3e;
  background: #eaf9ee;
}

.bo-kpi__icon.is-purple {
  color: #7c3aed;
  background: #f3eeff;
}

.bo-kpi__icon.is-amber {
  color: #b25e00;
  background: #fff4e0;
}

.bo-delta {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 7px;
  font-size: 11px;
  font-weight: 600;
  border-radius: 999px;
}

.bo-delta.is-up {
  color: #1e8e3e;
  background: #eaf9ee;
}

.bo-delta.is-down {
  color: #d70015;
  background: #ffeceb;
}

.bo-delta.is-flat {
  color: #6e6e73;
  background: #f2f2f7;
}

.bo-card {
  padding: 18px 20px;
  background: var(--ap-surface);
  border-radius: 20px;
  box-shadow: var(--ap-shadow-rest);
}

.bo-card__head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}

.bo-card__head h3 {
  display: inline;
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #1d1d1f;
  letter-spacing: -0.015em;
}

.bo-card__head span {
  margin-left: 6px;
  font-size: 12px;
  color: #86868b;
}

.bo-export-btn {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 30px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 600;
  color: #007aff;
  cursor: pointer;
  background: #e8f2ff;
  border: 0;
  border-radius: 999px;
}

.bo-export-btn:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
</style>
