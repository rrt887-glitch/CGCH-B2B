<script lang="ts" setup>
/**
 * 看板 · 数据分析：流量占比、AI 接待表现、商业转化，以及 AI 需要人工支持的事项。
 */
import type { Dayjs } from 'dayjs';

import type { ChannelDashboardV3Api } from '#/api/channel/dashboard';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Empty, Modal, RangePicker, Spin, Tooltip } from 'ant-design-vue';
import dayjs from 'dayjs';

import { getDashboardV3 } from '#/api/channel/dashboard';

defineOptions({ name: 'CgchV3Dashboard' });

const router = useRouter();
const range = ref<[Dayjs, Dayjs]>([dayjs(), dayjs()]);
const loading = ref(false);
const data = ref<ChannelDashboardV3Api.Overview>();

async function load() {
  loading.value = true;
  try {
    data.value = await getDashboardV3(range.value[0].format('YYYY-MM-DD'), range.value[1].format('YYYY-MM-DD'));
  } finally {
    loading.value = false;
  }
}

const pct = (v?: null | number) => (v === null || v === undefined ? '—' : `${(v * 100).toFixed(v * 100 >= 10 || v === 0 ? 0 : 1)}%`);
const pct2 = (v?: null | number) => (v === null || v === undefined ? '—' : `${(v * 100).toFixed(2)}%`);
const updated = computed(() => (data.value ? dayjs(data.value.updateTime).format('MM-DD HH:mm') : ''));

// ===== 流量占比 =====
const SEGMENTS = [
  { key: 'sales', label: '销售咨询', color: '#155dfc' },
  { key: 'after_sales', label: '售后支持', color: '#5cc8f5' },
  { key: 'business', label: '商务合作', color: '#8a7cf0' },
  { key: 'invalid', label: '无效咨询', color: '#aab4cc' },
] as const;
const total = computed(() => data.value?.traffic.total ?? 0);
const share = (n: number) => (total.value ? n / total.value : 0);
const segs = computed(() => {
  const t = data.value?.traffic;
  const list: { color: string; key: string; label: string; value: number }[] = SEGMENTS.map((s) => ({ ...s, value: share(t?.counts[s.key] ?? 0) }));
  list.push({ key: 'unclassified', label: '未分类咨询', color: '#e4e8f2', value: share(t?.unclassified ?? 0) });
  return list;
});
/** 环形图：每段的 stroke-dasharray */
const RADIUS = 52;
const CIRC = 2 * Math.PI * RADIUS;
const arcs = computed(() => {
  let offset = 0;
  return segs.value
    .filter((s) => s.value > 0)
    .map((s) => {
      const len = s.value * CIRC;
      const a = { color: s.color, dash: `${len} ${CIRC - len}`, offset: -offset };
      offset += len;
      return a;
    });
});
const trendOpen = ref(false);

// ===== 迷你趋势线（装饰，与原网站一致） =====
const SPARK = 'M0 26 C 10 26, 14 4, 24 4 S 36 24, 46 24 L 120 24';

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
const typeCount = (k: string) => (k === 'all' ? supportList.value.length : supportList.value.filter((i) => i.type === k).length);
const shownSupport = computed(() =>
  supportType.value === 'all' ? supportList.value : supportList.value.filter((i) => i.type === supportType.value),
);
const REASON_TEXT: Record<string, string> = {
  handoff: '转人工',
  forbidden: '回复含违禁内容',
  send_failed: '自动发送失败',
  no_credit: '智点不足',
  window_closed: '超过 24 小时窗口',
};
function openItem(i: ChannelDashboardV3Api.SupportItem) {
  router.push({ path: '/v3/conversation', query: { channel: i.channel, ref: String(i.refId) } });
}

onMounted(load);
</script>

