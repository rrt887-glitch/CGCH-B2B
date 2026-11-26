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
import { IconifyIcon } from '@vben/icons';

import { DatePicker, message, Modal, Select, Table, Textarea, Tooltip } from 'ant-design-vue';
import dayjs from 'dayjs';

import { getWaConversationPage } from '#/api/channel/whatsapp';
import { requestClient } from '#/api/request';
import { fmtDateTime } from '#/utils/datetime';

import { crmAvatarBg, crmInitial, stageLabel } from './components/shared';

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
const loadFailed = ref(false);
let loadVersion = 0;
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
  const version = ++loadVersion;
  const requestedTab = tab.value;
  loading.value = true;
  loadFailed.value = false;
  try {
    const r = await fetchTab(requestedTab, pager.no, pager.size);
    if (version !== loadVersion) return;
    rows.value = r?.list ?? [];
    total.value = r?.total ?? 0;
    counts[requestedTab] = total.value;
  } catch {
    if (version !== loadVersion) return;
    rows.value = [];
    total.value = 0;
    loadFailed.value = true;
  } finally {
    if (version === loadVersion) loading.value = false;
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

const emptyText = computed(() =>
  loadFailed.value ? '加载失败，请刷新重试' : TABS.find((t) => t.key === tab.value)!.empty,
);

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
  <div class="agenda-page">
    <div class="agenda-head">
      <div>
        <h2 class="agenda-title">跟进日程</h2>
        <p class="agenda-sub">
          集中查看约定今日联系、已逾期未联系、即将掉入公海及今日已完成的客户跟进。
        </p>
      </div>
      <Tooltip title="刷新日程">
        <button
          type="button"
          class="agenda-refresh"
          aria-label="刷新日程"
          @click="
            loadCounts();
            load();
          "
        >
          <IconifyIcon
            icon="lucide:refresh-cw"
            class="size-4"
            :class="{ 'animate-spin': loading }"
          />
        </button>
      </Tooltip>
    </div>

    <div class="agenda-card">
      <div class="agenda-tabs-bar">
        <div class="agenda-seg" role="tablist" aria-label="日程分组">
          <button
            v-for="t in TABS"
            :key="t.key"
            type="button"
            role="tab"
            :aria-selected="tab === t.key"
            class="agenda-seg__btn"
            :class="{ on: tab === t.key }"
            @click="switchTab(t.key)"
          >
            <span>{{ t.label }}</span>
            <span
              v-if="counts[t.key]"
              class="agenda-seg__badge tabular-nums"
              :class="{
                'is-danger': t.key === 'expired',
                'is-warn': t.key === 'pool',
                'is-done': t.key === 'done',
              }"
            >
              {{ counts[t.key] }}
            </span>
          </button>
        </div>
      </div>

      <Table
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        row-key="id"
        size="middle"
        :pagination="{
          current: pager.no,
          pageSize: pager.size,
          total,
          showSizeChanger: false,
          onChange: (p: number) => {
            pager.no = p;
            load();
          },
        }"
      >
        <template #bodyCell="{ column, record }">
          <div v-if="column.key === 'name'" class="flex min-w-0 items-center gap-2">
            <span
              class="ap-crm-avatar"
              :style="{ background: crmAvatarBg(record.name || record.id) }"
              aria-hidden="true"
              >{{ crmInitial(record.name) }}</span
            >
            <a
              class="min-w-0 truncate font-semibold text-[#1D1D1F] hover:text-[#007AFF]"
              @click="detail(record)"
              >{{ record.name }}</a
            >
          </div>
          <template v-else-if="column.key === 'stage'">
            <span v-if="record.stage" class="ap-crm-pill ap-crm-pill--blue">
              {{ stageLabel(record.stage) }}
            </span>
            <span v-else class="text-[#C7C7CC]">-</span>
          </template>
          <template v-else-if="column.key === 'next'">
            <template v-if="tab === 'pool'">
              <span class="ap-crm-pill ap-crm-pill--amber">
                {{ record.poolDay != null ? `剩余 ${record.poolDay} 天` : '即将进入' }}
              </span>
            </template>
            <template v-else>
              <span class="tabular-nums text-[#3A3A3C]">
                {{ fmtDateTime(record.contactNextTime, 'MM-DD HH:mm', '-') }}
              </span>
              <span
                v-if="tab === 'expired' && overdueDays(record)"
                class="ap-crm-pill ap-crm-pill--red ml-1.5"
              >
                逾期 {{ overdueDays(record) }} 天
              </span>
            </template>
          </template>
          <span
            v-else-if="column.key === 'last'"
            class="line-clamp-2 text-[13px] text-[#6E6E73]"
            >{{ record.contactLastContent || '-' }}</span
          >
          <template v-else-if="column.key === 'lastTime'">
            <span class="tabular-nums text-[#6E6E73]">
              {{ fmtDateTime(record.contactLastTime, 'MM-DD HH:mm', '-') }}
            </span>
          </template>
          <div v-else-if="column.key === 'action'" class="flex items-center gap-2">
            <button
              type="button"
              class="agenda-act-btn agenda-act-btn--soft"
              @click="chat(record)"
            >
              去会话
            </button>
            <button
              type="button"
              class="agenda-act-btn agenda-act-btn--primary"
              @click="openFollow(record)"
            >
              写跟进
            </button>
          </div>
        </template>
        <template #emptyText>
          <div class="flex flex-col items-center justify-center gap-2 py-12 text-center">
            <div class="grid size-11 place-items-center rounded-2xl bg-[#F4F5F8] text-[#8E8E93]">
              <IconifyIcon icon="lucide:calendar-check-2" class="size-5" />
            </div>
            <div class="text-[13px] font-medium text-[#86868B]">{{ emptyText }}</div>
          </div>
        </template>
      </Table>
    </div>

    <Modal
      v-model:open="open"
      :title="`写跟进 · ${target?.name || ''}`"
      ok-text="保存跟进"
      :confirm-loading="saving"
      :width="500"
      wrap-class-name="ap-modal-sheet"
      @ok="saveFollow"
    >
      <div class="ap-sheet-lead">
        <span class="ap-sheet-icon ap-sheet-icon--purple">
          <IconifyIcon icon="lucide:notebook-pen" class="size-5" />
        </span>
        <div class="ap-sheet-subtitle">
          记录本次沟通进展并预约下次联系时间，保存后自动从待办提醒中核销。
        </div>
      </div>
      <div class="ap-sheet-body">
        <div class="ap-sheet-field">
          <label class="ap-sheet-label">跟进方式</label>
          <Select
            v-model:value="form.type"
            class="w-full"
            :options="followTypes.map((o) => ({ label: o.label, value: o.value }))"
          />
        </div>
        <div class="ap-sheet-field">
          <label class="ap-sheet-label">沟通要点与反馈</label>
          <Textarea
            v-model:value="form.content"
            :auto-size="{ minRows: 4, maxRows: 8 }"
            :maxlength="2000"
            placeholder="这次聊了什么、客户的反馈、下一步要做什么…"
          />
        </div>
        <div class="ap-sheet-field">
          <label class="ap-sheet-label">下次联系时间</label>
          <div class="flex flex-wrap items-center gap-2">
            <DatePicker
              v-model:value="form.nextTime"
              show-time
              format="YYYY-MM-DD HH:mm"
              :allow-clear="false"
              class="min-w-[200px] flex-1"
            />
            <div class="flex flex-wrap gap-1.5">
              <button
                v-for="q in QUICK"
                :key="q.days"
                type="button"
                class="agenda-quick-chip"
                @click="
                  form.nextTime = dayjs()
                    .add(q.days, 'day')
                    .hour(10)
                    .minute(0)
                    .second(0)
                "
              >
                {{ q.label }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.agenda-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 20px 24px 16px;
}

