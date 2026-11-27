<script lang="ts" setup>
/**
 * 🧾 外贸成交订单与 PI 台账面板（Apple Bento KPI + 可编辑闭环台账）：
 * - 展示累计成交笔数、折合美金总签约额、本月新签笔数
 * - 支持按 PI / 订单号检索、点击买家直达 360° 全景抽屉、行内一键「编辑修改金额/交期备注」与「删除误录单」
 */
import { computed, onMounted, reactive, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Empty, Input, message, Popconfirm, Spin, Table } from 'ant-design-vue';
import dayjs from 'dayjs';

import {
  deleteContract,
  exportContract,
  getContractPage,
} from '#/api/crm/contract';
import { crmAvatarBg, crmInitial } from '#/views/cgch/v3/crm/components/shared';

import { formatDealMoney, toUsdAmount } from './deal-meta';

const emit = defineEmits<{
  'create-order': [];
  'edit-order': [order: Record<string, any>];
  'inspect-customer': [customerId: number];
}>();

const loading = ref(false);
const rows = ref<any[]>([]);
const total = ref(0);
const keyword = ref('');
const pager = reactive({ no: 1, size: 20 });

function detectCurrency(remark?: string): string {
  return remark?.match(/\[币种:([A-Z]{3})\]/)?.[1] || 'USD';
}

function cleanOrderRemark(remark?: string): string {
  return (remark || '').replace(/\[币种:[A-Z]{3}\]\s*\|?\s*/, '').trim();
}

const pageUsdTotal = computed(() =>
  rows.value.reduce(
    (sum, r) => sum + toUsdAmount(r.totalPrice, detectCurrency(r.remark)),
    0,
  ),
);

const thisMonthCount = computed(() => {
  const m = dayjs().format('YYYY-MM');
  return rows.value.filter(
    (r) => r.orderDate && dayjs(r.orderDate).format('YYYY-MM') === m,
  ).length;
});

const columns = [
  { title: 'PI 编号 / 订单摘要', key: 'name' },
  { title: '成交海外买家', key: 'customer', width: 220 },
  { title: '签约金额', key: 'amount', width: 150, align: 'right' as const },
  { title: '付款条款与交期进度', key: 'remark', width: 270 },
  { title: '签单日期', key: 'orderDate', width: 120 },
  { title: '负责人', key: 'owner', width: 100 },
  { title: '操作', key: 'action', width: 120, align: 'right' as const },
];

async function loadOrders() {
  loading.value = true;
  try {
    const res: any = await getContractPage({
      pageNo: pager.no,
      pageSize: pager.size,
      name: keyword.value.trim() || undefined,
    } as any);
    rows.value = res?.list ?? [];
    total.value = res?.total ?? 0;
  } finally {
    loading.value = false;
  }
}

async function handleDelete(row: any) {
  await deleteContract(row.id);
  message.success('已删除该订单记录');
  await loadOrders();
}

async function handleExport() {
  await exportContract({ name: keyword.value.trim() || undefined });
}

onMounted(() => {
  void loadOrders();
});

defineExpose({ reload: loadOrders, exportExcel: handleExport });
</script>

