<script lang="ts" setup>
/**
 * AI 工作报告：日报 / 周报。总结 AI 接待情况，列出转人工的对话和知识库缺口；缺口可直接补进常见问答。
 */
import { fmtDateTime } from '#/utils/datetime';
import type { ChannelReportApi } from '#/api/channel/report';

import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  Alert,
  Button,
  Card,
  DatePicker,
  Empty,
  Form,
  FormItem,
  Input,
  message,
  Modal,
  Popconfirm,
  Radio,
  RadioGroup,
  Space,
  Spin,
  Switch,
  Tag,
  Textarea,
  Tooltip,
} from 'ant-design-vue';
import dayjs from 'dayjs';

import { getAiConfig, saveAiConfig, saveKnowledge } from '#/api/channel/ai';
import { deleteReport, generateReport, getReport, getReportPage } from '#/api/channel/report';

defineOptions({ name: 'CgchChannelReport' });

const router = useRouter();

// ===== 列表 =====
const type = ref<'' | 'daily' | 'weekly'>('');
const list = ref<ChannelReportApi.Report[]>([]);
const total = ref(0);
const pageNo = ref(1);
const listLoading = ref(false);
async function loadList(keep = false) {
  listLoading.value = true;
  try {
    const res = await getReportPage({ pageNo: pageNo.value, pageSize: 20, periodType: type.value || undefined });
    list.value = res.list;
    total.value = res.total;
    if (!keep && list.value.length > 0 && !list.value.some((r) => r.id === current.value?.id)) {
      await open(list.value[0]!.id);
    }
    if (list.value.length === 0) current.value = undefined;
  } finally {
    listLoading.value = false;
  }
}
const label = (r: ChannelReportApi.Report) =>
  r.periodType === 'weekly' ? `周报 ${r.periodStart.slice(5)} ~ ${r.periodEnd.slice(5)}` : `日报 ${r.periodStart}`;

// ===== 详情 =====
const current = ref<ChannelReportApi.Report>();
const loading = ref(false);
async function open(id: number) {
  loading.value = true;
  try {
    current.value = await getReport(id);
  } finally {
    loading.value = false;
  }
}
const st = computed(() => current.value?.stats);
const pct = (v?: number) => `${((v ?? 0) * 100).toFixed(1)}%`;
function change(a = 0, b = 0) {
  if (b === 0) return a === 0 ? '持平' : '上期为 0';
  const d = Math.round(((a - b) / b) * 100);
  return d === 0 ? '持平' : `${d > 0 ? '+' : ''}${d}%`;
}
const tiles = computed(() => {
  const c = st.value?.current;
  const p = st.value?.previous;
  if (!c || !p) return [];
  return [
    { label: '新询盘', value: String(c.inquiries), sub: `较上期 ${change(c.inquiries, p.inquiries)}` },
    { label: '回复率', value: pct(c.replyRate), sub: `上期 ${pct(p.replyRate)}` },
    { label: 'AI 发出占比', value: pct(c.aiShare), sub: `AI 发出 ${c.mailAiOut + c.waAiOut} 条` },
    { label: '转人工', value: String(c.aiHandoff), sub: `待确认 ${c.aiPending} 份` },
    { label: '新线索', value: String(c.cluesCreated), sub: `转客户 ${c.cluesConverted}` },
    { label: '智点消耗', value: String(c.creditsConsumed), sub: `较上期 ${change(c.creditsConsumed, p.creditsConsumed)}` },
  ];
});
function goto(item: ChannelReportApi.Item) {
  if (item.channel === 'mail') router.push(`/v3/conversation?channel=mail&ref=${item.refId}`);
  else router.push(`/v3/conversation?channel=whatsapp&ref=${item.refId}`);
}

// ===== 生成 =====
const genOpen = ref(false);
const generating = ref(false);
const gen = reactive({ type: 'daily' as 'daily' | 'weekly', date: dayjs() });
async function doGenerate() {
  generating.value = true;
  try {
    const id = await generateReport(gen.type, gen.date.format('YYYY-MM-DD'));
    message.success('报告已生成');
    genOpen.value = false;
    await loadList(true);
    await open(id);
  } finally {
    generating.value = false;
  }
}
async function remove() {
  if (!current.value) return;
  await deleteReport(current.value.id);
  message.success('已删除');
  current.value = undefined;
  await loadList();
}

