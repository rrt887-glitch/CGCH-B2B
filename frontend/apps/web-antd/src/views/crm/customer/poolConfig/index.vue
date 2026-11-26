<script lang="ts" setup>
/**
 * 客户公海回收规则（macOS 系统设置分组卡片风格）：
 * 消除旧版无标签残缺拼接框与重复「或」字，直观配置未跟进回收、未成交回收与提前预警。
 */
import type { CrmCustomerPoolConfigApi } from '#/api/crm/customer/poolConfig';

import { computed, onMounted, reactive, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Button, InputNumber, message, Spin, Switch } from 'ant-design-vue';

import {
  getCustomerPoolConfig,
  saveCustomerPoolConfig,
} from '#/api/crm/customer/poolConfig';
import { $t } from '#/locales';

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

const savedSnapshot = ref<CrmCustomerPoolConfigApi.CustomerPoolConfig>({
  enabled: false,
  notifyEnabled: false,
});

function applyValues(res?: CrmCustomerPoolConfigApi.CustomerPoolConfig | null) {
  const next: CrmCustomerPoolConfigApi.CustomerPoolConfig = {
    enabled: Boolean(res?.enabled),
    contactExpireDays: res?.contactExpireDays ?? undefined,
    dealExpireDays: res?.dealExpireDays ?? undefined,
    notifyEnabled: Boolean(res?.notifyEnabled),
    notifyDays: res?.notifyDays ?? undefined,
  };
  Object.assign(form, next);
  savedSnapshot.value = { ...next };
}

function handleReset() {
  Object.assign(form, { ...savedSnapshot.value });
}

const ruleSummary = computed(() => {
  if (!form.enabled) return '当前未开启自动回收，所有私海客户长期保留在负责人名下。';
  const parts: string[] = [];
  if (form.contactExpireDays) {
    parts.push(`超过 ${form.contactExpireDays} 天未跟进`);
  }
  if (form.dealExpireDays) {
    parts.push(`超过 ${form.dealExpireDays} 天未成交`);
  }
  if (!parts.length) {
    return '请至少填写一项回收天数上限（未跟进天数或未成交天数）。';
  }
  const notifyText =
    form.notifyEnabled && form.notifyDays
      ? `，并在回收前 ${form.notifyDays} 天提醒负责人`
      : '';
  return `生效逻辑：当私海客户${parts.join('，或')}时，系统自动将其退回客户公海${notifyText}。`;
});

/** 获取配置 */
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

/** 提交表单 */
async function handleSubmit() {
  if (loading.value || loadFailed.value || saving.value) return;
  if (
    form.enabled &&
    !form.contactExpireDays &&
    !form.dealExpireDays
  ) {
    message.warning('开启公海规则后，请至少填写「未跟进天数」或「未成交天数」');
    return;
  }
  if (form.enabled && form.notifyEnabled && !form.notifyDays) {
    message.warning('开启提前提醒后，请填写提前提醒天数');
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
    message.success($t('ui.actionMessage.operationSuccess'));
  } catch {
    // 请求层已提示失败，保留表单供重试
  } finally {
    saving.value = false;
  }
}

onMounted(() => {
  getConfigInfo();
});
</script>

