<script lang="ts" setup>
/**
 * 商机：看板（按商机阶段分列，显示每列金额合计，拖动卡片即可改阶段）与列表两种视图。
 */
import type { CrmBusinessApi } from '#/api/crm/business';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useVbenModal } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { Button, Empty, Input, message, Select, Spin, Table } from 'ant-design-vue';

import { getBusinessPage, updateBusinessStatus } from '#/api/crm/business';
import { getBusinessStatusSimpleList, getBusinessStatusTypeSimpleList } from '#/api/crm/business/status';
import BusinessForm from '#/views/crm/business/modules/form.vue';

import { fmtMoney, fmtTime } from './components/shared';

const router = useRouter();
const view = ref<'board' | 'list'>('board');
const loading = ref(true);
const keyword = ref('');
const field = ref('name');
const statuses = ref<{ id: number; name: string }[]>([]);
const rows = ref<CrmBusinessApi.Business[]>([]);

const [FormModal, formApi] = useVbenModal({ connectedComponent: BusinessForm, destroyOnClose: true });

async function load() {
  loading.value = true;
  try {
    const r = await getBusinessPage({ pageNo: 1, pageSize: -1, ...(keyword.value.trim() ? { [field.value]: keyword.value.trim() } : {}) } as any);
    rows.value = r?.list ?? [];
  } finally {
    loading.value = false;
  }
}

const columns = computed(() =>
  statuses.value.map((s) => {
    const list = rows.value.filter((b) => b.statusId === s.id);
    return { ...s, list, sum: list.reduce((t, b) => t + Number(b.totalPrice || 0), 0) };
  }),
);

// ===== 拖动改阶段 =====
const dragging = ref<CrmBusinessApi.Business>();
const over = ref<number>();

async function drop(statusId: number) {
  const b = dragging.value;
  over.value = undefined;
  dragging.value = undefined;
  if (!b || b.statusId === statusId) return;
  const old = b.statusId;
  b.statusId = statusId;
  try {
    await updateBusinessStatus({ id: b.id, statusId, endStatus: undefined });
    message.success(`已移到「${statuses.value.find((s) => s.id === statusId)?.name}」`);
  } catch {
    b.statusId = old;
  }
}

function detail(b: CrmBusinessApi.Business) {
  router.push({ name: 'CrmBusinessDetail', params: { id: b.id } });
}

const listColumns = [
  { title: '商机名称', key: 'name', dataIndex: 'name', width: 200, fixed: 'left' as const },
  { title: '客户名称', key: 'customerName', dataIndex: 'customerName', width: 180 },
  { title: '商机阶段', key: 'statusName', dataIndex: 'statusName', width: 110 },
  { title: '商机金额', key: 'totalPrice', dataIndex: 'totalPrice', width: 130 },
  { title: '预计成交日期', key: 'dealTime', dataIndex: 'dealTime', width: 150 },
  { title: '负责人', key: 'ownerUserName', dataIndex: 'ownerUserName', width: 100 },
  { title: '最近联系时间', key: 'contactLastTime', dataIndex: 'contactLastTime', width: 150 },
  { title: '创建时间', key: 'createTime', dataIndex: 'createTime', width: 150 },
  { title: '操作', key: 'action', width: 110, fixed: 'right' as const },
];

onMounted(async () => {
  const types = await getBusinessStatusTypeSimpleList().catch(() => []);
  const typeId = (types as any[])[0]?.id;
  statuses.value = typeId ? ((await getBusinessStatusSimpleList(typeId).catch(() => [])) as any[]) : [];
  await load();
});
</script>

