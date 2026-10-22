<script lang="ts" setup>
/**
 * 客户概览：客户总量与成交率、联系活跃度、国家 / 来源 / 等级分布、每月新增、商机阶段、最活跃客户。
 * 数据范围跟随角色的数据权限（与数据看板一致）。
 */
import type { EChartsOption, EchartsUIType } from '@vben/plugins/echarts';

import type { CustomerOverviewApi } from '#/api/channel/customer-overview';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { DICT_TYPE } from '@vben/constants';
import { Page } from '@vben/common-ui';
import { getDictOptions } from '@vben/hooks';
import { EchartsUI, useEcharts } from '@vben/plugins/echarts';
import { usePreferences } from '@vben/preferences';

import { Card, Col, Empty, Radio, RadioGroup, Row, Select, Spin, Table, Tag, Tooltip } from 'ant-design-vue';

import { getCustomerOverview } from '#/api/channel/customer-overview';
import { getSimpleUserList } from '#/api/system/user';
import { countryName } from '#/views/cgch/crm/shared/countries';

defineOptions({ name: 'CgchBoardCustomerOverview' });

const router = useRouter();
const { isDark } = usePreferences();
const palette = computed(() =>
  isDark.value
    ? { s1: '#3987e5', grid: '#2e2e2c', axis: '#8a8984', text: '#c3c2b7', track: '#2a2a28' }
    : { s1: '#2a78d6', grid: '#ecebe8', axis: '#9a9993', text: '#52514e', track: '#f0efec' },
);

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

const tiles = computed(() => {
  const t = data.value?.totals;
  if (!t) return [];
  return [
    { label: '客户总数', value: t.total, sub: t.pool ? `其中公海 ${t.pool}` : `AI 托管 ${t.aiHosted}` },
    { label: `近 ${data.value!.days} 天新增`, value: t.created, sub: '新建档的客户' },
    { label: '已成交', value: t.dealt, sub: `成交率 ${pct(t.dealRate)}` },
    { label: '近 30 天有联系', value: t.active30, sub: `占 ${t.total ? pct(t.active30 / t.total) : '0%'}` },
    { label: '沉睡客户', value: t.dormant, sub: '超过 90 天未联系或从未联系', link: '/claw/reactivate' },
    { label: '跟进已逾期', value: t.overdueFollowUp, sub: '下次联系时间已过' },
  ];
});