<template>
  <div class="pc">
    <div v-if="loadFailed" class="pc__err">
      <div class="flex items-center gap-2">
        <IconifyIcon icon="lucide:circle-alert" class="size-4 shrink-0" />
        <span>客户公海规则加载失败，请检查网络后重试</span>
      </div>
      <Button size="small" shape="round" @click="getConfigInfo">重新加载</Button>
    </div>

    <Spin v-else :spinning="loading || saving">
      <div class="pc__wrap">
        <!-- 主开关头卡 -->
        <div class="pc__master">
          <div class="flex min-w-0 items-center gap-3.5">
            <span class="pc__icon is-blue">
              <IconifyIcon icon="lucide:globe" class="size-5" />
            </span>
            <div class="min-w-0">
              <div class="flex flex-wrap items-center gap-2">
                <span class="text-[15.5px] font-bold text-[#1D1D1F]">
                  客户公海自动回收规则
                </span>
                <span
                  class="ap-crm-pill"
                  :class="
                    form.enabled ? 'ap-crm-pill--green' : 'ap-crm-pill--gray'
                  "
                >
                  {{ form.enabled ? '已开启' : '已关闭' }}
                </span>
              </div>
              <div class="mt-0.5 text-xs leading-5 text-[#6E6E73]">
                开启后，超期未跟进或迟迟未成交的未锁定客户将自动释放回公海池，供其他业务员重新认领激活
              </div>
            </div>
          </div>
          <Switch v-model:checked="form.enabled" />
        </div>

        <!-- 生效逻辑实时预览条 -->
        <div class="pc__summary" :class="{ 'is-on': form.enabled }">
          <IconifyIcon
            :icon="form.enabled ? 'lucide:sparkles' : 'lucide:info'"
            class="mt-0.5 size-4 shrink-0"
          />
          <span>{{ ruleSummary }}</span>
        </div>

        <!-- 规则详细设置（关闭时半透明展示，开启后可编辑） -->
        <div
          class="pc__body"
          :class="{ 'is-disabled': !form.enabled }"
        >
          <div class="pc__section-title">
            <span>回收触发条件</span>
            <small>满足以下任意一项条件即触发退回公海</small>
          </div>

          <div class="pc__cond-stack">
            <!-- 条件 1：未跟进 -->
            <div class="pc__cond-card">
              <div class="flex min-w-0 items-center gap-3">
                <span class="pc__sub-icon is-blue">
                  <IconifyIcon icon="lucide:clock" class="size-4" />
                </span>
                <div class="min-w-0">
                  <div class="text-[14px] font-semibold text-[#1D1D1F]">
                    超期未跟进自动回收
                  </div>
                  <div class="mt-0.5 text-xs text-[#6E6E73]">
                    距离最近一次写跟进记录或双向沟通超过指定天数
                  </div>
                </div>
              </div>

              <div class="pc__num-slot">
                <span>连续超过</span>
                <InputNumber
                  v-model:value="form.contactExpireDays"
                  :min="1"
                  :max="3650"
                  :precision="0"
                  :disabled="!form.enabled"
                  placeholder="例如 15"
                  class="pc__num-input"
                />
                <span>天未跟进</span>
              </div>
            </div>

            <!-- 中间「或」连接胶囊 -->
            <div class="pc__or-divider">
              <span>或（满足任一即回收）</span>
            </div>

            <!-- 条件 2：未成交 -->
            <div class="pc__cond-card">
              <div class="flex min-w-0 items-center gap-3">
                <span class="pc__sub-icon is-purple">
                  <IconifyIcon icon="lucide:briefcase" class="size-4" />
                </span>
                <div class="min-w-0">
                  <div class="text-[14px] font-semibold text-[#1D1D1F]">
                    超期未成交自动回收
                  </div>
                  <div class="mt-0.5 text-xs text-[#6E6E73]">
                    自建档或领取之日起超过指定天数仍未产生有效订单
                  </div>
                </div>
              </div>

              <div class="pc__num-slot">
                <span>累计超过</span>
                <InputNumber
                  v-model:value="form.dealExpireDays"
                  :min="1"
                  :max="3650"
                  :precision="0"
                  :disabled="!form.enabled"
                  placeholder="例如 60"
                  class="pc__num-input"
                />
                <span>天未成交</span>
              </div>
            </div>
          </div>

          <!-- 提前提醒设置 -->
          <div class="pc__section-title mt-5">
            <span>回收前预警通知</span>
            <small>给业务员留出最后跟进挽回窗口</small>
          </div>

          <div class="pc__notify-box">
            <div class="flex flex-wrap items-center justify-between gap-3">
              <div class="flex min-w-0 items-center gap-3">
                <span class="pc__sub-icon is-amber">
                  <IconifyIcon icon="lucide:bell" class="size-4" />
                </span>
                <div>
                  <div class="text-[14px] font-semibold text-[#1D1D1F]">
                    掉入公海前提前发送提醒
                  </div>
                  <div class="mt-0.5 text-xs text-[#6E6E73]">
                    到期前自动向客户负责人推送提醒通知，及时跟进即可重置回收计时
                  </div>
                </div>
              </div>
              <Switch
                v-model:checked="form.notifyEnabled"
                :disabled="!form.enabled"
              />
            </div>

            <div
              v-if="form.notifyEnabled"
              class="pc__notify-sub"
            >
              <span>在客户掉入公海前提前</span>
              <InputNumber
                v-model:value="form.notifyDays"
                :min="1"
                :max="365"
                :precision="0"
                :disabled="!form.enabled"
                placeholder="例如 3"
                class="pc__num-input"
              />
              <span>天通知负责人</span>
            </div>
          </div>
        </div>

        <!-- 底部操作栏 -->
        <div class="pc__foot">
          <Button shape="round" @click="handleReset">重置</Button>
          <Button
            type="primary"
            shape="round"
            :loading="saving"
            @click="handleSubmit"
          >
            保存公海规则
          </Button>
        </div>
      </div>
    </Spin>
  </div>
