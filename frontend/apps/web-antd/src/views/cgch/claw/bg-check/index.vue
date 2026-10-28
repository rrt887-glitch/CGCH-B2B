<script lang="ts" setup>
/**
 * 深度背调：输入客户官网（或选择客户 / 线索），AI 读取官网公开页面并结合往来记录，整理成背调报告。
 * 可从 CRM 跳转过来：?customerId=1 或 ?clueId=2
 */
import { fmtDateTime } from '#/utils/datetime';
import type { ChannelResearchApi } from '#/api/channel/research';

import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  Alert,
  Button,
  Card,
  Descriptions,
  DescriptionsItem,
  Empty,
  Form,
  FormItem,
  Input,
  message,
  Popconfirm,
  Radio,
  RadioGroup,
  Select,
  Space,
  Spin,
  Tag,
} from 'ant-design-vue';

import { applyResearch, createResearch, deleteResearch, getResearch, getResearchPage } from '#/api/channel/research';
import { getCluePage } from '#/api/crm/clue';
import { getCustomerSimpleList } from '#/api/crm/customer';

defineOptions({ name: 'CgchClawBgCheck' });

const route = useRoute();
const router = useRouter();

const BUSINESS_TYPE: Record<string, string> = {
  manufacturer: '生产商', brand: '品牌商', distributor: '分销商', wholesaler: '批发商',
  retailer: '零售商', 'e-commerce': '电商卖家', trading: '贸易商', other: '其他',
};
const CONFIDENCE: Record<string, { color: string; label: string }> = {
  high: { color: 'green', label: '资料充分' }, medium: { color: 'blue', label: '资料一般' }, low: { color: 'orange', label: '资料较少' },
};

// ===== 发起 =====
const target = ref<'clue' | 'customer' | 'website'>('website');
const form = reactive({ website: '', companyName: '', customerId: undefined as number | undefined, clueId: undefined as number | undefined });
const customers = ref<{ id: number; name: string }[]>([]);
const clues = ref<{ id: number; name: string }[]>([]);
const creating = ref(false);
async function searchClues(kw: string) {
  clues.value = (await getCluePage({ pageNo: 1, pageSize: 20, name: kw, sceneType: 1 } as any)).list as any;
}
async function start() {
  creating.value = true;
  try {
    const id = await createResearch({
      website: form.website || undefined,
      companyName: form.companyName || undefined,
      customerId: target.value === 'customer' ? form.customerId : undefined,
      clueId: target.value === 'clue' ? form.clueId : undefined,
    });
    message.success('已开始背调，通常需要半分钟左右');
    await loadList();
    await open(id);
  } finally {
    creating.value = false;
  }
}

// ===== 列表与详情 =====
const list = ref<ChannelResearchApi.Research[]>([]);
async function loadList() {
  list.value = (await getResearchPage({ pageNo: 1, pageSize: 30 })).list;
}
const current = ref<ChannelResearchApi.Research>();
const loading = ref(false);
let timer: ReturnType<typeof setInterval> | undefined;
async function open(id: number) {
  loading.value = true;
  try {
    current.value = await getResearch(id);
  } finally {
    loading.value = false;
  }
  if (timer) clearInterval(timer);
  if (current.value?.status === 'running') {
    timer = setInterval(async () => {
      current.value = await getResearch(id);
      if (current.value.status !== 'running') {
        clearInterval(timer);
        timer = undefined;
        loadList();
      }
    }, 2000);
  }
}
onBeforeUnmount(() => timer && clearInterval(timer));
const rep = computed(() => current.value?.report);
const sections = computed(() => [
  { key: 'purchasingSignals', title: '采购信号', tip: '他们可能向我们采购什么' },
  { key: 'talkingPoints', title: '沟通切入点', tip: '跟进时可以聊的话题' },
  { key: 'risks', title: '风险提示', tip: '需要注意或核实的地方' },
] as const);

