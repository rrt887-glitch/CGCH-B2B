<script lang="ts" setup>
/**
 * 客户工作台：把「线索 / 客户 / 商机 / 待办」收敛成 4 个入口，按流程走就行。
 *
 * 客户：一条流程条（新询盘 → 报价 → 样品 → 谈判 → 成交 → 复购）+ 列表，阶段可直接在行内改。
 * 今天：逾期 / 今天要联系 / 快要掉公海 / 今天已联系（只提醒，不自动回收）。
 * 订单：成交后的合同与回款。
 * 设置：标签、分组、公海规则、线索分配。
 */
import { computed, onMounted, reactive, ref } from 'vue';

import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import {
  Button,
  Empty,
  Input,
  message,
  Modal,
  Select,
  Spin,
  Table,
  Tag,
  Upload,
} from 'ant-design-vue';

import { getCluePage, transformClue } from '#/api/crm/clue';
import { getContractPage } from '#/api/crm/contract';
import {
  getCustomerPage,
  getCustomerStageStats,
  importCustomer,
  importCustomerTemplate,
  transferCustomer,
  updateCustomerStage,
} from '#/api/crm/customer';
import { getSimpleUserList } from '#/api/system/user';
import { requestClient } from '#/api/request';

import Settings from '../settings.vue';

defineOptions({ name: 'CgchCrmWorkbench' });

type TabKey = 'customer' | 'lead' | 'order' | 'settings' | 'today';
type TodayKey = 'done' | 'expired' | 'pool' | 'today';

const router = useRouter();

/** 客户阶段：与字典 crm_customer_stage 一致 */
const STAGES = [
  { value: 1, label: '新询盘', color: '#007AFF' },
  { value: 2, label: '报价', color: '#FF9F0A' },
  { value: 3, label: '样品', color: '#AF52DE' },
  { value: 4, label: '谈判', color: '#FF6482' },
  { value: 5, label: '成交', color: '#34C759' },
  { value: 6, label: '复购', color: '#30B0C7' },
];
const stageOptions = STAGES.map((s) => ({ label: s.label, value: s.value }));
const stageMeta = (v?: number) => STAGES.find((s) => s.value === (v || 1)) || STAGES[0]!;

const tab = ref<TabKey>('customer');
const loading = ref(false);
const rows = ref<any[]>([]);
const total = ref(0);
const stats = ref<Record<string, number>>({});
const stage = ref<number | undefined>();
const keyword = ref('');
const pager = reactive({ no: 1, size: 20 });

/** 勾选批量操作 */
const selectedIds = ref<number[]>([]);
const users = ref<{ id?: number; nickname: string }[]>([]);
const transferOpen = ref(false);
const transferTo = ref<number>();
const transferring = ref(false);
const importOpen = ref(false);
const importFile = ref<File>();
const importFileList = ref<any[]>([]);
const importUpdate = ref(false);
const importing = ref(false);

function onSelectChange(keys: any[]) {
  selectedIds.value = (keys || []).map(Number);
}

async function ensureUsers() {
  if (!users.value.length) {
    users.value = ((await getSimpleUserList().catch(() => [])) as any) || [];
  }
}

async function openTransfer() {
  await ensureUsers();
  transferTo.value = undefined;
  transferOpen.value = true;
}

async function doTransfer() {
  if (!transferTo.value) {
    message.warning('请选择新的负责人');
    return;
  }
  transferring.value = true;
  try {
    let ok = 0;
    for (const id of selectedIds.value) {
      try {
        await transferCustomer({ id, newOwnerUserId: transferTo.value } as any);
        ok++;
      } catch {
        // 单个失败不中断，最后统一提示
      }
    }
    message.success(`已转交 ${ok} / ${selectedIds.value.length} 个客户`);
    transferOpen.value = false;
    selectedIds.value = [];
    await loadCustomers();
    await loadStats();
  } finally {
    transferring.value = false;
  }
}

function beforeImportFile(file: File) {
  importFile.value = file;
  importFileList.value = [file as any];
  return false;
}

