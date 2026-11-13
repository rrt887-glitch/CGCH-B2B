<script lang="ts" setup>
/**
 * 渠道账号：一张总表列出所有可接入的渠道（顺序与原网站一致），每个渠道下面列出已连接的账号。
 * 邮箱用「邮箱地址 + 授权码」绑定（高级配置里可改收发信服务器），Gmail / Outlook 配置了授权应用时走一键授权；
 * WhatsApp 用手机号配对码或二维码扫码接入（Evolution API，与原网站一致），WhatsApp Business Official API 走 Meta 官方接口；Facebook 主页、Instagram 走 Facebook 登录授权；
 * 阿里国际站、中国制造网通过留资邮箱接入；官网 IM 绑定域名后把嵌入代码放到网站上；YouTube、TikTok 授权后拉取视频评论进会话。
 */
import type { ChannelMailApi } from '#/api/channel/mail';
import type { ChannelWaApi } from '#/api/channel/whatsapp';

import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import {
  Button,
  Dropdown,
  Input,
  InputNumber,
  InputPassword,
  message,
  Menu,
  MenuDivider,
  MenuItem,
  Modal,
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
import {
  getWaAccountList,
  setWaAssignees,
  syncWaHistory,
} from '#/api/channel/whatsapp';
import { fmtDateTime } from '#/utils/datetime';
import { getSimpleUserList } from '#/api/system/user';

import LeadRules from './lead-rules.vue';
import WaAccounts from './wa-accounts.vue';
import WebImModal from './web-im-modal.vue';
import SocialConnectModal from './social-connect-modal.vue';
import EvoConnectModal from './evo-connect-modal.vue';
import { CHANNEL_ICONS } from '#/assets/channel-icons';

defineOptions({ name: 'CgchConfigChannels' });

/** 常见邮箱的收发信服务器；guide 是「绑定指引」里的说明 */
interface MailProvider {
  imap: [string, number, boolean];
  smtp: [string, number, boolean];
  secret: string;
  guide: string;
}
const MAIL: Record<string, MailProvider> = {
  '163': {
    imap: ['imap.163.com', 993, true],
    smtp: ['smtp.163.com', 465, true],
    secret: '授权码',
    guide:
      '登录网页版网易邮箱 → 设置 → POP3/SMTP/IMAP，开启 IMAP/SMTP 服务，按提示获取授权码。',
  },
  gmail: {
    imap: ['imap.gmail.com', 993, true],
    smtp: ['smtp.gmail.com', 465, true],
    secret: '应用专用密码',
    guide:
      '在 Google 账号中开启两步验证，然后在「安全性 → 应用专用密码」生成 16 位密码，填到下方。',
  },
  '126': {
    imap: ['imap.126.com', 993, true],
    smtp: ['smtp.126.com', 465, true],
    secret: '授权码',
    guide:
      '登录网页版 126 邮箱 → 设置 → POP3/SMTP/IMAP，开启 IMAP/SMTP 服务，按提示获取授权码。',
  },
  outlook: {
    imap: ['outlook.office365.com', 993, true],
    smtp: ['smtp.office365.com', 587, false],
    secret: '密码',
    guide:
      '个人 Outlook 账号需在网页版设置中开启 IMAP；企业账号可能被管理员禁用了密码登录，建议使用一键授权。',
  },
  '263': {
    imap: ['imap.263.net', 993, true],
    smtp: ['smtp.263.net', 465, true],
    secret: '密码',
    guide:
      '使用 263 企业邮箱的登录密码；如管理员开启了客户端授权码，请填写授权码。',
  },
  aliyun: {
    imap: ['imap.qiye.aliyun.com', 993, true],
    smtp: ['smtp.qiye.aliyun.com', 465, true],
    secret: '密码',
    guide:
      '使用阿里邮箱的登录密码；开启了三方客户端安全密码的，请填写安全密码。',
  },
  custom: {
    imap: ['', 993, true],
    smtp: ['', 465, true],
    secret: '密码或授权码',
    guide:
      '任何支持 IMAP / SMTP 的邮箱都可以绑定，在「高级配置」里填写邮箱服务商提供的收发信服务器。',
  },
  exmail: {
    imap: ['imap.exmail.qq.com', 993, true],
    smtp: ['smtp.exmail.qq.com', 465, true],
    secret: '客户端专用密码',
    guide:
      '登录腾讯企业邮箱 → 设置 → 账户 → 开启安全登录后生成「客户端专用密码」。',
  },
  boss: {
    imap: ['p80w.chinaemail.cn', 143, false],
    smtp: ['s80w.chinaemail.cn', 465, true],
    secret: '授权码',
    guide: '在 BOSS 邮箱网页版的设置中开启 IMAP/SMTP，获取客户端授权码。',
  },
  qq: {
    imap: ['imap.qq.com', 993, true],
    smtp: ['smtp.qq.com', 465, true],
    secret: '授权码',
    guide:
      '登录网页版 QQ 邮箱 → 设置 → 账号，开启 IMAP/SMTP 服务，按提示获取授权码。',
  },
};

type Kind =
  | 'im'
  | 'lead'
  | 'mail'
  | 'meta'
  | 'social'
  | 'soon'
  | 'wa'
  | 'wa-official';
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
  {
    key: 'whatsapp',
    name: 'WhatsApp',
    abbr: 'WA',
    color: '#25d366',
    kind: 'wa',
  },
  {
    key: 'instagram',
    name: 'Instagram',
    abbr: 'IG',
    color: '#d62976',
    kind: 'meta',
    platform: 'instagram',
  },
  {
    key: '163',
    name: '网易邮箱',
    abbr: '163',
    color: '#d7000f',
    kind: 'mail',
    provider: '163',
  },
  {
    key: 'gmail',
    name: '谷歌邮箱',
    abbr: 'G',
    color: '#ea4335',
    kind: 'mail',
    provider: 'gmail',
    oauth: 'google',
  },
  {
    key: 'tiktok',
    name: 'TikTok',
    abbr: 'TT',
    color: '#111111',
    kind: 'social',
    platform: 'tiktok',
  },
  {
    key: 'youtube',
    name: 'YouTube',
    abbr: 'YT',
    color: '#ff0000',
    kind: 'social',
    platform: 'youtube',
  },
  {
    key: 'messenger',
    name: 'Facebook Page',
    abbr: 'f',
    color: '#1877f2',
    kind: 'meta',
    platform: 'messenger',
  },
  {
    key: '126',
    name: '126 邮箱',
    abbr: '126',
    color: '#1a7f37',
    kind: 'mail',
    provider: '126',
  },
  {
    key: 'outlook',
    name: 'Outlook邮箱',
    abbr: 'O',
    color: '#0f6cbd',
    kind: 'mail',
    provider: 'outlook',
    oauth: 'microsoft',
  },
  {
    key: 'wa-official',
    name: 'WhatsApp Business Official API',
    abbr: 'B',
    color: '#25d366',
    kind: 'wa-official',
  },
  { key: 'im', name: '官网IM', abbr: 'Ai', color: '#0066cc', kind: 'im' },
  {
    key: 'alibaba',
    name: '阿里国际',
    abbr: '阿',
    color: '#ff6a00',
    kind: 'lead',
  },
  { key: 'mic', name: '中国制造网', abbr: 'M', color: '#c8102e', kind: 'lead' },
  {
    key: '263',
    name: '263邮箱',
    abbr: '263',
    color: '#c8102e',
    kind: 'mail',
    provider: '263',
  },
  {
    key: 'aliyun',
    name: '阿里邮箱',
    abbr: 'M',
    color: '#ff6a00',
    kind: 'mail',
    provider: 'aliyun',
  },
  {
    key: 'custom',
    name: '其他邮箱',
    abbr: '@',
    color: '#0ea5a4',
    kind: 'mail',
    provider: 'custom',
  },
  {
    key: 'exmail',
    name: '腾讯企业邮箱',
    abbr: 'M',
    color: '#1677ff',
    kind: 'mail',
    provider: 'exmail',
  },
  {
    key: 'boss',
    name: 'BOSS邮箱',
    abbr: 'BOSS',
    color: '#00a6a7',
    kind: 'mail',
    provider: 'boss',
  },
  {
    key: 'qq',
    name: 'QQ邮箱',
    abbr: 'QQ',
    color: '#12b7f5',
    kind: 'mail',
    provider: 'qq',
  },
];

