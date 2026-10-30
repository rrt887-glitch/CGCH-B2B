<script lang="ts" setup>
/**
 * WhatsApp 扫码接入（与原网站「WhatsApp」渠道一致）：默认输入电话号码 →「下一步」拿到 8 位配对码，
 * 在手机 WhatsApp「已关联的设备 → 关联新设备 → 改用电话号码关联」输入；也可以「切换为二维码登录」用手机扫码。
 * 手机确认后自动完成连接。基于开源项目 Evolution API（WhatsApp 网页版协议），不是 Meta 官方接口。
 */
import type { EvoSession } from '#/api/channel/evolution';

import { computed, onBeforeUnmount, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Button, Input, message, Modal, Select, Spin } from 'ant-design-vue';

import { cancelEvoSession, getEvoConfig, getEvoSession, startEvoSession } from '#/api/channel/evolution';

const props = defineProps<{ accountId?: number }>();
const emit = defineEmits<{ done: [] }>();
const open = defineModel<boolean>('open', { default: false });

/** 常用国家 / 地区与区号 */
const COUNTRIES: [string, string, string][] = [
  ['CN', '中国', '86'], ['HK', '中国香港', '852'], ['MO', '中国澳门', '853'], ['TW', '中国台湾', '886'],
  ['US', '美国 / 加拿大', '1'], ['GB', '英国', '44'], ['DE', '德国', '49'], ['FR', '法国', '33'], ['IT', '意大利', '39'],
  ['ES', '西班牙', '34'], ['NL', '荷兰', '31'], ['PL', '波兰', '48'], ['RU', '俄罗斯', '7'], ['TR', '土耳其', '90'],
  ['AE', '阿联酋', '971'], ['SA', '沙特阿拉伯', '966'], ['EG', '埃及', '20'], ['NG', '尼日利亚', '234'], ['KE', '肯尼亚', '254'],
  ['ZA', '南非', '27'], ['IN', '印度', '91'], ['PK', '巴基斯坦', '92'], ['BD', '孟加拉国', '880'], ['ID', '印度尼西亚', '62'],
  ['MY', '马来西亚', '60'], ['SG', '新加坡', '65'], ['TH', '泰国', '66'], ['VN', '越南', '84'], ['PH', '菲律宾', '63'],
  ['JP', '日本', '81'], ['KR', '韩国', '82'], ['AU', '澳大利亚', '61'], ['NZ', '新西兰', '64'], ['BR', '巴西', '55'],
  ['MX', '墨西哥', '52'], ['AR', '阿根廷', '54'], ['CL', '智利', '56'], ['CO', '哥伦比亚', '57'], ['PE', '秘鲁', '51'],
];
const countryOptions = COUNTRIES.map(([code, name, dial]) => ({ value: code, label: `${code}  ${name}`, dial }));

const enabled = ref<boolean | null>(null);
/** phone 输入号码；code 显示配对码；qr 显示二维码 */
const view = ref<'code' | 'phone' | 'qr'>('phone');
const country = ref('CN');
const phone = ref('');
const loading = ref(false);
const session = ref<EvoSession | null>(null);
const expired = ref(false);
let timer: ReturnType<typeof setTimeout> | undefined;
let finished = false;

const dial = computed(() => countryOptions.find((c) => c.value === country.value)?.dial ?? '');
const prettyCode = computed(() => {
  const c = session.value?.pairingCode ?? '';
  return c.length === 8 ? `${c.slice(0, 4)}-${c.slice(4)}` : c;
});

function stopPolling() {
  if (timer) clearTimeout(timer);
  timer = undefined;
}

async function abandon() {
  stopPolling();
  const s = session.value?.session;
  session.value = null;
  if (s && !finished) await cancelEvoSession(s).catch(() => {});
}

function reset() {
  view.value = 'phone';
  phone.value = '';
  expired.value = false;
  finished = false;
}

watch(open, async (v) => {
  if (v) {
    reset();
    if (enabled.value === null) enabled.value = await getEvoConfig().then((c) => c.enabled).catch(() => false);
  } else {
    await abandon();
  }
});
onBeforeUnmount(abandon);

