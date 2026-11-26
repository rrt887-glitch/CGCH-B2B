<script lang="ts" setup>
/**
 * 客户洞察（客户概览）：客户总量与成交率、联系活跃度、国家 / 来源 / 等级分布、每月新增、商机阶段与最活跃客户。
 */
import type { EChartsOption, EchartsUIType } from '@vben/plugins/echarts';

import type { CustomerOverviewApi } from '#/api/channel/customer-overview';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { IconifyIcon } from '@vben/icons';
import { EchartsUI, useEcharts } from '@vben/plugins/echarts';
import { usePreferences } from '@vben/preferences';

import { Col, Row, Select, Spin, Table, Tooltip } from 'ant-design-vue';

import { getCustomerOverview } from '#/api/channel/customer-overview';
import { getSimpleUserList } from '#/api/system/user';
import { countryName } from '#/views/cgch/crm/shared/countries';
import { crmAvatarBg, crmInitial } from '#/views/cgch/v3/crm/components/shared';

defineOptions({ name: 'CgchBoardCustomerOverview' });

const router = useRouter();
const { isDark } = usePreferences();
const palette = computed(() =>
  isDark.value
    ? { s1: '#0a84ff', s2: '#30d158', grid: 'rgba(255,255,255,0.08)', axis: '#8e8e93', text: '#d1d1d6', track: '#2c2c2e' }
    : { s1: '#007aff', s2: '#5856d6', grid: 'rgba(60,60,67,0.08)', axis: '#86868b', text: '#6e6e73', track: '#f2f2f7' },
);

const PERIODS = [
  { label: '近 30 天', value: 30 },
  { label: '近 90 天', value: 90 },
  { label: '近一年', value: 365 },
];
const days = ref(30);
const userId = ref<number>();
const loading = ref(false);
const data = ref<CustomerOverviewApi.Overview>();
const users = ref<{ id: number; nickname: string }[]>([]);
const userName = (id?: number) => users.value.find((u) => u.id === id)?.nickname ?? (id ? `#${id}` : '公海');
const userOptions = computed(() => {
  const s = data.value?.scope;
  if (!s) return [];
  const list = s.all ? users.value : users.value.filter((u) => s.userIds.includes(u.id));
  return list.map((u) => ({ value: u.id, label: u.nickname }));
});

const dictLabel = (type: string, key: string) =>
  key === '0' ? '未填写' : (getDictOptions(type, 'string').find((o: any) => String(o.value) === key)?.label as string) ?? key;
const pct = (v = 0) => `${(v * 100).toFixed(1)}%`;

interface OverviewTile {
  icon: string;
  label: string;
  link?: string;
  sub: string;
  tint: string;
  tip?: string;
  value: number;
}

const tiles = computed<OverviewTile[]>(() => {
  const t = data.value?.totals;
  if (!t) return [];
  return [
    {
      label: '客户总数',
      value: t.total,
      sub: t.pool ? `含公海 ${t.pool}` : `AI 托管 ${t.aiHosted}`,
      icon: 'lucide:users',
      tint: 'is-blue',
    },
    {
      label: `近 ${data.value!.days} 天新增`,
      value: t.created,
      sub: '周期内新建档客户',
      icon: 'lucide:user-plus',
      tint: 'is-purple',
    },
    {
      label: '已成交客户',
      value: t.dealt,
      sub: `成交率 ${pct(t.dealRate)}`,
      icon: 'lucide:badge-check',
      tint: 'is-green',
    },
    {
      label: '近 30 天活跃',
      value: t.active30,
      sub: `活跃占比 ${t.total ? pct(t.active30 / t.total) : '0%'}`,
      icon: 'lucide:activity',
      tint: 'is-blue',
    },
    {
      label: '沉睡客户',
      value: t.dormant,
      sub: '超 90 天未联系或从未联系',
      tip: '统计全部客户，不要求留有邮箱或 WhatsApp；「客户唤醒」候选需留有邮箱或 WhatsApp。',
      link: '/v3/crm?tab=salesmind&sub=wake',
      icon: 'lucide:sparkles',
      tint: 'is-amber',
    },
    {
      label: '跟进已逾期',
      value: t.overdueFollowUp,
      sub: '超过约定下次联系时间',
      link: '/v3/crm?tab=agenda',
      icon: 'lucide:clock',
      tint: 'is-red',
    },
  ];
});

