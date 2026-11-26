<script lang="ts" setup>
/**
 * 今天（工作台总览）：今日核心卡片、详细流量/AI/转化漏斗与全部待办，以及经营分析、客户概览切换。
 */
import type { Dayjs } from 'dayjs';

import type { ChannelDashboardV3Api } from '#/api/channel/dashboard';

import { computed, defineAsyncComponent, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';
import { useUserStore } from '@vben/stores';

import {
  Modal,
  RangePicker,
  Spin,
  Tooltip,
} from 'ant-design-vue';
import dayjs from 'dayjs';

import { getDashboardV3 } from '#/api/channel/dashboard';
import { ApSkeleton } from '#/components/apple';
import { crmAvatarBg, crmInitial, stageOptions } from '#/views/cgch/v3/crm/components/shared';

import AppleToday from './apple-today.vue';

defineOptions({ name: 'CgchV3Dashboard' });

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();

const TABS = [
  { label: '今日概览', value: 'data', icon: 'lucide:sparkles' },
  { label: '经营分析', value: 'business', icon: 'lucide:bar-chart-3' },
  { label: '客户洞察', value: 'customers', icon: 'lucide:users' },
];
const BusinessAnalysis = defineAsyncComponent({
  loader: () => import('#/views/cgch/board/overview/index.vue'),
  loadingComponent: ApSkeleton,
  delay: 120,
});
const CustomerOverview = defineAsyncComponent({
  loader: () => import('#/views/cgch/board/customer-overview/index.vue'),
  loadingComponent: ApSkeleton,
  delay: 120,
});
const tab = ref(
  TABS.some((t) => t.value === route.query.tab)
    ? String(route.query.tab)
    : 'data',
);
watch(tab, (v) =>
  router.replace({
    query: { ...route.query, tab: v === 'data' ? undefined : v },
  }),
);

const WEEKDAYS = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'];
const todayDateStr = computed(
  () => `${dayjs().format('MM 月 DD 日')} · ${WEEKDAYS[dayjs().day()]}`,
);
const greetingPrefix = computed(() => {
  const h = dayjs().hour();
  if (h < 11) return '早上好';
  if (h < 14) return '中午好';
  if (h < 18) return '下午好';
  return '晚上好';
});
const nickname = computed(
  () => userStore.userInfo?.realName || (userStore.userInfo as any)?.nickname || '',
);

const range = ref<[Dayjs, Dayjs]>([dayjs(), dayjs()]);
const activePreset = ref<number | string>(1);
const PRESETS = [
  { label: '今天', value: 1 },
  { label: '近 7 天', value: 7 },
  { label: '近 30 天', value: 30 },
];
const loading = ref(false);
const detailsOpen = ref(false);

function preset(days: number | string) {
  const n = Number(days);
  activePreset.value = n;
  range.value = [dayjs().subtract(n - 1, 'day'), dayjs()];
  load();
}
function onRangeChange() {
  activePreset.value = '';
  load();
}

const pendingCount = computed(
  () =>
    (data.value?.support.im.length ?? 0) +
    (data.value?.support.mail.length ?? 0),
);
const data = ref<ChannelDashboardV3Api.Overview>();

let requestSequence = 0;
async function load() {
  const sequence = ++requestSequence;
  loading.value = true;
  try {
    const result = await getDashboardV3(
      range.value[0].format('YYYY-MM-DD'),
      range.value[1].format('YYYY-MM-DD'),
    );
    if (sequence === requestSequence) data.value = result;
  } finally {
    if (sequence === requestSequence) loading.value = false;
  }
}

const pct = (v?: null | number) =>
  v === null || v === undefined
    ? '—'
    : `${(v * 100).toFixed(v * 100 >= 10 || v === 0 ? 0 : 1)}%`;
const pct2 = (v?: null | number) =>
  v === null || v === undefined ? '—' : `${(v * 100).toFixed(1)}%`;
const updated = computed(() =>
  data.value ? dayjs(data.value.updateTime).format('HH:mm') : '',
);

// ===== 流量占比 =====
const SEGMENTS = [
  { key: 'sales', label: '销售咨询', color: '#007AFF' },
  { key: 'after_sales', label: '售后支持', color: '#32ADE6' },
  { key: 'business', label: '商务合作', color: '#AF52DE' },
  { key: 'invalid', label: '无效咨询', color: '#C7C7CC' },
] as const;
const total = computed(() => data.value?.traffic.total ?? 0);
const share = (n: number) => (total.value ? n / total.value : 0);
const segs = computed(() => {
  const t = data.value?.traffic;
  const list: { color: string; count: number; key: string; label: string; value: number }[] =
    SEGMENTS.map((s) => {
      const c = t?.counts[s.key] ?? 0;
      return { ...s, count: c, value: share(c) };
    });
  const un = t?.unclassified ?? 0;
  list.push({
    key: 'unclassified',
    label: '未分类咨询',
    color: '#E5E5EA',
    count: un,
    value: share(un),
  });
  return list;
});

const RADIUS = 52;
const CIRC = 2 * Math.PI * RADIUS;
const arcs = computed(() => {
  let offset = 0;
  return segs.value
    .filter((s) => s.value > 0)
    .map((s) => {
      const len = s.value * CIRC;
      const a = {
        color: s.color,
        dash: `${len} ${CIRC - len}`,
        offset: -offset,
      };
      offset += len;
      return a;
    });
});
const trendOpen = ref(false);

const aiRateCards = computed(() => [
  {
    label: 'AI 1 小时回复率',
    tip: '客户首条来信后 1 小时内由 AI 自动回复的咨询占比',
    v: data.value?.ai.replyIn1hRate,
    color: '#AF52DE',
  },
  {
    label: '24 小时客户首回率',
    tip: 'AI 首次回复后 24 小时内客户再次来信互动的占比',
    v: data.value?.ai.customerBack24hRate,
    color: '#34C759',
  },
  {
    label: 'AI 多轮深入沟通率',
    tip: 'AI 与客户来回互动两轮及以上的咨询占比',
    v: data.value?.ai.multiRoundRate,
    color: '#007AFF',
  },
]);

// ===== 客户阶段漏斗 =====
const funnel = computed(() => {
  const counts = new Map(
    (data.value?.stages ?? []).map((s) => [s.stage, s.count]),
  );
  const opts = stageOptions();
  const rows = opts.map((o) => ({
    label: String(o.label),
    value: Number(o.value),
    current: counts.get(Number(o.value)) ?? 0,
    reached: [...counts.entries()]
      .filter(([k]) => k >= Number(o.value))
      .reduce((a, [, n]) => a + n, 0),
  }));
  const top = rows[0]?.reached || 0;
  return rows.map((r, i) => ({
    ...r,
    width: top ? Math.max(6, (r.reached / top) * 100) : 6,
    conv:
      i > 0 && rows[i - 1]!.reached ? r.reached / rows[i - 1]!.reached : null,
  }));
});
const funnelTotal = computed(() =>
  (data.value?.stages ?? []).reduce((a, s) => a + s.count, 0),
);

// ===== 需要支持 =====
const supportTab = ref<'im' | 'mail'>('im');
const SUPPORT_TYPES = [
  { key: 'all', label: '全部' },
  { key: 'handoff', label: '已转人工' },
  { key: 'missing', label: '信息缺失' },
  { key: 'permission', label: '权限决策' },
  { key: 'crisis', label: '客情危机' },
  { key: 'compliance', label: '合规风险' },
  { key: 'other', label: '其他' },
];
const supportType = ref('all');
const supportList = computed(() => data.value?.support[supportTab.value] ?? []);
const typeCount = (k: string) =>
  k === 'all'
    ? supportList.value.length
    : supportList.value.filter((i) => i.type === k).length;
const shownSupport = computed(() =>
  supportType.value === 'all'
    ? supportList.value
    : supportList.value.filter((i) => i.type === supportType.value),
);
const REASON_TEXT: Record<string, string> = {
  handoff: '要求转人工',
  forbidden: '含敏感表述待核对',
  send_failed: '自动发送失败',
  window_closed: '超过 24 小时窗口',
};
function openItem(i: ChannelDashboardV3Api.SupportItem) {
  router.push({
    path: '/v3/conversation',
    query: { channel: i.channel, ref: String(i.refId) },
  });
}

const detailsEl = ref<HTMLElement>();
/** 详情里的「全部待人工支持事项」那一块；点「全部」后要直接滚到它，而不是详情顶部 */
const supportEl = ref<HTMLElement>();
async function openDetails() {
  detailsOpen.value = true;
  await nextTick();
  // 详情区很长（流量、转化漏斗…），待办是第 5 小节；滚到详情顶等于还要用户自己再滚 600 多像素
  (supportEl.value ?? detailsEl.value)?.scrollIntoView({
    behavior: 'smooth',
    block: 'start',
  });
}

onMounted(load);
</script>

<template>
  <div class="v3-dash">
    <!-- 顶部板块切换胶囊栏 -->
    <div class="dash-nav-row">
      <div class="dash-tabs" role="tablist" aria-label="工作台视图">
        <button
          v-for="t in TABS"
          :key="t.value"
          type="button"
          role="tab"
          :aria-selected="tab === t.value"
          class="dash-tab"
          :class="{ on: tab === t.value }"
          @click="tab = t.value"
        >
          <IconifyIcon :icon="t.icon" class="size-3.5" />
          <span>{{ t.label }}</span>
        </button>
      </div>
    </div>

    <div :key="tab" class="dash-pane">
      <BusinessAnalysis v-if="tab === 'business'" />
      <CustomerOverview v-else-if="tab === 'customers'" />
      <div v-else>
        <!-- 今日问候与时段筛选栏 -->
        <div class="v3-dash__bar">
          <div class="today-heading">
            <div class="today-date">{{ todayDateStr }}</div>
            <h1>
              {{ greetingPrefix }}{{ nickname ? `，${nickname}` : '' }}
              <span v-if="pendingCount" class="pending-badge">
                {{ pendingCount }} 项待你处理
              </span>
            </h1>
          </div>

          <div class="v3-dash__controls">
            <div class="preset-seg" role="group" aria-label="快捷日期范围">
              <button
                v-for="p in PRESETS"
                :key="p.value"
                type="button"
                :class="{ on: activePreset === p.value }"
                @click="preset(p.value)"
              >
                {{ p.label }}
              </button>
            </div>

            <div class="range-pill">
              <IconifyIcon icon="lucide:calendar" class="size-3.5 shrink-0 text-[#8E8E93]" />
              <RangePicker
                v-model:value="range"
                :allow-clear="false"
                :bordered="false"
                size="small"
                format="MM月DD日"
                :disabled-date="(d: Dayjs) => d.isAfter(dayjs(), 'day')"
                class="w-44"
                @change="onRangeChange"
              />
            </div>

            <Tooltip :title="updated ? `数据更新于 ${updated}，点击刷新` : '刷新数据'">
              <button
                type="button"
                class="dash-refresh-btn"
                aria-label="刷新数据"
                @click="load"
              >
                <IconifyIcon
                  icon="lucide:refresh-cw"
                  class="size-3.5"
                  :class="{ 'animate-spin': loading }"
                />
                <span v-if="updated" class="tabular-nums">{{ updated }}</span>
              </button>
            </Tooltip>
          </div>
        </div>

        <ApSkeleton v-if="loading && !data" variant="cards" :rows="4" />
        <Spin v-else :spinning="loading">
          <AppleToday :data="data" @open="openItem" @details="openDetails" />
        </Spin>

        <!-- 底部展开详细分析与全部待办 -->
        <div ref="detailsEl" class="mt-5">
          <div class="flex items-center justify-center">
            <button
              type="button"
              class="details-toggle ap-press"
              :aria-expanded="detailsOpen"
              @click="detailsOpen = !detailsOpen"
            >
              <IconifyIcon icon="lucide:layers" class="size-4 text-[#007AFF]" />
              <span>{{ detailsOpen ? '收起深度转化分析与全部待办' : '展开深度转化分析与全部待办' }}</span>
              <IconifyIcon
                icon="lucide:chevron-down"
                class="size-4 transition-transform duration-300"
                :class="{ 'rotate-180': detailsOpen }"
              />
            </button>
          </div>

          <div v-if="detailsOpen" class="dash-details-body pt-4">
            <Spin :spinning="loading">
              <div class="v3-dash__cards">
                <!-- 1. 流量占比环形卡 -->
                <section class="v3-card">
                  <div class="v3-card__title">
                    流量构成 <span>进线意图识别分布</span>
                  </div>
                  <div class="flex items-center gap-5 py-2">
                    <svg viewBox="0 0 140 140" class="size-36 shrink-0">
                      <circle
                        cx="70"
                        cy="70"
                        :r="RADIUS"
                        fill="none"
                        stroke="#F2F2F7"
                        stroke-width="18"
                      />
                      <circle
                        v-for="(a, i) in arcs"
                        :key="i"
                        cx="70"
                        cy="70"
                        :r="RADIUS"
                        fill="none"
                        :stroke="a.color"
                        stroke-width="18"
                        :stroke-dasharray="a.dash"
                        :stroke-dashoffset="a.offset"
                        transform="rotate(-90 70 70)"
                      />
                      <text x="70" y="68" text-anchor="middle" class="v3-donut__num">
                        {{ pct(data?.traffic.effectiveRate ?? (total ? 0 : null)) }}
                      </text>
                      <text x="70" y="86" text-anchor="middle" class="v3-donut__label">
                        有效流量率
                      </text>
                    </svg>
                    <ul class="m-0 flex-1 list-none space-y-2 p-0 text-[13px]">
                      <li
                        v-for="s in segs.slice(0, 4)"
                        :key="s.key"
                        class="flex items-center justify-between gap-2"
                      >
                        <span class="flex items-center gap-2 text-[#6E6E73]">
                          <span
                            class="inline-block size-2.5 rounded-full"
                            :style="{ background: s.color }"
                          ></span>
                          <span>{{ s.label }}</span>
                        </span>
                        <span class="font-semibold text-[#1D1D1F] tabular-nums">
                          {{ total ? pct(s.value) : '0%' }}
                        </span>
                      </li>
                    </ul>
                  </div>
                  <div class="v3-card__foot">
                    <span>
                      未分类 {{ total ? pct(share(data?.traffic.unclassified ?? 0)) : '0%' }}
                    </span>
                    <button
                      type="button"
                      class="foot-trend-btn"
                      @click="trendOpen = true"
                    >
                      近 7 天趋势 ›
                    </button>
                  </div>
                </section>

                <!-- 2. AI 接待表现卡 -->
                <section class="v3-card">
                  <div class="v3-card__title">
                    AI 接待表现 <span>工作量与响应深度</span>
                  </div>
                  <div class="grid grid-cols-1 gap-3 sm:grid-cols-[1fr_1.45fr]">
                    <div class="flex flex-col justify-between gap-2.5 rounded-2xl bg-[#F6F6F9] p-4">
                      <div>
                        <div class="text-xs text-[#86868B]">AI 回复总数</div>
                        <div class="mt-0.5 text-2xl font-bold text-[#1D1D1F] tabular-nums">
                          {{ data?.ai.replies ?? 0 }}<small class="ml-1 text-xs font-medium text-[#86868B]">条</small>
                        </div>
                      </div>
                      <div class="h-px bg-black/[0.05]"></div>
                      <div>
                        <div class="text-xs text-[#86868B]">AI 完成任务数</div>
                        <div class="mt-0.5 text-2xl font-bold text-[#1D1D1F] tabular-nums">
                          {{ data?.ai.tasks ?? 0 }}<small class="ml-1 text-xs font-medium text-[#86868B]">个</small>
                        </div>
                      </div>
                      <div class="h-px bg-black/[0.05]"></div>
                      <div>
                        <div class="text-xs text-[#86868B]">CRM 字段自动录入</div>
                        <div class="mt-0.5 text-2xl font-bold text-[#1D1D1F] tabular-nums">
                          {{ data?.ai.crmWrites ?? 0 }}<small class="ml-1 text-xs font-medium text-[#86868B]">次</small>
                        </div>
                      </div>
                    </div>

                    <div class="flex flex-col justify-between gap-2.5">
                      <div
                        v-for="m in aiRateCards"
                        :key="m.label"
                        class="rounded-2xl bg-[#F6F6F9] px-4 py-3"
                      >
                        <div class="flex items-center justify-between gap-2">
                          <Tooltip :title="m.tip">
                            <span class="cursor-help text-[12.5px] font-medium text-[#6E6E73]">
                              {{ m.label }}
                            </span>
                          </Tooltip>
                          <span class="text-xl font-bold text-[#1D1D1F] tabular-nums">
                            {{ pct(m.v) }}
                          </span>
                        </div>
                        <div class="mt-2 h-1.5 overflow-hidden rounded-full bg-black/[0.06]">
                          <div
                            class="h-full rounded-full transition-all duration-700"
                            :style="{
                              width: `${Math.round(Math.max(0, Math.min(1, m.v ?? 0)) * 100)}%`,
                              background: m.color,
                            }"
                          ></div>
                        </div>
                      </div>
                    </div>
                  </div>
                </section>

                <!-- 3. 商业转化卡 -->
                <section class="v3-card">
                  <div class="v3-card__title">
                    商业转化 <span>从询盘到成交漏斗效率</span>
                  </div>
                  <div class="flex flex-col gap-2.5">
                    <div class="v3-tile flex items-center justify-between">
                      <div>
                        <div class="v3-tile__label">新增线索量</div>
                        <div class="mt-1 flex items-center gap-2">
                          <span class="text-2xl font-bold text-[#1D1D1F] tabular-nums">
                            {{ data?.business.clues ?? 0 }}
                          </span>
                          <span
                            v-if="data?.business.cluesChange != null"
                            class="ap-crm-pill tabular-nums"
                            :class="
                              data.business.cluesChange >= 0
                                ? 'ap-crm-pill--green'
                                : 'ap-crm-pill--red'
                            "
                          >
                            {{ data.business.cluesChange >= 0 ? '▲ +' : '▼ '
                            }}{{ (data.business.cluesChange * 100).toFixed(1) }}%
                          </span>
                        </div>
                      </div>
                      <span class="tile-badge is-green">
                        <IconifyIcon icon="lucide:user-plus" class="size-4" />
                      </span>
                    </div>

                    <div class="v3-tile">
                      <div class="flex items-center justify-between">
                        <div>
                          <div class="v3-tile__label">询盘 → 报价转化率</div>
                          <div class="mt-0.5 text-2xl font-bold text-[#1D1D1F] tabular-nums">
                            {{ pct2(data?.business.inquiryToQuoteRate ?? 0) }}
                          </div>
                        </div>
                        <span class="tile-badge is-blue">
                          <IconifyIcon icon="lucide:file-spreadsheet" class="size-4" />
                        </span>
                      </div>
                      <div class="mt-2 h-1.5 overflow-hidden rounded-full bg-black/[0.06]">
                        <div
                          class="h-full rounded-full bg-[#007AFF]"
                          :style="{ width: `${Math.min(100, (data?.business.inquiryToQuoteRate ?? 0) * 100)}%` }"
                        ></div>
                      </div>
                    </div>

                    <div class="v3-tile">
                      <div class="flex items-center justify-between">
                        <div>
                          <div class="v3-tile__label">商机 → 赢单成交率</div>
                          <div class="mt-0.5 text-2xl font-bold text-[#1D1D1F] tabular-nums">
                            {{ pct2(data?.business.businessToWinRate ?? 0) }}
                          </div>
                        </div>
                        <span class="tile-badge is-purple">
                          <IconifyIcon icon="lucide:trophy" class="size-4" />
                        </span>
                      </div>
                      <div class="mt-2 h-1.5 overflow-hidden rounded-full bg-black/[0.06]">
                        <div
                          class="h-full rounded-full bg-[#AF52DE]"
                          :style="{ width: `${Math.min(100, (data?.business.businessToWinRate ?? 0) * 100)}%` }"
                        ></div>
                      </div>
                    </div>
                  </div>
                </section>
              </div>

              <!-- 4. 客户阶段全量分布漏斗 -->
              <section class="v3-card mt-4">
                <div class="v3-card__title">
                  全库客户阶段漏斗
                  <span>当前共 {{ funnelTotal }} 位跟进中客户（不含公海）</span>
                </div>
                <div
                  v-if="!funnelTotal"
                  class="py-8 text-center text-[13px] text-[#86868B]"
                >
                  暂无在跟进客户记录
                </div>
                <div v-else class="flex flex-col gap-2.5">
                  <div
                    v-for="(r, idx) in funnel"
                    :key="r.value"
                    class="flex items-center gap-3 text-[13px]"
                  >
                    <span class="w-16 shrink-0 font-medium text-[#3A3A3C]">{{ r.label }}</span>
                    <div class="h-7 flex-1 overflow-hidden rounded-xl bg-[#F4F5F8]">
                      <div
                        class="flex h-7 items-center rounded-xl px-3 text-xs font-semibold text-white tabular-nums transition-all duration-500"
                        :style="{
                          width: `${r.width}%`,
                          background: `linear-gradient(90deg, #007AFF 0%, ${idx % 2 === 0 ? '#3395FF' : '#5856D6'} 100%)`,
                        }"
                      >
                        {{ r.reached }}
                      </div>
                    </div>
                    <span class="w-36 shrink-0 text-right text-xs text-[#6E6E73] tabular-nums">
                      停留 <b class="text-[#1D1D1F]">{{ r.current }}</b>
                      <template v-if="r.conv !== null">
                        · 递进 <span class="font-semibold text-[#007AFF]">{{ pct(r.conv) }}</span>
                      </template>
                    </span>
                  </div>
                </div>
              </section>

              <!-- 5. 全部待人工支持事项工作台 -->
              <section ref="supportEl" class="v3-support mt-4" style="scroll-margin-top: 12px">
                <div class="v3-support__head">
                  <div class="support-seg" role="tablist">
                    <button
                      type="button"
                      :class="{ on: supportTab === 'im' }"
                      @click="supportTab = 'im'"
                    >
                      即时消息待办
                      <span class="tabular-nums">{{ data?.support.im.length ?? 0 }}</span>
                    </button>
                    <button
                      type="button"
                      :class="{ on: supportTab === 'mail' }"
                      @click="supportTab = 'mail'"
                    >
                      邮件待办
                      <span class="tabular-nums">{{ data?.support.mail.length ?? 0 }}</span>
                    </button>
                  </div>
                  <button
                    type="button"
                    class="text-xs font-semibold text-[#007AFF] cursor-pointer bg-transparent border-0"
                    @click="router.push('/v3/conversation')"
                  >
                    前往会话工作台 ›
                  </button>
                </div>

                <div class="flex flex-wrap gap-2 px-5 py-3.5 border-0 border-b border-solid border-black/[0.05]">
                  <button
                    v-for="t in SUPPORT_TYPES"
                    :key="t.key"
                    type="button"
                    class="v3-chip"
                    :class="{ on: supportType === t.key }"
                    @click="supportType = t.key"
                  >
                    {{ t.label }} <span class="tabular-nums">{{ typeCount(t.key) }}</span>
                  </button>
                </div>

                <div v-if="shownSupport.length" class="p-3">
                  <div
                    v-for="i in shownSupport"
                    :key="i.draftId"
                    class="flex cursor-pointer items-center gap-3.5 rounded-xl px-3 py-3 transition-colors hover:bg-[#F5F6FA]"
                    @click="openItem(i)"
                  >
                    <span
                      class="ap-crm-avatar !size-9 !text-[13px]"
                      :style="{ background: crmAvatarBg(i.contact || i.refId) }"
                    >
                      {{ crmInitial(i.contact) }}
                    </span>
                    <div class="min-w-0 flex-1">
                      <div class="flex items-center gap-2 font-semibold text-[#1D1D1F]">
                        <span class="truncate">{{ i.contact || '海外客户' }}</span>
                        <span v-if="i.subject" class="truncate text-xs font-normal text-[#86868B]">
                          · {{ i.subject }}
                        </span>
                      </div>
                      <div class="mt-0.5 truncate text-[13px] text-[#6E6E73]">
                        {{ i.question || i.detail || '—' }}
                      </div>
                    </div>
                    <span class="ap-crm-pill ap-crm-pill--amber">
                      {{ REASON_TEXT[i.reason] ?? i.reason }}
                    </span>
                    <span class="w-28 text-right text-xs text-[#8E8E93] tabular-nums">
                      {{ i.time ? dayjs(i.time).format('MM-DD HH:mm') : '' }}
                    </span>
                  </div>
                </div>
                <div v-else class="flex flex-col items-center justify-center gap-2 py-14 text-center">
                  <div class="grid size-11 place-items-center rounded-2xl bg-[#EAF9EE] text-[#1E8E3E]">
                    <IconifyIcon icon="lucide:check-circle-2" class="size-5" />
                  </div>
                  <div class="text-[13.5px] font-semibold text-[#1D1D1F]">暂无待人工处理事项</div>
                  <div class="text-xs text-[#86868B]">AI 与客户沟通中需人工介入的对话将实时汇总至此</div>
                </div>
              </section>
            </Spin>
          </div>
        </div>

        <Modal
          v-model:open="trendOpen"
          title="有效流量趋势 · 近 7 天"
          :footer="null"
          :width="600"
          wrap-class-name="ap-modal-sheet"
        >
          <div class="flex h-56 items-end gap-3 px-2 pt-6 pb-2">
            <div
              v-for="d in data?.trend ?? []"
              :key="d.date"
              class="flex h-full flex-1 flex-col items-center justify-end gap-2"
            >
              <span class="text-xs font-semibold text-[#1D1D1F] tabular-nums">
                {{ d.total ? pct(d.rate) : '—' }}
              </span>
              <div class="flex h-36 w-full items-end overflow-hidden rounded-xl bg-[#F4F5F8] p-1">
                <div
                  class="w-full rounded-lg bg-gradient-to-t from-[#007AFF] to-[#47A1FF] transition-all duration-500"
                  :style="{ height: `${Math.max(6, (d.rate ?? 0) * 100)}%` }"
                ></div>
              </div>
              <div class="text-center">
                <div class="text-xs font-medium text-[#3A3A3C] tabular-nums">{{ d.date.slice(5) }}</div>
                <div class="text-[10.5px] text-[#8E8E93] tabular-nums">{{ d.effective }}/{{ d.total }}</div>
              </div>
            </div>
          </div>
        </Modal>
      </div>
    </div>
  </div>