async function begin(mode: 'code' | 'qr') {
  if (!enabled.value) return;
  let number: string | undefined;
  if (mode === 'code') {
    const local = phone.value.replaceAll(/\D/g, '').replace(/^0+/, '');
    if (local.length < 5) return message.warning('请输入电话号码');
    number = dial.value + local;
  }
  await abandon();
  finished = false;
  expired.value = false;
  loading.value = true;
  view.value = mode;
  try {
    // 配对码有时晚几秒生成，轮询时会补上
    session.value = await startEvoSession({ number, accountId: props.accountId });
    poll();
  } catch {
    session.value = null;
    if (mode === 'code') view.value = 'phone';
    else expired.value = true; // 显示「点击刷新」
  } finally {
    loading.value = false;
  }
}

function poll() {
  stopPolling();
  timer = setTimeout(async () => {
    const s = session.value;
    if (!s || !open.value) return;
    try {
      const r = await getEvoSession(s.session);
      if (session.value?.session !== s.session) return;
      if (r.status === 'open') {
        finished = true;
        stopPolling();
        open.value = false;
        emit('done');
        Modal.success({
          title: `已连接 WhatsApp ${r.displayPhone || ''}`,
          content: '客户发来的消息会出现在「会话」中；手机上的 WhatsApp 可以继续正常使用。',
        });
        return;
      }
      if (r.status === 'expired') {
        expired.value = true;
        return;
      }
      session.value = { ...s, qr: r.qr || s.qr, pairingCode: r.pairingCode || s.pairingCode };
    } catch {
      // 网络抖动：继续轮询
    }
    poll();
  }, 2500);
}

function switchTo(v: 'phone' | 'qr') {
  if (v === 'qr') begin('qr');
  else {
    abandon();
    expired.value = false;
    view.value = 'phone';
  }
}

async function copyCode() {
  await navigator.clipboard.writeText(session.value?.pairingCode ?? '');
  message.success('配对码已复制');
}
</script>

<template>
  <Modal v-model:open="open" :footer="null" :width="760" centered :body-style="{ padding: 0 }" wrap-class-name="evo-connect">
    <div class="evo">
      <template v-if="enabled === false">
        <h2 class="evo__title">WhatsApp 扫码接入即将开放</h2>
        <p class="evo__sub">管理员开通后即可用手机号或二维码连接 WhatsApp 账号</p>
      </template>

      <!-- 输入电话号码 -->
      <template v-else-if="view === 'phone'">
        <h2 class="evo__title">输入电话号码，连接您的 WhatsApp 账号</h2>
        <p class="evo__sub">选择国家/地区，并输入你的电话号码</p>
        <div class="evo__form">
          <div class="evo__label">所属国家</div>
          <Select v-model:value="country" :options="countryOptions" show-search option-filter-prop="label" size="large" class="w-full" />
          <div class="evo__label mt-5">电话号码</div>
          <Input v-model:value="phone" size="large" placeholder="请输入电话号码" @press-enter="begin('code')">
            <template #prefix><span class="pr-1 text-gray-700">+{{ dial }}</span></template>
          </Input>
          <Button type="primary" size="large" block class="evo__btn" :loading="loading" @click="begin('code')">下一步</Button>
          <a class="evo__switch" @click="switchTo('qr')">切换为二维码登录 <IconifyIcon icon="lucide:chevron-right" class="size-4" /></a>
        </div>
      </template>

      <!-- 配对码 -->
      <template v-else-if="view === 'code'">
        <h2 class="evo__title">在手机上输入配对码</h2>
        <p class="evo__sub">+{{ dial }} {{ phone }}</p>
        <Spin :spinning="loading || (!session?.pairingCode && !expired)">
          <div class="evo__code" :class="{ 'is-expired': expired }" @click="!expired && session?.pairingCode && copyCode()">
            {{ expired ? '配对码已失效' : prettyCode || '正在获取…' }}
          </div>
        </Spin>
        <Button v-if="expired" type="primary" class="mx-auto mt-3 block" @click="begin('code')">重新获取配对码</Button>
        <ol class="evo__steps">
          <li><span>1</span>在手机上打开 WhatsApp</li>
          <li><span>2</span>点击「设置」（iPhone）或右上角「⋮」（安卓），进入「已关联的设备」</li>
          <li><span>3</span>点击「关联新设备」，再点下方的「改用电话号码关联」</li>
          <li><span>4</span>输入上面的 8 位配对码，确认后自动完成连接</li>
        </ol>
        <a class="evo__switch" @click="switchTo('phone')"><IconifyIcon icon="lucide:chevron-left" class="size-4" /> 换一个号码</a>
      </template>

      <!-- 二维码 -->
      <template v-else>
        <h2 class="evo__title">扫描二维码，连接您的 WhatsApp 账号</h2>
        <p class="evo__sub">二维码会自动刷新，请在手机上完成扫描</p>
        <div class="evo__qr-wrap">
          <Spin :spinning="loading || (!session?.qr && !expired)">
            <div class="evo__qr">
              <img v-if="session?.qr && !expired" :src="session.qr" alt="WhatsApp 二维码" />
              <button v-else-if="expired" type="button" class="evo__qr-expired" @click="begin('qr')">
                <IconifyIcon icon="lucide:refresh-cw" class="size-6" />
                <span>二维码已失效，点击刷新</span>
              </button>
            </div>
          </Spin>
          <ol class="evo__steps evo__steps--side">
            <li><span>1</span>在手机上打开 WhatsApp</li>
            <li><span>2</span>点击「设置」或右上角「⋮」，进入「已关联的设备」</li>
            <li><span>3</span>点击「关联新设备」</li>
            <li><span>4</span>将手机对准左侧二维码扫描</li>
          </ol>
        </div>
        <a class="evo__switch" @click="switchTo('phone')">切换为手机号登录 <IconifyIcon icon="lucide:chevron-right" class="size-4" /></a>
      </template>

      <div class="evo__foot">
        扫码接入与登录电脑版 WhatsApp 相同，不是 Meta 官方接口，频繁群发或自动回复过多可能被 WhatsApp 限制，建议使用常用号码并控制发送频率。
        需要官方接口请使用「WhatsApp Business Official API」。本功能基于开源项目 Evolution API（Apache-2.0）。
      </div>
    </div>
  </Modal>
