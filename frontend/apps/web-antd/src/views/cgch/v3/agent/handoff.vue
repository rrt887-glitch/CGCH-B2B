<script lang="ts" setup>
/** 转人工：根据客户意图、根据客户条件、AI 工作时间、AI 黑名单、人工介入 AI 托管 */
import type { AgentApi } from '#/api/channel/agent';

import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { IconifyIcon } from '@vben/icons';

import { Button, Empty, Input, InputNumber, message, Modal, Popconfirm, Radio, RadioGroup, Select, Switch, TabPane, Tabs, Tag, Textarea } from 'ant-design-vue';

import { clone, useAgentSettings } from './use-settings';

const WorkTime = defineAsyncComponent(() => import('#/views/cgch/config/work-time/index.vue'));
const Blacklist = defineAsyncComponent(() => import('#/views/cgch/config/send-policy/index.vue'));

const { settings, load, save } = useAgentSettings();
const tab = ref('intent');
const h = ref<AgentApi.Handoff>();
const pauseHours = ref(12);

watch(
  () => settings.value,
  (s) => {
    if (!s) return;
    h.value = clone(s.handoff);
    pauseHours.value = s.humanPauseHours;
  },
  { immediate: true },
);

async function persist(tip = '已保存') {
  await save({ handoff: h.value }, tip);
}

const TYPES = [
  { value: 'handoff', label: '已转人工' },
  { value: 'missing', label: '信息缺失' },
  { value: 'permission', label: '权限决策' },
  { value: 'crisis', label: '客情危机' },
  { value: 'compliance', label: '合规风险' },
  { value: 'other', label: '其他' },
];

const groups = computed(() => {
  const m = new Map<string, AgentApi.Preset[]>();
  for (const p of settings.value?.presets ?? []) m.set(p.group, [...(m.get(p.group) ?? []), p]);
  return [...m.entries()];
});

function presetOn(key: string) {
  const v = h.value?.presets?.[key];
  return v === undefined ? !['ask_quote', 'ask_sample'].includes(key) : v;
}

function setPreset(key: string, v: boolean) {
  h.value!.presets = { ...h.value!.presets, [key]: v };
  persist(v ? '已开启' : '已关闭');
}

// ===== 自定义转人工项 =====
const customOpen = ref(false);
const customIndex = ref(-1);
const customDraft = ref<AgentApi.Custom>({ text: '', type: 'handoff', enabled: true });

function editCustom(i = -1) {
  if (i < 0 && (h.value?.custom.length ?? 0) >= 20) {
    message.warning('最多 20 条');
    return;
  }
  customIndex.value = i;
  customDraft.value = i >= 0 ? clone(h.value!.custom[i]!) : { text: '', type: 'handoff', enabled: true };
  customOpen.value = true;
}

async function saveCustom() {
  if (!customDraft.value.text.trim()) {
    message.warning('请描述触发条件');
    return;
  }
  const list = [...h.value!.custom];
  if (customIndex.value >= 0) list[customIndex.value] = customDraft.value;
  else list.push(customDraft.value);
  h.value!.custom = list;
  await persist();
  customOpen.value = false;
}

async function removeCustom(i: number) {
  h.value!.custom.splice(i, 1);
  await persist('已删除');
}

// ===== 根据客户条件 =====
const FIELDS = [
  { value: 'deal', label: '已成交客户' },
  { value: 'level', label: '客户级别' },
  { value: 'source', label: '客户来源' },
];
const condOpen = ref(false);
const condIndex = ref(-1);
const condDraft = ref<AgentApi.Condition>({ name: '', field: 'deal', values: [], enabled: true });
const valueOptions = computed(() =>
  condDraft.value.field === 'level'
    ? getDictOptions(DICT_TYPE.CRM_CUSTOMER_LEVEL, 'number')
    : condDraft.value.field === 'source'
      ? getDictOptions(DICT_TYPE.CRM_CUSTOMER_SOURCE, 'number')
      : [],
);

function condText(c: AgentApi.Condition) {
  if (c.field === 'deal') return '已成交的客户';
  const opts = c.field === 'level' ? getDictOptions(DICT_TYPE.CRM_CUSTOMER_LEVEL, 'number') : getDictOptions(DICT_TYPE.CRM_CUSTOMER_SOURCE, 'number');
  const labels = c.values.map((v) => opts.find((o: any) => o.value === v)?.label ?? v);
  return `${c.field === 'level' ? '客户级别' : '客户来源'}为 ${labels.join('、') || '-'}`;
}