async function apply() {
  if (!current.value) return;
  const changed = await applyResearch(current.value.id);
  message.success(changed.length ? `已更新：${changed.join('、')}` : '客户资料已是最新，无需更新');
}
async function remove() {
  if (!current.value) return;
  await deleteResearch(current.value.id);
  current.value = undefined;
  await loadList();
}

onMounted(async () => {
  customers.value = (await getCustomerSimpleList()) as any;
  await loadList();
  const cid = Number(route.query.customerId);
  const lid = Number(route.query.clueId);
  if (cid) {
    target.value = 'customer';
    form.customerId = cid;
  } else if (lid) {
    target.value = 'clue';
    form.clueId = lid;
    await searchClues('');
  }
  const rid = Number(route.query.researchId);
  if (rid) await open(rid);
  else if (list.value[0]) await open(list.value[0].id);
});
</script>

<template>
  <Page auto-content-height>
    <div class="flex h-full gap-4">
      <div class="flex w-80 shrink-0 flex-col gap-4">
        <Card title="发起背调" :bordered="false" size="small">
          <Form layout="vertical">
            <FormItem label="背调对象">
              <RadioGroup v-model:value="target" size="small" button-style="solid">
                <Radio.Button value="website">输入官网</Radio.Button>
                <Radio.Button value="customer">CRM 客户</Radio.Button>
                <Radio.Button value="clue">线索</Radio.Button>
              </RadioGroup>
            </FormItem>
            <FormItem v-if="target === 'customer'" label="客户">
              <Select v-model:value="form.customerId" show-search option-filter-prop="label" placeholder="选择客户"
                      :options="customers.map((c) => ({ value: c.id, label: c.name }))" />
            </FormItem>
            <FormItem v-if="target === 'clue'" label="线索">
              <Select v-model:value="form.clueId" show-search :filter-option="false" placeholder="搜索线索名称"
                      :options="clues.map((c) => ({ value: c.id, label: c.name }))" @search="searchClues" @focus="searchClues('')" />
            </FormItem>
            <FormItem :label="target === 'website' ? '官网' : '官网（不填则用客户资料里的官网或企业邮箱域名）'">
              <Input v-model:value="form.website" placeholder="example.com" />
            </FormItem>
            <FormItem v-if="target === 'website'" label="公司名称（可选）"><Input v-model:value="form.companyName" /></FormItem>
            <Button type="primary" block :loading="creating" @click="start">开始背调</Button>
            <div class="mt-2 text-xs text-gray-500">
              只读取对方官网的少量公开页面（遵守 robots.txt），并结合我们与客户的往来记录；只整理企业层面的商业信息。会消耗少量智点。
            </div>
          </Form>
        </Card>
        <Card title="背调记录" :bordered="false" size="small" class="min-h-0 flex-1 overflow-auto">
          <Empty v-if="list.length === 0" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="还没有背调记录" />
          <div
            v-for="r in list"
            :key="r.id"
            class="mb-1 cursor-pointer rounded px-2 py-2 hover:bg-gray-50 dark:hover:bg-gray-800"
            :class="r.id === current?.id ? 'bg-blue-50 dark:bg-gray-800' : ''"
            @click="open(r.id)"
          >
            <div class="flex items-center justify-between gap-2">
              <span class="truncate text-sm font-medium">{{ r.companyName || r.website || `#${r.id}` }}</span>
              <Tag v-if="r.status !== 'done'" :color="r.status === 'running' ? 'processing' : 'red'">{{ r.status === 'running' ? '进行中' : '失败' }}</Tag>
            </div>
            <div class="truncate text-xs text-gray-500">{{ r.website }} · {{ fmtDateTime(r.createTime, 'MM-DD HH:mm') }}</div>
          </div>
        </Card>
      </div>

      <div class="min-w-0 flex-1 overflow-auto">
        <Spin :spinning="loading || current?.status === 'running'" :tip="current?.status === 'running' ? '正在读取官网并整理报告…' : undefined">
          <Card v-if="!current" :bordered="false"><Empty description="在左侧输入客户官网开始背调" /></Card>
          <template v-else>
            <Alert v-if="current.status === 'failed'" type="error" show-icon :message="`背调失败：${current.reasonDetail}`" class="mb-4" />
            <Alert v-else-if="current.reasonDetail" type="warning" show-icon :message="current.reasonDetail" class="mb-4" />
            <Card v-if="rep" :bordered="false" class="mb-4">
              <template #title>
                <Space>
                  <span>{{ rep.companyName || current.companyName || '背调报告' }}</span>
                  <Tag v-if="rep.businessType">{{ BUSINESS_TYPE[rep.businessType] ?? rep.businessType }}</Tag>
                  <Tag v-if="rep.countryCode">{{ rep.countryCode.toUpperCase() }}</Tag>
                  <Tag v-if="rep.confidence" :color="CONFIDENCE[rep.confidence]?.color">{{ CONFIDENCE[rep.confidence]?.label }}</Tag>
                </Space>
              </template>
              <template #extra>
                <Space>
                  <Button v-if="current.customerId" size="small" @click="router.push(`/crm/customer/detail/${current.customerId}`)">查看客户</Button>
                  <Button v-if="current.clueId" size="small" @click="router.push(`/crm/clue/detail/${current.clueId}`)">查看线索</Button>
                  <Button v-if="current.customerId || current.clueId" size="small" type="primary" @click="apply">补充到客户资料</Button>
                  <Popconfirm title="删除这份背调？" @confirm="remove"><Button size="small" danger>删除</Button></Popconfirm>
                </Space>
              </template>
              <p class="mb-4 leading-7">{{ rep.summary }}</p>
              <Descriptions :column="1" size="small" bordered>
                <DescriptionsItem label="官网">
                  <a v-if="current.website" :href="current.website" target="_blank" rel="noopener noreferrer">{{ current.website }}</a>
                </DescriptionsItem>
                <DescriptionsItem label="主营产品">{{ rep.mainProducts?.join('、') || '—' }}</DescriptionsItem>
                <DescriptionsItem label="市场">{{ rep.markets?.join('、') || '—' }}</DescriptionsItem>
                <DescriptionsItem label="规模线索">{{ rep.scale || '—' }}</DescriptionsItem>
                <DescriptionsItem label="认证资质">{{ rep.certifications?.join('、') || '—' }}</DescriptionsItem>
                <DescriptionsItem label="公司公开联系方式">{{ rep.companyContacts?.join('；') || '—' }}</DescriptionsItem>
              </Descriptions>
            </Card>
            <div v-if="rep" class="mb-4 grid grid-cols-1 gap-4 lg:grid-cols-3">
              <Card v-for="s in sections" :key="s.key" :title="s.title" size="small" :bordered="false">
                <div class="mb-2 text-xs text-gray-500">{{ s.tip }}</div>
                <ul v-if="rep[s.key]?.length" class="list-disc pl-5 text-sm leading-6">
                  <li v-for="(x, i) in rep[s.key]" :key="i">{{ x }}</li>
                </ul>
                <div v-else class="text-sm text-gray-400">无</div>
              </Card>
            </div>
            <Card v-if="current.sources?.length" title="参考来源" size="small" :bordered="false">
              <div v-for="src in current.sources" :key="src.url" class="border-b py-2 last:border-b-0">
                <a :href="src.url" target="_blank" rel="noopener noreferrer" class="text-sm">{{ src.title || src.url }}</a>
                <div class="truncate text-xs text-gray-500">{{ src.url }}</div>
                <div class="line-clamp-2 text-xs text-gray-500">{{ src.snippet }}</div>
              </div>
              <div class="mt-2 text-xs text-gray-400">报告由 AI 根据以上公开页面整理，重要信息请自行核实。消耗 {{ current.credits }} 智点。</div>
            </Card>
          </template>
        </Spin>
      </div>
    </div>
  </Page>
</template>
