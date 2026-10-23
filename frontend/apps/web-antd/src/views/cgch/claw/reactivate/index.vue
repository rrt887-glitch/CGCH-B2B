<script lang="ts" setup>
/**
 * 客户唤醒：筛出久未联系的老客户 → AI 逐个写跟进邮件（WhatsApp 用已审核模板）→ 业务员确认 → 批量发出 → 跟踪回复。
 */
import type { ChannelReactivateApi } from '#/api/channel/reactivate';
import type { ChannelWaApi } from '#/api/channel/whatsapp';

import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  Alert,
  Button,
  Card,
  Drawer,
  Empty,
  Form,
  FormItem,
  Input,
  message,
  Modal,
  Popconfirm,
  Progress,
  Radio,
  RadioGroup,
  Select,
  Space,
  Steps,
  Table,
  Tag,
  Textarea,
  Tooltip,
} from 'ant-design-vue';

import {
  changeReactivateItems,
  createReactivateTask,
  deleteReactivateTask,
  getReactivateCandidates,
  getReactivateTask,
  getReactivateTaskPage,
  regenerateReactivateItem,
  sendReactivateTask,
  updateReactivateItem,
} from '#/api/channel/reactivate';
import { getWaAccountList, getWaTemplates } from '#/api/channel/whatsapp';
import { getSimpleUserList } from '#/api/system/user';

defineOptions({ name: 'CgchClawReactivate' });

const router = useRouter();
const users = ref<{ id: number; nickname: string }[]>([]);
const userName = (id?: number) => users.value.find((u) => u.id === id)?.nickname ?? (id ? `#${id}` : '—');

const TASK_STATUS: Record<string, { color: string; label: string }> = {
  generating: { color: 'processing', label: 'AI 撰写中' },
  ready: { color: 'orange', label: '待确认' },
  sending: { color: 'processing', label: '发送中' },
  done: { color: 'green', label: '已完成' },
};
const ITEM_STATUS: Record<string, { color: string; label: string }> = {
  generating: { color: 'processing', label: '撰写中' },
  draft: { color: 'orange', label: '待确认' },
  approved: { color: 'blue', label: '已确认' },
  sent: { color: 'green', label: '已发送' },
  failed: { color: 'red', label: '失败' },
  skipped: { color: 'default', label: '已跳过' },
};

// ===== 任务列表 =====
const tasks = ref<ChannelReactivateApi.Task[]>([]);
const loading = ref(false);
async function loadTasks() {
  loading.value = true;
  try {
    tasks.value = (await getReactivateTaskPage({ pageNo: 1, pageSize: 50 })).list;
  } finally {
    loading.value = false;
  }
}

// ===== 新建任务 =====
const createOpen = ref(false);
const step = ref(0);
const filter = reactive({ inactiveDays: 90, keyword: '', ownerUserId: undefined as number | undefined });
const candidates = ref<ChannelReactivateApi.Candidate[]>([]);
const candLoading = ref(false);
const selected = ref<number[]>([]);
const form = reactive({ name: '', channel: 'auto', instructions: '', waTemplateName: undefined as string | undefined, waTemplateLanguage: 'en_US' });
const templates = ref<ChannelWaApi.Template[]>([]);
const creating = ref(false);

async function searchCandidates() {
  candLoading.value = true;
  try {
    candidates.value = await getReactivateCandidates({ inactiveDays: filter.inactiveDays, keyword: filter.keyword || undefined, ownerUserId: filter.ownerUserId });
    selected.value = candidates.value.filter((c) => !c.inTask).map((c) => c.id);
  } finally {
    candLoading.value = false;
  }
}
async function openCreate() {
  step.value = 0;
  Object.assign(form, { name: '', channel: 'auto', instructions: '', waTemplateName: undefined, waTemplateLanguage: 'en_US' });
  createOpen.value = true;
  await searchCandidates();
  try {
    const accounts = await getWaAccountList();
    templates.value = accounts[0]?.id ? (await getWaTemplates(accounts[0].id)).filter((t) => t.status === 'APPROVED') : [];
  } catch {
    templates.value = [];
  }
}
const candColumns = [
  { title: '客户', key: 'name' },
  { title: '国家', dataIndex: 'countryCode', key: 'country', width: 70 },
  { title: '联系方式', key: 'contact' },
  { title: '负责人', key: 'owner', width: 100 },
  { title: '上次联系', key: 'last', width: 150 },
];
const selectedWithoutMail = computed(() => candidates.value.filter((c) => selected.value.includes(c.id) && !c.email).length);
async function create() {
  if (selected.value.length === 0) {
    message.warning('请至少选择一位客户');
    return;
  }
  creating.value = true;
  try {
    const t = templates.value.find((x) => x.name === form.waTemplateName);
    const id = await createReactivateTask({
      ...form,
      waTemplateLanguage: t?.language ?? form.waTemplateLanguage,
      inactiveDays: filter.inactiveDays,
      customerIds: selected.value,
    });
    message.success('任务已创建，AI 正在逐个撰写');
    createOpen.value = false;
    await loadTasks();
    await openTask(id);
  } finally {
    creating.value = false;
  }
}

