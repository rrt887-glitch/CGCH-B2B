<script lang="ts" setup>
/**
 * 渠道管理：渠道总表 + 邮箱（IMAP/SMTP，Gmail/Outlook 支持 OAuth 授权）+ WhatsApp + 留资邮箱。
 */
import type { ChannelMailApi } from '#/api/channel/mail';
import { getOAuthAuthorizeUrl, getOAuthProviders } from '#/api/channel/oauth';

import ChannelCatalog from './channel-catalog.vue';
import LeadRules from './lead-rules.vue';
import WaAccounts from './wa-accounts.vue';

import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Alert,
  Button,
  Card,
  Col,
  Form,
  FormItem,
  Input,
  InputNumber,
  InputPassword,
  message,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
} from 'ant-design-vue';

import {
  createMailAccount,
  deleteMailAccount,
  getMailAccountList,
  syncMailAccount,
  testMailAccount,
  updateMailAccount,
} from '#/api/channel/mail';

defineOptions({ name: 'CgchConfigChannels' });

/** 常见邮箱服务商的服务器设置；tip 提示需要在邮箱里开启什么 */
const PROVIDERS: Record<string, { label: string; imap: [string, number]; smtp: [string, number]; tip?: string }> = {
  gmail: { label: 'Gmail', imap: ['imap.gmail.com', 993], smtp: ['smtp.gmail.com', 465], tip: '需开启两步验证并生成「应用专用密码」' },
  outlook: { label: 'Outlook 国际版', imap: ['outlook.office365.com', 993], smtp: ['smtp.office365.com', 587], tip: '个人账号需开启 IMAP；企业账号可能被管理员禁用基础认证' },
  qq: { label: 'QQ 邮箱', imap: ['imap.qq.com', 993], smtp: ['smtp.qq.com', 465], tip: '在 设置 → 账户 中开启 IMAP/SMTP 并获取授权码' },
  exmail: { label: '腾讯企业邮箱', imap: ['imap.exmail.qq.com', 993], smtp: ['smtp.exmail.qq.com', 465] },
  '163': { label: '163 邮箱', imap: ['imap.163.com', 993], smtp: ['smtp.163.com', 465], tip: '需开启 IMAP/SMTP 并使用授权码' },
  '126': { label: '126 邮箱', imap: ['imap.126.com', 993], smtp: ['smtp.126.com', 465], tip: '需开启 IMAP/SMTP 并使用授权码' },
  '263': { label: '263 企业邮箱', imap: ['imap.263.net', 993], smtp: ['smtp.263.net', 465] },
  aliyun: { label: '阿里邮箱', imap: ['imap.qiye.aliyun.com', 993], smtp: ['smtp.qiye.aliyun.com', 465] },
  custom: { label: '其他邮箱（自定义）', imap: ['', 993], smtp: ['', 465] },
};
const providerOptions = Object.entries(PROVIDERS).map(([value, p]) => ({ value, label: p.label }));

const loading = ref(false);
const accounts = ref<ChannelMailApi.Account[]>([]);

const columns = [
  { title: '邮箱', key: 'email' },
  { title: '服务商', key: 'provider', width: 140 },
  { title: '自动收信', key: 'enabled', width: 100 },
  { title: '状态', key: 'status', width: 220 },
  { title: '未回复', dataIndex: 'unrepliedCount', key: 'unreplied', width: 90 },
  { title: '操作', key: 'action', width: 220 },
];

async function load() {
  loading.value = true;
  try {
    accounts.value = await getMailAccountList();
  } finally {
    loading.value = false;
  }
}

// ===== 绑定 / 修改 =====
const open = ref(false);
const saving = ref(false);
const testing = ref(false);
const emptyForm = (): ChannelMailApi.Account => ({
  provider: 'gmail',
  email: '',
  displayName: '',
  imapHost: PROVIDERS.gmail!.imap[0],
  imapPort: PROVIDERS.gmail!.imap[1],
  imapSsl: true,
  smtpHost: PROVIDERS.gmail!.smtp[0],
  smtpPort: PROVIDERS.gmail!.smtp[1],
  smtpSsl: true,
  username: '',
  password: '',
  enabled: true,
});
const form = reactive<ChannelMailApi.Account>(emptyForm());