<template>
  <div class="v3-dash">
    <div class="v3-dash__bar">
      <span class="text-base font-semibold">数据分析</span>
      <RangePicker
        v-model:value="range"
        :allow-clear="false"
        size="small"
        format="MM月DD日"
        :disabled-date="(d: Dayjs) => d.isAfter(dayjs(), 'day')"
        class="ml-3 w-56"
        @change="load"
      />
      <span class="ml-auto text-xs text-gray-500">
        数据更新于：{{ updated }}
        <a class="ml-1" @click="load">刷新</a>
      </span>
    </div>

    <Spin :spinning="loading">
      <div class="v3-dash__cards">
        <!-- 流量占比 -->
        <section class="v3-card">
          <div class="v3-card__title">流量占比 <span>来的是什么客户</span></div>
          <div class="flex items-center gap-6 py-2">
            <svg viewBox="0 0 140 140" class="size-36 shrink-0">
              <circle cx="70" cy="70" :r="RADIUS" fill="none" stroke="#eef1f7" stroke-width="20" />
              <circle
                v-for="(a, i) in arcs"
                :key="i"
                cx="70"
                cy="70"
                :r="RADIUS"
                fill="none"
                :stroke="a.color"
                stroke-width="20"
                :stroke-dasharray="a.dash"
                :stroke-dashoffset="a.offset"
                transform="rotate(-90 70 70)"
              />
              <text x="70" y="70" text-anchor="middle" class="v3-donut__num">{{ pct(data?.traffic.effectiveRate ?? (total ? 0 : null)) }}</text>
              <text x="70" y="88" text-anchor="middle" class="v3-donut__label">有效流量</text>
            </svg>
            <ul class="space-y-1 text-sm">
              <li v-for="s in segs.slice(0, 4)" :key="s.key" class="flex items-center gap-2">
                <span class="inline-block size-2 rounded-full" :style="{ background: s.color }"></span>
                <span class="w-16 text-gray-600">{{ s.label }}</span>
                <span>{{ total ? pct(s.value) : '0%' }}</span>
              </li>
            </ul>
          </div>
          <div class="v3-card__foot">
            <span class="border-b border-dashed border-gray-300">未分类咨询 {{ total ? pct(share(data?.traffic.unclassified ?? 0)) : '0%' }}</span>
            <span class="mx-3 text-gray-300">|</span>
            <a class="text-gray-600" @click="trendOpen = true">有效流量趋势 →</a>
          </div>
        </section>

        <!-- AI 接待表现 -->
        <section class="v3-card v3-card--wide">
          <div class="v3-card__title">AI 接待表现 <span>AI 干了多少，干得怎么样</span></div>
          <div class="grid grid-cols-[1fr_2fr] gap-3">
            <div class="v3-tile flex flex-col justify-between">
              <div>
                <div class="v3-tile__label">AI 回复总数</div>
                <div class="v3-tile__value">{{ data?.ai.replies ?? 0 }}<small>条</small></div>
              </div>
              <div>
                <div class="v3-tile__label">AI 完成任务数</div>
                <div class="v3-tile__value">{{ data?.ai.tasks ?? 0 }}<small>个</small></div>
              </div>
              <div>
                <div class="v3-tile__label">AI CRM 字段录入次数</div>
                <div class="v3-tile__value">{{ data?.ai.crmWrites ?? 0 }}<small>次</small></div>
              </div>
            </div>
            <div class="flex flex-col gap-3">
              <div
                v-for="m in [
                  { label: 'AI 1 小时回复率', tip: '客户首条来信后 1 小时内由 AI 回复的咨询占比', v: data?.ai.replyIn1hRate },
                  { label: 'AI 首响后 24 小时客户首回率', tip: 'AI 首次回复后 24 小时内客户再次来信的咨询占比', v: data?.ai.customerBack24hRate },
                  { label: 'AI 多轮沟通率', tip: 'AI 与客户来回沟通两轮及以上的咨询占比', v: data?.ai.multiRoundRate },
                ]"
                :key="m.label"
                class="v3-tile flex items-center justify-between"
              >
                <div>
                  <Tooltip :title="m.tip"><div class="v3-tile__label">{{ m.label }}</div></Tooltip>
                  <div class="v3-tile__value">{{ pct(m.v) }}</div>
                </div>
                <svg viewBox="0 0 120 30" class="h-8 w-32"><path :d="SPARK" fill="none" stroke="#155dfc" stroke-width="1.5" /></svg>
              </div>
            </div>
          </div>
        </section>

        <!-- 商业转化 -->
        <section class="v3-card">
          <div class="v3-card__title">商业转化 <span>最终带来了多少生意</span></div>
          <div class="flex flex-col gap-3">
            <div class="v3-tile flex items-center justify-between">
              <div>
                <div class="v3-tile__label">新增线索量</div>
                <div class="v3-tile__value">
                  {{ data?.business.clues ?? 0 }}
                  <small
                    v-if="data && data.business.cluesChange !== null && data.business.cluesChange !== undefined"
                    :class="data.business.cluesChange >= 0 ? 'text-green-600' : 'text-red-500'"
                  >
                    {{ data.business.cluesChange >= 0 ? '+' : '' }}{{ (data.business.cluesChange * 100).toFixed(1) }}%
                  </small>
                </div>
              </div>
              <svg viewBox="0 0 120 30" class="h-8 w-32"><path d="M0 18 L120 18" fill="none" stroke="#155dfc" stroke-width="1.5" /></svg>
            </div>
            <div class="v3-tile flex items-center justify-between">
              <div>
                <div class="v3-tile__label">询盘 → 报价</div>
                <div class="v3-tile__value">{{ pct2(data?.business.inquiryToQuoteRate ?? 0) }}</div>
              </div>
              <svg viewBox="0 0 120 30" class="h-8 w-32"><path d="M0 18 L120 18" fill="none" stroke="#155dfc" stroke-width="1.5" /></svg>
            </div>
            <div class="v3-tile flex items-center justify-between">
              <div>
                <div class="v3-tile__label">商机 → 赢单</div>
                <div class="v3-tile__value">{{ pct2(data?.business.businessToWinRate ?? 0) }}</div>
              </div>
              <svg viewBox="0 0 120 30" class="h-8 w-32"><path d="M0 18 L120 18" fill="none" stroke="#155dfc" stroke-width="1.5" /></svg>
            </div>
          </div>
        </section>
      </div>

      <!-- 需要支持 -->
      <section class="v3-support">
        <div class="v3-support__tabs">
          <button type="button" :class="{ on: supportTab === 'im' }" @click="supportTab = 'im'">
            会话需要支持 <span>({{ data?.support.im.length ?? 0 }})</span>
          </button>
          <button type="button" :class="{ on: supportTab === 'mail' }" @click="supportTab = 'mail'">
            邮件需要支持 <span>({{ data?.support.mail.length ?? 0 }})</span>
          </button>
          <span class="ml-auto pr-4 text-xs text-gray-500">查看全部 {{ supportList.length }} 个 ›</span>
        </div>
        <div class="flex flex-wrap gap-2 px-5 py-4">
          <button
            v-for="t in SUPPORT_TYPES"
            :key="t.key"
            type="button"
            class="v3-chip"
            :class="{ on: supportType === t.key }"
            @click="supportType = t.key"
          >
            {{ t.label }} <span>{{ typeCount(t.key) }}</span>
          </button>
        </div>
        <div v-if="shownSupport.length" class="divide-y px-5 pb-4">
          <div v-for="i in shownSupport" :key="i.draftId" class="flex cursor-pointer items-center gap-4 py-3 hover:bg-gray-50" @click="openItem(i)">
            <div class="min-w-0 flex-1">
              <div class="font-medium">{{ i.contact || '客户' }} <span v-if="i.subject" class="text-gray-500">· {{ i.subject }}</span></div>
              <div class="truncate text-sm text-gray-500">{{ i.question || i.detail || '—' }}</div>
            </div>
            <span class="rounded bg-orange-50 px-2 py-0.5 text-xs text-orange-600">{{ REASON_TEXT[i.reason] ?? i.reason }}</span>
            <span class="w-32 text-right text-xs text-gray-400">{{ i.time ? dayjs(i.time).format('MM-DD HH:mm') : '' }}</span>
          </div>
        </div>
        <div v-else class="py-16">
          <Empty :image="Empty.PRESENTED_IMAGE_SIMPLE" description="AI 与客户对话时，需要人工支持的事项将显示于此" />
        </div>
      </section>
    </Spin>

    <Modal v-model:open="trendOpen" title="有效流量趋势 · 近 7 天" :footer="null" :width="640">
      <div class="flex h-48 items-end gap-3 px-2 pt-4">
        <div v-for="d in data?.trend ?? []" :key="d.date" class="flex flex-1 flex-col items-center gap-1">
          <span class="text-xs text-gray-500">{{ d.total ? pct(d.rate) : '—' }}</span>
          <div class="w-full rounded-t bg-blue-500/80" :style="{ height: `${(d.rate ?? 0) * 120 + 2}px` }"></div>
          <span class="text-xs text-gray-500">{{ d.date.slice(5) }}</span>
        </div>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.v3-dash {
  padding: 4px 8px 16px;
}

