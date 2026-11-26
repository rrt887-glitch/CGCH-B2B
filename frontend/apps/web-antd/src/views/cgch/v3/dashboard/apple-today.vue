<script setup lang="ts">
import type { ChannelDashboardV3Api } from '#/api/channel/dashboard';
import type { ChannelMailApi } from '#/api/channel/mail';
import type { ChannelWaApi } from '#/api/channel/whatsapp';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import { getMailAccountList } from '#/api/channel/mail';
import { getWaAccountList } from '#/api/channel/whatsapp';
import { ApNumber } from '#/components/apple';
import { fmtDateTime } from '#/utils/datetime';
import { crmAvatarBg, crmInitial } from '#/views/cgch/v3/crm/components/shared';

const props = defineProps<{ data?: ChannelDashboardV3Api.Overview }>();
const emit = defineEmits<{
  details: [];
  open: [item: ChannelDashboardV3Api.SupportItem];
}>();
const router = useRouter();
const accounts = ref<ChannelWaApi.Account[]>();
const mail = ref<ChannelMailApi.Account[]>();

onMounted(async () => {
  const [imResult, mailResult] = await Promise.allSettled([
    getWaAccountList(),
    getMailAccountList(),
  ]);
  if (imResult.status === 'fulfilled') accounts.value = imResult.value;
  if (mailResult.status === 'fulfilled') mail.value = mailResult.value;
});

const pct = (n: null | number | undefined) =>
  n == null ? '—' : `${Math.round(n * 100)}%`;

const rings = computed(() => [
  {
    label: 'AI 1 小时回复率',
    sub: '首条来信 1h 内响应',
    value: props.data?.ai.replyIn1hRate,
    color: 'url(#today-ai-ring)',
    dot: '#AF52DE',
    radius: 74,
  },
  {
    label: '24 小时客户首回率',
    sub: 'AI 回复后客户再互动',
    value: props.data?.ai.customerBack24hRate,
    color: '#34C759',
    dot: '#34C759',
    radius: 56,
  },
  {
    label: '询盘转报价率',
    sub: '有效推进至报价阶段',
    value: props.data?.business.inquiryToQuoteRate,
    color: '#007AFF',
    dot: '#007AFF',
    radius: 38,
  },
]);

const metrics = computed(() => [
  {
    key: 'inquiries',
    label: '询盘总量',
    value: props.data?.traffic.total,
    detail: '所选时间范围内的咨询总量',
    icon: 'lucide:inbox',
    tint: 'blue',
    onClick: () => router.push('/v3/conversation'),
  },
  {
    key: 'replies',
    label: 'AI 已回复',
    value: props.data?.ai.replies,
    detail: '自动接待与辅助发出的消息',
    icon: 'lucide:sparkles',
    tint: 'purple',
    onClick: () => router.push('/v3/agent'),
  },
  {
    key: 'clues',
    label: '新增线索',
    value: props.data?.business.clues,
    detail: '自动建档进入 CRM 的采购意向',
    icon: 'lucide:user-plus',
    tint: 'green',
    onClick: () => router.push({ path: '/v3/crm', query: { tab: 'lead' } }),
  },
  {
    key: 'tasks',
    label: 'AI 完成任务',
    value: props.data?.ai.tasks,
    detail: '自动背调、起草与资料提取',
    icon: 'lucide:check-circle-2',
    tint: 'orange',
    onClick: () => emit('details'),
  },
]);

const support = computed(() => [
  ...(props.data?.support.im ?? []),
  ...(props.data?.support.mail ?? []),
]);
const visibleSupport = computed(() => support.value.slice(0, 4));
const reasonMeta: Record<string, { cls: string; text: string }> = {
  handoff: { text: '转人工接待', cls: 'is-amber' },
  forbidden: { text: '草稿待核对', cls: 'is-purple' },
  send_failed: { text: '发送失败', cls: 'is-red' },
  no_credit: { text: '点数不足', cls: 'is-amber' },
  window_closed: { text: '超 24h 窗口', cls: 'is-gray' },
};