// ===== 自动生成开关 =====
const auto = ref(false);
async function toggleAuto(v: boolean) {
  const cfg = await getAiConfig();
  await saveAiConfig({ ...cfg, llmApiKey: undefined, reportAuto: v });
  auto.value = v;
  message.success(v ? '已开启：每天早上生成昨天的日报，周一另生成上周周报' : '已关闭自动生成');
}

// ===== 补充知识库 =====
const kbOpen = ref(false);
const kbSaving = ref(false);
const kb = reactive({ title: '', content: '' });
const added = ref<Set<number>>(new Set());
function addToKb(item: ChannelReportApi.Item) {
  kb.title = item.question.replace(/^主题：/, '').split('\n').pop()?.slice(0, 200) ?? '';
  kb.content = '';
  kbItem.value = item;
  kbOpen.value = true;
}
const kbItem = ref<ChannelReportApi.Item>();
async function saveKb() {
  if (!kb.content.trim()) {
    message.warning('请填写答案');
    return;
  }
  kbSaving.value = true;
  try {
    await saveKnowledge({ type: 'faq', title: kb.title, content: kb.content, enabled: true, sort: 0 });
    if (kbItem.value) added.value.add(kbItem.value.draftId);
    message.success('已加入常见问答，之后遇到类似问题 AI 会按这条回答');
    kbOpen.value = false;
  } finally {
    kbSaving.value = false;
  }
}

onMounted(async () => {
  auto.value = !!(await getAiConfig()).reportAuto;
  await loadList();
});
</script>

