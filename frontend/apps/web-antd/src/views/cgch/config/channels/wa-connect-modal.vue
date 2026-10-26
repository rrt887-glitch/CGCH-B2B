<script lang="ts" setup>
/**
 * 连接 WhatsApp 渠道账号：左侧是扫码区，右侧是操作步骤。
 * 走 Meta 官方共存模式：客户输入正在用 WhatsApp Business App 的号码，用手机 App 扫码确认，
 * 手机照常使用，最近 6 个月的聊天记录同步过来。也可以改为开通一个新号码。
 */
import type { WaSignupConfig } from '#/api/channel/oauth';
import type { WaSignupMode } from './wa-signup';

import { computed, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { message, Modal } from 'ant-design-vue';

import { completeWaSignup, createYcloudLink, getYcloudResult } from '#/api/channel/oauth';

import { runWaSignup } from './wa-signup';

const props = defineProps<{ config: WaSignupConfig }>();
const emit = defineEmits<{ done: []; manual: [] }>();
const open = defineModel<boolean>('open', { default: false });

const mode = ref<WaSignupMode>('coexist');
/** idle 等待开始；waiting 授权窗口已打开；saving 正在接入 */
const state = ref<'idle' | 'saving' | 'waiting'>('idle');
const error = ref('');

const STEPS: Record<WaSignupMode, string[]> = {
  coexist: [
    '在手机上打开 WhatsApp Business App',
    '点击左侧「开始连接」，在弹出的窗口中点「Continue with Meta」，登录 Facebook 并选择你的企业',
    '输入手机上正在使用的 WhatsApp 号码',
    '按窗口提示用 WhatsApp Business App 扫描二维码，选择同步聊天记录后完成',
  ],
  new: [
    '准备一个没有在 WhatsApp App 上使用的手机号，能接收短信或电话',
    '点击左侧「开始连接」，在弹出的窗口中点「Continue with Meta」，登录 Facebook 并选择你的企业',
    '填写对外显示的企业名称，输入号码',
    '输入收到的验证码完成验证',
  ],
};

const tip = computed(() => {
  if (!props.config.enabled) return { type: 'warn', text: 'WhatsApp 官方接入正在开通中，平台审核通过后即可扫码连接' };
  if (error.value) return { type: 'error', text: error.value };
  if (state.value === 'waiting') return { type: 'warn', text: '请在弹出的 Meta 窗口中完成操作，不要关闭本窗口' };
  if (state.value === 'saving') return { type: 'warn', text: '正在接入账号，请稍候' };
  return mode.value === 'coexist'
    ? { type: 'info', text: '手机上的 WhatsApp Business App 可以继续正常使用，消息两边同步' }
    : { type: 'info', text: '新号码接入后只能在本系统中收发消息' };
});

/** 经 YCloud：打开托管的 Meta 授权页，轮询绑定结果（最多 15 分钟） */
async function startYcloud() {
  const win = window.open('about:blank', 'cgch-wa-connect', 'width=720,height=820');
  try {
    const link = await createYcloudLink(mode.value);
    if (win) win.location.href = link.url;
    else window.open(link.url, '_blank');
    const end = Date.now() + 15 * 60 * 1000;
    let closedAt = 0;
    while (Date.now() < end && open.value) {
      await new Promise((r) => setTimeout(r, 3000));
      const r = await getYcloudResult(link.ref).catch(() => null);
      if (r) {
        win?.close();
        open.value = false;
        emit('done');
        Modal.success({
          title: `已连接 WhatsApp ${r.displayPhone || ''}`,
          content:
            mode.value === 'coexist'
              ? '手机上的 WhatsApp Business App 可以继续使用，消息两边同步；客户发来的消息会出现在「会话」中。'
              : '号码已接入，客户发来的消息会出现在「会话」中。',
        });
        return;
      }
      // 授权窗口关闭后再等一会儿：绑定结果由 YCloud 推送过来，可能晚几秒
      if (win?.closed) {
        closedAt ||= Date.now();
        if (Date.now() - closedAt > 45_000) break;
      }
    }
    error.value = '没有完成连接，请重新点击「开始连接」';
  } catch (error_: any) {
    win?.close();
    throw error_;
  }
}

async function start() {
  if (!props.config.enabled || state.value !== 'idle') return;
  error.value = '';
  state.value = 'waiting';
  try {
    if (props.config.provider === 'ycloud') {
      await startYcloud();
      return;
    }
    const session = await runWaSignup(props.config, mode.value);
    if (!session) {
      error.value = '没有完成连接，请重新点击「开始连接」';
      return;
    }
    state.value = 'saving';
    const r = await completeWaSignup(session);
    open.value = false;
    emit('done');
    Modal.success({
      title: `已连接 WhatsApp ${r.displayPhone || ''}`,
      content: r.pin
        ? `已为这个号码设置两步验证 PIN：${r.pin}。请妥善保存，以后在其他地方重新注册这个号码时需要用到。`
        : session.mode === 'coexist'
          ? '聊天记录正在后台同步，稍后可在「会话」中查看。手机上的 WhatsApp Business App 可以继续使用。'
          : r.warning || '号码已接入，客户发来的消息会出现在「会话」中。',
    });
  } catch (error_: any) {
    error.value = error_?.message || '连接失败，请重试';
    if (error_?.message) message.error(error_.message);
  } finally {
    state.value = 'idle';
  }
}

function switchMode() {
  if (state.value !== 'idle') return;
  error.value = '';
  mode.value = mode.value === 'coexist' ? 'new' : 'coexist';
}
</script>

<template>
  <Modal v-model:open="open" :footer="null" :width="760" centered :body-style="{ padding: 0 }" wrap-class-name="wa-connect">
    <div class="wa-connect__body">
      <section class="wa-connect__left">
        <div class="wa-connect__badge">
          <IconifyIcon icon="lucide:link-2" class="size-6 text-gray-700" />
        </div>
        <div class="mt-4 text-lg font-semibold">{{ mode === 'coexist' ? '扫码连接设备' : '开通新号码' }}</div>

        <button type="button" class="wa-connect__qr" :disabled="!config.enabled || state !== 'idle'" @click="start">
          <template v-if="state === 'idle'">
            <IconifyIcon :icon="mode === 'coexist' ? 'lucide:scan-qr-code' : 'lucide:smartphone'" class="size-12 text-gray-400" />
            <span class="mt-3 text-sm text-gray-500">
              {{ config.enabled ? '点击开始连接' : '即将开放' }}
            </span>
          </template>
          <template v-else>
            <IconifyIcon icon="lucide:loader-circle" class="size-10 animate-spin text-[#155dfc]" />
            <span class="mt-3 text-sm text-gray-500">{{ state === 'waiting' ? '等待在 Meta 窗口中完成' : '正在接入' }}</span>
          </template>
        </button>

        <button type="button" class="wa-connect__primary" :disabled="!config.enabled || state !== 'idle'" @click="start">
          <IconifyIcon icon="lucide:refresh-cw" class="size-4" />
          {{ error ? '重新连接' : '开始连接' }}
        </button>

        <a class="wa-connect__switch" @click="switchMode">
          {{ mode === 'coexist' ? '没有 WhatsApp Business App？开通新号码' : '切换为扫码连接' }}
          <IconifyIcon icon="lucide:chevron-right" class="size-4" />
        </a>
      </section>

      <section class="wa-connect__right">
        <h3 class="wa-connect__title">连接您的<br />WhatsApp渠道账号</h3>
        <p class="wa-connect__desc">
          {{
            mode === 'coexist'
              ? '使用手机上的 WhatsApp Business App 扫码确认，即可安全快捷地把账号接入草根出海。'
              : '通过 Meta 官方流程为企业开通一个新的 WhatsApp 号码。'
          }}
        </p>
        <div class="wa-connect__tip" :class="`is-${tip.type}`">{{ tip.text }}</div>

        <ol class="wa-connect__steps">
          <li v-for="(s, i) in STEPS[mode]" :key="i">
            <span class="wa-connect__num">{{ i + 1 }}</span>
            <span>{{ s }}</span>
          </li>
        </ol>

        <div v-if="mode === 'coexist'" class="text-xs leading-5 text-gray-500">
          还在用个人版 WhatsApp？在应用商店下载 WhatsApp Business，用同一个号码登录，聊天记录会自动迁移。
        </div>

        <div class="wa-connect__foot">
          <span>
            连接即代表同意
            <a href="/terms" target="_blank">服务协议</a>
            与
            <a href="/privacy" target="_blank">隐私政策</a>
          </span>
          <a class="text-gray-400" @click="emit('manual')">手动填写 API 参数</a>
        </div>
      </section>
    </div>
  </Modal>
</template>

<style scoped>
.wa-connect__body {
  display: flex;
  min-height: 520px;
  overflow: hidden;
  border-radius: 8px;
}

.wa-connect__left {
  display: flex;
  flex: 0 0 300px;
  flex-direction: column;
  align-items: center;
  padding: 36px 20px 24px;
  background: #f5f6f8;
}

.wa-connect__badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  background: #fff;
  border-radius: 12px;
}

