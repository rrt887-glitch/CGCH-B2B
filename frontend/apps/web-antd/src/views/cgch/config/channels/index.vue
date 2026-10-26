<script lang="ts" setup>
/**
 * 渠道账号：一张总表列出所有可接入的渠道（顺序与原网站一致），每个渠道下面列出已连接的账号。
 * 邮箱用「邮箱地址 + 授权码」绑定（高级配置里可改收发信服务器），Gmail / Outlook 配置了授权应用时走一键授权；
 * WhatsApp 走 Meta 官方共存模式扫码连接；Facebook 主页、Instagram 走 Facebook 登录授权；
 * 阿里国际站、中国制造网通过留资邮箱接入；官网 IM 绑定域名后把嵌入代码放到网站上；YouTube、TikTok 授权后拉取视频评论进会话。
 */
import type { ChannelMailApi } from '#/api/channel/mail';
import type { ChannelWaApi } from '#/api/channel/whatsapp';

import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import {
  Button,
  Input,
  InputNumber,
  InputPassword,
  message,
  Modal,
  Popconfirm,
  Popover,
  Select,
  Spin,
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
import { getOAuthAuthorizeUrl, getOAuthProviders } from '#/api/channel/oauth';
import type { SocialPlatform } from '#/api/channel/social';
import { getSocialPlatforms, pollSocial } from '#/api/channel/social';

import LeadRules from './lead-rules.vue';
import WaAccounts from './wa-accounts.vue';
import WebImModal from './web-im-modal.vue';
import SocialConnectModal from './social-connect-modal.vue';

defineOptions({ name: 'CgchConfigChannels' });

/** 常见邮箱的收发信服务器；guide 是「绑定指引」里的说明 */
interface MailProvider {
  imap: [string, number, boolean];
  smtp: [string, number, boolean];
  secret: string;
  guide: string;
}
const MAIL: Record<string, MailProvider> = {
  '163': { imap: ['imap.163.com', 993, true], smtp: ['smtp.163.com', 465, true], secret: '授权码', guide: '登录网页版网易邮箱 → 设置 → POP3/SMTP/IMAP，开启 IMAP/SMTP 服务，按提示获取授权码。' },
  gmail: { imap: ['imap.gmail.com', 993, true], smtp: ['smtp.gmail.com', 465, true], secret: '应用专用密码', guide: '在 Google 账号中开启两步验证，然后在「安全性 → 应用专用密码」生成 16 位密码，填到下方。' },
  '126': { imap: ['imap.126.com', 993, true], smtp: ['smtp.126.com', 465, true], secret: '授权码', guide: '登录网页版 126 邮箱 → 设置 → POP3/SMTP/IMAP，开启 IMAP/SMTP 服务，按提示获取授权码。' },
  outlook: { imap: ['outlook.office365.com', 993, true], smtp: ['smtp.office365.com', 587, false], secret: '密码', guide: '个人 Outlook 账号需在网页版设置中开启 IMAP；企业账号可能被管理员禁用了密码登录，建议使用一键授权。' },
  '263': { imap: ['imap.263.net', 993, true], smtp: ['smtp.263.net', 465, true], secret: '密码', guide: '使用 263 企业邮箱的登录密码；如管理员开启了客户端授权码，请填写授权码。' },
  aliyun: { imap: ['imap.qiye.aliyun.com', 993, true], smtp: ['smtp.qiye.aliyun.com', 465, true], secret: '密码', guide: '使用阿里邮箱的登录密码；开启了三方客户端安全密码的，请填写安全密码。' },
  custom: { imap: ['', 993, true], smtp: ['', 465, true], secret: '密码或授权码', guide: '任何支持 IMAP / SMTP 的邮箱都可以绑定，在「高级配置」里填写邮箱服务商提供的收发信服务器。' },
  exmail: { imap: ['imap.exmail.qq.com', 993, true], smtp: ['smtp.exmail.qq.com', 465, true], secret: '客户端专用密码', guide: '登录腾讯企业邮箱 → 设置 → 账户 → 开启安全登录后生成「客户端专用密码」。' },
  boss: { imap: ['p80w.chinaemail.cn', 143, false], smtp: ['s80w.chinaemail.cn', 465, true], secret: '授权码', guide: '在 BOSS 邮箱网页版的设置中开启 IMAP/SMTP，获取客户端授权码。' },
  qq: { imap: ['imap.qq.com', 993, true], smtp: ['smtp.qq.com', 465, true], secret: '授权码', guide: '登录网页版 QQ 邮箱 → 设置 → 账号，开启 IMAP/SMTP 服务，按提示获取授权码。' },
};

type Kind = 'im' | 'lead' | 'mail' | 'meta' | 'social' | 'soon' | 'wa' | 'wa-official';
interface Channel {
  key: string;
  name: string;
  abbr: string;
  color: string;
  kind: Kind;
  /** 邮箱服务商 */
  provider?: string;
  /** 一键授权的服务商 */
  oauth?: string;
  platform?: 'instagram' | 'messenger' | 'tiktok' | 'youtube';
}

/** 渠道顺序与原网站一致 */
const CHANNELS: Channel[] = [
  { key: 'whatsapp', name: 'WhatsApp', abbr: 'WA', color: '#25d366', kind: 'wa' },
  { key: 'instagram', name: 'Instagram', abbr: 'IG', color: '#d62976', kind: 'meta', platform: 'instagram' },
  { key: '163', name: '网易邮箱', abbr: '163', color: '#d7000f', kind: 'mail', provider: '163' },
  { key: 'gmail', name: '谷歌邮箱', abbr: 'G', color: '#ea4335', kind: 'mail', provider: 'gmail', oauth: 'google' },
  { key: 'tiktok', name: 'TikTok', abbr: 'TT', color: '#111111', kind: 'social', platform: 'tiktok' },
  { key: 'youtube', name: 'YouTube', abbr: 'YT', color: '#ff0000', kind: 'social', platform: 'youtube' },
  { key: 'messenger', name: 'Facebook Page', abbr: 'f', color: '#1877f2', kind: 'meta', platform: 'messenger' },
  { key: '126', name: '126 邮箱', abbr: '126', color: '#1a7f37', kind: 'mail', provider: '126' },
  { key: 'outlook', name: 'Outlook邮箱', abbr: 'O', color: '#0f6cbd', kind: 'mail', provider: 'outlook', oauth: 'microsoft' },
  { key: 'wa-official', name: 'WhatsApp Business Official API', abbr: 'B', color: '#25d366', kind: 'wa-official' },
  { key: 'im', name: '官网IM', abbr: 'Ai', color: '#1d4ed8', kind: 'im' },
  { key: 'alibaba', name: '阿里国际', abbr: '阿', color: '#ff6a00', kind: 'lead' },
  { key: 'mic', name: '中国制造网', abbr: 'M', color: '#c8102e', kind: 'lead' },
  { key: '263', name: '263邮箱', abbr: '263', color: '#c8102e', kind: 'mail', provider: '263' },
  { key: 'aliyun', name: '阿里邮箱', abbr: 'M', color: '#ff6a00', kind: 'mail', provider: 'aliyun' },
  { key: 'custom', name: '其他邮箱', abbr: '@', color: '#0ea5a4', kind: 'mail', provider: 'custom' },
  { key: 'exmail', name: '腾讯企业邮箱', abbr: 'M', color: '#1677ff', kind: 'mail', provider: 'exmail' },
  { key: 'boss', name: 'BOSS邮箱', abbr: 'BOSS', color: '#00a6a7', kind: 'mail', provider: 'boss' },
  { key: 'qq', name: 'QQ邮箱', abbr: 'QQ', color: '#12b7f5', kind: 'mail', provider: 'qq' },
];

const loading = ref(true);
const mailAccounts = ref<ChannelMailApi.Account[]>([]);
const waRef = ref<InstanceType<typeof WaAccounts>>();
const waAccounts = ref<ChannelWaApi.Account[]>([]);
const oauthProviders = ref<string[]>([]);
const leadOpen = ref(false);
const imOpen = ref(false);
const socialOpen = ref(false);
const socialPlatform = ref<SocialPlatform>('youtube');
const socialEnabled = ref<SocialPlatform[]>([]);
const imRef = ref<InstanceType<typeof WebImModal>>();
const webSites = computed(() => waAccounts.value.filter((a) => a.platform === 'web'));
const mailboxes = computed(() => mailAccounts.value.map((a) => a.email));

async function loadMail() {
  mailAccounts.value = await getMailAccountList();
}

function onWaChanged() {
  waAccounts.value = waRef.value?.accounts ?? [];
}

/** 一行已连接账号 */
interface AccountRow {
  id: number;
  label: string;
  sub?: string;
  status: { color: string; text: string; tip?: string };
  mail?: ChannelMailApi.Account;
  wa?: ChannelWaApi.Account;
}

function mailRow(a: ChannelMailApi.Account): AccountRow {
  let status = { color: 'gray', text: '等待首次收信' } as AccountRow['status'];
  if (!a.enabled) status = { color: 'gray', text: '已暂停' };
  else if (a.lastSyncError) status = { color: 'red', text: '收信失败', tip: a.lastSyncError };
  else if (a.lastSyncTime) status = { color: 'green', text: '正常', tip: `上次收信 ${a.lastSyncTime}` };
  return { id: a.id!, label: a.email, sub: a.authType === 'oauth2' ? '一键授权' : undefined, status, mail: a };
}

function waRow(a: ChannelWaApi.Account): AccountRow {
  let status = { color: 'orange', text: '等待消息' } as AccountRow['status'];
  if (!a.enabled) status = { color: 'gray', text: '已停用' };
  else if (a.lastWebhookTime) status = { color: 'green', text: '正常', tip: `最近消息 ${a.lastWebhookTime}` };
  return { id: a.id!, label: a.displayPhone || a.name || a.phoneNumberId, sub: a.name && a.name !== a.displayPhone ? a.name : undefined, status, wa: a };
}

function accountsOf(c: Channel): AccountRow[] {
  const wa = (a: ChannelWaApi.Account) => !a.platform || a.platform === 'whatsapp';
  switch (c.kind) {
    case 'mail':
      return mailAccounts.value.filter((a) => (a.provider || 'custom') === c.provider).map(mailRow);
    case 'meta':
      return waAccounts.value.filter((a) => a.platform === c.platform).map(waRow);
    case 'wa':
      return waAccounts.value.filter((a) => wa(a) && a.authType === 'oauth').map(waRow);
    case 'wa-official':
      return waAccounts.value.filter((a) => wa(a) && a.authType !== 'oauth').map(waRow);
    case 'social':
      return waAccounts.value.filter((a) => a.platform === c.platform).map((a) => {
        const r = waRow(a);
        if (a.enabled && a.lastWebhookTime) r.status = { color: 'green', text: '正常', tip: `上次拉取评论 ${a.lastWebhookTime}` };
        else if (a.enabled) r.status = { color: 'orange', text: '等待拉取评论' };
        return r;
      });
    case 'im':
      return webSites.value.map((a) => ({ ...waRow(a), label: a.displayPhone || '', sub: a.name }));
    default:
      return [];
  }
}

const total = computed(() => mailAccounts.value.length + waAccounts.value.length);

// ===== 连接新账号 =====
function connect(c: Channel) {
  switch (c.kind) {
    case 'mail':
      if (c.oauth && oauthProviders.value.includes(c.oauth)) startOAuth(c.oauth);
      else openMail(c);
      break;
    case 'wa':
      waRef.value?.connect('whatsapp');
      break;
    case 'wa-official':
      waRef.value?.connectOfficial();
      break;
    case 'meta':
      waRef.value?.connect(c.platform as 'instagram' | 'messenger');
      break;
    case 'lead':
      leadOpen.value = true;
      break;
    case 'social':
      socialPlatform.value = c.platform as SocialPlatform;
      socialOpen.value = true;
      break;
    case 'im':
      imOpen.value = true;
      if (!webSites.value.length) imRef.value?.add();
      break;
  }
}

async function startOAuth(provider: string) {
  // 先同步打开窗口，避免被浏览器拦截弹窗
  const win = window.open('about:blank', 'cgch-mail-oauth', 'width=560,height=720');
  try {
    const url = await getOAuthAuthorizeUrl(provider);
    if (win) win.location.href = url;
    else window.location.href = url;
  } catch (error) {
    win?.close();
    throw error;
  }
}

function onOAuthMessage(e: MessageEvent) {
  if (e.data?.type === 'cgch-social-oauth') {
    if (e.data.ok) {
      socialOpen.value = false;
      message.success('授权成功，正在拉取视频评论');
      waRef.value?.load();
    }
    return;
  }
  if (e.data?.type !== 'cgch-mail-oauth') return;
  if (e.data.ok) {
    message.success(e.data.email ? `已授权绑定 ${e.data.email}` : '已授权绑定');
    loadMail();
  } else {
    message.error(e.data.message || '授权失败');
  }
}

// ===== 邮箱绑定弹窗 =====
const mailOpen = ref(false);
const advanced = ref(false);
const saving = ref(false);
const testing = ref(false);
const mailChannel = ref<Channel>(CHANNELS[2]!);
const form = reactive<ChannelMailApi.Account>({ email: '', provider: '163', imapHost: '', imapPort: 993, smtpHost: '', smtpPort: 465 });
const provider = computed(() => MAIL[form.provider || 'custom'] ?? MAIL.custom!);
const isOAuth = computed(() => form.authType === 'oauth2');
const SSL_OPTIONS = [
  { value: true, label: 'SSL' },
  { value: false, label: '无' },
];

function openMail(c: Channel, row?: ChannelMailApi.Account) {
  mailChannel.value = c;
  const p = MAIL[c.provider!] ?? MAIL.custom!;
  Object.assign(form, {
    id: undefined,
    authType: undefined,
    displayName: '',
    username: '',
    enabled: true,
    email: '',
    provider: c.provider,
    imapHost: p.imap[0],
    imapPort: p.imap[1],
    imapSsl: p.imap[2],
    smtpHost: p.smtp[0],
    smtpPort: p.smtp[1],
    smtpSsl: p.smtp[2],
    ...row,
    password: '',
  });
  advanced.value = false;
  mailOpen.value = true;
}

function validate() {
  if (!form.email?.includes('@')) {
    message.warning('请输入正确的邮箱地址');
    return false;
  }
  if (!form.id && !form.password && !isOAuth.value) {
    message.warning(`请输入${provider.value.secret}`);
    return false;
  }
  if (!form.imapHost || !form.smtpHost) {
    advanced.value = true;
    message.warning('请在高级配置中填写收发信服务器');
    return false;
  }
  return true;
}

async function testMail() {
  if (!validate()) return;
  testing.value = true;
  try {
    await testMailAccount({ ...form });
    message.success('连接成功：收信与发信都正常');
  } finally {
    testing.value = false;
  }
}

async function saveMail() {
  if (!validate()) return;
  saving.value = true;
  try {
    await (form.id ? updateMailAccount({ ...form }) : createMailAccount({ ...form }));
    message.success(form.id ? '已保存' : '绑定成功，几分钟内会开始收信');
    mailOpen.value = false;
    await loadMail();
  } finally {
    saving.value = false;
  }
}

// ===== 账号操作 =====
const busy = ref<string>();

async function pollNow(a: ChannelWaApi.Account) {
  busy.value = `poll-${a.id}`;
  try {
    const n = await pollSocial(a.id!);
    message.success(n > 0 ? `拉取到 ${n} 条新评论` : '没有新评论');
    await waRef.value?.load();
  } finally {
    busy.value = undefined;
  }
}

async function syncNow(a: ChannelMailApi.Account) {
  busy.value = `sync-${a.id}`;
  try {
    const n = await syncMailAccount(a.id!);
    message.success(n > 0 ? `收到 ${n} 封新邮件` : '没有新邮件');
    await loadMail();
  } finally {
    busy.value = undefined;
  }
}

async function toggle(r: AccountRow) {
  if (r.mail) {
    await updateMailAccount({ ...r.mail, enabled: !r.mail.enabled, password: '' });
    message.success(r.mail.enabled ? '已暂停自动收信' : '已开启自动收信');
    await loadMail();
  } else if (r.wa) {
    await waRef.value?.toggle(r.wa, !r.wa.enabled);
    await waRef.value?.load();
  }
}

async function unbind(r: AccountRow) {
  if (r.mail) {
    await deleteMailAccount(r.mail.id!);
    message.success('已解绑');
    await loadMail();
  } else if (r.wa) {
    await waRef.value?.remove(r.wa);
  }
}

function edit(c: Channel, r: AccountRow) {
  if (r.mail) {
    if (r.mail.authType === 'oauth2' && c.oauth) startOAuth(c.oauth);
    else openMail(c, r.mail);
  } else if (r.wa?.platform === 'web') {
    imOpen.value = true;
    imRef.value?.edit(r.wa);
  } else if (r.wa) {
    waRef.value?.openForm(r.wa, (r.wa.platform as any) || 'whatsapp');
  }
}

onMounted(async () => {
  window.addEventListener('message', onOAuthMessage);
  try {
    await loadMail();
  } finally {
    loading.value = false;
  }
  oauthProviders.value = await getOAuthProviders().catch(() => []);
  socialEnabled.value = await getSocialPlatforms().catch(() => []);
});
onBeforeUnmount(() => window.removeEventListener('message', onOAuthMessage));
</script>

<template>
  <div class="ch-page">
    <div class="ch-head">
      <div class="flex items-center gap-3">
        <h2 class="ch-title">渠道账号</h2>
        <span class="ch-count">
          <IconifyIcon icon="lucide:info" class="size-4 text-gray-400" />
          已绑定 <b>{{ total }}</b> 个账号
        </span>
      </div>
      <Button @click="leadOpen = true">设置留资邮箱</Button>
    </div>

    <Spin :spinning="loading" wrapper-class-name="ch-spin">
      <div class="ch-table">
        <div class="ch-row ch-row--head">
          <div class="ch-col-name">渠道</div>
          <div class="ch-col-status">状态</div>
          <div class="ch-col-account">账号</div>
          <div class="ch-col-op">操作</div>
        </div>
        <div class="ch-body">
          <template v-for="c in CHANNELS" :key="c.key">
            <div class="ch-row">
              <div class="ch-col-name">
                <span class="ch-badge" :style="{ background: c.color }" :class="{ 'is-long': c.abbr.length > 2 }">{{ c.abbr }}</span>
                <span class="truncate">{{ c.name }}</span>
              </div>
              <div class="ch-col-status">
                <span v-if="c.kind === 'lead'" class="text-xs text-gray-500">通过留资邮箱接入</span>
                <span v-else-if="c.kind === 'soon'" class="text-xs text-gray-400">即将开放</span>
              </div>
              <div class="ch-col-account"></div>
              <div class="ch-col-op">
                <span v-if="c.kind === 'soon'" class="text-gray-300">即将开放</span>
                <a v-else-if="c.kind === 'lead'" @click="connect(c)">设置留资邮箱</a>
                <a v-else @click="connect(c)">连接新账号</a>
              </div>
            </div>

            <div v-for="r in accountsOf(c)" :key="`${c.key}-${r.id}`" class="ch-row ch-row--sub">
              <div class="ch-col-name">
                <IconifyIcon icon="lucide:corner-down-right" class="ml-2 size-4 text-gray-300" />
              </div>
              <div class="ch-col-status">
                <Tooltip :title="r.status.tip">
                  <span class="ch-status" :class="`is-${r.status.color}`">{{ r.status.text }}</span>
                </Tooltip>
              </div>
              <div class="ch-col-account">
                <span class="truncate">{{ r.label }}</span>
                <span v-if="r.sub" class="ml-2 text-xs text-gray-400">{{ r.sub }}</span>
              </div>
              <div class="ch-col-op ch-ops">
                <a v-if="r.mail" :class="{ 'opacity-50': busy === `sync-${r.id}` }" @click="syncNow(r.mail)">立即收信</a>
                <a v-if="r.wa?.platform === 'web'" @click="imOpen = true">嵌入代码</a>
                <a v-else-if="r.wa?.platform === 'youtube' || r.wa?.platform === 'tiktok'" :class="{ 'opacity-50': busy === `poll-${r.id}` }" @click="pollNow(r.wa)">立即同步</a>
                <a v-else-if="r.wa && r.wa.authType !== 'oauth'" @click="waRef?.showGuide(r.wa)">回调配置</a>
                <a v-if="r.mail || (r.wa && r.wa.authType !== 'oauth')" @click="edit(c, r)">{{ r.mail?.authType === 'oauth2' ? '重新授权' : '修改' }}</a>
                <a @click="toggle(r)">{{ (r.mail ?? r.wa)?.enabled ? (r.mail ? '暂停' : '停用') : '启用' }}</a>
                <Popconfirm
                  :title="r.mail ? '解绑后已同步的邮件也会删除，确定吗？' : '解绑后会话与消息记录也会删除，确定吗？'"
                  @confirm="unbind(r)"
                >
                  <a class="text-red-500">解绑</a>
                </Popconfirm>
              </div>
            </div>
          </template>
        </div>
      </div>
    </Spin>

    <WaAccounts ref="waRef" headless :meta-o-auth="oauthProviders.includes('meta')" @changed="onWaChanged" />
    <LeadRules v-model:open="leadOpen" :mailboxes="mailboxes" />
    <WebImModal ref="imRef" v-model:open="imOpen" :sites="webSites" @changed="waRef?.load()" />
    <SocialConnectModal v-model:open="socialOpen" :platform="socialPlatform" :enabled="socialEnabled.includes(socialPlatform)" />

    <Modal v-model:open="mailOpen" :footer="null" :width="476" centered :closable="false">
      <div class="mb-5 flex items-center gap-3">
        <span class="text-base font-semibold">{{ form.id ? '修改' : '添加' }}{{ mailChannel.name }}账号</span>
        <Popover :title="`${mailChannel.name}绑定指引`" placement="bottom" :overlay-style="{ maxWidth: '320px' }">
          <template #content>
            <div class="text-sm leading-6">{{ provider.guide }}</div>
          </template>
          <a class="inline-flex items-center gap-1 text-sm"><IconifyIcon icon="lucide:link" class="size-3.5" />绑定指引</a>
        </Popover>
      </div>

      <template v-if="!advanced">
        <div class="ch-field">
          <div class="ch-label">邮箱地址</div>
          <Input v-model:value="form.email" size="large" placeholder="请输入邮箱地址" :disabled="!!form.id" />
        </div>
        <div v-if="!isOAuth" class="ch-field">
          <div class="ch-label">{{ provider.secret }}</div>
          <InputPassword
            v-model:value="form.password"
            size="large"
            autocomplete="new-password"
            :placeholder="form.id ? '不修改请留空' : `请输入${provider.secret}`"
          />
        </div>
        <div class="ch-divider">
          <a @click="advanced = true">高级配置 <IconifyIcon icon="lucide:chevrons-down" class="size-4" /></a>
        </div>
      </template>

      <template v-else>
        <div class="ch-field">
          <div class="ch-label"><i>*</i> 协议类型</div>
          <label class="inline-flex items-center gap-2 text-sm"><span class="ch-radio"></span>IMAP</label>
        </div>
        <div class="ch-server">
          <div class="ch-label"><i>*</i> 收邮件（IMAP）服务器</div>
          <div class="ch-label"><i>*</i> 加密</div>
          <div class="ch-label"><i>*</i> 端口</div>
          <Input v-model:value="form.imapHost" placeholder="imap.example.com" />
          <Select :value="form.imapSsl as any" :options="SSL_OPTIONS as any" @update:value="(v: any) => (form.imapSsl = v)" />
          <InputNumber v-model:value="form.imapPort" :min="1" :max="65535" :controls="false" class="w-full" />
          <div class="ch-label mt-2"><i>*</i> 发邮件（SMTP）服务器</div>
          <div class="ch-label mt-2"><i>*</i> 加密</div>
          <div class="ch-label mt-2"><i>*</i> 端口</div>
          <Input v-model:value="form.smtpHost" placeholder="smtp.example.com" />
          <Select :value="form.smtpSsl as any" :options="SSL_OPTIONS as any" @update:value="(v: any) => (form.smtpSsl = v)" />
          <InputNumber v-model:value="form.smtpPort" :min="1" :max="65535" :controls="false" class="w-full" />
        </div>
        <div class="ch-field mt-4">
          <div class="ch-label">发件人名称</div>
          <Input v-model:value="form.displayName" placeholder="客户看到的名字，默认用邮箱地址" />
        </div>
      </template>

      <div class="mt-6 flex items-center justify-end gap-3">
        <a v-if="!advanced" class="mr-auto text-sm" :class="{ 'opacity-50': testing }" @click="testMail">测试连接</a>
        <Button size="large" class="ch-btn-gray" @click="advanced ? (advanced = false) : (mailOpen = false)">
          {{ advanced ? '返回' : '取 消' }}
        </Button>
        <Button size="large" type="primary" :loading="saving" :disabled="!form.email" @click="saveMail">确认绑定</Button>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.ch-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 16px);
  padding: 20px 16px 12px;
  background: #fff;
  border-radius: 12px;
}

