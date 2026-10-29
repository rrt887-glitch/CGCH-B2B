<script lang="ts" setup>
/**
 * 智点消耗：余额、本月消耗（按渠道与业务员）、流水；在线购买套餐；管理员可手动充值或调整。
 */
import type { ChannelCreditApi } from '#/api/channel/credit';

import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { useAccess } from '@vben/access';

import {
  Alert,
  Button,
  Card,
  Col,
  Form,
  FormItem,
  Input,
  InputNumber,
  message,
  Modal,
  RangePicker,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
} from 'ant-design-vue';

import {
  createCreditOrder,
  getCreditLedger,
  getCreditOrderPage,
  getCreditPackages,
  getCreditSummary,
  rechargeCredit,
} from '#/api/channel/credit';
import { getSimpleUserList } from '#/api/system/user';
import { fmtDateTime } from '#/utils/datetime';

defineOptions({ name: 'CgchConfigCredits' });

const { hasAccessByCodes } = useAccess();
const canRecharge = computed(() => hasAccessByCodes(['channel:credit:recharge']));
const canBuy = computed(() => hasAccessByCodes(['channel:credit:buy']));
const route = useRoute();
const router = useRouter();

const summary = ref<ChannelCreditApi.Summary>();
const users = ref<{ id: number; nickname: string }[]>([]);
const userName = (id?: number) => users.value.find((u) => u.id === id)?.nickname ?? (id ? `#${id}` : '—');
const CHANNEL: Record<string, string> = { mail: '邮件', whatsapp: 'WhatsApp', knowledge: '知识库', pay: '在线支付', other: '其他' };
const TYPE: Record<string, { color: string; label: string }> = {
  recharge: { color: 'green', label: '充值' },
  consume: { color: 'blue', label: '消耗' },
  adjust: { color: 'orange', label: '调整' },
};

const byUserRows = computed(() =>
  Object.entries(summary.value?.monthByUser ?? {})
    .map(([id, n]) => ({ id: Number(id), name: userName(Number(id)), credits: n }))
    .sort((a, b) => b.credits - a.credits),
);

