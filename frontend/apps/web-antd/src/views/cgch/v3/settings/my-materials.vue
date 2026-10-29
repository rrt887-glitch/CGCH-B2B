<script lang="ts" setup>
/**
 * 我的物料：业务员自己的产品资料、报价口径、常见问答。AI 回复「我负责的会话」时会和公司物料一起参考，
 * 别人的会话不会用到。公司物料由管理员在「智能体 → 知识库」维护。
 */
import type { ChannelAiApi } from '#/api/channel/ai';

import { computed, onMounted, reactive, ref } from 'vue';

import { Button, Input, InputNumber, message, Modal, Popconfirm, Radio, RadioGroup, Select, Switch, Table, Tag, Textarea } from 'ant-design-vue';

import { categoryLabel, deleteMyMaterial, getMyMaterials, MATERIAL_CATEGORIES, saveMyMaterial, searchMyMaterials } from '#/api/channel/ai';

const rows = ref<ChannelAiApi.Knowledge[]>([]);
const loading = ref(false);
const category = ref<string>();
const shown = computed(() => rows.value.filter((r) => !category.value || r.category === category.value));
const columns = [
  { title: '标题 / 问题', dataIndex: 'title', key: 'title', width: 200 },
  { title: '分类', key: 'category', width: 100 },
  { title: '内容', key: 'content' },
  { title: '启用', key: 'enabled', width: 70 },
  { title: '操作', key: 'action', width: 110 },
];

async function load() {
  loading.value = true;
  try {
    rows.value = await getMyMaterials();
  } finally {
    loading.value = false;
  }
}

const open = ref(false);
const saving = ref(false);
const form = reactive<ChannelAiApi.Knowledge>({ type: 'product', title: '', content: '', enabled: true, sort: 0 });
function edit(r?: ChannelAiApi.Knowledge) {
  Object.assign(form, { id: undefined, type: 'product', title: '', content: '', enabled: true, sort: 0, category: category.value }, r ?? {});
  open.value = true;
}
async function save() {
  if (!form.content?.trim()) {
    message.warning('请填写内容');
    return;
  }
  saving.value = true;
  try {
    await saveMyMaterial({ ...form });
    message.success('已保存');
    open.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function toggle(r: ChannelAiApi.Knowledge, enabled: boolean) {
  await saveMyMaterial({ ...r, enabled });
  r.enabled = enabled;
}
async function remove(r: ChannelAiApi.Knowledge) {
  await deleteMyMaterial(r.id!);
  message.success('已删除');
  await load();
}

// 检索测试：看客户这句话会用到哪些资料（公司物料 + 我的物料）
const q = ref('');
const searching = ref(false);
const hits = ref<ChannelAiApi.SearchHit[]>();
async function search() {
  if (!q.value.trim()) return;
  searching.value = true;
  try {
    hits.value = await searchMyMaterials(q.value.trim());
  } finally {
    searching.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="rounded-xl bg-white p-5">
    <div class="mb-1 text-lg font-semibold">我的物料</div>
    <div class="mb-4 text-sm text-gray-500">
      自己的产品资料、报价口径、常见问答。AI 回复你负责的会话时，会和公司物料一起参考；别人的会话不会用到。公司物料由管理员在「智能体 → 知识库」维护。
    </div>
    <div class="mb-3 flex items-center gap-3">
      <Button type="primary" @click="edit()">新增物料</Button>
      <div class="flex-1"></div>
      <Select v-model:value="category" allow-clear class="w-36" placeholder="全部分类" :options="MATERIAL_CATEGORIES" />
    </div>
    <Table :columns="columns" :data-source="shown" :loading="loading" row-key="id" size="middle" :pagination="false">
      <template #bodyCell="{ column, record }">
        <span v-if="column.key === 'title'">
          <Tag v-if="record.type === 'faq'" color="cyan" class="mr-1">问答</Tag>{{ record.title || '（无标题）' }}
        </span>
        <template v-else-if="column.key === 'category'">
          <Tag v-if="record.category" class="m-0">{{ categoryLabel(record.category) }}</Tag>
          <span v-else class="text-gray-300">-</span>
        </template>
        <span v-else-if="column.key === 'content'" class="line-clamp-2 whitespace-pre-wrap text-gray-600">{{ record.content }}</span>
        <Switch v-else-if="column.key === 'enabled'" :checked="record.enabled" size="small" @change="(v: any) => toggle(record as any, !!v)" />
        <div v-else-if="column.key === 'action'" class="flex gap-3">
          <a @click="edit(record as any)">编辑</a>
          <Popconfirm title="删除这条物料？" @confirm="remove(record as any)"><a class="text-red-500">删除</a></Popconfirm>
        </div>
      </template>
      <template #emptyText>还没有个人物料，例如你负责的产品线报价、常用证书说明</template>
    </Table>

    <div class="mt-6 mb-2 font-medium">检索测试</div>
    <div class="mb-2 text-sm text-gray-500">输入一句客户可能会问的话，看 AI 回复你的会话时会参考哪些资料。</div>
    <div class="mb-3 flex gap-2">
      <Input v-model:value="q" class="max-w-[480px]" placeholder="例如：Do you have CE certificate?" @press-enter="search" />
      <Button :loading="searching" @click="search">检索</Button>
    </div>
    <div v-if="hits && hits.length === 0" class="text-sm text-gray-400">没有命中任何资料</div>
    <div v-for="(h, i) in hits" :key="h.id" class="border-b py-2 last:border-b-0">
      <div class="flex items-center gap-2 text-sm">
        <span class="text-gray-400">{{ i + 1 }}.</span>
        <span class="font-medium">{{ h.title || '（无标题）' }}</span>
        <Tag :color="h.scope === 'personal' ? 'purple' : 'blue'">{{ h.scope === 'personal' ? '我的物料' : '公司物料' }}</Tag>
        <Tag v-if="h.category">{{ categoryLabel(h.category) }}</Tag>
      </div>
      <div class="line-clamp-2 pl-5 text-sm text-gray-500">{{ h.content }}</div>
    </div>

    <Modal v-model:open="open" :title="form.id ? '编辑物料' : '新增物料'" ok-text="保存" :width="640" :confirm-loading="saving" @ok="save">
      <div class="flex flex-col gap-3">
        <RadioGroup v-model:value="form.type" size="small">
          <Radio value="product">资料</Radio>
          <Radio value="faq">问答</Radio>
        </RadioGroup>
        <Input v-model:value="form.title" :maxlength="200" :placeholder="form.type === 'faq' ? '问题，例如：Can you do OEM?' : '产品 / 主题，例如：吹风机 HD-200 报价'" />
        <Select v-model:value="form.category" allow-clear class="w-48" placeholder="分类（可不选）" :options="MATERIAL_CATEGORIES" />
        <Textarea v-model:value="form.content" :auto-size="{ minRows: 6, maxRows: 14 }" :maxlength="8000" show-count :placeholder="form.type === 'faq' ? '答案' : '规格、价格区间、起订量、交期等'" />
        <div class="flex items-center gap-2 text-sm text-gray-500">排序<InputNumber v-model:value="form.sort" :min="0" size="small" class="w-20" /></div>
      </div>
    </Modal>
  </div>
</template>
