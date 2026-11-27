<script lang="ts" setup>
/**
 * 客户公海自动回收规则（Apple macOS System Settings 卡片风格）：
 * - 移除无意义的「重置」按钮，提供外贸常用天数快捷胶囊（15/30/45 天未跟进、90/180 天未成交）
 * - 实时生成自然语言生效摘要，重点锁定客户与已成交客户天然豁免回收
 */
import type { CrmCustomerPoolConfigApi } from '#/api/crm/customer/poolConfig';

import { computed, onMounted, reactive, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { InputNumber, message, Spin, Switch } from 'ant-design-vue';

import {
  getCustomerPoolConfig,
  saveCustomerPoolConfig,
} from '#/api/crm/customer/poolConfig';

const loading = ref(true);
const loadFailed = ref(false);
const saving = ref(false);

const form = reactive<CrmCustomerPoolConfigApi.CustomerPoolConfig>({
  enabled: false,
  contactExpireDays: undefined,
  dealExpireDays: undefined,
  notifyEnabled: false,
  notifyDays: undefined,
});

const CONTACT_PRESETS = [7, 15, 30, 45];
const DEAL_PRESETS = [60, 90, 180, 365];

function applyValues(res?: CrmCustomerPoolConfigApi.CustomerPoolConfig | null) {
  Object.assign(form, {
    enabled: Boolean(res?.enabled),
    contactExpireDays: res?.contactExpireDays ?? undefined,
    dealExpireDays: res?.dealExpireDays ?? undefined,
    notifyEnabled: Boolean(res?.notifyEnabled),
    notifyDays: res?.notifyDays ?? undefined,
  });
}

const ruleSummary = computed(() => {
  if (!form.enabled) {
    return '当前未开启自动回收，所有客户长期保留在原负责人名下（已成交或点亮「锁定保护」的客户永不掉公海）。';
  }
  const parts: string[] = [];
  if (form.contactExpireDays) {
    parts.push(`连续 ${form.contactExpireDays} 天未写跟进/沟通`);
  }
  if (form.dealExpireDays) {
    parts.push(`累计 ${form.dealExpireDays} 天未产生成交订单`);
  }
  if (!parts.length) {
    return '请填写至少一项回收天数（外贸通常建议仅设「30 天未跟进回收」，大单可留空未成交天数）。';
  }
  const notifyText =
    form.notifyEnabled && form.notifyDays
      ? `，并在回收前 ${form.notifyDays} 天提醒负责人挽回`
      : '';
  return `生效规则：未锁定且未成交客户若${parts.join(' 或 ')}，将自动释放至公海池${notifyText}。`;
});

async function getConfigInfo() {
  loading.value = true;
  loadFailed.value = false;
  try {
    const res = await getCustomerPoolConfig();
    applyValues(res);
  } catch {
    loadFailed.value = true;
  } finally {
    loading.value = false;
  }
}

async function handleSubmit() {
  if (loading.value || loadFailed.value || saving.value) return;
  if (form.enabled && !form.contactExpireDays && !form.dealExpireDays) {
    message.warning('开启自动回收后，请至少填写「未跟进天数」或「未成交天数」');
    return;
  }
  if (form.enabled && form.notifyEnabled && !form.notifyDays) {
    message.warning('请填写提前预警提醒天数');
    return;
  }

  saving.value = true;
  try {
    const data: CrmCustomerPoolConfigApi.CustomerPoolConfig = {
      enabled: Boolean(form.enabled),
      contactExpireDays: form.enabled ? form.contactExpireDays : undefined,
      dealExpireDays: form.enabled ? form.dealExpireDays : undefined,
      notifyEnabled: form.enabled ? Boolean(form.notifyEnabled) : false,
      notifyDays:
        form.enabled && form.notifyEnabled ? form.notifyDays : undefined,
    };
    await saveCustomerPoolConfig(data);
    applyValues(data);
    message.success('公海回收规则已保存');
  } finally {
    saving.value = false;
  }
}

onMounted(() => {
  void getConfigInfo();
});
</script>

<template>
  <div class="pc">
    <div v-if="loadFailed" class="pc__err">
      <div class="flex items-center gap-2">
        <IconifyIcon icon="lucide:circle-alert" class="size-4 shrink-0" />
        <span>公海规则读取失败，请检查网络后重试</span>
      </div>
      <button type="button" class="pc__retry" @click="getConfigInfo">重试</button>
    </div>

    <Spin v-else :spinning="loading || saving">
      <div class="pc__wrap">
        <!-- 主开关行 -->
        <div class="pc__master">
          <div class="flex min-w-0 items-center gap-3">
            <span class="pc__icon">
              <IconifyIcon icon="lucide:globe" class="size-4.5" />
            </span>
            <div class="min-w-0">
              <div class="flex flex-wrap items-center gap-2">
                <span class="text-[14.5px] font-bold text-[#1D1D1F]">
                  公海自动回收机制
                </span>
                <span
                  class="pc__status"
                  :class="form.enabled ? 'is-on' : 'is-off'"
                >
                  {{ form.enabled ? '运行中' : '未开启' }}
                </span>
              </div>
              <div class="mt-0.5 text-[12px] text-[#6E6E73]">
                盘活长期沉睡无人跟进的询盘，点亮「锁定保护」的重点大客户自动豁免
              </div>
            </div>
          </div>
          <Switch v-model:checked="form.enabled" />
        </div>

        <!-- 实时生效语义提示 -->
        <div class="pc__summary" :class="{ 'is-on': form.enabled }">
          <IconifyIcon
            :icon="form.enabled ? 'lucide:sparkles' : 'lucide:shield-check'"
            class="mt-0.5 size-3.5 shrink-0"
          />
          <span>{{ ruleSummary }}</span>
        </div>

        <!-- 触发条件卡片 -->
        <div class="pc__group" :class="{ 'is-dim': !form.enabled }">
          <!-- 条件 1：超期未跟进 -->
          <div class="pc__row">
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2">
                <span class="text-[13.5px] font-semibold text-[#1D1D1F]">
                  超期未跟进回收
                </span>
                <span class="rounded bg-[#E8F2FF] px-1.5 py-0.5 text-[10.5px] font-semibold text-[#0071E3]">
                  推荐开启
                </span>
              </div>
              <div class="mt-0.5 text-[11.5px] text-[#86868B]">
                距离最后一次写跟进或双向消息往来超过设定天数
              </div>
              <div class="mt-2 flex flex-wrap gap-1">
                <button
                  v-for="d in CONTACT_PRESETS"
                  :key="d"
                  type="button"
                  :disabled="!form.enabled"
                  class="pc__preset"
                  :class="{ on: form.contactExpireDays === d }"
                  @click="form.contactExpireDays = d"
                >
                  {{ d }} 天
                </button>
              </div>
            </div>

            <div class="pc__num-box">
              <span>超过</span>
              <InputNumber
                v-model:value="form.contactExpireDays"
                :min="1"
                :max="3650"
                :precision="0"
                :disabled="!form.enabled"
                placeholder="如 30"
                class="pc__input"
              />
              <span>天</span>
            </div>
          </div>

          <div class="pc__divider" />

          <!-- 条件 2：超期未成交（可选） -->
          <div class="pc__row">
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2">
                <span class="text-[13.5px] font-semibold text-[#1D1D1F]">
                  超期未成交回收
                </span>
                <span class="text-[11px] text-[#86868B]">（选填，留空则不限成交周期）</span>
              </div>
              <div class="mt-0.5 text-[11.5px] text-[#86868B]">
                外贸长周期定制单建议设 90~180 天或留空不限
              </div>
              <div class="mt-2 flex flex-wrap gap-1">
                <button
                  v-for="d in DEAL_PRESETS"
                  :key="d"
                  type="button"
                  :disabled="!form.enabled"
                  class="pc__preset"
                  :class="{ on: form.dealExpireDays === d }"
                  @click="form.dealExpireDays = d"
                >
                  {{ d }} 天
                </button>
                <button
                  type="button"
                  :disabled="!form.enabled"
                  class="pc__preset"
                  :class="{ on: !form.dealExpireDays }"
                  @click="form.dealExpireDays = undefined"
                >
                  不限周期
                </button>
              </div>
            </div>

            <div class="pc__num-box">
              <span>超过</span>
              <InputNumber
                v-model:value="form.dealExpireDays"
                :min="1"
                :max="3650"
                :precision="0"
                :disabled="!form.enabled"
                placeholder="不限"
                class="pc__input"
              />
              <span>天</span>
            </div>
          </div>

          <div class="pc__divider" />

          <!-- 回收前预警 -->
          <div class="pc__row">
            <div class="min-w-0 flex-1">
              <div class="text-[13.5px] font-semibold text-[#1D1D1F]">
                掉入公海前预警通知
              </div>
              <div class="mt-0.5 text-[11.5px] text-[#86868B]">
                提前在「快掉公海」胶囊与消息通知提醒负责人及时跟进重置计时
              </div>
            </div>
            <div class="flex items-center gap-2.5">
              <div v-if="form.notifyEnabled" class="pc__num-box">
                <span>提前</span>
                <InputNumber
                  v-model:value="form.notifyDays"
                  :min="1"
                  :max="60"
                  :precision="0"
                  :disabled="!form.enabled"
                  placeholder="3"
                  class="pc__input !w-16"
                />
                <span>天</span>
              </div>
              <Switch
                v-model:checked="form.notifyEnabled"
                size="small"
                :disabled="!form.enabled"
              />
            </div>
          </div>
        </div>

        <!-- 底部单一明确保存动作 -->
        <div class="flex justify-end pt-1">
          <button
            type="button"
            class="pc__save-btn"
            :disabled="saving"
            @click="handleSubmit"
          >
            <IconifyIcon icon="lucide:check" class="size-3.5" />
            <span>保存公海规则</span>
          </button>
        </div>
      </div>
    </Spin>
  </div>
</template>

<style scoped>
.pc {
  width: 100%;
}

.pc__err {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 11px 14px;
  font-size: 12.5px;
  color: #d70015;
  background: #ffeceb;
  border-radius: 12px;
}

.pc__retry {
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 600;
  color: #d70015;
  cursor: pointer;
  background: #fff;
  border: none;
  border-radius: 999px;
}

.pc__wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.pc__master {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: #fff;
  border: 0.5px solid rgb(60 60 67 / 10%);
  border-radius: 14px;
}

.pc__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 38px;
  height: 38px;
  color: #0071e3;
  background: #e8f2ff;
  border-radius: 11px;
}

