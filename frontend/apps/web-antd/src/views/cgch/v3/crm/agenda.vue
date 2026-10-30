<script lang="ts" setup>
/**
 * 跟进日程：我负责的客户里，今天需要联系的、已经逾期的、快要掉进公海的，以及今天已经联系过的。
 * 每一行可以直接「去会话」接着聊，或者「写跟进」记下这次沟通并约好下次联系时间（写完自动从待办里消失）。
 */
import type { Dayjs } from 'dayjs';

import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { getDictOptions } from '@vben/hooks';
import { DICT_TYPE } from '@vben/constants';

import { Button, DatePicker, message, Modal, Segmented, Select, Space, Table, Tag, Textarea } from 'ant-design-vue';
import dayjs from 'dayjs';

import { getWaConversationPage } from '#/api/channel/whatsapp';
import { requestClient } from '#/api/request';
import { fmtDateTime } from '#/utils/datetime';

import { stageLabel } from './components/shared';

type TabKey = 'done' | 'expired' | 'pool' | 'today';
const TABS: { key: TabKey; label: string; empty: string }[] = [
  { key: 'today', label: '今天需联系', empty: '今天没有约好要联系的客户' },
  { key: 'expired', label: '已逾期', empty: '没有逾期未联系的客户' },
  { key: 'pool', label: '即将进入公海', empty: '没有快要掉进公海的客户' },
  { key: 'done', label: '今天已联系', empty: '今天还没有联系过客户' },
];
const STATUS: Record<Exclude<TabKey, 'pool'>, number> = { today: 1, expired: 2, done: 3 };

const router = useRouter();
const tab = ref<TabKey>('today');
const rows = ref<any[]>([]);
const total = ref(0);
const counts = reactive<Record<TabKey, number>>({ today: 0, expired: 0, pool: 0, done: 0 });
const loading = ref(false);
const pager = reactive({ no: 1, size: 20 });

function fetchTab(key: TabKey, pageNo: number, pageSize: number) {
  if (key === 'pool') {
    return requestClient.get<{ list: any[]; total: number }>('/crm/customer/put-pool-remind-page', {
      params: { pageNo, pageSize, sceneType: 1 },
    });
  }
  return requestClient.get<{ list: any[]; total: number }>('/crm/customer/page', {
    params: { pageNo, pageSize, sceneType: 1, contactStatus: STATUS[key] },
  });
}

async function load() {
  loading.value = true;
  try {
    const r = await fetchTab(tab.value, pager.no, pager.size);
    rows.value = r?.list ?? [];
    total.value = r?.total ?? 0;
    counts[tab.value] = total.value;
  } finally {
    loading.value = false;
  }
}

async function loadCounts() {
  await Promise.all(
    TABS.map(async (t) => {
      try {
        counts[t.key] = (await fetchTab(t.key, 1, 1))?.total ?? 0;
      } catch {
        counts[t.key] = 0;
      }
    }),
  );
}

const tabOptions = computed(() => TABS.map((t) => ({ label: `${t.label} ${counts[t.key] || ''}`.trim(), value: t.key })));
const emptyText = computed(() => TABS.find((t) => t.key === tab.value)!.empty);

const columns = computed(() => [
  { title: '客户', key: 'name', width: 200 },
  { title: '阶段', key: 'stage', width: 90 },
  { title: tab.value === 'pool' ? '距离进入公海' : '下次联系时间', key: 'next', width: 150 },
  { title: '最近跟进', key: 'last' },
  { title: '最近联系', key: 'lastTime', width: 130 },
  { title: '操作', key: 'action', width: 150 },
]);

function overdueDays(r: any) {
  if (!r.contactNextTime) return 0;
  return Math.max(0, dayjs().startOf('day').diff(dayjs(r.contactNextTime).startOf('day'), 'day'));
}

function detail(r: any) {
  router.push({ name: 'CrmCustomerDetail', params: { id: r.id } });
}

/** 去会话：打开和这个客户最近的一个即时消息会话 */
async function chat(r: any) {
  const res = await getWaConversationPage({ pageNo: 1, pageSize: 1, customerId: r.id });
  const c = res.list[0];
  if (!c) {
    message.info('这个客户还没有关联的会话，可以在客户详情里查看邮件往来');
    return;
  }
  router.push({ path: '/v3/conversation', query: { channel: 'whatsapp', ref: String(c.id) } });
}