// ===== 流水 =====
const filter = reactive<{ type?: string; channel?: string; userId?: number; range?: any[] }>({});
const list = ref<ChannelCreditApi.Ledger[]>([]);
const total = ref(0);
const pageNo = ref(1);
const loading = ref(false);
async function loadLedger() {
  loading.value = true;
  try {
    const res = await getCreditLedger({
      pageNo: pageNo.value,
      pageSize: 20,
      type: filter.type,
      channel: filter.channel,
      userId: filter.userId,
      createTime: filter.range?.length === 2
        ? [filter.range[0].format('YYYY-MM-DD 00:00:00'), filter.range[1].format('YYYY-MM-DD 23:59:59')]
        : undefined,
    });
    list.value = res.list;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
}
function search() {
  pageNo.value = 1;
  loadLedger();
}
const columns = [
  { title: '时间', dataIndex: 'createTime', key: 'time', width: 170, customRender: ({ text }: { text: unknown }) => fmtDateTime(text, 'YYYY-MM-DD HH:mm:ss') },
  { title: '类型', key: 'type', width: 80 },
  { title: '变动', key: 'amount', width: 100 },
  { title: '余额', dataIndex: 'balanceAfter', key: 'balance', width: 100 },
  { title: '渠道', key: 'channel', width: 100 },
  { title: '业务员', key: 'user', width: 120 },
  { title: 'Token', dataIndex: 'tokens', key: 'tokens', width: 90 },
  { title: '备注', dataIndex: 'remark', key: 'remark' },
];

// ===== 充值 =====
const open = ref(false);
const form = reactive({ amount: 1000 as number | undefined, remark: '' });
async function submit() {
  if (!form.amount) {
    message.warning('请填写数量');
    return;
  }
  const after = await rechargeCredit(form.amount, form.remark);
  message.success(`已调整，当前余额 ${after}`);
  open.value = false;
  await Promise.all([loadSummary(), loadLedger()]);
}

// ===== 在线购买 =====
const packages = ref<ChannelCreditApi.Package[]>([]);
const orders = ref<ChannelCreditApi.Order[]>([]);
const buying = ref<number>();
const yuan = (fen: number) => (fen / 100).toFixed(2);
async function buy(p: ChannelCreditApi.Package) {
  buying.value = p.id;
  try {
    const order = await createCreditOrder(p.id);
    const back = encodeURIComponent(route.path);
    router.push(`/pay/cashier?id=${order.payOrderId}&returnUrl=${back}`);
  } finally {
    buying.value = undefined;
  }
}
async function loadOrders() {
  orders.value = (await getCreditOrderPage({ pageNo: 1, pageSize: 10 })).list;
}
const orderColumns = [
  { title: '下单时间', dataIndex: 'createTime', key: 'time', width: 170, customRender: ({ text }: { text: unknown }) => fmtDateTime(text, 'YYYY-MM-DD HH:mm:ss') },
  { title: '套餐', dataIndex: 'packageName', key: 'pkg' },
  { title: '智点', dataIndex: 'credits', key: 'credits', width: 90 },
  { title: '金额', key: 'price', width: 100 },
  { title: '状态', key: 'status', width: 90 },
  { title: '支付时间', dataIndex: 'payTime', key: 'payTime', width: 170, customRender: ({ text }: { text: unknown }) => fmtDateTime(text, 'YYYY-MM-DD HH:mm:ss', '—') },
];

async function loadSummary() {
  summary.value = await getCreditSummary();
}

onMounted(async () => {
  if (route.query.payResult === 'success') {
    message.success('支付成功，智点到账可能有几秒延迟');
    router.replace({ path: route.path });
  }
  users.value = (await getSimpleUserList()) as any;
  await Promise.all([loadSummary(), loadLedger(), loadOrders()]);
  packages.value = await getCreditPackages();
});
</script>

<template>
  <Page auto-content-height>
    <Card title="智点消耗" :bordered="false">
      <template #extra>
        <Button v-if="canRecharge" type="primary" @click="open = true">充值 / 调整</Button>
      </template>
      <Alert
        v-if="summary && summary.balance <= 0"
        class="mb-4"
        type="error"
        show-icon
        message="智点已用完：AI 不会再起草或自动回复，新消息需要业务员人工处理。请在下方购买智点或联系管理员充值。"
      />
      <Row :gutter="16" class="mb-4">
        <Col :xs="12" :md="6"><Card size="small"><Statistic title="当前余额" :value="summary?.balance ?? 0" /></Card></Col>
        <Col :xs="12" :md="6"><Card size="small"><Statistic title="本月消耗" :value="summary?.monthConsumed ?? 0" /></Card></Col>
        <Col :xs="12" :md="6"><Card size="small"><Statistic title="累计获得" :value="summary?.totalIn ?? 0" /></Card></Col>
        <Col :xs="12" :md="6"><Card size="small"><Statistic title="累计消耗" :value="summary?.totalOut ?? 0" /></Card></Col>
      </Row>
      <p class="mb-4 text-sm text-gray-500">
        计费规则：AI 每次起草或回复按实际使用的 token 扣点，每 {{ summary?.tokensPerCredit ?? 1000 }} token 计 1 智点，不足 1 点按 1 点计。
      </p>
      <Row :gutter="16">
        <Col :xs="24" :md="12">
          <Card size="small" title="本月按渠道">
            <div v-if="Object.keys(summary?.monthByChannel ?? {}).length === 0" class="text-gray-400">本月暂无消耗</div>
            <div v-for="(n, ch) in summary?.monthByChannel" :key="ch" class="flex justify-between py-1">
              <span>{{ CHANNEL[ch] ?? ch }}</span><span class="font-medium">{{ n }}</span>
            </div>
          </Card>
        </Col>
        <Col :xs="24" :md="12">
          <Card size="small" title="本月按业务员">
            <div v-if="byUserRows.length === 0" class="text-gray-400">本月暂无消耗</div>
            <div v-for="r in byUserRows" :key="r.id" class="flex justify-between py-1">
              <span>{{ r.name }}</span><span class="font-medium">{{ r.credits }}</span>
            </div>
          </Card>
        </Col>
      </Row>
    </Card>

    <Card v-if="packages.length" title="购买智点" :bordered="false" class="mt-4">
      <Row :gutter="16">
        <Col v-for="p in packages" :key="p.id" :xs="24" :md="8" class="mb-3">
          <Card size="small" class="h-full">
            <div class="text-base font-medium">{{ p.name }}</div>
            <div class="my-2 text-2xl font-semibold">¥{{ yuan(p.price) }}</div>
            <div class="text-sm">{{ p.credits.toLocaleString() }} 智点</div>
            <div class="mb-3 min-h-5 text-xs text-gray-400">{{ p.description }}</div>
            <Button v-if="canBuy" type="primary" block :loading="buying === p.id" @click="buy(p)">立即购买</Button>
            <div v-else class="text-xs text-gray-400">请联系管理员购买</div>
          </Card>
        </Col>
      </Row>
      <Table v-if="orders.length" :columns="orderColumns" :data-source="orders" row-key="id" size="small" :pagination="false" class="mt-2">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'price'">¥{{ yuan(record.price) }}</template>
          <template v-else-if="column.key === 'status'">
            <Tag :color="record.payStatus ? 'green' : 'default'">{{ record.payStatus ? '已到账' : '待支付' }}</Tag>
          </template>
        </template>
      </Table>
    </Card>

    <Card title="流水" :bordered="false" class="mt-4">
      <Space class="mb-3" wrap>
        <Select v-model:value="filter.type" allow-clear placeholder="类型" class="w-28"
                :options="Object.entries(TYPE).map(([v, t]) => ({ value: v, label: t.label }))" />
        <Select v-model:value="filter.channel" allow-clear placeholder="渠道" class="w-32"
                :options="[{ value: 'mail', label: '邮件' }, { value: 'whatsapp', label: 'WhatsApp' }, { value: 'pay', label: '在线支付' }]" />
        <Select v-model:value="filter.userId" allow-clear show-search option-filter-prop="label" placeholder="业务员" class="w-40"
                :options="users.map((u) => ({ value: u.id, label: u.nickname }))" />
        <RangePicker :value="filter.range as any" @update:value="(v: any) => (filter.range = v)" />
        <Button type="primary" @click="search">查询</Button>
      </Space>
      <Table
        :columns="columns"
        :data-source="list"
        :loading="loading"
        row-key="id"
        size="small"
        :pagination="{ current: pageNo, pageSize: 20, total, onChange: (p: number) => { pageNo = p; loadLedger(); } }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'type'"><Tag :color="TYPE[record.type]?.color">{{ TYPE[record.type]?.label }}</Tag></template>
          <template v-else-if="column.key === 'amount'">
            <span :class="record.amount > 0 ? 'text-green-600' : 'text-red-500'">{{ record.amount > 0 ? '+' : '' }}{{ record.amount }}</span>
          </template>
          <template v-else-if="column.key === 'channel'">{{ record.channel ? CHANNEL[record.channel] ?? record.channel : '—' }}</template>
          <template v-else-if="column.key === 'user'">{{ userName(record.userId) }}</template>
        </template>
        <template #emptyText>暂无流水</template>
      </Table>
    </Card>

    <Modal v-model:open="open" title="充值 / 调整智点" @ok="submit">
      <Form layout="vertical">
        <FormItem label="数量（正数增加，负数扣减）" required><InputNumber v-model:value="form.amount" class="w-full" /></FormItem>
        <FormItem label="备注"><Input v-model:value="form.remark" :maxlength="200" placeholder="例如：线下转账充值" /></FormItem>
      </Form>
    </Modal>
  </Page>
</template>
