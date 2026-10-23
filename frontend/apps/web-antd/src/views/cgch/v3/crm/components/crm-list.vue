<script lang="ts" setup>
/**
 * CRM 列表页（与原网站一致）：标题 + 右上角操作按钮；左侧筛选栏（全部 / 分类，可展开，带数量，可收起）；
 * 右侧搜索框、日期范围、字段过滤、表格设置，表格与分页。
 */
import type { Dayjs } from 'dayjs';

import type { CrmColumn, RailItem } from './types';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Checkbox, DatePicker, Empty, Input, Pagination, Popover, Table, Tooltip } from 'ant-design-vue';


const props = withDefaults(
  defineProps<{
    title: string;
    /** 标题旁的总数（订单管理显示） */
    showTotal?: boolean;
    rail?: RailItem[];
    columns: CrmColumn[];
    fetch: (params: Record<string, any>) => Promise<{ list: any[]; total: number }>;
    searchField?: string;
    searchPlaceholder?: string;
    /** 日期范围筛选，例如 { field: 'contactLastTime', label: '最近联系时间' } */
    dateFilter?: { field: string; label: string };
    /** 是否显示勾选列 */
    selectable?: boolean;
    /** 额外固定的查询参数（例如公海） */
    baseParams?: Record<string, any>;
    storageKey: string;
    /** 「全部」一项的名称，例如订单管理里是「订单总数」 */
    allLabel?: string;
    /** 「全部」下方只展示、不可点选的统计，例如订单总额 */
    stats?: { label: string; value: number | string }[];
  }>(),
  { rail: () => [], stats: () => [], allLabel: '全部', searchField: 'name', searchPlaceholder: '请输入名称', selectable: true, baseParams: () => ({}), showTotal: false },
);

const emit = defineEmits<{ select: [rows: any[]] }>();

// ===== 左侧筛选栏 =====
const railOpen = ref(true);
const railKey = ref('all');
const expanded = reactive<Record<string, boolean>>({});
const counts = reactive<Record<string, number>>({});

const allItem: RailItem = { key: 'all', label: '全部', filter: {} };
const railFilter = computed(() => findItem(railKey.value)?.filter ?? {});

function findItem(key: string): RailItem | undefined {
  if (key === 'all') return allItem;
  for (const it of props.rail) {
    if (it.key === key) return it;
    const c = it.children?.find((x) => x.key === key);
    if (c) return c;
  }
  return undefined;
}

async function countOf(it: RailItem) {
  if (it.children && !it.filter) {
    // 分类本身没有筛选条件：数量是各子项之和
    return;
  }
  try {
    const r = await props.fetch({ ...props.baseParams, ...it.filter, pageNo: 1, pageSize: 1 });
    counts[it.key] = r.total ?? 0;
  } catch {
    counts[it.key] = 0;
  }
}

async function loadCounts() {
  await countOf(allItem);
  await Promise.all(props.rail.map((it) => countOf(it)));
  await Promise.all(props.rail.flatMap((it) => (it.children ?? []).map((c) => countOf(c))));
}

function groupCount(it: RailItem) {
  if (!it.children || it.filter) return counts[it.key] ?? 0;
  return it.children.reduce((s, c) => s + (counts[c.key] ?? 0), 0);
}

function toggleGroup(it: RailItem) {
  expanded[it.key] = !expanded[it.key];
}

function pickRail(key: string) {
  railKey.value = key;
  page.no = 1;
  load();
}

// ===== 搜索与表格 =====
const keyword = ref('');
const range = ref<[Dayjs, Dayjs]>();
const page = reactive({ no: 1, size: 20 });
const loading = ref(false);
const rows = ref<any[]>([]);
const total = ref(0);
const selectedKeys = ref<number[]>([]);
/** 外部筛选条件（例如「背调结果」「订单状态」下拉），由页面通过 setExtra 设置 */
const extra = ref<Record<string, any>>({});

function readHidden(): string[] {
  try {
    const v = localStorage.getItem(`cgch-crm-cols:${props.storageKey}`);
    if (v) return JSON.parse(v);
  } catch {
    // 浏览器禁止存储时使用默认设置
  }
  return props.columns.filter((c) => c.hidden).map((c) => c.key);
}
const hiddenCols = ref<string[]>(readHidden());
watch(hiddenCols, (v) => {
  try {
    localStorage.setItem(`cgch-crm-cols:${props.storageKey}`, JSON.stringify(v));
  } catch {
    // 忽略
  }
});

const tableColumns = computed(() =>
  props.columns
    .filter((c) => !hiddenCols.value.includes(c.key))
    .map((c) => ({ key: c.key, dataIndex: c.key, title: c.title, width: c.width ?? 140, fixed: c.fixed, ellipsis: c.ellipsis ?? true })),
);
const scrollX = computed(() => tableColumns.value.reduce((s, c) => s + (c.width || 140), 0) + (props.selectable ? 48 : 0));

