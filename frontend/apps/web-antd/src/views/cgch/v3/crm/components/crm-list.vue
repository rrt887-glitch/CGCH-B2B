<script lang="ts" setup>
/**
 * CRM 列表页（与原网站一致）：标题 + 右上角操作按钮；左侧筛选栏（全部 / 分类，可展开，带数量，可收起）；
 * 右侧搜索框、日期范围、字段过滤、表格设置，表格与分页。
 */
import type { Dayjs } from 'dayjs';

import type { CrmColumn, RailItem } from './types';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import {
  Checkbox,
  DatePicker,
  Pagination,
  Popover,
  Table,
  Tooltip,
} from 'ant-design-vue';

const props = withDefaults(
  defineProps<{
    title: string;
    /** 标题旁的总数（订单管理显示） */
    showTotal?: boolean;
    rail?: RailItem[];
    columns: CrmColumn[];
    fetch: (
      params: Record<string, any>,
    ) => Promise<{ list: any[]; total: number }>;
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
  {
    rail: () => [],
    stats: () => [],
    allLabel: '全部',
    searchField: 'name',
    searchPlaceholder: '请输入名称',
    selectable: true,
    baseParams: () => ({}),
    showTotal: false,
  },
);

const emit = defineEmits<{
  loaded: [rows: any[]];
  /** 搜索关键词变化（空字符串表示已清空），父组件据此判断列表是否处于筛选态 */
  search: [keyword: string];
  select: [rows: any[]];
}>();

// ===== 左侧筛选栏 =====
const railOpen = ref(false);
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
    const r = await props.fetch({
      ...props.baseParams,
      ...it.filter,
      pageNo: 1,
      pageSize: 1,
    });
    counts[it.key] = r.total ?? 0;
  } catch {
    counts[it.key] = 0;
  }
}

async function loadCounts() {
  await countOf(allItem);
  await Promise.all(props.rail.map((it) => countOf(it)));
  await Promise.all(
    props.rail.flatMap((it) => (it.children ?? []).map((c) => countOf(c))),
  );
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
const loadFailed = ref(false);
let loadVersion = 0;
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
    localStorage.setItem(
      `cgch-crm-cols:${props.storageKey}`,
      JSON.stringify(v),
    );
  } catch {
    // 忽略
  }
});

const tableColumns = computed(() =>
  props.columns
    .filter((c) => !hiddenCols.value.includes(c.key))
    .map((c) => ({
      key: c.key,
      dataIndex: c.key,
      title: c.title,
      width: c.width ?? 140,
      fixed: c.fixed,
      ellipsis: c.ellipsis ?? true,
    })),
);
const scrollX = computed(
  () =>
    tableColumns.value.reduce((s, c) => s + (c.width || 140), 0) +
    (props.selectable ? 48 : 0),
);

// 记住本次会话里的搜索词、左侧筛选和页码：从详情页返回列表时恢复（只存在本标签页的会话存储里）
const STATE_KEY = `cgch-crm-list:${props.storageKey}`;
try {
  const saved = JSON.parse(sessionStorage.getItem(STATE_KEY) || 'null');
  if (saved) {
    keyword.value = saved.keyword ?? '';
    railKey.value = saved.railKey ?? 'all';
    page.no = saved.pageNo ?? 1;
    page.size = saved.pageSize ?? page.size;
  }
} catch {
  // 浏览器禁止存储时不恢复
}
function saveState() {
  try {
    sessionStorage.setItem(
      STATE_KEY,
      JSON.stringify({
        keyword: keyword.value,
        railKey: railKey.value,
        pageNo: page.no,
        pageSize: page.size,
      }),
    );
  } catch {
    // 忽略
  }
}

function params() {
  const p: Record<string, any> = {
    ...props.baseParams,
    ...railFilter.value,
    ...extra.value,
    pageNo: page.no,
    pageSize: page.size,
  };
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
  const version = ++loadVersion;
  saveState();
  loading.value = true;
  loadFailed.value = false;
  selectedKeys.value = [];
  emit('select', []);
  try {
    const r = await props.fetch(params());
    if (version !== loadVersion) return;
    rows.value = r.list ?? [];
    total.value = r.total ?? 0;
    emit('loaded', rows.value);
  } catch {
    if (version !== loadVersion) return;
    rows.value = [];
    total.value = 0;
    loadFailed.value = true;
    emit('loaded', []);
  } finally {
    if (version === loadVersion) loading.value = false;
  }
}

