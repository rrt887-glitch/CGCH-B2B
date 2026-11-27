<script lang="ts" setup>
/**
 * 📥 灵动待甄别来信抽屉（Smart Triage Inbox）：
 * - 承接 AI 初筛判定为「待确认采购意向」或「疑似推销/通知」的渠道来信及历史未转化线索
 * - 一键「✓ 立项进新询盘」（转为正式客户并置为 ① 新询盘），或「✕ 忽略删除」
 * - 若没有任何待甄别记录，顶部只显示轻量胶囊，不打断业务员主工作流
 */
import { computed, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Drawer, Empty, Input, message, Popconfirm, Spin } from 'ant-design-vue';
import dayjs from 'dayjs';

import { deleteClue, getCluePage, transformClue } from '#/api/crm/clue';
import { updateCustomerStage } from '#/api/crm/customer';
import {
  countryName,
  crmAvatarBg,
  crmInitial,
  sourceLabel,
} from '#/views/cgch/v3/crm/components/shared';

import {
  countryFlag,
  getBuyerLocalClock,
  parseDealBoard,
} from './deal-meta';

const props = defineProps<{
  open: boolean;
}>();

const emit = defineEmits<{
  'count-change': [count: number];
  promoted: [customerId?: number];
  'update:open': [val: boolean];
}>();

const loading = ref(false);
const actingId = ref<null | number>(null);
const batchActing = ref(false);
const keyword = ref('');
const rawList = ref<any[]>([]);

const untransformed = computed(() =>
  rawList.value.filter((item) => !item.transformStatus),
);

const filteredList = computed(() => {
  const q = keyword.value.trim().toLowerCase();
  if (!q) return untransformed.value;
  return untransformed.value.filter((item) =>
    [item.name, item.companyName, item.email, item.whatsapp, item.remark]
      .filter(Boolean)
      .some((v) => String(v).toLowerCase().includes(q)),
  );
});

async function loadTriage() {
  loading.value = true;
  try {
    const res: any = await getCluePage({
      pageNo: 1,
      pageSize: 100,
      transformStatus: false,
    } as any);
    const list = (res?.list ?? []).filter((r: any) => !r.transformStatus);
    rawList.value = list;
    emit('count-change', list.length);
  } catch {
    rawList.value = [];
  } finally {
    loading.value = false;
  }
}

async function handlePromote(clue: any) {
  actingId.value = clue.id;
  try {
    const newId: any = await transformClue(clue.id);
    if (typeof newId === 'number' && newId > 0) {
      await updateCustomerStage(newId, 1).catch(() => undefined);
    }
    rawList.value = rawList.value.filter((c) => c.id !== clue.id);
    emit('count-change', untransformed.value.length);
    message.success(`已将「${clue.name}」立项至 ① 新询盘`);
    emit('promoted', typeof newId === 'number' ? newId : undefined);
  } finally {
    actingId.value = null;
  }
}

async function handleIgnore(clue: any) {
  actingId.value = clue.id;
  try {
    await deleteClue(clue.id);
    rawList.value = rawList.value.filter((c) => c.id !== clue.id);
    emit('count-change', untransformed.value.length);
    message.success('已忽略并移除该条来信');
  } finally {
    actingId.value = null;
  }
}

async function handlePromoteAll() {
  const targets = [...filteredList.value];
  if (!targets.length) return;
  batchActing.value = true;
  let ok = 0;
  try {
    for (const c of targets) {
      try {
        const cid: any = await transformClue(c.id);
        if (typeof cid === 'number' && cid > 0) {
          await updateCustomerStage(cid, 1).catch(() => undefined);
        }
        ok++;
      } catch {
        // 忽略单条失败
      }
    }
    message.success(`已批量立项 ${ok} 条记录进入「新询盘」`);
    await loadTriage();
    emit('promoted');
  } finally {
    batchActing.value = false;
  }
}

function fmtRelative(v?: string) {
  if (!v) return '';
  const d = dayjs(v);
  if (!d.isValid()) return '';
  const hours = dayjs().diff(d, 'hour');
  if (hours < 1) return '刚刚';
  if (hours < 24) return `${hours} 小时前`;
  const days = dayjs().diff(d, 'day');
  if (days < 30) return `${days} 天前`;
  return d.format('MM-DD');
}

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) void loadTriage();
  },
);

defineExpose({ reload: loadTriage });
</script>