const RECENCY = [
  { key: 'd7', label: '7 天内联系', color: '#34C759' },
  { key: 'd30', label: '8 ~ 30 天', color: '#007AFF' },
  { key: 'd90', label: '31 ~ 90 天', color: '#5856D6' },
  { key: 'older', label: '超过 90 天', color: '#FF9500' },
  { key: 'never', label: '从未联系', color: '#AEAEB2' },
] as const;
const recencyRows = computed(() => {
  const r = data.value?.recency;
  const total = data.value?.totals.total || 0;
  return RECENCY.map((x) => ({ ...x, n: r?.[x.key] ?? 0, w: total ? ((r?.[x.key] ?? 0) / total) * 100 : 0 }));
});
function distRows(list: CustomerOverviewApi.Dist[] | undefined, label: (k: string) => string) {
  const max = Math.max(1, ...(list ?? []).map((x) => x.count));
  return (list ?? []).map((x) => ({ ...x, label: label(x.key), w: (x.count / max) * 100 }));
}
const sourceRows = computed(() => distRows(data.value?.bySource, (k) => dictLabel(DICT_TYPE.CRM_CUSTOMER_SOURCE, k)));
const levelRows = computed(() => distRows(data.value?.byLevel, (k) => dictLabel(DICT_TYPE.CRM_CUSTOMER_LEVEL, k)));
const funnelRows = computed(() => {
  const list = data.value?.funnel ?? [];
  const max = Math.max(1, ...list.map((x) => x.count));
  return list.map((x) => ({ ...x, w: (x.count / max) * 100 }));
});
const won = computed(() => data.value?.ended?.won);

const monthRef = ref<EchartsUIType>();
const countryRef = ref<EchartsUIType>();
const { renderEcharts: renderMonth } = useEcharts(monthRef);
const { renderEcharts: renderCountry } = useEcharts(countryRef);
function axis(): any {
  const p = palette.value;
  return {
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: p.axis, fontSize: 11 },
    splitLine: { lineStyle: { color: p.grid, type: 'dashed' } },
  };
}
async function renderCharts() {
  const d = data.value;
  if (!d) return;
  const p = palette.value;
  await nextTick();
  const months = Object.keys(d.monthly);
  const monthOpt: EChartsOption = {
    backgroundColor: 'transparent',
    grid: { left: 6, right: 8, top: 14, bottom: 4, containLabel: true },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: 'rgba(0,0,0,0.06)',
      borderRadius: 10,
      textStyle: { color: '#1d1d1f', fontSize: 12 },
      axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(0,122,255,0.04)' } },
    },
    xAxis: { type: 'category', data: months.map((m) => m.slice(2)), ...axis(), splitLine: { show: false } },
    yAxis: { type: 'value', minInterval: 1, ...axis() },
    series: [{
      name: '新增客户',
      type: 'bar',
      data: months.map((m) => d.monthly[m] ?? 0),
      barMaxWidth: 18,
      itemStyle: { color: p.s1, borderRadius: [6, 6, 0, 0] },
    }],
  };
  renderMonth(monthOpt);
  const countries = [...d.byCountry].slice(0, 10).reverse();
  const countryOpt: EChartsOption = {
    backgroundColor: 'transparent',
    grid: { left: 8, right: 28, top: 8, bottom: 4, containLabel: true },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: 'rgba(0,0,0,0.06)',
      borderRadius: 10,
      textStyle: { color: '#1d1d1f', fontSize: 12 },
      axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(0,122,255,0.04)' } },
      formatter: (ps: any) => {
        const x = countries[ps[0].dataIndex]!;
        return `<b>${ps[0].name}</b><br/>客户数 ${x.count} · 已成交 ${x.dealt}`;
      },
    },
    xAxis: { type: 'value', minInterval: 1, ...axis() },
    yAxis: { type: 'category', data: countries.map((c) => (c.key === '?' ? '未填写' : countryName(c.key) || c.key)), ...axis(), splitLine: { show: false } },
    series: [{
      name: '客户数',
      type: 'bar',
      data: countries.map((c) => c.count),
      barMaxWidth: 13,
      itemStyle: { color: p.s2, borderRadius: [0, 6, 6, 0] },
      label: { show: true, position: 'right', color: p.text, fontSize: 11 },
    }],
  };
  renderCountry(countryOpt);
}