function editCond(i = -1) {
  condIndex.value = i;
  condDraft.value = i >= 0 ? clone(h.value!.conditions[i]!) : { name: '', field: 'deal', values: [], enabled: true };
  condOpen.value = true;
}

async function saveCond() {
  const c = condDraft.value;
  if (c.field !== 'deal' && !c.values.length) {
    message.warning('请选择取值');
    return;
  }
  c.name = c.name.trim() || condText(c);
  const list = [...h.value!.conditions];
  if (condIndex.value >= 0) list[condIndex.value] = c;
  else list.push(c);
  h.value!.conditions = list;
  await persist();
  condOpen.value = false;
}

async function removeCond(i: number) {
  h.value!.conditions.splice(i, 1);
  await persist('已删除');
}

// ===== 人工介入 AI 托管 =====
async function saveTakeover() {
  await save({ handoff: h.value, humanPauseHours: pauseHours.value });
}

onMounted(() => load().catch(() => {}));
</script>

<template>
  <div class="ho">
    <Tabs v-model:active-key="tab" class="ho__tabs">
      <TabPane key="intent" tab="根据客户意图" />
      <TabPane key="condition" tab="根据客户条件" />
      <TabPane key="worktime" tab="AI 工作时间" />
      <TabPane key="blacklist" tab="AI 黑名单" />
      <TabPane key="takeover" tab="人工介入 AI 托管" />
    </Tabs>

    <div v-if="tab === 'intent' && h" class="ho__body">
      <div class="mb-1 flex items-center gap-2">
        <Switch size="small" :checked="h.intentEnabled" @change="(v: any) => ((h!.intentEnabled = !!v), persist(v ? '已启用' : '已停用'))" />
        <span class="text-sm">{{ h.intentEnabled ? '已启用' : '未启用' }}</span>
      </div>
      <p class="mb-4 text-sm text-gray-600">遇到以下情况时，AI 将自动转人工。此配置对企业内所有账号生效。</p>

      <div class="ho__block">
        <div class="flex items-center justify-between">
          <span class="ho__h">自定义转人工 {{ h.custom.length }}/20</span>
          <Button size="small" type="primary" ghost @click="editCustom()"><IconifyIcon icon="lucide:plus" class="mr-1 inline size-3.5" />添加转人工项</Button>
        </div>
        <p class="ho__tip">用自然语言描述触发条件；同时命中多项时，优先使用靠前的项。</p>
        <div v-if="!h.custom.length" class="py-3 text-center text-sm text-gray-400">尚未配置自定义转人工项</div>
        <div v-for="(c, i) in h.custom" :key="i" class="ho__item">
          <Switch size="small" :checked="c.enabled" @change="(v: any) => ((c.enabled = !!v), persist())" />
          <span class="min-w-0 flex-1 truncate text-sm">{{ c.text }}</span>
          <Tag class="m-0">{{ TYPES.find((t) => t.value === c.type)?.label }}</Tag>
          <a class="text-sm" @click="editCustom(i)">编辑</a>
          <Popconfirm title="删除这一项？" @confirm="removeCustom(i)"><a class="text-sm text-red-500">删除</a></Popconfirm>
        </div>
      </div>

      <div v-for="[group, items] in groups" :key="group" class="ho__block">
        <div class="ho__h mb-2">{{ group }}</div>
        <div v-for="p in items" :key="p.key" class="ho__item">
          <Switch size="small" :checked="presetOn(p.key)" :disabled="!h.intentEnabled" @change="(v: any) => setPreset(p.key, !!v)" />
          <span class="text-sm">{{ p.text }}</span>
        </div>
      </div>
    </div>

    <div v-else-if="tab === 'condition' && h" class="ho__body">
      <div class="mb-3 flex items-center justify-between">
        <span class="text-sm text-gray-600">命中条件的客户（例如老客户），AI 只起草回复、不直接发送，由业务员确认。</span>
        <Button type="primary" @click="editCond()"><IconifyIcon icon="lucide:plus" class="mr-1 inline size-4" />新建条件</Button>
      </div>
      <Empty v-if="!h.conditions.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="尚未配置老客户转人工条件" />
      <div v-for="(c, i) in h.conditions" :key="i" class="ho__item ho__item--card">
        <Switch size="small" :checked="c.enabled" @change="(v: any) => ((c.enabled = !!v), persist())" />
        <div class="min-w-0 flex-1">
          <div class="text-sm font-medium">{{ c.name }}</div>
          <div class="text-xs text-gray-500">{{ condText(c) }}</div>
        </div>
        <a class="text-sm" @click="editCond(i)">编辑</a>
        <Popconfirm title="删除这个条件？" @confirm="removeCond(i)"><a class="text-sm text-red-500">删除</a></Popconfirm>
      </div>
    </div>

    <div v-else-if="tab === 'worktime'" class="ho__embed"><WorkTime /></div>
    <div v-else-if="tab === 'blacklist'" class="ho__embed"><Blacklist /></div>

    <div v-else-if="tab === 'takeover' && h" class="ho__body">
      <p class="mb-4 text-sm text-gray-600">AI 托管期间，人工从其他平台（例如手机 WhatsApp、邮箱客户端）发送消息时，改变 AI 托管模式：</p>
      <RadioGroup v-model:value="h.takeoverMode" class="flex flex-col gap-4">
        <Radio value="pause">
          <span class="text-sm">暂停 AI 托管</span>
          <span class="ml-2 inline-flex items-center gap-1 text-sm text-gray-600">
            <InputNumber v-model:value="pauseHours" :min="1" :max="720" size="small" class="w-20" :disabled="h.takeoverMode !== 'pause'" />小时后自动恢复
          </span>
        </Radio>
        <Radio value="off"><span class="text-sm">关闭这个客户的 AI 托管</span><span class="ml-2 text-xs text-gray-500">之后由业务员接待，需要时再手动开启</span></Radio>
        <Radio value="none"><span class="text-sm">不改变</span><span class="ml-2 text-xs text-gray-500">AI 继续自动回复</span></Radio>
      </RadioGroup>
      <Button type="primary" class="mt-6" @click="saveTakeover">保存</Button>
    </div>

    <Modal v-model:open="customOpen" :title="customIndex >= 0 ? '编辑转人工项' : '添加转人工项'" ok-text="保存" @ok="saveCustom">
      <div class="mb-1 text-sm">触发条件</div>
      <Textarea v-model:value="customDraft.text" :rows="3" :maxlength="200" show-count placeholder="例如：客户询问能否做独家代理" />
      <div class="mb-1 mt-4 text-sm">在看板「需要支持」里归为</div>
      <Select v-model:value="customDraft.type" class="w-40" :options="TYPES" />
    </Modal>

    <Modal v-model:open="condOpen" :title="condIndex >= 0 ? '编辑条件' : '新建条件'" ok-text="保存" @ok="saveCond">
      <div class="mb-1 text-sm">名称（选填）</div>
      <Input v-model:value="condDraft.name" :maxlength="40" placeholder="例如：A 类老客户" />
      <div class="mb-1 mt-4 text-sm">条件</div>
      <Select v-model:value="condDraft.field" class="w-40" :options="FIELDS" @change="condDraft.values = []" />
      <Select
        v-if="condDraft.field !== 'deal'"
        v-model:value="condDraft.values"
        mode="multiple"
        class="ml-2 w-60"
        placeholder="选择取值"
        :options="valueOptions"
      />
    </Modal>
  </div>
</template>

<style scoped>
.ho {
  padding: 8px 16px 18px;
}

.ho__tabs :deep(.ant-tabs-nav) {
  margin-bottom: 14px;
}

.ho__body {
  max-width: 820px;
}

.ho__block {
  padding: 14px 16px;
  margin-bottom: 14px;
  border: 1px solid #eef0f3;
  border-radius: 10px;
}

.ho__h {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.ho__tip {
  margin: 4px 0 8px;
  font-size: 12px;
  color: #6b7280;
}

.ho__item {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 8px 0;
}

.ho__item--card {
  padding: 12px 14px;
  margin-bottom: 8px;
  border: 1px solid #eef0f3;
  border-radius: 8px;
}

.ho__embed :deep(.p-5),
.ho__embed :deep(.p-4) {
  padding: 0;
}
</style>