<template>
  <Drawer
    :open="open"
    :width="540"
    placement="right"
    :closable="false"
    root-class-name="triage-drawer-root"
    @close="emit('update:open', false)"
  >
    <div class="td-wrap">
      <!-- 顶部栏 -->
      <header class="td-head">
        <div class="flex items-center gap-2.5">
          <span class="td-icon-badge">
            <IconifyIcon icon="lucide:inbox" class="size-4" />
          </span>
          <div>
            <div class="flex items-center gap-2">
              <h3 class="m-0 text-[16px] font-bold tracking-tight text-[#1D1D1F]">
                待甄别来信
              </h3>
              <span class="td-count-pill">{{ untransformed.length }}</span>
            </div>
            <p class="m-0 text-[12px] text-[#86868B]">
              明确询盘已由 AI 自动建档入「新询盘」，此处仅保留意向不明来信与历史线索
            </p>
          </div>
        </div>
        <button
          type="button"
          class="td-close-btn"
          title="关闭"
          @click="emit('update:open', false)"
        >
          <IconifyIcon icon="lucide:x" class="size-4" />
        </button>
      </header>

      <!-- 搜索与一键批量处理 -->
      <div class="td-toolbar">
        <Input
          v-model:value="keyword"
          allow-clear
          placeholder="搜索发件人、邮箱、WhatsApp 或内容…"
          class="flex-1"
        >
          <template #prefix>
            <IconifyIcon icon="lucide:search" class="size-3.5 text-[#86868B]" />
          </template>
        </Input>
        <Popconfirm
          v-if="filteredList.length > 1"
          :title="`确定将当前 ${filteredList.length} 条来信全部立项为「新询盘」客户吗？`"
          ok-text="全部立项"
          cancel-text="取消"
          @confirm="handlePromoteAll"
        >
          <button type="button" class="td-batch-btn" :disabled="batchActing">
            <IconifyIcon icon="lucide:check-check" class="size-3.5" />
            <span>全部立项 ({{ filteredList.length }})</span>
          </button>
        </Popconfirm>
      </div>

      <!-- 列表主体 -->
      <Spin :spinning="loading" wrapper-class-name="td-spin">
        <div v-if="!loading && filteredList.length === 0" class="td-empty">
          <Empty :image="Empty.PRESENTED_IMAGE_SIMPLE" description="太棒了，当前没有待甄别来信">
            <p class="m-0 text-[12px] text-[#86868B]">
              来自邮件、WhatsApp 与社媒的明确采购询盘会自动进入「① 新询盘」管道
            </p>
          </Empty>
        </div>

        <div v-else class="td-list">
          <div
            v-for="item in filteredList"
            :key="item.id"
            class="td-card"
          >
            <div class="flex items-start gap-3">
              <span
                class="td-avatar"
                :style="{ background: crmAvatarBg(item.name || item.id) }"
              >
                {{ crmInitial(item.name) }}
              </span>

              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-1.5">
                  <span class="truncate text-[14px] font-semibold text-[#1D1D1F]">
                    {{ item.name || '未知来信人' }}
                  </span>
                  <span
                    v-if="item.countryCode"
                    class="td-meta-chip"
                    :title="countryName(item.countryCode)"
                  >
                    {{ countryFlag(item.countryCode) }} {{ countryName(item.countryCode) }}
                    <template v-if="getBuyerLocalClock(item.countryCode)">
                      · {{ getBuyerLocalClock(item.countryCode)?.icon }}
                      {{ getBuyerLocalClock(item.countryCode)?.time }}
                    </template>
                  </span>
                  <span v-if="item.source" class="td-source-chip">
                    {{ sourceLabel(item.source) }}
                  </span>
                  <span class="ml-auto text-[11px] text-[#86868B]">
                    {{ fmtRelative(item.createTime) }}
                  </span>
                </div>

                <div class="mt-0.5 flex flex-wrap items-center gap-2 text-[12px] text-[#6E6E73]">
                  <span v-if="item.companyName" class="font-medium text-[#3A3A3C]">
                    {{ item.companyName }}
                  </span>
                  <span v-if="item.email" class="truncate">{{ item.email }}</span>
                  <span v-if="item.whatsapp">{{ item.whatsapp }}</span>
                </div>

                <!-- AI 初筛标签 & 同企防撞单提示 -->
                <div class="mt-2 flex flex-wrap items-center gap-1.5">
                  <span
                    v-if="parseDealBoard(item.remark).screeningTag === 'spam'"
                    class="td-ai-tag td-ai-tag--warn"
                  >
                    <IconifyIcon icon="lucide:shield-alert" class="size-3" />
                    AI 初筛：疑似推销 / 系统通知
                  </span>
                  <span v-else class="td-ai-tag td-ai-tag--info">
                    <IconifyIcon icon="lucide:sparkles" class="size-3" />
                    AI 初筛：待人工确认采购意向
                  </span>

                  <span
                    v-if="parseDealBoard(item.remark).collisionHint"
                    class="td-ai-tag td-ai-tag--collision"
                  >
                    <IconifyIcon icon="lucide:users-round" class="size-3" />
                    {{ parseDealBoard(item.remark).collisionHint }}
                  </span>
                </div>

                <!-- 来信摘要 / 备注 -->
                <div
                  v-if="item.contactLastContent || parseDealBoard(item.remark).note"
                  class="td-snippet"
                >
                  {{ item.contactLastContent || parseDealBoard(item.remark).note }}
                </div>

                <!-- 操作栏 -->
                <div class="mt-2.5 flex items-center justify-end gap-2">
                  <Popconfirm
                    title="确定忽略并删除这条来信吗？"
                    ok-text="删除"
                    cancel-text="取消"
                    @confirm="handleIgnore(item)"
                  >
                    <button
                      type="button"
                      class="td-btn td-btn--ghost"
                      :disabled="actingId === item.id"
                    >
                      <IconifyIcon icon="lucide:x" class="size-3.5" />
                      <span>忽略删除</span>
                    </button>
                  </Popconfirm>

                  <button
                    type="button"
                    class="td-btn td-btn--primary"
                    :disabled="actingId === item.id"
                    @click="handlePromote(item)"
                  >
                    <IconifyIcon icon="lucide:arrow-up-right" class="size-3.5" />
                    <span>立项进「新询盘」</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </Spin>
    </div>
  </Drawer>
