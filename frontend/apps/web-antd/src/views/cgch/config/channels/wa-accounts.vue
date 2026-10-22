<script lang="ts" setup>
/**
 * 渠道管理中的 WhatsApp 部分：绑定 Meta 官方 Cloud API 号码，并给出回调地址配置说明。
 */
import type { ChannelWaApi } from '#/api/channel/whatsapp';

import { onMounted, reactive, ref } from 'vue';

import {
  Alert,
  Button,
  Card,
  Col,
  Collapse,
  CollapsePanel,
  Form,
  FormItem,
  Input,
  InputPassword,
  message,
  Modal,
  Popconfirm,
  Row,
  Space,
  Switch,
  Table,
  Tag,
  TypographyParagraph,
} from 'ant-design-vue';

import { createWaAccount, deleteWaAccount, getWaAccountList, updateWaAccount } from '#/api/channel/whatsapp';

const loading = ref(false);
const accounts = ref<ChannelWaApi.Account[]>([]);
const origin = window.location.origin;
const webhookUrl = (a: ChannelWaApi.Account) => `${origin}${a.webhookPath}`;

const columns = [
  { title: '号码', key: 'phone' },
  { title: '启用', key: 'enabled', width: 80 },
  { title: '回调状态', key: 'webhook', width: 200 },
  { title: '操作', key: 'action', width: 200 },
];

async function load() {
  loading.value = true;
  try {
    accounts.value = await getWaAccountList();
  } finally {
    loading.value = false;
  }
}

const open = ref(false);
const saving = ref(false);
const emptyForm = (): ChannelWaApi.Account => ({
  name: '',
  phoneNumberId: '',
  wabaId: '',
  accessToken: '',
  appSecret: '',
  verifyToken: '',
  apiBase: '',
  apiVersion: '',
  enabled: true,
});
const form = reactive<ChannelWaApi.Account>(emptyForm());

function openForm(row?: ChannelWaApi.Account) {
  Object.assign(form, emptyForm(), row ? { ...row, accessToken: '', appSecret: '' } : {});
  open.value = true;
}