// ===== 任务详情 =====
const detailOpen = ref(false);
const task = ref<ChannelReactivateApi.Task>();
const itemSel = ref<number[]>([]);
let timer: ReturnType<typeof setInterval> | undefined;
async function refreshTask() {
  if (!task.value) return;
  task.value = await getReactivateTask(task.value.id);
  const busy = task.value.status === 'generating' || task.value.status === 'sending'
    || (task.value.items ?? []).some((i) => i.status === 'generating');
  if (!busy && timer) {
    clearInterval(timer);
    timer = undefined;
    loadTasks();
  }
}
async function openTask(id: number) {
  task.value = await getReactivateTask(id);
  itemSel.value = [];
  detailOpen.value = true;
  if (timer) clearInterval(timer);
  timer = setInterval(refreshTask, 2000);
}
onBeforeUnmount(() => timer && clearInterval(timer));

const progress = computed(() => {
  const t = task.value;
  if (!t || !t.total) return 0;
  return Math.round(((t.total - t.counts.generating) / t.total) * 100);
});
const itemColumns = [
  { title: '客户', key: 'customer', width: 180 },
  { title: '渠道', key: 'channel', width: 90 },
  { title: '内容', key: 'content' },
  { title: '状态', key: 'status', width: 110 },
  { title: '操作', key: 'action', width: 170 },
];
async function batch(action: 'approve' | 'draft' | 'skip', ids?: number[]) {
  const list = ids ?? itemSel.value;
  if (list.length === 0) {
    message.warning('请先勾选客户');
    return;
  }
  const n = await changeReactivateItems(action, list);
  message.success(`已处理 ${n} 条`);
  itemSel.value = [];
  await refreshTask();
}
async function approveAll() {
  const ids = (task.value?.items ?? []).filter((i) => i.status === 'draft').map((i) => i.id);
  await batch('approve', ids);
}
async function send() {
  if (!task.value) return;
  const n = await sendReactivateTask(task.value.id);
  message.success(`开始发送 ${n} 条，发送间隔几秒，避免被判为群发`);
  if (timer) clearInterval(timer);
  timer = setInterval(refreshTask, 2000);
  await refreshTask();
}
async function regenerate(i: ChannelReactivateApi.Item) {
  await regenerateReactivateItem(i.id);
  if (!timer) timer = setInterval(refreshTask, 2000);
  await refreshTask();
}
async function removeTask(id: number) {
  await deleteReactivateTask(id);
  message.success('已删除');
  detailOpen.value = false;
  await loadTasks();
}

// ===== 编辑 =====
const editOpen = ref(false);
const edit = reactive({ id: 0, subject: '', content: '', channel: 'mail' as string });
function openEdit(i: ChannelReactivateApi.Item) {
  Object.assign(edit, { id: i.id, subject: i.subject ?? '', content: i.content ?? '', channel: i.channel });
  editOpen.value = true;
}
async function saveEdit() {
  await updateReactivateItem({ id: edit.id, subject: edit.subject, content: edit.content });
  message.success('已保存');
  editOpen.value = false;
  await refreshTask();
}

onMounted(async () => {
  users.value = (await getSimpleUserList()) as any;
  await loadTasks();
});
</script>

