<script lang="ts" setup>
/**
 * 违禁词：AI 回复中出现这些词时不会自动发出，转为草稿等业务员确认。
 */
import type { ChannelAiApi } from '#/api/channel/ai';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { Alert, Button, Card, Form, FormItem, Input, message, Modal, Popconfirm, Space, Switch, Table } from 'ant-design-vue';

import { deleteForbidden, getForbiddenList, saveForbidden } from '#/api/channel/ai';

defineOptions({ name: 'CgchAgentForbidden' });

const loading = ref(false);
const list = ref<ChannelAiApi.ForbiddenWord[]>([]);
const columns = [
  { title: '违禁词', dataIndex: 'word', key: 'word', width: 240 },
  { title: '说明', dataIndex: 'remark', key: 'remark' },
  { title: '启用', key: 'enabled', width: 80 },
  { title: '操作', key: 'action', width: 140 },
];

async function load() {
  loading.value = true;
  try {
    list.value = await getForbiddenList();
  } finally {
    loading.value = false;
  }
}

const open = ref(false);
const form = reactive<ChannelAiApi.ForbiddenWord>({ word: '', remark: '', enabled: true });
function openForm(row?: ChannelAiApi.ForbiddenWord) {
  Object.assign(form, { id: undefined, word: '', remark: '', enabled: true }, row ?? {});
  open.value = true;
}
async function save() {
  if (!form.word.trim()) {
    message.warning('请填写违禁词');
    return;
  }
  await saveForbidden({ ...form });
  message.success('已保存');
  open.value = false;
  await load();
}
async function toggle(row: ChannelAiApi.ForbiddenWord, enabled: boolean) {
  await saveForbidden({ ...row, enabled });
  row.enabled = enabled;
}
async function remove(row: ChannelAiApi.ForbiddenWord) {
  await deleteForbidden(row.id!);
  await load();
}

onMounted(load);
</script>

<template>
  <Page auto-content-height>
    <Card title="违禁词配置" :bordered="false">
      <template #extra><Button type="primary" @click="openForm()">新增违禁词</Button></template>
      <Alert
        class="mb-4"
        type="info"
        show-icon
        message="AI 的回复里出现这些词（不区分大小写）时，不会自动发给客户，而是留作草稿由业务员确认。适合拦截不能承诺的内容，例如 guarantee、free sample、exclusive、100%。"
      />
      <Table :columns="columns" :data-source="list" :loading="loading" :pagination="false" row-key="id">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'enabled'">
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
        <template #emptyText>还没有违禁词</template>
      </Table>
    </Card>
    <Modal v-model:open="open" :title="form.id ? '修改违禁词' : '新增违禁词'" @ok="save">
      <Form layout="vertical">
        <FormItem label="违禁词" required><Input v-model:value="form.word" :maxlength="100" /></FormItem>
        <FormItem label="说明"><Input v-model:value="form.remark" :maxlength="200" placeholder="例如：不能承诺质量保证" /></FormItem>
      </Form>
    </Modal>
  </Page>
</template>