const loading = ref(true);
const mailAccounts = ref<ChannelMailApi.Account[]>([]);
const waRef = ref<InstanceType<typeof WaAccounts>>();
const waAccounts = ref<ChannelWaApi.Account[]>([]);
const oauthProviders = ref<string[]>([]);
const leadOpen = ref(false);
const imOpen = ref(false);
const socialOpen = ref(false);
const evoOpen = ref(false);
/** 重新扫码的账号 */
const evoAccountId = ref<number>();
const socialPlatform = ref<SocialPlatform>('youtube');
const socialEnabled = ref<SocialPlatform[]>([]);
const imRef = ref<InstanceType<typeof WebImModal>>();
const webSites = computed(() =>
  waAccounts.value.filter((a) => a.platform === 'web'),
);
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
  else if (a.lastSyncError)
    status = { color: 'red', text: '收信失败', tip: a.lastSyncError };
  else if (a.lastSyncTime)
    status = {
      color: 'green',
      text: '正常',
      tip: `上次收信 ${a.lastSyncTime}`,
    };
  return {
    id: a.id!,
    label: a.email,
    sub: a.authType === 'oauth2' ? '一键授权' : undefined,
    status,
    mail: a,
  };
}

function waRow(a: ChannelWaApi.Account): AccountRow {
  let status = { color: 'orange', text: '等待消息' } as AccountRow['status'];
  if (!a.enabled) status = { color: 'gray', text: '已停用' };
  else if (a.authType === 'evolution' && a.connState === 'close')
    status = {
      color: 'red',
      text: '已断开',
      tip: '手机上退出了关联设备或长时间离线，请点「重新扫码」',
    };
  else if (a.authType === 'evolution')
    status = {
      color: 'green',
      text: '已连接',
      tip: a.lastWebhookTime ? `最近消息 ${a.lastWebhookTime}` : undefined,
    };
  else if (a.lastWebhookTime)
    status = {
      color: 'green',
      text: '正常',
      tip: `最近消息 ${a.lastWebhookTime}`,
    };
  return {
    id: a.id!,
    label: a.displayPhone || a.name || a.phoneNumberId,
    sub: a.name && a.name !== a.displayPhone ? a.name : undefined,
    status,
    wa: a,
  };
}

