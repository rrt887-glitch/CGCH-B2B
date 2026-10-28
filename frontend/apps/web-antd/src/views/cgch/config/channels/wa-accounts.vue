<script lang="ts" setup>
/**
 * 渠道管理中的 Meta 即时通讯账号：WhatsApp（官方 Cloud API）、Facebook 主页私信、Instagram 私信。
 * Facebook / Instagram 推荐用「Facebook 登录授权」一键接入；也可以手动填写主页令牌。
 */
import { fmtDateTime } from '#/utils/datetime';
import type { ChannelWaApi } from '#/api/channel/whatsapp';

import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';

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

import type { WaSignupConfig } from '#/api/channel/oauth';

import { getMetaAuthorizeUrl, getWaSignupConfig } from '#/api/channel/oauth';
import { createWaAccount, deleteWaAccount, getWaAccountList, updateWaAccount } from '#/api/channel/whatsapp';

import WaConnectModal from './wa-connect-modal.vue';

type Platform = 'instagram' | 'messenger' | 'whatsapp';

/** headless：只提供弹窗（渠道总表里使用），不显示自带的账号表格 */
const props = defineProps<{ headless?: boolean; metaOAuth?: boolean }>();
const emit = defineEmits<{ changed: [] }>();

const PLATFORM: Record<Platform, { color: string; idLabel: string; label: string }> = {
  whatsapp: { label: 'WhatsApp', color: 'green', idLabel: 'Phone number ID' },
  messenger: { label: 'Facebook 主页', color: 'blue', idLabel: '主页编号 Page ID' },
  instagram: { label: 'Instagram', color: 'magenta', idLabel: 'Instagram 专业账号编号' },
};
const platformOf = (a: ChannelWaApi.Account): Platform => (a.platform as Platform) || 'whatsapp';

const loading = ref(false);
const accounts = ref<ChannelWaApi.Account[]>([]);
const origin = window.location.origin;
const webhookUrl = (a: ChannelWaApi.Account) => `${origin}${a.webhookPath}`;

const columns = [
  { title: '账号', key: 'phone' },
  { title: '启用', key: 'enabled', width: 80 },
  { title: '回调状态', key: 'webhook', width: 200 },
  { title: '操作', key: 'action', width: 200 },
];

async function load() {
  loading.value = true;
  try {
    accounts.value = await getWaAccountList();
    emit('changed');
  } finally {
    loading.value = false;
  }
}