.agenda-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
}

.agenda-title {
  margin: 0;
  font-size: 21px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #1d1d1f;
}

.agenda-sub {
  margin: 3px 0 0;
  font-size: 13px;
  color: #6e6e73;
}

.agenda-refresh {
  display: grid;
  flex: none;
  place-items: center;
  width: 32px;
  height: 32px;
  color: #6e6e73;
  cursor: pointer;
  background: rgb(255 255 255 / 86%);
  border: 0;
  border-radius: 50%;
  box-shadow:
    0 1px 2px rgb(0 0 0 / 5%),
    inset 0 0 0 0.5px rgb(0 0 0 / 10%);
}

.agenda-refresh:hover {
  color: #1d1d1f;
  background: #fff;
}

.agenda-card {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: var(--ap-surface);
  border-radius: 18px;
  box-shadow: var(--ap-shadow-rest);
}

.agenda-tabs-bar {
  padding: 12px 16px;
  border-bottom: 0.5px solid var(--ap-separator);
}

.agenda-seg {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 3px;
  padding: 3px;
  background: #f2f2f7;
  border-radius: 10px;
}

.agenda-seg__btn {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  height: 30px;
  padding: 0 12px;
  font-size: 12.5px;
  font-weight: 500;
  color: #6e6e73;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 8px;
  transition: all var(--ap-dur-fast) var(--ap-ease);
}

.agenda-seg__btn.on {
  font-weight: 600;
  color: #1d1d1f;
  background: #fff;
  box-shadow:
    0 1px 3px rgb(0 0 0 / 8%),
    0 0 0 0.5px rgb(0 0 0 / 4%);
}

.agenda-seg__badge {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  font-size: 11px;
  font-weight: 700;
  line-height: 18px;
  color: #007aff;
  background: #e8f2ff;
  border-radius: 999px;
}

.agenda-seg__badge.is-danger {
  color: #d70015;
  background: #ffeceb;
}

.agenda-seg__badge.is-warn {
  color: #b25e00;
  background: #fff4e0;
}

.agenda-seg__badge.is-done {
  color: #1e8e3e;
  background: #eaf9ee;
}

.agenda-act-btn {
  height: 26px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  border: 0;
  border-radius: 999px;
  transition: all var(--ap-dur-fast);
}

.agenda-act-btn--soft {
  color: #3a3a3c;
  background: #f2f2f7;
}

.agenda-act-btn--soft:hover {
  color: #1d1d1f;
  background: #e5e5ea;
}

.agenda-act-btn--primary {
  color: #007aff;
  background: #e8f2ff;
}

.agenda-act-btn--primary:hover {
  background: #d8e9ff;
}

.agenda-quick-chip {
  height: 30px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 500;
  color: #3a3a3c;
  cursor: pointer;
  background: #f2f2f7;
  border: 0;
  border-radius: 999px;
  transition: all var(--ap-dur-fast);
}

.agenda-quick-chip:hover {
  color: #007aff;
  background: #e8f2ff;
}
</style>