</template>

<style scoped>
.v3-dash {
  padding: 18px 24px 28px;
}

.dash-nav-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.dash-tabs {
  display: inline-flex;
  gap: 3px;
  padding: 3.5px;
  background: rgb(118 118 128 / 11%);
  border-radius: 12px;
}

.dash-tab {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  height: 31px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 500;
  color: #6e6e73;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 9px;
  transition: all var(--ap-dur-fast) var(--ap-ease);
}

.dash-tab:hover:not(.on) {
  color: #1d1d1f;
}

.dash-tab.on {
  font-weight: 600;
  color: #1d1d1f;
  background: #fff;
  box-shadow:
    0 1px 4px rgb(0 0 0 / 8%),
    0 0 0 0.5px rgb(0 0 0 / 4%);
}

.dash-pane {
  animation: ap-fade-up 220ms var(--ap-ease-out) both;
}

.v3-dash__bar {
  display: flex;
  flex-wrap: wrap;
  gap: 14px 20px;
  align-items: flex-end;
  justify-content: space-between;
  padding: 8px 2px 18px;
}

.today-date {
  font-size: 12.5px;
  font-weight: 600;
  color: #86868b;
  letter-spacing: 0.01em;
}

.today-heading h1 {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin: 4px 0 0;
  font-size: 25px;
  font-weight: 700;
  color: #1d1d1f;
  letter-spacing: -0.02em;
}