.ch-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 16px;
}

.ch-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #111827;
}

.ch-count {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  font-size: 14px;
  color: #4b5563;
}

.ch-count b {
  color: #155dfc;
}

.ch-spin {
  flex: 1;
  min-height: 0;
}

.ch-spin :deep(.ant-spin-container) {
  height: 100%;
}

.ch-table {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.ch-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.ch-row {
  display: flex;
  align-items: center;
  min-height: 43px;
  padding: 0 12px;
  font-size: 14px;
  color: #1f2937;
  border-bottom: 1px solid #f0f1f3;
}

.ch-row--head {
  min-height: 42px;
  color: #6b7280;
  background: #f5f7fa;
  border-bottom: 0;
  border-radius: 6px;
}

.ch-row--sub {
  min-height: 38px;
  font-size: 13px;
  background: #fafbfc;
}

.ch-col-name {
  display: flex;
  flex: 0 0 180px;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.ch-col-status {
  flex: 0 0 90px;
}

.ch-col-account {
  display: flex;
  flex: 1;
  align-items: center;
  min-width: 0;
}

.ch-col-op {
  flex: 0 0 auto;
  text-align: right;
}

.ch-col-op a {
  color: #155dfc;
}

.ch-ops {
  display: flex;
  gap: 14px;
}

.ch-badge {
  display: inline-flex;
  flex: 0 0 24px;
  align-items: center;
  justify-content: center;
  height: 24px;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  border-radius: 6px;
}

.ch-badge.is-long {
  font-size: 8px;
  letter-spacing: -0.3px;
}

.ch-status {
  font-size: 12px;
}

.ch-status::before {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 5px;
  vertical-align: middle;
  content: '';
  border-radius: 50%;
}

.ch-status.is-green::before {
  background: #22c55e;
}

.ch-status.is-red {
  color: #dc2626;
}

.ch-status.is-red::before {
  background: #dc2626;
}

.ch-status.is-orange::before {
  background: #f59e0b;
}

.ch-status.is-gray {
  color: #9ca3af;
}

.ch-status.is-gray::before {
  background: #d1d5db;
}

.ch-field {
  margin-bottom: 18px;
}

.ch-label {
  margin-bottom: 8px;
  font-size: 14px;
  color: #374151;
}

.ch-label i {
  font-style: normal;
  color: #ef4444;
}

.ch-divider {
  display: flex;
  gap: 12px;
  align-items: center;
  font-size: 14px;
}

.ch-divider::before,
.ch-divider::after {
  flex: 1;
  height: 1px;
  content: '';
  background: #e5e7eb;
}

.ch-divider a {
  display: inline-flex;
  gap: 2px;
  align-items: center;
  color: #374151;
}

.ch-radio {
  width: 16px;
  height: 16px;
  border: 5px solid #155dfc;
  border-radius: 50%;
}

.ch-server {
  display: grid;
  grid-template-columns: 1.6fr 1fr 0.6fr;
  gap: 0 12px;
  margin-top: 4px;
}

.ch-btn-gray {
  min-width: 98px;
  color: #155dfc;
  background: #f3f4f6;
  border: 0;
}

@media (max-width: 640px) {
  .ch-col-name {
    flex-basis: 130px;
  }

  .ch-col-status {
    flex-basis: 64px;
  }

  .ch-ops {
    flex-wrap: wrap;
    gap: 8px;
  }
}
</style>