.v3-dash__bar {
  display: flex;
  align-items: center;
  padding: 8px 4px 12px;
}

.v3-dash__cards {
  display: grid;
  grid-template-columns: 1fr 2fr 1.05fr;
  gap: 12px;
}

@media (width <= 1280px) {
  .v3-dash__cards {
    grid-template-columns: 1fr;
  }
}

.v3-card {
  padding: 16px;
  background: #fff;
  border-radius: 12px;
}

.v3-card__title {
  margin-bottom: 12px;
  font-weight: 600;
}

.v3-card__title span {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 400;
  color: #8a94a6;
}

.v3-card__foot {
  display: flex;
  align-items: center;
  padding: 10px 14px;
  margin-top: 8px;
  font-size: 13px;
  color: #6b7280;
  background: #f6f8fc;
  border-radius: 8px;
}

.v3-tile {
  min-height: 72px;
  padding: 12px 16px;
  background: #f6f8fc;
  border-radius: 8px;
}

.v3-tile__label {
  display: inline-block;
  font-size: 13px;
  color: #374151;
  border-bottom: 1px dashed #c3cad8;
}

.v3-tile__value {
  margin-top: 4px;
  font-size: 20px;
  font-weight: 600;
}

.v3-tile__value small {
  margin-left: 4px;
  font-size: 12px;
  font-weight: 400;
  color: #8a94a6;
}

.v3-donut__num {
  font-size: 22px;
  font-weight: 700;
  fill: #111827;
}

.v3-donut__label {
  font-size: 10px;
  fill: #4b5563;
}

.v3-support {
  min-height: 420px;
  margin-top: 12px;
  overflow: hidden;
  background: #fff;
  border-radius: 12px;
}

.v3-support__tabs {
  display: flex;
  align-items: center;
  background: #fbf7f2;
}

.v3-support__tabs button {
  padding: 14px 24px;
  font-size: 15px;
  color: #374151;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.v3-support__tabs button.on {
  font-weight: 600;
  color: #111827;
  background: #fff;
  border-radius: 12px 12px 0 0;
}

.v3-chip {
  padding: 4px 12px;
  font-size: 13px;
  color: #374151;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 999px;
}

.v3-chip span {
  margin-left: 4px;
  color: #9ca3af;
}

.v3-chip.on {
  color: #155dfc;
  background: rgb(21 93 252 / 10%);
}
</style>