.pending-badge {
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 11px;
  font-size: 12.5px;
  font-weight: 600;
  color: #007aff;
  background: #e8f2ff;
  border-radius: 999px;
}

.v3-dash__controls {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.preset-seg {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  background: rgb(255 255 255 / 78%);
  border-radius: 999px;
  box-shadow:
    0 1px 2px rgb(0 0 0 / 4%),
    inset 0 0 0 0.5px rgb(0 0 0 / 8%);
}

.preset-seg button {
  height: 28px;
  padding: 0 12px;
  font-size: 12.5px;
  font-weight: 500;
  color: #6e6e73;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 999px;
  transition: all var(--ap-dur-fast);
}

.preset-seg button.on {
  font-weight: 600;
  color: #fff;
  background: #1d1d1f;
}

.range-pill {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  height: 34px;
  padding: 0 6px 0 12px;
  background: rgb(255 255 255 / 86%);
  border-radius: 999px;
  box-shadow:
    0 1px 2px rgb(0 0 0 / 4%),
    inset 0 0 0 0.5px rgb(0 0 0 / 8%);
}

.dash-refresh-btn {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 34px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 500;
  color: #6e6e73;
  cursor: pointer;
  background: rgb(255 255 255 / 86%);
  border: 0;
  border-radius: 999px;
  box-shadow:
    0 1px 2px rgb(0 0 0 / 4%),
    inset 0 0 0 0.5px rgb(0 0 0 / 8%);
  transition: all var(--ap-dur-fast);
}

.dash-refresh-btn:hover {
  color: #1d1d1f;
  background: #fff;
}

.details-toggle {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  height: 38px;
  padding: 0 20px;
  font-size: 13px;
  font-weight: 600;
  color: #1d1d1f;
  cursor: pointer;
  background: rgb(255 255 255 / 88%);
  border: 0;
  border-radius: 999px;
  box-shadow: var(--ap-shadow-rest);
  backdrop-filter: blur(16px);
  transition: all var(--ap-dur-fast) var(--ap-ease);
}

.details-toggle:hover {
  background: #fff;
  box-shadow: var(--ap-shadow-lift);
}

.dash-details-body {
  animation: ap-fade-up 240ms var(--ap-ease-out) both;
}

.v3-dash__cards {
  display: grid;
  grid-template-columns: 1fr 1.65fr 1.05fr;
  gap: 14px;
}

@media (width <= 1280px) {
  .v3-dash__cards {
    grid-template-columns: 1fr;
  }
}

.v3-card {
  padding: 20px 22px;
  background: var(--ap-surface);
  border-radius: 20px;
  box-shadow: var(--ap-shadow-rest);
}

.v3-card__title {
  margin-bottom: 14px;
  font-size: 16.5px;
  font-weight: 700;
  color: #1d1d1f;
  letter-spacing: -0.015em;
}

.v3-card__title span {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 400;
  color: #86868b;
}

.v3-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 13px;
  margin-top: 10px;
  font-size: 12.5px;
  color: #6e6e73;
  background: #f6f6f9;
  border-radius: 12px;
}