<template>
  <Page auto-content-height>
    <Card title="客户唤醒" :bordered="false">
      <template #extra><Button type="primary" @click="openCreate">新建唤醒任务</Button></template>
      <Alert
        class="mb-4"
        type="info"
        show-icon
        message="选出很久没有联系的老客户，AI 会参考客户资料、上次联系内容和你的写作要求，为每位客户写一封跟进邮件；你逐条确认后再批量发出，并能看到谁回复了。"
      />
      <Empty v-if="!loading && tasks.length === 0" description="还没有唤醒任务" />
      <div class="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
        <Card v-for="t in tasks" :key="t.id" size="small" class="cursor-pointer" @click="openTask(t.id)">
          <div class="flex items-center justify-between">
            <span class="truncate font-medium">{{ t.name }}</span>
            <Tag :color="TASK_STATUS[t.status]?.color">{{ TASK_STATUS[t.status]?.label }}</Tag>
          </div>
          <div class="mt-2 text-sm text-gray-500">{{ t.createTime }} · {{ t.total }} 位客户</div>
          <div class="mt-2 flex gap-4 text-sm">
            <span>已发送 <b>{{ t.counts.sent }}</b></span>
            <span>待确认 <b>{{ t.counts.draft + t.counts.approved }}</b></span>
            <span>已回复 <b class="text-green-600">{{ t.replied }}</b></span>
          </div>
        </Card>
      </div>
    </Card>

    <!-- 新建 -->
    <Drawer v-model:open="createOpen" title="新建唤醒任务" width="880">
      <Steps :current="step" class="mb-6" :items="[{ title: '选择客户' }, { title: '写作要求' }]" />
      <template v-if="step === 0">
        <Space class="mb-3" wrap>
          <Select
            v-model:value="filter.inactiveDays"
            class="w-40"
            :options="[30, 60, 90, 180, 365].map((d) => ({ value: d, label: `超过 ${d} 天未联系` }))"
          />
          <Select v-model:value="filter.ownerUserId" allow-clear placeholder="负责人" class="w-36"
                  :options="users.map((u) => ({ value: u.id, label: u.nickname }))" />
          <Input v-model:value="filter.keyword" placeholder="客户或公司名称" class="w-48" @press-enter="searchCandidates" />
          <Button type="primary" @click="searchCandidates">筛选</Button>
        </Space>
        <Table
          :columns="candColumns"
          :data-source="candidates"
          :loading="candLoading"
          row-key="id"
          size="small"
          :pagination="{ pageSize: 10 }"
          :row-selection="{ selectedRowKeys: selected, onChange: (k: any[]) => (selected = k as number[]), getCheckboxProps: (r: any) => ({ disabled: r.inTask }) }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">
              <div class="font-medium">{{ record.name }}</div>
              <div class="text-xs text-gray-500">{{ record.companyName }}</div>
            </template>
            <template v-else-if="column.key === 'contact'">
              <div class="text-xs">{{ record.email || '—' }}</div>
              <div class="text-xs text-gray-500">{{ record.whatsapp }}</div>
            </template>
            <template v-else-if="column.key === 'owner'">{{ userName(record.ownerUserId) }}</template>
            <template v-else-if="column.key === 'last'">
              <div class="text-sm">{{ record.lastContactTime }}</div>
              <div class="text-xs text-gray-500">
                {{ record.contacted ? `${record.inactiveDays} 天前` : '建档后未联系' }}
                <Tag v-if="record.inTask" color="orange" class="ml-1">已在任务中</Tag>
              </div>
            </template>
          </template>
          <template #emptyText>没有符合条件的客户</template>
        </Table>
        <div class="mt-4 flex items-center justify-between">
          <span class="text-sm text-gray-500">已选 {{ selected.length }} 位客户（一次最多 200 位）</span>
          <Button type="primary" :disabled="selected.length === 0" @click="step = 1">下一步</Button>
        </div>
      </template>

      <template v-else>
        <Form layout="vertical">
          <FormItem label="任务名称"><Input v-model:value="form.name" placeholder="例如：2027 新品老客户唤醒（不填自动生成）" /></FormItem>
          <FormItem label="发送渠道">
            <RadioGroup v-model:value="form.channel">
              <Radio value="auto">优先邮件，没有邮箱的发 WhatsApp</Radio>
              <Radio value="mail">只发邮件</Radio>
              <Radio value="whatsapp">只发 WhatsApp</Radio>
            </RadioGroup>
          </FormItem>
          <FormItem label="给 AI 的写作要求" extra="写清这次联系客户的理由，AI 只会使用这里和知识库里的信息，不会编造价格和交期。">
            <Textarea v-model:value="form.instructions" :rows="4" :maxlength="1000" show-count
                      placeholder="例如：介绍我们 2027 年的新款吹风机，比上一代轻 20%，可以免费寄样；询问客户今年的采购计划。" />
          </FormItem>
          <FormItem
            v-if="form.channel !== 'mail'"
            label="WhatsApp 模板"
            :extra="`超过 24 小时未联系的客户只能收到 Meta 审核通过的模板消息。${selectedWithoutMail && form.channel === 'auto' ? `已选客户中有 ${selectedWithoutMail} 位没有邮箱，会改发 WhatsApp。` : ''}`"
          >
            <Select v-model:value="form.waTemplateName" allow-clear placeholder="选择已审核的模板"
                    :options="templates.map((t) => ({ value: t.name, label: `${t.name}（${t.language}）` }))" />
          </FormItem>
        </Form>
        <div class="mt-4 flex justify-between">
          <Button @click="step = 0">上一步</Button>
          <Button type="primary" :loading="creating" @click="create">创建并让 AI 撰写（{{ selected.length }} 位）</Button>
        </div>
      </template>
    </Drawer>

    <!-- 详情 -->
    <Drawer v-model:open="detailOpen" :title="task?.name" width="1000">
      <template v-if="task" #extra>
        <Popconfirm title="删除这个任务？已发送的邮件不受影响" @confirm="removeTask(task.id)">
          <Button size="small" danger :disabled="task.status === 'sending'">删除</Button>
        </Popconfirm>
      </template>
      <template v-if="task">
        <div class="mb-4 grid grid-cols-3 gap-3 md:grid-cols-6">
          <div v-for="(s, k) in ITEM_STATUS" :key="k" class="rounded bg-gray-50 p-2 text-center dark:bg-gray-800">
            <div class="text-lg font-semibold tabular-nums">{{ task.counts[k as ChannelReactivateApi.ItemStatus] }}</div>
            <div class="text-xs text-gray-500">{{ s.label }}</div>
          </div>
        </div>
        <div v-if="task.status === 'generating'" class="mb-4"><Progress :percent="progress" size="small" /><span class="text-sm text-gray-500">AI 正在逐个撰写…</span></div>
        <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
          <Space wrap>
            <Button size="small" :disabled="!task.counts.draft" @click="approveAll">全部确认（{{ task.counts.draft }}）</Button>
            <Button size="small" @click="batch('approve')">确认所选</Button>
            <Button size="small" @click="batch('draft')">撤回确认</Button>
            <Button size="small" @click="batch('skip')">跳过所选</Button>
          </Space>
          <Space>
            <span class="text-sm">已回复 <b class="text-green-600">{{ task.replied }}</b> / 已发送 {{ task.counts.sent }}</span>
            <Button type="primary" :disabled="!task.counts.approved || task.status === 'sending'" @click="send">
              发送已确认（{{ task.counts.approved }}）
            </Button>
          </Space>
        </div>
        <Table
          :columns="itemColumns"
          :data-source="task.items"
          row-key="id"
          size="small"
          :pagination="{ pageSize: 20 }"
          :row-selection="{ selectedRowKeys: itemSel, onChange: (k: any[]) => (itemSel = k as number[]), getCheckboxProps: (r: any) => ({ disabled: ['sent', 'generating'].includes(r.status) }) }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'customer'">
              <a @click="router.push(`/crm/customer/detail/${record.customerId}`)">{{ record.customerName }}</a>
              <div class="text-xs text-gray-500">{{ record.recipient }}</div>
              <div class="text-xs text-gray-400">负责人 {{ userName(record.ownerUserId) }}</div>
            </template>
            <template v-else-if="column.key === 'channel'">
              <Tag :color="record.channel === 'mail' ? 'blue' : 'green'">{{ record.channel === 'mail' ? '邮件' : 'WhatsApp' }}</Tag>
            </template>
            <template v-else-if="column.key === 'content'">
              <div v-if="record.subject" class="truncate font-medium">{{ record.subject }}</div>
              <div class="line-clamp-2 whitespace-pre-wrap text-sm text-gray-600 dark:text-gray-300">{{ record.content }}</div>
              <div v-if="record.reasonDetail" class="text-xs text-red-500">{{ record.reasonDetail }}</div>
            </template>
            <template v-else-if="column.key === 'status'">
              <Tag :color="ITEM_STATUS[record.status]?.color">{{ ITEM_STATUS[record.status]?.label }}</Tag>
              <Tag v-if="record.replied" color="green">已回复</Tag>
              <div v-if="record.sentTime" class="text-xs text-gray-400">{{ record.sentTime }}</div>
            </template>
            <template v-else-if="column.key === 'action'">
              <Space :size="2">
                <Button size="small" type="link" :disabled="!['draft', 'approved'].includes(record.status) || record.channel !== 'mail'" @click="openEdit(record as any)">修改</Button>
                <Tooltip title="让 AI 重新写一封">
                  <Button size="small" type="link" :disabled="record.channel !== 'mail' || !['draft', 'failed', 'approved'].includes(record.status)" @click="regenerate(record as any)">重写</Button>
                </Tooltip>
                <Button v-if="record.status === 'draft'" size="small" type="link" @click="batch('approve', [record.id])">确认</Button>
              </Space>
            </template>
          </template>
        </Table>
      </template>
    </Drawer>

    <Modal v-model:open="editOpen" title="修改内容" :width="720" @ok="saveEdit">
      <Form layout="vertical">
        <FormItem v-if="edit.channel === 'mail'" label="主题"><Input v-model:value="edit.subject" :maxlength="200" /></FormItem>
        <FormItem label="正文"><Textarea v-model:value="edit.content" :rows="12" /></FormItem>
      </Form>
    </Modal>
  </Page>
</template>