function applyProvider(key: string) {
  const p = PROVIDERS[key];
  if (!p || key === 'custom') return;
  form.imapHost = p.imap[0];
  form.imapPort = p.imap[1];
  form.imapSsl = true;
  form.smtpHost = p.smtp[0];
  form.smtpPort = p.smtp[1];
  form.smtpSsl = p.smtp[1] === 465; // 587 走 STARTTLS
}

function openForm(row?: ChannelMailApi.Account, provider?: string) {
  Object.assign(form, emptyForm(), row ? { ...row, password: '' } : {});
  if (!row && provider) {
    form.provider = provider;
    applyProvider(provider);
    if (provider === 'custom') {
      form.imapHost = '';
      form.smtpHost = '';
    }
  }
  open.value = true;
}

const isOAuth = computed(() => form.authType === 'oauth2');

// ===== 渠道总表 / OAuth 授权 / 留资邮箱 =====
const waRef = ref<InstanceType<typeof WaAccounts>>();
const waAccounts = computed(() => waRef.value?.accounts ?? []);
const oauthProviders = ref<string[]>([]);
const leadOpen = ref(false);
const mailboxes = computed(() => accounts.value.map((a) => a.email));

async function startOAuth(provider: string) {
  // 先同步打开窗口，避免被浏览器拦截弹窗
  const win = window.open('about:blank', 'cgch-mail-oauth', 'width=560,height=720');
  try {
    const url = await getOAuthAuthorizeUrl(provider);
    if (win) {
      win.location.href = url;
    } else {
      window.location.href = url;
    }
  } catch (error) {
    win?.close();
    throw error;
  }
}

function onOAuthMessage(e: MessageEvent) {
  if (e.data?.type !== 'cgch-mail-oauth') return;
  if (e.data.ok) {
    message.success(e.data.email ? `已授权绑定 ${e.data.email}` : '已授权绑定');
    load();
  } else {
    message.error(e.data.message || '授权失败');
  }
}

function validate() {
  if (!form.email || !form.imapHost || !form.smtpHost) {
    message.warning('请填写邮箱地址和服务器');
    return false;
  }
  if (!form.id && !form.password && !isOAuth.value) {
    message.warning('请填写密码或授权码');
    return false;
  }
  return true;
}

async function test() {
  if (!validate()) return;
  testing.value = true;
  try {
    await testMailAccount({ ...form });
    message.success('连接成功：收信与发信都正常');
  } finally {
    testing.value = false;
  }
}

