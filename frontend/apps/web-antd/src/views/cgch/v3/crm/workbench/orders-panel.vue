<script lang="ts" setup>
/**
 * 🧾 外贸成交订单与 PI 台账面板（Foreign-Trade Order Ledger）：
 * - 汇总展示所有已登记的 PI / 大货订单、折合美金总签约额与本月签约趋势
 * - 支持按订单号/客户搜索，点击客户直接滑出 360° 客户全景抽屉，右上角一键极简登记新订单
 */
import { computed, onMounted, reactive, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Empty, Input, Spin, Table } from 'ant-design-vue';
import dayjs from 'dayjs';

import { exportContract, getContractPage } from '#/api/crm/contract';

import { formatDealMoney, toUsdAmount } from './deal-meta';

const emit = defineEmits<{
  'create-order': [];
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
  { title: 'PI / 订单名称', key: 'name' },
  { title: '成交买家', key: 'customer', width: 200 },
  { title: '订单金额', key: 'amount', width: 155, align: 'right' as const },
  { title: '付款条款与备注', key: 'remark', width: 260 },
  { title: '签单日期', key: 'orderDate', width: 120 },
  { title: '负责人', key: 'owner', width: 110 },
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

async function handleExport() {
  await exportContract({ name: keyword.value.trim() || undefined });
}

onMounted(() => {
  void loadOrders();
});

defineExpose({ reload: loadOrders });
</script>

<template>
  <div class="op-wrap">
    <!-- 顶部关键指标摘要卡 -->
    <div class="grid grid-cols-3 gap-3">
      <div class="op-kpi">
        <span class="op-kpi-lbl">累计成交订单笔数</span>
        <strong class="op-kpi-val">{{ total }} <span>笔</span></strong>
      </div>
      <div class="op-kpi">
        <span class="op-kpi-lbl">当前页折合美金总额</span>
        <strong class="op-kpi-val text-[#248A3D]">
          {{ formatDealMoney(pageUsdTotal, 'USD') || '$0' }}
        </strong>
      </div>
      <div class="op-kpi">
        <span class="op-kpi-lbl">本月新签订单</span>
        <strong class="op-kpi-val text-[#0071E3]">{{ thisMonthCount }} <span>笔</span></strong>
      </div>
    </div>

    <!-- 工具条 -->
    <div class="flex flex-wrap items-center gap-2.5">
      <Input
        v-model:value="keyword"
        allow-clear
        placeholder="搜索 PI 编号或订单名称…"
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
      <button type="button" class="op-btn" @click="handleExport">
        <IconifyIcon icon="lucide:download" class="size-3.5" />
        <span>导出 Excel</span>
      </button>

      <button
        type="button"
        class="op-btn op-btn--primary ml-auto"
        @click="emit('create-order')"
      >
        <IconifyIcon icon="lucide:plus" class="size-4" />
        <span>登记成交订单 / PI</span>
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
              description="暂无成交订单记录，把客户推进到「⑤ 已成交」或点击右上角即可极简登记"
            />
          </template>
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">
              <div class="font-semibold text-[#1D1D1F]">{{ record.name || record.no }}</div>
              <div class="text-[11px] text-[#86868B]">编号：{{ record.no || '自动生成' }}</div>
            </template>

            <template v-else-if="column.key === 'customer'">
              <button
                v-if="record.customerId"
                type="button"
                class="cursor-pointer border-none bg-transparent p-0 text-left text-[13px] font-medium text-[#0071E3] hover:underline"
                @click="emit('inspect-customer', record.customerId)"
              >
                {{ record.customerName || `客户 #${record.customerId}` }} ›
              </button>
              <span v-else>{{ record.customerName || '—' }}</span>
            </template>

            <template v-else-if="column.key === 'amount'">
              <span class="text-[14px] font-bold text-[#248A3D]">
                {{ formatDealMoney(record.totalPrice, detectCurrency(record.remark)) || '$0' }}
              </span>
            </template>

            <template v-else-if="column.key === 'remark'">
              <span class="line-clamp-2 text-[12px] text-[#48484A]">
                {{ cleanOrderRemark(record.remark) || '—' }}
              </span>
            </template>

            <template v-else-if="column.key === 'orderDate'">
              <span class="text-[12px] text-[#6E6E73]">
                {{ record.orderDate ? dayjs(record.orderDate).format('YYYY-MM-DD') : '—' }}
              </span>
            </template>

            <template v-else-if="column.key === 'owner'">
              <span class="text-[12px] text-[#3A3A3C]">
                {{ record.ownerUserName || '—' }}
              </span>
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
  flex-direction: column;
  gap: 3px;
  padding: 13px 16px;
  background: #fff;
  border: 0.5px solid rgb(60 60 67 / 10%);
  border-radius: 14px;
  box-shadow: 0 1px 3px rgb(0 0 0 / 3%);
}

.op-kpi-lbl {
  font-size: 11.5px;
  font-weight: 500;
  color: #86868b;
}

.op-kpi-val {
  font-size: 21px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #1d1d1f;
}

.op-kpi-val span {
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
  font-size: 12.5px;
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

.op-btn--primary {
  color: #fff;
  background: #0071e3;
  border-color: #0071e3;
}

.op-btn--primary:hover {
  background: #0077ed;
}
</style>
