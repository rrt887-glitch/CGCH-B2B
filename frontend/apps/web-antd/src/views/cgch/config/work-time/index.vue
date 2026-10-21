<script lang="ts" setup>
/**
 * 工作时间：上班时间业务员优先（超时无人回复由 AI 接手）、下班时间 AI 接待；支持节假日与调休。
 * 只影响数字员工「自动回复」模式；「只起草」模式不受影响。
 */
import type { ChannelWorkTimeApi } from '#/api/channel/worktime';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Alert,
  Badge,
  Button,
  Card,
  Checkbox,
  DatePicker,
  Form,
  FormItem,
  Input,
  InputNumber,
  message,
  Radio,
  RadioGroup,
  Select,
  Space,
  Switch,
  Tag,
} from 'ant-design-vue';
import dayjs from 'dayjs';

import { getWorkTime, getWorkTimeStatus, saveWorkTime } from '#/api/channel/worktime';

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

const days = computed(() => [...(form.value?.schedule ?? [])].sort((a, b) => a.day - b.day));

function applyPreset(kind: 'standard' | 'six' | 'allday') {
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
}

const holidayPick = ref<any>();
const workdayPick = ref<any>();
function addDate(list: string[], v: any) {
  if (!v) return;
  const s = dayjs(v).format('YYYY-MM-DD');
  if (!list.includes(s)) list.push(s);
  list.sort();
}

async function load() {
  [form.value, status.value] = await Promise.all([getWorkTime(), getWorkTimeStatus()]);
}
async function save() {
  if (!form.value) return;
  saving.value = true;
  try {
    await saveWorkTime(form.value);
    message.success('已保存');
    status.value = await getWorkTimeStatus();
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<template>
  <Page auto-content-height>
    <Card v-if="status" :bordered="false" class="mb-4">
      <div class="flex flex-wrap items-center gap-x-6 gap-y-2">
        <div>
          <Badge :status="!status.enabled ? 'default' : status.inWorkTime ? 'success' : 'warning'" />
          <span class="font-medium">{{ !status.enabled ? '未启用' : status.inWorkTime ? '工作时间' : '非工作时间' }}</span>
        </div>
        <div class="text-sm text-gray-500">公司当地时间 {{ status.localTime }}（{{ status.timezone }}）</div>
        <div v-if="status.enabled && status.nextChange" class="text-sm text-gray-500">
          {{ status.inWorkTime ? '下班' : '上班' }}时间：{{ status.nextChange }}
        </div>
        <Tag v-if="status.waitingCount" color="orange">{{ status.waitingCount }} 条消息等待业务员回复</Tag>
      </div>
      <div class="mt-2 text-sm">{{ status.policy }}</div>
    </Card>

    <Card v-if="form" title="工作时间" :bordered="false">
      <template #extra>
        <Space>
          <span class="text-sm">启用</span>
          <Switch v-model:checked="form.enabled" />
          <Button type="primary" :loading="saving" @click="save">保存</Button>
        </Space>
      </template>
      <Alert
        class="mb-4"
        type="info"
        show-icon
        message="只影响数字员工设为「自动回复」的渠道。投诉退款转人工、违禁词、客户未托管等规则照常生效。"
      />
      <Form layout="vertical" :disabled="!form.enabled">
        <FormItem label="时区">
          <Select v-model:value="form.timezone" :options="TIMEZONES" show-search class="w-72" />
        </FormItem>

        <FormItem label="每周上班时间">
          <Space class="mb-2">
            <Button size="small" @click="applyPreset('standard')">周一至周五</Button>
            <Button size="small" @click="applyPreset('six')">周一至周六</Button>
            <Button size="small" @click="applyPreset('allday')">全天候</Button>
            <Button size="small" type="link" @click="copyMonday">把周一的时间段应用到其他上班日</Button>
          </Space>
          <div v-for="d in days" :key="d.day" class="mb-2 flex items-center gap-3">
            <Checkbox v-model:checked="d.enabled" class="w-16">{{ WEEK[d.day] }}</Checkbox>
            <Input v-model:value="d.ranges" :disabled="!form.enabled || !d.enabled" placeholder="09:00-12:00,13:30-18:00" class="w-80" />
            <span v-if="!d.enabled" class="text-sm text-gray-400">休息</span>
          </div>
          <div class="text-xs text-gray-500">多个时间段用英文逗号分隔；全天上班填 00:00-24:00。</div>
        </FormItem>

        <FormItem label="节假日（全天休息）">
          <Space wrap>
            <Tag v-for="h in form.holidays" :key="h" closable @close="form!.holidays = form!.holidays.filter((x) => x !== h)">{{ h }}</Tag>
            <DatePicker v-model:value="holidayPick" size="small" placeholder="添加日期" @change="(v: any) => { addDate(form!.holidays, v); holidayPick = undefined; }" />
          </Space>
        </FormItem>
        <FormItem label="调休上班日（按周一的时间上班）">
          <Space wrap>
            <Tag v-for="h in form.extraWorkdays" :key="h" closable @close="form!.extraWorkdays = form!.extraWorkdays.filter((x) => x !== h)">{{ h }}</Tag>
            <DatePicker v-model:value="workdayPick" size="small" placeholder="添加日期" @change="(v: any) => { addDate(form!.extraWorkdays, v); workdayPick = undefined; }" />
          </Space>
        </FormItem>

        <FormItem label="工作时间内，新消息怎么处理">
          <RadioGroup v-model:value="form.inHoursPolicy" class="flex flex-col gap-2">
            <Radio value="human_first">
              业务员优先：等待
              <InputNumber v-model:value="form.takeoverMinutes" :min="1" :max="1440" size="small" class="mx-1 w-20" :disabled="!form.enabled || form.inHoursPolicy !== 'human_first'" />
              分钟，仍无人回复再由 AI 接手
            </Radio>
            <Radio value="ai">AI 立即回复</Radio>
            <Radio value="draft">AI 只起草，由业务员确认发送</Radio>
          </RadioGroup>
        </FormItem>
        <FormItem label="非工作时间，新消息怎么处理">
          <RadioGroup v-model:value="form.offHoursPolicy" class="flex flex-col gap-2">
            <Radio value="ai">AI 自动接待：能答的直接回答，需要人工的告诉客户业务员上班后跟进</Radio>
            <Radio value="draft">AI 只起草，等业务员上班后确认发送</Radio>
          </RadioGroup>
        </FormItem>
      </Form>
    </Card>
  </Page>
</template>
