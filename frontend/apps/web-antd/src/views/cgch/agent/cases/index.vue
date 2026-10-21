<script lang="ts" setup>
/**
 * 成交案例：AI 从已成交客户的往来记录中总结成交关键点、客户顾虑与回应、有效话术；
 * 人工确认开启后，AI 回复同类询盘时参考（不会透露原客户名称和价格）。
 */
import type { ChannelCaseApi } from '#/api/channel/cases';

import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  Alert,
  Button,
  Card,
  Empty,
  Form,
  FormItem,
  Input,
  message,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Tag,
  Textarea,
} from 'ant-design-vue';

import { deleteCase, enableCase, generateCase, getCaseCandidates, getCaseList, saveCase } from '#/api/channel/cases';

defineOptions({ name: 'CgchAgentCases' });

const router = useRouter();
const list = ref<ChannelCaseApi.Case[]>([]);
const candidates = ref<ChannelCaseApi.Candidate[]>([]);
const loading = ref(false);
async function load() {
  loading.value = true;
  try {
    [list.value, candidates.value] = await Promise.all([getCaseList(), getCaseCandidates()]);
  } finally {
    loading.value = false;
  }
}
const pendingCandidates = computed(() => candidates.value.filter((c) => !c.hasCase));

// ===== AI 生成 =====
const pick = ref<number>();
const generating = ref(false);
async function generate() {
  if (!pick.value) {
    message.warning('请选择一位已成交客户');
    return;
  }
  generating.value = true;
  try {
    const id = await generateCase(pick.value);
    message.success('案例已生成，请检查内容后开启');
    pick.value = undefined;
    await load();
    const c = list.value.find((x) => x.id === id);
    if (c) openEdit(c);
  } finally {
    generating.value = false;
  }
}

// ===== 编辑 =====
const editOpen = ref(false);
const saving = ref(false);
const form = reactive({ id: undefined as number | undefined, title: '', industry: '', product: '', countryCode: '', summary: '',
  keyPoints: '', objections: '', phrases: '', enabled: false, customerName: '' });