// ===== 平滑贝塞尔面积折线图 =====
const trend = computed(() => props.data?.trend ?? []);
const maxValue = computed(() =>
  Math.max(4, ...trend.value.map((d) => d.total)),
);
const hoverIdx = ref<null | number>(null);

function coords(key: 'effective' | 'total') {
  const len = Math.max(1, trend.value.length - 1);
  return trend.value.map((d, i) => ({
    x: 28 + (i * 510) / len,
    y: 168 - (d[key] / maxValue.value) * 134,
  }));
}

function smoothLine(pts: { x: number; y: number }[]) {
  if (!pts.length) return '';
  if (pts.length === 1) return `M ${pts[0]!.x} ${pts[0]!.y}`;
  let d = `M ${pts[0]!.x} ${pts[0]!.y}`;
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[i]!;
    const p1 = pts[i + 1]!;
    const cx = (p0.x + p1.x) / 2;
    d += ` C ${cx} ${p0.y}, ${cx} ${p1.y}, ${p1.x} ${p1.y}`;
  }
  return d;
}

function smoothArea(pts: { x: number; y: number }[]) {
  if (!pts.length) return '';
  const line = smoothLine(pts);
  const last = pts[pts.length - 1]!;
  const first = pts[0]!;
  return `${line} L ${last.x} 168 L ${first.x} 168 Z`;
}

const totalPts = computed(() => coords('total'));
const effPts = computed(() => coords('effective'));
const totalLinePath = computed(() => smoothLine(totalPts.value));
const effLinePath = computed(() => smoothLine(effPts.value));
const totalAreaPath = computed(() => smoothArea(totalPts.value));
const effAreaPath = computed(() => smoothArea(effPts.value));

const hoverPoint = computed(() => {
  const i = hoverIdx.value;
  if (i === null || !trend.value[i]) return null;
  const d = trend.value[i]!;
  const p = totalPts.value[i]!;
  const ep = effPts.value[i]!;
  return {
    ...d,
    x: p.x,
    yTotal: p.y,
    yEff: ep.y,
    rateStr: d.total ? `${Math.round((d.effective / d.total) * 100)}%` : '0%',
  };
});

const channels = computed(() => [
  ...(['whatsapp', 'messenger', 'instagram'] as const).map((platform) => {
    const list = accounts.value?.filter(
      (a) => (a.platform || 'whatsapp') === platform,
    );
    const issue = list?.some(
      (a) => a.enabled === false || a.connState === 'close',
    );
    const connected = Boolean(list?.length && !issue);
    return {
      key: platform,
      label: {
        whatsapp: 'WhatsApp',
        messenger: 'Messenger',
        instagram: 'Instagram',
      }[platform],
      icon: {
        whatsapp: 'lucide:message-circle',
        messenger: 'lucide:messages-square',
        instagram: 'lucide:camera',
      }[platform],
      detail: !list
        ? '状态同步中'
        : !list.length
          ? '尚未接入账号'
          : issue
            ? '有账号需重新连接'
            : `已连接 ${list.length} 个账号`,
      badge: !list?.length ? '去连接' : issue ? '需处理' : '在线',
      issue,
      connected,
    };
  }),
  {
    key: 'mail',
    label: '企业邮箱',
    icon: 'lucide:mail',
    detail: !mail.value
      ? '状态同步中'
      : !mail.value.length
        ? '尚未绑定邮箱'
        : mail.value.some((a) => a.lastSyncError || a.enabled === false)
          ? '有邮箱同步异常'
          : `已绑定 ${mail.value.length} 个邮箱`,
    badge: !mail.value?.length
      ? '去绑定'
      : mail.value?.some((a) => a.lastSyncError || a.enabled === false)
        ? '需处理'
        : '在线',
    issue: mail.value?.some((a) => a.lastSyncError || a.enabled === false),
    connected: Boolean(
      mail.value?.length &&
        !mail.value.some((a) => a.lastSyncError || a.enabled === false),
    ),
  },
]);
</script>

