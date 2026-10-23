<script lang="ts" setup>
/** 订单管理：左侧显示订单总数、订单总额、已回款与待回款总额，并按订单状态筛选 */
import type { CrmColumn, RailItem } from './components/types';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useVbenModal } from '@vben/common-ui';
import { DICT_TYPE } from '@vben/constants';
import { getDictLabel } from '@vben/hooks';
import { IconifyIcon } from '@vben/icons';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Button, message, Popconfirm, Select } from 'ant-design-vue';

import { deleteContract, exportContract, getContractPage } from '#/api/crm/contract';
import ContractForm from '#/views/crm/contract/modules/form.vue';

import CrmList from './components/crm-list.vue';
import { dictRail, fmtMoney, fmtTime, page } from './components/shared';

const router = useRouter();
const listRef = ref<InstanceType<typeof CrmList>>();
const status = ref<number>();

const statusRail = dictRail(DICT_TYPE.CRM_AUDIT_STATUS, 'auditStatus', 'audit');
const rail: RailItem[] = [{ key: 'status', label: '订单状态', children: statusRail }];
const statusOptions = statusRail.map((s) => ({ value: s.filter!.auditStatus, label: s.label }));

const sums = ref({ total: 0, received: 0 });
const stats = computed(() => [
  { label: '订单总额', value: fmtMoney(sums.value.total) },
  { label: '已回款总额', value: fmtMoney(sums.value.received) },
  { label: '待回款总额', value: fmtMoney(sums.value.total - sums.value.received) },
]);

const columns: CrmColumn[] = [
  { key: 'no', title: '订单编号', width: 160, fixed: 'left', required: true },
  { key: 'name', title: '订单名称', width: 180 },
  { key: 'customerName', title: '客户名称', width: 180 },
  { key: 'totalPrice', title: '订单总金额', width: 130 },
  { key: 'auditStatus', title: '订单状态', width: 100 },
  { key: 'orderDate', title: '订单时间', width: 150 },
  { key: 'source', title: '订单来源', width: 110, hidden: true },
  { key: 'payment', title: '付款方式', width: 110, hidden: true },
  { key: 'shipping', title: '运输方式', width: 110, hidden: true },
  { key: 'delivery', title: '交货方式', width: 110, hidden: true },
  { key: 'tracking', title: '物流单号', width: 140, hidden: true },
  { key: 'createTime', title: '创建时间', width: 150 },
  { key: 'action', title: '操作', width: 140, fixed: 'right', required: true },
];

const fetch = (p: Record<string, any>) => page(getContractPage({ ...p } as any));

const [FormModal, formApi] = useVbenModal({ connectedComponent: ContractForm, destroyOnClose: true });

async function loadSums() {
  const r = await getContractPage({ pageNo: 1, pageSize: -1 } as any).catch(() => undefined);
  const list = r?.list ?? [];
  sums.value = {
    total: list.reduce((s, c) => s + Number(c.totalPrice || 0), 0),
    received: list.reduce((s, c) => s + Number(c.totalReceivablePrice || 0), 0),
  };
}

function refresh() {
  listRef.value?.reload();
  loadSums();
}

function detail(r: any) {
  router.push({ name: 'CrmContractDetail', params: { id: r.id } });
}

async function remove(r: any) {
  await deleteContract(r.id);
  message.success('已删除');
  refresh();
}

async function doExport() {
  const data = await exportContract({ ...listRef.value?.currentParams(), pageNo: 1, pageSize: -1 });
  downloadFileFromBlobPart({ fileName: '订单.xls', source: data });
}

onMounted(loadSums);
</script>

<template>
  <div class="h-full">
    <FormModal @success="refresh" />
    <CrmList
      ref="listRef"
      title="订单管理"
      show-total
      all-label="订单总数"
      :stats="stats"
      :rail="rail"
      :columns="columns"
      :fetch="fetch"
      storage-key="order"
      search-placeholder="请输入订单名称"
    >
      <template #actions>
        <Button type="primary" @click="formApi.setData(null).open()"><IconifyIcon icon="lucide:plus" class="mr-1 inline size-4" />新增</Button>
        <Button type="primary" @click="doExport"><IconifyIcon icon="lucide:upload" class="mr-1 inline size-4" />导出</Button>
      </template>
      <template #filters>
        <div class="inline-flex items-center rounded-md border border-solid border-gray-200 pl-2.5">
          <span class="border-0 border-r border-solid border-gray-200 pr-2 text-[13px] text-gray-700">订单状态</span>
          <Select
            v-model:value="status"
            :bordered="false"
            allow-clear
            class="w-32"
            placeholder="请选择"
            :options="statusOptions"
            @change="listRef?.setExtra({ auditStatus: status })"
          />
        </div>
      </template>
      <template #cell="{ column, record }">
        <a v-if="column.key === 'no'" class="font-medium" @click="detail(record)">{{ record.no || record.id }}</a>
        <template v-else-if="column.key === 'totalPrice'">{{ fmtMoney(record.totalPrice) }}</template>
        <template v-else-if="column.key === 'auditStatus'">{{ getDictLabel(DICT_TYPE.CRM_AUDIT_STATUS, record.auditStatus) || '-' }}</template>
        <template v-else-if="column.key === 'orderDate'">{{ fmtTime(record.orderDate) }}</template>
        <template v-else-if="column.key === 'createTime'">{{ fmtTime(record.createTime) }}</template>
        <template v-else-if="column.key === 'action'">
          <div class="flex gap-3">
            <a @click="detail(record)">查看</a>
            <a @click="formApi.setData(record).open()">编辑</a>
            <Popconfirm title="确定删除这个订单吗？" @confirm="remove(record)"><a class="text-red-500">删除</a></Popconfirm>
          </div>
        </template>
        <template v-else>{{ (record as any)[column.key as string] || '-' }}</template>
      </template>
    </CrmList>
  </div>
</template>
