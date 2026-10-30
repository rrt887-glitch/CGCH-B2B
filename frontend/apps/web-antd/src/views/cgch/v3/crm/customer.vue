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

import { Button, message, Popconfirm, Select, Tag, Tooltip } from 'ant-design-vue';

import { getCustomerGroupList, getTagList } from '#/api/crm/cgch';
import {
  deleteCustomer,
  exportCustomer,
  getCustomerPage,
  putCustomerPool,
  receiveCustomer,
} from '#/api/crm/customer';
import { createReactivateTask } from '#/api/channel/reactivate';
import type { LatestResearch } from '#/api/channel/research';
import { createResearch, getLatestResearch } from '#/api/channel/research';
import { requestClient } from '#/api/request';
import CustomerForm from '#/views/crm/customer/modules/form.vue';
import ImportForm from '#/views/crm/customer/modules/import-form.vue';

import CrmList from './components/crm-list.vue';
import { channelTags, countryName, fmtTime, industryLabel, levelLabel, levelRail, page, sourceLabel, stageLabel, stageOptions } from './components/shared';

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

function detail(r: any) {
  router.push({ name: 'CrmCustomerDetail', params: { id: r.id } });
}

async function toggleStar(r: any) {
  await requestClient.put(`/crm/customer/update-starred?id=${r.id}&starred=${!r.starred}`);
  r.starred = !r.starred;
  message.success(r.starred ? '已特别关注' : '已取消特别关注');
}

// ===== 客户阶段 =====
const stageCount = ref<Record<number, number>>({});
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
const RESEARCH_STATUS: Record<string, { color: string; text: string }> = {
  done: { color: 'text-green-600', text: '已背调' },
  running: { color: 'text-amber-500', text: '背调中' },
  failed: { color: 'text-red-500', text: '背调失败' },
};

async function loadResearch(rows: any[]) {
  if (props.pool || !rows.length) return;
  try {
    const list = await getLatestResearch(rows.map((r) => r.id));
    research.value = Object.fromEntries(list.map((x) => [x.customerId, x]));
  } catch {
    research.value = {};
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
    >
      <template #actions>
        <Button class="crm-ai-btn" :loading="busy" @click="oneClickWake"><IconifyIcon icon="lucide:sparkles" class="mr-1 inline size-3.5" />一键唤醒</Button>
        <Button v-if="!pool" class="crm-ai-btn" :loading="busy" @click="oneClickResearch">
          <IconifyIcon icon="lucide:sparkles" class="mr-1 inline size-3.5" />一键背调
        </Button>
        <Button v-if="!pool" type="primary" @click="formApi.setData(null).open()">
          <IconifyIcon icon="lucide:plus" class="mr-1 inline size-4" />新增
        </Button>
        <Button type="primary" @click="importApi.open()"><IconifyIcon icon="lucide:download" class="mr-1 inline size-4" />导入</Button>
        <Button type="primary" @click="doExport"><IconifyIcon icon="lucide:upload" class="mr-1 inline size-4" />导出</Button>
      </template>
      <template #cell="{ column, record }">
        <span v-if="column.key === 'name'" class="inline-flex items-center gap-1">
          <IconifyIcon
            v-if="!pool"
            :icon="record.starred ? 'mdi:star' : 'mdi:star-outline'"
            class="size-4 shrink-0 cursor-pointer"
            :class="record.starred ? 'text-amber-400' : 'text-gray-300'"
            @click="toggleStar(record)"
          />
          <a class="truncate font-medium" @click="detail(record)">{{ record.name }}</a>
        </span>
        <template v-else-if="column.key === 'aiHosted'">
          <Tag :color="record.aiHosted ? 'blue' : 'default'" class="m-0">{{ record.aiHosted ? 'AI托管中' : '人工' }}</Tag>
        </template>
        <template v-else-if="column.key === 'channel'">
          <Tag v-for="t in channelTags(record)" :key="t" class="m-0 mr-1">{{ t }}</Tag>
          <span v-if="!channelTags(record).length">-</span>
        </template>
        <template v-else-if="column.key === 'stage'">
          <Select
            :value="record.stage"
            size="small"
            class="w-full"
            :options="stageOptions().map((o) => ({ label: o.label, value: o.value }))"
            placeholder="未设置"
            @change="(v: any) => changeStage(record, Number(v))"
          />
        </template>
        <template v-else-if="column.key === 'source'">{{ sourceLabel(record.source) }}</template>
        <template v-else-if="column.key === 'dealStatus'">
          <Tag :color="record.dealStatus ? 'green' : 'default'" class="m-0">{{ record.dealStatus ? '已成交' : '未成交' }}</Tag>
        </template>
        <template v-else-if="column.key === 'mobile'">{{ record.mobile || record.telephone || '-' }}</template>
        <template v-else-if="column.key === 'countryCode'">{{ countryName(record.countryCode) || '-' }}</template>
        <template v-else-if="column.key === 'industryId'">{{ industryLabel(record.industryId) }}</template>
        <template v-else-if="column.key === 'level'">{{ levelLabel(record.level) }}</template>
        <template v-else-if="column.key === 'tags'">
          <Tag v-for="id in record.tagIds || []" :key="id" class="m-0 mr-1">{{ tagName(id) || id }}</Tag>
          <span v-if="!record.tagIds?.length">-</span>
        </template>
        <template v-else-if="column.key === 'research'">
          <template v-if="research[record.id]">
            <Tooltip :title="research[record.id]!.summary">
              <span :class="RESEARCH_STATUS[research[record.id]!.status]?.color">{{ RESEARCH_STATUS[research[record.id]!.status]?.text }}</span>
              <span v-if="research[record.id]!.summary" class="ml-1 text-xs text-gray-500">{{ research[record.id]!.summary }}</span>
            </Tooltip>
          </template>
          <span v-else>-</span>
        </template>
        <template v-else-if="column.key === 'report'">
          <a v-if="research[record.id]?.status === 'done'" @click="openReport(record.id)">查看</a>
          <span v-else>-</span>
        </template>
        <template v-else-if="column.key === 'contactNextTime'">{{ fmtTime(record.contactNextTime) }}</template>
        <template v-else-if="column.key === 'contactLastTime'">{{ fmtTime(record.contactLastTime) }}</template>
        <template v-else-if="column.key === 'action'">
          <div class="flex gap-3">
            <a @click="detail(record)">查看</a>
            <template v-if="pool">
              <Popconfirm title="领取后这个客户由你负责，确定吗？" @confirm="receive(record)"><a>领取</a></Popconfirm>
            </template>
            <template v-else>
              <a @click="formApi.setData(record).open()">编辑</a>
              <Popconfirm v-if="record.ownerUserId === me" title="放入公海后其他同事可以领取，确定吗？" @confirm="toPool(record)">
                <a>放入公海</a>
              </Popconfirm>
              <Popconfirm title="确定删除这个客户吗？" @confirm="remove(record)">
                <a class="text-red-500">删除</a>
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
.crm-ai-btn {
  color: #2563EB;
  background: #eef3fe;
  border-color: #dbe5fd;
}
</style>