.wa-connect__qr {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 236px;
  height: 236px;
  margin-top: 22px;
  cursor: pointer;
  background: #fff;
  border: 10px solid #fff;
  border-radius: 20px;
  box-shadow: inset 0 0 0 1px #eef0f3;
}

.wa-connect__qr:disabled {
  cursor: default;
}

.wa-connect__qr:not(:disabled):hover {
  box-shadow: inset 0 0 0 1px #155dfc;
}

.wa-connect__primary {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  margin-top: 22px;
  font-size: 14px;
  color: #155dfc;
  cursor: pointer;
  background: none;
  border: 0;
}

.wa-connect__primary:disabled {
  color: #9ca3af;
  cursor: default;
}

.wa-connect__switch {
  display: inline-flex;
  gap: 2px;
  align-items: center;
  margin-top: auto;
  font-size: 13px;
  color: #155dfc;
}

.wa-connect__right {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 36px 36px 24px;
  background: #fff;
}

.wa-connect__title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.35;
  color: #111827;
}

.wa-connect__desc {
  margin: 12px 0 18px;
  font-size: 14px;
  line-height: 1.7;
  color: #6b7280;
}

.wa-connect__tip {
  padding: 10px 14px;
  font-size: 13px;
  border-radius: 8px;
}

.wa-connect__tip.is-warn {
  color: #b45309;
  background: #fff7e6;
}

