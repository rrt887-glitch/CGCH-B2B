<script setup lang="ts">
import type { ChannelDashboardV3Api } from '#/api/channel/dashboard';
import type { ChannelMailApi } from '#/api/channel/mail';
import type { ChannelWaApi } from '#/api/channel/whatsapp';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import { Empty } from 'ant-design-vue';

import { getMailAccountList } from '#/api/channel/mail';
import { getWaAccountList } from '#/api/channel/whatsapp';
import { ApNumber } from '#/components/apple';
import { fmtDateTime } from '#/utils/datetime';

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
    value: props.data?.ai.replyIn1hRate,
    color: 'url(#today-ai-ring)',
    text: '#af52de',
    radius: 74,
  },
  {
    label: '24 小时客户首回率',
    value: props.data?.ai.customerBack24hRate,
    color: '#34c759',
    text: '#248a3d',
    radius: 56,
  },
  {
    label: '询盘转报价',
    value: props.data?.business.inquiryToQuoteRate,
    color: '#007aff',
    text: '#007aff',
    radius: 38,
  },
]);

const metrics = computed(() => [
  {
    label: '询盘总量',
    value: props.data?.traffic.total,
    detail: '所选时间范围内的咨询',
    icon: 'lucide:inbox',
    tint: 'blue',
  },
  {
    label: 'AI 已回复',
    value: props.data?.ai.replies,
    detail: '所选时间范围内的回复条数',
    icon: 'lucide:sparkles',
    tint: 'purple',
  },
  {
    label: '新增线索',
    value: props.data?.business.clues,
    detail: '进入 CRM 的新采购意向',
    icon: 'lucide:user-plus',
    tint: 'green',
  },
  {
    label: 'AI 完成任务',
    value: props.data?.ai.tasks,
    detail: '所选时间范围内的完成数',
    icon: 'lucide:check-circle-2',
    tint: 'orange',
  },
]);

const support = computed(() => [
  ...(props.data?.support.im ?? []),
  ...(props.data?.support.mail ?? []),
]);
const visibleSupport = computed(() => support.value.slice(0, 4));
const reason: Record<string, string> = {
  handoff: '要求转人工',
  forbidden: '回复需要核对',
  send_failed: '消息发送失败',
  no_credit: 'AI 点数不足',
  window_closed: '回复窗口已关闭',
};

const trend = computed(() => props.data?.trend ?? []);
const maxValue = computed(() =>
  Math.max(1, ...trend.value.map((d) => d.total)),
);

function coords(key: 'effective' | 'total') {
  const len = Math.max(1, trend.value.length - 1);
  return trend.value.map((d, i) => ({
    x: 14 + (i * 532) / len,
    y: 170 - (d[key] / maxValue.value) * 140,
  }));
}

function points(key: 'effective' | 'total') {
  return coords(key)
    .map((p) => `${p.x},${p.y}`)
    .join(' ');
}