function accountsOf(c: Channel): AccountRow[] {
  const wa = (a: ChannelWaApi.Account) =>
    !a.platform || a.platform === 'whatsapp';
  switch (c.kind) {
    case 'mail':
      return mailAccounts.value
        .filter((a) => (a.provider || 'custom') === c.provider)
        .map(mailRow);
    case 'meta':
      return waAccounts.value
        .filter((a) => a.platform === c.platform)
        .map(waRow);
    case 'wa':
      return waAccounts.value
        .filter((a) => wa(a) && a.authType === 'evolution')
        .map(waRow);
    case 'wa-official':
      return waAccounts.value
        .filter((a) => wa(a) && a.authType !== 'evolution')
        .map(waRow);
    case 'social':
      return waAccounts.value
        .filter((a) => a.platform === c.platform)
        .map((a) => {
          const r = waRow(a);
          if (a.enabled && a.lastWebhookTime)
            r.status = {
              color: 'green',
              text: '正常',
              tip: `上次拉取评论 ${a.lastWebhookTime}`,
            };
          else if (a.enabled)
            r.status = { color: 'orange', text: '等待拉取评论' };
          return r;
        });
    case 'im':
      return webSites.value.map((a) => ({
        ...waRow(a),
        label: a.displayPhone || '',
        sub: a.name,
      }));
    default:
      return [];
  }
}

