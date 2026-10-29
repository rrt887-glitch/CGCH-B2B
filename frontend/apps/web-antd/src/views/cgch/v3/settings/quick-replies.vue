<script lang="ts" setup>
/**
 * 快捷回复：会话里点「快捷回复」一键插入的常用话术。全员快捷回复所有业务员可用（需要维护权限才能改），个人快捷回复只有自己能看到。
 * 内容里的 {name} 发送时换成客户称呼。
 */
import type { QuickReplyApi } from '#/api/channel/quick-reply';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';

import { Button, Input, InputNumber, message, Modal, Popconfirm, Radio, RadioGroup, Table, Tag, Textarea } from 'ant-design-vue';

import { deleteQuickReply, getQuickReplies, saveQuickReply } from '#/api/channel/quick-reply';

const { hasAccessByCodes } = useAccess();
const canManage = computed(() => hasAccessByCodes(['channel:quick-reply:manage']));
const canEdit = (r: QuickReplyApi.Item) => r.scope === 'personal' || canManage.value;

const rows = ref<QuickReplyApi.Item[]>([]);
const loading = ref(false);
const keyword = ref('');
const columns = [
  { title: '标题', dataIndex: 'title', key: 'title', width: 180 },
  { title: '内容', dataIndex: 'content', key: 'content' },
  { title: '范围', key: 'scope', width: 90 },
  { title: '使用次数', dataIndex: 'useCount', key: 'useCount', width: 90 },
  { title: '操作', key: 'action', width: 120 },
];

async function load() {
  loading.value = true;
  try {
    rows.value = await getQuickReplies(keyword.value.trim() || undefined);
  } finally {
    loading.value = false;
  }
}

const open = ref(false);
const form = reactive<QuickReplyApi.Item>({ scope: 'personal', title: '', content: '', sort: 0 });
function edit(r?: QuickReplyApi.Item) {
  Object.assign(form, r ? { ...r } : { id: undefined, scope: 'personal', title: '', content: '', sort: 0 });
  open.value = true;
}
async function save() {
  if (!form.title.trim() || !form.content.trim()) {
    message.warning('请填写标题和内容');
    return;
  }
  await saveQuickReply({ ...form });
  message.success('已保存');
  open.value = false;
  await load();
}
async function remove(r: QuickReplyApi.Item) {
  await deleteQuickReply(r.id!);
  message.success('已删除');
  await load();
}

onMounted(load);
</script>

<template>
  <div class="rounded-xl bg-white p-5">
    <div class="mb-1 text-lg font-semibold">快捷回复</div>
    <div class="mb-4 text-sm text-gray-500">在会话里点「快捷回复」一键插入。内容里写 {name}，插入时会换成客户称呼。</div>
    <div class="mb-3 flex items-center gap-3">
      <Button type="primary" @click="edit()">新建快捷回复</Button>
      <div class="flex-1"></div>
      <Input v-model:value="keyword" allow-clear class="w-56" placeholder="搜索标题或内容" @press-enter="load" @change="(e: any) => !e.target.value && load()" />
    </div>
    <Table :columns="columns" :data-source="rows" :loading="loading" row-key="id" size="middle" :pagination="false">
      <template #bodyCell="{ column, record }">
        <span v-if="column.key === 'content'" class="line-clamp-2 whitespace-pre-wrap text-gray-600">{{ record.content }}</span>
        <Tag v-else-if="column.key === 'scope'" :color="record.scope === 'enterprise' ? 'blue' : 'default'">
          {{ record.scope === 'enterprise' ? '全员' : '个人' }}
        </Tag>
        <div v-else-if="column.key === 'action'" class="flex gap-3">
          <template v-if="canEdit(record as any)">
            <a @click="edit(record as any)">编辑</a>
            <Popconfirm title="删除这条快捷回复？" @confirm="remove(record as any)"><a class="text-red-500">删除</a></Popconfirm>
          </template>
          <span v-else class="text-xs text-gray-400">管理员维护</span>
        </div>
      </template>
      <template #emptyText>还没有快捷回复</template>
    </Table>

    <Modal v-model:open="open" :title="form.id ? '编辑快捷回复' : '新建快捷回复'" ok-text="保存" :width="560" @ok="save">
      <div class="flex flex-col gap-3">
        <Input v-model:value="form.title" :maxlength="100" placeholder="标题，例如：报价单已发送" />
        <Textarea v-model:value="form.content" :auto-size="{ minRows: 4, maxRows: 10 }" :maxlength="4000" placeholder="Hi {name}, I have sent you our latest price list..." />
        <div class="flex items-center gap-4 text-sm">
          <RadioGroup v-model:value="form.scope" size="small">
            <Radio value="personal">仅自己</Radio>
            <Radio value="enterprise" :disabled="!canManage">全员可用</Radio>
          </RadioGroup>
          <span class="text-gray-500">排序</span>
          <InputNumber v-model:value="form.sort" :min="0" size="small" class="w-20" />
        </div>
        <div v-if="!canManage" class="text-xs text-gray-400">全员快捷回复由管理员维护</div>
      </div>
    </Modal>
  </div>
</template>
