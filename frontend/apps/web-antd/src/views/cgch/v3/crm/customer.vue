<script lang="ts" setup>
/**
 * 客户列表 / 公海客户：左侧按特别关注、客户级别、客户分组、客户标签筛选；
 * 一键唤醒、一键背调交给 CRM 智枢处理选中的客户。
 */
import type { CrmColumn, RailItem } from './components/types';

import type { CgchCrmApi } from '#/api/crm/cgch';

import { computed, inject, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useVbenModal } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';
import { useUserStore } from '@vben/stores';
import { downloadFileFromBlobPart } from '@vben/utils';

import { Button, message, Popconfirm, Select, Tooltip } from 'ant-design-vue';

import { getCustomerGroupList, getTagList } from '#/api/crm/cgch';
import {
  deleteCustomer,
  exportCustomer,
  getCustomerPage,
  putCustomerPool,
  receiveCustomer,
} from '#/api/crm/customer';
import { createReactivateTask } from '#/api/channel/reactivate';
import { batchDraftCustomers } from '#/api/channel/mail-batch';
import type { LatestResearch } from '#/api/channel/research';
import { createResearch, getLatestResearch } from '#/api/channel/research';
import { requestClient } from '#/api/request';
import CustomerForm from '#/views/crm/customer/modules/form.vue';
import ImportForm from '#/views/crm/customer/modules/import-form.vue';

import CrmList from './components/crm-list.vue';
import { crmDetailPath, openCrmDetail } from './crm-detail-nav';
import {
  channelTags,
  countryName,
  crmAvatarBg,
  crmInitial,
  fmtTime,
  industryLabel,
  levelLabel,
  levelRail,
  page,
  sourceLabel,
  stageLabel,
  stageOptions,
} from './components/shared';

const props = defineProps<{ pool?: boolean }>();
const router = useRouter();
const userStore = useUserStore();
const hubSelect = inject<(k: string, extra?: Record<string, string>) => void>('cgchHubSelect', () => {});
const listRef = ref<InstanceType<typeof CrmList>>();
const selected = ref<any[]>([]);

const groups = ref<CgchCrmApi.TreeNode[]>([]);
const tags = ref<CgchCrmApi.TreeNode[]>([]);
const tagName = (id: number) => tags.value.find((t) => t.id === id)?.name;

const rail = computed<RailItem[]>(() =>
  props.pool
    ? []
    : [
        { key: 'star', label: '特别关注', icon: 'mdi:star', filter: { starred: true } },
        {
          key: 'stage',
          label: '客户阶段',
          children: stageOptions().map((o) => ({
            key: `stage-${o.value}`,
            label: stageCount.value[o.value] ? `${o.label} (${stageCount.value[o.value]})` : o.label,
            filter: { stage: o.value },
          })),
        },
        { key: 'level', label: '客户级别', children: levelRail() },
        {
          key: 'group',
          label: '客户分组',
          divider: true,
          children: groups.value.map((g) => ({ key: `group-${g.id}`, label: g.name, filter: { groupId: g.id } })),
        },
        {
          key: 'tag',
          label: '客户标签',
          divider: true,
          children: tags.value.map((t) => ({ key: `tag-${t.id}`, label: t.name, filter: { tagId: t.id } })),
        },
      ],
);