async function save() {
  if (!form.phoneNumberId || !form.wabaId) {
    message.warning('请填写 Phone number ID 和 WhatsApp Business Account ID');
    return;
  }
  if (!form.id && (!form.accessToken || !form.appSecret)) {
    message.warning('请填写访问令牌和应用密钥');
    return;
  }
  saving.value = true;
  try {
    await (form.id ? updateWaAccount({ ...form }) : createWaAccount({ ...form }));
    message.success(form.id ? '已保存' : '已绑定，请按说明在 Meta 后台配置回调地址');
    open.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function toggle(row: ChannelWaApi.Account, enabled: boolean) {
  await updateWaAccount({ ...row, enabled, accessToken: '', appSecret: '' });
  row.enabled = enabled;
}

async function remove(row: ChannelWaApi.Account) {
  await deleteWaAccount(row.id!);
  message.success('已解绑');
  await load();
}

const guide = ref<ChannelWaApi.Account>();

onMounted(load);

defineExpose({ openForm, accounts });
</script>

<template>
  <Card title="WhatsApp（官方 Cloud API）" :bordered="false" class="mt-4">
    <template #extra><Button type="primary" @click="openForm()">绑定号码</Button></template>
    <Table :columns="columns" :data-source="accounts" :loading="loading" :pagination="false" row-key="id">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'phone'">
          <div class="font-medium">{{ record.displayPhone || record.phoneNumberId }}</div>
          <div class="text-xs text-gray-500">{{ record.name }} · Phone number ID {{ record.phoneNumberId }}</div>
        </template>
        <template v-else-if="column.key === 'enabled'">
          <Switch :checked="record.enabled" size="small" @change="(v: any) => toggle(record, !!v)" />
        </template>
        <template v-else-if="column.key === 'webhook'">
          <Tag v-if="record.lastWebhookTime" color="green">已收到推送</Tag>
          <Tag v-else color="orange">尚未收到推送</Tag>
          <div v-if="record.lastWebhookTime" class="text-xs text-gray-500">{{ record.lastWebhookTime }}</div>
        </template>
        <template v-else-if="column.key === 'action'">
          <Space>
            <Button size="small" type="link" @click="guide = record">回调配置</Button>
            <Button size="small" type="link" @click="openForm(record)">修改</Button>
            <Popconfirm title="解绑后会话与消息记录也会删除，确定吗？" @confirm="remove(record)">
              <Button size="small" type="link" danger>解绑</Button>
            </Popconfirm>
          </Space>
        </template>
      </template>
      <template #emptyText>还没有绑定 WhatsApp 号码</template>
    </Table>

    <Modal v-model:open="open" :title="form.id ? '修改 WhatsApp 号码' : '绑定 WhatsApp 号码'" :width="640" :confirm-loading="saving" @ok="save">
      <Alert
        class="mb-4"
        type="info"
        show-icon
        message="需要先在 Meta 开发者后台创建应用并添加 WhatsApp 产品。下面的信息都可以在「WhatsApp → API 设置」和「应用设置 → 基本」里找到。"
      />
      <Form layout="vertical">
        <Row :gutter="16">
          <Col :span="12"><FormItem label="显示名称"><Input v-model:value="form.name" placeholder="例如：销售一部" /></FormItem></Col>
          <Col :span="12"><FormItem label="Phone number ID" required><Input v-model:value="form.phoneNumberId" /></FormItem></Col>
          <Col :span="12"><FormItem label="WhatsApp Business Account ID" required><Input v-model:value="form.wabaId" /></FormItem></Col>
          <Col :span="12">
            <FormItem label="Webhook 验证令牌（不填自动生成）"><Input v-model:value="form.verifyToken" /></FormItem>
          </Col>
          <Col :span="24">
            <FormItem :label="form.id ? '访问令牌（不改请留空）' : '访问令牌'" :required="!form.id" help="建议用「系统用户」生成的永久令牌，临时令牌 24 小时后失效">
              <InputPassword v-model:value="form.accessToken" autocomplete="new-password" />
            </FormItem>
          </Col>
          <Col :span="24">
            <FormItem :label="form.id ? '应用密钥 App Secret（不改请留空）' : '应用密钥 App Secret'" :required="!form.id" help="用于校验 Meta 推送的签名，防止伪造消息">
              <InputPassword v-model:value="form.appSecret" autocomplete="new-password" />
            </FormItem>
          </Col>
        </Row>
        <Collapse ghost>
          <CollapsePanel key="adv" header="高级设置">
            <Row :gutter="16">
              <Col :span="16"><FormItem label="Graph API 地址"><Input v-model:value="form.apiBase" placeholder="https://graph.facebook.com" /></FormItem></Col>
              <Col :span="8"><FormItem label="API 版本"><Input v-model:value="form.apiVersion" placeholder="v24.0" /></FormItem></Col>
            </Row>
          </CollapsePanel>
        </Collapse>
      </Form>
    </Modal>

    <Modal :open="!!guide" title="在 Meta 后台配置回调" :footer="null" :width="640" @cancel="guide = undefined">
      <template v-if="guide">
        <ol class="list-decimal space-y-3 pl-5 text-sm">
          <li>打开 Meta 开发者后台 → 你的应用 → WhatsApp → 配置。</li>
          <li>
            回调地址填写：
            <TypographyParagraph copyable class="!mb-0 rounded bg-gray-100 px-2 py-1 font-mono">{{ webhookUrl(guide) }}</TypographyParagraph>
            <div class="text-xs text-gray-500">必须是公网可访问的 HTTPS 地址；本地开发可用内网穿透工具临时转发。</div>
          </li>
          <li>
            验证令牌填写：
            <TypographyParagraph copyable class="!mb-0 rounded bg-gray-100 px-2 py-1 font-mono">{{ guide.verifyToken }}</TypographyParagraph>
          </li>
          <li>点击「验证并保存」，然后在 Webhook 字段里订阅 <b>messages</b>。</li>
          <li>用手机给这个号码发一条 WhatsApp，几秒后在「消息工作台」就能看到。</li>
        </ol>
      </template>
    </Modal>
  </Card>
</template>
