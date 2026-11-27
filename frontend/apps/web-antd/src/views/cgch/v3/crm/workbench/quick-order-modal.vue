<script lang="ts" setup>
/**
 * 🧾 极简外贸成交订单 / PI 登记与编辑弹窗（Apple Sheet）：
 * - 同时支持「新建登记」与「修改已有订单」（金额、币种、签单日、付款条款、交期备注）
 * - 新建时自动从客户「4 格商务黑板」带出预估单值、币种与意向产品，并可同步点亮客户「⑤ 已成交」
 */
import { computed, reactive, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';
import { useUserStore } from '@vben/stores';

import {
  Checkbox,
  DatePicker,
  Input,
  InputNumber,
  message,
  Modal,
  Select,
} from 'ant-design-vue';
import dayjs from 'dayjs';

import { createContract, updateContract } from '#/api/crm/contract';
import {
  getCustomerSimpleList,
  updateCustomerDealStatus,
  updateCustomerStage,
} from '#/api/crm/customer';

import type { CurrencyCode } from './deal-meta';
import { CURRENCIES, parseDealBoard } from './deal-meta';

const props = defineProps<{
  customer?: null | Record<string, any>;
  open: boolean;
  order?: null | Record<string, any>;
}>();

const emit = defineEmits<{
  success: [customerId: number];
  'update:open': [val: boolean];
}>();

const userStore = useUserStore();
const saving = ref(false);
const customerList = ref<{ id: number; name: string }[]>([]);

const isEditing = computed(() => Boolean(props.order?.id));

const form = reactive({
  customerId: undefined as number | undefined,
  name: '',
  currency: 'USD' as CurrencyCode,
  amount: undefined as number | undefined,
  orderDate: dayjs().format('YYYY-MM-DD'),
  paymentTerm: '30% T/T 定金，70% 出货前付清',
  note: '',
  syncStageWon: true,
});

const PAYMENT_CHIPS = [
  '30% T/T 定金，70% 出货前付清',
  '30% T/T + 70% 见提单副本',
  '100% T/T 款到发货',
  'PayPal / 样品试单全款',
  'L/C 即期信用证',
];

const customerOptions = computed(() =>
  customerList.value.map((c) => ({ label: c.name, value: c.id })),
);

async function ensureCustomers() {
  if (customerList.value.length) return;
  customerList.value = ((await getCustomerSimpleList().catch(() => [])) as any[]) || [];
}

function parseOrderRemark(raw?: string) {
  if (!raw) return { currency: 'USD' as CurrencyCode, paymentTerm: '', note: '' };
  const curMatch = raw.match(/\[币种:([A-Z]{3})\]/);
  const currency = (curMatch?.[1] as CurrencyCode) || 'USD';
  const rest = raw.replace(/\[币种:[A-Z]{3}\]\s*\|?\s*/, '').trim();
  const segs = rest.split('|').map((s) => s.trim()).filter(Boolean);
  let paymentTerm = '';
  const notes: string[] = [];
  for (const s of segs) {
    if (s.startsWith('付款条款：')) {
      paymentTerm = s.replace(/^付款条款：/, '').trim();
    } else {
      notes.push(s);
    }
  }
  return { currency, paymentTerm, note: notes.join(' | ') };
}

function initForm() {
  if (props.order?.id) {
    const parsed = parseOrderRemark(props.order.remark);
    form.customerId = props.order.customerId;
    form.name = props.order.name || props.order.no || '';
    form.currency = parsed.currency;
    form.amount = props.order.totalPrice !== undefined ? Number(props.order.totalPrice) : undefined;
    form.orderDate = props.order.orderDate
      ? dayjs(props.order.orderDate).format('YYYY-MM-DD')
      : dayjs().format('YYYY-MM-DD');
    form.paymentTerm = parsed.paymentTerm;
    form.note = parsed.note;
    form.syncStageWon = false;
    return;
  }

  const todayTag = dayjs().format('YYYYMMDD');
  const cust = props.customer;
  if (cust?.id) {
    const board = parseDealBoard(cust.remark);
    form.customerId = cust.id;
    form.currency = board.currency || 'USD';
    form.amount = board.amount;
    const prodHint = board.productQty
      ? board.productQty.slice(0, 28)
      : cust.companyName || cust.name;
    form.name = `PI-${todayTag} · ${prodHint}`;
    form.note = board.quoteTerms ? `报价条款：${board.quoteTerms}` : '';
  } else {
    form.customerId = undefined;
    form.currency = 'USD';
    form.amount = undefined;
    form.name = `PI-${todayTag} · 外贸订单`;
    form.note = '';
  }
  form.paymentTerm = '30% T/T 定金，70% 出货前付清';
  form.orderDate = dayjs().format('YYYY-MM-DD');
  form.syncStageWon = true;
}

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      initForm();
      if (!props.customer?.id) {
        void ensureCustomers();
      }
    }
  },
);