async function load() {
  loading.value = true;
  try {
    data.value = await getCustomerOverview(days.value, userId.value);
  } finally {
    loading.value = false;
  }
  await renderCharts();
}
watch([days, userId], load);
watch(isDark, renderCharts);

const topColumns = [
  { title: '客户', key: 'name' },
  { title: '国家 / 地区', key: 'country', width: 100 },
  { title: '负责人', key: 'owner', width: 100 },
  { title: '邮件往来', dataIndex: 'mails', key: 'mails', width: 85, align: 'right' as const },
  { title: 'WhatsApp', dataIndex: 'whatsapp', key: 'whatsapp', width: 95, align: 'right' as const },
  { title: '最近联系', dataIndex: 'lastContact', key: 'last', width: 115 },
];

onMounted(async () => {
  users.value = (await getSimpleUserList()) as any;
  await load();
});
</script>

<template>
  <div class="co-page">
    <div class="co-bar">
      <div>
        <h2 class="co-title">客户洞察</h2>
        <div class="co-sub">全库客户资产盘点、跟进活跃度分布与重点市场透视</div>
      </div>

      <div class="flex flex-wrap items-center gap-2.5">
        <div v-if="userOptions.length > 1" class="co-user-pill">
          <IconifyIcon icon="lucide:user-round" class="size-3.5 text-[#8E8E93]" />
          <Select
            v-model:value="userId"
            :bordered="false"
            allow-clear
            show-search
            size="small"
            option-filter-prop="label"
            :options="userOptions"
            placeholder="全部业务员"
            class="w-36"
          />
        </div>

        <div class="co-period-seg" role="group" aria-label="统计周期">
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
      <!-- 6 项客户资产 KPI -->
      <div class="mb-4 grid grid-cols-2 gap-3.5 md:grid-cols-3 xl:grid-cols-6">
        <div
          v-for="(t, i) in tiles"
          :key="t.label"
          class="co-kpi ap-lift ap-rise"
          :class="{ 'cursor-pointer': !!t.link }"
          :style="{ animationDelay: `${i * 40}ms` }"
          @click="t.link && router.push(t.link)"
        >
          <div class="flex items-center justify-between gap-2">
            <span class="text-[12.5px] font-semibold text-[#6E6E73]">{{ t.label }}</span>
            <span class="co-kpi__icon" :class="t.tint">
              <IconifyIcon :icon="t.icon" class="size-3.5" />
            </span>
          </div>
          <div class="my-2 text-[27px] font-bold leading-none tracking-tight text-[#1D1D1F] tabular-nums">
            {{ t.value }}
          </div>
          <div class="text-xs text-[#86868B]">
            <Tooltip v-if="t.tip" :title="t.tip">
              <span class="cursor-help underline decoration-dotted">{{ t.sub }}</span>
            </Tooltip>
            <span v-else>{{ t.sub }}</span>
          </div>
          <div v-if="t.link" class="mt-2">
            <span class="inline-flex items-center rounded-full bg-[#EEF4FF] px-2 py-0.5 text-[11px] font-semibold text-[#007AFF]">
              {{ t.label === '沉睡客户' ? '去智能唤醒 ›' : '查看日程 ›' }}
            </span>
          </div>
        </div>
      </div>

      <!-- 每月新增 & 国家分布前 10 -->
      <Row :gutter="[14, 14]" class="mb-4">
        <Col :xs="24" :lg="12">
          <section class="co-card">
            <div class="co-card__head">
              <h3>每月新增客户</h3>
              <span>近 12 个月建档趋势</span>
            </div>
            <EchartsUI ref="monthRef" height="246px" />
          </section>
        </Col>
        <Col :xs="24" :lg="12">
          <section class="co-card">
            <div class="co-card__head">
              <h3>客户国家 / 地区分布</h3>
              <span>客户量前 10 市场</span>
            </div>
            <div v-if="!data?.byCountry.length" class="flex h-[246px] items-center justify-center text-[13px] text-[#86868B]">
              暂无客户国家分布数据
            </div>
            <EchartsUI v-show="data?.byCountry.length" ref="countryRef" height="246px" />
          </section>
        </Col>
      </Row>

      <!-- 联系活跃度、客户来源、客户等级三栏分布 -->
      <Row :gutter="[14, 14]" class="mb-4">
        <Col :xs="24" :lg="8">
          <section class="co-card h-full">
            <div class="co-card__head">
              <h3>联系活跃度</h3>
              <span>距上次沟通时长</span>
            </div>
            <div class="space-y-3">
              <div v-for="r in recencyRows" :key="r.key">
                <div class="mb-1 flex justify-between text-[13px]">
                  <span class="flex items-center gap-2 font-medium text-[#3A3A3C]">
                    <span class="size-2 rounded-full" :style="{ background: r.color }"></span>
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
        <Col :xs="24" :lg="8">
          <section class="co-card h-full">
            <div class="co-card__head">
              <h3>客户来源</h3>
              <span>获客渠道构成</span>
            </div>
            <div v-if="!loading && !sourceRows.length" class="py-12 text-center text-[13px] text-[#86868B]">
              暂无来源数据
            </div>
            <div v-else class="space-y-3">
              <div v-for="r in sourceRows" :key="r.key">
                <div class="mb-1 flex justify-between text-[13px]">
                  <span class="font-medium text-[#3A3A3C]">{{ r.label }}</span>
                  <Tooltip :title="`已成交 ${r.dealt}`">
                    <span class="font-semibold text-[#1D1D1F] tabular-nums">{{ r.count }}</span>
                  </Tooltip>
                </div>
                <div class="h-2 overflow-hidden rounded-full bg-[#F2F2F7]">
                  <div class="h-full rounded-full bg-[#007AFF] transition-all duration-500" :style="{ width: `${r.w}%` }" />
                </div>
              </div>
            </div>
          </section>
        </Col>
        <Col :xs="24" :lg="8">
          <section class="co-card h-full">
            <div class="co-card__head">
              <h3>客户等级</h3>
              <span>A / B / C 价值分层</span>
            </div>
            <div v-if="!loading && !levelRows.length" class="py-12 text-center text-[13px] text-[#86868B]">
              暂无等级数据
            </div>
            <div v-else class="space-y-3">
              <div v-for="r in levelRows" :key="r.key">
                <div class="mb-1 flex justify-between text-[13px]">
                  <span class="font-medium text-[#3A3A3C]">{{ r.label }}</span>
                  <Tooltip :title="`已成交 ${r.dealt}`">
                    <span class="font-semibold text-[#1D1D1F] tabular-nums">{{ r.count }}</span>
                  </Tooltip>
                </div>
                <div class="h-2 overflow-hidden rounded-full bg-[#F2F2F7]">
                  <div class="h-full rounded-full bg-[#AF52DE] transition-all duration-500" :style="{ width: `${r.w}%` }" />
                </div>
              </div>
            </div>
          </section>
        </Col>
      </Row>

      <!-- 商机阶段与最活跃客户 -->
      <Row :gutter="[14, 14]">
        <Col :xs="24" :lg="10">
          <section class="co-card h-full">
            <div class="co-card__head">
              <h3>进行中商机阶段</h3>
              <span v-if="won" class="ap-crm-pill ap-crm-pill--green tabular-nums">
                已赢单 {{ won.count }} · {{ won.amount.toLocaleString() }}
              </span>
            </div>
            <div v-if="loading && !funnelRows.length" class="py-10 text-center text-xs text-[#86868B]">
              加载中…
            </div>
            <div v-else-if="!funnelRows.length" class="py-12 text-center text-[13px] text-[#86868B]">
              暂无进行中的商机阶段数据
            </div>
            <div v-else class="space-y-3">
              <div v-for="f in funnelRows" :key="f.id">
                <div class="mb-1 flex justify-between text-[13px]">
                  <span class="font-medium text-[#1D1D1F]">
                    {{ f.name }}
                    <small class="ml-1 text-xs text-[#86868B]">赢率 {{ f.percent }}%</small>
                  </span>
                  <span class="font-semibold text-[#1D1D1F] tabular-nums">
                    {{ f.count }}
                    <span v-if="f.amount" class="font-normal text-xs text-[#86868B]"> · {{ f.amount.toLocaleString() }}</span>
                  </span>
                </div>
                <div class="h-2 overflow-hidden rounded-full bg-[#F2F2F7]">
                  <div class="h-full rounded-full bg-gradient-to-r from-[#007AFF] to-[#5856D6]" :style="{ width: `${f.w}%` }" />
                </div>
              </div>
            </div>
          </section>
        </Col>

        <Col :xs="24" :lg="14">
          <section class="co-card h-full">
            <div class="co-card__head">
              <h3>最活跃客户</h3>
              <span>近 {{ data?.days ?? 30 }} 天高频互动排行</span>
            </div>
            <Table
              :columns="topColumns"
              :data-source="data?.topActive ?? []"
              row-key="id"
              size="small"
              :pagination="false"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'name'">
                  <div class="flex min-w-0 items-center gap-2">
                    <span
                      class="ap-crm-avatar"
                      :style="{ background: crmAvatarBg(record.name || record.id) }"
                    >
                      {{ crmInitial(record.name) }}
                    </span>
                    <div class="min-w-0">
                      <div class="flex items-center gap-1.5">
                        <a
                          class="truncate font-semibold text-[#1D1D1F] hover:text-[#007AFF]"
                          @click="router.push(`/crm/customer/detail/${record.id}`)"
                        >
                          {{ record.name }}
                        </a>
                        <span v-if="record.dealt" class="ap-crm-pill ap-crm-pill--green">已成交</span>
                      </div>
                      <div v-if="record.company" class="truncate text-xs text-[#86868B]">{{ record.company }}</div>
                    </div>
                  </div>
                </template>
                <template v-else-if="column.key === 'country'">
                  {{ record.country ? countryName(record.country) || record.country : '—' }}
                </template>
                <template v-else-if="column.key === 'owner'">
                  {{ userName(record.ownerUserId) }}
                </template>
                <span v-else-if="column.key === 'last'" class="tabular-nums text-[#6E6E73]">
                  {{ record.lastContact || '—' }}
                </span>
              </template>
              <template #emptyText>
                <div class="py-10 text-center text-[13px] text-[#86868B]">所选周期内暂无客户互动记录</div>
              </template>
            </Table>
          </section>
        </Col>
      </Row>
    </Spin>
  </div>