/** 没有数据来源的列默认隐藏，可在「表格设置」里打开 */
const columns = computed<CrmColumn[]>(() => [
  { key: 'name', title: props.pool ? '客户名称' : '客户名称(背调名称)', width: 200, fixed: 'left', required: true },
  ...(props.pool ? [{ key: 'companyName', title: '公司名称', width: 180 }] : []),
  ...(props.pool ? [] : [{ key: 'aiHosted', title: 'AI托管状态', width: 110 }, { key: 'channel', title: '渠道状态', width: 140 }]),
  ...(props.pool ? [] : [{ key: 'stage', title: '客户阶段', width: 120 }]),
  { key: 'source', title: '客户来源', width: 110 },
  { key: 'dealStatus', title: '成交状态', width: 100 },
  ...(props.pool ? [] : [{ key: 'ownerUserName', title: '负责人', width: 100 }]),
  { key: 'contact', title: '主要联系人', width: 120, hidden: true },
  { key: 'mobile', title: '联系电话', width: 140 },
  { key: 'wechat', title: '微信号', width: 120 },
  { key: 'whatsapp', title: 'whatsapp', width: 150 },
  { key: 'facebook', title: 'Facebook', width: 120, hidden: true },
  { key: 'instagram', title: 'Instagram', width: 120, hidden: true },
  { key: 'alibaba', title: '阿里TM', width: 110, hidden: true },
  { key: 'mic', title: 'MIC国际站', width: 110, hidden: true },
  { key: 'email', title: '邮箱', width: 200 },
  { key: 'countryCode', title: '国家', width: 100 },
  { key: 'industryId', title: '客户行业', width: 110 },
  { key: 'level', title: '客户级别', width: 100 },
  { key: 'tags', title: '标签', width: 160 },
  { key: 'detailAddress', title: '详细地址', width: 200 },
  { key: 'contactNextTime', title: '下次联系时间', width: 150 },
  { key: 'contactLastTime', title: '最近联系时间', width: 150 },
  { key: 'research', title: '背调结果', width: 180 },
  { key: 'report', title: '报告', width: 80 },
  { key: 'remark', title: '备注', width: 160 },
  { key: 'action', title: '操作', width: props.pool ? 120 : 190, fixed: 'right', required: true },
]);

const fetch = (p: Record<string, any>) => page(getCustomerPage({ ...p } as any));

const [FormModal, formApi] = useVbenModal({ connectedComponent: CustomerForm, destroyOnClose: true });
const [ImportModal, importApi] = useVbenModal({ connectedComponent: ImportForm, destroyOnClose: true });

function detail(r: any, event?: MouseEvent) {
  // ctrl / ⌘ / shift + 点击交给浏览器（新标签页）
  if (event && (event.ctrlKey || event.metaKey || event.shiftKey)) return;
  event?.preventDefault();
  void openCrmDetail(router, crmDetailPath('customer', r.id), (href) => {
    console.warn('[crm] SPA 跳转客户详情未生效，改用整页打开', href);
    window.location.assign(href);
  });
}

async function toggleStar(r: any) {
  await requestClient.put(`/crm/customer/update-starred?id=${r.id}&starred=${!r.starred}`);
  r.starred = !r.starred;
  message.success(r.starred ? '已特别关注' : '已取消特别关注');
}

// ===== 客户阶段 =====
const stageCount = ref<Record<number, number>>({});
/** 是否处于关键词搜索状态：阶段数字是全部客户口径，搜索时更要说明清楚 */
const keywordActive = ref(false);
/** 顶部阶段快捷筛选（设计稿「CRM · 客户列表」） */
const stageChip = ref<number>();
function pickStage(v?: number) {
  stageChip.value = v;
  listRef.value?.setExtra({ stage: v });
}
/** 列表的搜索关键词变化时记录是否处于筛选态（用于阶段数字的口径提示） */
function onKeywordChange(keyword: string) {
  keywordActive.value = !!keyword;
}

async function loadStageStats() {
  if (props.pool) return;
  try {
    stageCount.value = await requestClient.get('/crm/customer/stage-stats');
  } catch {
    stageCount.value = {};
  }
}

async function changeStage(r: any, stage: number) {
  if (stage === r.stage) return;
  await requestClient.put(`/crm/customer/update-stage?id=${r.id}&stage=${stage}`);
  r.stage = stage;
  message.success(`已改为「${stageLabel(stage)}」，跟进记录里已留痕`);
  loadStageStats();
}

async function remove(r: any) {
  await deleteCustomer(r.id);
  message.success('已删除');
  listRef.value?.reload();
}

async function toPool(r: any) {
  await putCustomerPool(r.id);
  message.success('已放入公海');
  listRef.value?.reload();
}

async function receive(r: any) {
  await receiveCustomer([r.id]);
  message.success('已领取，可在「客户列表」中查看');
  listRef.value?.reload();
}