<template>
  <Page auto-content-height>
    <div class="flex h-full gap-4">
      <Card :bordered="false" class="w-72 shrink-0" :body-style="{ padding: '12px' }">
        <div class="mb-3 flex items-center justify-between">
          <span class="font-medium">工作报告</span>
          <Button type="primary" size="small" @click="genOpen = true">立即生成</Button>
        </div>
        <RadioGroup v-model:value="type" size="small" class="mb-3" @change="() => { pageNo = 1; loadList(); }">
          <Radio.Button value="">全部</Radio.Button>
          <Radio.Button value="daily">日报</Radio.Button>
          <Radio.Button value="weekly">周报</Radio.Button>
        </RadioGroup>
        <div class="mb-3 flex items-center justify-between rounded bg-gray-50 px-2 py-1.5 text-sm dark:bg-gray-800">
          <Tooltip title="每天早上 7:30 生成昨天的日报，周一另生成上周周报">
            <span>每天自动生成</span>
          </Tooltip>
          <Switch :checked="auto" size="small" @change="(v: any) => toggleAuto(!!v)" />
        </div>
        <Spin :spinning="listLoading">
          <Empty v-if="list.length === 0" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="还没有报告" />
          <div
            v-for="r in list"
            :key="r.id"
            class="mb-1 cursor-pointer rounded px-2 py-2 hover:bg-gray-50 dark:hover:bg-gray-800"
            :class="r.id === current?.id ? 'bg-blue-50 dark:bg-gray-800' : ''"
            @click="open(r.id)"
          >
            <div class="text-sm font-medium">{{ label(r) }}</div>
            <div class="text-xs text-gray-500">
              {{ r.generateMode === 'auto' ? '自动生成' : '手动生成' }} · {{ fmtDateTime(r.createTime, 'MM-DD HH:mm') }}
            </div>
          </div>
          <div v-if="total > list.length" class="text-center">
            <Button size="small" type="link" @click="() => { pageNo++; loadList(true); }">更多</Button>
          </div>
        </Spin>
      </Card>

      <div class="min-w-0 flex-1 overflow-auto">
        <Spin :spinning="loading">
          <Card v-if="!current" :bordered="false">
            <Empty description="选择左侧报告，或点「立即生成」">
              <Button type="primary" @click="genOpen = true">立即生成今天的日报</Button>
            </Empty>
          </Card>
          <template v-else>
            <Card :bordered="false" class="mb-4">
              <template #title>{{ label(current) }}</template>
              <template #extra>
                <Space>
                  <Tag v-if="current.aiGenerated" color="blue">AI 撰写 · {{ current.credits }} 智点</Tag>
                  <Tag v-else>模板生成</Tag>
                  <Button size="small" @click="() => { gen.type = current!.periodType; gen.date = dayjs(current!.periodStart); doGenerate(); }">重新生成</Button>
                  <Popconfirm title="确定删除这份报告？" @confirm="remove">
                    <Button size="small" danger>删除</Button>
                  </Popconfirm>
                </Space>
              </template>
              <Alert
                v-if="!current.aiGenerated"
                class="mb-3"
                type="info"
                show-icon
                message="未配置大模型或智点不足，总结按模板生成。配置大模型后重新生成，可得到 AI 撰写的分析与建议。"
              />
              <div class="whitespace-pre-wrap leading-7">{{ current.summary }}</div>
            </Card>

            <div class="mb-4 grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-6">
              <Card v-for="t in tiles" :key="t.label" size="small" :bordered="false">
                <div class="text-sm text-gray-500">{{ t.label }}</div>
                <div class="my-1 text-xl font-semibold tabular-nums">{{ t.value }}</div>
                <div class="text-xs text-gray-500">{{ t.sub }}</div>
              </Card>
            </div>

            <Card :bordered="false" class="mb-4" :title="`转人工的对话（${st?.handoffs.length ?? 0}）`">
              <div class="mb-2 text-sm text-gray-500">AI 判断需要人工处理（投诉、退款、合同等），没有自动回复，请业务员跟进。</div>
              <Empty v-if="!st?.handoffs.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="本期没有转人工的对话" />
              <div v-for="h in st?.handoffs" :key="h.draftId" class="flex items-start justify-between gap-3 border-b py-2 last:border-b-0">
                <div class="min-w-0">
                  <div class="flex items-center gap-2">
                    <Tag :color="h.channel === 'mail' ? 'blue' : 'green'">{{ h.channel === 'mail' ? '邮件' : 'WhatsApp' }}</Tag>
                    <span class="truncate font-medium">{{ h.customer }}</span>
                    <span class="shrink-0 text-xs text-gray-500">{{ h.time }}</span>
                  </div>
                  <div class="mt-1 line-clamp-2 whitespace-pre-wrap text-sm text-gray-600 dark:text-gray-300">{{ h.question }}</div>
                </div>
                <Button size="small" @click="goto(h)">去处理</Button>
              </div>
            </Card>

            <Card :bordered="false" :title="`知识库缺口（${st?.gaps.length ?? 0}）`">
              <div class="mb-2 text-sm text-gray-500">这些问题在知识库里没有找到相关资料，AI 只能回复「确认后答复」。补充答案后，AI 下次可以直接回答。</div>
              <Empty v-if="!st?.gaps.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="本期没有缺口，知识库覆盖得不错" />
              <div v-for="g in st?.gaps" :key="g.draftId" class="flex items-start justify-between gap-3 border-b py-2 last:border-b-0">
                <div class="min-w-0">
                  <div class="flex items-center gap-2">
                    <Tag :color="g.channel === 'mail' ? 'blue' : 'green'">{{ g.channel === 'mail' ? '邮件' : 'WhatsApp' }}</Tag>
                    <span class="truncate text-sm text-gray-500">{{ g.customer }}</span>
                  </div>
                  <div class="mt-1 line-clamp-2 whitespace-pre-wrap text-sm">{{ g.question }}</div>
                </div>
                <Space>
                  <Button size="small" type="link" @click="goto(g)">查看</Button>
                  <Button size="small" :disabled="added.has(g.draftId)" @click="addToKb(g)">
                    {{ added.has(g.draftId) ? '已补充' : '补充到知识库' }}
                  </Button>
                </Space>
              </div>
            </Card>
          </template>
        </Spin>
      </div>
    </div>

    <Modal v-model:open="genOpen" title="生成工作报告" :confirm-loading="generating" @ok="doGenerate">
      <Form layout="vertical">
        <FormItem label="类型">
          <RadioGroup v-model:value="gen.type">
            <Radio value="daily">日报</Radio>
            <Radio value="weekly">周报（所选日期所在的一周）</Radio>
          </RadioGroup>
        </FormItem>
        <FormItem label="日期"><DatePicker v-model:value="gen.date" :allow-clear="false" class="w-full" /></FormItem>
        <div class="text-xs text-gray-500">同一天（或同一周）已有报告时会重新生成并替换。配置了大模型时，撰写总结会消耗少量智点。</div>
      </Form>
    </Modal>

    <Modal v-model:open="kbOpen" title="补充到常见问答" :width="640" :confirm-loading="kbSaving" @ok="saveKb">
      <Form layout="vertical">
        <FormItem label="问题"><Input v-model:value="kb.title" :maxlength="200" /></FormItem>
        <FormItem label="答案" required>
          <Textarea v-model:value="kb.content" :rows="6" :maxlength="8000" show-count placeholder="写下标准答案，AI 之后会按这条回答类似的问题" />
        </FormItem>
      </Form>
    </Modal>
  </Page>
</template>
