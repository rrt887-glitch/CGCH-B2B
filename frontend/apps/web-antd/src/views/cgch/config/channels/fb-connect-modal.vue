<script lang="ts" setup>
/**
 * 连接 Facebook 主页（及关联的 Instagram）：左侧是授权二维码，右侧是操作步骤。
 * 二维码就是 Facebook 官方授权页的地址：手机扫码后在手机上登录 Facebook、选择主页并授权，
 * 本窗口每 2 秒查询一次结果，授权完成后自动关闭并刷新账号列表。也可以直接在本机浏览器打开授权页。
 * 本系统不接触 Facebook 账号密码，授权随时可以在 Facebook 设置里撤销。
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { message, Modal, QRCode } from 'ant-design-vue';

import { getMetaAuthorize, getMetaAuthorizeStatus } from '#/api/channel/oauth';

const emit = defineEmits<{ done: []; manual: [] }>();
const open = defineModel<boolean>('open', { default: false });

/** loading 正在生成二维码；ready 等待扫码；saving 已授权正在接入；expired 二维码过期；error 生成失败 */
const state = ref<'error' | 'expired' | 'loading' | 'ready'>('loading');
const url = ref('');
const sid = ref('');
const error = ref('');
let pollTimer: ReturnType<typeof setInterval> | undefined;
let expireTimer: ReturnType<typeof setTimeout> | undefined;

const STEPS = [
  '用手机相机或 Facebook App 扫描左侧二维码',
  '在手机上登录 Facebook，选择要接入的主页（关联的 Instagram 专业账号会一起接入）',
  '确认授权，完成后本页面会自动刷新',
];

const tip = computed(() => {
  if (error.value) return { type: 'error', text: error.value };
  if (state.value === 'expired') return { type: 'warn', text: '二维码已过期，请点击刷新' };
  return { type: 'info', text: '使用 Facebook 官方授权，本系统不会获取你的 Facebook 密码，随时可以在 Facebook 设置中撤销' };
});

function stop() {
  if (pollTimer) clearInterval(pollTimer);
  if (expireTimer) clearTimeout(expireTimer);
  pollTimer = undefined;
  expireTimer = undefined;
}

async function refresh() {
  stop();
  error.value = '';
  state.value = 'loading';
  try {
    const r = await getMetaAuthorize();
    url.value = r.url;
    sid.value = r.state;
    state.value = 'ready';
    pollTimer = setInterval(poll, 2000);
    // 授权链接 10 分钟内有效，提前一点提示刷新
    expireTimer = setTimeout(() => {
      stop();
      state.value = 'expired';
    }, 9.5 * 60_000);
  } catch (error_: any) {
    state.value = 'error';
    error.value = error_?.message || '生成二维码失败，请重试';
  }
}

let polling = false;
async function poll() {
  if (polling || !sid.value) return;
  polling = true;
  try {
    const r = await getMetaAuthorizeStatus(sid.value);
    if (r.status === 'ok') {
      stop();
      open.value = false;
      emit('done');
      Modal.success({ title: 'Facebook 授权完成', content: r.message || '已接入主页和关联的 Instagram' });
    } else if (r.status === 'fail') {
      stop();
      state.value = 'expired';
      error.value = r.message || '授权失败，请刷新二维码重试';
    } else if (r.status === 'expired') {
      stop();
      state.value = 'expired';
    }
  } catch {
    // 网络抖动时下次再查
  } finally {
    polling = false;
  }
}

/** 在本机浏览器打开授权页（电脑上已登录 Facebook 时更方便），结果仍由轮询得知 */
function openHere() {
  if (state.value !== 'ready' || !url.value) return;
  const win = window.open(url.value, 'cgch-meta-oauth', 'width=600,height=760');
  if (!win) message.warning('浏览器拦截了弹出窗口，请允许弹窗后重试，或直接用手机扫码');
}

watch(open, (v) => (v ? refresh() : stop()), { immediate: true });
onBeforeUnmount(stop);
</script>

<template>
  <Modal v-model:open="open" :footer="null" :width="760" centered :body-style="{ padding: 0 }" wrap-class-name="fb-connect">
    <div class="fb-connect__body">
      <section class="fb-connect__left">
        <div class="fb-connect__badge">
          <IconifyIcon icon="logos:facebook" class="size-6" />
        </div>
        <div class="mt-4 text-lg font-semibold">扫码授权主页</div>

        <div class="fb-connect__qr">
          <QRCode v-if="state === 'ready' || state === 'expired'" :value="url" :size="200" :bordered="false" :status="state === 'expired' ? 'expired' : 'active'" @refresh="refresh" />
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
        <h3 class="fb-connect__title">连接您的<br />Facebook 主页</h3>
        <p class="fb-connect__desc">扫码登录 Facebook 并授权，主页私信和关联的 Instagram 私信就会进入草根出海，历史对话也会同步过来。</p>
        <div class="fb-connect__tip" :class="`is-${tip.type}`">{{ tip.text }}</div>

        <ol class="fb-connect__steps">
          <li v-for="(s, i) in STEPS" :key="i">
            <span class="fb-connect__num">{{ i + 1 }}</span>
            <span>{{ s }}</span>
          </li>
        </ol>

        <div class="text-xs leading-5 text-gray-500">
          只能接入你在 Facebook 上有管理权限的主页。Instagram 需要是专业账号，并已关联到这个主页。
        </div>

        <div class="fb-connect__foot">
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