function search() {
  page.no = 1;
  emit('search', keyword.value.trim());
  load();
}

function setExtra(v: Record<string, any>) {
  extra.value = Object.fromEntries(
    Object.entries(v).filter(
      ([, x]) => x !== undefined && x !== null && x !== '',
    ),
  );
  search();
}

/**
 * 筛选入口的文案：这个组件被客户、线索、订单复用，写死「客户筛选」会误导，
 * 按列表名推导（客户列表 → 客户筛选、线索列表 → 线索筛选、订单管理 → 订单筛选）。
 */
const filterLabel = computed(() => {
  const base = props.title.replace(/(列表|管理)$/, '').trim();
  return `${base || '客户'}筛选`;
});

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
  () =>
    props.rail
      .map(
        (it) => `${it.key}:${(it.children ?? []).map((c) => c.key).join(',')}`,
      )
      .join('|'),
  () => loadCounts(),
);

defineExpose({ reload, setExtra, currentParams: params });
</script>

<template>
  <div class="crm-list">
    <div class="crm-list__head">
      <div class="flex items-center gap-2.5">
        <h2 class="crm-list__title">{{ title }}</h2>
        <span v-if="showTotal || total > 0" class="crm-list__count-badge tabular-nums">
          {{ total }}
        </span>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <button
          v-if="rail.length || $slots.rail"
          type="button"
          class="crm-filter-toggle"
          :class="{ 'is-open': railOpen || railKey !== 'all' }"
          :aria-expanded="railOpen"
          @click="railOpen = !railOpen"
        >
          <IconifyIcon
            :icon="railOpen ? 'lucide:panel-left-close' : 'lucide:sliders-horizontal'"
            class="size-3.5"
          />
          <span>{{ railOpen ? '收起分类' : filterLabel }}</span>
        </button>
        <slot name="actions"></slot>
      </div>
    </div>

    <div class="crm-list__body">
      <aside
        v-if="(rail.length || $slots.rail) && railOpen"
        class="crm-list__rail"
      >
        <div class="crm-rail__head">
          <span>分类视图</span>
          <button
            type="button"
            class="crm-rail__close"
            title="收起分类栏"
            @click="railOpen = false"
          >
            <IconifyIcon icon="lucide:panel-left-close" class="size-3.5" />
          </button>
        </div>

        <div
          class="crm-rail__item crm-rail__all"
          :class="{ on: railKey === 'all' }"
          @click="pickRail('all')"
        >
          <span>{{ allLabel }}</span>
          <span class="crm-rail__num tabular-nums">{{ counts.all ?? 0 }}</span>
        </div>

        <div v-if="stats.length" class="crm-rail__stats">
          <div
            v-for="st in stats"
            :key="st.label"
            class="crm-rail__stat-row"
          >
            <span class="text-[#86868B]">{{ st.label }}</span>
            <span class="font-semibold text-[#1D1D1F] tabular-nums">{{ st.value }}</span>
          </div>
        </div>

        <div class="crm-rail__list">
          <template v-for="it in rail" :key="it.key">
            <div v-if="it.divider" class="crm-rail__sep"></div>
            <div
              class="crm-rail__item"
              :class="{ on: railKey === it.key }"
              @click="
                it.children && !it.filter ? toggleGroup(it) : pickRail(it.key)
              "
            >
              <span class="flex min-w-0 items-center gap-1.5">
                <IconifyIcon
                  v-if="it.children"
                  icon="lucide:chevron-right"
                  class="crm-rail__caret shrink-0"
                  :class="{ open: expanded[it.key] }"
                  @click.stop="toggleGroup(it)"
                />
                <IconifyIcon
                  v-if="it.icon"
                  :icon="it.icon"
                  class="size-3.5 shrink-0 text-[#FF9500]"
                />
                <span class="truncate">{{ it.label }}</span>
              </span>
              <span class="crm-rail__num tabular-nums">{{ groupCount(it) }}</span>
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
                <span class="crm-rail__num tabular-nums">{{ counts[c.key] ?? 0 }}</span>
              </div>
              <div
                v-if="!it.children.length"
                class="crm-rail__child py-1.5 text-xs text-[#AEAEB2]"
              >
                暂无子项
              </div>
            </template>
          </template>
        </div>
      </aside>

      <section class="crm-list__main">
        <div class="crm-list__filters">
          <!-- iOS 无边框圆角搜索框 -->
          <div class="crm-search-box">
            <IconifyIcon icon="lucide:search" class="crm-search-box__icon" />
            <input
              v-model="keyword"
              type="search"
              class="crm-search-box__input"
              :placeholder="searchPlaceholder"
              aria-label="搜索"
              @keydown.enter="search"
              @search="search"
            />
            <button
              v-if="keyword"
              type="button"
              class="crm-search-box__clear"
              aria-label="清空搜索"
              @click="
                keyword = '';
                search();
              "
            >
              <IconifyIcon icon="lucide:x" class="size-3" />
            </button>
          </div>

          <div v-if="dateFilter" class="crm-list__range">
            <IconifyIcon icon="lucide:calendar" class="size-3.5 shrink-0 text-[#8E8E93]" />
            <span class="crm-list__range-label">{{ dateFilter.label }}</span>
            <DatePicker.RangePicker
              v-model:value="range"
              :bordered="false"
              size="small"
              :placeholder="['开始日期', '结束日期']"
              @change="search"
            />
          </div>

          <slot name="filters"></slot>

          <!-- 勾选行时显示轻量胶囊提示 -->
          <div v-if="selectedKeys.length" class="crm-selected-pill">
            <span>已选 {{ selectedKeys.length }} 项</span>
            <button
              type="button"
              @click="
                selectedKeys = [];
                emit('select', []);
              "
            >
              清空
            </button>
          </div>

          <div class="ml-auto flex items-center gap-1">
            <Tooltip title="刷新列表">
              <button
                type="button"
                class="crm-list__icon"
                aria-label="刷新列表"
                @click="reload"
              >
                <IconifyIcon
                  icon="lucide:refresh-cw"
                  class="size-3.5"
                  :class="{ 'animate-spin': loading }"
                />
              </button>
            </Tooltip>
            <Popover trigger="click" placement="bottomRight">
              <template #content>
                <div class="w-52 p-1">
                  <div class="mb-2 flex items-center justify-between border-0 border-b border-solid border-black/[0.06] pb-1.5 text-xs">
                    <span class="font-semibold text-[#1D1D1F]">显示字段</span>
                    <button
                      type="button"
                      class="cursor-pointer border-0 bg-transparent p-0 text-xs text-[#007AFF]"
                      @click="
                        hiddenCols = props.columns
                          .filter((c) => c.hidden)
                          .map((c) => c.key)
                      "
                    >
                      恢复默认
                    </button>
                  </div>
                  <div class="max-h-72 overflow-auto">
                    <div
                      v-for="c in columns"
                      :key="c.key"
                      class="rounded-md px-1.5 py-1 hover:bg-black/[0.03]"
                    >
                      <Checkbox
                        :checked="!hiddenCols.includes(c.key)"
                        :disabled="c.required"
                        @change="
                          (e: any) =>
                            (hiddenCols = e.target.checked
                              ? hiddenCols.filter((k) => k !== c.key)
                              : [...hiddenCols, c.key])
                        "
                      >
                        <span class="text-[12.5px]">{{ c.title }}</span>
                      </Checkbox>
                    </div>
                  </div>
                </div>
              </template>
              <Tooltip title="字段显示设置">
                <button
                  type="button"
                  class="crm-list__icon"
                  aria-label="字段显示设置"
                >
                  <IconifyIcon icon="lucide:sliders-horizontal" class="size-3.5" />
                </button>
              </Tooltip>
            </Popover>
          </div>
        </div>

        <div class="crm-list__table">
          <Table
            :columns="tableColumns"
            :data-source="rows"
            :loading="loading"
            :pagination="false"
            :row-selection="rowSelection"
            :scroll="rows.length ? { x: scrollX, y: 'calc(100vh - 280px)' } : { x: scrollX }"
            row-key="id"
            size="middle"
          >
            <template #bodyCell="slotProps">
              <slot name="cell" v-bind="slotProps">{{
                slotProps.text ?? '-'
              }}</slot>
            </template>
            <template #emptyText>
              <div class="flex flex-col items-center justify-center gap-2 py-12 text-center">
                <div class="grid size-11 place-items-center rounded-2xl bg-[#F4F5F8] text-[#8E8E93]">
                  <IconifyIcon
                    :icon="loadFailed ? 'lucide:alert-circle' : 'lucide:inbox'"
                    class="size-5"
                  />
                </div>
                <div class="text-[13px] font-medium text-[#86868B]">
                  {{
                    loading
                      ? '正在加载…'
                      : loadFailed
                        ? '数据加载失败，请点击右上角刷新重试'
                        : '暂无符合条件的数据'
                  }}
                </div>
              </div>
            </template>
          </Table>
        </div>

        <div class="crm-list__pager">
          <span class="text-[12.5px] text-[#86868B] tabular-nums">
            共 <b class="font-semibold text-[#1D1D1F]">{{ total }}</b> 条记录
          </span>
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
  padding: 20px 24px 16px;
}

