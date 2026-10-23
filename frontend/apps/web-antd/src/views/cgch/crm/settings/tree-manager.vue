<script lang="ts" setup>
/**
 * 树形配置项管理（标签、客户分组通用）：列表展示为树，支持新增子项、修改、删除。
 */
import type { CgchCrmApi } from '#/api/crm/cgch';

import { computed, onMounted, reactive, ref } from 'vue';

import {
  Button,
  Form,
  FormItem,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Space,
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

onMounted(load);
</script>

<template>
  <div>
    <div class="mb-3 flex justify-end">
      <Button type="primary" @click="openForm()">新增{{ label }}</Button>
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
        <template v-if="column.key === 'color'">
          <span
            v-if="record.color"
            class="inline-block h-4 w-4 rounded align-middle"
            :style="{ background: record.color }"
          ></span>
          <span class="ml-2 align-middle">{{ record.color }}</span>
        </template>
        <template v-else-if="column.key === 'action'">
          <Space>
            <Button size="small" type="link" @click="openForm(undefined, record.id)">新增子项</Button>
            <Button size="small" type="link" @click="openForm(record as any)">修改</Button>
            <Popconfirm :title="`确定删除「${record.name}」吗？`" @confirm="remove(record as any)">
              <Button size="small" type="link" danger>删除</Button>
            </Popconfirm>
          </Space>
        </template>
      </template>
      <template #emptyText>暂无{{ label }}，点右上角新增</template>
    </Table>

    <Modal
      v-model:open="open"
      :title="form.id ? `修改${label}` : `新增${label}`"
      :confirm-loading="saving"
      @ok="save"
    >
      <Form layout="vertical">
        <FormItem label="名称" required>
          <Input v-model:value="form.name" :maxlength="64" :placeholder="`请输入${label}名称`" />
        </FormItem>
        <FormItem label="上级">
          <TreeSelect v-model:value="form.parentId" :tree-data="parentOptions" tree-default-expand-all />
        </FormItem>
        <FormItem v-if="withColor" label="颜色">
          <Input v-model:value="form.color" placeholder="#1677ff" />
        </FormItem>
        <FormItem label="排序（越小越靠前）">
          <InputNumber v-model:value="form.sort" :min="0" class="w-full" />
        </FormItem>
      </Form>
    </Modal>
  </div>
</template>