<template>
  <div class="op-wrap">
    <!-- Bento KPI 指标卡 -->
    <div class="grid grid-cols-3 gap-3">
      <div class="op-kpi">
        <span class="op-kpi-icon is-blue">
          <IconifyIcon icon="lucide:file-check-2" class="size-4.5" />
        </span>
        <div>
          <span class="op-kpi-lbl">累计成交订单</span>
          <strong class="op-kpi-val">{{ total }} <small>笔</small></strong>
        </div>
      </div>

      <div class="op-kpi">
        <span class="op-kpi-icon is-green">
          <IconifyIcon icon="lucide:badge-dollar-sign" class="size-4.5" />
        </span>
        <div>
          <span class="op-kpi-lbl">当前列表折合美金总额</span>
          <strong class="op-kpi-val text-[#1E7E34]">
            {{ formatDealMoney(pageUsdTotal, 'USD') || '$0' }}
          </strong>
        </div>
      </div>

      <div class="op-kpi">
        <span class="op-kpi-icon is-purple">
          <IconifyIcon icon="lucide:trending-up" class="size-4.5" />
        </span>
        <div>
          <span class="op-kpi-lbl">本月新签订单</span>
          <strong class="op-kpi-val text-[#5856D6]">
            {{ thisMonthCount }} <small>笔</small>
          </strong>
        </div>
      </div>
    </div>

    <!-- 检索栏 -->
    <div class="flex flex-wrap items-center gap-2">
      <Input
        v-model:value="keyword"
        allow-clear
        placeholder="搜索 PI 编号、订单摘要…"
        class="w-72"
        @press-enter="() => { pager.no = 1; loadOrders(); }"
      >
        <template #prefix>
          <IconifyIcon icon="lucide:search" class="size-3.5 text-[#86868B]" />
        </template>
      </Input>
      <button
        type="button"
        class="op-btn"
        @click="() => { pager.no = 1; loadOrders(); }"
      >
        查询
      </button>
      <button type="button" class="op-btn ml-auto" @click="handleExport">
        <IconifyIcon icon="lucide:download" class="size-3.5" />
        <span>导出订单 Excel</span>
      </button>
    </div>

    <!-- 台账表格 -->
    <div class="op-card">
      <Spin :spinning="loading">
        <Table
          :columns="columns"
          :data-source="rows"
          :row-key="(r: any) => r.id"
          :pagination="{
            current: pager.no,
            pageSize: pager.size,
            total,
            showSizeChanger: false,
            onChange: (p: number) => { pager.no = p; loadOrders(); },
          }"
          size="middle"
        >
          <template #emptyText>
            <Empty
              :image="Empty.PRESENTED_IMAGE_SIMPLE"
              description="暂无成交订单记录，把客户拖入「⑤ 已成交」或点击右上角即可 10 秒登记"
            />
          </template>
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">
              <div class="font-semibold text-[#1D1D1F]">{{ record.name || record.no }}</div>
              <div class="text-[11px] tabular-nums text-[#86868B]">
                单号：{{ record.no || '自动生成' }}
              </div>
            </template>

            <template v-else-if="column.key === 'customer'">
              <button
                v-if="record.customerId"
                type="button"
                class="flex cursor-pointer items-center gap-2 border-none bg-transparent p-0 text-left"
                @click="emit('inspect-customer', record.customerId)"
              >
                <span
                  class="inline-flex size-6 shrink-0 items-center justify-center rounded-md text-[11px] font-bold text-white"
                  :style="{ background: crmAvatarBg(record.customerName || record.customerId) }"
                >
                  {{ crmInitial(record.customerName) }}
                </span>
                <span class="truncate text-[13px] font-medium text-[#0071E3] hover:underline">
                  {{ record.customerName || `客户 #${record.customerId}` }}
                </span>
              </button>
              <span v-else>{{ record.customerName || '—' }}</span>
            </template>

            <template v-else-if="column.key === 'amount'">
              <span class="text-[14px] font-bold tabular-nums text-[#1E7E34]">
                {{ formatDealMoney(record.totalPrice, detectCurrency(record.remark)) || '$0' }}
              </span>
            </template>

            <template v-else-if="column.key === 'remark'">
              <span class="line-clamp-2 text-[12px] text-[#48484A]">
                {{ cleanOrderRemark(record.remark) || '—' }}
              </span>
            </template>

            <template v-else-if="column.key === 'orderDate'">
              <span class="text-[12px] tabular-nums text-[#6E6E73]">
                {{ record.orderDate ? dayjs(record.orderDate).format('YYYY-MM-DD') : '—' }}
              </span>
            </template>

            <template v-else-if="column.key === 'owner'">
              <span class="text-[12px] text-[#3A3A3C]">
                {{ record.ownerUserName || '—' }}
              </span>
            </template>

            <template v-else-if="column.key === 'action'">
              <div class="flex justify-end gap-2">
                <button
                  type="button"
                  class="op-link"
                  @click="emit('edit-order', record)"
                >
                  编辑
                </button>
                <Popconfirm
                  title="确定删除这条订单记录吗？"
                  ok-text="删除"
                  cancel-text="取消"
                  @confirm="handleDelete(record)"
                >
                  <button type="button" class="op-link is-danger">删除</button>
                </Popconfirm>
              </div>
            </template>
          </template>
        </Table>
      </Spin>
    </div>
  </div>
</template>

<style scoped>
.op-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.op-kpi {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
  background: rgb(255 255 255 / 90%);
  border: 0.5px solid rgb(60 60 67 / 10%);
  border-radius: 16px;
  box-shadow: 0 1px 3px rgb(0 0 0 / 3%);
}

.op-kpi-icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
}

.op-kpi-icon.is-blue {
  color: #0071e3;
  background: #e8f2ff;
}

.op-kpi-icon.is-green {
  color: #1e7e34;
  background: #eaf9ee;
}

.op-kpi-icon.is-purple {
  color: #5856d6;
  background: #ededfc;
}

.op-kpi-lbl {
  display: block;
  font-size: 11.5px;
  font-weight: 500;
  color: #86868b;
}

.op-kpi-val {
  display: block;
  margin-top: 1px;
  font-size: 21px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: #1d1d1f;
  font-variant-numeric: tabular-nums;
}

.op-kpi-val small {
  font-size: 12px;
  font-weight: 500;
  color: #86868b;
}

.op-card {
  overflow: hidden;
  background: #fff;
  border: 0.5px solid rgb(60 60 67 / 10%);
  border-radius: 16px;
  box-shadow: 0 1px 3px rgb(0 0 0 / 4%);
}

.op-btn {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 32px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 600;
  color: #3a3a3c;
  cursor: pointer;
  background: #fff;
  border: 0.5px solid rgb(60 60 67 / 14%);
  border-radius: 9px;
  transition: all 0.15s ease;
}

.op-btn:hover {
  background: #f2f2f7;
}

.op-link {
  padding: 2px 6px;
  font-size: 12px;
  font-weight: 600;
  color: #0071e3;
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: 6px;
}

.op-link:hover {
  background: #e8f2ff;
}

.op-link.is-danger {
  color: #86868b;
}

.op-link.is-danger:hover {
  color: #d70015;
  background: #ffeceb;
}
</style>