</template>

<style scoped>
.pc {
  max-width: 780px;
  padding: 4px 0 12px;
}

.pc__err {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  font-size: 13px;
  color: #d70015;
  background: #ffeceb;
  border-radius: 14px;
}

.pc__wrap {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.pc__master {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px;
  background: #f7f8fb;
  border-radius: 16px;
}

.pc__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 13px;
}

.pc__icon.is-blue {
  color: #007aff;
  background: #e8f2ff;
}

.pc__summary {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 11px 15px;
  font-size: 12.5px;
  line-height: 1.55;
  color: #6e6e73;
  background: #f5f6fa;
  border-radius: 13px;
}

.pc__summary.is-on {
  color: #1d1d1f;
  background: linear-gradient(135deg, #f5f3ff 0%, #eff6ff 100%);
  box-shadow: inset 0 0 0 0.5px rgb(88 86 214 / 15%);
}

.pc__summary.is-on :deep(svg) {
  color: #5856d6;
}

.pc__body {
  padding: 18px 20px;
  background: #f7f8fb;
  border-radius: 16px;
  transition: opacity var(--ap-dur-fast);
}

.pc__body.is-disabled {
  opacity: 0.52;
}

.pc__section-title {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 13.5px;
  font-weight: 700;
  color: #1d1d1f;
}

.pc__section-title small {
  font-size: 11.5px;
  font-weight: 400;
  color: #86868b;
}

.pc__cond-stack {
  display: flex;
  flex-direction: column;
}

.pc__cond-card {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: #fff;
  border-radius: 13px;
  box-shadow: 0 1px 3px rgb(0 0 0 / 4%);
}

.pc__or-divider {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 6px 0;
}

.pc__or-divider span {
  padding: 2px 10px;
  font-size: 11px;
  font-weight: 600;
  color: #86868b;
  background: #ebedf3;
  border-radius: 999px;
}

.pc__sub-icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
}

.pc__sub-icon.is-blue {
  color: #007aff;
  background: #e8f2ff;
}

.pc__sub-icon.is-purple {
  color: #7c3aed;
  background: #f3eeff;
}

.pc__sub-icon.is-amber {
  color: #d97706;
  background: #fff4e0;
}

.pc__num-slot {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  padding: 5px 12px;
  font-size: 13px;
  font-weight: 500;
  color: #3a3a3c;
  background: #f5f6fa;
  border-radius: 11px;
}

.pc__num-input {
  width: 96px !important;
}

.pc__notify-box {
  padding: 14px 16px;
  background: #fff;
  border-radius: 13px;
  box-shadow: 0 1px 3px rgb(0 0 0 / 4%);
}

.pc__notify-sub {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding: 10px 14px;
  margin-top: 12px;
  margin-left: 48px;
  font-size: 13px;
  font-weight: 500;
  color: #3a3a3c;
  background: #f7f8fb;
  border-radius: 11px;
}

.pc__foot {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  padding-top: 4px;
}

@media (width <= 680px) {
  .pc__notify-sub {
    margin-left: 0;
  }
}
</style>