<template>
  <div class="apple-today">
    <!-- 左上：Apple Watch 三环 AI 接待总览 -->
    <section class="today-card ai-performance ap-rise">
      <header>
        <div class="flex items-center gap-2">
          <h2>AI 正在为你工作</h2>
        </div>
        <span class="live-pill">
          <i class="live-pill__dot"></i>7×24 自动值守
        </span>
      </header>

      <div class="ring-body">
        <div class="rings-wrap">
          <svg
            viewBox="0 0 168 168"
            class="rings"
            role="img"
            aria-label="AI 回复率、客户首回率及询盘转报价率"
          >
            <defs>
              <linearGradient id="today-ai-ring" x1="0" y1="0" x2="1" y2="1">
                <stop stop-color="#ff9500" />
                <stop offset=".35" stop-color="#ff2d92" />
                <stop offset=".7" stop-color="#af52de" />
                <stop offset="1" stop-color="#007aff" />
              </linearGradient>
            </defs>
            <g transform="rotate(-90 84 84)">
              <template v-for="r in rings" :key="r.label">
                <circle
                  cx="84"
                  cy="84"
                  :r="r.radius"
                  fill="none"
                  :stroke="r.dot"
                  stroke-width="13"
                  opacity=".12"
                />
                <circle
                  v-if="(r.value ?? 0) > 0"
                  cx="84"
                  cy="84"
                  :r="r.radius"
                  fill="none"
                  :stroke="r.color"
                  stroke-width="13"
                  stroke-linecap="round"
                  :stroke-dasharray="2 * Math.PI * r.radius"
                  :stroke-dashoffset="
                    2 *
                    Math.PI *
                    r.radius *
                    (1 - Math.max(0.02, Math.min(1, r.value ?? 0)))
                  "
                  class="ring-value"
                />
              </template>
            </g>
          </svg>
          <span class="rings-core" aria-hidden="true">
            <IconifyIcon icon="lucide:sparkles" class="size-5" />
          </span>
        </div>

        <div class="ring-legend">
          <div v-for="r in rings" :key="r.label" class="ring-item">
            <div class="ring-item__top">
              <span class="ring-item__title">
                <i class="ring-item__dot" :style="{ background: r.dot }"></i>
                {{ r.label }}
              </span>
              <strong>{{ pct(r.value) }}</strong>
            </div>
            <div class="ring-item__track">
              <div
                class="ring-item__fill"
                :style="{
                  width: `${Math.round(Math.max(0, Math.min(1, r.value ?? 0)) * 100)}%`,
                  background: r.dot,
                }"
              ></div>
            </div>
          </div>
        </div>
      </div>

      <footer>
        <span class="ai-foot-chip">
          AI 回复 <b><ApNumber :value="data?.ai.replies" /></b> 条
        </span>
        <span class="ai-foot-chip">
          自动建档录入 <b><ApNumber :value="data?.ai.crmWrites" /></b> 次
        </span>
        <button type="button" class="ai-foot-link" @click="emit('details')">
          详细数据 ›
        </button>
      </footer>
    </section>

    <!-- 右上：2x2 核心指标卡（点击直达对应业务模块） -->
    <div class="today-stats">
      <button
        v-for="(m, idx) in metrics"
        :key="m.key"
        type="button"
        class="today-card stat ap-lift ap-rise"
        :style="{ animationDelay: `${(idx + 1) * 45}ms` }"
        @click="m.onClick()"
      >
        <div class="stat__top">
          <span>{{ m.label }}</span>
          <span class="stat__icon" :class="`is-${m.tint}`">
            <IconifyIcon :icon="m.icon" class="size-[17px]" />
          </span>
        </div>
        <div class="stat__num-row">
          <strong><ApNumber :value="m.value" /></strong>
          <IconifyIcon icon="lucide:arrow-up-right" class="stat__jump" />
        </div>
        <small>{{ m.detail }}</small>
      </button>
    </div>

    <!-- 左下：等你处理的 AI 转交 / 草稿事项 -->
    <section
      class="today-card tasks ap-rise"
      style="animation-delay: 140ms"
    >
      <header>
        <div class="flex items-center gap-2">
          <h2>等你处理</h2>
          <span class="task-count" :class="{ 'has-items': support.length > 0 }">
            {{ support.length }}
          </span>
        </div>
        <button type="button" class="text-action" @click="emit('details')">
          全部待办 ›
        </button>
      </header>

      <div v-if="visibleSupport.length" class="task-list">
        <button
          v-for="item in visibleSupport"
          :key="`${item.channel}-${item.draftId}`"
          type="button"
          class="task"
          @click="emit('open', item)"
        >
          <span
            class="task-avatar"
            :style="{ background: crmAvatarBg(item.contact || item.refId) }"
            aria-hidden="true"
          >
            {{ crmInitial(item.contact) }}
          </span>
          <span class="task-body">
            <span class="task-title-row">
              <b>{{ item.contact || '海外客户' }}</b>
              <span
                class="task-reason"
                :class="reasonMeta[item.reason]?.cls || 'is-purple'"
              >
                {{ reasonMeta[item.reason]?.text || '待人工确认' }}
              </span>
            </span>
            <small>
              {{
                item.channel === 'mail' ? '邮件' : item.platform || 'WhatsApp'
              }}
              ·
              {{
                item.question || item.subject || item.detail || '点击查看对话上下文'
              }}
            </small>
          </span>
          <span class="task-tail">
            <span class="task-action">
              {{ item.reason === 'handoff' ? '接手 ›' : '查看 ›' }}
            </span>
            <small>{{ fmtDateTime(item.time, 'MM-DD HH:mm', '') }}</small>
          </span>
        </button>
      </div>

      <div v-else class="zero-inbox">
        <div class="zero-inbox__icon">
          <IconifyIcon icon="lucide:check" class="size-5" />
        </div>
        <div class="zero-inbox__title">当前没有积压事项</div>
        <div class="zero-inbox__sub">
          AI 遇到高意向议价、定制需求或需人工审核的回复草稿时，会第一时间呈现在这里。
        </div>
      </div>
    </section>

    <!-- 右下：近 7 天平滑面积趋势图（带悬浮十字准星与数据浮窗） -->
    <section
      class="today-card trend ap-rise"
      style="animation-delay: 180ms"
    >
      <header>
        <h2>询盘趋势 <small>近 7 天</small></h2>
        <span class="chart-legend">
          <span><i class="is-total"></i>总询盘</span>
          <span><i class="is-eff"></i>有效询盘</span>
        </span>
      </header>

      <div
        v-if="trend.length"
        class="chart-stage"
        @mouseleave="hoverIdx = null"
      >
        <div
          v-if="hoverPoint"
          class="chart-pop"
          :style="{
            left: `${Math.min(78, Math.max(18, (hoverPoint.x / 560) * 100))}%`,
          }"
        >
          <div class="chart-pop__date">{{ hoverPoint.date }}</div>
          <div class="chart-pop__row">
            <span>总询盘 <b>{{ hoverPoint.total }}</b></span>
            <span>有效 <b class="text-[#AF52DE]">{{ hoverPoint.effective }}</b></span>
            <span class="text-[#86868B]">({{ hoverPoint.rateStr }})</span>
          </div>
        </div>

        <svg
          viewBox="0 0 560 206"
          class="chart"
          role="img"
          aria-label="近七天询盘总量与有效询盘趋势"
        >
          <defs>
            <linearGradient id="today-total-fill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stop-color="#007AFF" stop-opacity=".18" />
              <stop offset="100%" stop-color="#007AFF" stop-opacity="0" />
            </linearGradient>
            <linearGradient id="today-eff-fill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stop-color="#AF52DE" stop-opacity=".14" />
              <stop offset="100%" stop-color="#AF52DE" stop-opacity="0" />
            </linearGradient>
          </defs>

          <!-- 水平参考线与刻度 -->
          <g v-for="tick in [
            { y: 34, val: maxValue },
            { y: 101, val: Math.round(maxValue / 2) },
            { y: 168, val: 0 },
          ]" :key="tick.y">
            <path
              :d="`M28 ${tick.y} H538`"
              stroke="rgba(60,60,67,0.08)"
              stroke-dasharray="3 3"
            />
            <text x="4" :y="tick.y + 3" class="chart-axis">{{ tick.val }}</text>
          </g>

          <path :d="totalAreaPath" fill="url(#today-total-fill)" />
          <path :d="effAreaPath" fill="url(#today-eff-fill)" />

          <path
            :d="totalLinePath"
            fill="none"
            stroke="#007AFF"
            stroke-width="2.5"
            stroke-linecap="round"
            class="chart-line"
          />
          <path
            :d="effLinePath"
            fill="none"
            stroke="#AF52DE"
            stroke-width="2.2"
            stroke-linecap="round"
            class="chart-line is-delayed"
          />

          <!-- 悬停指示竖线 -->
          <line
            v-if="hoverPoint"
            :x1="hoverPoint.x"
            y1="26"
            :x2="hoverPoint.x"
            y2="168"
            stroke="rgba(0,122,255,0.28)"
            stroke-width="1.2"
            stroke-dasharray="3 2"
          />

          <!-- 数据节点与热区 -->
          <g v-for="(d, i) in trend" :key="d.date">
            <circle
              :cx="totalPts[i]!.x"
              :cy="totalPts[i]!.y"
              :r="hoverIdx === i ? 5.5 : 3.8"
              fill="#fff"
              stroke="#007AFF"
              stroke-width="2.3"
              class="chart-dot"
            />
            <circle
              :cx="effPts[i]!.x"
              :cy="effPts[i]!.y"
              :r="hoverIdx === i ? 5 : 3.2"
              fill="#fff"
              stroke="#AF52DE"
              stroke-width="2"
              class="chart-dot"
            />
            <text
              :x="totalPts[i]!.x"
              y="194"
              text-anchor="middle"
              class="chart-date"
              :class="{ 'is-active': hoverIdx === i }"
            >
              {{ d.date.slice(5) }}
            </text>
            <rect
              :x="totalPts[i]!.x - 28"
              y="16"
              width="56"
              height="180"
              fill="transparent"
              class="cursor-pointer"
              @mouseenter="hoverIdx = i"
            />
          </g>
        </svg>
      </div>

      <div v-else class="zero-inbox">
        <div class="zero-inbox__icon is-blue">
          <IconifyIcon icon="lucide:trending-up" class="size-5" />
        </div>
        <div class="zero-inbox__title">暂无近 7 天趋势记录</div>
        <div class="zero-inbox__sub">接入渠道并产生客户咨询后将自动绘制每日曲线</div>
      </div>
    </section>

    <!-- 底部：四大渠道实时状态条 -->
    <div class="today-channels">
      <button
        v-for="(c, idx) in channels"
        :key="c.key"
        type="button"
        class="today-card channel ap-lift ap-press ap-rise"
        :style="{ animationDelay: `${220 + idx * 40}ms` }"
        @click="router.push('/v3/channel')"
      >
        <span class="channel-icon" :class="c.key">
          <IconifyIcon :icon="c.icon" class="size-5" />
        </span>
        <span class="channel-info">
          <span class="channel-title-row">
            <b>{{ c.label }}</b>
            <span
              class="channel-pill"
              :class="
                c.issue
                  ? 'is-warn'
                  : c.connected
                    ? 'is-ok'
                    : 'is-idle'
              "
            >
              {{ c.badge }}
            </span>
          </span>
          <small :class="{ warning: c.issue }">
            <span
              class="ap-dot"
              :class="
                c.issue
                  ? 'ap-dot--orange'
                  : c.connected
                    ? 'ap-dot--green'
                    : ''
              "
            ></span>
            {{ c.detail }}
          </small>
        </span>
        <IconifyIcon icon="lucide:chevron-right" class="channel-arrow" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.apple-today {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  gap: 16px;
  color: var(--ap-label);
}

