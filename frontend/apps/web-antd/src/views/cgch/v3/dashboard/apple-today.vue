<script setup lang="ts">
import type { ChannelDashboardV3Api } from '#/api/channel/dashboard';
import type { ChannelWaApi } from '#/api/channel/whatsapp';
import type { ChannelMailApi } from '#/api/channel/mail';
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { Empty } from 'ant-design-vue';
import { IconifyIcon } from '@vben/icons';
import { getWaAccountList } from '#/api/channel/whatsapp';
import { getMailAccountList } from '#/api/channel/mail';
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
    text: '#a254d4',
    radius: 74,
  },
  {
    label: '24 小时客户首回率',
    value: props.data?.ai.customerBack24hRate,
    color: '#34c759',
    text: '#247d3b',
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
  },
  {
    label: 'AI 已回复',
    value: props.data?.ai.replies,
    detail: '所选时间范围内的回复条数',
  },
  {
    label: '新增线索',
    value: props.data?.business.clues,
    detail: '进入 CRM 的新采购意向',
  },
  {
    label: 'AI 完成任务',
    value: props.data?.ai.tasks,
    detail: '所选时间范围内的完成数',
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
function points(key: 'effective' | 'total') {
  return trend.value
    .map(
      (d, i) =>
        `${14 + (i * 532) / Math.max(1, trend.value.length - 1)},${170 - (d[key] / maxValue.value) * 140}`,
    )
    .join(' ');
}
const channels = computed(() => [
  ...(['whatsapp', 'messenger', 'instagram'] as const).map((platform) => {
    const list = accounts.value?.filter(
      (a) => (a.platform || 'whatsapp') === platform,
    );
    const issue = list?.some(
      (a) => a.enabled === false || a.connState === 'close',
    );
    return {
      key: platform,
      label: {
        whatsapp: 'WhatsApp',
        messenger: 'Messenger',
        instagram: 'Instagram',
      }[platform],
      icon: { whatsapp: 'WA', messenger: 'FB', instagram: 'IG' }[platform],
      detail: !list
        ? '状态暂不可用'
        : !list.length
          ? '尚未接入'
          : issue
            ? '有账号需要处理'
            : `已接入 ${list.length} 个账号`,
      issue,
    };
  }),
  {
    key: 'mail',
    label: '邮箱',
    icon: '邮',
    detail: !mail.value
      ? '状态暂不可用'
      : !mail.value.length
        ? '尚未接入'
        : mail.value.some((a) => a.lastSyncError || a.enabled === false)
          ? '有邮箱需要处理'
          : `已接入 ${mail.value.length} 个邮箱`,
    issue: mail.value?.some((a) => a.lastSyncError || a.enabled === false),
  },
]);
</script>

<template>
  <div class="apple-today">
    <section class="today-card ai-performance">
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
            <linearGradient id="today-ai-ring">
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
                opacity=".13"
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
            <span :style="{ color: r.text }">{{ r.label }}</span
            ><strong>{{ pct(r.value) }}</strong>
          </div>
        </div>
      </div>
      <footer>
        <span
          >AI CRM 录入 <b>{{ data?.ai.crmWrites ?? '—' }}</b> 次</span
        ><span>缺少数据时显示 —</span>
      </footer>
    </section>
    <div class="today-stats">
      <section v-for="m in metrics" :key="m.label" class="today-card stat">
        <span>{{ m.label }}</span
        ><strong>{{ m.value?.toLocaleString() ?? '—' }}</strong
        ><small>{{ m.detail }}</small>
      </section>
    </div>
    <section class="today-card tasks">
      <header>
        <h2>
          等你处理 <span class="task-count">{{ support.length }}</span>
        </h2>
        <button class="text-action" @click="emit('details')">全部 ›</button>
      </header>
      <button
        v-for="item in visibleSupport"
        :key="`${item.channel}-${item.draftId}`"
        class="task"
        @click="emit('open', item)"
      >
        <span class="task-icon" :class="{ 'is-ai': item.reason !== 'handoff' }"
          ><IconifyIcon
            :icon="
              item.reason === 'handoff'
                ? 'lucide:user-round'
                : 'lucide:sparkles'
            "
        /></span>
        <span class="task-body"
          ><b
            >{{ item.contact || '客户' }} ·
            {{ reason[item.reason] || '需要你确认' }}</b
          ><small
            >{{
              item.channel === 'mail' ? '邮件' : item.platform || '即时消息'
            }}
            ·
            {{
              item.question || item.subject || item.detail || '查看会话详情'
            }}</small
          ></span
        >
        <span class="task-tail"
          ><span class="task-action">{{
            item.reason === 'handoff' ? '接手' : '查看'
          }}</span
          ><small>{{ fmtDateTime(item.time, 'MM-DD HH:mm', '') }}</small></span
        >
      </button>
      <Empty
        v-if="!support.length"
        :image="Empty.PRESENTED_IMAGE_SIMPLE"
        description="暂时没有需要你处理的事项"
      />
    </section>
    <section class="today-card trend">
      <header>
        <h2>询盘趋势 <small>近 7 天</small></h2>
        <span class="chart-legend"
          ><i></i>总询盘 <i class="effective"></i>有效询盘</span
        >
      </header>
      <svg
        v-if="trend.length"
        viewBox="0 0 560 210"
        class="chart"
        role="img"
        aria-label="近七天询盘总量与有效询盘趋势"
      >
        <path
          v-for="y in [30, 100, 170]"
          :key="y"
          :d="`M14 ${y} H546`"
          stroke="#e9e9ef"
        />
        <polyline
          :points="points('total')"
          fill="none"
          stroke="#007aff"
          stroke-width="2.2"
          stroke-linejoin="round"
        />
        <polyline
          :points="points('effective')"
          fill="none"
          stroke="#af52de"
          stroke-width="2.2"
          stroke-linejoin="round"
        />
        <g v-for="(d, i) in trend" :key="d.date">
          <circle
            :cx="14 + (i * 532) / Math.max(1, trend.length - 1)"
            :cy="170 - (d.total / maxValue) * 140"
            r="4"
            fill="#007aff"
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
        v-for="c in channels"
        :key="c.key"
        class="today-card channel"
        @click="router.push('/v3/channel')"
      >
        <span class="channel-icon" :class="c.key">{{ c.icon }}</span
        ><span
          ><b>{{ c.label }}</b
          ><small :class="{ warning: c.issue }">{{ c.detail }}</small></span
        ><span class="channel-arrow">›</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.apple-today {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
  color: #1d1d1f;
}
.today-card {
  min-width: 0;
  padding: 21px;
  background: #fff;
  border: 0;
  border-radius: 22px;
  box-shadow: 0 2px 12px rgb(0 0 0 / 4%);
}
header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 18px;
}
h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
}
h2 small {
  font-size: 11px;
  color: #86868b;
  font-weight: 400;
}
.period {
  padding: 3px 9px;
  background: #f2f2f7;
  border-radius: 99px;
  font-size: 11px;
  color: #6e6e73;
}
.ring-body {
  display: flex;
  align-items: center;
  gap: 30px;
}
.rings {
  width: 164px;
  height: 164px;
  flex: none;
}
.ring-value {
  transition: stroke-dashoffset 0.8s cubic-bezier(0.32, 0.72, 0, 1);
}
.ring-legend {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 16px;
}
.ring-legend > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.ring-legend span {
  font-size: 12px;
  font-weight: 600;
}
.ring-legend strong {
  font-size: 27px;
  font-variant-numeric: tabular-nums;
}
footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding-top: 12px;
  margin-top: 16px;
  border-top: 0.5px solid #e5e5ea;
  font-size: 11px;
  color: #86868b;
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
.stat > span {
  font-size: 13px;
  color: #86868b;
}
.stat strong {
  font-size: 34px;
  letter-spacing: -1px;
  font-variant-numeric: tabular-nums;
  margin: 5px 0;
}
.stat small {
  font-size: 11px;
  color: #6e6e73;
}
.text-action {
  border: 0;
  background: none;
  color: #007aff;
  cursor: pointer;
  font-size: 13px;
}
.task-count {
  padding: 3px 7px;
  border-radius: 6px;
  background: #f2f2f7;
  color: #86868b;
  font-size: 12px;
  margin-left: 5px;
}
.task {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 16px 0;
  border: 0;
  border-bottom: 0.5px solid #e5e5ea;
  text-align: left;
  background: none;
  cursor: pointer;
}
.task:last-child {
  border: 0;
}
.task:hover {
  background: #f7f8fb;
}
.task-icon {
  display: grid;
  place-items: center;
  width: 33px;
  height: 33px;
  flex: none;
  background: #ff9500;
  color: white;
  border-radius: 10px;
}
.task-icon :deep(svg) {
  width: 17px;
  height: 17px;
}
.task-icon.is-ai {
  background: linear-gradient(
    135deg,
    #ff9500,
    #ff2d92 40%,
    #af52de 70%,
    #007aff
  );
}
.task-body {
  min-width: 0;
  flex: 1;
}
.task-body b {
  font-size: 14px;
  font-weight: 500;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.task-body small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #86868b;
  font-size: 12px;
  margin-top: 5px;
}
.task-tail {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 5px;
}
.task-tail small {
  font-size: 10px;
  color: #86868b;
}
.task-action {
  padding: 4px 11px;
  background: #e8f2ff;
  color: #007aff;
  border-radius: 99px;
  font-size: 12px;
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
  fill: #86868b;
  font-size: 10px;
}
.chart-legend {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 10px;
  color: #86868b;
}
.chart-legend i {
  width: 9px;
  height: 3px;
  background: #007aff;
}
.chart-legend .effective {
  background: #af52de;
  margin-left: 7px;
}
.today-channels {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}
.channel {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  text-align: left;
  cursor: pointer;
}
.channel-icon {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 11px;
  font-size: 12px;
  font-weight: 700;
  color: white;
  background: #007aff;
  flex: none;
}
.channel-icon.whatsapp {
  background: #25c965;
}
.channel-icon.instagram {
  background: linear-gradient(135deg, #ffb24c, #df348c, #8751d6);
}
.channel b {
  display: block;
  font-size: 14px;
  font-weight: 600;
}
.channel small {
  display: block;
  color: #6e6e73;
  font-size: 11px;
  margin-top: 2px;
}
.channel .warning {
  color: #b36b20;
}
.channel-arrow {
  margin-left: auto;
  color: #b1b1b7;
}
button:focus-visible {
  outline: 2px solid #007aff;
  outline-offset: 3px;
}
@media (width <= 1250px) {
  .apple-today {
    grid-template-columns: 1fr;
  }
  .ring-legend {
    max-width: 350px;
  }
  .ring-body {
    justify-content: center;
  }
}
@media (width <= 700px) {
  .today-channels {
    grid-template-columns: 1fr 1fr;
  }
  .ring-body {
    gap: 18px;
  }
  .rings {
    width: 135px;
    height: 135px;
  }
  .ring-legend strong {
    font-size: 23px;
  }
  .ring-legend span {
    font-size: 11px;
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
@media (prefers-reduced-motion: reduce) {
  .ring-value {
    transition: none;
  }
}
</style>
