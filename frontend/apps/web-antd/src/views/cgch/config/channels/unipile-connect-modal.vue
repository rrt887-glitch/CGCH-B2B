<script lang="ts" setup>
/**
 * 通过 Unipile 连接渠道（LinkedIn / Facebook / Instagram / Telegram）：
 * 左侧是 Unipile 托管授权页的二维码，右侧是操作步骤。
 * 手机扫码后在该页面登录，授权完成后 Unipile 回调我们，账号自动出现；
 * 本窗口每 2 秒查一次账号列表，出现新账号就自动关闭并刷新。
 * 也可以直接在本机浏览器打开授权页。
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { message, Modal, QRCode } from 'ant-design-vue';

import type { ChannelUnipileApi } from '#/api/channel/unipile';
import { connectUnipile } from '#/api/channel/unipile';
import { getWaAccountList } from '#/api/channel/whatsapp';

const emit = defineEmits<{ done: [] }>();
const open = defineModel<boolean>('open', { default: false });
const provider = defineModel<ChannelUnipileApi.Provider>('provider', { default: 'linkedin' });

/** loading 正在生成二维码；ready 等待扫码；expired 二维码过期；error 生成失败 */
const state = ref<'error' | 'expired' | 'loading' | 'ready'>('loading');
const url = ref('');
const error = ref('');

let pollTimer: ReturnType<typeof setInterval> | undefined;
let expireTimer: ReturnType<typeof setTimeout> | undefined;
interface AuthorizationAttempt {
  baseline: Set<number>;
  polling: boolean;
  provider: ChannelUnipileApi.Provider;
}
let activeAttempt: AuthorizationAttempt | undefined;

const PROVIDERS: Record<
  ChannelUnipileApi.Provider,
  { platform: string; name: string; title: string; icon: string; desc: string; steps: string[] }
> = {
  whatsapp: {
    platform: 'whatsapp',
    name: 'WhatsApp',
    title: '连接您的 WhatsApp',
    icon: 'logos:whatsapp-icon',
    desc: '扫码后在手机上确认要关联的设备，消息会进入草根出海。',
    steps: ['用手机相机扫描左侧二维码', '在手机上确认关联设备', '完成后本页面会自动刷新'],
  },
  linkedin: {
    platform: 'linkedin',
    name: 'LinkedIn',
    title: '连接您的 LinkedIn',
    icon: 'logos:linkedin-icon',
    desc: '扫码后在手机上登录 LinkedIn 并授权，私信就会进入草根出海。',
    steps: ['用手机相机扫描左侧二维码', '在 Unipile 页面登录 LinkedIn 并授权', '完成后本页面会自动刷新'],
  },
  facebook: {
    platform: 'messenger',
    name: 'Facebook',
    title: '连接您的 Facebook',
    icon: 'logos:facebook',
    desc: '登录 Facebook 后勾选要接入的主页或个人 Messenger。提示 Intent not found 说明链接已失效，点「刷新二维码」重新生成即可。',
    steps: [
      '用手机相机扫描左侧二维码（或点「在这台电脑上授权」）',
      '登录 Facebook，勾选要接入的主页或个人账号，点「下一步」',
      '完成后本页面会自动刷新；没反应就关掉窗口重新点「一键授权登录」',
    ],
  },
  instagram: {
    platform: 'instagram',
    name: 'Instagram',
    title: '连接您的 Instagram',
    icon: 'logos:instagram-icon',
    desc: '扫码后在手机上登录 Instagram 并授权，私信就会进入草根出海。',
    steps: ['用手机相机扫描左侧二维码', '在 Unipile 页面登录 Instagram 并授权', '完成后本页面会自动刷新'],
  },
  telegram: {
    platform: 'telegram',
    name: 'Telegram',
    title: '连接您的 Telegram',
    icon: 'logos:telegram',
    desc: '扫码后在手机上登录 Telegram，输入收到的验证码完成授权。',
    steps: ['用手机相机扫描左侧二维码', '登录 Telegram 并输入短信验证码', '完成后本页面会自动刷新'],
  },
};

const meta = computed(() => PROVIDERS[provider.value] ?? PROVIDERS.linkedin);

const tip = computed(() => {
  if (error.value) return { type: 'error', text: error.value };
  if (state.value === 'expired') return { type: 'warn', text: '二维码已过期，请点击刷新' };
  return {
    type: 'info',
    text: '授权由 Unipile 托管完成，本系统不保存账号密码，随时可以在渠道页断开',
  };
});