.today-card {
  min-width: 0;
  padding: 20px 22px;
  background: var(--ap-surface);
  border: 0;
  border-radius: 20px;
  box-shadow: var(--ap-shadow-rest);
}

header {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: -0.015em;
  color: #1d1d1f;
}

h2 small {
  margin-left: 5px;
  font-size: 12px;
  font-weight: 500;
  color: #86868b;
}

.live-pill {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  font-size: 11.5px;
  font-weight: 600;
  color: #6d28d9;
  background: linear-gradient(135deg, #f5f0ff 0%, #eaf3ff 100%);
  border-radius: 999px;
}

.live-pill__dot {
  width: 6px;
  height: 6px;
  background: #34c759;
  border-radius: 50%;
  box-shadow: 0 0 0 2px rgb(52 199 89 / 22%);
}

.ring-body {
  display: flex;
  gap: 26px;
  align-items: center;
}

.rings-wrap {
  position: relative;
  display: grid;
  flex: none;
  place-items: center;
  width: 160px;
  height: 160px;
}

.rings {
  width: 100%;
  height: 100%;
}

.rings-core {
  position: absolute;
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  color: #7c3aed;
  background: linear-gradient(180deg, #faf7ff 0%, #eef4ff 100%);
  border-radius: 50%;
  box-shadow: inset 0 0 0 0.5px rgb(124 58 237 / 16%);
}

.ring-value {
  transition: stroke-dashoffset 900ms var(--ap-ease);
}

.ring-legend {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 13px;
  min-width: 0;
}

.ring-item {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.ring-item__top {
  display: flex;
  gap: 8px;
  align-items: baseline;
  justify-content: space-between;
}

.ring-item__title {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  font-size: 12.5px;
  font-weight: 600;
  color: #3a3a3c;
}

.ring-item__dot {
  flex: none;
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.ring-item strong {
  font-size: 21px;
  font-weight: 700;
  color: #1d1d1f;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
}

.ring-item__track {
  height: 4px;
  overflow: hidden;
  background: #f2f2f7;
  border-radius: 999px;
}

.ring-item__fill {
  height: 100%;
  border-radius: 999px;
  transition: width 700ms var(--ap-ease);
}

footer {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding-top: 13px;
  margin-top: 16px;
  font-size: 12px;
  color: #6e6e73;
  border-top: 0.5px solid var(--ap-separator);
}

.ai-foot-chip {
  padding: 3px 9px;
  background: #f5f5f7;
  border-radius: 999px;
}

.ai-foot-chip b {
  font-weight: 700;
  color: #1d1d1f;
}

.ai-foot-link {
  padding: 3px 6px;
  margin-left: auto;
  font-size: 12px;
  font-weight: 600;
  color: #007aff;
  cursor: pointer;
  background: none;
  border: 0;
}

.ai-foot-link:hover {
  opacity: 0.8;
}

.today-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.stat {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  text-align: left;
  cursor: pointer;
}

.stat__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  color: #6e6e73;
}

.stat__icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 11px;
}

.stat__icon.is-blue {
  color: #007aff;
  background: #e8f2ff;
}

.stat__icon.is-purple {
  color: #8b5cf6;
  background: #f3eeff;
}

.stat__icon.is-green {
  color: #1e8e3e;
  background: #eaf9ee;
}

.stat__icon.is-orange {
  color: #d97706;
  background: #fff4e0;
}

.stat__num-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 10px 0 4px;
}

.stat strong {
  font-size: 33px;
  font-weight: 700;
  line-height: 1.08;
  color: #1d1d1f;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.03em;
}

.stat__jump {
  width: 15px;
  height: 15px;
  color: #c7c7cc;
  opacity: 0;
  transform: translate(-3px, 3px);
  transition: all var(--ap-dur-fast) var(--ap-ease);
}

.stat:hover .stat__jump {
  color: #007aff;
  opacity: 1;
  transform: translate(0, 0);
}

.stat small {
  font-size: 12px;
  color: #86868b;
}

.text-action {
  padding: 4px 10px;
  font-size: 12.5px;
  font-weight: 600;
  color: #007aff;
  cursor: pointer;
  background: #eef6ff;
  border: 0;
  border-radius: 999px;
  transition: background-color var(--ap-dur-fast);
}

.text-action:hover {
  background: #dcebff;
}

.task-count {
  min-width: 22px;
  height: 20px;
  padding: 0 7px;
  font-size: 11.5px;
  font-weight: 700;
  line-height: 20px;
  color: #6e6e73;
  text-align: center;
  background: #f2f2f7;
  border-radius: 999px;
}

.task-count.has-items {
  color: #fff;
  background: #ff9500;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-inline: -8px;
}

.task {
  display: flex;
  gap: 11px;
  align-items: center;
  width: 100%;
  padding: 10px 10px;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 13px;
  transition:
    background-color var(--ap-dur-fast),
    transform var(--ap-dur-fast) var(--ap-ease);
}

.task:hover {
  background: #f5f6fa;
}

.task:active {
  transform: scale(0.992);
}

.task-avatar {
  display: grid;
  flex: none;
  place-items: center;
  width: 36px;
  height: 36px;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  border-radius: 50%;
  box-shadow:
    inset 0 0.5px 0 rgb(255 255 255 / 35%),
    0 1px 3px rgb(0 0 0 / 10%);
}

.task-body {
  flex: 1;
  min-width: 0;
}

.task-title-row {
  display: flex;
  gap: 6px;
  align-items: center;
}

.task-body b {
  overflow: hidden;
  font-size: 13.5px;
  font-weight: 600;
  color: #1d1d1f;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-reason {
  flex: none;
  padding: 1px 7px;
  font-size: 10.5px;
  font-weight: 600;
  border-radius: 999px;
}

.task-reason.is-amber {
  color: #b25e00;
  background: #fff4e0;
}

.task-reason.is-purple {
  color: #7c3aed;
  background: #f3eeff;
}

.task-reason.is-red {
  color: #d70015;
  background: #ffeceb;
}

.task-reason.is-gray {
  color: #6e6e73;
  background: #f2f2f7;
}

.task-body small {
  display: block;
  margin-top: 2px;
  overflow: hidden;
  font-size: 12px;
  color: #86868b;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-tail {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-end;
}

.task-tail small {
  font-size: 11px;
  color: #8e8e93;
  font-variant-numeric: tabular-nums;
}

.task-action {
  padding: 3px 10px;
  font-size: 11.5px;
  font-weight: 600;
  color: #007aff;
  background: #e8f2ff;
  border-radius: 999px;
  transition: all var(--ap-dur-fast);
}

.task:hover .task-action {
  color: #fff;
  background: #007aff;
}

.zero-inbox {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 176px;
  padding: 20px 16px;
  text-align: center;
  background: #f8f8fb;
  border-radius: 16px;
}

.zero-inbox__icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  margin-bottom: 10px;
  color: #1e8e3e;
  background: #eaf9ee;
  border-radius: 14px;
}

