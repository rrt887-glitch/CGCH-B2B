<script lang="ts" setup>
/**
 * 限频与黑名单（发信信誉保护工作台）：
 * 邮箱 / WhatsApp 小时与日发送上限、自动退订与硬退信拦截、各账号实时发送水位及全局黑名单名录。
 */
import type { ChannelPolicyApi } from '#/api/channel/policy';

import { onMounted, reactive, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import {
  Button,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Progress,
  Select,
  Switch,
  Table,
  Textarea,
} from 'ant-design-vue';

import {
  createSuppression,
  deleteSuppression,
  getSendPolicy,
  getSendUsage,
  getSuppressionPage,
  saveSendPolicy,
} from '#/api/channel/policy';

defineOptions({ name: 'CgchConfigSendPolicy' });

const REASON: Record<string, { label: string; pill: string }> = {
  unsubscribe: { pill: 'ap-crm-pill--amber', label: '客户退订' },
  bounce: { pill: 'ap-crm-pill--red', label: '退信（地址无效）' },
  manual: { pill: 'ap-crm-pill--gray', label: '手动拉黑' },
};

const policy = ref<ChannelPolicyApi.Policy>();
const usage = ref<ChannelPolicyApi.Usage[]>([]);
const saving = ref(false);

async function loadPolicy() {
  [policy.value, usage.value] = await Promise.all([
    getSendPolicy(),
    getSendUsage(),
  ]);
}

async function save() {
  if (!policy.value) return;
  saving.value = true;
  try {
    await saveSendPolicy(policy.value);
    message.success('发送保护策略已保存');
  } finally {
    saving.value = false;
  }
}

function limitOf(u: ChannelPolicyApi.Usage, kind: 'day' | 'hour') {
  const p = policy.value;
  if (!p) return 0;
  return u.channel === 'mail'
    ? kind === 'hour'
      ? p.mailHourlyLimit
      : p.mailDailyLimit
    : kind === 'hour'
      ? p.waHourlyLimit
      : p.waDailyLimit;
}

function pct(u: ChannelPolicyApi.Usage, kind: 'day' | 'hour') {
  const max = limitOf(u, kind);
  return max ? Math.min(100, Math.round((u[kind] / max) * 100)) : 0;
}

// ===== 黑名单名录 =====
const filter = reactive({
  channel: undefined as string | undefined,
  reason: undefined as string | undefined,
  keyword: '',
});
const list = ref<ChannelPolicyApi.Suppression[]>([]);
const total = ref(0);
const pageNo = ref(1);
const loading = ref(false);

async function loadList() {
  loading.value = true;
  try {
    const res = await getSuppressionPage({
      pageNo: pageNo.value,
      pageSize: 20,
      ...filter,
      keyword: filter.keyword || undefined,
    });
    list.value = res.list;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
}

const columns = [
  { title: '渠道', key: 'channel', width: 95 },
  { title: '拦截邮箱 / 域名 / 号码', dataIndex: 'value', key: 'value' },
  { title: '拦截原因', key: 'reason', width: 135 },
  { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
  { title: '加入时间', dataIndex: 'createTime', key: 'time', width: 150 },
  { title: '操作', key: 'action', width: 80, align: 'right' as const },
];

const addOpen = ref(false);
const add = reactive({ channel: 'mail', value: '', remark: '' });

async function doAdd() {
  if (!add.value.trim()) {
    message.warning('请填写邮箱、域名或号码');
    return;
  }
  await createSuppression({ ...add });
  message.success('已加入黑名单，系统将拦截向其发出的所有消息');
  addOpen.value = false;
  Object.assign(add, { value: '', remark: '' });
  await loadList();
}

async function remove(id: number) {
  await deleteSuppression(id);
  message.success('已从黑名单移出');
  await loadList();
}

onMounted(async () => {
  await Promise.all([loadPolicy(), loadList()]);
});
</script>

<template>
  <div class="sp">
    <!-- 卡片 1：发送限频与退订保护 -->
    <div v-if="policy" class="sp__card">
      <div class="sp__head">
        <div class="flex min-w-0 items-center gap-3">
          <span class="sp__icon is-blue">
            <IconifyIcon icon="lucide:shield-check" class="size-5" />
          </span>
          <div>
            <div class="text-[15px] font-bold text-[#1D1D1F]">
              账号发信频率上限与自动退订保护
            </div>
            <div class="mt-0.5 text-xs text-[#6E6E73]">
              防止短时间高频群发或向失效地址反复发信导致企业邮箱与 WhatsApp 账号受限封禁
            </div>
          </div>
        </div>
        <Button type="primary" shape="round" :loading="saving" @click="save">
          保存策略
        </Button>
      </div>

      <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <!-- 左：频率上限 -->
        <div class="sp__block">
          <div class="sp__block-title">单账号发送频率上限（0 表示不限）</div>

          <div class="sp__quota-row">
            <div class="flex items-center gap-2">
              <span class="ap-crm-pill ap-crm-pill--blue">邮件账号</span>
            </div>
            <div class="flex flex-wrap items-center gap-3 text-xs text-[#3A3A3C]">
              <label class="flex items-center gap-1.5">
                <span>每小时</span>
                <InputNumber
                  v-model:value="policy.mailHourlyLimit"
                  :min="0"
                  :max="100000"
                  size="small"
                  class="w-24"
                />
              </label>
              <label class="flex items-center gap-1.5">
                <span>每天</span>
                <InputNumber
                  v-model:value="policy.mailDailyLimit"
                  :min="0"
                  :max="100000"
                  size="small"
                  class="w-24"
                />
              </label>
            </div>
          </div>

          <div class="sp__quota-row mt-2.5">
            <div class="flex items-center gap-2">
              <span class="ap-crm-pill ap-crm-pill--green">WhatsApp</span>
            </div>
            <div class="flex flex-wrap items-center gap-3 text-xs text-[#3A3A3C]">
              <label class="flex items-center gap-1.5">
                <span>每小时</span>
                <InputNumber
                  v-model:value="policy.waHourlyLimit"
                  :min="0"
                  :max="100000"
                  size="small"
                  class="w-24"
                />
              </label>
              <label class="flex items-center gap-1.5">
                <span>每天</span>
                <InputNumber
                  v-model:value="policy.waDailyLimit"
                  :min="0"
                  :max="100000"
                  size="small"
                  class="w-24"
                />
              </label>
            </div>
          </div>

          <div class="mt-3.5 flex items-center justify-between gap-3 rounded-xl bg-white p-3">
            <div>
              <div class="text-[13px] font-semibold text-[#1D1D1F]">
                同时限制业务员手动发信
              </div>
              <div class="text-[11.5px] text-[#86868B]">
                默认仅限制 AI 自动回复与批量唤醒（超限自动留为草稿）
              </div>
            </div>
            <Switch v-model:checked="policy.applyToManual" size="small" />
          </div>
        </div>

        <!-- 右：退订与退信 -->
        <div class="sp__block">
          <div class="sp__block-title">退订与硬退信自动化</div>

          <div class="space-y-2">
            <div class="flex items-center justify-between gap-3 rounded-xl bg-white p-3">
              <div>
                <div class="text-[13px] font-semibold text-[#1D1D1F]">
                  自动识别客户退订指令
                </div>
                <div class="text-[11.5px] text-[#86868B]">
                  客户回复 unsubscribe / STOP / 退订时自动拉入名单不再打扰
                </div>
              </div>
              <Switch v-model:checked="policy.autoUnsubscribe" size="small" />
            </div>

            <div class="flex items-center justify-between gap-3 rounded-xl bg-white p-3">
              <div>
                <div class="text-[13px] font-semibold text-[#1D1D1F]">
                  自动拉黑硬退信无效邮箱
                </div>
                <div class="text-[11.5px] text-[#86868B]">
                  收到「地址不存在」退信立即封锁该地址，保护发信域名信誉
                </div>
              </div>
              <Switch v-model:checked="policy.autoBounce" size="small" />
            </div>

            <div class="rounded-xl bg-white p-3">
              <div class="flex items-center justify-between gap-3">
                <div>
                  <div class="text-[13px] font-semibold text-[#1D1D1F]">
                    唤醒等批量邮件附带合规退订声明
                  </div>
                  <div class="text-[11.5px] text-[#86868B]">
                    在批量邮件底部追加退订指引，符合欧美商业邮件法规
                  </div>
                </div>
                <Switch
                  v-model:checked="policy.unsubscribeFooter"
                  size="small"
                />
              </div>
              <Textarea
                v-if="policy.unsubscribeFooter"
                v-model:value="policy.footerText"
                class="mt-2.5"
                :rows="2"
                :maxlength="500"
              />
            </div>
          </div>
        </div>
      </div>

      <!-- 实时发送量监控 -->
      <template v-if="usage.length">
        <div class="mt-5 mb-2.5 text-[13.5px] font-bold text-[#1D1D1F]">
          各渠道账号实时发送水位
        </div>
        <div class="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
          <div
            v-for="u in usage"
            :key="u.channel + u.accountId"
            class="sp__usage-card"
          >
            <div class="mb-2 flex items-center gap-2">
              <span
                class="ap-crm-pill"
                :class="
                  u.channel === 'mail'
                    ? 'ap-crm-pill--blue'
                    : 'ap-crm-pill--green'
                "
              >
                {{ u.channel === 'mail' ? '邮件' : 'WhatsApp' }}
              </span>
              <span class="truncate text-[13px] font-semibold text-[#1D1D1F]">
                {{ u.name }}
              </span>
            </div>
            <div class="flex justify-between text-[11.5px] text-[#6E6E73]">
              <span>近 1 小时</span>
              <span class="tabular-nums">
                {{ u.hour }}{{ limitOf(u, 'hour') ? ` / ${limitOf(u, 'hour')}` : '' }}
              </span>
            </div>
            <Progress
              :percent="pct(u, 'hour')"
              :show-info="false"
              size="small"
              :status="pct(u, 'hour') >= 90 ? 'exception' : 'normal'"
            />
            <div class="mt-1 flex justify-between text-[11.5px] text-[#6E6E73]">
              <span>近 24 小时</span>
              <span class="tabular-nums">
                {{ u.day }}{{ limitOf(u, 'day') ? ` / ${limitOf(u, 'day')}` : '' }}
              </span>
            </div>
            <Progress
              :percent="pct(u, 'day')"
              :show-info="false"
              size="small"
              :status="pct(u, 'day') >= 90 ? 'exception' : 'normal'"
            />
          </div>
        </div>
      </template>
    </div>

    <!-- 卡片 2：退订与黑名单名录 -->
    <div class="sp__card">
      <div class="sp__head">
        <div class="flex min-w-0 items-center gap-3">
          <span class="sp__icon is-red">
            <IconifyIcon icon="lucide:ban" class="size-5" />
          </span>
          <div>
            <div class="text-[15px] font-bold text-[#1D1D1F]">
              退订与全局黑名单名录（{{ total }}）
            </div>
            <div class="mt-0.5 text-xs text-[#6E6E73]">
              名单内的邮箱、整站域名或 WhatsApp 号码将被全面拒发（含人工手动发送）
            </div>
          </div>
        </div>
        <button type="button" class="sp__add-btn" @click="addOpen = true">
          <IconifyIcon icon="lucide:plus" class="size-3.5" />
          <span>加入黑名单</span>
        </button>
      </div>

      <div class="mb-3 flex flex-wrap items-center gap-2">
        <Select
          v-model:value="filter.channel"
          allow-clear
          placeholder="全部渠道"
          class="w-32"
          :options="[
            { value: 'mail', label: '邮件' },
            { value: 'whatsapp', label: 'WhatsApp' },
          ]"
          @change="
            () => {
              pageNo = 1;
              loadList();
            }
          "
        />
        <Select
          v-model:value="filter.reason"
          allow-clear
          placeholder="全部原因"
          class="w-40"
          :options="
            Object.entries(REASON).map(([v, r]) => ({
              value: v,
              label: r.label,
            }))
          "
          @change="
            () => {
              pageNo = 1;
              loadList();
            }
          "
        />
        <Input
          v-model:value="filter.keyword"
          allow-clear
          placeholder="搜索邮箱、域名或号码…"
          class="w-56"
          @press-enter="
            () => {
              pageNo = 1;
              loadList();
            }
          "
        />
        <Button
          type="primary"
          @click="
            () => {
              pageNo = 1;
              loadList();
            }
          "
        >
          筛选
        </Button>
      </div>

      <Table
        :columns="columns"
        :data-source="list"
        :loading="loading"
        row-key="id"
        size="small"
        :pagination="{
          current: pageNo,
          pageSize: 20,
          total,
          onChange: (p: number) => {
            pageNo = p;
            loadList();
          },
        }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'channel'">
            <span
              class="ap-crm-pill"
              :class="
                record.channel === 'mail'
                  ? 'ap-crm-pill--blue'
                  : 'ap-crm-pill--green'
              "
            >
              {{ record.channel === 'mail' ? '邮件' : 'WhatsApp' }}
            </span>
          </template>
          <template v-else-if="column.key === 'value'">
            <span class="font-medium text-[#1D1D1F]">
              {{
                record.channel === 'whatsapp'
                  ? `+${record.value}`
                  : record.value
              }}
            </span>
          </template>
          <template v-else-if="column.key === 'reason'">
            <span
              class="ap-crm-pill"
              :class="REASON[record.reason]?.pill ?? 'ap-crm-pill--gray'"
            >
              {{ REASON[record.reason]?.label ?? record.reason }}
            </span>
          </template>
          <template v-else-if="column.key === 'action'">
            <Popconfirm
              title="移出黑名单后将恢复向其发信，确认移出？"
              ok-text="移出"
              cancel-text="取消"
              @confirm="remove(record.id)"
            >
              <a class="ap-crm-link">移出</a>
            </Popconfirm>
          </template>
        </template>
        <template #emptyText>黑名单为空</template>
      </Table>
    </div>

    <!-- 加入黑名单弹窗（Apple Sheet） -->
    <Modal
      v-model:open="addOpen"
      title="加入全局发信黑名单"
      :width="500"
      ok-text="确认拦截"
      cancel-text="取消"
      wrap-class-name="ap-modal-sheet"
      @ok="doAdd"
    >
      <div class="ap-sheet-lead">
        <span class="ap-sheet-icon" style="color: #d70015; background: #ffeceb">
          <IconifyIcon icon="lucide:ban" class="size-5" />
        </span>
        <div class="ap-sheet-subtitle">
          加入后 AI 与业务员手动发送均会被系统自动拦截，支持按单个邮箱或整个域名（如 competitor.com）封锁。
        </div>
      </div>

      <div class="ap-sheet-body">
        <div class="ap-sheet-field">
          <div class="ap-sheet-label"><span>拦截渠道</span></div>
          <div class="grid grid-cols-2 gap-2">
            <button
              type="button"
              class="sp__ch-btn"
              :class="{ on: add.channel === 'mail' }"
              @click="add.channel = 'mail'"
            >
              邮件 / 域名
            </button>
            <button
              type="button"
              class="sp__ch-btn"
              :class="{ on: add.channel === 'whatsapp' }"
              @click="add.channel = 'whatsapp'"
            >
              WhatsApp 号码
            </button>
          </div>
        </div>

        <div class="ap-sheet-field">
          <div class="ap-sheet-label">
            <span>
              {{
                add.channel === 'mail'
                  ? '邮箱地址或整站域名 *'
                  : 'WhatsApp 号码（含国家区号） *'
              }}
            </span>
          </div>
          <Input
            v-model:value="add.value"
            :placeholder="
              add.channel === 'mail'
                ? 'someone@example.com 或 example.com'
                : '+44 7900 000123'
            "
          />
        </div>

        <div class="ap-sheet-field">
          <div class="ap-sheet-label">
            <span>备注说明</span>
            <span class="ap-sheet-optional">选填</span>
          </div>
          <Input
            v-model:value="add.remark"
            :maxlength="200"
            placeholder="例如：同行竞品域名 / 明确要求不再联系"
          />
        </div>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.sp {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.sp__card {
  padding: 20px 22px;
  background: #fff;
  border-radius: 18px;
  box-shadow: var(--ap-shadow-rest);
}

.sp__head {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 16px;
  margin-bottom: 16px;
  border-bottom: 0.5px solid var(--ap-separator);
}

.sp__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 12px;
}

.sp__icon.is-blue {
  color: #007aff;
  background: #e8f2ff;
}

.sp__icon.is-red {
  color: #d70015;
  background: #ffeceb;
}

.sp__block {
  padding: 15px 16px;
  background: #f7f8fb;
  border-radius: 14px;
}

.sp__block-title {
  margin-bottom: 10px;
  font-size: 13.5px;
  font-weight: 700;
  color: #1d1d1f;
}

.sp__quota-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: #fff;
  border-radius: 11px;
}

.sp__usage-card {
  padding: 12px 14px;
  background: #f7f8fb;
  border-radius: 13px;
}

.sp__add-btn {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 32px;
  padding: 0 14px;
  font-size: 12.5px;
  font-weight: 600;
  color: #fff;
  cursor: pointer;
  background: linear-gradient(180deg, #1a88ff 0%, #007aff 100%);
  border: 0;
  border-radius: 999px;
  box-shadow: 0 2px 6px rgb(0 122 255 / 22%);
}

.sp__ch-btn {
  height: 36px;
  font-size: 13px;
  font-weight: 600;
  color: #3a3a3c;
  cursor: pointer;
  background: #f4f5f8;
  border: 1.5px solid transparent;
  border-radius: 10px;
}

.sp__ch-btn.on {
  color: #007aff;
  background: #e8f2ff;
  border-color: rgb(0 122 255 / 40%);
}
</style>