</template>

<style scoped>
.evo {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-height: 560px;
  padding: 56px 48px 24px;
  background: #fff;
  border-radius: 8px;
}

.evo__title {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  color: #111827;
  text-align: center;
}

.evo__sub {
  margin: 10px 0 30px;
  font-size: 14px;
  color: #4b5563;
  text-align: center;
}

.evo__form {
  width: 336px;
  max-width: 100%;
}

.evo__label {
  margin-bottom: 8px;
  font-size: 14px;
  color: #374151;
}

.evo__btn {
  height: 48px;
  margin-top: 36px;
  font-size: 16px;
  border-radius: 10px;
}

.evo__switch {
  display: flex;
  gap: 4px;
  align-items: center;
  justify-content: center;
  margin-top: 26px;
  font-size: 14px;
  color: #2563EB;
}

.evo__code {
  min-width: 320px;
  padding: 22px 28px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 40px;
  font-weight: 700;
  color: #111827;
  text-align: center;
  letter-spacing: 6px;
  cursor: pointer;
  background: #f5f7fb;
  border-radius: 14px;
}

.evo__code.is-expired {
  font-size: 22px;
  color: #9ca3af;
  letter-spacing: 0;
  cursor: default;
}

.evo__steps {
  width: 460px;
  max-width: 100%;
  padding: 0;
  margin: 28px 0 0;
  list-style: none;
}

.evo__steps li {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 14px;
  font-size: 14px;
  line-height: 24px;
  color: #1f2937;
}

.evo__steps li span {
  display: inline-flex;
  flex: 0 0 24px;
  align-items: center;
  justify-content: center;
  height: 24px;
  font-size: 12px;
  color: #6b7280;
  background: #f3f4f6;
  border-radius: 50%;
}

.evo__qr-wrap {
  display: flex;
  gap: 36px;
  align-items: center;
}

.evo__qr {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 248px;
  height: 248px;
  overflow: hidden;
  background: #fff;
  border: 1px solid #eef0f3;
  border-radius: 16px;
}

.evo__qr img {
  width: 100%;
  height: 100%;
}

.evo__qr-expired {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  font-size: 13px;
  color: #6b7280;
  cursor: pointer;
  background: none;
  border: 0;
}

.evo__steps--side {
  width: 260px;
  margin: 0;
}

.evo__foot {
  max-width: 600px;
  margin-top: auto;
  padding-top: 28px;
  font-size: 12px;
  line-height: 1.7;
  color: #9ca3af;
  text-align: center;
}

@media (max-width: 640px) {
  .evo {
    padding: 36px 16px 20px;
  }

  .evo__title {
    font-size: 20px;
  }

  .evo__qr-wrap {
    flex-direction: column;
    gap: 20px;
  }

  .evo__code {
    min-width: 0;
    font-size: 30px;
  }
}
</style>