.zero-inbox__icon.is-blue {
  color: #007aff;
  background: #e8f2ff;
}

.zero-inbox__title {
  font-size: 14px;
  font-weight: 600;
  color: #1d1d1f;
}

.zero-inbox__sub {
  max-width: 280px;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.55;
  color: #86868b;
}

.trend {
  display: flex;
  flex-direction: column;
}

.chart-stage {
  position: relative;
  flex: 1;
  display: flex;
  align-items: center;
}

.chart-pop {
  position: absolute;
  top: 0;
  z-index: 3;
  padding: 6px 10px;
  pointer-events: none;
  background: rgb(255 255 255 / 94%);
  border: 0.5px solid rgb(0 0 0 / 8%);
  border-radius: 10px;
  box-shadow: 0 6px 18px rgb(15 23 42 / 10%);
  transform: translateX(-50%);
  backdrop-filter: blur(14px);
}

.chart-pop__date {
  font-size: 10.5px;
  font-weight: 600;
  color: #86868b;
}

.chart-pop__row {
  display: flex;
  gap: 8px;
  margin-top: 2px;
  font-size: 11.5px;
  color: #3a3a3c;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.chart {
  width: 100%;
}

.chart-axis {
  font-size: 10px;
  fill: #a1a1a6;
  font-variant-numeric: tabular-nums;
}

