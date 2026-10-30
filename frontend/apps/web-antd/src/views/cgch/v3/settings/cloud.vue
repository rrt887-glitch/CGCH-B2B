<script lang="ts" setup>
/**
 * 云盘文件（与原网站一致）：企业云盘全员共享，打开「AI 可发送」的文件会在客户需要时由 AI 作为附件发出；
 * 个人云盘只有自己能看到。支持文件夹、搜索、改名、写描述。
 */
import { fmtDateTime } from '#/utils/datetime';
import type { CloudApi } from '#/api/channel/cloud';

import { computed, onMounted, reactive, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { useAccess } from '@vben/access';

import { Button, Empty, Input, message, Modal, Pagination, Popconfirm, Switch, Table, Textarea, Tooltip, Upload } from 'ant-design-vue';

import { createCloudFolder, deleteCloudFile, getCloudFiles, getCloudPath, updateCloudFile, uploadCloudFile } from '#/api/channel/cloud';

const scope = ref<CloudApi.Scope>('enterprise');
const { hasAccessByCodes } = useAccess();
/** 企业云盘的文件 AI 会发给客户，只有有「企业云盘维护」权限的人能增删改；个人云盘本人随意 */
const canEdit = computed(() => scope.value === 'personal' || hasAccessByCodes(['channel:cloud:manage']));
const parentId = ref(0);
const path = ref<CloudApi.File[]>([]);
const keyword = ref('');
const rows = ref<CloudApi.File[]>([]);
const loading = ref(false);
const uploading = ref(0);
const page = reactive({ no: 1, size: 20 });
const paged = computed(() => rows.value.slice((page.no - 1) * page.size, page.no * page.size));

const columns = computed(() => [
  { title: '文件名称', key: 'name' },
  { title: '描述', key: 'description', width: 240 },
  { title: '大小', key: 'size', width: 100 },
  ...(scope.value === 'enterprise' ? [{ title: 'AI 可发送', key: 'ai', width: 100 }] : []),
  { title: '更新时间', key: 'updateTime', width: 160 },
  { title: '操作', key: 'action', width: 150 },
]);

async function load() {
  loading.value = true;
  try {
    rows.value = await getCloudFiles({ scope: scope.value, parentId: parentId.value, keyword: keyword.value.trim() || undefined });
    path.value = parentId.value ? await getCloudPath(parentId.value) : [];
  } finally {
    loading.value = false;
  }
}

function switchScope(s: CloudApi.Scope) {
  scope.value = s;
  parentId.value = 0;
  keyword.value = '';
  page.no = 1;
  load();
}

function openFolder(id: number) {
  parentId.value = id;
  keyword.value = '';
  page.no = 1;
  load();
}

function size(n?: number) {
  if (!n) return '-';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}

function icon(f: CloudApi.File) {
  if (f.folder) return { i: 'lucide:folder', c: '#f59e0b' };
  const n = f.name.toLowerCase();
  if (n.endsWith('.pdf')) return { i: 'lucide:file-text', c: '#ef4444' };
  if (/\.(xlsx?|csv)$/.test(n)) return { i: 'lucide:file-spreadsheet', c: '#16a34a' };
  if (/\.(docx?)$/.test(n)) return { i: 'lucide:file-type', c: '#2563eb' };
  if (/\.(png|jpe?g|webp|gif)$/.test(n)) return { i: 'lucide:image', c: '#8b5cf6' };
  if (/\.(mp4|mov)$/.test(n)) return { i: 'lucide:film', c: '#0ea5e9' };
  return { i: 'lucide:file', c: '#6b7280' };
}

async function beforeUpload(file: File) {
  if (file.size > 25 * 1024 * 1024) {
    message.warning(`${file.name} 超过 25MB`);
    return false;
  }
  uploading.value++;
  try {
    await uploadCloudFile(file, scope.value, parentId.value || undefined);
    message.success(`已上传 ${file.name}`);
    await load();
  } finally {
    uploading.value--;
  }
  return false;
}

// ===== 新建文件夹 / 编辑 =====
const folderOpen = ref(false);
const folderName = ref('');
async function saveFolder() {
  if (!folderName.value.trim()) {
    message.warning('请输入文件夹名称');
    return;
  }
  await createCloudFolder({ scope: scope.value, parentId: parentId.value || undefined, name: folderName.value.trim() });
  folderOpen.value = false;
  folderName.value = '';
  await load();
}

const editOpen = ref(false);
const editing = reactive({ id: 0, name: '', description: '', folder: false });
function edit(f: CloudApi.File) {
  Object.assign(editing, { id: f.id, name: f.name, description: f.description || '', folder: f.folder });
  editOpen.value = true;
}
async function saveEdit() {
  await updateCloudFile({ id: editing.id, name: editing.name, description: editing.description });
  editOpen.value = false;
  message.success('已保存');
  await load();
}

async function toggleAi(f: CloudApi.File, v: boolean) {
  await updateCloudFile({ id: f.id, aiSendable: v });
  f.aiSendable = v;
  message.success(v ? 'AI 可以在客户需要时发送这个文件' : '已关闭');
}

async function remove(f: CloudApi.File) {
  await deleteCloudFile(f.id);
  message.success('已删除');
  await load();
}

onMounted(load);
</script>

<template>
  <div class="cd">
    <div class="cd__seg">
      <button type="button" :class="{ on: scope === 'enterprise' }" @click="switchScope('enterprise')">企业云盘</button>
      <button type="button" :class="{ on: scope === 'personal' }" @click="switchScope('personal')">个人云盘</button>
    </div>
    <div class="mb-4 mt-4 flex items-baseline gap-2">
      <h2 class="m-0 text-2xl font-bold text-gray-900">云盘文件</h2>
      <span class="text-sm text-gray-500">管理企业及个人资料名称、类型、文件</span>
    </div>

    <div class="mb-3 flex flex-wrap items-center gap-3">
      <template v-if="canEdit">
      <Button @click="folderOpen = true">新建文件夹 <IconifyIcon icon="lucide:folder" class="ml-1 inline size-4" /></Button>
      <Upload :show-upload-list="false" multiple :before-upload="beforeUpload">
        <Button type="primary" :loading="uploading > 0">上传文件 <IconifyIcon icon="lucide:plus" class="ml-1 inline size-4" /></Button>
      </Upload>
      </template>
      <span v-else class="text-xs text-gray-500">企业云盘由管理员维护，你可以查看和下载</span>
      <div class="flex-1"></div>
      <Input v-model:value="keyword" allow-clear class="w-56" placeholder="搜索文件名称" @press-enter="load" @change="(e: any) => !e.target.value && load()">
        <template #suffix><IconifyIcon icon="lucide:search" class="size-3.5 cursor-pointer text-gray-400" @click="load" /></template>
      </Input>
    </div>

    <div v-if="scope === 'enterprise'" class="mb-3 text-xs text-gray-500">
      企业云盘对所有同事可见。打开「AI 可发送」并写清描述（例如「2026 年吹风机报价单」），在「智能体 → 配技能」开启「AI 发送文件」后，AI 会在客户需要时把文件作为附件发出。
    </div>

    <div v-if="parentId" class="mb-2 flex flex-wrap items-center gap-1 text-sm">
      <a @click="openFolder(0)">{{ scope === 'enterprise' ? '企业云盘' : '个人云盘' }}</a>
      <template v-for="p in path" :key="p.id">
        <IconifyIcon icon="lucide:chevron-right" class="size-3.5 text-gray-400" />
        <a @click="openFolder(p.id)">{{ p.name }}</a>
      </template>
    </div>

    <Table :columns="columns" :data-source="paged" :loading="loading" :pagination="false" row-key="id" size="middle" :scroll="{ x: 860 }">
      <template #headerCell="{ column }">
        <span v-if="column.key === 'name' || column.key === 'description'" class="inline-flex items-center gap-1">
          {{ column.title }}
          <Tooltip :title="column.key === 'name' ? '点击文件夹打开' : 'AI 根据描述判断什么时候发送这个文件'">
            <IconifyIcon icon="lucide:info" class="size-3.5 text-gray-400" />
          </Tooltip>
        </span>
      </template>
      <template #bodyCell="{ column, record }">
        <div v-if="column.key === 'name'" class="flex min-w-0 items-center gap-2">
          <IconifyIcon :icon="icon(record as any).i" class="size-4 shrink-0" :style="{ color: icon(record as any).c }" />
          <a v-if="record.folder" class="truncate" @click="openFolder(record.id)">{{ record.name }}</a>
          <a v-else :href="record.url" target="_blank" class="truncate text-gray-800">{{ record.name }}</a>
        </div>
        <span v-else-if="column.key === 'description'" class="text-gray-500">{{ record.description || '-' }}</span>
        <template v-else-if="column.key === 'size'">{{ record.folder ? '-' : size(record.size) }}</template>
        <template v-else-if="column.key === 'ai'">
          <Switch v-if="!record.folder" size="small" :disabled="!canEdit" :checked="!!record.aiSendable" @change="(v: any) => toggleAi(record as any, !!v)" />
        </template>
        <template v-else-if="column.key === 'updateTime'">{{ fmtDateTime(record.updateTime) }}</template>
        <div v-else-if="column.key === 'action'" class="flex gap-3">
          <a v-if="!record.folder" :href="record.url" target="_blank">下载</a>
          <template v-if="canEdit">
          <a @click="edit(record as any)">编辑</a>
          <Popconfirm :title="record.folder ? '删除文件夹及里面的所有文件？' : '删除这个文件？'" @confirm="remove(record as any)">
            <a class="text-red-500">删除</a>
          </Popconfirm>
          </template>
        </div>
      </template>
      <template #emptyText>
        <Empty :image="Empty.PRESENTED_IMAGE_SIMPLE">
          <template #description>
            <div class="font-medium text-gray-700">暂无资料</div>
            <div class="text-xs text-gray-500">{{ scope === 'enterprise' ? '支持查看企业云盘中的共享资料文件' : '个人云盘中的文件只有你自己能看到' }}</div>
          </template>
        </Empty>
      </template>
    </Table>

    <div class="mt-4 flex items-center justify-end gap-2 text-sm text-gray-500">
      共 {{ rows.length }} 项数据
      <Pagination v-model:current="page.no" v-model:page-size="page.size" :total="rows.length" show-size-changer show-quick-jumper size="small" />
    </div>

    <Modal v-model:open="folderOpen" title="新建文件夹" ok-text="创建" @ok="saveFolder">
      <Input v-model:value="folderName" :maxlength="200" placeholder="文件夹名称" @press-enter="saveFolder" />
    </Modal>

    <Modal v-model:open="editOpen" :title="editing.folder ? '编辑文件夹' : '编辑文件'" ok-text="保存" @ok="saveEdit">
      <div class="mb-1 text-sm">名称</div>
      <Input v-model:value="editing.name" :maxlength="200" />
      <template v-if="!editing.folder">
        <div class="mb-1 mt-4 text-sm">描述</div>
        <Textarea v-model:value="editing.description" :rows="3" :maxlength="250" show-count placeholder="例如：2026 年吹风机报价单，客户询价时发送" />
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.cd {
  padding: 4px 16px 18px;
}

.cd__seg {
  display: inline-flex;
  padding: 4px;
  background: #F3F4F6;
  border: 1px solid #eceef3;
  border-radius: 14px;
}

.cd__seg button {
  padding: 8px 38px;
  font-size: 15px;
  color: #4b5563;
  cursor: pointer;
  background: none;
  border: 0;
  border-radius: 10px;
}

.cd__seg button.on {
  font-weight: 600;
  color: #2563EB;
  background: #fff;
  box-shadow: 0 1px 3px rgb(0 0 0 / 6%);
}
</style>