const RECENCY = [
  { key: 'd7', label: '7 天内' },
  { key: 'd30', label: '8~30 天' },
  { key: 'd90', label: '31~90 天' },
  { key: 'older', label: '超过 90 天' },
  { key: 'never', label: '从未联系' },
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

// ===== 图表 =====
const monthRef = ref<EchartsUIType>();
const countryRef = ref<EchartsUIType>();
const { renderEcharts: renderMonth } = useEcharts(monthRef);
const { renderEcharts: renderCountry } = useEcharts(countryRef);
function axis(): any {
  const p = palette.value;
  return {
    axisLine: { lineStyle: { color: p.grid } },
    axisTick: { show: false },
    axisLabel: { color: p.axis },
    splitLine: { lineStyle: { color: p.grid } },
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
    grid: { left: 8, right: 8, top: 12, bottom: 4, containLabel: true },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: { type: 'category', data: months.map((m) => m.slice(2)), ...axis(), splitLine: { show: false } },
    yAxis: { type: 'value', minInterval: 1, ...axis() },
    series: [{ name: '新增客户', type: 'bar', data: months.map((m) => d.monthly[m] ?? 0), barMaxWidth: 18,
      itemStyle: { color: p.s1, borderRadius: [4, 4, 0, 0] } }],
  };
  renderMonth(monthOpt);
  const countries = [...d.byCountry].slice(0, 10).reverse();
  const countryOpt: EChartsOption = {
    backgroundColor: 'transparent',
    grid: { left: 8, right: 24, top: 8, bottom: 4, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (ps: any) => {
        const x = countries[ps[0].dataIndex]!;
        return `${ps[0].name}<br/>客户 ${x.count} · 已成交 ${x.dealt}`;
      },
    },
    xAxis: { type: 'value', minInterval: 1, ...axis() },
    yAxis: { type: 'category', data: countries.map((c) => (c.key === '?' ? '未填写' : countryName(c.key) || c.key)), ...axis(), splitLine: { show: false } },
    series: [{ name: '客户数', type: 'bar', data: countries.map((c) => c.count), barMaxWidth: 14,
      itemStyle: { color: p.s1, borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right', color: p.text } }],
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
  { title: '国家', key: 'country', width: 90 },
  { title: '负责人', key: 'owner', width: 100 },
  { title: '邮件', dataIndex: 'mails', key: 'mails', width: 70, align: 'right' as const },
  { title: 'WhatsApp', dataIndex: 'whatsapp', key: 'whatsapp', width: 90, align: 'right' as const },
  { title: '上次联系', dataIndex: 'lastContact', key: 'last', width: 110 },
];

onMounted(async () => {
  users.value = await getSimpleUserList();
  await load();
});
</script>

<template>
  <Page auto-content-height>
    <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
      <div class="text-lg font-semibold">客户概览</div>
      <div class="flex flex-wrap items-center gap-3">
        <Select v-if="userOptions.length > 1" v-model:value="userId" allow-clear show-search option-filter-prop="label"
                :options="userOptions" placeholder="全部业务员" class="w-44" />
        <RadioGroup v-model:value="days" button-style="solid">
          <Radio.Button :value="30">近 30 天</Radio.Button>
          <Radio.Button :value="90">近 90 天</Radio.Button>
          <Radio.Button :value="365">近一年</Radio.Button>
        </RadioGroup>
      </div>
    </div>

    <Spin :spinning="loading">
      <div class="mb-4 grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-6">
        <Card v-for="t in tiles" :key="t.label" size="small" :bordered="false"
              :class="t.link ? 'cursor-pointer' : ''" @click="t.link && router.push(t.link)">
          <div class="text-sm text-gray-500">{{ t.label }}</div>
          <div class="my-1 text-2xl font-semibold tabular-nums">{{ t.value }}</div>
          <div class="text-xs text-gray-500">{{ t.sub }}<span v-if="t.link" class="text-blue-500"> · 去唤醒</span></div>
        </Card>
      </div>

      <Row :gutter="16">
        <Col :xs="24" :lg="12" class="mb-4">
          <Card title="每月新增客户（近 12 个月）" size="small" :bordered="false">
            <EchartsUI ref="monthRef" height="240px" />
          </Card>
        </Col>
        <Col :xs="24" :lg="12" class="mb-4">
          <Card title="客户国家分布（前 10）" size="small" :bordered="false">
            <Empty v-if="!data?.byCountry.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" />
            <EchartsUI v-show="data?.byCountry.length" ref="countryRef" height="240px" />
          </Card>
        </Col>
      </Row>

      <Row :gutter="16">
        <Col :xs="24" :lg="8" class="mb-4">
          <Card title="多久没联系" size="small" :bordered="false">
            <div v-for="r in recencyRows" :key="r.key" class="py-1.5">
              <div class="flex justify-between text-sm"><span>{{ r.label }}</span><span class="tabular-nums">{{ r.n }}</span></div>
              <div class="h-1.5 rounded" :style="{ background: palette.track }">
                <div class="h-1.5 rounded" :style="{ width: `${r.w}%`, background: palette.s1 }" />
              </div>
            </div>
          </Card>
        </Col>
        <Col :xs="24" :lg="8" class="mb-4">
          <Card title="客户来源" size="small" :bordered="false">
            <Empty v-if="!sourceRows.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" />
            <div v-for="r in sourceRows" :key="r.key" class="py-1.5">
              <div class="flex justify-between text-sm">
                <span>{{ r.label }}</span>
                <Tooltip :title="`已成交 ${r.dealt}`"><span class="tabular-nums">{{ r.count }}</span></Tooltip>
              </div>
              <div class="h-1.5 rounded" :style="{ background: palette.track }">
                <div class="h-1.5 rounded" :style="{ width: `${r.w}%`, background: palette.s1 }" />
              </div>
            </div>
          </Card>
        </Col>
        <Col :xs="24" :lg="8" class="mb-4">
          <Card title="客户等级" size="small" :bordered="false">
            <Empty v-if="!levelRows.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" />
            <div v-for="r in levelRows" :key="r.key" class="py-1.5">
              <div class="flex justify-between text-sm">
                <span>{{ r.label }}</span>
                <Tooltip :title="`已成交 ${r.dealt}`"><span class="tabular-nums">{{ r.count }}</span></Tooltip>
              </div>
              <div class="h-1.5 rounded" :style="{ background: palette.track }">
                <div class="h-1.5 rounded" :style="{ width: `${r.w}%`, background: palette.s1 }" />
              </div>
            </div>
          </Card>
        </Col>
      </Row>

      <Row :gutter="16">
        <Col :xs="24" :lg="10" class="mb-4">
          <Card title="商机阶段（进行中）" size="small" :bordered="false">
            <template #extra>
              <span v-if="won" class="text-xs text-gray-500">已赢单 {{ won.count }} 个 · {{ won.amount.toLocaleString() }}</span>
            </template>
            <Empty v-if="!funnelRows.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="还没有配置商机阶段" />
            <div v-for="f in funnelRows" :key="f.id" class="py-1.5">
              <div class="flex justify-between text-sm">
                <span>{{ f.name }} <span class="text-xs text-gray-400">赢率 {{ f.percent }}%</span></span>
                <span class="tabular-nums">{{ f.count }}<span v-if="f.amount" class="text-xs text-gray-500"> · {{ f.amount.toLocaleString() }}</span></span>
              </div>
              <div class="h-1.5 rounded" :style="{ background: palette.track }">
                <div class="h-1.5 rounded" :style="{ width: `${f.w}%`, background: palette.s1 }" />
              </div>
            </div>
          </Card>
        </Col>
        <Col :xs="24" :lg="14" class="mb-4">
          <Card :title="`最活跃客户（近 ${data?.days ?? 30} 天往来消息最多）`" size="small" :bordered="false">
            <Table :columns="topColumns" :data-source="data?.topActive ?? []" row-key="id" size="small" :pagination="false">
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'name'">
                  <a @click="router.push(`/crm/customer/detail/${record.id}`)">{{ record.name }}</a>
                  <Tag v-if="record.dealt" color="green" class="ml-1">已成交</Tag>
                  <div v-if="record.company" class="text-xs text-gray-500">{{ record.company }}</div>
                </template>
                <template v-else-if="column.key === 'country'">{{ record.country ? countryName(record.country) || record.country : '—' }}</template>
                <template v-else-if="column.key === 'owner'">{{ userName(record.ownerUserId) }}</template>
              </template>
              <template #emptyText>这段时间还没有客户往来</template>
            </Table>
          </Card>
        </Col>
      </Row>
    </Spin>
  </Page>
</template>
