<script lang="ts" setup>
/**
 * 商机：看板（按商机阶段分列，显示每列金额合计，拖动卡片即可改阶段）与列表两种视图。
 */
import type { CrmBusinessApi } from '#/api/crm/business';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useVbenModal } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { Button, message, Spin, Table } from 'ant-design-vue';

import { getBusinessPage, updateBusinessStatus } from '#/api/crm/business';
import { getBusinessStatusSimpleList, getBusinessStatusTypeSimpleList } from '#/api/crm/business/status';
import BusinessForm from '#/views/crm/business/modules/form.vue';

import { crmAvatarBg, crmInitial, fetchAll, fmtMoney, fmtTime } from './components/shared';

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
    rows.value = await fetchAll((p) => getBusinessPage(p as any), keyword.value.trim() ? { [field.value]: keyword.value.trim() } : {});
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

const STAGE_DOTS = ['#007AFF', '#5856D6', '#AF52DE', '#FF9500', '#34C759', '#00C7BE'];
const totalPipeline = computed(() =>
  rows.value.reduce((s, b) => s + Number(b.totalPrice || 0), 0),
);

// ===== 拖动改阶段 =====
const dragging = ref<CrmBusinessApi.Business>();
const over = ref<number>();
const moving = ref(new Set<number>());