function stop() {
  activeAttempt = undefined;
  if (pollTimer) clearInterval(pollTimer);
  if (expireTimer) clearTimeout(expireTimer);
  pollTimer = undefined;
  expireTimer = undefined;
}

async function refresh() {
  stop();
  const attempt: AuthorizationAttempt = {
    baseline: new Set(),
    polling: false,
    provider: provider.value,
  };
  activeAttempt = attempt;
  state.value = 'loading';
  error.value = '';
  url.value = '';
  try {
    // 获取授权链接前记录已有账号，避免刚连接的账号被误记为已有账号。
    const list = await getWaAccountList();
    if (!isActive(attempt)) return;
    attempt.baseline = new Set((list || []).map((a) => Number(a.id)));
    const res = await connectUnipile(attempt.provider);
    if (!isActive(attempt)) return;
    if (res.recovered && res.recovered > 0) {
      // Unipile 上已经有这个渠道的账号（上次绑成功了但没落库），后端直接找回来了
      stop();
      emit('done');
      open.value = false;
      message.success(
        `已找回 ${res.recovered} 个之前绑定过的账号，不需要再授权`,
      );
      return;
    }
    url.value = res.url;
    state.value = 'ready';
    pollTimer = setInterval(() => void poll(attempt), 2000);
    expireTimer = setTimeout(() => {
      if (!isActive(attempt)) return;
      stop();
      state.value = 'expired';
    }, 55 * 60_000);
  } catch (e) {
    if (!isActive(attempt)) return;
    state.value = 'error';
    error.value = (e as Error)?.message || '生成二维码失败，请重试';
  }
}

function isActive(attempt: AuthorizationAttempt) {
  return activeAttempt === attempt && open.value && provider.value === attempt.provider;
}

async function poll(attempt: AuthorizationAttempt) {
  if (!isActive(attempt) || attempt.polling) return;
  attempt.polling = true;
  try {
    const list = await getWaAccountList();
    if (!isActive(attempt)) return;
    const channel = PROVIDERS[attempt.provider];
    const found = (list || []).find(
      (a) =>
        Number(a.id) &&
        !attempt.baseline.has(Number(a.id)) &&
        a.authType === 'unipile' &&
        (a.platform || 'whatsapp') === channel.platform,
    );
    if (found) {
      stop();
      message.success(`已连接 ${channel.name}：${found.displayPhone || found.name || ''}`);
      emit('done');
      open.value = false;
    }
  } catch {
    // 轮询失败不打扰用户，下一轮再试
  } finally {
    attempt.polling = false;
  }
}

function openHere() {
  if (!url.value) return;
  window.open(url.value, 'cgch-unipile', 'width=560,height=760');
}

watch([open, provider], ([v]) => {
  if (v) {
    void refresh();
  } else {
    stop();
  }
});
onBeforeUnmount(stop);
</script>

<template>
  <Modal
    v-model:open="open"
    :footer="null"
    :width="760"
    centered
    :body-style="{ padding: 0 }"
    wrap-class-name="fb-connect"
  >
    <div class="fb-connect__body">
      <section class="fb-connect__left">
        <div class="fb-connect__badge">
          <IconifyIcon :icon="meta.icon" class="size-6" />
        </div>
        <div class="mt-4 text-lg font-semibold">扫码授权</div>

        <div class="fb-connect__qr">
          <QRCode
            v-if="state === 'ready' || state === 'expired'"
            :value="url"
            :size="200"
            :bordered="false"
            :status="state === 'expired' ? 'expired' : 'active'"
            @refresh="refresh"
          />
          <template v-else-if="state === 'loading'">
            <IconifyIcon icon="lucide:loader-circle" class="size-10 animate-spin text-[#0071E3]" />
            <span class="mt-3 text-sm text-gray-500">正在生成二维码</span>
          </template>
          <template v-else>
            <IconifyIcon icon="lucide:circle-alert" class="size-10 text-gray-400" />
            <span class="mt-3 text-sm text-gray-500">二维码生成失败</span>
          </template>
        </div>

        <div v-if="state === 'ready'" class="fb-connect__wait">
          <span class="fb-connect__dot"></span>
          等待手机上完成授权
        </div>

        <button type="button" class="fb-connect__primary" :disabled="state === 'loading'" @click="refresh">
          <IconifyIcon icon="lucide:refresh-cw" class="size-4" />
          刷新二维码
        </button>

        <a class="fb-connect__switch" :class="{ 'is-disabled': state !== 'ready' }" @click="openHere">
          在这台电脑上授权
          <IconifyIcon icon="lucide:chevron-right" class="size-4" />
        </a>
      </section>

      <section class="fb-connect__right">
        <h3 class="fb-connect__title">{{ meta.title }}</h3>
        <p class="fb-connect__desc">{{ meta.desc }}</p>
        <div class="fb-connect__tip" :class="`is-${tip.type}`">{{ tip.text }}</div>

        <ol class="fb-connect__steps">
          <li v-for="(s, i) in meta.steps" :key="i">
            <span class="fb-connect__num">{{ i + 1 }}</span>
            <span>{{ s }}</span>
          </li>
        </ol>

        <div class="text-xs leading-5 text-gray-500">
          连接即代表同意
          <a href="/terms" target="_blank">服务协议</a>
          与
          <a href="/privacy" target="_blank">隐私政策</a>
        </div>
      </section>
    </div>
  </Modal>
