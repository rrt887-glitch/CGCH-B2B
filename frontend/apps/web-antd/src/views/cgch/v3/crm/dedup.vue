<script lang="ts" setup>
/** 查重：在客户与线索中查找可能重复的记录（与原网站布局一致） */
import type { CgchCrmApi } from '#/api/crm/cgch';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import { Button, message, Select, Table } from 'ant-design-vue';

import { searchDedup } from '#/api/crm/cgch';

import { crmAvatarBg, crmInitial } from './components/shared';

const router = useRouter();
const scope = ref<'all' | 'clue' | 'customer'>('all');
const field = ref('name');
const keyword = ref('');
const loading = ref(false);
const searched = ref(false);
const loadFailed = ref(false);
let searchVersion = 0;
const rows = ref<CgchCrmApi.DedupItem[]>([]);
const pageSize = ref(10);

const FIELDS = [
  { value: 'name', label: '名称或简称' },
  { value: 'email', label: '邮箱' },
  { value: 'mobile', label: '电话' },
  { value: 'website', label: '官网' },
];

const SCOPES = [
  { value: 'all', label: '全部' },
  { value: 'customer', label: '客户' },
  { value: 'clue', label: '线索' },
] as const;

const columns = [
  { title: '类型', key: 'type', width: 80 },
  { title: '编号', key: 'id', dataIndex: 'id', width: 90 },
  { title: '阶段', key: 'stage', width: 100 },
  { title: '客户名称', key: 'customerName', width: 180 },
  { title: '线索名称', key: 'clueName', width: 180 },
  { title: '公司名称', key: 'companyName', dataIndex: 'companyName', width: 200 },
  { title: '操作', key: 'action', width: 80 },
];

async function search() {
  const version = ++searchVersion;
  const k = keyword.value.trim();
  loadFailed.value = false;
  rows.value = [];
  if (k.length < 2 || k.length > 100) {
    loading.value = false;
    searched.value = false;
    message.info('请输入 2-100 个字符');
    return;
  }
  searched.value = true;
  loading.value = true;
  try {
    const result = await searchDedup(k, scope.value, field.value);
    if (version === searchVersion) rows.value = result;
  } catch {
    if (version === searchVersion) loadFailed.value = true;
  } finally {
    if (version === searchVersion) loading.value = false;
  }
}

function pickScope(v: 'all' | 'clue' | 'customer') {
  scope.value = v;
  if (searched.value) search();
}

function view(r: CgchCrmApi.DedupItem) {
  router.push({ name: r.type === 'customer' ? 'CrmCustomerDetail' : 'CrmClueDetail', params: { id: r.id } });
}
</script>