async function drop(statusId: number) {
  const b = dragging.value;
  over.value = undefined;
  dragging.value = undefined;
  if (!b || b.endStatus != null || b.statusId === statusId || moving.value.has(b.id)) return;
  const old = b.statusId;
  const oldName = b.statusName;
  const nextName = statuses.value.find((s) => s.id === statusId)?.name;
  moving.value.add(b.id);
  b.statusId = statusId;
  b.statusName = nextName;
  try {
    await updateBusinessStatus({ id: b.id, statusId, endStatus: undefined });
    message.success(nextName ? `已移到「${nextName}」` : '已更新商机阶段');
  } catch {
    b.statusId = old;
    b.statusName = oldName;
  } finally {
    moving.value.delete(b.id);
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
      <div class="flex flex-wrap items-center gap-2.5">
        <h2 class="biz__title">商机推进</h2>
        <span class="ap-crm-pill ap-crm-pill--blue tabular-nums">
          {{ rows.length }} 个商机 · {{ fmtMoney(totalPipeline) }}
        </span>
      </div>
      <div class="flex flex-wrap items-center gap-2.5">
        <div class="biz__search">
          <IconifyIcon icon="lucide:search" class="size-3.5 shrink-0 text-[#8E8E93]" />
          <input
            v-model="keyword"
            type="search"
            class="biz__search-input"
            placeholder="搜索商机名称…"
            @keydown.enter="load"
            @search="load"
          />
          <button
            v-if="keyword"
            type="button"
            class="biz__search-clear"
            aria-label="清空"
            @click="
              keyword = '';
              load();
            "
          >
            <IconifyIcon icon="lucide:x" class="size-3" />
          </button>
        </div>

        <div class="biz__switch" role="group" aria-label="视图切换">
          <button type="button" :class="{ on: view === 'board' }" @click="view = 'board'">
            <IconifyIcon icon="lucide:kanban" class="size-3.5" />看板
          </button>
          <button type="button" :class="{ on: view === 'list' }" @click="view = 'list'">
            <IconifyIcon icon="lucide:list" class="size-3.5" />列表
          </button>
        </div>

        <Button type="primary" shape="round" @click="formApi.setData(null).open()">
          <IconifyIcon icon="lucide:plus" class="mr-1 inline size-4 align-[-3px]" />新增商机
        </Button>
      </div>
    </div>

    <Spin :spinning="loading" wrapper-class-name="biz__spin">
      <div v-if="view === 'board'" class="biz__board">
        <div
          v-for="(c, idx) in columns"
          :key="c.id"
          class="biz__col"
          :class="{ over: over === c.id }"
          @dragover.prevent="over = c.id"
          @dragleave="over === c.id && (over = undefined)"
          @drop.prevent="drop(c.id)"
        >
          <div class="biz__col-head">
            <div class="flex items-center justify-between gap-2">
              <span class="flex min-w-0 items-center gap-2 font-semibold text-[#1D1D1F]">
                <span
                  class="size-2 shrink-0 rounded-full"
                  :style="{ background: STAGE_DOTS[idx % STAGE_DOTS.length] }"
                ></span>
                <span class="truncate text-[13.5px]">{{ c.name }}</span>
              </span>
              <span class="biz__col-count tabular-nums">{{ c.list.length }}</span>
            </div>
            <div class="mt-1 text-[12px] font-semibold text-[#6E6E73] tabular-nums">
              {{ fmtMoney(c.sum) }}
            </div>
          </div>

          <div class="biz__cards">
            <div
              v-for="b in c.list"
              :key="b.id"
              class="biz__card"
              :draggable="b.endStatus == null && !moving.has(b.id)"
              @dragstart="dragging = b"
              @dragend="dragging = over = undefined"
              @click="detail(b)"
            >
              <div class="truncate text-[13.5px] font-semibold text-[#1D1D1F]">
                {{ b.name }}
              </div>
              <div class="mt-1.5 flex items-center gap-1.5 text-xs text-[#6E6E73]">
                <span
                  class="inline-grid size-4 shrink-0 place-items-center rounded-full text-[9px] font-bold text-white"
                  :style="{ background: crmAvatarBg(b.customerName || b.id) }"
                  >{{ crmInitial(b.customerName) }}</span
                >
                <span class="truncate">{{ b.customerName || '未关联客户' }}</span>
              </div>
              <div class="mt-3 flex items-center justify-between border-0 border-t border-solid border-black/[0.05] pt-2 text-xs">
                <span class="font-bold text-[#007AFF] tabular-nums">
                  {{ fmtMoney(b.totalPrice) }}
                </span>
                <span v-if="b.dealTime" class="text-[#8E8E93] tabular-nums">
                  {{ fmtTime(b.dealTime).slice(0, 10) }}
                </span>
              </div>
            </div>
            <div v-if="!c.list.length" class="biz__empty">
              拖拽商机卡片至此阶段
            </div>
          </div>
        </div>
      </div>

      <div v-else class="biz__list-card">
        <Table
          :columns="listColumns"
          :data-source="rows"
          :pagination="{ pageSize: 20, showTotal: (t: number) => `共 ${t} 条` }"
          row-key="id"
          size="middle"
          :scroll="{ x: 1280 }"
        >
          <template #bodyCell="{ column, record }">
            <a
              v-if="column.key === 'name'"
              class="font-semibold text-[#1D1D1F] hover:text-[#007AFF]"
              @click="detail(record as any)"
              >{{ record.name }}</a
            >
            <span v-else-if="column.key === 'statusName'" class="ap-crm-pill ap-crm-pill--blue">
              {{ record.statusName || '-' }}
            </span>
            <span
              v-else-if="column.key === 'totalPrice'"
              class="font-semibold text-[#1D1D1F] tabular-nums"
              >{{ fmtMoney(record.totalPrice) }}</span
            >
            <span
              v-else-if="['dealTime', 'contactLastTime', 'createTime'].includes(String(column.key))"
              class="tabular-nums text-[#6E6E73]"
              >{{ fmtTime(record[column.key as string]) }}</span
            >
            <template v-else-if="column.key === 'action'">
              <div class="flex gap-3">
                <a class="ap-crm-link" @click="detail(record as any)">查看</a>
                <a class="ap-crm-link" @click="formApi.setData(record as any).open()">编辑</a>
              </div>
            </template>
          </template>
          <template #emptyText>
            <div class="py-12 text-center text-[13px] text-[#86868B]">暂无商机记录</div>
          </template>
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
  padding: 20px 24px 16px;
}

.biz__head {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 14px;
}

.biz__title {
  margin: 0;
  font-size: 21px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #1d1d1f;
}

.biz__search {
  display: flex;
  gap: 6px;
  align-items: center;
  width: 210px;
  height: 32px;
  padding: 0 10px;
  background: rgb(255 255 255 / 86%);
  border-radius: 999px;
  box-shadow:
    0 1px 2px rgb(0 0 0 / 5%),
    inset 0 0 0 0.5px rgb(0 0 0 / 10%);
  transition: all var(--ap-dur-fast);
}

.biz__search:focus-within {
  background: #fff;
  box-shadow:
    0 0 0 2px rgb(0 122 255 / 22%),
    inset 0 0 0 0.5px rgb(0 122 255 / 45%);
}

.biz__search-input {
  flex: 1;
  min-width: 0;
  height: 100%;
  padding: 0;
  font-size: 12.5px;
  color: #1d1d1f;
  background: transparent;
  border: 0;
  outline: none;
}

.biz__search-input::-webkit-search-cancel-button {
  display: none;
}

.biz__search-clear {
  display: grid;
  flex: none;
  place-items: center;
  width: 15px;
  height: 15px;
  padding: 0;
  color: #fff;
  cursor: pointer;
  background: #aeaeb2;
  border: 0;
  border-radius: 50%;
}

.biz__switch {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  background: rgb(118 118 128 / 11%);
  border-radius: 999px;
}

.biz__switch button {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 26px;
  padding: 0 12px;
  font-size: 12.5px;
  font-weight: 500;
  color: #6e6e73;
  cursor: pointer;
  background: none;
  border: 0;
  border-radius: 999px;
  transition: all var(--ap-dur-fast);
}

.biz__switch button.on {
  font-weight: 600;
  color: #1d1d1f;
  background: #fff;
  box-shadow:
    0 1px 3px rgb(0 0 0 / 8%),
    0 0 0 0.5px rgb(0 0 0 / 4%);
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
  gap: 12px;
  height: 100%;
  padding-bottom: 6px;
  overflow: auto hidden;
}

.biz__col {
  display: flex;
  flex: 0 0 256px;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  background: rgb(255 255 255 / 66%);
  border: 0.5px solid rgb(0 0 0 / 6%);
  border-radius: 18px;
  backdrop-filter: blur(18px);
  transition:
    background-color var(--ap-dur-fast),
    box-shadow var(--ap-dur-fast);
}

.biz__col.over {
  background: rgb(232 242 255 / 80%);
  box-shadow: inset 0 0 0 1.5px rgb(0 122 255 / 45%);
}

.biz__col-head {
  padding: 13px 14px 10px;
  border-bottom: 0.5px solid rgb(0 0 0 / 6%);
}

.biz__col-count {
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  font-size: 11px;
  font-weight: 600;
  line-height: 20px;
  color: #6e6e73;
  text-align: center;
  background: rgb(0 0 0 / 5%);
  border-radius: 999px;
}

.biz__cards {
  flex: 1;
  padding: 10px;
  overflow: auto;
}

.biz__card {
  padding: 12px 13px;
  margin-bottom: 9px;
  cursor: grab;
  background: #fff;
  border-radius: 14px;
  box-shadow:
    0 1px 3px rgb(15 23 42 / 5%),
    0 0 0 0.5px rgb(0 0 0 / 5%);
  transition:
    transform var(--ap-dur-fast) var(--ap-ease),
    box-shadow var(--ap-dur-fast) var(--ap-ease);
}

.biz__card:hover {
  transform: translateY(-1.5px);
  box-shadow:
    0 6px 16px rgb(15 23 42 / 8%),
    0 0 0 0.5px rgb(0 122 255 / 22%);
}

.biz__card:active {
  cursor: grabbing;
  transform: scale(0.99);
}

.biz__empty {
  padding: 28px 12px;
  font-size: 12px;
  color: #a1a1a6;
  text-align: center;
  border: 1px dashed rgb(0 0 0 / 10%);
  border-radius: 12px;
}

.biz__list-card {
  height: 100%;
  overflow: auto;
  background: var(--ap-surface);
  border-radius: 18px;
  box-shadow: var(--ap-shadow-rest);
}
</style>