.wa-connect__tip.is-info {
  color: #1d4ed8;
  background: #eef4ff;
}

.wa-connect__tip.is-error {
  color: #b91c1c;
  background: #fef2f2;
}

.wa-connect__steps {
  padding: 0;
  margin: 22px 0 14px;
  list-style: none;
}

.wa-connect__steps li {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  margin-bottom: 16px;
  font-size: 14px;
  line-height: 28px;
  color: #1f2937;
}

.wa-connect__num {
  display: inline-flex;
  flex: 0 0 28px;
  align-items: center;
  justify-content: center;
  height: 28px;
  font-size: 13px;
  color: #6b7280;
  background: #f3f4f6;
  border-radius: 50%;
}

.wa-connect__foot {
  display: flex;
  justify-content: space-between;
  margin-top: auto;
  font-size: 12px;
  color: #6b7280;
}

.wa-connect__foot a {
  color: #155dfc;
}

.wa-connect__foot a.text-gray-400 {
  color: #9ca3af;
}

@media (max-width: 640px) {
  .wa-connect__body {
    flex-direction: column;
  }

  .wa-connect__left {
    flex-basis: auto;
  }

  .wa-connect__switch {
    margin-top: 16px;
  }

  .wa-connect__right {
    padding: 24px 16px;
  }
}
</style>