async function doExport() {
  // 导出时后端自己取全部，这里的分页参数只为通过校验
  const data = await exportCustomer({ ...listRef.value?.currentParams(), pageNo: 1, pageSize: 10 });
  downloadFileFromBlobPart({ fileName: props.pool ? '公海客户.xls' : '客户.xls', source: data });
}

// ===== 背调结果 =====
const research = ref<Record<number, LatestResearch>>({});
let researchVersion = 0;
const RESEARCH_STATUS: Record<string, { color: string; text: string }> = {
  done: { color: 'text-green-600', text: '已背调' },
  running: { color: 'text-amber-500', text: '背调中' },
  failed: { color: 'text-red-500', text: '背调失败' },
};

async function loadResearch(rows: any[]) {
  const version = ++researchVersion;
  research.value = {};
  if (props.pool || !rows.length) return;
  try {
    const list = await getLatestResearch(rows.map((r) => r.id));
    if (version === researchVersion) {
      research.value = Object.fromEntries(list.map((x) => [x.customerId, x]));
    }
  } catch {
    // 背调状态加载失败时，客户列表仍可使用。
  }
}

function openReport(customerId: number) {
  const r = research.value[customerId];
  if (r) hubSelect('salesmind', { sub: 'research', researchId: String(r.id) });
}

/** 一键背调：为勾选的客户发起深度背调（读取官网公开页面与往来记录，AI 生成报告） */
const busy = ref(false);
async function oneClickResearch() {
  if (!selected.value.length) {
    message.info('请先勾选要背调的客户');
    return;
  }
  const list = selected.value.slice(0, 20);
  busy.value = true;
  let ok = 0;
  const failed: string[] = [];
  try {
    for (const c of list) {
      try {
        await createResearch({ customerId: c.id });
        ok++;
      } catch {
        failed.push(c.name);
      }
    }
  } finally {
    busy.value = false;
  }
  if (ok) message.success(`已发起 ${ok} 个背调，完成后在「背调结果」列查看${selected.value.length > 20 ? '（一次最多 20 个）' : ''}`);
  if (failed.length) message.warning(`${failed.join('、')} 未能发起背调（可能缺少官网或企业邮箱）`);
  listRef.value?.reload();
}

/** 一键唤醒：为勾选的客户创建唤醒任务，AI 逐个撰写唤醒邮件 / 消息，确认后发送 */
async function oneClickWake() {
  if (!selected.value.length) {
    message.info('请先勾选要唤醒的客户');
    return;
  }
  busy.value = true;
  try {
    await createReactivateTask({
      name: `一键唤醒 ${new Date().toLocaleDateString('zh-CN')}`,
      customerIds: selected.value.map((c) => c.id),
      channel: 'auto',
    });
    message.success('唤醒任务已创建，AI 正在逐个撰写，确认后再发送');
    hubSelect('salesmind', { sub: 'wake' });
  } finally {
    busy.value = false;
  }
}

/** 群发邮件：勾选的客户带到「邮件群发」新建页 */
function mailToSelected() {
  if (!selected.value.length) {
    message.info('请先勾选要群发邮件的客户');
    return;
  }
  batchDraftCustomers.value = selected.value.map((c) => ({ id: c.id, name: c.name }));
  hubSelect('mail-batch', { new: '1' });
}

onMounted(async () => {
  if (props.pool) return;
  loadStageStats();
  [groups.value, tags.value] = await Promise.all([getCustomerGroupList().catch(() => []), getTagList().catch(() => [])]);
});

const me = computed(() => userStore.userInfo?.id);
</script>