.pc__status {
  padding: 1px 8px;
  font-size: 11px;
  font-weight: 600;
  border-radius: 999px;
}

.pc__status.is-on {
  color: #1e7e34;
  background: #eaf9ee;
}

.pc__status.is-off {
  color: #6e6e73;
  background: #f2f2f7;
}

.pc__summary {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 10px 13px;
  font-size: 12px;
  line-height: 1.55;
  color: #6e6e73;
  background: #f2f3f7;
  border-radius: 11px;
}

.pc__summary.is-on {
  color: #1d1d1f;
  background: #eef5ff;
  border: 0.5px solid rgb(0 113 227 / 18%);
}

.pc__summary.is-on :deep(svg) {
  color: #0071e3;
}

.pc__group {
  background: #fff;
  border: 0.5px solid rgb(60 60 67 / 10%);
  border-radius: 14px;
  transition: opacity 0.16s ease;
}

.pc__group.is-dim {
  opacity: 0.48;
}

.pc__row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
}

.pc__divider {
  height: 0.5px;
  margin: 0 16px;
  background: rgb(60 60 67 / 10%);
}

.pc__preset {
  padding: 2px 9px;
  font-size: 11px;
  font-weight: 500;
  color: #6e6e73;
  cursor: pointer;
  background: #f5f5f7;
  border: 0.5px solid transparent;
  border-radius: 999px;
  transition: all 0.14s ease;
}

.pc__preset:hover:not(:disabled) {
  color: #0071e3;
  background: #e8f2ff;
}

.pc__preset.on {
  font-weight: 600;
  color: #0071e3;
  background: #e8f2ff;
  border-color: rgb(0 113 227 / 28%);
}

.pc__num-box {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 500;
  color: #3a3a3c;
  background: #f5f5f7;
  border-radius: 10px;
}

.pc__input {
  width: 78px !important;
}

.pc__save-btn {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 34px;
  padding: 0 16px;
  font-size: 12.5px;
  font-weight: 600;
  color: #fff;
  cursor: pointer;
  background: #0071e3;
  border: none;
  border-radius: 10px;
  box-shadow: 0 2px 6px rgb(0 113 227 / 24%);
  transition: all 0.15s ease;
}

.pc__save-btn:hover:not(:disabled) {
  background: #0077ed;
}
</style>