const open = ref(false);
const saving = ref(false);
const emptyForm = (): ChannelWaApi.Account => ({
  platform: 'whatsapp',
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
const formPlatform = computed(() => platformOf(form));

function openForm(row?: ChannelWaApi.Account, platform: Platform = 'whatsapp') {
  Object.assign(form, emptyForm(), row ? { ...row, accessToken: '', appSecret: '' } : { platform });
  open.value = true;
}

async function save() {
  const wa = formPlatform.value === 'whatsapp';
  if (!form.phoneNumberId || (wa && !form.wabaId)) {
    message.warning(wa ? '请填写 Phone number ID 和 WhatsApp Business Account ID' : `请填写${PLATFORM[formPlatform.value].idLabel}`);
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

// ===== Facebook 登录授权 =====
async function startMetaOAuth() {
  const win = window.open('about:blank', 'cgch-meta-oauth', 'width=600,height=760');
  try {
    const url = await getMetaAuthorizeUrl();
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

function onMessage(e: MessageEvent) {
  if (e.data?.type !== 'cgch-meta-oauth') return;
  if (e.data.ok) {
    message.success('Facebook 授权完成，已接入主页和关联的 Instagram');
    load();
  } else {
    message.error('Facebook 授权失败，请查看授权窗口中的说明');
  }
}

// ===== WhatsApp 官方接入（共存模式扫码 / 开通新号码） =====
const signupCfg = ref<WaSignupConfig>({ enabled: false });
const connectOpen = ref(false);

function openManual() {
  connectOpen.value = false;
  openForm(undefined, 'whatsapp');
}

/** 渠道总表点击：WhatsApp 打开扫码连接窗口；Facebook / Instagram 已配置时走授权，否则手动填写 */
function connect(platform: Platform) {
  if (platform === 'whatsapp') {
    connectOpen.value = true;
  } else if (props.metaOAuth) {
    startMetaOAuth();
  } else {
    openForm(undefined, platform);
  }
}

onMounted(() => {
  window.addEventListener('message', onMessage);
  load();
  getWaSignupConfig()
    .then((c) => (signupCfg.value = c))
    .catch(() => {});
});
onBeforeUnmount(() => window.removeEventListener('message', onMessage));

/** 渠道总表「WhatsApp Business Official API」：开通新号码；没配置嵌入式注册时手动填写 */
function connectOfficial() {
  if (signupCfg.value.enabled) {
    connectOpen.value = true;
  } else {
    openForm(undefined, 'whatsapp');
  }
}

defineExpose({ openForm, connect, connectOfficial, accounts, load, toggle, remove, showGuide: (a: ChannelWaApi.Account) => (guide.value = a) });
</script>

<template>
  <div>
  <Card v-if="!headless" title="WhatsApp / Facebook / Instagram" :bordered="false" class="mt-4">
    <template #extra>
      <Space>
        <Button v-if="metaOAuth" @click="startMetaOAuth">Facebook 登录授权</Button>
        <Button type="primary" @click="connectOpen = true">连接 WhatsApp</Button>
        <Button @click="openForm()">手动绑定</Button>
      </Space>
    </template>
    <Alert
      v-if="metaOAuth"
      class="mb-4"
      type="info"
      show-icon
      message="点「Facebook 登录授权」，登录后勾选要接入的主页，一次即可接入这些主页的私信，以及主页关联的 Instagram 专业账号的私信。"
    />
    <Table :columns="columns" :data-source="accounts" :loading="loading" :pagination="false" row-key="id">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'phone'">
          <div class="flex items-center gap-2">
            <Tag :color="PLATFORM[platformOf(record as any)].color" class="m-0">{{ PLATFORM[platformOf(record as any)].label }}</Tag>
            <span class="font-medium">{{ record.displayPhone || record.phoneNumberId }}</span>
            <Tag v-if="record.authType === 'oauth'" class="m-0">授权接入</Tag>
          </div>
          <div class="text-xs text-gray-500">{{ record.name }} · {{ PLATFORM[platformOf(record as any)].idLabel }} {{ record.phoneNumberId }}</div>
        </template>
        <template v-else-if="column.key === 'enabled'">
          <Switch :checked="record.enabled" size="small" @change="(v: any) => toggle(record as any, !!v)" />
        </template>
        <template v-else-if="column.key === 'webhook'">
          <Tag v-if="record.lastWebhookTime" color="green">已收到推送</Tag>
          <Tag v-else color="orange">尚未收到推送</Tag>
          <div v-if="record.lastWebhookTime" class="text-xs text-gray-500">{{ fmtDateTime(record.lastWebhookTime) }}</div>
        </template>
        <template v-else-if="column.key === 'action'">
          <Space>
            <Button size="small" type="link" @click="guide = record as any">回调配置</Button>
            <Button v-if="record.authType !== 'oauth'" size="small" type="link" @click="openForm(record as any)">修改</Button>
            <Popconfirm title="解绑后会话与消息记录也会删除，确定吗？" @confirm="remove(record as any)">
              <Button size="small" type="link" danger>解绑</Button>
            </Popconfirm>
          </Space>
        </template>
      </template>
      <template #emptyText>还没有绑定账号</template>
    </Table>
  </Card>

    <Modal
      v-model:open="open"
      :title="`${form.id ? '修改' : '绑定'}${PLATFORM[formPlatform].label}${formPlatform === 'whatsapp' ? ' 号码' : ' 账号'}`"
      :width="640"
      :confirm-loading="saving"
      @ok="save"
    >
      <Alert
        v-if="formPlatform === 'whatsapp'"
        class="mb-4"
        type="info"
        show-icon
        message="需要先在 Meta 开发者后台创建应用并添加 WhatsApp 产品。下面的信息都可以在「WhatsApp → API 设置」和「应用设置 → 基本」里找到。"
      />
      <Alert
        v-else
        class="mb-4"
        type="info"
        show-icon
        :message="
          formPlatform === 'messenger'
            ? '手动接入：在 Meta 开发者后台的应用里添加 Messenger 产品，生成主页访问令牌（需 pages_messaging 权限）。推荐让管理员配置 Meta 应用后使用「Facebook 登录授权」。'
            : '手动接入：Instagram 需是专业账号并关联 Facebook 主页；访问令牌填关联主页的主页令牌（需 instagram_manage_messages 权限）。'
        "
      />
      <Form layout="vertical">
        <Row :gutter="16">
          <Col :span="12"><FormItem label="显示名称"><Input v-model:value="form.name" placeholder="例如：销售一部" /></FormItem></Col>
          <Col :span="12">
            <FormItem :label="PLATFORM[formPlatform].idLabel" required><Input v-model:value="form.phoneNumberId" /></FormItem>
          </Col>
          <Col v-if="formPlatform === 'whatsapp'" :span="12">
            <FormItem label="WhatsApp Business Account ID" required><Input v-model:value="form.wabaId" /></FormItem>
          </Col>
          <Col :span="12">
            <FormItem label="Webhook 验证令牌（不填自动生成）"><Input v-model:value="form.verifyToken" /></FormItem>
          </Col>
          <Col :span="24">
            <FormItem
              :label="form.id ? '访问令牌（不改请留空）' : formPlatform === 'whatsapp' ? '访问令牌' : '主页访问令牌'"
              :required="!form.id"
              :help="formPlatform === 'whatsapp' ? '建议用「系统用户」生成的永久令牌，临时令牌 24 小时后失效' : '建议用长期令牌换取的主页令牌，不会过期'"
            >
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
      <template v-if="guide && guide.authType === 'oauth'">
        <p class="text-sm">
          这个账号通过 Facebook 登录授权接入，系统已自动订阅主页的消息推送。管理员只需在 Meta 应用里配置一次应用级回调（所有授权接入的主页和 Instagram 共用）：
        </p>
        <TypographyParagraph copyable class="!mb-2 rounded bg-gray-100 px-2 py-1 font-mono">{{ webhookUrl(guide) }}</TypographyParagraph>
        <p class="text-xs text-gray-500">
          验证令牌为服务器环境变量 CGCH_META_WEBHOOK_VERIFY_TOKEN 的值。Messenger 订阅 messages、messaging_postbacks、message_echoes、message_deliveries、message_reads；Instagram 订阅 messages。
        </p>
      </template>
      <template v-else-if="guide">
        <ol class="list-decimal space-y-3 pl-5 text-sm">
          <li>
            打开 Meta 开发者后台 → 你的应用 →
            {{ platformOf(guide) === 'whatsapp' ? 'WhatsApp → 配置' : platformOf(guide) === 'messenger' ? 'Messenger → Messenger API 设置' : 'Instagram → API 设置' }}。
          </li>
          <li>
            回调地址填写：
            <TypographyParagraph copyable class="!mb-0 rounded bg-gray-100 px-2 py-1 font-mono">{{ webhookUrl(guide) }}</TypographyParagraph>
            <div class="text-xs text-gray-500">必须是公网可访问的 HTTPS 地址；本地开发可用内网穿透工具临时转发。</div>
          </li>
          <li>
            验证令牌填写：
            <TypographyParagraph copyable class="!mb-0 rounded bg-gray-100 px-2 py-1 font-mono">{{ guide.verifyToken }}</TypographyParagraph>
          </li>
          <li v-if="platformOf(guide) === 'whatsapp'">点击「验证并保存」，然后在 Webhook 字段里订阅 <b>messages</b>。</li>
          <li v-else>点击「验证并保存」，订阅 <b>messages</b>、<b>messaging_postbacks</b>、<b>message_echoes</b>、<b>message_reads</b>，并把主页添加到订阅列表。</li>
          <li>用手机给这个账号发一条消息，几秒后在「消息工作台」就能看到。</li>
        </ol>
      </template>
    </Modal>
    <WaConnectModal v-model:open="connectOpen" :config="signupCfg" @done="load" @manual="openManual" />
  </div>
</template>