function params() {
  const p: Record<string, any> = { ...props.baseParams, ...railFilter.value, ...extra.value, pageNo: page.no, pageSize: page.size };
  if (keyword.value.trim()) p[props.searchField] = keyword.value.trim();
  if (props.dateFilter && range.value) {
    p[props.dateFilter.field] = [
      range.value[0].startOf('day').format('YYYY-MM-DD HH:mm:ss'),
      range.value[1].endOf('day').format('YYYY-MM-DD HH:mm:ss'),
    ];
  }
  return p;
}

async function load() {
  loading.value = true;
  try {
    const r = await props.fetch(params());
    rows.value = r.list ?? [];
    total.value = r.total ?? 0;
    selectedKeys.value = [];
    emit('select', []);
  } finally {
    loading.value = false;
  }
}

function search() {
  page.no = 1;
  load();
}

function setExtra(v: Record<string, any>) {
  extra.value = Object.fromEntries(Object.entries(v).filter(([, x]) => x !== undefined && x !== null && x !== ''));
  search();
}

const rowSelection = computed(() =>
  props.selectable
    ? {
        selectedRowKeys: selectedKeys.value,
        onChange: (keys: any[], selected: any[]) => {
          selectedKeys.value = keys;
          emit('select', selected);
        },
      }
    : undefined,
);

/** 刷新列表与左侧数量 */
function reload() {
  load();
  loadCounts();
}

onMounted(reload);
watch(
  () => props.rail.map((it) => `${it.key}:${(it.children ?? []).map((c) => c.key).join(',')}`).join('|'),
  () => loadCounts(),
);

defineExpose({ reload, setExtra, currentParams: params });
</script>

<template>
  <div class="crm-list">
    <div class="crm-list__head">
      <h2 class="crm-list__title">
        {{ title }}
        <span v-if="showTotal" class="ml-1">{{ total }}</span>
      </h2>
      <div class="flex flex-wrap items-center gap-2">
        <slot name="actions"></slot>
      </div>
    </div>

    <div class="crm-list__body">
      <aside v-if="rail.length || $slots.rail" class="crm-list__rail" :class="{ closed: !railOpen }">
        <template v-if="railOpen">
          <div class="crm-rail__item crm-rail__all" :class="{ on: railKey === 'all' }" @click="pickRail('all')">
            <span>{{ allLabel }}</span><span class="crm-rail__num">{{ counts.all ?? 0 }}</span>
          </div>
          <div v-for="st in stats" :key="st.label" class="crm-rail__item crm-rail__stat">
            <span>{{ st.label }}</span><span class="crm-rail__num">{{ st.value }}</span>
          </div>
          <div class="crm-rail__list">
            <template v-for="it in rail" :key="it.key">
              <div v-if="it.divider" class="crm-rail__sep"></div>
              <div
                class="crm-rail__item"
                :class="{ on: railKey === it.key }"
                @click="it.children && !it.filter ? toggleGroup(it) : pickRail(it.key)"
              >
                <span class="flex items-center gap-1">
                  <IconifyIcon
                    v-if="it.children"
                    icon="lucide:triangle"
                    class="crm-rail__caret"
                    :class="{ open: expanded[it.key] }"
                    @click.stop="toggleGroup(it)"
                  />
                  <IconifyIcon v-if="it.icon" :icon="it.icon" class="size-3.5 text-amber-400" />
                  {{ it.label }}
                </span>
                <span class="crm-rail__num">{{ groupCount(it) }}</span>
              </div>
              <template v-if="it.children && expanded[it.key]">
                <div
                  v-for="c in it.children"
                  :key="c.key"
                  class="crm-rail__item crm-rail__child"
                  :class="{ on: railKey === c.key }"
                  @click="pickRail(c.key)"
                >
                  <span class="truncate">{{ c.label }}</span>
                  <span class="crm-rail__num">{{ counts[c.key] ?? 0 }}</span>
                </div>
                <div v-if="!it.children.length" class="crm-rail__child py-1 text-xs text-gray-400">暂无</div>
              </template>
            </template>
          </div>
        </template>
        <button type="button" class="crm-list__fold" :title="railOpen ? '收起筛选' : '展开筛选'" @click="railOpen = !railOpen">
          <IconifyIcon :icon="railOpen ? 'lucide:chevron-left' : 'lucide:chevron-right'" class="size-3.5" />
        </button>
      </aside>

      <section class="crm-list__main">
        <div class="crm-list__filters">
          <Input
            v-model:value="keyword"
            allow-clear
            class="crm-list__search"
            :placeholder="searchPlaceholder"
            @press-enter="search"
            @change="(e: any) => !e.target.value && search()"
          >
            <template #prefix><IconifyIcon icon="lucide:search" class="size-3.5 text-gray-400" /></template>
          </Input>
          <div v-if="dateFilter" class="crm-list__range">
            <span class="crm-list__range-label">{{ dateFilter.label }}</span>
            <DatePicker.RangePicker
              v-model:value="range"
              :bordered="false"
              :placeholder="['开始日期', '结束日期']"
              @change="search"
            />
          </div>
          <slot name="filters"></slot>
          <Tooltip title="刷新">
            <button type="button" class="crm-list__icon" @click="reload"><IconifyIcon icon="lucide:rotate-cw" class="size-4" /></button>
          </Tooltip>
          <Popover trigger="click" placement="bottomRight">
            <template #content>
              <div class="max-h-80 w-48 overflow-auto">
                <div class="mb-2 text-xs text-gray-500">显示的列</div>
                <div v-for="c in columns" :key="c.key" class="py-1">
                  <Checkbox
                    :checked="!hiddenCols.includes(c.key)"
                    :disabled="c.required"
                    @change="(e: any) => (hiddenCols = e.target.checked ? hiddenCols.filter((k) => k !== c.key) : [...hiddenCols, c.key])"
                  >
                    {{ c.title }}
                  </Checkbox>
                </div>
              </div>
            </template>
            <Tooltip title="表格设置">
              <button type="button" class="crm-list__icon"><IconifyIcon icon="lucide:settings" class="size-4" /></button>
            </Tooltip>
          </Popover>
        </div>

        <div class="crm-list__table">
          <Table
            :columns="tableColumns"
            :data-source="rows"
            :loading="loading"
            :pagination="false"
            :row-selection="rowSelection"
            :scroll="{ x: scrollX, y: 'calc(100vh - 290px)' }"
            row-key="id"
            size="middle"
          >
            <template #bodyCell="slotProps">
              <slot name="cell" v-bind="slotProps">{{ slotProps.text ?? '-' }}</slot>
            </template>
            <template #emptyText>
              <Empty :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无数据" />
            </template>
          </Table>
        </div>

        <div class="crm-list__pager">
          <span class="mr-2 text-gray-500">共 {{ total }} 条</span>
          <Pagination
            v-model:current="page.no"
            v-model:page-size="page.size"
            :total="total"
            :show-size-changer="true"
            :page-size-options="['10', '20', '50', '100']"
            size="small"
            @change="load"
          />
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.crm-list {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 14px 16px 10px;
}

