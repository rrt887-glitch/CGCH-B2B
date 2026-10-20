<script lang="ts" setup>
/**
 * 数字员工：人设、回复模式、大模型连接，以及最近的 AI 回复记录。
 */
import type { ChannelAiApi } from '#/api/channel/ai';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  Alert,
  Button,
  Card,
  Col,
  Form,
  FormItem,
  Input,
  InputNumber,
  InputPassword,
  message,
  RadioButton,
  RadioGroup,
  Row,
  Select,
  Switch,
  Table,
  Tag,
  Textarea,
  Tooltip,
} from 'ant-design-vue';

import { DRAFT_REASON, getAiConfig, getDraftPage, saveAiConfig, testAiConfig } from '#/api/channel/ai';

defineOptions({ name: 'CgchAgentProfile' });

const PERSONALITIES = [
  { value: 'professional', label: '专业严谨', desc: '用词准确、条理清楚' },
  { value: 'concise', label: '高效简洁', desc: '直奔主题、少寒暄' },
  { value: 'friendly', label: '温和友好', desc: '语气亲切' },
  { value: 'proactive', label: '积极推进', desc: '主动提出寄样、报价等下一步' },
  { value: 'calm', label: '冷静专业', desc: '客户情绪激动时保持克制' },
];
const LANGUAGES = ['English', 'Español', 'Français', 'Deutsch', 'Português', 'Русский', 'العربية', '日本語', '한국어', '中文'].map(
  (v) => ({ value: v, label: v }),
);
/** 常见服务商的接口地址与模型，只是预填，可以改 */
const PROVIDERS = [
  { label: 'DeepSeek', base: 'https://api.deepseek.com/v1', model: 'deepseek-chat' },
  { label: '通义千问（兼容模式）', base: 'https://dashscope.aliyuncs.com/compatible-mode/v1', model: 'qwen-plus' },
  { label: 'Kimi（月之暗面）', base: 'https://api.moonshot.cn/v1', model: 'moonshot-v1-8k' },
  { label: 'OpenAI', base: 'https://api.openai.com/v1', model: 'gpt-4o-mini' },
];
const MODES = [
  { value: 'off', label: '关闭' },
  { value: 'draft', label: '只起草' },
  { value: 'auto', label: '自动回复' },
];

const form = reactive<ChannelAiApi.Config>({});
const saving = ref(false);
const testing = ref(false);

async function load() {
  Object.assign(form, await getAiConfig(), { llmApiKey: '' });
}

async function save() {
  saving.value = true;
  try {
    await saveAiConfig({ ...form });
    message.success('已保存');
    await load();
  } finally {
    saving.value = false;
  }
}

async function test() {
  testing.value = true;
  try {
    const answer = await testAiConfig({ ...form });
    message.success(`连接成功，模型回复：${answer}`);
  } finally {
    testing.value = false;
  }
}

function useProvider(label: string) {
  const p = PROVIDERS.find((x) => x.label === label);
  if (p) {
    form.llmBaseUrl = p.base;
    form.llmModel = p.model;
  }
}

// ===== AI 回复记录 =====
const logs = ref<ChannelAiApi.Draft[]>([]);
const logTotal = ref(0);
const logPage = ref(1);
const logLoading = ref(false);
async function loadLogs() {
  logLoading.value = true;
  try {
    const res = await getDraftPage({ pageNo: logPage.value, pageSize: 10 });
    logs.value = res.list;
    logTotal.value = res.total;
  } finally {
    logLoading.value = false;
  }
}
const STATUS: Record<string, { color: string; label: string }> = {
  sent: { color: 'green', label: '已发出' },
  pending: { color: 'orange', label: '待处理' },
  discarded: { color: 'default', label: '已放弃' },
};
const logColumns = [
  { title: '时间', dataIndex: 'createTime', key: 'time', width: 170 },
  { title: '渠道', key: 'channel', width: 100 },
  { title: '状态', key: 'status', width: 90 },
  { title: '原因', key: 'reason', width: 220 },
  { title: '回复内容', key: 'content' },
  { title: 'Token', key: 'tokens', width: 100 },
];

onMounted(() => {
  load();
  loadLogs();
});
</script>