const totalAreaPath = computed(() => {
  const pts = coords('total');
  if (!pts.length) return '';
  const line = pts.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x} ${p.y}`).join(' ');
  const last = pts[pts.length - 1]!;
  const first = pts[0]!;
  return `${line} L${last.x} 170 L${first.x} 170 Z`;
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
        ? '状态暂不可用'
        : !list.length
          ? '尚未接入'
          : issue
            ? '有账号需要处理'
            : `已接入 ${list.length} 个账号`,
      issue,
      connected,
    };
  }),
  {
    key: 'mail',
    label: '邮箱',
    icon: 'lucide:mail',
    detail: !mail.value
      ? '状态暂不可用'
      : !mail.value.length
        ? '尚未接入'
        : mail.value.some((a) => a.lastSyncError || a.enabled === false)
          ? '有邮箱需要处理'
          : `已接入 ${mail.value.length} 个邮箱`,
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
    <section class="today-card ai-performance ap-rise">
      <header>
        <h2>AI 正在为你工作</h2>
        <span class="period">所选时段</span>
      </header>
      <div class="ring-body">
        <svg
          viewBox="0 0 168 168"
          class="rings"
          role="img"
          aria-label="AI 回复率、客户首回率及询盘转报价率，具体数值见右侧"
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
                :stroke="r.color"
                stroke-width="14"
                opacity=".14"
              />
              <circle
                cx="84"
                cy="84"
                :r="r.radius"
                fill="none"
                :stroke="r.color"
                stroke-width="14"
                stroke-linecap="round"
                :stroke-dasharray="2 * Math.PI * r.radius"
                :stroke-dashoffset="
                  2 *
                  Math.PI *
                  r.radius *
                  (1 - Math.max(0, Math.min(1, r.value ?? 0)))
                "
                class="ring-value"
              />
            </template>
          </g>
        </svg>
        <div class="ring-legend">
          <div v-for="r in rings" :key="r.label">
            <span :style="{ color: r.text }">{{ r.label }}</span>
            <strong>{{ pct(r.value) }}</strong>
          </div>
        </div>
      </div>
      <footer>
        <span>
          AI CRM 录入 <b><ApNumber :value="data?.ai.crmWrites" /></b> 次
        </span>
        <span>缺少数据时显示 —</span>
      </footer>
    </section>

    <div class="today-stats">
      <section
        v-for="(m, idx) in metrics"
        :key="m.label"
        class="today-card stat ap-lift ap-rise"
        :style="{ animationDelay: `${(idx + 1) * 45}ms` }"
      >
        <div class="stat__top">
          <span>{{ m.label }}</span>
          <span class="stat__icon" :class="`is-${m.tint}`">
            <IconifyIcon :icon="m.icon" class="size-4" />
          </span>
        </div>
        <strong><ApNumber :value="m.value" /></strong>
        <small>{{ m.detail }}</small>
      </section>
    </div>

    <section
      class="today-card tasks ap-rise"
      style="animation-delay: 140ms"
    >
      <header>
        <h2>
          等你处理 <span class="task-count">{{ support.length }}</span>
        </h2>
        <button type="button" class="text-action" @click="emit('details')">
          全部 ›
        </button>
      </header>
      <div class="task-list">
        <button
          v-for="item in visibleSupport"
          :key="`${item.channel}-${item.draftId}`"
          type="button"
          class="task"
          @click="emit('open', item)"
        >
          <span
            class="task-icon"
            :class="{ 'is-ai': item.reason !== 'handoff' }"
          >
            <IconifyIcon
              :icon="
                item.reason === 'handoff'
                  ? 'lucide:user-round'
                  : 'lucide:sparkles'
              "
            />
          </span>
          <span class="task-body">
            <b>
              {{ item.contact || '客户' }} ·
              {{ reason[item.reason] || '需要你确认' }}
            </b>
            <small>
              {{
                item.channel === 'mail' ? '邮件' : item.platform || '即时消息'
              }}
              ·
              {{
                item.question || item.subject || item.detail || '查看会话详情'
              }}
            </small>
          </span>
          <span class="task-tail">
            <span class="task-action">
              {{ item.reason === 'handoff' ? '接手' : '查看' }}
            </span>
            <small>{{ fmtDateTime(item.time, 'MM-DD HH:mm', '') }}</small>
          </span>
        </button>
      </div>
      <Empty
        v-if="!support.length"
        :image="Empty.PRESENTED_IMAGE_SIMPLE"
        description="暂时没有需要你处理的事项"
      />
    </section>

    <section
      class="today-card trend ap-rise"
      style="animation-delay: 180ms"
    >
      <header>
        <h2>询盘趋势 <small>近 7 天</small></h2>
        <span class="chart-legend">
          <i></i>总询盘 <i class="effective"></i>有效询盘
        </span>
      </header>
      <svg
        v-if="trend.length"
        viewBox="0 0 560 210"
        class="chart"
        role="img"
        aria-label="近七天询盘总量与有效询盘趋势"
      >
        <defs>
          <linearGradient id="today-trend-fill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stop-color="#007aff" stop-opacity=".16" />
            <stop offset="100%" stop-color="#007aff" stop-opacity="0" />
          </linearGradient>
        </defs>
        <path
          v-for="y in [30, 100, 170]"
          :key="y"
          :d="`M14 ${y} H546`"
          stroke="rgba(60,60,67,0.1)"
          stroke-dasharray="3 3"
        />
        <path :d="totalAreaPath" fill="url(#today-trend-fill)" />
        <polyline
          :points="points('total')"
          fill="none"
          stroke="#007aff"
          stroke-width="2.4"
          stroke-linecap="round"
          stroke-linejoin="round"
          class="chart-line"
        />
        <polyline
          :points="points('effective')"
          fill="none"
          stroke="#af52de"
          stroke-width="2.2"
          stroke-linecap="round"
          stroke-linejoin="round"
          class="chart-line is-delayed"
        />
        <g v-for="(d, i) in trend" :key="d.date">
          <circle
            :cx="14 + (i * 532) / Math.max(1, trend.length - 1)"
            :cy="170 - (d.total / maxValue) * 140"
            r="4.5"
            fill="#fff"
            stroke="#007aff"
            stroke-width="2.4"
            class="chart-dot"
          >
            <title>
              {{ d.date }}：总询盘 {{ d.total }}，有效询盘 {{ d.effective }}
            </title>
          </circle>
          <text
            :x="14 + (i * 532) / Math.max(1, trend.length - 1)"
            y="200"
            text-anchor="middle"
          >
            {{ d.date.slice(5) }}
          </text>
        </g>
        <text x="14" y="16">{{ maxValue }}</text>
        <text x="14" y="185">0</text>
      </svg>
      <Empty
        v-else
        :image="Empty.PRESENTED_IMAGE_SIMPLE"
        description="暂无趋势数据"
      />
    </section>

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
          <b>{{ c.label }}</b>
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
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
  color: var(--ap-label);
}

.today-card {
  min-width: 0;
  padding: 18px 20px;
  background: var(--ap-surface);
  border: 0;
  border-radius: var(--ap-radius-lg);
  box-shadow: var(--ap-shadow-rest);
}

header {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

h2 {
  margin: 0;
  font-size: var(--ap-text-headline);
  font-weight: 600;
}

h2 small {
  margin-left: 4px;
  font-size: var(--ap-text-footnote);
  font-weight: 400;
  color: var(--ap-label-3);
}

.period {
  padding: 3px 10px;
  font-size: 11px;
  font-weight: 500;
  color: var(--ap-label-2);
  background: var(--ap-fill-4);
  border-radius: var(--ap-radius-pill);
}

.ring-body {
  display: flex;
  gap: 30px;
  align-items: center;
}

.rings {
  flex: none;
  width: 164px;
  height: 164px;
}

.ring-value {
  transition: stroke-dashoffset 900ms var(--ap-ease);
}

.ring-legend {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 16px;
}

.ring-legend > div {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.ring-legend span {
  font-size: 13px;
  font-weight: 600;
}

.ring-legend strong {
  font-size: 26px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
}

footer {
  display: flex;
  gap: 12px;
  justify-content: space-between;
  padding-top: 12px;
  margin-top: 16px;
  font-size: 12px;
  color: var(--ap-label-3);
  border-top: 0.5px solid var(--ap-separator);
}

.today-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.stat {
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.stat__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 500;
  color: var(--ap-label-2);
}

.stat__icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
}

.stat__icon.is-blue {
  color: var(--ap-accent);
  background: var(--ap-accent-soft);
}

.stat__icon.is-purple {
  color: var(--ap-purple);
  background: rgb(175 82 222 / 12%);
}

.stat__icon.is-green {
  color: var(--ap-green-text);
  background: rgb(52 199 89 / 12%);
}

.stat__icon.is-orange {
  color: var(--ap-orange-text);
  background: rgb(255 149 0 / 12%);
}

.stat strong {
  margin: 8px 0 4px;
  font-size: 34px;
  font-weight: 700;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.03em;
}

.stat small {
  font-size: 12px;
  color: var(--ap-label-3);
}

.text-action {
  padding: 4px 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--ap-accent);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 8px;
  transition: background-color var(--ap-dur-fast);
}

.text-action:hover {
  background: var(--ap-accent-softer);
}

.task-count {
  padding: 2px 8px;
  margin-left: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--ap-label-2);
  background: var(--ap-fill-4);
  border-radius: var(--ap-radius-pill);
}

.task-list {
  margin-inline: -10px;
}

.task {
  display: flex;
  gap: 12px;
  align-items: center;
  width: 100%;
  padding: 13px 10px;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 12px;
  transition:
    background-color var(--ap-dur-fast),
    transform var(--ap-dur-fast) var(--ap-ease);
}

.task + .task {
  box-shadow: inset 0 0.5px 0 0 var(--ap-separator);
}

.task:hover {
  background: var(--ap-fill-4);
}

.task:active {
  transform: scale(0.99);
}

.task-icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 34px;
  height: 34px;
  color: #fff;
  background: var(--ap-orange);
  border-radius: 10px;
}

.task-icon :deep(svg) {
  width: 17px;
  height: 17px;
}

.task-icon.is-ai {
  background: var(--ap-intelligence);
}

.task-body {
  flex: 1;
  min-width: 0;
}

.task-body b {
  display: block;
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-body small {
  display: block;
  margin-top: 3px;
  overflow: hidden;
  font-size: 12px;
  color: var(--ap-label-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-tail {
  display: flex;
  flex-direction: column;
  gap: 5px;
  align-items: flex-end;
}

.task-tail small {
  font-size: 11px;
  color: var(--ap-label-3);
  font-variant-numeric: tabular-nums;
}

.task-action {
  padding: 4px 12px;
  font-size: 12px;
  font-weight: 500;
  color: var(--ap-accent);
  background: var(--ap-accent-soft);
  border-radius: var(--ap-radius-pill);
  transition:
    background-color var(--ap-dur-fast),
    color var(--ap-dur-fast);
}

.task:hover .task-action {
  color: #fff;
  background: var(--ap-accent);
}

.trend {
  display: flex;
  flex-direction: column;
}

.chart {
  width: 100%;
  margin: auto 0;
}

.chart text {
  font-size: 10px;
  fill: var(--ap-label-3);
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
  transition: r 160ms var(--ap-ease);
}

.chart-dot:hover {
  r: 6.5;
}

.chart-legend {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 11px;
  color: var(--ap-label-3);
}

.chart-legend i {
  width: 10px;
  height: 3px;
  background: var(--ap-accent);
  border-radius: 2px;
}

.chart-legend .effective {
  margin-left: 8px;
  background: var(--ap-purple);
}

.today-channels {
  display: grid;
  grid-column: 1 / -1;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.channel {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 16px 18px;
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
}

.channel-icon.whatsapp {
  background: linear-gradient(135deg, #34c759, #25b84f);
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

.channel b {
  display: block;
  font-size: 14px;
  font-weight: 600;
}

.channel small {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-top: 3px;
  font-size: 12px;
  color: var(--ap-label-2);
}

.channel .warning {
  color: var(--ap-orange-text);
}

.channel-arrow {
  flex: none;
  width: 16px;
  height: 16px;
  color: var(--ap-label-4);
  transition: transform var(--ap-dur) var(--ap-ease);
}

.channel:hover .channel-arrow {
  color: var(--ap-label-2);
  transform: translateX(3px);
}

@media (width <= 1250px) {
  .apple-today {
    grid-template-columns: 1fr;
  }

  .ring-legend {
    max-width: 360px;
  }

  .ring-body {
    justify-content: center;
  }
}

@media (width <= 760px) {
  .today-channels {
    grid-template-columns: 1fr 1fr;
  }

  .ring-body {
    gap: 18px;
  }

  .rings {
    width: 136px;
    height: 136px;
  }

  .ring-legend strong {
    font-size: 22px;
  }

  .today-card {
    padding: 17px;
  }

  .task-tail small {
    display: none;
  }

  .today-stats {
    gap: 12px;
  }
}
</style>