.crm-list__head {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 14px;
}

.crm-list__title {
  margin: 0;
  font-size: 21px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #1d1d1f;
}

.crm-list__count-badge {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  font-size: 12px;
  font-weight: 600;
  color: #6e6e73;
  background: rgb(118 118 128 / 10%);
  border-radius: 999px;
}

.crm-filter-toggle {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  height: 32px;
  padding: 0 13px;
  font-size: 12.5px;
  font-weight: 600;
  color: #3a3a3c;
  cursor: pointer;
  background: rgb(255 255 255 / 86%);
  border: 0;
  border-radius: 999px;
  box-shadow:
    0 1px 2px rgb(0 0 0 / 5%),
    inset 0 0 0 0.5px rgb(0 0 0 / 10%);
  transition: all var(--ap-dur-fast) var(--ap-ease);
}

.crm-filter-toggle:hover {
  color: #1d1d1f;
  background: #fff;
}

.crm-filter-toggle.is-open {
  color: #007aff;
  background: #e8f2ff;
  box-shadow: inset 0 0 0 0.5px rgb(0 122 255 / 22%);
}

.crm-list__body {
  display: flex;
  flex: 1;
  gap: 14px;
  min-height: 0;
}

.crm-list__rail {
  display: flex;
  flex: 0 0 198px;
  flex-direction: column;
  padding: 12px 10px;
  overflow: hidden;
  background: var(--ap-surface);
  border-radius: 18px;
  box-shadow: var(--ap-shadow-rest);
}