.chart-date {
  font-size: 10.5px;
  fill: #8e8e93;
  font-variant-numeric: tabular-nums;
}

.chart-date.is-active {
  font-weight: 700;
  fill: #007aff;
}

.chart-line {
  --ap-path-len: 900;
  stroke-dasharray: var(--ap-path-len);
  animation: ap-draw 900ms var(--ap-ease) both;
}

.chart-line.is-delayed {
  animation-delay: 160ms;
}

.chart-dot {
  transition: r 150ms var(--ap-ease);
}

.chart-legend {
  display: inline-flex;
  gap: 12px;
  align-items: center;
  font-size: 11.5px;
  font-weight: 500;
  color: #6e6e73;
}

.chart-legend span {
  display: inline-flex;
  gap: 5px;
  align-items: center;
}

.chart-legend i {
  width: 10px;
  height: 3.5px;
  border-radius: 99px;
}

.chart-legend i.is-total {
  background: #007aff;
}

.chart-legend i.is-eff {
  background: #af52de;
}

.today-channels {
  display: grid;
  grid-column: 1 / -1;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.channel {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 15px 18px;
  text-align: left;
  cursor: pointer;
}

.channel-icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 40px;
  height: 40px;
  color: #fff;
  background: var(--ap-accent);
  border-radius: 12px;
  box-shadow: inset 0 0.5px 0 rgb(255 255 255 / 30%);
}