<template>
  <div class="biz">
    <FormModal @success="load" />
    <div class="biz__head">
      <div class="flex items-baseline gap-2">
        <h2 class="biz__title">商机</h2>
        <span class="text-sm text-gray-500">集中管理商机阶段、客户和预计成交时间</span>
      </div>
      <div class="flex items-center gap-2">
        <div class="biz__switch">
          <button type="button" :class="{ on: view === 'board' }" @click="view = 'board'">
            <IconifyIcon icon="lucide:layout-grid" class="size-3.5" />看板
          </button>
          <button type="button" :class="{ on: view === 'list' }" @click="view = 'list'">
            <IconifyIcon icon="lucide:list" class="size-3.5" />列表
          </button>
        </div>
        <Button @click="formApi.setData(null).open()"><IconifyIcon icon="lucide:plus" class="mr-1 inline size-4" />新增</Button>
      </div>
    </div>

    <div class="biz__search">
      <Select v-model:value="field" :bordered="false" class="w-24" :options="[{ value: 'name', label: '商机名称' }]" />
      <Input v-model:value="keyword" :bordered="false" allow-clear placeholder="请输入商机名称" @press-enter="load" @change="(e: any) => !e.target.value && load()">
        <template #suffix><IconifyIcon icon="lucide:search" class="size-3.5 cursor-pointer text-gray-400" @click="load" /></template>
      </Input>
    </div>

    <Spin :spinning="loading" wrapper-class-name="biz__spin">
      <div v-if="view === 'board'" class="biz__board">
        <div
          v-for="c in columns"
          :key="c.id"
          class="biz__col"
          :class="{ over: over === c.id }"
          @dragover.prevent="over = c.id"
          @dragleave="over === c.id && (over = undefined)"
          @drop.prevent="drop(c.id)"
        >
          <div class="biz__stage">{{ c.name }}<span v-if="c.list.length" class="ml-1 opacity-80">({{ c.list.length }})</span></div>
          <div class="biz__sum">{{ fmtMoney(c.sum) }}</div>
          <div class="biz__cards">
            <div
              v-for="b in c.list"
              :key="b.id"
              class="biz__card"
              draggable="true"
              @dragstart="dragging = b"
              @click="detail(b)"
            >
              <div class="truncate font-medium text-gray-900">{{ b.name }}</div>
              <div class="mt-1 truncate text-xs text-gray-500">{{ b.customerName || '-' }}</div>
              <div class="mt-2 flex items-center justify-between text-xs">
                <span class="font-medium text-[#155dfc]">{{ fmtMoney(b.totalPrice) }}</span>
                <span class="text-gray-400">{{ b.dealTime ? fmtTime(b.dealTime).slice(0, 10) : '' }}</span>
              </div>
            </div>
            <div v-if="!c.list.length" class="biz__empty">暂无商机</div>
          </div>
        </div>
      </div>

      <div v-else class="px-4">
        <Table :columns="listColumns" :data-source="rows" :pagination="{ pageSize: 20, showTotal: (t: number) => `共 ${t} 条` }" row-key="id" size="middle" :scroll="{ x: 1280 }">
          <template #bodyCell="{ column, record }">
            <a v-if="column.key === 'name'" class="font-medium" @click="detail(record as any)">{{ record.name }}</a>
            <template v-else-if="column.key === 'totalPrice'">{{ fmtMoney(record.totalPrice) }}</template>
            <template v-else-if="['dealTime', 'contactLastTime', 'createTime'].includes(String(column.key))">{{ fmtTime(record[column.key as string]) }}</template>
            <template v-else-if="column.key === 'action'">
              <div class="flex gap-3"><a @click="detail(record as any)">查看</a><a @click="formApi.setData(record as any).open()">编辑</a></div>
            </template>
          </template>
          <template #emptyText><Empty :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无商机" /></template>
        </Table>
      </div>
    </Spin>
  </div>
</template>

<style scoped>
.biz {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding-top: 14px;
}

.biz__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px 10px;
}

.biz__title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #111827;
}

.biz__switch {
  display: inline-flex;
  padding: 3px;
  background: #f3f5fa;
  border-radius: 8px;
}

.biz__switch button {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 3px 10px;
  font-size: 13px;
  color: #4b5563;
  cursor: pointer;
  background: none;
  border: 0;
  border-radius: 6px;
}

.biz__switch button.on {
  color: #155dfc;
  background: #fff;
  box-shadow: 0 1px 2px rgb(0 0 0 / 6%);
}

.biz__search {
  display: flex;
  align-items: center;
  width: 300px;
  margin: 0 16px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.biz__spin {
  flex: 1;
  min-height: 0;
}

.biz__spin :deep(.ant-spin-container) {
  height: 100%;
}

.biz__board {
  display: flex;
  gap: 0;
  height: 100%;
  padding: 0 0 8px 12px;
  overflow: auto hidden;
}

.biz__col {
  display: flex;
  flex: 0 0 210px;
  flex-direction: column;
  min-height: 0;
  margin-right: -2px;
}

.biz__col.over .biz__cards {
  background: #eef4ff;
}

.biz__stage {
  height: 32px;
  padding-left: 18px;
  font-size: 13px;
  font-weight: 600;
  line-height: 32px;
  color: #fff;
  text-align: center;
  background: #34c759;
  clip-path: polygon(0 0, calc(100% - 10px) 0, 100% 50%, calc(100% - 10px) 100%, 0 100%, 10px 50%);
}

.biz__col:first-child .biz__stage {
  clip-path: polygon(0 0, calc(100% - 10px) 0, 100% 50%, calc(100% - 10px) 100%, 0 100%);
  border-radius: 6px 0 0 6px;
}

.biz__sum {
  padding: 6px 0;
  font-size: 12px;
  font-weight: 600;
  color: #374151;
  text-align: center;
  background: #f7f8fa;
}

.biz__cards {
  flex: 1;
  padding: 8px 6px;
  overflow: auto;
  border-right: 1px solid #f0f1f3;
}

.biz__card {
  padding: 10px 12px;
  margin-bottom: 8px;
  cursor: grab;
  background: #fff;
  border: 1px solid #eef0f3;
  border-radius: 8px;
}

.biz__card:hover {
  border-color: #c7d7fe;
  box-shadow: 0 2px 8px rgb(21 93 252 / 8%);
}

.biz__empty {
  padding: 28px 0;
  font-size: 12px;
  color: #9ca3af;
  text-align: center;
  background: #f9fafb;
  border-radius: 8px;
}
</style>
