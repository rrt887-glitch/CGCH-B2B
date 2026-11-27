<script lang="ts" setup>
/**
 * 🌊 公海淘金与防撞单查重面板（Public Lead Pool & Anti-Collision Dedup）：
 * - 集中展示超期未跟进释放或手工退回公海的客户资产，透视历史预估单值、意向产品与当地时区
 * - 支持业务员一键认领回自己名下、批量认领、主管定向分配
 * - 内置「全库防撞单查重」滑块，输入公司/邮箱/域名/WhatsApp 即刻排查撞单归属
 */
import type { CgchCrmApi } from '#/api/crm/cgch';

import { computed, onMounted, reactive, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import {
  Empty,
  Input,
  message,
  Modal,
  Select,
  Spin,
  Table,
} from 'ant-design-vue';
import dayjs from 'dayjs';

import { searchDedup } from '#/api/crm/cgch';
import {
  distributeCustomer,
  getCustomerPage,
  receiveCustomer,
} from '#/api/crm/customer';
import { getSimpleUserList } from '#/api/system/user';
import { COUNTRY_OPTIONS } from '#/views/cgch/crm/shared/countries';
import {
  countryName,
  crmAvatarBg,
  crmInitial,
  levelLabel,
  sourceLabel,
} from '#/views/cgch/v3/crm/components/shared';

import {
  countryFlag,
  formatDealMoney,
  getBuyerLocalClock,
  parseDealBoard,
} from './deal-meta';

const emit = defineEmits<{
  claimed: [];
  inspect: [customerId: number];
}>();

const mode = ref<'dedup' | 'pool'>('pool');
const loading = ref(false);
const rows = ref<any[]>([]);
const total = ref(0);
const keyword = ref('');
const countryCode = ref<string | undefined>();
const pager = reactive({ no: 1, size: 20 });
const selectedIds = ref<number[]>([]);

// 主管分配弹窗
const distOpen = ref(false);
const distOwnerId = ref<number | undefined>();
const distSubmitting = ref(false);
const users = ref<{ id: number; nickname: string }[]>([]);

// 防撞单查重状态
const dedupKeyword = ref('');
const dedupLoading = ref(false);
const dedupSearched = ref(false);
const dedupRows = ref<CgchCrmApi.DedupItem[]>([]);

const FIELD_MAP: Record<string, string> = {
  name: '姓名',
  companyName: '公司名',
  email: '邮箱',
  mobile: '手机',
  telephone: '电话',
  website: '官网域名',
  whatsapp: 'WhatsApp',
};

const poolColumns = [
  { title: '海外买家 / 公司', key: 'buyer' },
  { title: '国家与当地时区', key: 'country', width: 185 },
  { title: '历史商务黑板（产品 / 报价）', key: 'board', width: 250 },
  { title: '等级 / 来源', key: 'meta', width: 140 },
  { title: '上次联系', key: 'lastTime', width: 130 },
  { title: '操作', key: 'action', width: 150, align: 'right' as const },
];

const dedupColumns = [
  { title: '档案类型', key: 'type', width: 95 },
  { title: '客户 / 线索名称', key: 'name' },
  { title: '公司与联系方式', key: 'contact' },
  { title: '国家', key: 'country', width: 120 },
  { title: '当前归属状态', key: 'owner', width: 120 },
  { title: '命中重复字段', key: 'matched', width: 180 },
  { title: '操作', key: 'action', width: 90, align: 'right' as const },
];

async function loadPool() {
  loading.value = true;
  try {
    const res: any = await getCustomerPage({
      pageNo: pager.no,
      pageSize: pager.size,
      pool: true,
      name: keyword.value.trim() || undefined,
      countryCode: countryCode.value || undefined,
    } as any);
    rows.value = res?.list ?? [];
    total.value = res?.total ?? 0;
  } finally {
    loading.value = false;
  }
}

async function handleClaim(ids: number[]) {
  if (!ids.length) return;
  await receiveCustomer(ids);
  message.success(`成功认领 ${ids.length} 位客户，已加入您的跟进管道`);
  selectedIds.value = [];
  await loadPool();
  emit('claimed');
}

async function openDistribute(ids: number[]) {
  if (!ids.length) return;
  selectedIds.value = ids;
  if (!users.value.length) {
    users.value = ((await getSimpleUserList().catch(() => [])) as any[]) || [];
  }
  distOwnerId.value = undefined;
  distOpen.value = true;
}

async function confirmDistribute() {
  if (!distOwnerId.value) {
    message.warning('请选择分配给哪位负责人');
    return;
  }
  distSubmitting.value = true;
  try {
    await distributeCustomer(selectedIds.value, distOwnerId.value);
    message.success(`已分配 ${selectedIds.value.length} 位公海客户`);
    distOpen.value = false;
    selectedIds.value = [];
    await loadPool();
    emit('claimed');
  } finally {
    distSubmitting.value = false;
  }
}

async function runDedup() {
  const q = dedupKeyword.value.trim();
  if (q.length < 2) {
    message.warning('请输入至少 2 个字符（如公司关键字、邮箱域名或号码）');
    return;
  }
  dedupLoading.value = true;
  try {
    dedupRows.value = await searchDedup(q, 'all');
    dedupSearched.value = true;
  } finally {
    dedupLoading.value = false;
  }
}

const hasSelection = computed(() => selectedIds.value.length > 0);

onMounted(() => {
  void loadPool();
});

defineExpose({ reload: loadPool });
</script>

<template>
  <div class="pp-wrap">
    <!-- 顶部子模式切换与筛选 -->
    <div class="pp-bar">
      <div class="pp-seg">
        <button
          type="button"
          class="pp-seg-btn"
          :class="{ on: mode === 'pool' }"
          @click="mode = 'pool'"
        >
          <IconifyIcon icon="lucide:globe" class="size-3.5" />
          <span>公海客户池 ({{ total }})</span>
        </button>
        <button
          type="button"
          class="pp-seg-btn"
          :class="{ on: mode === 'dedup' }"
          @click="mode = 'dedup'"
        >
          <IconifyIcon icon="lucide:radar" class="size-3.5" />
          <span>全库防撞单查重</span>
        </button>
      </div>

      <template v-if="mode === 'pool'">
        <Input
          v-model:value="keyword"
          allow-clear
          placeholder="搜索公海客户名 / 公司…"
          class="w-60"
          @press-enter="() => { pager.no = 1; loadPool(); }"
        >
          <template #prefix>
            <IconifyIcon icon="lucide:search" class="size-3.5 text-[#86868B]" />
          </template>
        </Input>
        <Select
          v-model:value="countryCode"
          allow-clear
          show-search
          option-filter-prop="label"
          placeholder="全部国家/地区"
          :options="COUNTRY_OPTIONS"
          class="w-44"
          @change="() => { pager.no = 1; loadPool(); }"
        />
        <button
          type="button"
          class="pp-btn"
          @click="() => { pager.no = 1; loadPool(); }"
        >
          刷新
        </button>

        <div v-if="hasSelection" class="ml-auto flex items-center gap-2">
          <span class="text-[12px] font-semibold text-[#0071E3]">
            已选 {{ selectedIds.length }} 位
          </span>
          <button
            type="button"
            class="pp-btn pp-btn--primary"
            @click="handleClaim(selectedIds)"
          >
            批量认领
          </button>
          <button
            type="button"
            class="pp-btn"
            @click="openDistribute(selectedIds)"
          >
            指定分配
          </button>
        </div>
      </template>

      <template v-else>
        <Input
          v-model:value="dedupKeyword"
          allow-clear
          placeholder="输入公司名、企业邮箱域名（如 acme.com）、WhatsApp 或电话查撞单…"
          class="w-96"
          @press-enter="runDedup"
        />
        <button
          type="button"
          class="pp-btn pp-btn--primary"
          :disabled="dedupLoading"
          @click="runDedup"
        >
          <IconifyIcon icon="lucide:search" class="size-3.5" />
          <span>立即查重</span>
        </button>
      </template>
    </div>

    <!-- 公海列表 -->
    <div v-if="mode === 'pool'" class="pp-card">
      <Spin :spinning="loading">
        <Table
          :columns="poolColumns"
          :data-source="rows"
          :row-key="(r: any) => r.id"
          :row-selection="{
            selectedRowKeys: selectedIds,
            onChange: (keys: any[]) => { selectedIds = keys.map(Number); },
          }"
          :pagination="{
            current: pager.no,
            pageSize: pager.size,
            total,
            showSizeChanger: false,
            onChange: (p: number) => { pager.no = p; loadPool(); },
          }"
          size="middle"
        >
          <template #emptyText>
            <Empty
              :image="Empty.PRESENTED_IMAGE_SIMPLE"
              description="当前公海池暂无待认领客户"
            />
          </template>
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'buyer'">
              <div
                class="flex cursor-pointer items-center gap-2.5"
                @click="emit('inspect', record.id)"
              >
                <span
                  class="pp-avatar"
                  :style="{ background: crmAvatarBg(record.name || record.id) }"
                >
                  {{ crmInitial(record.name) }}
                </span>
                <div class="min-w-0">
                  <div class="truncate text-[13.5px] font-semibold text-[#1D1D1F] hover:text-[#0071E3]">
                    {{ record.name }}
                  </div>
                  <div class="truncate text-[11.5px] text-[#6E6E73]">
                    {{ record.companyName || record.email || record.whatsapp || '点击查看历史沟通详情' }}
                  </div>
                </div>
              </div>
            </template>

            <template v-else-if="column.key === 'country'">
              <div v-if="record.countryCode" class="text-[12.5px] text-[#3A3A3C]">
                <span>{{ countryFlag(record.countryCode) }} {{ countryName(record.countryCode) }}</span>
                <div
                  v-if="getBuyerLocalClock(record.countryCode)"
                  class="text-[11px] text-[#86868B]"
                >
                  {{ getBuyerLocalClock(record.countryCode)?.icon }}
                  当地 {{ getBuyerLocalClock(record.countryCode)?.time }}
                </div>
              </div>
              <span v-else class="text-[#C7C7CC]">—</span>
            </template>

            <template v-else-if="column.key === 'board'">
              <div class="text-[12px]">
                <span
                  v-if="parseDealBoard(record.remark).amount"
                  class="mr-1.5 font-bold text-[#0071E3]"
                >
                  {{
                    formatDealMoney(
                      parseDealBoard(record.remark).amount,
                      parseDealBoard(record.remark).currency,
                    )
                  }}
                </span>
                <span class="text-[#3A3A3C]">
                  {{
                    parseDealBoard(record.remark).productQty ||
                    parseDealBoard(record.remark).note ||
                    record.contactLastContent ||
                    '暂无需求摘要'
                  }}
                </span>
              </div>
            </template>

            <template v-else-if="column.key === 'meta'">
              <div class="text-[12px] text-[#3A3A3C]">{{ levelLabel(record.level) }}</div>
              <div class="text-[11px] text-[#86868B]">{{ sourceLabel(record.source) }}</div>
            </template>

            <template v-else-if="column.key === 'lastTime'">
              <span class="text-[12px] text-[#6E6E73]">
                {{ record.contactLastTime ? dayjs(record.contactLastTime).format('YYYY-MM-DD') : '—' }}
              </span>
            </template>

            <template v-else-if="column.key === 'action'">
              <div class="flex justify-end gap-1.5">
                <button
                  type="button"
                  class="pp-btn pp-btn--primary"
                  @click.stop="handleClaim([record.id])"
                >
                  认领跟进
                </button>
                <button
                  type="button"
                  class="pp-btn"
                  @click.stop="openDistribute([record.id])"
                >
                  分配
                </button>
              </div>
            </template>
          </template>
        </Table>
      </Spin>
    </div>

    <!-- 防撞单查重结果 -->
    <div v-else class="pp-card">
      <Table
        :columns="dedupColumns"
        :data-source="dedupRows"
        :loading="dedupLoading"
        :pagination="false"
        :row-key="(r: any) => `${r.type}-${r.id}`"
        size="middle"
      >
        <template #emptyText>
          <Empty
            :image="Empty.PRESENTED_IMAGE_SIMPLE"
            :description="
              dedupSearched
                ? '未发现撞单记录，可放心建档跟进'
                : '输入公司名称、企业邮箱域名或 WhatsApp 号码排查是否已有同事在跟进'
            "
          />
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'type'">
            <span
              class="rounded-md px-2 py-0.5 text-[11px] font-semibold"
              :class="
                record.type === 'customer'
                  ? 'bg-[#E8F2FF] text-[#0071E3]'
                  : 'bg-[#FFF4E0] text-[#B25000]'
              "
            >
              {{ record.type === 'customer' ? '正式客户' : '待甄别来信' }}
            </span>
          </template>
          <template v-else-if="column.key === 'name'">
            <span class="font-semibold text-[#1D1D1F]">{{ record.name }}</span>
          </template>
          <template v-else-if="column.key === 'contact'">
            <div class="text-[12px] text-[#3A3A3C]">{{ record.companyName || '—' }}</div>
            <div class="text-[11px] text-[#86868B]">{{ record.email || record.mobile || '' }}</div>
          </template>
          <template v-else-if="column.key === 'country'">
            <span v-if="record.countryCode">
              {{ countryFlag(record.countryCode) }} {{ countryName(record.countryCode) }}
            </span>
            <span v-else>—</span>
          </template>
          <template v-else-if="column.key === 'owner'">
            <span
              class="rounded-full px-2.5 py-0.5 text-[11px] font-semibold"
              :class="
                record.ownerUserId
                  ? 'bg-[#FFECEB] text-[#D70015]'
                  : 'bg-[#EAF9EE] text-[#248A3D]'
              "
            >
              {{ record.ownerUserId ? '已有同事跟进' : '公海可认领' }}
            </span>
          </template>
          <template v-else-if="column.key === 'matched'">
            <div class="flex flex-wrap gap-1">
              <span
                v-for="f in record.matchedFields"
                :key="f"
                class="rounded bg-[#FFF2ED] px-1.5 py-0.5 text-[11px] font-medium text-[#C93400]"
              >
                {{ FIELD_MAP[f] || f }}
              </span>
            </div>
          </template>
          <template v-else-if="column.key === 'action'">
            <button
              v-if="record.type === 'customer'"
              type="button"
              class="pp-btn"
              @click="emit('inspect', record.id)"
            >
              查看
            </button>
          </template>
        </template>
      </Table>
    </div>

    <Modal
      v-model:open="distOpen"
      title="分配公海客户"
      :width="420"
      centered
      :confirm-loading="distSubmitting"
      ok-text="确认分配"
      cancel-text="取消"
      wrap-class-name="ap-modal-sheet"
      @ok="confirmDistribute"
    >
      <div class="py-2">
        <p class="mb-2 text-[12.5px] text-[#6E6E73]">
          将选中的 {{ selectedIds.length }} 位公海客户指定分配给：
        </p>
        <Select
          v-model:value="distOwnerId"
          show-search
          option-filter-prop="label"
          placeholder="选择业务负责人"
          :options="users.map((u) => ({ label: u.nickname, value: u.id }))"
          class="w-full"
        />
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.pp-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.pp-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.pp-seg {
  display: inline-flex;
  gap: 3px;
  padding: 3px;
  background: rgb(118 118 128 / 10%);
  border-radius: 10px;
}

.pp-seg-btn {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 29px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 600;
  color: #6e6e73;
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: 7px;
}

.pp-seg-btn.on {
  color: #1d1d1f;
  background: #fff;
  box-shadow: 0 1px 3px rgb(0 0 0 / 8%);
}

.pp-card {
  overflow: hidden;
  background: #fff;
  border: 0.5px solid rgb(60 60 67 / 10%);
  border-radius: 16px;
  box-shadow: 0 1px 3px rgb(0 0 0 / 4%);
}

.pp-avatar {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  border-radius: 10px;
}

.pp-btn {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  height: 30px;
  padding: 0 11px;
  font-size: 12px;
  font-weight: 600;
  color: #3a3a3c;
  cursor: pointer;
  background: #f2f2f7;
  border: none;
  border-radius: 8px;
  transition: all 0.15s ease;
}

.pp-btn:hover {
  background: #e5e5ea;
}

.pp-btn--primary {
  color: #fff;
  background: #0071e3;
}

.pp-btn--primary:hover {
  background: #0077ed;
}
</style>