function pickImportFile(file: File) {
  beforeImportFile(file);
  return false;
}

async function doImport() {
  if (!importFile.value) {
    message.warning('请选择 Excel 文件');
    return;
  }
  importing.value = true;
  try {
    await importCustomer({
      file: importFile.value,
      updateSupport: importUpdate.value,
    } as any);
    message.success('导入完成');
    importOpen.value = false;
    importFile.value = undefined;
    importFileList.value = [];
    pager.no = 1;
    await loadCustomers();
    await loadStats();
  } finally {
    importing.value = false;
  }
}

const tabs = [
  { key: 'lead' as TabKey, label: '线索' },
  { key: 'customer' as TabKey, label: '客户' },
  { key: 'today' as TabKey, label: '今天' },
  { key: 'order' as TabKey, label: '订单' },
  { key: 'settings' as TabKey, label: '设置' },
];

async function loadStats() {
  stats.value = (await getCustomerStageStats().catch(() => ({} as Record<string, number>))) || {};
}

async function loadCustomers() {
  loading.value = true;
  try {
    const r: any = await getCustomerPage({
      pageNo: pager.no,
      pageSize: pager.size,
      sceneType: 1,
      stage: stage.value,
      name: keyword.value || undefined,
    } as any);
    rows.value = r?.list ?? [];
    total.value = r?.total ?? 0;
  } finally {
    loading.value = false;
  }
}

/** 线索：询盘先进线索，确认意向后再转客户 */
const leadRows = ref<any[]>([]);
const leadTotal = ref(0);
const leadLoading = ref(false);
const leadKeyword = ref('');

const SOURCE_LABEL: Record<number, string> = {
  10: '邮件',
  11: 'WhatsApp',
  12: '阿里国际站',
  13: '中国制造网',
  14: '官网表单',
  15: 'Facebook',
  16: 'Instagram',
  17: '官网IM',
  18: 'YouTube',
  19: 'TikTok',
};
const sourceLabel = (v?: number) => (v ? SOURCE_LABEL[v] || '其它' : '—');

async function loadLeads() {
  leadLoading.value = true;
  try {
    const r: any = await getCluePage({
      pageNo: pager.no,
      pageSize: pager.size,
      name: leadKeyword.value || undefined,
    } as any);
    leadRows.value = r?.list ?? [];
    leadTotal.value = r?.total ?? 0;
  } finally {
    leadLoading.value = false;
  }
}

async function convertLead(row: any) {
  await transformClue(row.id);
  message.success(`已把「${row.name}」转为客户`);
  await loadLeads();
  await loadStats();
}

const leadColumns = [
  { title: '线索', dataIndex: 'name', key: 'name' },
  { title: '来源', dataIndex: 'source', key: 'source', width: 120 },
  { title: '国家', dataIndex: 'countryCode', key: 'countryCode', width: 90 },
  { title: '下次跟进', dataIndex: 'contactNextTime', key: 'contactNextTime', width: 130 },
  { title: '负责人', dataIndex: 'ownerUserName', key: 'ownerUserName', width: 110 },
  { title: '', key: 'action', width: 110 },
];

/** 今天：逾期 / 今天要联系 / 快要掉公海 / 今天已联系 */
const todayKey = ref<TodayKey>('expired');
const todayRows = ref<any[]>([]);
const todayTotal = ref(0);
const todayCounts = reactive<Record<TodayKey, number>>({
  expired: 0,
  today: 0,
  pool: 0,
  done: 0,
});
const todayTabs = [
  { key: 'expired' as TodayKey, label: '逾期未联系' },
  { key: 'today' as TodayKey, label: '今天要联系' },
  { key: 'pool' as TodayKey, label: '快要掉公海' },
  { key: 'done' as TodayKey, label: '今天已联系' },
];
const CONTACT_STATUS: Record<Exclude<TodayKey, 'pool'>, number> = {
  expired: 2,
  today: 1,
  done: 3,
};

