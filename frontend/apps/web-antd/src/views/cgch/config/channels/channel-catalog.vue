<script lang="ts" setup>
/**
 * 渠道总表：列出所有可接入的外部账号，显示连接状态，并提供「连接新账号」入口。
 * 图标使用文字缩写色块，不使用各平台商标。
 */
import type { ChannelMailApi } from '#/api/channel/mail';
import type { ChannelWaApi } from '#/api/channel/whatsapp';

import { computed } from 'vue';

import { Button, Card, Tag, Tooltip } from 'ant-design-vue';

const props = defineProps<{
  mailAccounts: ChannelMailApi.Account[];
  oauthProviders: string[];
  waAccounts: ChannelWaApi.Account[];
}>();

const emit = defineEmits<{
  leadRules: [];
  mail: [provider: string];
  meta: [platform: 'instagram' | 'messenger'];
  oauth: [provider: string];
  wa: [];
}>();

type Kind = 'lead' | 'mail' | 'meta' | 'soon' | 'wa';
interface Item {
  key: string;
  name: string;
  abbr: string;
  color: string;
  desc: string;
  kind: Kind;
  /** 邮箱：对应的 provider；可 OAuth 的还有 oauth 服务商 */
  provider?: string;
  oauth?: string;
  /** Meta 账号的平台 */
  platform?: 'instagram' | 'messenger';
  soonText?: string;
}

const GROUPS: { title: string; items: Item[] }[] = [
  {
    title: '即时通讯',
    items: [
      { key: 'wa', name: 'WhatsApp', abbr: 'WA', color: '#25a244', desc: '用手机 WhatsApp Business App 扫码连接，消息两边同步，AI 自动接待', kind: 'wa' },
      { key: 'im', name: '官网 IM', abbr: 'IM', color: '#3b6fd8', desc: '在官网嵌入聊天窗口，访客消息进入统一收件箱', kind: 'soon', soonText: '开发中' },
    ],
  },
  {
    title: '邮箱',
    items: [
      { key: 'gmail', name: '谷歌邮箱', abbr: 'G', color: '#d44638', desc: 'Gmail / Google Workspace', kind: 'mail', provider: 'gmail', oauth: 'google' },
      { key: 'outlook', name: 'Outlook', abbr: 'O', color: '#0f6cbd', desc: 'Outlook.com / Microsoft 365', kind: 'mail', provider: 'outlook', oauth: 'microsoft' },
      { key: 'exmail', name: '腾讯企业邮箱', abbr: '腾', color: '#1677ff', desc: '使用客户端专用密码登录', kind: 'mail', provider: 'exmail' },
      { key: 'qq', name: 'QQ 邮箱', abbr: 'QQ', color: '#12b7f5', desc: '需开启 IMAP/SMTP 并使用授权码', kind: 'mail', provider: 'qq' },
      { key: '163', name: '网易邮箱', abbr: '163', color: '#c9302c', desc: '163 邮箱，需使用授权码', kind: 'mail', provider: '163' },
      { key: '126', name: '126 邮箱', abbr: '126', color: '#2f9e44', desc: '需开启 IMAP/SMTP 并使用授权码', kind: 'mail', provider: '126' },
      { key: 'aliyun', name: '阿里邮箱', abbr: '阿', color: '#ff6a00', desc: '阿里企业邮箱', kind: 'mail', provider: 'aliyun' },
      { key: '263', name: '263 企业邮箱', abbr: '263', color: '#7048e8', desc: '263 企业邮箱', kind: 'mail', provider: '263' },
      { key: 'custom', name: '其他邮箱', abbr: '@', color: '#6b7280', desc: '任何支持 IMAP / SMTP 的邮箱', kind: 'mail', provider: 'custom' },
    ],
  },
  {
    title: 'B2B 平台',
    items: [
      { key: 'alibaba', name: '阿里国际站', abbr: '阿', color: '#ff6a00', desc: '询盘通知邮件自动解析建档，回复请在平台后台完成', kind: 'lead' },
      { key: 'mic', name: '中国制造网', abbr: 'MIC', color: '#d9480f', desc: '询盘通知邮件自动解析建档', kind: 'lead' },
    ],
  },
  {
    title: '社交媒体',
    items: [
      { key: 'facebook', name: 'Facebook 主页', abbr: 'FB', color: '#1877f2', desc: '主页私信（Messenger），AI 可自动回复', kind: 'meta', platform: 'messenger' },
      { key: 'instagram', name: 'Instagram', abbr: 'IG', color: '#c13584', desc: '专业账号私信，需关联 Facebook 主页', kind: 'meta', platform: 'instagram' },
      { key: 'tiktok', name: 'TikTok', abbr: 'TT', color: '#111827', desc: '企业号私信', kind: 'soon', soonText: '需平台开通权限' },
      { key: 'youtube', name: 'YouTube', abbr: 'YT', color: '#e00000', desc: '频道评论', kind: 'soon', soonText: '需 Google 应用审核' },
    ],
  },
];