<template>
  <Page auto-content-height>
    <Card title="数字员工" :bordered="false">
      <template #extra>
        <Button type="primary" :loading="saving" @click="save">保存</Button>
      </template>
      <Form layout="vertical">
        <Row :gutter="24">
          <Col :xs="24" :lg="12">
            <h4 class="mb-3 font-medium">人设</h4>
            <Row :gutter="16">
              <Col :span="12"><FormItem label="名称（回复时的自称）"><Input v-model:value="form.agentName" :maxlength="64" /></FormItem></Col>
              <Col :span="12">
                <FormItem label="默认答复语种" help="仅在无法判断客户语种时使用">
                  <Select v-model:value="form.defaultLanguage" :options="LANGUAGES" />
                </FormItem>
              </Col>
            </Row>
            <FormItem label="个性">
              <RadioGroup v-model:value="form.personality" button-style="solid">
                <Tooltip v-for="p in PERSONALITIES" :key="p.value" :title="p.desc">
                  <RadioButton :value="p.value">{{ p.label }}</RadioButton>
                </Tooltip>
              </RadioGroup>
            </FormItem>
            <FormItem label="补充要求" help="例如：报价统一用美元；不主动提及竞品">
              <Textarea v-model:value="form.extraInstructions" :rows="3" :maxlength="1000" />
            </FormItem>
            <FormItem label="邮件签名"><Textarea v-model:value="form.mailSignature" :rows="3" :maxlength="1000" /></FormItem>
          </Col>

          <Col :xs="24" :lg="12">
            <h4 class="mb-3 font-medium">回复模式</h4>
            <FormItem label="邮件">
              <RadioGroup v-model:value="form.mailMode" :options="MODES" option-type="button" button-style="solid" />
            </FormItem>
            <FormItem label="WhatsApp">
              <RadioGroup v-model:value="form.waMode" :options="MODES" option-type="button" button-style="solid" />
            </FormItem>
            <Alert
              class="mb-4"
              type="info"
              show-icon
              message="自动回复时，以下情况不会发出，只留草稿给业务员确认：AI 判断需要人工（投诉、退款、合同等）、回复含违禁词、业务员近期人工回复过、客户档案未开启「AI 托管」。新询盘（还没有客户档案）默认由 AI 接待。"
            />
            <FormItem label="业务员人工回复后，AI 暂停自动回复（小时）">
              <InputNumber v-model:value="form.humanPauseHours" :min="0" :max="720" class="w-40" />
            </FormItem>

            <h4 class="mb-3 mt-6 font-medium">线索自动建档</h4>
            <FormItem label="新询盘自动建线索" extra="陌生人发来邮件或 WhatsApp 时，自动在 CRM 线索里建档，负责人为渠道账号所属业务员；同一联系人不会重复建。">
              <Switch v-model:checked="form.leadAutoCreate" />
            </FormItem>
            <FormItem label="AI 提取公司、国家和需求" extra="用大模型从询盘内容里识别公司名、国家、联系人、产品和数量，每次消耗少量智点；关闭时只按邮箱域名和电话区号推断。">
              <Switch v-model:checked="form.leadAiExtract" :disabled="!form.leadAutoCreate" />
            </FormItem>

            <h4 class="mb-3 mt-6 font-medium">大模型</h4>
            <FormItem label="快速填写">
              <Select placeholder="选择服务商自动填写地址与模型" :options="PROVIDERS.map((p) => ({ value: p.label, label: p.label }))" @change="(v: any) => useProvider(String(v))" />
            </FormItem>
            <FormItem label="接口地址（OpenAI 兼容）"><Input v-model:value="form.llmBaseUrl" placeholder="https://api.deepseek.com/v1" /></FormItem>
            <Row :gutter="16">
              <Col :span="12"><FormItem label="模型名称"><Input v-model:value="form.llmModel" placeholder="deepseek-chat" /></FormItem></Col>
              <Col :span="12">
                <FormItem :label="form.hasApiKey ? 'API Key（已填写，不改请留空）' : 'API Key'">
                  <InputPassword v-model:value="form.llmApiKey" autocomplete="new-password" />
                </FormItem>
              </Col>
            </Row>
            <Button :loading="testing" @click="test">测试连接</Button>
          </Col>
        </Row>
      </Form>
    </Card>

    <Card title="AI 回复记录" :bordered="false" class="mt-4">
      <template #extra><Button size="small" @click="loadLogs">刷新</Button></template>
      <Table
        :columns="logColumns"
        :data-source="logs"
        :loading="logLoading"
        row-key="id"
        size="small"
        :pagination="{ current: logPage, pageSize: 10, total: logTotal, onChange: (p: number) => { logPage = p; loadLogs(); } }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'channel'">{{ record.channel === 'mail' ? '邮件' : 'WhatsApp' }}</template>
          <template v-else-if="column.key === 'status'">
            <Tag :color="STATUS[record.status]?.color">{{ STATUS[record.status]?.label }}</Tag>
          </template>
          <template v-else-if="column.key === 'reason'">
            <span v-if="record.reason">{{ DRAFT_REASON[record.reason] ?? record.reason }}</span>
            <span v-if="record.reasonDetail" class="text-gray-500">（{{ record.reasonDetail }}）</span>
          </template>
          <template v-else-if="column.key === 'content'">
            <div class="line-clamp-2 whitespace-pre-wrap text-sm">{{ record.content || '—' }}</div>
          </template>
          <template v-else-if="column.key === 'tokens'">
            <span class="text-xs text-gray-500">{{ (record.promptTokens || 0) + (record.completionTokens || 0) }}</span>
          </template>
        </template>
        <template #emptyText>还没有 AI 回复记录</template>
      </Table>
    </Card>
  </Page>
</template>
