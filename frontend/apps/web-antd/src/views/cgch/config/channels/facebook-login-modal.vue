<script lang="ts" setup>
/**
 * Facebook 直接登录（Unipile Custom Auth）。
 *
 * 托管授权页（Hosted Auth）的 intent 会过期，导致「选 Page」这一步报 Intent not found。
 * 这里改成我们自己的向导，直接调 Unipile 的 /v2/auth/intent + /v2/auth/checkpoint：
 *   账号密码 → 勾选要接管的 Page → 完成（可选跳过加密聊天历史）
 */
import { computed, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import {
  Alert,
  Button,
  Checkbox,
  Empty,
  Input,
  InputPassword,
  message,
  Modal,
  Spin,
  Tabs,
} from 'ant-design-vue';

import type { FacebookAuthResult } from '#/api/channel/unipile';
import { facebookLogin, facebookSolve } from '#/api/channel/unipile';

const emit = defineEmits<{ done: [] }>();
const open = defineModel<boolean>('open', { default: false });

type Step = 'checkpoint' | 'done' | 'form' | 'loading' | 'products' | 'verify';

const step = ref<Step>('form');
const mode = ref<'cookies' | 'password'>('password');
const username = ref('');
const password = ref('');
const cookies = ref('');
const userAgent = ref(navigator.userAgent);

const intentId = ref('');
const products = ref<{ id: string; display_name: string }[]>([]);
const selected = ref<string[]>([]);
const verifyType = ref('');
const verifyCode = ref('');
const error = ref('');
const submitting = ref(false);
const waitingText = ref('');

const selectAllPages = computed(() => {
  const pages = products.value.filter((p) => p.id.startsWith('page_'));
  return pages.length > 0 && pages.every((p) => selected.value.includes(p.id));
});

function reset() {
  step.value = 'form';
  error.value = '';
  intentId.value = '';
  products.value = [];
  selected.value = [];
  verifyCode.value = '';
  verifyType.value = '';
}

watch(open, (v) => {
  if (v) reset();
});

async function submitForm() {
  error.value = '';
  if (mode.value === 'password' && (!username.value || !password.value)) {
    error.value = '请填写 Facebook 的邮箱/手机号和密码';
    return;
  }
  if (mode.value === 'cookies' && !cookies.value.trim()) {
    error.value = '请粘贴 facebook.com 的 cookie';
    return;
  }
  submitting.value = true;
  step.value = 'loading';
  try {
    const r = await facebookLogin({
      username: username.value || undefined,
      password: password.value || undefined,
      cookies: mode.value === 'cookies' ? cookies.value : undefined,
      userAgent: mode.value === 'cookies' ? userAgent.value : undefined,
      selectPages: true,
    });
    handleResult(r);
  } catch (e: any) {
    error.value = e?.message || '登录失败，请重试';
    step.value = 'form';
  } finally {
    submitting.value = false;
  }
}

function handleResult(r: FacebookAuthResult) {
  if (r.status === 'account') {
    step.value = 'done';
    message.success('Facebook 已连接');
    emit('done');
    setTimeout(() => (open.value = false), 600);
    return;
  }
  intentId.value = r.intentId || intentId.value;
  const type = (r.type || '').toUpperCase();
  if (type === 'CONSENT_SCREEN') {
    products.value = (r.products as any) || [];
    selected.value = products.value
      .filter((p) => p.id.startsWith('page_'))
      .map((p) => p.id);
    step.value = 'products';
    return;
  }
  if (type === 'ENCRYPTED_HISTORY') {
    // 不导入历史加密消息，直接跳过
    void solve('SKIP');
    return;
  }
  if (type === '2FA' || type === 'OTP' || type === 'PHONE_REGISTER') {
    verifyType.value = type;
    verifyCode.value = '';
    step.value = 'verify';
    return;
  }
  if (type === 'IN_APP_VALIDATION' || type === 'LOADING') {
    waitingText.value =
      type === 'IN_APP_VALIDATION'
        ? '请在手机上打开 Facebook，确认这次登录'
        : '正在完成登录…';
    step.value = 'verify';
    void solve('');
    return;
  }
  error.value = `需要额外的验证步骤（${r.type || '未知'}），请改用 Cookies 方式或稍后再试`;
  step.value = 'form';
}

async function solve(code: string, credential?: string) {
  submitting.value = true;
  try {
    const r = await facebookSolve({ intentId: intentId.value, code, credential });
    handleResult(r);
  } catch (e: any) {
    error.value = e?.message || '验证失败，请重试';
    step.value = 'verify';
  } finally {
    submitting.value = false;
  }
}

function togglePage(id: string, checked: boolean) {
  if (checked) {
    if (!selected.value.includes(id)) selected.value.push(id);
  } else {
    selected.value = selected.value.filter((x) => x !== id);
  }
}

function toggleAllPages(checked: boolean) {
  const pages = products.value
    .filter((p) => p.id.startsWith('page_'))
    .map((p) => p.id);
  if (checked) {
    selected.value = Array.from(new Set([...selected.value, ...pages]));
  } else {
    selected.value = selected.value.filter((x) => !pages.includes(x));
  }
}

async function submitProducts() {
  if (!selected.value.length) {
    error.value = '请至少勾选一个要接入的主页（个人 Messenger 默认会连）';
    return;
  }
  error.value = '';
  await solve(JSON.stringify(selected.value));
}

async function submitVerify() {
  if (verifyType.value === 'IN_APP_VALIDATION' || verifyType.value === 'LOADING') {
    await solve('');
    return;
  }
  if (!verifyCode.value.trim()) {
    error.value = '请填写验证码';
    return;
  }
  error.value = '';
  await solve(verifyCode.value.trim());
}
</script>

<template>
  <Modal
    v-model:open="open"
    :footer="null"
    :width="520"
    centered
    title="连接 Facebook"
  >
    <div class="fb-login">
      <Alert
        v-if="error"
        type="error"
        :message="error"
        show-icon
        class="mb-3"
      />

      <!-- 第 1 步：账号密码 / Cookies -->
      <template v-if="step === 'form'">
        <p class="fb-login__tip">
          直接在本系统登录 Facebook，不用再经过 Unipile 的授权页（那个页面会过期）。
        </p>
        <Tabs v-model:active-key="mode" size="small">
          <Tabs.TabPane key="password" tab="账号密码">
            <div class="fb-login__field">
              <label>邮箱 / 手机号</label>
              <Input v-model:value="username" placeholder="例如 name@example.com 或 +8613..." />
            </div>
            <div class="fb-login__field">
              <label>密码</label>
              <InputPassword v-model:value="password" placeholder="Facebook 登录密码" />
            </div>
            <p class="fb-login__hint">
              开了两步验证也没关系，下一步会让你填验证码。
            </p>
          </Tabs.TabPane>
          <Tabs.TabPane key="cookies" tab="Cookies（最稳）">
            <div class="fb-login__field">
              <label>facebook.com 的 Cookie</label>
              <Input.TextArea
                v-model:value="cookies"
                :rows="4"
                placeholder="从浏览器扩展导出 cookie 后粘贴到这里，例如 c_user=...; xs=...; datr=..."
              />
            </div>
            <p class="fb-login__hint">
              先在自己浏览器登录 Facebook，再用 EditThisCookie / Cookie-Editor 导出，粘贴进来。
            </p>
          </Tabs.TabPane>
        </Tabs>
        <div class="fb-login__actions">
          <Button @click="open = false">取消</Button>
          <Button type="primary" :loading="submitting" @click="submitForm">
            下一步
          </Button>
        </div>
      </template>

      <!-- 加载中 -->
      <template v-else-if="step === 'loading'">
        <div class="fb-login__center">
          <Spin />
          <p class="mt-3 text-sm text-gray-500">正在登录 Facebook…</p>
        </div>
      </template>

      <!-- 选 Page -->
      <template v-else-if="step === 'products'">
        <p class="fb-login__tip">选择要接入的主页。个人 Messenger 默认会连上。</p>
        <div class="fb-login__list">
          <label class="fb-login__item fb-login__item--all">
            <Checkbox
              :checked="selectAllPages"
              @change="(e: any) => toggleAllPages(e.target.checked)"
            />
            <span>全选主页</span>
          </label>
          <label
            v-for="p in products"
            :key="p.id"
            class="fb-login__item"
            :class="{ 'is-messaging': !p.id.startsWith('page_') }"
          >
            <Checkbox
              :checked="p.id.startsWith('page_') ? selected.includes(p.id) : true"
              :disabled="!p.id.startsWith('page_')"
              @change="(e: any) => togglePage(p.id, e.target.checked)"
            />
            <span>
              {{ p.display_name }}
              <em v-if="!p.id.startsWith('page_')">（个人 Messenger，必连）</em>
            </span>
          </label>
        </div>
        <div class="fb-login__actions">
          <Button @click="step = 'form'">上一步</Button>
          <Button type="primary" :loading="submitting" @click="submitProducts">
            完成连接
          </Button>
        </div>
      </template>

      <!-- 验证码 / 等待确认 -->
      <template v-else-if="step === 'verify'">
        <template v-if="verifyType === '2FA' || verifyType === 'OTP' || verifyType === 'PHONE_REGISTER'">
          <p class="fb-login__tip">
            请输入 Facebook 发来的验证码（两步验证 / 短信 / 邮件）。
          </p>
          <div class="fb-login__field">
            <Input v-model:value="verifyCode" placeholder="验证码" @press-enter="submitVerify" />
          </div>
          <div class="fb-login__actions">
            <Button @click="step = 'form'">返回</Button>
            <Button type="primary" :loading="submitting" @click="submitVerify">
              提交
            </Button>
          </div>
        </template>
        <template v-else>
          <div class="fb-login__center">
            <IconifyIcon icon="lucide:smartphone" class="size-8 text-[#0071E3]" />
            <p class="mt-3 text-sm text-gray-600">{{ waitingText }}</p>
            <p class="mt-1 text-xs text-gray-400">完成操作后点下面的按钮</p>
            <Button
              class="mt-4"
              type="primary"
              :loading="submitting"
              @click="submitVerify"
            >
              我已完成
            </Button>
          </div>
        </template>
      </template>

      <!-- 完成 -->
      <template v-else>
        <div class="fb-login__center">
          <IconifyIcon icon="lucide:circle-check-big" class="size-10 text-[#34C759]" />
          <p class="mt-3 text-sm text-gray-600">Facebook 已连接</p>
        </div>
      </template>
    </div>
  </Modal>
</template>

<style scoped>
.fb-login {
  padding: 4px 2px 8px;
}
.fb-login__tip {
  margin: 0 0 12px;
  font-size: 13px;
  color: #6e6e73;
}
.fb-login__field {
  margin-bottom: 12px;
}
.fb-login__field label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: #3a3a3c;
}
.fb-login__hint {
  margin: 2px 0 0;
  font-size: 12px;
  color: #8e8e93;
}
.fb-login__actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 18px;
}
.fb-login__list {
  max-height: 320px;
  overflow: auto;
  border: 1px solid rgb(0 0 0 / 6%);
  border-radius: 12px;
}
.fb-login__item {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 10px 14px;
  font-size: 13.5px;
  cursor: pointer;
  border-bottom: 1px solid rgb(0 0 0 / 5%);
}
.fb-login__item:last-child {
  border-bottom: none;
}
.fb-login__item.is-messaging {
  cursor: default;
  background: rgb(0 122 255 / 4%);
}
.fb-login__item em {
  font-size: 12px;
  font-style: normal;
  color: #8e8e93;
}
.fb-login__item--all {
  font-weight: 600;
  background: rgb(118 118 128 / 6%);
}
.fb-login__center {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 28px 0;
}
</style>
