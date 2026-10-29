<script lang="ts" setup>
/**
 * 知识库：企业介绍、产品资料、常见问答。AI 回复时按客户提问挑选最相关的条目作为依据；
 * 配置了向量模型时按「关键词 + 语义」综合排序。
 */
import type { ChannelAiApi } from '#/api/channel/ai';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Alert,
  Button,
  Card,
  Form,
  FormItem,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Tag,
  Tooltip,
  Table,
  TabPane,
  Tabs,
  Textarea,
} from 'ant-design-vue';

import {
  categoryLabel,
  deleteKnowledge,
  getAiConfig,
  getKnowledgeList,
  MATERIAL_CATEGORIES,
  reindexKnowledge,
  saveKnowledge,
  searchKnowledge,
} from '#/api/channel/ai';

defineOptions({ name: 'CgchAgentKnowledge' });

type Kind = ChannelAiApi.Knowledge['type'];
const TABS: { key: Kind; label: string; titleLabel: string; contentLabel: string; tip: string }[] = [
  { key: 'company', label: '企业介绍', titleLabel: '标题', contentLabel: '内容', tip: '每次回复都会带上，建议写清公司名称、主营产品、工厂规模、出口市场、认证等，控制在 1000 字以内。' },
  { key: 'product', label: '产品资料', titleLabel: '产品 / 主题', contentLabel: '资料', tip: '按产品或主题分条填写：规格、价格区间、起订量、交期、包装、付款方式等。客户问到相关内容时才会带上。' },
  { key: 'faq', label: '常见问答', titleLabel: '问题', contentLabel: '答案', tip: '把客户常问的问题和标准答案逐条录入，AI 会优先按这里的答案回复。' },
];

const active = ref<Kind>('company');
const loading = ref(false);
const all = ref<ChannelAiApi.Knowledge[]>([]);
const category = ref<string>();
const hasCategory = computed(() => active.value !== 'company');
const rows = computed(() =>
  all.value.filter((k) => k.type === active.value && (!hasCategory.value || !category.value || k.category === category.value)),
);
const tab = computed(() => TABS.find((t) => t.key === active.value)!);

const embeddingModel = ref<string>();
const indexed = (k: ChannelAiApi.Knowledge) => !!embeddingModel.value && k.embeddingModel === embeddingModel.value;
const unindexed = computed(() => all.value.filter((k) => k.enabled && !indexed(k)).length);
const reindexing = ref(false);
async function reindex() {
  reindexing.value = true;
  try {
    const n = await reindexKnowledge();
    message.success(n ? `已为 ${n} 条资料建立语义索引` : '所有资料都已建立索引');
    await load();
  } finally {
    reindexing.value = false;
  }
}

// 检索测试
const q = ref('');
const searching = ref(false);
const hits = ref<ChannelAiApi.SearchHit[]>();
async function search() {
  if (!q.value.trim()) return;
  searching.value = true;
  try {
    hits.value = await searchKnowledge(q.value.trim());
  } finally {
    searching.value = false;
  }
}

const columns = computed(() => [
  { title: tab.value.titleLabel, dataIndex: 'title', key: 'title', width: 240 },
  ...(hasCategory.value ? [{ title: '分类', key: 'category', width: 100 }] : []),
  { title: tab.value.contentLabel, key: 'content' },
  { title: '启用', key: 'enabled', width: 80 },
  ...(embeddingModel.value ? [{ title: '语义索引', key: 'index', width: 100 }] : []),
  { title: '操作', key: 'action', width: 140 },
]);

async function load() {
  loading.value = true;
  try {
    all.value = await getKnowledgeList();
  } finally {
    loading.value = false;
  }
}