<template>
  <div class="h-full">
    <FormModal @success="listRef?.reload()" />
    <ImportModal @success="listRef?.reload()" />
    <CrmList
      ref="listRef"
      :title="pool ? '公海客户' : '客户列表'"
      :rail="rail"
      :columns="columns"
      :fetch="fetch"
      :base-params="pool ? { pool: true } : {}"
      :storage-key="pool ? 'customer-pool' : 'customer'"
      search-placeholder="搜索客户名称、公司名称"
      @select="(rows) => (selected = rows)"
      @loaded="loadResearch"
      @search="onKeywordChange"
    >
      <template v-if="!pool" #filters>
        <div class="crm-stage-chips" role="group" aria-label="按阶段筛选（数字为全部客户统计）">
          <button type="button" :class="{ on: stageChip === undefined }" @click="pickStage(undefined)">全部阶段</button>
          <button
            v-for="o in stageOptions()"
            :key="o.value"
            type="button"
            :class="{ on: stageChip === o.value }"
            :title="`${o.label}：全部客户中 ${stageCount[Number(o.value)] || 0} 个`"
            @click="pickStage(Number(o.value))"
          >
            {{ o.label }}<span v-if="stageCount[Number(o.value)]">{{ stageCount[Number(o.value)] }}</span>
          </button>
          <!-- 数字是 /crm/customer/stage-stats 的全量口径，不随搜索变化，必须说明 -->
          <span class="crm-stage-hint">{{ keywordActive ? '阶段数字为全部客户统计，不随搜索变化' : '阶段数字为全部客户统计' }}</span>
        </div>
      </template>
      <template #actions>
        <template v-if="!pool">
          <div class="crm-action-group">
            <button
              type="button"
              class="crm-top-btn crm-top-btn--ai"
              :disabled="busy"
              @click="oneClickResearch"
            >
              <IconifyIcon icon="lucide:sparkles" class="size-3.5" />
              <span>一键背调</span>
            </button>
            <button
              type="button"
              class="crm-top-btn crm-top-btn--ai"
              :disabled="busy"
              @click="oneClickWake"
            >
              <IconifyIcon icon="lucide:wand-sparkles" class="size-3.5" />
              <span>一键唤醒</span>
            </button>
            <button
              type="button"
              class="crm-top-btn crm-top-btn--soft"
              @click="mailToSelected"
            >
              <IconifyIcon icon="lucide:send" class="size-3.5" />
              <span>群发邮件</span>
            </button>
          </div>
          <span class="mx-0.5 h-4 w-px bg-black/[0.08]"></span>
        </template>
        <button
          v-else
          type="button"
          class="crm-top-btn crm-top-btn--ai"
          :disabled="busy"
          @click="oneClickWake"
        >
          <IconifyIcon icon="lucide:sparkles" class="size-3.5" />
          <span>一键唤醒</span>
        </button>

        <button
          type="button"
          class="crm-top-btn crm-top-btn--soft"
          @click="importApi.open()"
        >
          <IconifyIcon icon="lucide:download" class="size-3.5" />
          <span>导入</span>
        </button>
        <button
          type="button"
          class="crm-top-btn crm-top-btn--soft"
          @click="doExport"
        >
          <IconifyIcon icon="lucide:upload" class="size-3.5" />
          <span>导出</span>
        </button>
        <Button
          v-if="!pool"
          type="primary"
          shape="round"
          @click="formApi.setData(null).open()"
        >
          <IconifyIcon icon="lucide:plus" class="mr-1 inline size-4 align-[-3px]" />新增客户
        </Button>
      </template>
      <template #cell="{ column, record }">
        <div v-if="column.key === 'name'" class="flex min-w-0 items-center gap-2">
          <button
            v-if="!pool"
            type="button"
            class="grid size-5 shrink-0 cursor-pointer place-items-center border-0 bg-transparent p-0 transition-transform active:scale-90"
            :aria-label="record.starred ? '取消特别关注' : '设为特别关注'"
            @click="toggleStar(record)"
          >
            <IconifyIcon
              :icon="record.starred ? 'mdi:star' : 'mdi:star-outline'"
              class="size-4"
              :class="record.starred ? 'text-[#FF9500]' : 'text-[#D1D1D6] hover:text-[#8E8E93]'"
            />
          </button>
          <span
            class="ap-crm-avatar"
            :style="{ background: crmAvatarBg(record.name || record.id) }"
            aria-hidden="true"
            >{{ crmInitial(record.name) }}</span
          >
          <a
            class="min-w-0 truncate font-semibold text-[#1D1D1F] hover:text-[#007AFF]"
            :href="crmDetailPath('customer', record.id)"
            @click="detail(record, $event)"
            >{{ record.name }}</a
          >
        </div>
        <template v-else-if="column.key === 'aiHosted'">
          <span
            class="ap-crm-pill"
            :class="record.aiHosted ? 'ap-crm-pill--purple' : 'ap-crm-pill--gray'"
          >
            <span
              class="size-1.5 rounded-full"
              :class="record.aiHosted ? 'bg-[#7C3AED]' : 'bg-[#AEAEB2]'"
            ></span>
            {{ record.aiHosted ? 'AI 托管中' : '人工接待' }}
          </span>
        </template>
        <template v-else-if="column.key === 'channel'">
          <div v-if="channelTags(record).length" class="flex flex-wrap gap-1">
            <span
              v-for="t in channelTags(record)"
              :key="t"
              class="ap-crm-pill"
              :class="t === 'WhatsApp' ? 'ap-crm-pill--green' : 'ap-crm-pill--blue'"
              >{{ t }}</span
            >
          </div>
          <span v-else class="text-[#C7C7CC]">-</span>
        </template>
        <template v-else-if="column.key === 'stage'">
          <Select
            :value="record.stage"
            size="small"
            :bordered="false"
            class="crm-inline-select w-full"
            :options="stageOptions().map((o) => ({ label: o.label, value: o.value }))"
            placeholder="未设置"
            @change="(v: any) => changeStage(record, Number(v))"
          />
        </template>
        <template v-else-if="column.key === 'source'">{{ sourceLabel(record.source) }}</template>
        <template v-else-if="column.key === 'dealStatus'">
          <span
            class="ap-crm-pill"
            :class="record.dealStatus ? 'ap-crm-pill--green' : 'ap-crm-pill--gray'"
          >
            {{ record.dealStatus ? '已成交' : '未成交' }}
          </span>
        </template>
        <template v-else-if="column.key === 'mobile'">
          <span class="tabular-nums">{{ record.mobile || record.telephone || '-' }}</span>
        </template>
        <template v-else-if="column.key === 'countryCode'">{{ countryName(record.countryCode) || '-' }}</template>
        <template v-else-if="column.key === 'industryId'">{{ industryLabel(record.industryId) }}</template>
        <template v-else-if="column.key === 'level'">
          <span v-if="record.level" class="ap-crm-pill ap-crm-pill--amber">
            {{ levelLabel(record.level) }}
          </span>
          <span v-else class="text-[#C7C7CC]">-</span>
        </template>
        <template v-else-if="column.key === 'tags'">
          <div v-if="record.tagIds?.length" class="flex flex-wrap gap-1">
            <span
              v-for="id in record.tagIds"
              :key="id"
              class="ap-crm-pill ap-crm-pill--gray"
              >{{ tagName(id) || id }}</span
            >
          </div>
          <span v-else class="text-[#C7C7CC]">-</span>
        </template>
        <template v-else-if="column.key === 'research'">
          <template v-if="research[record.id]">
            <Tooltip :title="research[record.id]!.summary">
              <div class="flex min-w-0 items-center gap-1.5">
                <span
                  class="ap-crm-pill shrink-0"
                  :class="
                    research[record.id]!.status === 'done'
                      ? 'ap-crm-pill--green'
                      : research[record.id]!.status === 'running'
                        ? 'ap-crm-pill--amber'
                        : 'ap-crm-pill--red'
                  "
                >
                  {{ RESEARCH_STATUS[research[record.id]!.status]?.text }}
                </span>
                <span
                  v-if="research[record.id]!.summary"
                  class="truncate text-xs text-[#6E6E73]"
                  >{{ research[record.id]!.summary }}</span
                >
              </div>
            </Tooltip>
          </template>
          <span v-else class="text-[#C7C7CC]">-</span>
        </template>
        <template v-else-if="column.key === 'report'">
          <a
            v-if="research[record.id]?.status === 'done'"
            class="ap-crm-link"
            @click="openReport(record.id)"
            >查看 ›</a
          >
          <span v-else class="text-[#C7C7CC]">-</span>
        </template>
        <template v-else-if="column.key === 'contactNextTime'">
          <span class="tabular-nums text-[#6E6E73]">{{ fmtTime(record.contactNextTime) }}</span>
        </template>
        <template v-else-if="column.key === 'contactLastTime'">
          <span class="tabular-nums text-[#6E6E73]">{{ fmtTime(record.contactLastTime) }}</span>
        </template>
        <template v-else-if="column.key === 'action'">
          <div class="flex items-center gap-3">
            <a class="ap-crm-link" @click="detail(record)">查看</a>
            <template v-if="pool">
              <Popconfirm title="领取后这个客户由你负责，确定吗？" @confirm="receive(record)">
                <a class="ap-crm-link">领取</a>
              </Popconfirm>
            </template>
            <template v-else>
              <a class="ap-crm-link" @click="formApi.setData(record).open()">编辑</a>
              <Popconfirm v-if="record.ownerUserId === me" title="放入公海后其他同事可以领取，确定吗？" @confirm="toPool(record)">
                <a class="ap-crm-link">公海</a>
              </Popconfirm>
              <Popconfirm title="确定删除这个客户吗？" @confirm="remove(record)">
                <a class="ap-crm-link ap-crm-link--danger">删除</a>
              </Popconfirm>
            </template>
          </div>
        </template>
        <template v-else>{{ (record as any)[column.key as string] || '-' }}</template>
      </template>
    </CrmList>
  </div>