async function save() {
  if (!validate()) return;
  saving.value = true;
  try {
    await (form.id ? updateMailAccount({ ...form }) : createMailAccount({ ...form }));
    message.success(form.id ? '已保存' : '已绑定，几分钟内会开始收信');
    open.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function toggle(row: ChannelMailApi.Account, enabled: boolean) {
  await updateMailAccount({ ...row, enabled, password: '' });
  row.enabled = enabled;
  message.success(enabled ? '已开启自动收信' : '已暂停自动收信');
}

const syncing = ref<number>();
async function syncNow(row: ChannelMailApi.Account) {
  syncing.value = row.id;
  try {
    const n = await syncMailAccount(row.id!);
    message.success(n > 0 ? `收到 ${n} 封新邮件` : '没有新邮件');
    await load();
  } finally {
    syncing.value = undefined;
  }
}

async function remove(row: ChannelMailApi.Account) {
  await deleteMailAccount(row.id!);
  message.success('已解绑');
  await load();
}

onMounted(async () => {
  window.addEventListener('message', onOAuthMessage);
  await load();
  oauthProviders.value = await getOAuthProviders().catch(() => []);
});
onBeforeUnmount(() => window.removeEventListener('message', onOAuthMessage));
</script>

<template>
  <Page auto-content-height>
    <ChannelCatalog
      :mail-accounts="accounts"
      :wa-accounts="waAccounts"
      :oauth-providers="oauthProviders"
      @mail="(p) => openForm(undefined, p)"
      @oauth="startOAuth"
      @wa="waRef?.openForm()"
      @lead-rules="leadOpen = true"
    />

    <Card title="邮箱" :bordered="false" class="mt-4">
      <template #extra>
        <Button type="primary" @click="openForm()">绑定邮箱</Button>
      </template>
      <Alert
        class="mb-4"
        type="info"
        show-icon
        message="绑定后系统每 5 分钟自动收信，可在「渠道消息 → 邮件工作台」查看与回复。"
      />
      <Table :columns="columns" :data-source="accounts" :loading="loading" :pagination="false" row-key="id">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'email'">
            <div class="font-medium">{{ record.email }}</div>
            <div v-if="record.displayName" class="text-xs text-gray-500">{{ record.displayName }}</div>
          </template>
          <template v-else-if="column.key === 'provider'">
            {{ PROVIDERS[record.provider]?.label ?? record.provider }}
            <Tag v-if="record.authType === 'oauth2'" color="blue" class="ml-1">OAuth</Tag>
          </template>
          <template v-else-if="column.key === 'enabled'">
            <Switch :checked="record.enabled" size="small" @change="(v: any) => toggle(record, !!v)" />
          </template>
          <template v-else-if="column.key === 'status'">
            <Tooltip v-if="record.lastSyncError" :title="record.lastSyncError">
              <Tag color="red">收信失败</Tag>
            </Tooltip>
            <Tag v-else-if="record.lastSyncTime" color="green">正常</Tag>
            <Tag v-else>等待首次收信</Tag>
            <span v-if="record.lastSyncTime" class="text-xs text-gray-500">{{ record.lastSyncTime }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <Space>
              <Button size="small" type="link" :loading="syncing === record.id" @click="syncNow(record)">立即收信</Button>
              <Button size="small" type="link" @click="openForm(record)">修改</Button>
              <Popconfirm title="解绑后已同步的邮件也会删除，确定吗？" @confirm="remove(record)">
                <Button size="small" type="link" danger>解绑</Button>
              </Popconfirm>
            </Space>
          </template>
        </template>
        <template #emptyText>还没有绑定邮箱</template>
      </Table>
    </Card>

    <WaAccounts ref="waRef" />

    <LeadRules v-model:open="leadOpen" :mailboxes="mailboxes" />

    <Modal v-model:open="open" :title="form.id ? '修改邮箱' : '绑定邮箱'" :width="640" :footer="null">
      <Form layout="vertical">
        <Alert v-if="isOAuth" class="mb-4" type="success" show-icon message="此邮箱通过 OAuth 授权登录，无需密码；授权失效时请在上方重新「授权登录」。" />
        <FormItem v-else label="邮箱服务商">
          <Select v-model:value="form.provider" :options="providerOptions" @change="(v: any) => applyProvider(String(v))" />
        </FormItem>
        <Alert v-if="!isOAuth && PROVIDERS[form.provider || '']?.tip" class="mb-4" type="warning" :message="PROVIDERS[form.provider || '']?.tip" />
        <Row :gutter="16">
          <Col :span="12">
            <FormItem label="邮箱地址" required><Input v-model:value="form.email" placeholder="sales@yourcompany.com" /></FormItem>
          </Col>
          <Col :span="12">
            <FormItem label="发件人名称"><Input v-model:value="form.displayName" placeholder="客户看到的名字" /></FormItem>
          </Col>
          <Col v-if="!isOAuth" :span="12">
            <FormItem :label="form.id ? '密码或授权码（不改请留空）' : '密码或授权码'" :required="!form.id">
              <InputPassword v-model:value="form.password" autocomplete="new-password" />
            </FormItem>
          </Col>
          <Col v-if="!isOAuth" :span="12">
            <FormItem label="登录名（默认同邮箱地址）"><Input v-model:value="form.username" /></FormItem>
          </Col>
          <Col :span="12">
            <FormItem label="IMAP 服务器" required><Input v-model:value="form.imapHost" /></FormItem>
          </Col>
          <Col :span="6">
            <FormItem label="端口"><InputNumber v-model:value="form.imapPort" :min="1" :max="65535" class="w-full" /></FormItem>
          </Col>
          <Col :span="6">
            <FormItem label="SSL"><Switch v-model:checked="form.imapSsl" /></FormItem>
          </Col>
          <Col :span="12">
            <FormItem label="SMTP 服务器" required><Input v-model:value="form.smtpHost" /></FormItem>
          </Col>
          <Col :span="6">
            <FormItem label="端口"><InputNumber v-model:value="form.smtpPort" :min="1" :max="65535" class="w-full" /></FormItem>
          </Col>
          <Col :span="6">
            <FormItem label="SSL"><Switch v-model:checked="form.smtpSsl" /></FormItem>
          </Col>
        </Row>
        <div class="flex justify-end gap-2">
          <Button :loading="testing" @click="test">测试连接</Button>
          <Button @click="open = false">取消</Button>
          <Button type="primary" :loading="saving" @click="save">保存</Button>
        </div>
      </Form>
    </Modal>
  </Page>
</template>
