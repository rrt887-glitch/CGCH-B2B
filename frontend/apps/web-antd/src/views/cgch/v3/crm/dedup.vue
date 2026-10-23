<script lang="ts" setup>
/** 查重：在客户与线索中查找可能重复的记录（与原网站布局一致） */
import type { CgchCrmApi } from '#/api/crm/cgch';

import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Button, Empty, Input, message, Select, Table, Tag } from 'ant-design-vue';

import { searchDedup } from '#/api/crm/cgch';

const router = useRouter();
const scope = ref<'all' | 'clue' | 'customer'>('all');
const field = ref('name');
const keyword = ref('');
const loading = ref(false);
const searched = ref(false);
const rows = ref<CgchCrmApi.DedupItem[]>([]);
const pageSize = ref(10);

const FIELDS = [
  { value: 'name', label: '名称或简称', match: ['name', 'companyName'] },
  { value: 'email', label: '邮箱', match: ['email'] },
  { value: 'mobile', label: '电话', match: ['mobile', 'telephone', 'whatsapp'] },
  { value: 'website', label: '官网', match: ['website'] },
];

const SCOPES = [
  { value: 'all', label: '全部' },
  { value: 'customer', label: '客户' },
  { value: 'clue', label: '线索' },
] as const;

const filtered = computed(() => {
  const m = FIELDS.find((f) => f.value === field.value)?.match ?? [];
  return rows.value.filter((r) => !r.matchedFields?.length || r.matchedFields.some((x) => m.includes(x)));
});

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
  const k = keyword.value.trim();
  if (k.length < 2 || k.length > 100) {
    message.info('请输入 2-100 个字符');
    return;
  }
  loading.value = true;
  try {
    rows.value = await searchDedup(k, scope.value);
    searched.value = true;
  } finally {
    loading.value = false;
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
    <h2 class="dd__title">查重</h2>
    <div class="dd__card">
      <div class="dd__bar">
        <div class="dd__seg">
          <button v-for="s in SCOPES" :key="s.value" type="button" :class="{ on: scope === s.value }" @click="pickScope(s.value)">
            {{ s.label }}
          </button>
        </div>
        <Select v-model:value="field" class="w-28" :options="FIELDS" />
        <div class="dd__input">
          <Input v-model:value="keyword" :bordered="false" allow-clear placeholder="请输入 2-100 个字符" :maxlength="100" @press-enter="search" />
          <Button type="primary" :loading="loading" @click="search">搜 索</Button>
        </div>
      </div>
      <Table
        :columns="columns"
        :data-source="filtered"
        :loading="loading"
        :pagination="{ pageSize, showSizeChanger: true, pageSizeOptions: ['10', '20', '50'], onShowSizeChange: (_c: number, s: number) => (pageSize = s) }"
        :row-key="(r: any) => `${r.type}-${r.id}`"
        size="middle"
        :scroll="{ x: 900 }"
      >
        <template #bodyCell="{ column, record }">
          <Tag v-if="column.key === 'type'" :color="record.type === 'customer' ? 'blue' : 'orange'" class="m-0">
            {{ record.type === 'customer' ? '客户' : '线索' }}
          </Tag>
          <template v-else-if="column.key === 'stage'">{{ record.ownerUserId ? '跟进中' : '公海' }}</template>
          <template v-else-if="column.key === 'customerName'">{{ record.type === 'customer' ? record.name : '-' }}</template>
          <template v-else-if="column.key === 'clueName'">{{ record.type === 'clue' ? record.name : '-' }}</template>
          <template v-else-if="column.key === 'companyName'">{{ record.companyName || '-' }}</template>
          <a v-else-if="column.key === 'action'" @click="view(record)">查看</a>
        </template>
        <template #emptyText>
          <Empty :description="searched ? '没有找到重复记录' : '暂无数据'" />
        </template>
      </Table>
    </div>
  </div>
</template>

<style scoped>
.dd {
  padding: 18px 16px;
}

.dd__title {
  margin: 0 0 14px;
  font-size: 20px;
  font-weight: 700;
  color: #111827;
}

.dd__card {
  padding: 14px;
  border: 1px solid #eef0f3;
  border-radius: 10px;
}

.dd__bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}

.dd__seg {
  display: inline-flex;
  padding: 3px;
  background: #f3f5fa;
  border-radius: 6px;
}

.dd__seg button {
  padding: 2px 10px;
  font-size: 13px;
  color: #4b5563;
  cursor: pointer;
  background: none;
  border: 0;
  border-radius: 4px;
}

.dd__seg button.on {
  color: #111827;
  background: #fff;
  box-shadow: 0 1px 2px rgb(0 0 0 / 6%);
}

.dd__input {
  display: inline-flex;
  align-items: center;
  width: 330px;
  max-width: 100%;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.dd__input :deep(.ant-btn) {
  border-radius: 0;
}
</style>