function fetchToday(key: TodayKey, pageNo = 1, pageSize = 20) {
  if (key === 'pool') {
    return requestClient.get<{ list: any[]; total: number }>(
      '/crm/customer/put-pool-remind-page',
      { params: { pageNo, pageSize, sceneType: 1 } },
    );
  }
  return requestClient.get<{ list: any[]; total: number }>('/crm/customer/page', {
    params: { pageNo, pageSize, sceneType: 1, contactStatus: CONTACT_STATUS[key] },
  });
}

async function loadToday() {
  loading.value = true;
  try {
    const r: any = await fetchToday(todayKey.value, pager.no, pager.size);
    todayRows.value = r?.list ?? [];
    todayTotal.value = r?.total ?? 0;
  } finally {
    loading.value = false;
  }
}

async function loadTodayCounts() {
  await Promise.all(
    todayTabs.map(async (t) => {
      try {
        todayCounts[t.key] = (await fetchToday(t.key, 1, 1))?.total ?? 0;
      } catch {
        todayCounts[t.key] = 0;
      }
    }),
  );
}

/** 订单：合同与回款 */
const orderRows = ref<any[]>([]);
const orderTotal = ref(0);
async function loadOrders() {
  loading.value = true;
  try {
    const r: any = await getContractPage({ pageNo: pager.no, pageSize: pager.size });
    orderRows.value = r?.list ?? [];
    orderTotal.value = r?.total ?? 0;
  } finally {
    loading.value = false;
  }
}

async function changeStage(row: any, v: number) {
  await updateCustomerStage(row.id, v);
  row.stage = v;
  message.success(`已改为「${stageMeta(v).label}」`);
  await loadStats();
}

function openCustomer(row: any) {
  router.push(`/crm/customer/detail/${row.id}`);
}

/** 关系阶段：从客户阶段推导（潜在 / 合作中 / 老客户） */
function relationOf(row: any) {
  const st = row?.stage || 1;
  if (st >= 6) return { label: '老客户', color: '#30B0C7' };
  if (st === 5) return { label: '合作中', color: '#34C759' };
  return { label: '潜在', color: '#8E8E93' };
}

/** 下一步该做什么：让列表直接告诉业务员动作，而不是只有数据 */
function nextAction(row: any): { text: string; tone: 'danger' | 'normal' | 'primary' } {
  const now = Date.now();
  const next = row?.contactNextTime ? new Date(row.contactNextTime).getTime() : null;
  if (next && next < now) return { text: '今天联系（已逾期）', tone: 'danger' };
  if (!next) return { text: '约下次联系', tone: 'normal' };
  const st = row?.stage || 1;
  if (st === 1) return { text: '发报价', tone: 'primary' };
  if (st === 2) return { text: '跟进报价', tone: 'primary' };
  if (st === 3) return { text: '确认样品', tone: 'primary' };
  if (st === 4) return { text: '推动成交', tone: 'primary' };
  if (st === 5) return { text: '安排回款', tone: 'normal' };
  return { text: '维护关系', tone: 'normal' };
}

const emptyOnboarding = computed(
  () => tab.value === 'customer' && total.value === 0 && !keyword.value && !stage.value,
);

function onLeadSearch() {
  pager.no = 1;
  void loadLeads();
}

function onSearch() {
  pager.no = 1;
  void loadCustomers();
}

function pickStage(v?: number) {
  stage.value = v;
  pager.no = 1;
  void loadCustomers();
}

function onTabChange(key: any) {
  tab.value = key as TabKey;
  pager.no = 1;
  if (key === 'lead') {
    void loadLeads();
  } else if (key === 'customer') {
    void loadCustomers();
  } else if (key === 'today') {
    void loadToday();
    void loadTodayCounts();
  } else if (key === 'order') {
    void loadOrders();
  }
}

function onTodayChange(key: any) {
  todayKey.value = key as TodayKey;
  pager.no = 1;
  void loadToday();
}