.channel-icon.whatsapp {
  background: linear-gradient(135deg, #34c759, #22b348);
}

.channel-icon.messenger {
  background: linear-gradient(135deg, #007aff, #005ecb);
}

.channel-icon.instagram {
  background: linear-gradient(135deg, #ffb24c, #df348c, #8751d6);
}

.channel-icon.mail {
  background: linear-gradient(135deg, #5856d6, #007aff);
}

.channel-info {
  flex: 1;
  min-width: 0;
}

.channel-title-row {
  display: flex;
  gap: 6px;
  align-items: center;
}

.channel b {
  font-size: 14px;
  font-weight: 600;
  color: #1d1d1f;
}

.channel-pill {
  padding: 1px 6px;
  font-size: 10px;
  font-weight: 600;
  border-radius: 999px;
}

.channel-pill.is-ok {
  color: #1e8e3e;
  background: #eaf9ee;
}

.channel-pill.is-warn {
  color: #b25e00;
  background: #fff4e0;
}

.channel-pill.is-idle {
  color: #007aff;
  background: #e8f2ff;
}

.channel small {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-top: 3px;
  font-size: 12px;
  color: #6e6e73;
}

.channel .warning {
  color: #c93400;
}

.channel-arrow {
  flex: none;
  width: 15px;
  height: 15px;
  color: #c7c7cc;
  transition: transform var(--ap-dur) var(--ap-ease);
}

.channel:hover .channel-arrow {
  color: #6e6e73;
  transform: translateX(3px);
}

@media (width <= 1250px) {
  .apple-today {
    grid-template-columns: 1fr;
  }

  .today-channels {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 760px) {
  .today-channels {
    grid-template-columns: 1fr;
  }

  .ring-body {
    flex-direction: column;
    gap: 16px;
  }

  .ring-legend {
    width: 100%;
  }

  .today-stats {
    gap: 10px;
  }
}
</style>
