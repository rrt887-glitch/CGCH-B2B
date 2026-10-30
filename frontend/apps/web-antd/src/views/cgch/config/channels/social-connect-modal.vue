<script lang="ts" setup>
/**
 * 连接 YouTube / TikTok（与原网站一致）。YouTube：左侧扫码在手机上授权，或点按钮在网页授权；
 * TikTok：填写用户名后点「授权 TikTok」。授权后系统定时拉取视频下的新评论进「会话」，AI 或业务员回复评论。
 */
import type { SocialPlatform } from '#/api/channel/social';

import { computed, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Button, Input, message } from 'ant-design-vue';

import { getSocialAuthorizeUrl } from '#/api/channel/social';
import { Qrcode } from '#/components/qrcode';

const props = defineProps<{ enabled: boolean; platform: SocialPlatform }>();
const open = defineModel<boolean>('open', { default: false });

const url = ref('');
const loading = ref(false);
const username = ref('');
const isYt = computed(() => props.platform === 'youtube');

async function loadUrl() {
  if (!props.enabled) return;
  loading.value = true;
  try {
    url.value = await getSocialAuthorizeUrl(props.platform, username.value.trim() || undefined);
  } finally {
    loading.value = false;
  }
}

watch(open, (v) => {
  if (v) {
    url.value = '';
    username.value = '';
    if (isYt.value) loadUrl();
  }
});

async function openWeb() {
  if (!props.enabled) return;
  if (!isYt.value && !username.value.trim()) {
    message.warning('请输入 TikTok 用户名');
    return;
  }
  const win = window.open('about:blank', 'cgch-social-oauth', 'width=600,height=760');
  try {
    if (!isYt.value || !url.value) await loadUrl();
    if (win) win.location.href = url.value;
    else window.location.href = url.value;
  } catch (error) {
    win?.close();
    throw error;
  }
}
</script>

<template>
  <!-- YouTube：扫码 + 网页授权 -->
  <div v-if="open && isYt" class="sc-mask" @click.self="open = false">
    <div class="sc-yt">
      <button type="button" class="sc-close" @click="open = false"><IconifyIcon icon="lucide:x" class="size-4" /></button>
      <div class="sc-yt__left">
        <div class="sc-yt__icon"><IconifyIcon icon="lucide:link-2" class="size-5" /></div>
        <div class="mt-3 text-base font-semibold">扫码连接设备</div>
        <div class="sc-yt__qr">
          <Qrcode v-if="url" :text="url" :width="190" />
          <div v-else class="flex h-[190px] w-[190px] items-center justify-center text-xs text-gray-400">
            {{ enabled ? (loading ? '正在生成二维码…' : '二维码加载失败') : '管理员还没有配置 YouTube 授权应用' }}
          </div>
        </div>
        <a class="mt-4 text-sm" :class="{ 'opacity-50': loading }" @click="loadUrl"><IconifyIcon icon="lucide:rotate-cw" class="mr-1 inline size-3.5" />刷新二维码</a>
        <Button type="primary" size="large" class="mt-6 w-[200px]" :disabled="!enabled" @click="openWeb">点击跳转网页版授权</Button>
      </div>
      <div class="sc-yt__right">
        <h3 class="m-0 text-2xl font-bold leading-snug text-gray-900">连接您的<br />YouTube渠道账号</h3>
        <p class="mt-3 text-sm leading-6 text-gray-500">使用移动设备扫描左侧二维码，即可安全快捷地授权登录当前电脑。</p>
        <ol class="sc-steps">
          <li><span>1</span>接下来将跳转至 YouTube 官方页面进行授权</li>
          <li><span>2</span>必须使用 YouTube 账号完成绑定</li>
          <li><span>3</span>手机扫码授权后需要手动刷新当前页面</li>
        </ol>
        <div class="mt-auto text-xs text-gray-500">
          扫描即代表同意 <a href="/terms" target="_blank">服务协议</a> 与 <a href="/privacy" target="_blank">隐私政策</a>
        </div>
      </div>
    </div>
  </div>

  <!-- TikTok：用户名 + 授权 -->
  <div v-if="open && !isYt" class="sc-mask" @click.self="open = false">
    <div class="sc-tt">
      <button type="button" class="sc-close" @click="open = false"><IconifyIcon icon="lucide:x" class="size-4" /></button>
      <div class="sc-tt__logo"><IconifyIcon icon="simple-icons:tiktok" class="size-7 text-white" /></div>
      <div class="mt-4 text-center text-xl font-bold text-gray-900">连接 TikTok</div>
      <div class="mt-6 flex items-center gap-3 text-sm"><span class="sc-no">1</span>输入您的 TikTok 用户名</div>
      <Input v-model:value="username" class="ml-9 mt-3 !w-[288px]" size="large" placeholder="请输入 TikTok 用户名（如 yisorz）" />
      <div class="mt-5 flex items-center gap-3 text-sm"><span class="sc-no">2</span>点击下方按钮完成 TikTok 账号授权</div>
      <div class="mt-4 text-center">
        <Button size="large" :disabled="!enabled || !username.trim()" :loading="loading" @click="openWeb">授权 TikTok</Button>
      </div>
      <div v-if="!enabled" class="mt-3 text-center text-xs text-gray-400">管理员还没有配置 TikTok 授权应用</div>
    </div>
  </div>
</template>

<style scoped>
.sc-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgb(0 0 0 / 45%);
}

.sc-close {
  position: absolute;
  top: 14px;
  right: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: #6b7280;
  cursor: pointer;
  background: #f3f4f6;
  border: 0;
  border-radius: 50%;
}

.sc-yt {
  position: relative;
  display: flex;
  width: 680px;
  max-width: calc(100vw - 32px);
  min-height: 540px;
  overflow: hidden;
  background: #fff;
  border-radius: 20px;
}

.sc-yt__left {
  display: flex;
  flex: 0 0 290px;
  flex-direction: column;
  align-items: center;
  padding: 44px 24px 32px;
  background: #f7f8fa;
}

.sc-yt__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  color: #111827;
  background: #fff;
  border-radius: 10px;
}

.sc-yt__qr {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 244px;
  height: 244px;
  margin-top: 26px;
  background: #fff;
  border-radius: 18px;
}

.sc-yt__right {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 44px 36px 32px;
}

.sc-steps {
  padding: 0;
  margin: 24px 0 0;
  list-style: none;
}

.sc-steps li {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 18px;
  font-size: 14px;
  color: #374151;
}

.sc-steps span {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  font-size: 13px;
  color: #6b7280;
  background: #f3f4f6;
  border-radius: 50%;
}

.sc-tt {
  position: relative;
  width: 430px;
  max-width: calc(100vw - 32px);
  padding: 44px 40px 40px;
  background: #fff;
  border-radius: 16px;
}

.sc-tt__logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  margin: 0 auto;
  background: #111;
  border-radius: 12px;
}

.sc-no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  background: #2563EB;
  border-radius: 50%;
}

@media (width <= 640px) {
  .sc-yt {
    flex-direction: column;
  }
}
</style>