<template>
  <div class="dd">
    <div class="dd__head">
      <h2 class="dd__title">客户与线索查重</h2>
      <p class="dd__sub">
        按名称、邮箱、电话或官网在全库客户与线索中快速检索，避免同事重复跟进撞单。
      </p>
    </div>

    <div class="dd__card">
      <div class="dd__bar">
        <div class="dd__seg" role="group" aria-label="查重范围">
          <button
            v-for="s in SCOPES"
            :key="s.value"
            type="button"
            :class="{ on: scope === s.value }"
            @click="pickScope(s.value)"
          >
            {{ s.label }}
          </button>
        </div>

        <Select
          v-model:value="field"
          :bordered="false"
          class="dd__field-select w-32"
          :options="FIELDS"
          @change="searched && search()"
        />

        <div class="dd__search-box">
          <IconifyIcon icon="lucide:search" class="size-4 shrink-0 text-[#8E8E93]" />
          <input
            v-model="keyword"
            type="search"
            class="dd__search-input"
            placeholder="输入至少 2 个字符，回车查询…"
            maxlength="100"
            @keydown.enter="search"
          />
          <button
            v-if="keyword"
            type="button"
            class="dd__search-clear"
            aria-label="清空"
            @click="keyword = ''"
          >
            <IconifyIcon icon="lucide:x" class="size-3" />
          </button>
        </div>

        <Button
          type="primary"
          shape="round"
          :loading="loading"
          @click="search"
        >
          立即查重
        </Button>
      </div>

      <Table
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="{
          pageSize,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50'],
          onShowSizeChange: (_c: number, s: number) => (pageSize = s),
        }"
        :row-key="(r: any) => `${r.type}-${r.id}`"
        size="middle"
        :scroll="{ x: 900 }"
      >
        <template #bodyCell="{ column, record }">
          <span
            v-if="column.key === 'type'"
            class="ap-crm-pill"
            :class="
              record.type === 'customer'
                ? 'ap-crm-pill--blue'
                : 'ap-crm-pill--amber'
            "
          >
            {{ record.type === 'customer' ? '客户' : '线索' }}
          </span>
          <span v-else-if="column.key === 'id'" class="tabular-nums text-[#86868B]">
            #{{ record.id }}
          </span>
          <template v-else-if="column.key === 'stage'">
            <span
              class="ap-crm-pill"
              :class="record.ownerUserId ? 'ap-crm-pill--green' : 'ap-crm-pill--gray'"
            >
              {{ record.ownerUserId ? '跟进中' : '公海' }}
            </span>
          </template>
          <template v-else-if="column.key === 'customerName'">
            <div v-if="record.type === 'customer'" class="flex min-w-0 items-center gap-2">
              <span
                class="ap-crm-avatar"
                :style="{ background: crmAvatarBg(record.name || record.id) }"
                >{{ crmInitial(record.name) }}</span
              >
              <span class="truncate font-semibold text-[#1D1D1F]">{{ record.name }}</span>
            </div>
            <span v-else class="text-[#C7C7CC]">-</span>
          </template>
          <template v-else-if="column.key === 'clueName'">
            <div v-if="record.type === 'clue'" class="flex min-w-0 items-center gap-2">
              <span
                class="ap-crm-avatar"
                :style="{ background: crmAvatarBg(record.name || record.id) }"
                >{{ crmInitial(record.name) }}</span
              >
              <span class="truncate font-semibold text-[#1D1D1F]">{{ record.name }}</span>
            </div>
            <span v-else class="text-[#C7C7CC]">-</span>
          </template>
          <template v-else-if="column.key === 'companyName'">{{
            record.companyName || '-'
          }}</template>
          <a
            v-else-if="column.key === 'action'"
            class="ap-crm-link"
            @click="view(record as any)"
            >查看 ›</a
          >
        </template>
        <template #emptyText>
          <div class="flex flex-col items-center justify-center gap-2 py-14 text-center">
            <div class="grid size-12 place-items-center rounded-2xl bg-[#F4F5F8] text-[#007AFF]">
              <IconifyIcon icon="lucide:scan-search" class="size-6" />
            </div>
            <div class="text-[13.5px] font-medium text-[#1D1D1F]">
              {{
                loadFailed
                  ? '查重请求失败，请重试'
                  : searched
                    ? '未发现匹配的重复记录'
                    : '输入名称、邮箱、电话或官网开始查重'
              }}
            </div>
            <div v-if="!searched" class="text-xs text-[#86868B]">
              支持模糊匹配，快速确认客户是否已建档或归属公海
            </div>
          </div>
        </template>
      </Table>
    </div>
  </div>
</template>

<style scoped>
.dd {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 20px 24px 16px;
}

.dd__head {
  padding-bottom: 14px;
}

.dd__title {
  margin: 0;
  font-size: 21px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #1d1d1f;
}

.dd__sub {
  margin: 3px 0 0;
  font-size: 13px;
  color: #6e6e73;
}

.dd__card {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: var(--ap-surface);
  border-radius: 18px;
  box-shadow: var(--ap-shadow-rest);
}

.dd__bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 0.5px solid var(--ap-separator);
}

.dd__seg {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  background: #f2f2f7;
  border-radius: 10px;
}

.dd__seg button {
  height: 28px;
  padding: 0 12px;
  font-size: 12.5px;
  font-weight: 500;
  color: #6e6e73;
  cursor: pointer;
  background: none;
  border: 0;
  border-radius: 8px;
  transition: all var(--ap-dur-fast);
}

.dd__seg button.on {
  font-weight: 600;
  color: #1d1d1f;
  background: #fff;
  box-shadow:
    0 1px 3px rgb(0 0 0 / 8%),
    0 0 0 0.5px rgb(0 0 0 / 4%);
}

.dd__field-select :deep(.ant-select-selector) {
  height: 34px !important;
  padding-inline: 11px !important;
  background: #f2f2f7 !important;
  border-radius: 10px !important;
}

.dd__field-select :deep(.ant-select-selection-item) {
  line-height: 32px !important;
  font-size: 12.5px;
  font-weight: 500;
}

.dd__search-box {
  display: flex;
  flex: 1;
  gap: 7px;
  align-items: center;
  min-width: 220px;
  max-width: 360px;
  height: 34px;
  padding: 0 11px;
  background: #f2f2f7;
  border-radius: 10px;
  transition: all var(--ap-dur-fast);
}

.dd__search-box:focus-within {
  background: #fff;
  box-shadow:
    0 0 0 2px rgb(0 122 255 / 22%),
    inset 0 0 0 0.5px rgb(0 122 255 / 45%);
}

.dd__search-input {
  flex: 1;
  min-width: 0;
  height: 100%;
  padding: 0;
  font-size: 13px;
  color: #1d1d1f;
  background: transparent;
  border: 0;
  outline: none;
}

.dd__search-input::-webkit-search-cancel-button {
  display: none;
}

.dd__search-clear {
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
</style>
