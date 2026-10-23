<script lang="ts" setup>
/**
 * 查重：输入名称、公司、邮箱、电话、官网或 WhatsApp，在客户与线索中查找可能重复的记录。
 */
import type { CgchCrmApi } from '#/api/crm/cgch';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { Button, Card, Input, RadioButton, RadioGroup, Space, Table, Tag } from 'ant-design-vue';

import { searchDedup } from '#/api/crm/cgch';
import { countryName } from '#/views/cgch/crm/shared/countries';

defineOptions({ name: 'CgchCrmDedup' });

const router = useRouter();
const keyword = ref('');
const scope = ref<'all' | 'clue' | 'customer'>('all');
const loading = ref(false);
const searched = ref(false);
const rows = ref<CgchCrmApi.DedupItem[]>([]);

const FIELD_LABEL: Record<string, string> = {
  name: '名称',
  companyName: '公司',
  email: '邮箱',
  mobile: '手机',
  telephone: '电话',
  website: '官网',
  whatsapp: 'WhatsApp',
};

const columns = [
  { title: '类型', key: 'type', width: 80 },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '公司名称', dataIndex: 'companyName', key: 'companyName' },
  { title: '邮箱', dataIndex: 'email', key: 'email' },
  { title: '手机', dataIndex: 'mobile', key: 'mobile', width: 150 },
  { title: '国家/地区', key: 'country', width: 110 },
  { title: '归属', key: 'owner', width: 90 },
  { title: '重复字段', key: 'matched', width: 200 },
  { title: '操作', key: 'action', width: 80 },
];

async function search() {
  if (keyword.value.trim().length < 2) {
    return;
  }
  loading.value = true;
  try {
    rows.value = await searchDedup(keyword.value.trim(), scope.value);
    searched.value = true;
  } finally {
    loading.value = false;
  }
}

function view(row: CgchCrmApi.DedupItem) {
  router.push(row.type === 'customer' ? `/crm/customer/detail/${row.id}` : `/crm/clue/detail/${row.id}`);
}
</script>

<template>
  <Page auto-content-height>
    <Card title="查重" :bordered="false">
      <Space class="mb-4" wrap>
        <Input
          v-model:value="keyword"
          allow-clear
          class="w-80"
          placeholder="名称 / 公司 / 邮箱 / 电话 / 官网 / WhatsApp，至少 2 个字"
          @press-enter="search"
        />
        <RadioGroup v-model:value="scope" button-style="solid">
          <RadioButton value="all">全部</RadioButton>
          <RadioButton value="customer">客户</RadioButton>
          <RadioButton value="clue">线索</RadioButton>
        </RadioGroup>
        <Button type="primary" :loading="loading" :disabled="keyword.trim().length < 2" @click="search">搜索</Button>
      </Space>

      <Table :columns="columns" :data-source="rows" :loading="loading" :pagination="false" row-key="id" size="middle">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'type'">
            <Tag :color="record.type === 'customer' ? 'blue' : 'orange'">
              {{ record.type === 'customer' ? '客户' : '线索' }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'country'">{{ countryName(record.countryCode) }}</template>
          <template v-else-if="column.key === 'owner'">{{ record.ownerUserId ? '有负责人' : '公海' }}</template>
          <template v-else-if="column.key === 'matched'">
            <Tag v-for="f in record.matchedFields" :key="f" color="red">{{ FIELD_LABEL[f] ?? f }}</Tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <Button size="small" type="link" @click="view(record as any)">查看</Button>
          </template>
        </template>
        <template #emptyText>{{ searched ? '没有找到重复记录' : '输入关键字后搜索' }}</template>
      </Table>
    </Card>
  </Page>
</template>