/** 已连接的账号名称列表 */
function connected(item: Item): string[] {
  if (item.kind === 'wa') {
    return props.waAccounts
      .filter((a) => !a.platform || a.platform === 'whatsapp')
      .map((a) => a.displayPhone || a.name || a.phoneNumberId);
  }
  if (item.kind === 'meta') {
    return props.waAccounts.filter((a) => a.platform === item.platform).map((a) => a.displayPhone || a.name || a.phoneNumberId);
  }
  if (item.kind === 'mail') {
    return props.mailAccounts.filter((a) => (a.provider || 'custom') === item.provider).map((a) => a.email);
  }
  return [];
}

const total = computed(() => props.mailAccounts.length + props.waAccounts.length);

function canOAuth(item: Item) {
  return !!item.oauth && props.oauthProviders.includes(item.oauth);
}
</script>

<template>
  <Card :bordered="false">
    <template #title>
      渠道账号
      <span class="ml-2 text-sm font-normal text-gray-500">已连接 {{ total }} 个账号</span>
    </template>
    <template #extra>
      <Button @click="emit('leadRules')">设置留资邮箱</Button>
    </template>

    <div v-for="g in GROUPS" :key="g.title" class="mb-4 last:mb-0">
      <div class="mb-2 text-xs font-medium text-gray-500">{{ g.title }}</div>
      <div class="grid grid-cols-1 gap-3 lg:grid-cols-2 2xl:grid-cols-3">
        <div
          v-for="item in g.items"
          :key="item.key"
          class="flex items-center gap-3 rounded-lg border border-solid border-gray-200 px-3 py-3 dark:border-gray-700"
        >
          <div
            class="flex size-10 shrink-0 items-center justify-center rounded-lg text-xs font-bold text-white"
            :style="{ background: item.color }"
          >
            {{ item.abbr }}
          </div>
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-2">
              <span class="truncate font-medium">{{ item.name }}</span>
              <Tag v-if="connected(item).length" color="green" class="m-0">已连接 {{ connected(item).length }}</Tag>
              <Tag v-else-if="item.kind === 'soon'" class="m-0">{{ item.soonText }}</Tag>
              <Tag v-else-if="item.kind === 'lead'" color="blue" class="m-0">通过留资邮箱接入</Tag>
            </div>
            <Tooltip v-if="connected(item).length" :title="connected(item).join('、')">
              <div class="truncate text-xs text-gray-500">{{ connected(item).join('、') }}</div>
            </Tooltip>
            <div v-else class="truncate text-xs text-gray-500">{{ item.desc }}</div>
          </div>
          <div class="shrink-0">
            <template v-if="item.kind === 'mail'">
              <Button v-if="canOAuth(item)" size="small" type="primary" ghost @click="emit('oauth', item.oauth!)">
                授权登录
              </Button>
              <Button size="small" type="link" @click="emit('mail', item.provider!)">
                {{ canOAuth(item) ? '用授权码' : '连接新账号' }}
              </Button>
            </template>
            <Button v-else-if="item.kind === 'wa'" size="small" type="link" @click="emit('wa')">连接新账号</Button>
            <template v-else-if="item.kind === 'meta'">
              <Button v-if="oauthProviders.includes('meta')" size="small" type="primary" ghost @click="emit('meta', item.platform!)">
                授权登录
              </Button>
              <Button v-else size="small" type="link" @click="emit('meta', item.platform!)">连接新账号</Button>
            </template>
            <Button v-else-if="item.kind === 'lead'" size="small" type="link" @click="emit('leadRules')">设置</Button>
            <Button v-else size="small" type="link" disabled>敬请期待</Button>
          </div>
        </div>
      </div>
    </div>
  </Card>
</template>
