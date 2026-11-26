<script lang="ts" setup>
/**
 * AI 工作时间（人机分时值守策略）：
 * 上班时间可设业务员优先（超时未回再由 AI 接手），下班及节假日由 AI 7×24 自动值守。
 */
import type { ChannelWorkTimeApi } from '#/api/channel/worktime';

import { computed, onMounted, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import {
  Button,
  Checkbox,
  DatePicker,
  Input,
  InputNumber,
  message,
  Select,
  Switch,
} from 'ant-design-vue';
import dayjs from 'dayjs';

import {
  getWorkTime,
  getWorkTimeStatus,
  saveWorkTime,
} from '#/api/channel/worktime';

defineOptions({ name: 'CgchConfigWorkTime' });

const WEEK = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日'];
const TIMEZONES = [
  { value: 'Asia/Shanghai', label: '中国（北京时间 UTC+8）' },
  { value: 'Asia/Hong_Kong', label: '中国香港（UTC+8）' },
  { value: 'Asia/Singapore', label: '新加坡（UTC+8）' },
  { value: 'Asia/Ho_Chi_Minh', label: '越南（UTC+7）' },
  { value: 'Asia/Kolkata', label: '印度（UTC+5:30）' },
  { value: 'Asia/Dubai', label: '迪拜（UTC+4）' },
  { value: 'Europe/London', label: '伦敦' },
  { value: 'Europe/Berlin', label: '柏林' },
  { value: 'America/New_York', label: '纽约' },
  { value: 'America/Los_Angeles', label: '洛杉矶' },
];

const form = ref<ChannelWorkTimeApi.Config>();
const status = ref<ChannelWorkTimeApi.Status>();
const saving = ref(false);

const days = computed(() =>
  [...(form.value?.schedule ?? [])].sort((a, b) => a.day - b.day),
);

function applyPreset(kind: 'allday' | 'six' | 'standard') {
  if (!form.value) return;
  form.value.schedule = [1, 2, 3, 4, 5, 6, 7].map((d) => ({
    day: d,
    enabled: kind === 'allday' ? true : kind === 'six' ? d <= 6 : d <= 5,
    ranges: kind === 'allday' ? '00:00-24:00' : '09:00-12:00,13:30-18:00',
  }));
}

function copyMonday() {
  const mon = form.value?.schedule.find((d) => d.day === 1);
  if (!form.value || !mon) return;
  form.value.schedule.forEach((d) => {
    if (d.day !== 1 && d.enabled) d.ranges = mon.ranges;
  });
  message.success('已同步周一作息到全部工作日');
}

const holidayPick = ref<any>();
const workdayPick = ref<any>();

const dateConflicts = computed(() =>
  form.value
    ? form.value.holidays.filter((d) => form.value!.extraWorkdays.includes(d))
    : [],
);

function addDate(field: 'extraWorkdays' | 'holidays', v: any) {
  if (!form.value || !v) return;
  const s = dayjs(v).format('YYYY-MM-DD');
  const isHoliday = field === 'holidays';
  const other = isHoliday ? form.value.extraWorkdays : form.value.holidays;
  if (other.includes(s)) {
    message.warning(
      `${s} 已填在「${isHoliday ? '调休上班日' : '节假日'}」里，同一天不能同时存在于两处`,
    );
    return;
  }
  const list = form.value[field];
  if (!list.includes(s)) list.push(s);
  list.sort();
}

async function load() {
  [form.value, status.value] = await Promise.all([
    getWorkTime(),
    getWorkTimeStatus(),
  ]);
}

async function save() {
  if (!form.value) return;
  if (dateConflicts.value.length) {
    message.warning(
      `以下日期同时存在于节假日和调休上班日，请先移除一侧：${dateConflicts.value.join('、')}`,
    );
    return;
  }
  saving.value = true;
  try {
    await saveWorkTime(form.value);
    message.success('工作时间策略已保存');
    status.value = await getWorkTimeStatus();
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="wt">
    <!-- 实时状态横幅卡 -->
    <div v-if="status" class="wt__status-card">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div class="flex flex-wrap items-center gap-2.5">
          <span
            class="ap-crm-pill"
            :class="
              !status.enabled
                ? 'ap-crm-pill--gray'
                : status.inWorkTime
                  ? 'ap-crm-pill--green'
                  : 'ap-crm-pill--purple'
            "
          >
            ●
            {{
              !status.enabled
                ? '分时值守未启用'
                : status.inWorkTime
                  ? '当前处于工作时间'
                  : '当前处于下班 / AI 夜间值守时段'
            }}
          </span>
          <span class="text-xs text-[#6E6E73]">
            当地时间 {{ status.localTime }}（{{ status.timezone }}）
          </span>
          <span
            v-if="status.enabled && status.nextChange"
            class="text-xs text-[#86868B]"
          >
            · 下次切换（{{ status.inWorkTime ? '下班' : '上班' }}）：{{
              status.nextChange
            }}
          </span>
        </div>

        <span
          v-if="status.waitingCount"
          class="ap-crm-pill ap-crm-pill--amber"
        >
          {{ status.waitingCount }} 条新消息正等待业务员优先处理
        </span>
      </div>
      <div class="mt-2 text-xs text-[#48484A]">{{ status.policy }}</div>
    </div>

    <!-- 主配置面板 -->
    <div v-if="form" class="wt__card">
      <div class="wt__head">
        <div class="flex min-w-0 items-center gap-3">
          <span class="wt__icon">
            <IconifyIcon icon="lucide:clock" class="size-5" />
          </span>
          <div>
            <div class="text-[15px] font-bold text-[#1D1D1F]">
              团队工作时间与分时接待策略
            </div>
            <div class="mt-0.5 text-xs text-[#6E6E73]">
              仅影响设为「自动回复」模式的渠道；客诉转人工、违禁词拦截等安全规则始终生效
            </div>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <span class="text-xs font-medium text-[#6E6E73]">
            {{ form.enabled ? '已启用' : '未启用' }}
          </span>
          <Switch v-model:checked="form.enabled" />
          <Button
            type="primary"
            shape="round"
            :loading="saving"
            @click="save"
          >
            保存配置
          </Button>
        </div>
      </div>

      <div :class="{ 'opacity-50 pointer-events-none': !form.enabled }" class="space-y-4">
        <!-- 1. 时区与周排班 -->
        <div class="wt__block">
          <div class="flex flex-wrap items-center justify-between gap-3 mb-3">
            <div>
              <div class="wt__label mb-0">公司所在时区与每周排班</div>
              <div class="text-xs text-[#86868B]">
                多时段用英文逗号分隔，24 小时全天上班填 00:00-24:00
              </div>
            </div>
            <Select
              v-model:value="form.timezone"
              :options="TIMEZONES"
              show-search
              class="w-64"
            />
          </div>

          <div class="mb-3 flex flex-wrap items-center gap-1.5">
            <span class="text-xs text-[#6E6E73] mr-1">快捷排班：</span>
            <button
              type="button"
              class="wt__preset"
              @click="applyPreset('standard')"
            >
              标准双休（周一至周五）
            </button>
            <button
              type="button"
              class="wt__preset"
              @click="applyPreset('six')"
            >
              大小周 / 单休（周一至周六）
            </button>
            <button
              type="button"
              class="wt__preset"
              @click="applyPreset('allday')"
            >
              7×24 全天候
            </button>
            <button
              type="button"
              class="wt__preset is-link"
              @click="copyMonday"
            >
              将周一时间段同步到其他工作日
            </button>
          </div>

          <div class="space-y-2">
            <div v-for="d in days" :key="d.day" class="wt__day-row">
              <Checkbox v-model:checked="d.enabled" class="w-20 font-medium">
                {{ WEEK[d.day] }}
              </Checkbox>
              <Input
                v-model:value="d.ranges"
                :disabled="!d.enabled"
                placeholder="09:00-12:00,13:30-18:00"
                class="max-w-xs"
              />
              <span
                v-if="!d.enabled"
                class="ap-crm-pill ap-crm-pill--gray"
              >
                休息日 · AI 全天值守
              </span>
            </div>
          </div>
        </div>

        <!-- 2. 节假日与调休 -->
        <div class="grid grid-cols-1 gap-3 md:grid-cols-2">
          <div class="wt__block">
            <div class="wt__label">法定节假日（全天休息 · AI 值守）</div>
            <div class="flex flex-wrap items-center gap-1.5">
              <span
                v-for="hd in form.holidays"
                :key="hd"
                class="wt__date-chip"
              >
                <span>{{ hd }}</span>
                <button
                  type="button"
                  @click="
                    form!.holidays = form!.holidays.filter((x) => x !== hd)
                  "
                >
                  ×
                </button>
              </span>
              <DatePicker
                v-model:value="holidayPick"
                size="small"
                placeholder="+ 添加放假日期"
                @change="
                  (v: any) => {
                    addDate('holidays', v);
                    holidayPick = undefined;
                  }
                "
              />
            </div>
          </div>

          <div class="wt__block">
            <div class="wt__label">调休补班日（按周一作息上班）</div>
            <div class="flex flex-wrap items-center gap-1.5">
              <span
                v-for="wd in form.extraWorkdays"
                :key="wd"
                class="wt__date-chip is-work"
              >
                <span>{{ wd }}</span>
                <button
                  type="button"
                  @click="
                    form!.extraWorkdays = form!.extraWorkdays.filter(
                      (x) => x !== wd,
                    )
                  "
                >
                  ×
                </button>
              </span>
              <DatePicker
                v-model:value="workdayPick"
                size="small"
                placeholder="+ 添加补班日期"
                @change="
                  (v: any) => {
                    addDate('extraWorkdays', v);
                    workdayPick = undefined;
                  }
                "
              />
            </div>
          </div>
        </div>

        <div
          v-if="dateConflicts.length"
          class="rounded-xl bg-[#FFECEB] px-3.5 py-2.5 text-xs text-[#D70015]"
        >
          日期冲突提示：{{ dateConflicts.join('、') }} 同时存在于节假日与调休补班日，请移除其中一侧后再保存。
        </div>

        <!-- 3. 工作时间与非工作时间接待策略 -->
        <div class="grid grid-cols-1 gap-3 md:grid-cols-2">
          <div class="wt__block">
            <div class="wt__label">上班时间内 · 新消息处理方式</div>
            <div class="space-y-2">
              <div
                class="wt__policy"
                :class="{ on: form.inHoursPolicy === 'human_first' }"
                @click="form.inHoursPolicy = 'human_first'"
              >
                <div class="font-semibold text-[#1D1D1F]">业务员优先接待</div>
                <div class="mt-1 flex flex-wrap items-center gap-1.5 text-xs text-[#6E6E73]">
                  <span>预留</span>
                  <InputNumber
                    v-model:value="form.takeoverMinutes"
                    :min="1"
                    :max="1440"
                    size="small"
                    class="w-18"
                    @click.stop
                  />
                  <span>分钟给业务员，超时未回再由 AI 自动接手</span>
                </div>
              </div>

              <div
                class="wt__policy"
                :class="{ on: form.inHoursPolicy === 'ai' }"
                @click="form.inHoursPolicy = 'ai'"
              >
                <div class="font-semibold text-[#1D1D1F]">AI 秒级立即回复</div>
                <div class="mt-0.5 text-xs text-[#6E6E73]">
                  上班时间也由 AI 第一时间响应买家
                </div>
              </div>

              <div
                class="wt__policy"
                :class="{ on: form.inHoursPolicy === 'draft' }"
                @click="form.inHoursPolicy = 'draft'"
              >
                <div class="font-semibold text-[#1D1D1F]">AI 仅拟定草稿</div>
                <div class="mt-0.5 text-xs text-[#6E6E73]">
                  不自动发送，由在线业务员审阅确认后发出
                </div>
              </div>
            </div>
          </div>

          <div class="wt__block">
            <div class="wt__label">下班及节假日 · 新消息处理方式</div>
            <div class="space-y-2">
              <div
                class="wt__policy"
                :class="{ on: form.offHoursPolicy === 'ai' }"
                @click="form.offHoursPolicy = 'ai'"
              >
                <div class="font-semibold text-[#1D1D1F]">
                  AI 夜间自动值守接待（推荐）
                </div>
                <div class="mt-0.5 text-xs text-[#6E6E73]">
                  克服跨国时差秒回买家；遇复杂定制需求礼貌告知上班后跟进
                </div>
              </div>

              <div
                class="wt__policy"
                :class="{ on: form.offHoursPolicy === 'draft' }"
                @click="form.offHoursPolicy = 'draft'"
              >
                <div class="font-semibold text-[#1D1D1F]">
                  仅生成草稿留待次日上班处理
                </div>
                <div class="mt-0.5 text-xs text-[#6E6E73]">
                  夜间不自动发信，提前备好草稿供业务员上班一键发出
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.wt {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.wt__status-card {
  padding: 14px 18px;
  background: #fff;
  border-radius: 16px;
  box-shadow: var(--ap-shadow-rest);
}

.wt__card {
  padding: 20px 22px;
  background: #fff;
  border-radius: 18px;
  box-shadow: var(--ap-shadow-rest);
}

.wt__head {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 16px;
  margin-bottom: 16px;
  border-bottom: 0.5px solid var(--ap-separator);
}

.wt__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 42px;
  height: 42px;
  color: #007aff;
  background: #e8f2ff;
  border-radius: 12px;
}

.wt__block {
  padding: 15px 16px;
  background: #f7f8fb;
  border-radius: 14px;
}

.wt__label {
  margin-bottom: 8px;
  font-size: 13.5px;
  font-weight: 700;
  color: #1d1d1f;
}

.wt__preset {
  height: 27px;
  padding: 0 11px;
  font-size: 12px;
  font-weight: 500;
  color: #3a3a3c;
  cursor: pointer;
  background: #fff;
  border: 0;
  border-radius: 999px;
  box-shadow: 0 1px 2px rgb(0 0 0 / 5%);
}

.wt__preset:hover {
  color: #007aff;
}

.wt__preset.is-link {
  color: #007aff;
  background: #e8f2ff;
  box-shadow: none;
}

.wt__day-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 7px 11px;
  background: #fff;
  border-radius: 10px;
}

.wt__date-chip {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 26px;
  padding: 0 9px;
  font-size: 12px;
  font-weight: 500;
  color: #b25e00;
  background: #fff4e0;
  border-radius: 999px;
}

.wt__date-chip.is-work {
  color: #007aff;
  background: #e8f2ff;
}

.wt__date-chip button {
  padding: 0;
  font-size: 14px;
  line-height: 1;
  color: inherit;
  cursor: pointer;
  background: transparent;
  border: 0;
  opacity: 0.7;
}

.wt__policy {
  padding: 11px 13px;
  font-size: 13px;
  cursor: pointer;
  background: #fff;
  border: 1.5px solid transparent;
  border-radius: 11px;
  transition: all var(--ap-dur-fast);
}

.wt__policy.on {
  background: rgb(0 122 255 / 5%);
  border-color: #007aff;
}
</style>