</template>

<style scoped>
.co-page {
  padding-top: 6px;
}

.co-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-end;
  justify-content: space-between;
  padding-bottom: 16px;
}

.co-title {
  margin: 0;
  font-size: 23px;
  font-weight: 700;
  color: #1d1d1f;
  letter-spacing: -0.02em;
}

.co-sub {
  margin-top: 3px;
  font-size: 12.5px;
  color: #6e6e73;
}

.co-user-pill {
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

.co-period-seg {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  background: rgb(255 255 255 / 80%);
  border-radius: 999px;
  box-shadow:
    0 1px 2px rgb(0 0 0 / 4%),
    inset 0 0 0 0.5px rgb(0 0 0 / 8%);
}

.co-period-seg button {
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

.co-period-seg button.on {
  font-weight: 600;
  color: #fff;
  background: #1d1d1f;
}

.co-kpi {
  display: flex;
  flex-direction: column;
  padding: 16px 18px;
  background: var(--ap-surface);
  border-radius: 18px;
  box-shadow: var(--ap-shadow-rest);
}

.co-kpi__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 9px;
}

.co-kpi__icon.is-blue {
  color: #007aff;
  background: #e8f2ff;
}

.co-kpi__icon.is-green {
  color: #1e8e3e;
  background: #eaf9ee;
}

.co-kpi__icon.is-purple {
  color: #7c3aed;
  background: #f3eeff;
}

.co-kpi__icon.is-amber {
  color: #b25e00;
  background: #fff4e0;
}

.co-kpi__icon.is-red {
  color: #d70015;
  background: #ffeceb;
}

.co-card {
  padding: 18px 20px;
  background: var(--ap-surface);
  border-radius: 20px;
  box-shadow: var(--ap-shadow-rest);
}

.co-card__head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.co-card__head h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #1d1d1f;
  letter-spacing: -0.015em;
}

.co-card__head > span:not(.ap-crm-pill) {
  font-size: 12px;
  color: #86868b;
}
</style>