async function handleSubmit() {
  if (!form.customerId) {
    message.warning('请选择关联客户');
    return;
  }
  if (!form.name.trim()) {
    message.warning('请填写订单名称或 PI 编号');
    return;
  }
  if (form.amount === undefined || form.amount === null || Number(form.amount) < 0) {
    message.warning('请填写订单金额');
    return;
  }

  saving.value = true;
  try {
    const ownerUserId =
      props.order?.ownerUserId ||
      props.customer?.ownerUserId ||
      userStore.userInfo?.id ||
      1;
    const orderDateMs = dayjs(form.orderDate || undefined).isValid()
      ? dayjs(form.orderDate).valueOf()
      : Date.now();

    const remarkParts = [`[币种:${form.currency}]`];
    if (form.paymentTerm.trim()) remarkParts.push(`付款条款：${form.paymentTerm.trim()}`);
    if (form.note.trim()) remarkParts.push(form.note.trim());

    const payload: any = {
      id: props.order?.id,
      name: form.name.trim(),
      customerId: form.customerId,
      ownerUserId,
      signUserId: props.order?.signUserId || userStore.userInfo?.id || ownerUserId,
      orderDate: orderDateMs,
      discountPercent: props.order?.discountPercent ?? 0,
      totalProductPrice: Number(form.amount),
      totalPrice: Number(form.amount),
      remark: remarkParts.join(' | '),
      products: props.order?.products ?? [],
    };

    if (isEditing.value) {
      await updateContract(payload);
      message.success('订单信息已更新');
    } else {
      await createContract(payload);
      if (form.syncStageWon) {
        await Promise.allSettled([
          updateCustomerStage(form.customerId, 5),
          updateCustomerDealStatus(form.customerId, true),
        ]);
      }
      message.success('成交订单已登记');
    }

    emit('update:open', false);
    emit('success', form.customerId);
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <Modal
    :open="open"
    :title="isEditing ? '修改成交订单 / PI' : '登记成交订单 / PI'"
    :width="520"
    centered
    :confirm-loading="saving"
    :ok-text="isEditing ? '保存修改' : '确认登记'"
    cancel-text="取消"
    wrap-class-name="ap-modal-sheet"
    @cancel="emit('update:open', false)"
    @ok="handleSubmit"
  >
    <div class="qo-body">
      <div v-if="customer?.id && !isEditing" class="qo-cust-banner">
        <IconifyIcon icon="lucide:building-2" class="size-4 text-[#0071E3]" />
        <div class="min-w-0 flex-1">
          <div class="truncate text-[13px] font-semibold text-[#1D1D1F]">
            {{ customer.name }}
            <span v-if="customer.companyName" class="font-normal text-[#6E6E73]">
              · {{ customer.companyName }}
            </span>
          </div>
          <div class="text-[11px] text-[#86868B]">
            已自动带出商务黑板中的预估单值与条款，核对后即可保存
          </div>
        </div>
      </div>

      <div v-else class="qo-field">
        <label class="qo-label">关联客户 <span>*</span></label>
        <Select
          v-model:value="form.customerId"
          show-search
          :disabled="isEditing"
          option-filter-prop="label"
          placeholder="搜索并选择成交客户"
          :options="customerOptions"
          class="w-full"
        />
      </div>

      <div class="qo-field">
        <label class="qo-label">PI 编号 / 订单摘要 <span>*</span></label>
        <Input
          v-model:value="form.name"
          placeholder="例如：PI-20261127 · 高速吹风机 2000 pcs 大货单"
        />
      </div>

      <div class="grid grid-cols-12 gap-3">
        <div class="qo-field col-span-4">
          <label class="qo-label">结算币种</label>
          <Select
            v-model:value="form.currency"
            :options="CURRENCIES.map((c) => ({ label: c.label, value: c.code }))"
            class="w-full"
          />
        </div>
        <div class="qo-field col-span-4">
          <label class="qo-label">订单总金额 <span>*</span></label>
          <InputNumber
            v-model:value="form.amount"
            :min="0"
            :precision="2"
            placeholder="例如 18500"
            class="w-full"
          />
        </div>
        <div class="qo-field col-span-4">
          <label class="qo-label">签单日期 <span>*</span></label>
          <DatePicker
            v-model:value="form.orderDate"
            value-format="YYYY-MM-DD"
            format="YYYY-MM-DD"
            :allow-clear="false"
            class="w-full"
          />
        </div>
      </div>

      <div class="qo-field">
        <label class="qo-label">付款方式与贸易条款</label>
        <Input
          v-model:value="form.paymentTerm"
          placeholder="例如：30% T/T 定金，70% 出货前付清"
        />
        <div class="mt-1.5 flex flex-wrap gap-1.5">
          <button
            v-for="chip in PAYMENT_CHIPS"
            :key="chip"
            type="button"
            class="qo-chip"
            :class="{ on: form.paymentTerm === chip }"
            @click="form.paymentTerm = chip"
          >
            {{ chip }}
          </button>
        </div>
      </div>

      <div class="qo-field">
        <label class="qo-label">尾款水单 / 交期进度备注</label>
        <Input.TextArea
          v-model:value="form.note"
          :rows="2"
          placeholder="选填：定金水单已收、预计排产交货期或船期提单备注…"
        />
      </div>

      <div v-if="!isEditing" class="pt-1">
        <Checkbox v-model:checked="form.syncStageWon">
          <span class="text-[12.5px] text-[#3A3A3C]">
            同步将客户推进至「⑤ 已成交」阶段并点亮成交状态
          </span>
        </Checkbox>
      </div>
    </div>
  </Modal>
</template>

<style scoped>
.qo-body {
  display: flex;
  flex-direction: column;
  gap: 13px;
  padding-top: 6px;
}

.qo-cust-banner {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  background: #f0f7ff;
  border: 0.5px solid rgb(0 113 227 / 18%);
  border-radius: 11px;
}

.qo-field {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.qo-label {
  font-size: 12px;
  font-weight: 600;
  color: #3a3a3c;
}

.qo-label span {
  color: #ff3b30;
}

.qo-chip {
  padding: 3px 9px;
  font-size: 11px;
  color: #48484a;
  cursor: pointer;
  background: #f2f2f7;
  border: 0.5px solid transparent;
  border-radius: 999px;
  transition: all 0.15s ease;
}

.qo-chip:hover {
  color: #0071e3;
  background: #e8f2ff;
}

.qo-chip.on {
  font-weight: 600;
  color: #0071e3;
  background: #e8f2ff;
  border-color: rgb(0 113 227 / 30%);
}
</style>