.crm-rail__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px 8px;
  font-size: 11.5px;
  font-weight: 600;
  color: #8e8e93;
}

.crm-rail__close {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  color: #8e8e93;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 6px;
}

.crm-rail__close:hover {
  color: #1d1d1f;
  background: rgb(0 0 0 / 5%);
}

.crm-rail__stats {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 8px 10px;
  margin: 4px 2px 8px;
  font-size: 12px;
  background: #f5f5f7;
  border-radius: 11px;
}

.crm-rail__stat-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.crm-rail__list {
  flex: 1;
  overflow: auto;
}

.crm-rail__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  min-height: 33px;
  padding: 5px 9px;
  font-size: 13px;
  font-weight: 500;
  color: #3a3a3c;
  cursor: pointer;
  border-radius: 9px;
  transition:
    background-color var(--ap-dur-fast),
    color var(--ap-dur-fast);
}

.crm-rail__item:hover:not(.on) {
  color: #1d1d1f;
  background: rgb(0 0 0 / 4%);
}

.crm-rail__item.on {
  font-weight: 600;
  color: #007aff;
  background: #e8f2ff;
}

.crm-rail__all {
  margin-bottom: 4px;
  font-weight: 600;
}

.crm-rail__child {
  padding-left: 26px;
  font-size: 12.5px;
  color: #6e6e73;
}