.crm-list__head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 10px;
}

.crm-list__title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: #111827;
}

.crm-list__body {
  display: flex;
  flex: 1;
  min-height: 0;
}

.crm-list__rail {
  position: relative;
  display: flex;
  flex: 0 0 168px;
  flex-direction: column;
  padding-right: 10px;
  margin-right: 14px;
  border-right: 1px solid #eef0f3;
}

.crm-list__rail.closed {
  flex-basis: 6px;
  padding-right: 0;
}

.crm-list__fold {
  position: absolute;
  top: 2px;
  right: -9px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  color: #6b7280;
  cursor: pointer;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 50%;
}

.crm-rail__list {
  flex: 1;
  overflow: auto;
}

.crm-rail__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px;
  font-size: 13px;
  color: #1f2937;
  cursor: pointer;
  border-radius: 6px;
}

.crm-rail__item:hover {
  background: #f5f7fb;
}

.crm-rail__item.on {
  color: #155dfc;
  background: #eef3fe;
}

.crm-rail__all {
  margin-bottom: 6px;
  font-weight: 600;
  border-bottom: 1px solid #eef0f3;
  border-radius: 6px 6px 0 0;
}

.crm-rail__stat {
  cursor: default;
}

.crm-rail__stat:hover {
  background: none;
}

.crm-rail__child {
  padding-left: 26px;
  color: #4b5563;
}

.crm-rail__sep {
  height: 1px;
  margin: 8px 0;
  background: #eef0f3;
}

.crm-rail__num {
  font-size: 12px;
  color: #9ca3af;
}

.crm-rail__caret {
  width: 7px;
  height: 7px;
  color: #6b7280;
  transform: rotate(90deg);
  transition: transform 0.15s;
}

.crm-rail__caret.open {
  transform: rotate(180deg);
}

.crm-list__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.crm-list__filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding-bottom: 10px;
}

.crm-list__search {
  width: 200px;
}

.crm-list__range {
  display: inline-flex;
  align-items: center;
  padding-left: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.crm-list__range-label {
  padding-right: 4px;
  font-size: 13px;
  color: #374151;
  white-space: nowrap;
  border-right: 1px solid #e5e7eb;
}

.crm-list__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  color: #4b5563;
  cursor: pointer;
  background: none;
  border: 0;
  border-radius: 6px;
}

.crm-list__icon:hover {
  color: #155dfc;
  background: #f3f6fd;
}

.crm-list__table {
  flex: 1;
  min-height: 0;
}

.crm-list__table :deep(.ant-table-thead > tr > th) {
  font-weight: 400;
  color: #6b7280;
  background: #f7f8fa;
}

.crm-list__pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding-top: 10px;
  font-size: 13px;
}

@media (max-width: 760px) {
  .crm-list__rail {
    display: none;
  }

  .crm-list__search {
    width: 100%;
  }
}
</style>