.foot-trend-btn {
  padding: 0;
  font-size: 12.5px;
  font-weight: 600;
  color: #007aff;
  cursor: pointer;
  background: none;
  border: 0;
}

.v3-tile {
  padding: 13px 15px;
  background: #f6f6f9;
  border-radius: 14px;
}

.v3-tile__label {
  font-size: 12.5px;
  font-weight: 500;
  color: #6e6e73;
}

.tile-badge {
  display: grid;
  flex: none;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
}

.tile-badge.is-green {
  color: #1e8e3e;
  background: #eaf9ee;
}

.tile-badge.is-blue {
  color: #007aff;
  background: #e8f2ff;
}

.tile-badge.is-purple {
  color: #7c3aed;
  background: #f3eeff;
}

.v3-donut__num {
  font-size: 21px;
  font-weight: 700;
  fill: #1d1d1f;
}

.v3-donut__label {
  font-size: 10.5px;
  fill: #86868b;
}

.v3-support {
  overflow: hidden;
  background: var(--ap-surface);
  border-radius: 20px;
  box-shadow: var(--ap-shadow-rest);
}

.v3-support__head {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 0.5px solid var(--ap-separator);
}

.support-seg {
  display: inline-flex;
  gap: 3px;
  padding: 3px;
  background: #f2f2f7;
  border-radius: 10px;
}

.support-seg button {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 28px;
  padding: 0 12px;
  font-size: 12.5px;
  font-weight: 500;
  color: #6e6e73;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 8px;
}

.support-seg button.on {
  font-weight: 600;
  color: #1d1d1f;
  background: #fff;
  box-shadow: 0 1px 3px rgb(0 0 0 / 8%);
}

.v3-chip {
  height: 28px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 500;
  color: #6e6e73;
  cursor: pointer;
  background: #f4f5f8;
  border: 0;
  border-radius: 999px;
  transition: all var(--ap-dur-fast);
}

.v3-chip:hover:not(.on) {
  color: #1d1d1f;
  background: #e9eaf0;
}

.v3-chip span {
  margin-left: 3px;
  opacity: 0.72;
}

.v3-chip.on {
  font-weight: 600;
  color: #fff;
  background: #1d1d1f;
}
</style>