// ===== 写跟进 =====
const followTypes = computed(() => getDictOptions(DICT_TYPE.CRM_FOLLOW_UP_TYPE, 'number'));
const open = ref(false);
const saving = ref(false);
const target = ref<any>();
const form = reactive<{ content: string; nextTime?: Dayjs; type?: number }>({ content: '', nextTime: undefined, type: undefined });
const QUICK = [
  { label: '明天', days: 1 },
  { label: '3 天后', days: 3 },
  { label: '一周后', days: 7 },
  { label: '两周后', days: 14 },
];
function openFollow(r: any) {
  target.value = r;
  form.content = '';
  form.type = followTypes.value.find((o) => o.label === 'WhatsApp')?.value ?? followTypes.value[0]?.value;
  form.nextTime = dayjs().add(3, 'day').hour(10).minute(0).second(0);
  open.value = true;
}
async function saveFollow() {
  if (!form.content.trim()) {
    message.warning('请填写跟进内容');
    return;
  }
  if (!form.type || !form.nextTime) {
    message.warning('请选择跟进方式和下次联系时间');
    return;
  }
  saving.value = true;
  try {
    await requestClient.post('/crm/follow-up-record/create', {
      bizType: 2,
      bizId: target.value.id,
      type: form.type,
      content: form.content.trim(),
      nextTime: form.nextTime.valueOf(),
      businessIds: [],
      contactIds: [],
      picUrls: [],
      fileUrls: [],
    });
    message.success(`已记录，下次联系：${form.nextTime.format('MM-DD HH:mm')}`);
    open.value = false;
    await Promise.all([load(), loadCounts()]);
  } finally {
    saving.value = false;
  }
}

function switchTab(v: any) {
  tab.value = v;
  pager.no = 1;
  load();
}

onMounted(() => {
  loadCounts();
  load();
});
</script>

<template>
  <div class="rounded-xl bg-white p-5">
    <div class="mb-1 text-lg font-semibold">跟进日程</div>
    <div class="mb-4 text-sm text-gray-500">我负责的客户里需要联系的。写跟进时约好下次联系时间，到期会出现在「今天需联系」，过期没联系会出现在「已逾期」。</div>
    <Segmented :value="tab" :options="tabOptions" class="mb-3" @change="switchTab" />
    <Table
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      row-key="id"
      size="middle"
      :pagination="{ current: pager.no, pageSize: pager.size, total, showSizeChanger: false, onChange: (p: number) => { pager.no = p; load(); } }"
    >
      <template #bodyCell="{ column, record }">
        <a v-if="column.key === 'name'" class="font-medium" @click="detail(record)">{{ record.name }}</a>
        <template v-else-if="column.key === 'stage'">
          <Tag v-if="record.stage" class="m-0">{{ stageLabel(record.stage) }}</Tag>
          <span v-else class="text-gray-300">-</span>
        </template>
        <template v-else-if="column.key === 'next'">
          <template v-if="tab === 'pool'">
            <span class="text-orange-500">{{ record.poolDay != null ? `还剩 ${record.poolDay} 天` : '即将进入' }}</span>
          </template>
          <template v-else>
            {{ fmtDateTime(record.contactNextTime, 'MM-DD HH:mm', '-') }}
            <span v-if="tab === 'expired' && overdueDays(record)" class="ml-1 text-xs text-red-500">逾期 {{ overdueDays(record) }} 天</span>
          </template>
        </template>
        <span v-else-if="column.key === 'last'" class="line-clamp-2 text-gray-600">{{ record.contactLastContent || '-' }}</span>
        <template v-else-if="column.key === 'lastTime'">{{ fmtDateTime(record.contactLastTime, 'MM-DD HH:mm', '-') }}</template>
        <Space v-else-if="column.key === 'action'" :size="12">
          <a @click="chat(record)">去会话</a>
          <a @click="openFollow(record)">写跟进</a>
        </Space>
      </template>
      <template #emptyText>{{ emptyText }}</template>
    </Table>

    <Modal v-model:open="open" :title="`写跟进 · ${target?.name || ''}`" ok-text="保存" :confirm-loading="saving" :width="520" @ok="saveFollow">
      <div class="flex flex-col gap-3">
        <div class="flex items-center gap-2 text-sm">
          <span class="w-20 shrink-0 text-gray-500">跟进方式</span>
          <Select v-model:value="form.type" class="w-40" :options="followTypes.map((o) => ({ label: o.label, value: o.value }))" />
        </div>
        <Textarea v-model:value="form.content" :auto-size="{ minRows: 4, maxRows: 8 }" :maxlength="2000" placeholder="这次聊了什么、客户的反馈、下一步要做什么" />
        <div class="flex flex-wrap items-center gap-2 text-sm">
          <span class="w-20 shrink-0 text-gray-500">下次联系</span>
          <DatePicker v-model:value="form.nextTime" show-time format="YYYY-MM-DD HH:mm" :allow-clear="false" class="w-48" />
          <Button v-for="q in QUICK" :key="q.days" size="small" @click="form.nextTime = dayjs().add(q.days, 'day').hour(10).minute(0).second(0)">{{ q.label }}</Button>
        </div>
      </div>
    </Modal>
  </div>
</template>