const lines = (s: string) => s.split('\n').map((x) => x.trim()).filter(Boolean);
function openEdit(c?: ChannelCaseApi.Case) {
  Object.assign(form, {
    id: c?.id, title: c?.title ?? '', industry: c?.industry ?? '', product: c?.product ?? '', countryCode: c?.countryCode ?? '',
    summary: c?.summary ?? '', keyPoints: (c?.keyPoints ?? []).join('\n'), objections: (c?.objections ?? []).join('\n'),
    phrases: (c?.phrases ?? []).join('\n'), enabled: c?.enabled ?? false, customerName: c?.customerName ?? '',
  });
  editOpen.value = true;
}
async function save() {
  if (!form.title.trim()) {
    message.warning('请填写标题');
    return;
  }
  saving.value = true;
  try {
    await saveCase({ ...form, keyPoints: lines(form.keyPoints), objections: lines(form.objections), phrases: lines(form.phrases) });
    message.success('已保存');
    editOpen.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function toggle(c: ChannelCaseApi.Case, v: boolean) {
  await enableCase(c.id!, v);
  c.enabled = v;
  message.success(v ? '已开启，AI 回复同类询盘时会参考' : '已关闭');
}
async function remove(c: ChannelCaseApi.Case) {
  await deleteCase(c.id!);
  message.success('已删除');
  await load();
}

onMounted(load);
</script>

<template>
  <Page auto-content-height>
    <Card title="成交案例" :bordered="false">
      <template #extra><Button @click="openEdit()">手动添加</Button></template>
      <Alert
        class="mb-4"
        type="info"
        show-icon
        message="从成交客户的往来中总结经验：成交关键点、客户顾虑及回应、有效话术。开启后，AI 回复同类询盘时会借鉴这些思路和话术；原客户的名称、联系方式和具体价格会自动去掉，不会透露给其他客户。"
      />
      <div class="mb-4 flex flex-wrap items-center gap-2 rounded bg-gray-50 p-3 dark:bg-gray-800">
        <span class="text-sm">从已成交客户生成：</span>
        <Select
          v-model:value="pick"
          show-search
          option-filter-prop="label"
          placeholder="选择已成交的客户"
          class="w-64"
          :options="pendingCandidates.map((c) => ({ value: c.id, label: c.companyName ? `${c.name}（${c.companyName}）` : c.name }))"
          :not-found-content="candidates.length ? '已成交客户都已生成过案例' : '还没有已成交的客户（在客户详情里标记成交）'"
        />
        <Button type="primary" :loading="generating" @click="generate">AI 生成案例</Button>
        <span class="text-xs text-gray-500">会读取与该客户的全部邮件和 WhatsApp 往来，消耗少量智点</span>
      </div>

      <Empty v-if="!loading && list.length === 0" description="还没有成交案例" />
      <div class="grid grid-cols-1 gap-4 xl:grid-cols-2">
        <Card v-for="c in list" :key="c.id" size="small">
          <template #title>
            <Space>
              <span>{{ c.title }}</span>
              <Tag v-if="c.source === 'ai'" color="blue">AI 生成</Tag>
            </Space>
          </template>
          <template #extra>
            <Space>
              <span class="text-xs text-gray-500">供 AI 参考</span>
              <Switch :checked="c.enabled" size="small" @change="(v: any) => toggle(c, !!v)" />
            </Space>
          </template>
          <div class="mb-2 flex flex-wrap gap-1">
            <Tag v-if="c.industry">{{ c.industry }}</Tag>
            <Tag v-if="c.product">{{ c.product }}</Tag>
            <Tag v-if="c.countryCode">{{ c.countryCode }}</Tag>
          </div>
          <p class="mb-2 text-sm">{{ c.summary }}</p>
          <div v-if="c.keyPoints.length" class="mb-2">
            <div class="text-xs font-medium text-gray-500">成交关键点</div>
            <ul class="list-disc pl-5 text-sm"><li v-for="(x, i) in c.keyPoints" :key="i">{{ x }}</li></ul>
          </div>
          <div v-if="c.objections.length" class="mb-2">
            <div class="text-xs font-medium text-gray-500">客户顾虑与回应</div>
            <ul class="list-disc pl-5 text-sm"><li v-for="(x, i) in c.objections" :key="i">{{ x }}</li></ul>
          </div>
          <div v-if="c.phrases.length" class="mb-2">
            <div class="text-xs font-medium text-gray-500">有效话术</div>
            <ul class="list-disc pl-5 text-sm italic"><li v-for="(x, i) in c.phrases" :key="i">{{ x }}</li></ul>
          </div>
          <div class="mt-2 flex items-center justify-between text-xs text-gray-400">
            <span>
              <a v-if="c.customerId" @click="router.push(`/crm/customer/detail/${c.customerId}`)">来源客户：{{ c.customerName }}（仅内部可见）</a>
              <template v-else>手动录入</template>
              · {{ c.createTime }}
            </span>
            <Space>
              <Button size="small" type="link" @click="openEdit(c)">修改</Button>
              <Popconfirm title="删除这个案例？" @confirm="remove(c)"><Button size="small" type="link" danger>删除</Button></Popconfirm>
            </Space>
          </div>
        </Card>
      </div>
    </Card>

    <Modal v-model:open="editOpen" :title="form.id ? '修改案例' : '添加案例'" :width="760" :confirm-loading="saving" @ok="save">
      <Form layout="vertical">
        <FormItem label="标题" required><Input v-model:value="form.title" :maxlength="200" /></FormItem>
        <div class="grid grid-cols-3 gap-3">
          <FormItem label="客户类型 / 行业"><Input v-model:value="form.industry" placeholder="例如：澳洲家电分销商" /></FormItem>
          <FormItem label="成交产品"><Input v-model:value="form.product" /></FormItem>
          <FormItem label="国家"><Input v-model:value="form.countryCode" placeholder="US" /></FormItem>
        </div>
        <FormItem label="背景与过程"><Textarea v-model:value="form.summary" :rows="3" /></FormItem>
        <FormItem label="成交关键点（每行一条）"><Textarea v-model:value="form.keyPoints" :rows="3" /></FormItem>
        <FormItem label="客户顾虑与回应（每行一条，例如：顾虑：交期太长；回应：先发现货 200 台）"><Textarea v-model:value="form.objections" :rows="3" /></FormItem>
        <FormItem label="有效话术（每行一条，保留原文）"><Textarea v-model:value="form.phrases" :rows="3" /></FormItem>
        <FormItem label="供 AI 参考"><Switch v-model:checked="form.enabled" /></FormItem>
        <div class="text-xs text-gray-500">保存时会自动把原客户的名称、邮箱、电话替换为「客户」，把具体价格替换为「[价格]」。</div>
      </Form>
    </Modal>
  </Page>
</template>