</template>

<style scoped>
.td-wrap {
  display: flex;
  flex-direction: column;
  height: 100%;
  margin: -24px;
  background: #f5f5f7;
}

.td-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 18px 20px 14px;
  background: rgb(255 255 255 / 86%);
  backdrop-filter: blur(20px);
  border-bottom: 0.5px solid rgb(60 60 67 / 12%);
}

.td-icon-badge {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: #fff;
  background: linear-gradient(180deg, #ffab2e 0%, #ff9500 100%);
  border-radius: 10px;
  box-shadow: 0 4px 10px rgb(255 149 0 / 24%);
}

.td-count-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 20px;
  padding: 0 7px;
  font-size: 11px;
  font-weight: 700;
  color: #b25000;
  background: #fff3e0;
  border-radius: 999px;
}

.td-close-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: #6e6e73;
  cursor: pointer;
  background: rgb(118 118 128 / 10%);
  border: none;
  border-radius: 999px;
  transition: all 0.16s ease;
}

.td-close-btn:hover {
  color: #1d1d1f;
  background: rgb(118 118 128 / 18%);
}

.td-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 12px 20px;
  background: rgb(255 255 255 / 65%);
  border-bottom: 0.5px solid rgb(60 60 67 / 8%);
}

.td-batch-btn {
  display: inline-flex;
  flex-shrink: 0;
  gap: 5px;
  align-items: center;
  height: 32px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 600;
  color: #0071e3;
  cursor: pointer;
  background: #e8f2ff;
  border: none;
  border-radius: 9px;
  transition: all 0.16s ease;
}

.td-batch-btn:hover {
  background: #d6e8ff;
}

.td-spin {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.td-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 64px 24px;
}

.td-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 20px 24px;
}

.td-card {
  padding: 14px 15px;
  background: #fff;
  border: 0.5px solid rgb(60 60 67 / 10%);
  border-radius: 14px;
  box-shadow: 0 1px 3px rgb(0 0 0 / 4%);
  transition: box-shadow 0.18s ease, transform 0.18s ease;
}

.td-card:hover {
  box-shadow: 0 6px 18px rgb(0 0 0 / 6%);
}

.td-avatar {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  font-size: 14px;
  font-weight: 700;
  color: #fff;
  border-radius: 10px;
}

.td-meta-chip {
  display: inline-flex;
  gap: 3px;
  align-items: center;
  padding: 1px 7px;
  font-size: 11px;
  color: #3a3a3c;
  background: #f2f2f7;
  border-radius: 6px;
}

.td-source-chip {
  padding: 1px 7px;
  font-size: 11px;
  font-weight: 500;
  color: #0071e3;
  background: #e8f2ff;
  border-radius: 6px;
}

.td-ai-tag {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 500;
  border-radius: 6px;
}

.td-ai-tag--info {
  color: #5856d6;
  background: #ededfc;
}

.td-ai-tag--warn {
  color: #b25000;
  background: #fff4e0;
}

.td-ai-tag--collision {
  color: #c93400;
  background: #ffece6;
}

.td-snippet {
  display: -webkit-box;
  padding: 8px 10px;
  margin-top: 8px;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.5;
  color: #48484a;
  background: #f8f8fa;
  border-radius: 8px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.td-btn {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  height: 28px;
  padding: 0 11px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  border: none;
  border-radius: 8px;
  transition: all 0.16s ease;
}

.td-btn--ghost {
  color: #6e6e73;
  background: #f2f2f7;
}

.td-btn--ghost:hover {
  color: #d70015;
  background: #ffeceb;
}

.td-btn--primary {
  color: #fff;
  background: #0071e3;
  box-shadow: 0 2px 6px rgb(0 113 227 / 24%);
}

.td-btn--primary:hover {
  background: #0077ed;
}
</style>
