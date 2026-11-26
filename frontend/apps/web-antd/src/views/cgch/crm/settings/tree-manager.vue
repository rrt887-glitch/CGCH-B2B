<script lang="ts" setup>
/**
 * 树形配置项管理（标签、客户分组通用）：列表展示为树，支持新增子项、修改、删除。
 */
import type { CgchCrmApi } from '#/api/crm/cgch';

import { computed, onMounted, reactive, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import {
  Button,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Table,
  TreeSelect,
} from 'ant-design-vue';

import {
  createTreeNode,
  deleteTreeNode,
  getTreeList,
  updateTreeNode,
} from '#/api/crm/cgch';

const props = defineProps<{
  kind: 'customer-group' | 'tag';
  label: string;
  withColor?: boolean;
}>();

interface Row extends CgchCrmApi.TreeNode {
  children?: Row[];
}

const loading = ref(false);
const list = ref<CgchCrmApi.TreeNode[]>([]);

/** 平铺列表组装成树 */
const tree = computed<Row[]>(() => {
  const map = new Map<number, Row>();
  list.value.forEach((n) => map.set(n.id!, { ...n, children: [] }));
  const roots: Row[] = [];
  map.forEach((n) => {
    const parent = n.parentId ? map.get(n.parentId) : undefined;
    (parent ? parent.children! : roots).push(n);
  });
  const prune = (rows: Row[]) =>
    rows.forEach((r) => {
      if (r.children!.length === 0) delete r.children;
      else prune(r.children!);
    });
  prune(roots);
  return roots;
});

const parentOptions = computed(() => {
  const toOption = (rows: Row[]): any[] =>
    rows.map((r) => ({
      title: r.name,
      value: r.id,
      disabled: r.id === form.id,
      children: r.children ? toOption(r.children) : undefined,
    }));
  return [{ title: '顶级', value: 0, children: toOption(tree.value) }];
});

const columns = computed(() => [
  { title: '名称', dataIndex: 'name', key: 'name' },
  ...(props.withColor ? [{ title: '颜色', dataIndex: 'color', key: 'color', width: 120 }] : []),
  { title: '排序', dataIndex: 'sort', key: 'sort', width: 80 },
  { title: '操作', key: 'action', width: 220 },
]);

async function load() {
  loading.value = true;
  try {
    list.value = await getTreeList(props.kind);
  } finally {
    loading.value = false;
  }
}

const open = ref(false);
const saving = ref(false);
const form = reactive<CgchCrmApi.TreeNode>({ name: '', parentId: 0, sort: 0, color: '' });

function openForm(row?: Row, parentId = 0) {
  Object.assign(form, {
    id: row?.id,
    name: row?.name ?? '',
    parentId: row?.parentId ?? parentId,
    sort: row?.sort ?? 0,
    color: row?.color ?? '',
  });
  open.value = true;
}

async function save() {
  if (!form.name.trim()) {
    message.warning(`请输入${props.label}名称`);
    return;
  }
  saving.value = true;
  try {
    const data = { ...form, name: form.name.trim() };
    await (form.id ? updateTreeNode(props.kind, data) : createTreeNode(props.kind, data));
    message.success('已保存');
    open.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function remove(row: Row) {
  await deleteTreeNode(props.kind, row.id!);
  message.success('已删除');
  await load();
}

const PRESET_COLORS = [
  '#007AFF',
  '#34C759',
  '#FF9500',
  '#FF3B30',
  '#AF52DE',
  '#5856D6',
  '#00C7BE',
  '#FF2D55',
];

onMounted(load);
</script>

<template>
  <div>
    <div class="mb-3 flex justify-end">
      <Button type="primary" shape="round" @click="openForm()">
        <IconifyIcon icon="lucide:plus" class="mr-1 inline size-4 align-[-3px]" />新增{{ label }}
      </Button>
    </div>
    <Table
      :columns="columns"
      :data-source="tree"
      :loading="loading"
      :pagination="false"
      row-key="id"
      default-expand-all-rows
      size="middle"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'name'">
          <span class="font-medium text-[#1D1D1F]">{{ record.name }}</span>
        </template>
        <template v-else-if="column.key === 'color'">
          <span v-if="record.color" class="inline-flex items-center gap-2">
            <span
              class="size-3.5 rounded-full shadow-inner"
              :style="{ background: record.color }"
            ></span>
            <span class="text-xs text-[#6E6E73] tabular-nums">{{ record.color }}</span>
          </span>
          <span v-else class="text-[#C7C7CC]">-</span>
        </template>
        <template v-else-if="column.key === 'action'">
          <div class="flex items-center gap-3">
            <a class="ap-crm-link" @click="openForm(undefined, record.id)">新增子项</a>
            <a class="ap-crm-link" @click="openForm(record as any)">编辑</a>
            <Popconfirm :title="`确定删除「${record.name}」吗？`" @confirm="remove(record as any)">
              <a class="ap-crm-link ap-crm-link--danger">删除</a>
            </Popconfirm>
          </div>
        </template>
      </template>
      <template #emptyText>
        <div class="py-10 text-center text-[13px] text-[#86868B]">
          暂无{{ label }}，点击右上角「新增{{ label }}」创建
        </div>
      </template>
    </Table>

    <Modal
      v-model:open="open"
      :title="form.id ? `编辑${label}` : `新增${label}`"
      ok-text="保存"
      :width="440"
      wrap-class-name="ap-modal-sheet"
      :confirm-loading="saving"
      @ok="save"
    >
      <div class="ap-sheet-body">
        <div class="ap-sheet-field">
          <label class="ap-sheet-label">{{ label }}名称</label>
          <Input v-model:value="form.name" :maxlength="64" :placeholder="`请输入${label}名称`" />
        </div>
        <div class="ap-sheet-field">
          <label class="ap-sheet-label">所属上级</label>
          <TreeSelect v-model:value="form.parentId" :tree-data="parentOptions" tree-default-expand-all />
        </div>
        <div v-if="withColor" class="ap-sheet-field">
          <label class="ap-sheet-label">标签色彩</label>
          <div class="flex flex-wrap items-center gap-2 pb-1">
            <button
              v-for="c in PRESET_COLORS"
              :key="c"
              type="button"
              class="size-6 cursor-pointer rounded-full border-0 transition-transform hover:scale-110"
              :style="{
                background: c,
                boxShadow: form.color === c ? `0 0 0 2px #fff, 0 0 0 4px ${c}` : 'none',
              }"
              :aria-label="`选择颜色 ${c}`"
              @click="form.color = c"
            ></button>
          </div>
          <Input v-model:value="form.color" placeholder="或输入自定义色值，如 #007AFF" />
        </div>
        <div class="ap-sheet-field">
          <label class="ap-sheet-label">
            <span>排序权重</span>
            <span class="ap-sheet-optional">数字越小越靠前</span>
          </label>
          <InputNumber v-model:value="form.sort" :min="0" class="w-full" />
        </div>
      </div>
    </Modal>
  </div>
</template>