const columns = [
  { title: '客户', dataIndex: 'name', key: 'name' },
  { title: '阶段', dataIndex: 'stage', key: 'stage', width: 140 },
  { title: '下次跟进', dataIndex: 'contactNextTime', key: 'contactNextTime', width: 140 },
  { title: '最近联系', dataIndex: 'contactLastTime', key: 'contactLastTime', width: 140 },
  { title: '负责人', dataIndex: 'ownerUserName', key: 'ownerUserName', width: 120 },
];

const orderColumns = [
  { title: '合同编号', dataIndex: 'no', key: 'no', width: 180 },
  { title: '客户', dataIndex: 'customerName', key: 'customerName' },
  { title: '金额', dataIndex: 'totalPrice', key: 'totalPrice', width: 140 },
  { title: '状态', dataIndex: 'auditStatus', key: 'auditStatus', width: 120 },
  { title: '签订时间', dataIndex: 'orderDate', key: 'orderDate', width: 140 },
];

const customerColumns = [
  { title: '客户', dataIndex: 'name', key: 'name' },
  { title: '关系', dataIndex: 'relation', key: 'relation', width: 90 },
  { title: '阶段', dataIndex: 'stage', key: 'stage', width: 140 },
  { title: '下次跟进', dataIndex: 'contactNextTime', key: 'contactNextTime', width: 140 },
  { title: '最近联系', dataIndex: 'contactLastTime', key: 'contactLastTime', width: 140 },
  { title: '负责人', dataIndex: 'ownerUserName', key: 'ownerUserName', width: 120 },
  { title: '下一步', dataIndex: 'nextAction', key: 'nextAction', width: 160 },
];

const fmt = (v?: string) => (v ? String(v).slice(0, 10) : '—');
const money = (v?: number) =>
  v == null ? '—' : `¥ ${Number(v).toLocaleString('zh-CN')}`;

onMounted(() => {
  void loadLeads();
  void loadCustomers();
  void loadStats();
});
</script>

