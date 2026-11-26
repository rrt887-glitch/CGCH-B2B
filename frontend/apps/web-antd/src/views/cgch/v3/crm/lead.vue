<script lang="ts" setup>
/** 线索列表：左侧按跟进状态、来源筛选；表格列与原网站一致 */
import type { CrmColumn, RailItem } from './components/types';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { useVbenModal } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { Button, message, Popconfirm } from 'ant-design-vue';

import { deleteClue, getCluePage, transformClue } from '#/api/crm/clue';
import ClueForm from '#/views/crm/clue/modules/form.vue';

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
  page,
  sourceLabel,
  sourceRail,
} from './components/shared';

const props = defineProps<{ pool?: boolean }>();
const router = useRouter();
const listRef = ref<InstanceType<typeof CrmList>>();

const rail: RailItem[] = props.pool
  ? [{ key: 'source', label: '来源', children: sourceRail() }]
  : [
      { key: 'todo', label: '未跟进', filter: { followUpStatus: false } },
      { key: 'done', label: '已跟进', filter: { followUpStatus: true } },
      { key: 'source', label: '来源', children: sourceRail() },
    ];

const columns: CrmColumn[] = [
  { key: 'name', title: '线索名称', width: 180, fixed: 'left', required: true },
  { key: 'source', title: '线索来源', width: 110 },
  { key: 'channel', title: '渠道状态', width: 140 },
  { key: 'ownerUserName', title: '负责人', width: 100 },
  ...(props.pool ? [{ key: 'lastOwner', title: '上一次负责人', width: 110, hidden: true }] : []),
  { key: 'contactLastTime', title: '最近联系时间', width: 150 },
  { key: 'mobile', title: '联系电话', width: 140 },
  { key: 'whatsapp', title: 'whatsapp', width: 150 },
  { key: 'alibaba', title: '阿里TM', width: 110, hidden: true },
  { key: 'mic', title: 'MIC国际站', width: 110, hidden: true },
  { key: 'email', title: '邮箱', width: 200 },
  { key: 'countryCode', title: '公司所在国家', width: 120 },
  { key: 'industryId', title: '客户行业', width: 110 },
  { key: 'level', title: '客户级别', width: 100 },
  { key: 'detailAddress', title: '详细地址', width: 200 },
  { key: 'action', title: '操作', width: props.pool ? 80 : 170, fixed: 'right', required: true },
];

const fetch = (p: Record<string, any>) => page(getCluePage({ ...p } as any));

const [FormModal, formApi] = useVbenModal({ connectedComponent: ClueForm, destroyOnClose: true });

function detail(r: any, event?: MouseEvent) {
  if (event && (event.ctrlKey || event.metaKey || event.shiftKey)) return;
  event?.preventDefault();
  void openCrmDetail(router, crmDetailPath('clue', r.id), (href) => {
    console.warn('[crm] SPA 跳转线索详情未生效，改用整页打开', href);
    window.location.assign(href);
  });
}

async function remove(r: any) {
  await deleteClue(r.id);
  message.success('已删除');
  listRef.value?.reload();
}

async function transform(r: any) {
  await transformClue(r.id);
  message.success('已转为客户，可在「客户列表」中查看');
  listRef.value?.reload();
}
</script>

<template>
  <div class="h-full">
    <FormModal @success="listRef?.reload()" />
    <CrmList
      ref="listRef"
      :title="pool ? '公海线索' : '线索列表'"
      :rail="rail"
      :columns="columns"
      :fetch="fetch"
      :base-params="pool ? { pool: true } : {}"
      :storage-key="pool ? 'lead-pool' : 'lead'"
      search-placeholder="请输入线索名称"
      :date-filter="{ field: 'contactLastTime', label: '最近联系时间' }"
    >
      <template #actions>
        <Button
          v-if="!pool"
          type="primary"
          shape="round"
          @click="formApi.setData(null).open()"
        >
          <IconifyIcon icon="lucide:plus" class="mr-1 inline size-4 align-[-3px]" />新建线索
        </Button>
      </template>
      <template #cell="{ column, record }">
        <div v-if="column.key === 'name'" class="flex min-w-0 items-center gap-2">
          <span
            class="ap-crm-avatar"
            :style="{ background: crmAvatarBg(record.name || record.id) }"
            aria-hidden="true"
            >{{ crmInitial(record.name) }}</span
          >
          <a
            class="min-w-0 truncate font-semibold text-[#1D1D1F] hover:text-[#007AFF]"
            :href="crmDetailPath('clue', record.id)"
            @click="detail(record, $event)"
            >{{ record.name }}</a
          >
        </div>
        <template v-else-if="column.key === 'source'">{{ sourceLabel(record.source) }}</template>
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
        <template v-else-if="column.key === 'contactLastTime'">
          <span class="tabular-nums text-[#6E6E73]">{{ fmtTime(record.contactLastTime) }}</span>
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
        <template v-else-if="column.key === 'action'">
          <div class="flex items-center gap-3">
            <a class="ap-crm-link" @click="detail(record)">查看</a>
            <template v-if="!pool">
              <a class="ap-crm-link" @click="formApi.setData(record).open()">编辑</a>
              <Popconfirm v-if="!record.transformStatus" title="把这条线索转为客户？" @confirm="transform(record)">
                <a class="ap-crm-link">转客户</a>
              </Popconfirm>
              <Popconfirm title="确定删除这条线索吗？" @confirm="remove(record)">
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