const open = ref(false);
const saving = ref(false);
const form = reactive<ChannelAiApi.Knowledge>({ type: 'company', title: '', content: '', enabled: true, sort: 0 });
function openForm(row?: ChannelAiApi.Knowledge) {
  Object.assign(
    form,
    { id: undefined, type: active.value, title: '', content: '', enabled: true, sort: 0, category: hasCategory.value ? category.value : undefined },
    row ?? {},
  );
  open.value = true;
}
async function save() {
  if (!form.content?.trim()) {
    message.warning(`请填写${tab.value.contentLabel}`);
    return;
  }
  saving.value = true;
  try {
    await saveKnowledge({ ...form });
    message.success('已保存');
    open.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function toggle(row: ChannelAiApi.Knowledge, enabled: boolean) {
  await saveKnowledge({ ...row, enabled });
  row.enabled = enabled;
}
async function remove(row: ChannelAiApi.Knowledge) {
  await deleteKnowledge(row.id!);
  message.success('已删除');
  await load();
}

onMounted(async () => {
  embeddingModel.value = (await getAiConfig()).embeddingModel || undefined;
  await load();
});
</script>

<template>
  <Page auto-content-height>
    <Card title="知识库" :bordered="false">
      <template #extra>
        <Space>
          <Tooltip v-if="embeddingModel" :title="`向量模型：${embeddingModel}`">
            <Button :loading="reindexing" @click="reindex">建立语义索引<span v-if="unindexed">（{{ unindexed }} 条待处理）</span></Button>
          </Tooltip>
          <Button type="primary" @click="openForm()">新增{{ tab.label }}</Button>
        </Space>
      </template>
      <Alert class="mb-4" type="info" show-icon message="AI 只会依据这里的资料回答价格、起订量、交期等具体问题；资料里没有的，会回复客户「确认后答复」，不会编造。" />
      <Tabs v-model:active-key="active">
        <TabPane v-for="t in TABS" :key="t.key" :tab="`${t.label} ${all.filter((k) => k.type === t.key).length}`" />
      </Tabs>
      <div class="mb-3 flex items-center gap-3">
        <p class="m-0 flex-1 text-sm text-gray-500">{{ tab.tip }}</p>
        <Select v-if="hasCategory" v-model:value="category" allow-clear class="w-36" placeholder="全部分类" :options="MATERIAL_CATEGORIES" />
      </div>
      <Table :columns="columns" :data-source="rows" :loading="loading" :pagination="false" row-key="id">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'content'">
            <div class="line-clamp-3 whitespace-pre-wrap text-sm">{{ record.content }}</div>
          </template>
          <template v-else-if="column.key === 'category'">
            <Tag v-if="record.category" class="m-0">{{ categoryLabel(record.category) }}</Tag>
            <span v-else class="text-gray-300">-</span>
          </template>
          <template v-else-if="column.key === 'index'">
            <Tag :color="indexed(record as any) ? 'green' : 'default'">{{ indexed(record as any) ? '已建立' : '未建立' }}</Tag>
          </template>
          <template v-else-if="column.key === 'enabled'">
            <Switch :checked="record.enabled" size="small" @change="(v: any) => toggle(record as any, !!v)" />
          </template>
          <template v-else-if="column.key === 'action'">
            <Space>
              <Button size="small" type="link" @click="openForm(record as any)">修改</Button>
              <Popconfirm title="确定删除？" @confirm="remove(record as any)">
                <Button size="small" type="link" danger>删除</Button>
              </Popconfirm>
            </Space>
          </template>
        </template>
        <template #emptyText>还没有{{ tab.label }}</template>
      </Table>
    </Card>

    <Card title="检索测试" :bordered="false" class="mt-4">
      <p class="mb-3 text-sm text-gray-500">
        输入一句客户可能会问的话，看 AI 回复时会参考哪些资料（企业介绍每次都会带上，这里不列出）。
        {{ embeddingModel ? '当前为关键词 + 语义综合检索。' : '当前只按关键词检索；在数字员工档案里填写向量模型后，换种说法也能命中。' }}
      </p>
      <Space class="mb-3 w-full">
        <Input v-model:value="q" placeholder="例如：How many pieces must I buy at minimum?" class="w-[480px]" @press-enter="search" />
        <Button type="primary" :loading="searching" @click="search">检索</Button>
      </Space>
      <div v-if="hits && hits.length === 0" class="text-gray-400">没有命中任何资料，AI 遇到这类问题会回复「确认后答复」。</div>
      <div v-for="(h, i) in hits" :key="h.id" class="border-b py-2 last:border-b-0">
        <div class="flex items-center gap-2">
          <span class="text-gray-400">{{ i + 1 }}.</span>
          <span class="font-medium">{{ h.title || '（无标题）' }}</span>
          <Tag>{{ TABS.find((t) => t.key === h.type)?.label || '资料文件' }}</Tag>
          <Tag v-if="h.category">{{ categoryLabel(h.category) }}</Tag>
          <Tag v-if="h.scope === 'personal'" color="purple">我的个人物料</Tag>
          <Tag v-if="h.keywordScore > 0" color="blue">关键词 {{ h.keywordScore }}</Tag>
          <Tag v-if="h.semantic" :color="h.semanticScore >= 0.35 ? 'green' : 'default'">语义相似度 {{ h.semanticScore.toFixed(2) }}</Tag>
        </div>
        <div class="line-clamp-2 pl-5 text-sm text-gray-500">{{ h.content }}</div>
      </div>
    </Card>

    <Modal v-model:open="open" :title="form.id ? `修改${tab.label}` : `新增${tab.label}`" :width="720" :confirm-loading="saving" @ok="save">
      <Form layout="vertical">
        <FormItem :label="tab.titleLabel"><Input v-model:value="form.title" :maxlength="200" /></FormItem>
        <FormItem v-if="hasCategory" label="分类">
          <Select v-model:value="form.category" allow-clear class="w-48" placeholder="不分类" :options="MATERIAL_CATEGORIES" />
        </FormItem>
        <FormItem :label="tab.contentLabel" required>
          <Textarea v-model:value="form.content" :rows="10" show-count :maxlength="8000" />
        </FormItem>
        <FormItem label="排序（越小越靠前）"><InputNumber v-model:value="form.sort" :min="0" /></FormItem>
      </Form>
    </Modal>
  </Page>
</template>