</template>

<style scoped>
.fb-connect__body {
  display: flex;
  min-height: 520px;
  overflow: hidden;
  border-radius: 20px;
}

.fb-connect__left {
  display: flex;
  flex: 0 0 300px;
  flex-direction: column;
  align-items: center;
  padding: 36px 20px 24px;
  background: #f5f5f7;
}

.fb-connect__badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgb(0 0 0 / 4%), 0 6px 18px rgb(0 0 0 / 5%);
}

.fb-connect__qr {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 236px;
  height: 236px;
  margin-top: 22px;
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 1px 2px rgb(0 0 0 / 4%), 0 6px 18px rgb(0 0 0 / 5%);
}

.fb-connect__wait {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  margin-top: 16px;
  font-size: 13px;
  color: #6e6e73;
}

.fb-connect__dot {
  width: 8px;
  height: 8px;
  background: #34c759;
  border-radius: 50%;
  animation: fb-pulse 1.6s ease-in-out infinite;
}

@keyframes fb-pulse {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.3;
  }
}

.fb-connect__primary {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  margin-top: 14px;
  font-size: 14px;
  color: #0071e3;
  cursor: pointer;
  background: none;
  border: 0;
}

.fb-connect__primary:disabled {
  color: #a1a1a6;
  cursor: default;
}

.fb-connect__switch {
  display: inline-flex;
  gap: 2px;
  align-items: center;
  margin-top: auto;
  font-size: 13px;
  color: #0071e3;
}

.fb-connect__switch.is-disabled {
  color: #a1a1a6;
  cursor: default;
}

.fb-connect__right {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 36px 36px 24px;
  background: #fff;
}

.fb-connect__title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.35;
  color: #1d1d1f;
  letter-spacing: -0.01em;
}

.fb-connect__desc {
  margin: 12px 0 18px;
  font-size: 14px;
  line-height: 1.7;
  color: #86868b;
}

.fb-connect__tip {
  padding: 10px 14px;
  font-size: 13px;
  border-radius: 10px;
}

.fb-connect__tip.is-warn {
  color: #c93400;
  background: #fff7e6;
}

.fb-connect__tip.is-info {
  color: #0066cc;
  background: #e8f2fc;
}

.fb-connect__tip.is-error {
  color: #d70015;
  background: #fef2f2;
}

.fb-connect__steps {
  padding: 0;
  margin: 22px 0 14px;
  list-style: none;
}

.fb-connect__steps li {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  margin-bottom: 16px;
  font-size: 14px;
  line-height: 28px;
  color: #1d1d1f;
}

.fb-connect__num {
  display: inline-flex;
  flex: 0 0 28px;
  align-items: center;
  justify-content: center;
  height: 28px;
  font-size: 13px;
  color: #86868b;
  background: #f2f2f7;
  border-radius: 50%;
}

.fb-connect__foot {
  display: flex;
  justify-content: space-between;
  margin-top: auto;
  font-size: 12px;
  color: #86868b;
}

.fb-connect__foot a {
  color: #0071e3;
}

.fb-connect__foot a.text-gray-400 {
  color: #a1a1a6;
}

@media (max-width: 640px) {
  .fb-connect__body {
    flex-direction: column;
  }

  .fb-connect__left {
    flex-basis: auto;
  }

  .fb-connect__switch {
    margin-top: 16px;
  }

  .fb-connect__right {
    padding: 24px 16px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .fb-connect__dot {
    animation: none;
  }
}
</style>