<template>
  <div class="crm-wb">
    <div class="crm-wb__nav">
      <button
        v-for="t in tabs"
        :key="t.key"
        type="button"
        class="crm-wb__nav-item"
        :class="{ on: tab === t.key }"
        @click="onTabChange(t.key)"
      >
        {{ t.label }}
      </button>
    </div>

    <!-- ========== 线索 ========== -->
    <template v-if="tab === 'lead'">
      <div class="crm-wb__guide">
        <IconifyIcon icon="lucide:inbox" class="size-4 shrink-0" />
        <span>
          渠道来询盘会先落到这里。确认对方有意向、谈得下去，点右侧「转为客户」；没意向的直接忽略。
        </span>
      </div>

      <div class="crm-wb__toolbar">
        <Input
          v-model:value="leadKeyword"
          allow-clear
          placeholder="搜索线索名称"
          class="max-w-70"
          @press-enter="onLeadSearch"
        />
        <Button @click="onLeadSearch">搜索</Button>
        <span class="text-xs text-gray-400">共 {{ leadTotal }} 条线索</span>
      </div>

      <Spin :spinning="leadLoading">
        <Table
          :columns="leadColumns as any"
          :data-source="leadRows"
          :pagination="{
            current: pager.no,
            pageSize: pager.size,
            total: leadTotal,
            showSizeChanger: false,
            onChange: (p: number) => { pager.no = p; loadLeads(); },
          }"
          :row-key="(r: any) => r.id"
          size="middle"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">
              <div class="font-medium">{{ record.name }}</div>
              <div class="text-xs text-gray-400">
                {{ record.companyName || record.email || record.whatsapp || '—' }}
              </div>
            </template>
            <template v-else-if="column.key === 'source'">
              <Tag>{{ sourceLabel(record.source) }}</Tag>
            </template>
            <template v-else-if="column.key === 'contactNextTime'">
              {{ fmt(record.contactNextTime) }}
            </template>
            <template v-else-if="column.key === 'ownerUserName'">
              {{ record.ownerUserName || '未分配' }}
            </template>
            <template v-else-if="column.key === 'action'">
              <Button size="small" type="primary" @click="convertLead(record)">
                转为客户
              </Button>
            </template>
          </template>
        </Table>
      </Spin>
    </template>

    <!-- ========== 客户 ========== -->
    <template v-else-if="tab === 'customer'">
      <div class="crm-wb__guide">
        <IconifyIcon icon="lucide:compass" class="size-4 shrink-0" />
        <span>
          流程：渠道来的询盘自动建成客户 → 在这里<b>直接改阶段</b>推进 →
          到期没联系的会出现在「今天」提醒你。阶段：新询盘 → 报价 → 样品 → 谈判 → 成交 → 复购。
        </span>
      </div>

      <div class="crm-wb__pipeline">
        <button
          type="button"
          class="crm-wb__stage"
          :class="{ on: stage === undefined }"
          @click="pickStage(undefined)"
        >
          全部
          <b>{{ stats.total ?? total }}</b>
        </button>
        <button
          v-for="s in STAGES"
          :key="s.value"
          type="button"
          class="crm-wb__stage"
          :class="{ on: stage === s.value }"
          @click="pickStage(s.value)"
        >
          <span class="dot" :style="{ background: s.color }"></span>
          {{ s.label }}
          <b>{{ stats[String(s.value)] ?? 0 }}</b>
        </button>
      </div>

      <div class="crm-wb__toolbar">
        <Input
          v-model:value="keyword"
          allow-clear
          placeholder="搜索客户名称"
          class="max-w-70"
          @press-enter="onSearch"
        />
        <Button @click="onSearch">
          <IconifyIcon icon="lucide:search" class="mr-1 inline size-4 align-[-3px]" />
          搜索
        </Button>
        <Button @click="importOpen = true">导入客户</Button>
        <span class="text-xs text-gray-400">共 {{ total }} 个客户</span>
      </div>

      <div v-if="emptyOnboarding" class="crm-wb__empty">
        <Empty description="还没有客户" />
        <div class="crm-wb__steps">
          <div class="crm-wb__step">
            <span class="num">1</span>
            <div>
              <b>接入渠道</b>
              <p>在「渠道」接入 WhatsApp / 邮箱 / LinkedIn，客户发来的消息会自动进「会话」。</p>
              <Button size="small" @click="router.push('/v3/channel')">去接入渠道</Button>
            </div>
          </div>
          <div class="crm-wb__step">
            <span class="num">2</span>
            <div>
              <b>询盘自动成为客户</b>
              <p>新客户第一次发消息时，系统会自动建客户并放到「新询盘」阶段。</p>
            </div>
          </div>
          <div class="crm-wb__step">
            <span class="num">3</span>
            <div>
              <b>改阶段推进</b>
              <p>聊到报价就改成「报价」，寄样了改成「样品」，成交改成「成交」——这就是你的销售进度。</p>
            </div>
          </div>
        </div>
      </div>

      <div v-if="selectedIds.length" class="crm-wb__batch">
        <span>已选 {{ selectedIds.length }} 个客户</span>
        <Button size="small" type="primary" @click="openTransfer">批量转交负责人</Button>
        <Button size="small" @click="onSelectChange([])">取消选择</Button>
      </div>

      <Spin v-else :spinning="loading">
        <Table
          :columns="customerColumns as any"
          :data-source="rows"
          :pagination="{
            current: pager.no,
            pageSize: pager.size,
            total,
            showSizeChanger: false,
            onChange: (p: number) => { pager.no = p; loadCustomers(); },
          }"
          :row-key="(r: any) => r.id"
          :row-selection="{
            selectedRowKeys: selectedIds,
            onChange: onSelectChange,
          }"
          size="middle"
          :custom-row="(r: any) => ({ onClick: () => openCustomer(r), style: 'cursor:pointer' })"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">
              <div class="font-medium">{{ record.name }}</div>
              <div class="text-xs text-gray-400">
                {{ record.countryCode || record.areaName || '—' }}
              </div>
            </template>
            <template v-else-if="column.key === 'stage'">
              <Select
                size="small"
                class="w-full"
                :value="record.stage || 1"
                :options="stageOptions"
                @click.stop
                @change="(v: any) => changeStage(record, v)"
              />
            </template>
            <template v-else-if="column.key === 'contactNextTime'">
              <span
                :class="{
                  'text-red-500 font-medium':
                    record.contactNextTime && new Date(record.contactNextTime) < new Date(),
                }"
              >
                {{ fmt(record.contactNextTime) }}
              </span>
            </template>
            <template v-else-if="column.key === 'contactLastTime'">
              {{ fmt(record.contactLastTime) }}
            </template>
            <template v-else-if="column.key === 'ownerUserName'">
              {{ record.ownerUserName || '未分配' }}
            </template>
            <template v-else-if="column.key === 'relation'">
              <Tag :color="relationOf(record).color">{{ relationOf(record).label }}</Tag>
            </template>
            <template v-else-if="column.key === 'nextAction'">
              <Tag :color="nextAction(record).tone === 'danger' ? 'red' : nextAction(record).tone === 'primary' ? 'blue' : 'default'">
                {{ nextAction(record).text }}
              </Tag>
            </template>
          </template>
        </Table>
      </Spin>
    </template>

    <!-- ========== 今天 ========== -->
    <template v-else-if="tab === 'today'">
      <div class="crm-wb__pipeline">
        <button
          v-for="tt in todayTabs"
          :key="tt.key"
          type="button"
          class="crm-wb__stage"
          :class="{ on: todayKey === tt.key }"
          @click="onTodayChange(tt.key)"
        >
          {{ tt.label }}
          <b>{{ todayCounts[tt.key] }}</b>
        </button>
      </div>

      <Spin :spinning="loading">
        <Table
          :columns="columns as any"
          :data-source="todayRows"
          :pagination="{
            current: pager.no,
            pageSize: pager.size,
            total: todayTotal,
            showSizeChanger: false,
            onChange: (p: number) => { pager.no = p; loadToday(); },
          }"
          :row-key="(r: any) => r.id"
          size="middle"
          :custom-row="(r: any) => ({ onClick: () => openCustomer(r), style: 'cursor:pointer' })"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">
              <div class="font-medium">{{ record.name }}</div>
            </template>
            <template v-else-if="column.key === 'stage'">
              <Tag :color="stageMeta(record.stage).color">
                {{ stageMeta(record.stage).label }}
              </Tag>
            </template>
            <template v-else-if="column.key === 'contactNextTime'">
              {{ fmt(record.contactNextTime) }}
            </template>
            <template v-else-if="column.key === 'contactLastTime'">
              {{ fmt(record.contactLastTime) }}
            </template>
            <template v-else-if="column.key === 'ownerUserName'">
              {{ record.ownerUserName || '未分配' }}
            </template>
          </template>
        </Table>
      </Spin>
    </template>

    <!-- ========== 订单 ========== -->
    <template v-else-if="tab === 'order'">
      <div class="crm-wb__guide">
        <IconifyIcon icon="lucide:receipt" class="size-4 shrink-0" />
        <span>客户阶段改成「成交」后，在这里看合同与回款进度。</span>
      </div>
      <Spin :spinning="loading">
        <Table
          :columns="orderColumns as any"
          :data-source="orderRows"
          :pagination="{
            current: pager.no,
            pageSize: pager.size,
            total: orderTotal,
            showSizeChanger: false,
            onChange: (p: number) => { pager.no = p; loadOrders(); },
          }"
          :row-key="(r: any) => r.id"
          size="middle"
          :custom-row="(r: any) => ({
            onClick: () => router.push(`/crm/contract/detail/${r.id}`),
            style: 'cursor:pointer',
          })"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'totalPrice'">
              {{ money(record.totalPrice) }}
            </template>
            <template v-else-if="column.key === 'auditStatus'">
              <Tag>{{ record.auditStatus }}</Tag>
            </template>
            <template v-else-if="column.key === 'orderDate'">
              {{ fmt(record.orderDate) }}
            </template>
          </template>
        </Table>
      </Spin>
    </template>

    <Modal
      v-model:open="transferOpen"
      title="批量转交负责人"
      :width="440"
      centered
      :confirm-loading="transferring"
      ok-text="转交"
      @ok="doTransfer"
    >
      <p class="mb-3 text-sm text-gray-500">
        选中 {{ selectedIds.length }} 个客户，全部分配给下面的同事。
      </p>
      <Select
        v-model:value="transferTo"
        class="w-full"
        placeholder="选择新的负责人"
        :options="users.map((u) => ({ label: u.nickname, value: u.id }))"
        option-filter-prop="label"
        show-search
      />
    </Modal>

    <Modal
      v-model:open="importOpen"
      title="导入客户"
      :width="480"
      centered
      :confirm-loading="importing"
      ok-text="开始导入"
      @ok="doImport"
    >
      <div class="mb-3 text-sm text-gray-500">
        支持 Excel（.xlsx）。先下载模板，按模板填写后再上传。
        <Button type="link" size="small" @click="importCustomerTemplate">
          下载模板
        </Button>
      </div>
      <Upload
        :file-list="importFileList"
        :before-upload="pickImportFile"
        :max-count="1"
        accept=".xlsx,.xls"
      >
        <Button>选择文件</Button>
      </Upload>
      <div class="mt-3">
        <label class="flex items-center gap-2 text-sm text-gray-600">
          <input v-model="importUpdate" type="checkbox" />
          已存在的客户要更新（不勾选则跳过重复）
        </label>
      </div>
    </Modal>

    <!-- ========== 设置 ========== -->
    <template v-else>
      <Settings />
    </template>
  </div>