.crm-rail__sep {
  height: 0.5px;
  margin: 7px 6px;
  background: var(--ap-separator);
}

.crm-rail__num {
  font-size: 11.5px;
  font-weight: 500;
  color: #8e8e93;
}

.crm-rail__item.on .crm-rail__num {
  color: #007aff;
}

.crm-rail__caret {
  width: 13px;
  height: 13px;
  color: #8e8e93;
  transition: transform 180ms var(--ap-ease);
}

.crm-rail__caret.open {
  transform: rotate(90deg);
}

.crm-list__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  background: var(--ap-surface);
  border-radius: 18px;
  box-shadow: var(--ap-shadow-rest);
}

.crm-list__filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 0.5px solid var(--ap-separator);
}

.crm-search-box {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 230px;
  height: 32px;
  padding: 0 10px;
  background: #f2f2f7;
  border-radius: 10px;
  transition:
    background-color var(--ap-dur-fast),
    box-shadow var(--ap-dur-fast);
}

.crm-search-box:focus-within {
  background: #fff;
  box-shadow:
    0 0 0 2px rgb(0 122 255 / 22%),
    inset 0 0 0 0.5px rgb(0 122 255 / 45%);
}

.crm-search-box__icon {
  flex: none;
  width: 14px;
  height: 14px;
  color: #8e8e93;
}

.crm-search-box__input {
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

.crm-search-box__input::placeholder {
  color: #8e8e93;
}

.crm-search-box__input::-webkit-search-cancel-button {
  display: none;
}

.crm-search-box__clear {
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

.crm-list__range {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  height: 32px;
  padding: 0 4px 0 11px;
  background: #f2f2f7;
  border-radius: 10px;
}

.crm-list__range-label {
  padding-right: 6px;
  font-size: 12px;
  font-weight: 500;
  color: #6e6e73;
  white-space: nowrap;
  border-right: 0.5px solid rgb(0 0 0 / 10%);
}

.crm-selected-pill {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 600;
  color: #007aff;
  background: #e8f2ff;
  border-radius: 999px;
}

.crm-selected-pill button {
  padding: 0;
  font-size: 11.5px;
  font-weight: 500;
  color: #007aff;
  cursor: pointer;
  background: none;
  border: 0;
  opacity: 0.8;
}

.crm-selected-pill button:hover {
  opacity: 1;
  text-decoration: underline;
}

.crm-list__icon {
  display: inline-grid;
  place-items: center;
  width: 30px;
  height: 30px;
  color: #6e6e73;
  cursor: pointer;
  background: #f2f2f7;
  border: 0;
  border-radius: 8px;
  transition: all var(--ap-dur-fast);
}

.crm-list__icon:hover {
  color: #1d1d1f;
  background: #e5e5ea;
}

.crm-list__table {
  flex: 1;
  min-height: 0;
}

.crm-list__table :deep(.ant-table-thead > tr > th) {
  font-size: 12px;
  font-weight: 600;
  color: #86868b;
  background: #fbfbfd;
  border-bottom: 0.5px solid var(--ap-separator);
}

.crm-list__table :deep(.ant-table-tbody > tr > td) {
  font-size: 13px;
  border-bottom: 0.5px solid rgb(0 0 0 / 4.5%);
}

.crm-list__pager {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  font-size: 13px;
  border-top: 0.5px solid var(--ap-separator);
}

@media (width <= 760px) {
  .crm-list {
    padding: 12px;
  }

  .crm-search-box {
    width: 100%;
  }

  .crm-list__rail {
    position: absolute;
    top: 0;
    left: 0;
    z-index: 12;
    width: 215px;
    max-height: 100%;
    background: #fff !important;
    box-shadow: 0 12px 32px rgb(0 0 0 / 14%);
  }

  .crm-list__body {
    position: relative;
  }
}
</style>