</template>

<style scoped>
.crm-action-group {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.crm-top-btn {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 32px;
  padding: 0 13px;
  font-size: 12.5px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  border: 0;
  border-radius: 999px;
  transition:
    background-color var(--ap-dur-fast),
    color var(--ap-dur-fast),
    transform var(--ap-dur-fast) var(--ap-ease),
    box-shadow var(--ap-dur-fast);
}

.crm-top-btn:active:not(:disabled) {
  transform: scale(0.96);
}

.crm-top-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.crm-top-btn--ai {
  color: #6d28d9;
  background: linear-gradient(135deg, #f5f0ff 0%, #eaf3ff 100%);
  box-shadow: inset 0 0 0 0.5px rgb(124 58 237 / 20%);
}

.crm-top-btn--ai:hover:not(:disabled) {
  color: #5b21b6;
  background: linear-gradient(135deg, #ede5ff 0%, #dcebff 100%);
}

.crm-top-btn--soft {
  color: #3a3a3c;
  background: rgb(255 255 255 / 86%);
  box-shadow:
    0 1px 2px rgb(0 0 0 / 5%),
    inset 0 0 0 0.5px rgb(0 0 0 / 10%);
}

.crm-top-btn--soft:hover:not(:disabled) {
  color: #1d1d1f;
  background: #fff;
}

.crm-inline-select :deep(.ant-select-selector) {
  padding-inline: 8px !important;
  background: #f4f5f8 !important;
  border-radius: 999px !important;
}

.crm-stage-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

/* 说明阶段数字的统计口径，避免搜索命中 0 条时把数字误读成筛选结果 */
.crm-stage-hint {
  font-size: 12px;
  color: var(--ap-label-3);
}

.crm-stage-chips button {
  height: 28px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 500;
  color: var(--ap-label-2);
  cursor: pointer;
  background: var(--ap-fill-4);
  border: 0;
  border-radius: var(--ap-radius-pill);
  transition:
    background-color var(--ap-dur-fast) linear,
    color var(--ap-dur-fast) linear,
    transform var(--ap-dur-fast) var(--ap-ease);
}

.crm-stage-chips button:hover:not(.on) {
  color: var(--ap-label);
  background: var(--ap-fill-3);
}

.crm-stage-chips button:active {
  transform: scale(0.96);
}

.crm-stage-chips button span {
  margin-left: 4px;
  color: var(--ap-label-3);
}

.crm-stage-chips button.on {
  font-weight: 600;
  color: #fff;
  background: var(--ap-label);
}

.crm-stage-chips button.on span {
  color: rgb(255 255 255 / 72%);
}
</style>