</template>

<style scoped>
.crm-wb {
  height: 100%;
  overflow: auto;
  padding: 4px 4px 24px;
}
.crm-wb__nav {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  margin-bottom: 14px;
  background: rgb(118 118 128 / 8%);
  border-radius: 12px;
}
.crm-wb__nav-item {
  padding: 6px 18px;
  font-size: 13.5px;
  color: #4b5563;
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: 9px;
  transition: all 0.15s;
}
.crm-wb__nav-item.on {
  color: #1d1d1f;
  background: #fff;
  box-shadow: 0 1px 3px rgb(0 0 0 / 8%);
}
.crm-wb__guide {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 10px 14px;
  margin-bottom: 12px;
  font-size: 13px;
  line-height: 1.6;
  color: #4b5563;
  background: rgb(0 122 255 / 6%);
  border-radius: 12px;
}
.crm-wb__pipeline {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.crm-wb__stage {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 6px 14px;
  font-size: 13px;
  color: #1f2937;
  cursor: pointer;
  background: #fff;
  border: 1px solid rgb(0 0 0 / 8%);
  border-radius: 999px;
  transition: all 0.15s;
}
.crm-wb__stage:hover {
  border-color: rgb(0 122 255 / 40%);
}
.crm-wb__stage.on {
  color: #fff;
  background: #1d1d1f;
  border-color: #1d1d1f;
}
.crm-wb__stage .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}
.crm-wb__stage b {
  font-weight: 600;
}
.crm-wb__batch {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 8px 12px;
  margin-bottom: 8px;
  font-size: 13px;
  color: #1d1d1f;
  background: rgb(0 122 255 / 8%);
  border-radius: 10px;
}
.crm-wb__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
}
.crm-wb__empty {
  padding: 24px 0;
}
.crm-wb__steps {
  display: grid;
  gap: 12px;
  max-width: 680px;
  margin: 16px auto 0;
}
.crm-wb__step {
  display: flex;
  gap: 12px;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid rgb(0 0 0 / 6%);
  border-radius: 14px;
}
.crm-wb__step .num {
  display: flex;
  flex: 0 0 24px;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: #007aff;
  border-radius: 50%;
}
.crm-wb__step p {
  margin: 4px 0 8px;
  font-size: 12.5px;
  color: #6e6e73;
}
</style>