// ===== 连接新账号 =====
function connect(c: Channel) {
  switch (c.kind) {
    case 'mail':
      if (c.oauth && oauthProviders.value.includes(c.oauth))
        startOAuth(c.oauth);
      else openMail(c);
      break;
    case 'wa':
      evoAccountId.value = undefined;
      evoOpen.value = true;
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

/** 扫码接入的号码掉线后重新扫码：连接后沿用原账号与会话 */
function rescan(a: ChannelWaApi.Account) {
  evoAccountId.value = a.id;
  evoOpen.value = true;
}

async function startOAuth(provider: string) {
  // 先同步打开窗口，避免被浏览器拦截弹窗
  const win = window.open(
    'about:blank',
    'cgch-mail-oauth',
    'width=560,height=720',
  );
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
const form = reactive<ChannelMailApi.Account>({
  email: '',
  provider: '163',
  imapHost: '',
  imapPort: 993,
  smtpHost: '',
  smtpPort: 465,
});
const provider = computed(
  () => MAIL[form.provider || 'custom'] ?? MAIL.custom!,
);
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
    await (form.id
      ? updateMailAccount({ ...form })
      : createMailAccount({ ...form }));
    message.success(form.id ? '已保存' : '绑定成功，几分钟内会开始收信');
    mailOpen.value = false;
    await loadMail();
  } finally {
    saving.value = false;
  }
}

// ===== 账号操作 =====
const busy = ref<string>();

async function syncHistory(a: ChannelWaApi.Account) {
  busy.value = `hist-${a.id}`;
  try {
    const before = a.historySyncTime;
    const n = await syncWaHistory(a.id!);
    if (n >= 0) {
      message.success(
        n > 0
          ? `同步了 ${n} 条聊天记录，可在「会话」中查看`
          : '没有新的聊天记录',
      );
      return;
    }
    // 扫码接入的号码在后台同步：每 3 秒看一次结果，最多等 3 分钟
    const hide = message.loading('正在从手机同步的记录里导入聊天记录…', 0);
    try {
      for (let i = 0; i < 60; i++) {
        await new Promise((r) => setTimeout(r, 3000));
        const fresh = (await getWaAccountList()).find((x) => x.id === a.id);
        if (
          fresh &&
          fresh.historySyncTime &&
          fresh.historySyncTime !== before
        ) {
          showHistoryResult(fresh);
          await waRef.value?.load();
          return;
        }
      }
      message.info('聊天记录较多，仍在后台导入，稍后在「会话」中查看');
    } finally {
      hide();
    }
  } finally {
    busy.value = undefined;
  }
}

/** 同步结果：没有任何历史时，说明关联时手机没把聊天记录发过来，需要重新关联 */
function showHistoryResult(a: ChannelWaApi.Account) {
  if (a.historySyncError) {
    Modal.error({ title: '同步聊天记录失败', content: a.historySyncError });
    return;
  }
  const fetched = a.historyFetched ?? 0;
  const imported = a.historyImported ?? 0;
  const noNumber = a.historyNoNumber ?? 0;
  if (fetched === 0) {
    Modal.warning({
      title: '手机没有同步过来历史聊天记录',
      content:
        'WhatsApp 只在「关联新设备」那一刻把历史聊天记录发过来一次。请在手机 WhatsApp →「已关联的设备」里退出这台设备，然后在这里点「重新扫码」重新关联，关联后几分钟内会自动导入。',
    });
    return;
  }
  const lines = [
    `手机同步过来 ${fetched} 条记录，这次新导入 ${imported} 条（已有的自动跳过），可在「会话」中查看。`,
  ];
  if (noNumber > 0)
    lines.push(
      `其中 ${noNumber} 条来自隐藏了手机号的联系人，已按「隐藏号码联系人」导入，可以照常回复；对方之后带上号码时会自动合并。`,
    );
  Modal.success({ title: '聊天记录同步完成', content: lines.join('') });
}

/** 渠道列表里扫码号码的同步状态 */
function historyText(a?: ChannelWaApi.Account) {
  if (!a?.historySyncTime) return '';
  if (a.historySyncError) return `同步失败：${a.historySyncError}`;
  return `上次同步 ${fmtDateTime(a.historySyncTime, 'MM-DD HH:mm')} · 导入 ${a.historyImported ?? 0} 条`;
}

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

// ===== 接待成员：这个号码上的新客户轮流分给谁 =====
const assignOpen = ref(false);
const assignAccount = ref<ChannelWaApi.Account>();
const assignIds = ref<number[]>([]);
const users = ref<{ id?: number; nickname: string }[]>([]);
const assignSaving = ref(false);
async function openAssign(a: ChannelWaApi.Account) {
  assignAccount.value = a;
  assignIds.value = (a.assignUserIds || '')
    .split(',')
    .map(Number)
    .filter((n) => n > 0);
  assignOpen.value = true;
  if (!users.value.length)
    users.value = ((await getSimpleUserList().catch(() => [])) as any) || [];
}
async function saveAssign() {
  if (!assignAccount.value?.id) return;
  assignSaving.value = true;
  try {
    await setWaAssignees(assignAccount.value.id, assignIds.value);
    message.success(
      assignIds.value.length
        ? `已设置，新客户将在 ${assignIds.value.length} 位成员之间轮流分配`
        : '已恢复为号码所属的业务员接待',
    );
    assignOpen.value = false;
    await waRef.value?.load();
  } finally {
    assignSaving.value = false;
  }
}
function assignText(a?: ChannelWaApi.Account) {
  const n = (a?.assignUserIds || '').split(',').filter(Boolean).length;
  return n ? `接待成员(${n})` : '接待成员';
}

async function toggle(r: AccountRow) {
  if (r.mail) {
    await updateMailAccount({
      ...r.mail,
      enabled: !r.mail.enabled,
      password: '',
    });
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

// ===== 收件箱列表（参考 Chatwoot）：每个已接入的账号是一个收件箱 =====
interface InboxRow extends AccountRow {
  channel: Channel;
}
const KIND_LABEL: Record<Kind, string> = {
  wa: 'WhatsApp · 扫码',
  'wa-official': 'WhatsApp · 官方 API',
  meta: '社媒私信',
  mail: '邮箱',
  im: '官网在线客服',
  social: '视频评论',
  lead: '询盘平台',
  soon: '',
};
const inboxes = computed<InboxRow[]>(() =>
  CHANNELS.flatMap((c) => accountsOf(c).map((r) => ({ ...r, channel: c }))),
);
const stats = computed(() => [
  { label: '收件箱', value: inboxes.value.length, color: '#1D1D1F' },
  {
    label: '正常',
    value: inboxes.value.filter((r) => r.status.color === 'green').length,
    color: '#248A3D',
  },
  {
    label: '需要处理',
    value: inboxes.value.filter((r) => r.status.color === 'red').length,
    color: '#C93400',
  },
  {
    label: '已停用',
    value: inboxes.value.filter((r) => r.status.color === 'gray').length,
    color: '#86868B',
  },
]);
const STATUS_PILL: Record<string, string> = {
  green: 'bg-[#E3F6E8] text-[#248A3D]',
  red: 'bg-[#FFE5E5] text-[#D70015]',
  orange: 'bg-[#FFF4E0] text-[#C93400]',
  gray: 'bg-[#F2F2F7] text-[#86868B]',
};
const STATUS_DOT: Record<string, string> = {
  green: '#248A3D',
  red: '#D70015',
  orange: '#C93400',
  gray: '#A1A1A6',
};
const channelIcon = (c: Channel) =>
  CHANNEL_ICONS[c.key] ?? CHANNEL_ICONS[c.provider ?? ''];

/** 收件箱最常用的一个操作，放在行上；其余放进「设置」菜单 */
function primaryAction(
  r: InboxRow,
): { key: string; label: string } | undefined {
  if (r.wa?.authType === 'evolution' && r.wa.connState === 'close')
    return { key: 'rescan', label: '重新扫码' };
  if (r.mail) return { key: 'sync', label: '立即收信' };
  if (r.wa?.platform === 'web') return { key: 'code', label: '嵌入代码' };
  if (r.wa?.platform === 'youtube' || r.wa?.platform === 'tiktok')
    return { key: 'poll', label: '立即同步' };
  if (
    r.wa?.authType === 'evolution' ||
    (r.wa?.authType === 'oauth' &&
      (r.wa.platform === 'messenger' || r.wa.platform === 'instagram'))
  )
    return { key: 'history', label: '同步聊天记录' };
  return undefined;
}
function runAction(key: string, r: InboxRow) {
  switch (key) {
    case 'rescan':
      if (r.wa) rescan(r.wa);
      break;
    case 'sync':
      if (r.mail) syncNow(r.mail);
      break;
    case 'code':
      imOpen.value = true;
      break;
    case 'poll':
      if (r.wa) pollNow(r.wa);
      break;
    case 'history':
      if (r.wa) syncHistory(r.wa);
      break;
    case 'guide':
      if (r.wa) waRef.value?.showGuide(r.wa);
      break;
    case 'edit':
      edit(r.channel, r);
      break;
    case 'assign':
      if (r.wa) openAssign(r.wa);
      break;
    case 'toggle':
      toggle(r);
      break;
    case 'unbind':
      Modal.confirm({
        title: `解绑 ${r.label}？`,
        content: r.mail
          ? '解绑后已同步的邮件也会删除。'
          : '解绑后这个收件箱的会话与消息记录也会删除。',
        okText: '解绑',
        okButtonProps: { danger: true },
        onOk: () => unbind(r),
      });
      break;
  }
}
function menuOf(r: InboxRow) {
  const items: { danger?: boolean; key: string; label: string }[] = []; // 解绑单独放在菜单最后
  const main = primaryAction(r)?.key;
  if (r.wa?.authType === 'evolution') {
    if (main !== 'history')
      items.push({ key: 'history', label: '同步聊天记录' });
    if (main !== 'rescan') items.push({ key: 'rescan', label: '重新扫码' });
  }
  if (
    r.wa &&
    r.wa.authType !== 'oauth' &&
    r.wa.authType !== 'evolution' &&
    r.wa.platform !== 'web' &&
    r.wa.platform !== 'youtube' &&
    r.wa.platform !== 'tiktok'
  )
    items.push({ key: 'guide', label: '回调配置' });
  if (
    r.mail ||
    (r.wa && r.wa.authType !== 'oauth' && r.wa.authType !== 'evolution')
  )
    items.push({
      key: 'edit',
      label: r.mail?.authType === 'oauth2' ? '重新授权' : '修改配置',
    });
  if (r.wa) items.push({ key: 'assign', label: assignText(r.wa) });
  items.push({
    key: 'toggle',
    label: (r.mail ?? r.wa)?.enabled ? (r.mail ? '暂停收信' : '停用') : '启用',
  });
  return items;
}

// ===== 添加收件箱向导：先选渠道，再进入对应的连接方式 =====
const addOpen = ref(false);
const ADD_GROUPS: { keys: string[]; title: string }[] = [
  {
    title: '即时消息',
    keys: ['whatsapp', 'wa-official', 'messenger', 'instagram', 'im'],
  },
  {
    title: '邮箱',
    keys: [
      'gmail',
      'outlook',
      'exmail',
      'qq',
      '163',
      '126',
      'aliyun',
      '263',
      'boss',
      'custom',
    ],
  },
  { title: '视频评论', keys: ['youtube', 'tiktok'] },
  { title: '询盘平台', keys: ['alibaba', 'mic'] },
];
const CHANNEL_DESC: Record<string, string> = {
  whatsapp: '手机扫码或输入配对码，几分钟就能用，可同步历史聊天',
  'wa-official': '通过 Meta 官方接口接入，可发审核通过的模板消息',
  messenger: '授权 Facebook 主页，接收主页私信',
  instagram: '授权专业账号，接收 Instagram 私信',
  im: '复制一段代码到网站，访客直接发起聊天',
  youtube: '授权频道，视频评论进会话统一回复',
  tiktok: '授权账号，视频评论进会话统一回复',
  alibaba: '通过留资邮箱把询盘转进系统',
  mic: '通过留资邮箱把询盘转进系统',
};
const channelOf = (k: string) => CHANNELS.find((c) => c.key === k)!;
function addConnect(c: Channel) {
  addOpen.value = false;
  connect(c);
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
    <div class="flex flex-wrap items-start gap-4 pb-5">
      <div class="min-w-[260px] flex-1">
        <h2 class="m-0 text-[34px] font-bold tracking-tight text-[#1D1D1F]">
          收件箱
        </h2>
        <p class="m-0 mt-1 text-sm text-[#6E6E73]">
          每个接入的账号是一个收件箱。客户消息进入收件箱后，按接待成员分配给业务员或
          AI，在「会话」里统一处理。
        </p>
      </div>
      <Button @click="leadOpen = true">设置留资邮箱</Button>
      <Button type="primary" @click="addOpen = true"
        ><IconifyIcon
          icon="lucide:plus"
          class="mr-1 inline size-4 align-[-3px]"
        />添加收件箱</Button
      >
    </div>

    <div class="mb-4 grid grid-cols-2 gap-3 md:grid-cols-4">
      <div
        v-for="st in stats"
        :key="st.label"
        class="rounded-xl border border-solid border-[#E5E5EA] bg-white px-4 py-3"
      >
        <div class="text-[13px] text-[#6E6E73]">{{ st.label }}</div>
        <div
          class="text-[30px] font-bold tabular-nums tracking-tight"
          :style="{ color: st.color }"
        >
          {{ st.value }}
        </div>
      </div>
    </div>

    <Spin :spinning="loading" wrapper-class-name="ch-spin">
      <div class="channel-account-panel">
        <div class="channel-table-labels">
          <span>收件箱</span><span>渠道类型</span><span>状态</span
          ><span>接待</span><span class="text-right">操作</span>
        </div>
        <div class="channel-account-grid">
          <div
            v-if="!loading && inboxes.length === 0"
            class="flex flex-col items-center gap-3 py-16 text-[#86868B]"
          >
            <span>还没有接入任何账号</span>
            <Button type="primary" @click="addOpen = true"
              >添加第一个收件箱</Button
            >
          </div>
          <div
            v-for="r in inboxes"
            :key="`${r.channel.key}-${r.id}`"
            class="channel-account-card"
          >
            <div class="flex min-w-0 items-center gap-3">
              <img
                v-if="channelIcon(r.channel)"
                :src="channelIcon(r.channel)"
                :alt="r.channel.name"
                class="ch-icon"
              />
              <span
                v-else
                class="ch-badge"
                :style="{ background: r.channel.color }"
                :class="{ 'is-long': r.channel.abbr.length > 2 }"
                >{{ r.channel.abbr }}</span
              >
              <div class="flex min-w-0 flex-col">
                <span class="truncate font-semibold text-[#1D1D1F]">{{
                  r.sub || r.label
                }}</span>
                <span class="truncate text-xs text-[#86868B]">
                  {{ r.sub ? r.label : r.channel.name
                  }}<template
                    v-if="r.wa?.authType === 'evolution' && historyText(r.wa)"
                  >
                    · {{ historyText(r.wa) }}</template
                  >
                </span>
              </div>
            </div>
            <span class="text-[13px] text-[#424245]">{{
              KIND_LABEL[r.channel.kind] === '邮箱'
                ? r.channel.name
                : KIND_LABEL[r.channel.kind] || r.channel.name
            }}</span>
            <span>
              <Tooltip :title="r.status.tip">
                <span
                  class="inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-medium"
                  :class="STATUS_PILL[r.status.color]"
                >
                  <span
                    class="size-1.5 rounded-full"
                    :style="{ background: STATUS_DOT[r.status.color] }"
                  ></span
                  >{{ r.status.text }}
                </span>
              </Tooltip>
            </span>
            <span class="channel-assignees text-[13px] text-[#424245]">
              <small>接待成员</small
              ><a v-if="r.wa" @click="openAssign(r.wa)">{{
                assignText(r.wa)
              }}</a>
              <span v-else class="text-[#86868B]">-</span>
            </span>
            <div class="flex items-center justify-end gap-2">
              <Button
                v-if="primaryAction(r)"
                size="small"
                :loading="
                  busy === `hist-${r.id}` ||
                  busy === `sync-${r.id}` ||
                  busy === `poll-${r.id}`
                "
                class="!border-transparent !bg-[#E8F2FC] !text-[#0066CC]"
                @click="runAction(primaryAction(r)!.key, r)"
              >
                {{ primaryAction(r)!.label }}
              </Button>
              <Dropdown :trigger="['click']">
                <Button size="small">设置</Button>
                <template #overlay>
                  <Menu @click="(e: any) => runAction(String(e.key), r)">
                    <MenuItem
                      v-for="it in menuOf(r).filter((x) => !x.danger)"
                      :key="it.key"
                      >{{ it.label }}</MenuItem
                    >
                    <MenuDivider />
                    <MenuItem key="unbind" danger>解绑</MenuItem>
                  </Menu>
                </template>
              </Dropdown>
            </div>
          </div>
        </div>
      </div>
    </Spin>

    <Modal
      v-model:open="addOpen"
      title="添加收件箱"
      :footer="null"
      :width="860"
      centered
    >
      <p class="mb-4 mt-0 text-sm text-[#6E6E73]">
        选择要接入的渠道。接入后客户消息会进入「会话」统一处理。
      </p>
      <div class="flex max-h-[64vh] flex-col gap-5 overflow-auto pr-1">
        <section v-for="g in ADD_GROUPS" :key="g.title">
          <h3 class="m-0 mb-2.5 text-[13px] font-semibold text-[#424245]">
            {{ g.title }}
          </h3>
          <div class="grid grid-cols-2 gap-2.5 md:grid-cols-3">
            <button
              v-for="k in g.keys"
              :key="k"
              type="button"
              class="flex cursor-pointer items-start gap-3 rounded-xl border border-solid border-[#E5E5EA] bg-white p-3.5 text-left hover:border-[#0071E3] hover:bg-[#F4F8FD]"
              @click="addConnect(channelOf(k))"
            >
              <img
                v-if="channelIcon(channelOf(k))"
                :src="channelIcon(channelOf(k))"
                alt=""
                class="ch-icon"
              />
              <span
                v-else
                class="ch-badge"
                :style="{ background: channelOf(k).color }"
                :class="{ 'is-long': channelOf(k).abbr.length > 2 }"
                >{{ channelOf(k).abbr }}</span
              >
              <span class="flex min-w-0 flex-col gap-1">
                <span class="text-sm font-semibold text-[#1D1D1F]">{{
                  channelOf(k).name
                }}</span>
                <span class="text-xs leading-[18px] text-[#86868B]">{{
                  CHANNEL_DESC[k] ??
                  (channelOf(k).oauth &&
                  oauthProviders.includes(channelOf(k).oauth!)
                    ? '一键授权登录，自动收发邮件'
                    : '填写邮箱和授权码，自动收发邮件')
                }}</span>
              </span>
            </button>
          </div>
        </section>
        <p class="m-0 text-xs text-[#C93400]">
          扫码接入的 WhatsApp 请勿短时间内群发陌生号码，以免号码被 WhatsApp
          限制。
        </p>
      </div>
    </Modal>

    <WaAccounts
      ref="waRef"
      headless
      :meta-o-auth="oauthProviders.includes('meta')"
      @changed="onWaChanged"
    />
    <LeadRules v-model:open="leadOpen" :mailboxes="mailboxes" />
    <WebImModal
      ref="imRef"
      v-model:open="imOpen"
      :sites="webSites"
      @changed="waRef?.load()"
    />
    <EvoConnectModal
      v-model:open="evoOpen"
      :account-id="evoAccountId"
      @done="waRef?.load()"
    />
    <SocialConnectModal
      v-model:open="socialOpen"
      :platform="socialPlatform"
      :enabled="socialEnabled.includes(socialPlatform)"
    />

    <Modal
      v-model:open="assignOpen"
      title="接待成员"
      :width="460"
      centered
      :confirm-loading="assignSaving"
      ok-text="保存"
      @ok="saveAssign"
    >
      <div class="mb-3 text-sm leading-6 text-gray-500">
        {{
          assignAccount?.displayPhone || assignAccount?.name
        }}
        上的<b>新客户</b>会在下面的成员之间轮流分配，分到谁就出现在谁的会话列表里。
        不选成员时由号码所属的业务员接待。已有的会话不受影响，可以在会话里「转交」。
      </div>
      <Select
        v-model:value="assignIds"
        mode="multiple"
        class="w-full"
        placeholder="选择接待成员"
        :options="users.map((u) => ({ label: u.nickname, value: u.id }))"
        option-filter-prop="label"
      />
    </Modal>

    <Modal
      v-model:open="mailOpen"
      :footer="null"
      :width="476"
      centered
      :closable="false"
    >
      <div class="mb-5 flex items-center gap-3">
        <span class="text-base font-semibold"
          >{{ form.id ? '修改' : '添加' }}{{ mailChannel.name }}账号</span
        >
        <Popover
          :title="`${mailChannel.name}绑定指引`"
          placement="bottom"
          :overlay-style="{ maxWidth: '320px' }"
        >
          <template #content>
            <div class="text-sm leading-6">{{ provider.guide }}</div>
          </template>
          <a class="inline-flex items-center gap-1 text-sm"
            ><IconifyIcon icon="lucide:link" class="size-3.5" />绑定指引</a
          >
        </Popover>
      </div>

      <template v-if="!advanced">
        <div class="ch-field">
          <div class="ch-label">邮箱地址</div>
          <Input
            v-model:value="form.email"
            size="large"
            placeholder="请输入邮箱地址"
            :disabled="!!form.id"
          />
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
          <a @click="advanced = true"
            >高级配置 <IconifyIcon icon="lucide:chevrons-down" class="size-4"
          /></a>
        </div>
      </template>

      <template v-else>
        <div class="ch-field">
          <div class="ch-label"><i>*</i> 协议类型</div>
          <label class="inline-flex items-center gap-2 text-sm"
            ><span class="ch-radio"></span>IMAP</label
          >
        </div>
        <div class="ch-server">
          <div class="ch-label"><i>*</i> 收邮件（IMAP）服务器</div>
          <div class="ch-label"><i>*</i> 加密</div>
          <div class="ch-label"><i>*</i> 端口</div>
          <Input v-model:value="form.imapHost" placeholder="imap.example.com" />
          <Select
            :value="form.imapSsl as any"
            :options="SSL_OPTIONS as any"
            @update:value="(v: any) => (form.imapSsl = v)"
          />
          <InputNumber
            v-model:value="form.imapPort"
            :min="1"
            :max="65535"
            :controls="false"
            class="w-full"
          />
          <div class="ch-label mt-2"><i>*</i> 发邮件（SMTP）服务器</div>
          <div class="ch-label mt-2"><i>*</i> 加密</div>
          <div class="ch-label mt-2"><i>*</i> 端口</div>
          <Input v-model:value="form.smtpHost" placeholder="smtp.example.com" />
          <Select
            :value="form.smtpSsl as any"
            :options="SSL_OPTIONS as any"
            @update:value="(v: any) => (form.smtpSsl = v)"
          />
          <InputNumber
            v-model:value="form.smtpPort"
            :min="1"
            :max="65535"
            :controls="false"
            class="w-full"
          />
        </div>
        <div class="ch-field mt-4">
          <div class="ch-label">发件人名称</div>
          <Input
            v-model:value="form.displayName"
            placeholder="客户看到的名字，默认用邮箱地址"
          />
        </div>
      </template>

      <div class="mt-6 flex items-center justify-end gap-3">
        <a
          v-if="!advanced"
          class="mr-auto text-sm"
          :class="{ 'opacity-50': testing }"
          @click="testMail"
          >测试连接</a
        >
        <Button
          size="large"
          class="ch-btn-gray"
          @click="advanced ? (advanced = false) : (mailOpen = false)"
        >
          {{ advanced ? '返回' : '取 消' }}
        </Button>
        <Button
          size="large"
          type="primary"
          :loading="saving"
          :disabled="!form.email"
          @click="saveMail"
          >确认绑定</Button
        >
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.ch-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 32px);
  padding: 24px 28px 16px;
  background: #f5f5f7;
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
  color: #1d1d1f;
}

.ch-count {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  font-size: 14px;
  color: #6e6e73;
}

.ch-count b {
  color: #0071e3;
}

.ch-grid {
  display: grid;
  grid-template-columns: minmax(0, 2.4fr) minmax(0, 1.3fr) minmax(
      0,
      1fr
    ) minmax(0, 1fr) 210px;
  gap: 12px;
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
  border-bottom: 1px solid #ebebf0;
}

.ch-row--head {
  min-height: 42px;
  color: #86868b;
  background: #f5f5f7;
  border-bottom: 0;
  border-radius: 6px;
}

.ch-row--sub {
  min-height: 38px;
  font-size: 13px;
  background: #f7f7f9;
}

.ch-col-name {
  display: flex;
  flex: 0 0 210px;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.ch-col-status {
  flex: 0 0 140px;
  white-space: nowrap;
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
  color: #0071e3;
}

.ch-ops {
  display: flex;
  gap: 14px;
}

.ch-icon {
  flex: none;
  width: 26px;
  height: 26px;
  object-fit: contain;
  border-radius: 6px;
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
  color: #a1a1a6;
}

.ch-status.is-gray::before {
  background: #d2d2d7;
}

.ch-field {
  margin-bottom: 18px;
}

.ch-label {
  margin-bottom: 8px;
  font-size: 14px;
  color: #424245;
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
  background: #e5e5ea;
}

.ch-divider a {
  display: inline-flex;
  gap: 2px;
  align-items: center;
  color: #424245;
}

.ch-radio {
  width: 16px;
  height: 16px;
  border: 5px solid #0071e3;
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
  color: #0071e3;
  background: #f2f2f7;
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
