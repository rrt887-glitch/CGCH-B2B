<script lang="ts" setup>
/** 教话术：学习真人销售、FAQ（成交案例）、固定开场白、言语边界 */
import type { AgentApi } from '#/api/channel/agent';

import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Button, Checkbox, CheckboxGroup, InputNumber, message, Radio, RadioGroup, Select, Switch, TabPane, Tabs, Tag, Textarea } from 'ant-design-vue';

import { learnNow } from '#/api/channel/agent';
import type { CloudApi } from '#/api/channel/cloud';
import { getEnterpriseFiles } from '#/api/channel/cloud';

import { clone, useAgentSettings } from './use-settings';

const Cases = defineAsyncComponent(() => import('#/views/cgch/agent/cases/index.vue'));
const Forbidden = defineAsyncComponent(() => import('#/views/cgch/agent/forbidden/index.vue'));

const { settings, load, save } = useAgentSettings();
const tab = ref('learn');

// ===== 学习真人销售 =====
const learn = ref<AgentApi.Learn>();
const style = ref('');
const learning = ref(false);
const learnDirty = ref(false);

watch(
  () => settings.value,
  (s) => {
    if (!s) return;
    learn.value = clone(s.learn);
    style.value = s.salesStyle;
    opening.value = { ...clone(s.opening), fileIds: s.opening.fileIds ?? [] };
  },
  { immediate: true },
);

async function saveLearn() {
  await save({ learn: learn.value });
  learnDirty.value = false;
}

async function toggleLearn(v: boolean) {
  learn.value!.enabled = v;
  await save({ learn: learn.value }, v ? '已启用' : '已停用');
}

async function runLearn() {
  if (learnDirty.value) await saveLearn();
  learning.value = true;
  try {
    const r = await learnNow();
    style.value = r.salesStyle;
    if (settings.value) {
      settings.value.salesStyle = r.salesStyle;
      settings.value.learn.lastLearnTime = r.lastLearnTime;
      settings.value.learn.lastSamples = r.samples;
    }
    learn.value!.lastLearnTime = r.lastLearnTime;
    learn.value!.lastSamples = r.samples;
    message.success(`已从 ${r.samples} 段人工对话中学习`);
  } finally {
    learning.value = false;
  }
}

const styleDirty = computed(() => settings.value && style.value !== settings.value.salesStyle);

// ===== 固定开场白 =====
const opening = ref<AgentApi.Opening>();

async function saveOpening() {
  await save({ opening: opening.value });
}

const cloudFiles = ref<CloudApi.File[]>([]);

onMounted(() => {
  load().catch(() => {});
  getEnterpriseFiles()
    .then((f) => (cloudFiles.value = f))
    .catch(() => {});
});
</script>

<template>
  <div class="sc">
    <Tabs v-model:active-key="tab" class="sc__tabs">
      <TabPane key="learn" tab="学习真人销售" />
      <TabPane key="faq" tab="FAQ" />
      <TabPane key="opening" tab="固定开场白" />
      <TabPane key="boundary" tab="言语边界" />
    </Tabs>

    <div v-if="tab === 'learn' && learn" class="sc__body">
      <div class="mb-4 flex items-center gap-2">
        <Switch size="small" :checked="learn.enabled" @change="(v: any) => toggleLearn(!!v)" />
        <span class="text-sm">{{ learn.enabled ? '已启用' : '未启用' }}</span>
      </div>

      <div class="sc__block">
        <div class="sc__head"><span class="sc__no">1</span>选择学习范围</div>
        <p class="sc__tip">选择用于学习的人工对话数据范围</p>
        <RadioGroup value="all" class="flex flex-col gap-2">
          <Radio value="all">
            <span class="font-medium">全部账号 · 全部渠道（默认）</span>
            <div class="text-xs text-gray-500">学习当前企业下全部已接入账号的人工对话</div>
          </Radio>
          <Radio value="some" disabled>
            <span>指定账号 · 指定渠道</span> <Tag class="ml-1">即将开放</Tag>
          </Radio>
        </RadioGroup>
      </div>

      <div class="sc__block">
        <div class="sc__head"><span class="sc__no">2</span>学习内容与质量</div>
        <p class="sc__tip">设置学习周期、学习目标与样本质量要求</p>
        <div class="mb-4">
          <div class="sc__label">时间范围</div>
          <div class="flex items-center gap-2 text-sm">
            最近
            <InputNumber v-model:value="learn.days" :min="1" :max="365" class="w-24" @change="learnDirty = true" />
            天
          </div>
          <div class="mt-1 text-xs text-gray-500">每次学习按最近 N 天的对话重新总结，旧样本自然淘汰</div>
        </div>
        <div class="mb-4">
          <div class="sc__label">学习目标</div>
          <CheckboxGroup v-model:value="learn.goals" class="flex flex-col gap-2" @change="learnDirty = true">
            <Checkbox value="needs">挖需策略 <span class="text-xs text-gray-500">学习销售如何识别需求、追问和推进客户表达真实诉求</span></Checkbox>
            <Checkbox value="style">回复风格 <span class="text-xs text-gray-500">学习人工销售常用的语气、表达长度和回复习惯</span></Checkbox>
          </CheckboxGroup>
        </div>
        <div class="flex items-start gap-2">
          <Switch size="small" :checked="learn.repliedOnly" @change="(v: any) => ((learn!.repliedOnly = !!v), (learnDirty = true))" />
          <div>
            <div class="text-sm">仅学习包含客户回复的对话</div>
            <div class="text-xs text-gray-500">过滤单向发送但未获得客户回复的会话，保证学习样本质量</div>
          </div>
        </div>
      </div>

      <div class="flex flex-wrap items-center gap-3">
        <Button :disabled="!learnDirty" @click="saveLearn">保存设置</Button>
        <Button type="primary" :loading="learning" :disabled="!learn.enabled" @click="runLearn">
          <IconifyIcon icon="lucide:graduation-cap" class="mr-1 inline size-4" />立即学习
        </Button>
        <span v-if="learn.lastLearnTime" class="text-xs text-gray-500">上次学习：{{ learn.lastLearnTime }} · {{ learn.lastSamples }} 段对话</span>
      </div>

      <div class="sc__block mt-5">
        <div class="sc__head">学到的话术要点</div>
        <p class="sc__tip">AI 回复客户时会参考这些要点。可以直接修改；再次学习会覆盖。</p>
        <Textarea v-model:value="style" :rows="8" :maxlength="3000" show-count placeholder="还没有学习，点「立即学习」从最近的人工回复中总结" />
        <Button class="mt-2" size="small" :disabled="!styleDirty" @click="save({ salesStyle: style })">保存要点</Button>
      </div>
      <p class="text-xs text-gray-400">学习范围只决定读取哪些历史人工对话，学习结果对整个企业生效。</p>
    </div>

    <div v-else-if="tab === 'faq'" class="sc__embed"><Cases /></div>

    <div v-else-if="tab === 'opening' && opening" class="sc__body">
      <div class="mb-4 flex items-center gap-2">
        <Switch size="small" :checked="opening.enabled" @change="(v: any) => (opening!.enabled = !!v)" />
        <span class="text-sm">{{ opening.enabled ? '已启用' : '未启用' }}</span>
      </div>
      <div class="sc__block">
        <div class="sc__head">开场问候</div>
        <p class="sc__tip">新客户第一次发来消息时，AI 先发送这段问候，再回答客户的问题</p>
        <div class="sc__label">问候内容</div>
        <Textarea v-model:value="opening.greeting" :rows="4" :maxlength="1000" show-count placeholder="Hi, thanks for reaching out! I'm Becky from XX. How can I help you today?" />
        <div class="sc__label mt-4">发送文件（可选）</div>
        <Select
          v-model:value="opening.fileIds"
          mode="multiple"
          @change="(v: any) => Array.isArray(v) && v.length > 2 && (opening!.fileIds = v.slice(0, 2))"
          class="w-full"
          placeholder="选择企业云盘文件"
          :options="cloudFiles.map((f) => ({ value: f.id, label: f.name }))"
          :not-found-content="'企业云盘还没有文件，先到「设置 → 云盘文件」上传'"
        />
        <div class="mt-1 text-xs text-gray-400">问候与文件合计最多 3 条消息，按问候、文件的顺序分别发送（WhatsApp / Facebook）</div>
      </div>
      <div class="sc__block">
        <div class="sc__head">文案兜底回复</div>
        <p class="sc__tip">AI 判断需要转人工、不能直接回复时，先发给客户的话术；留空则不发</p>
        <Textarea v-model:value="opening.fallback" :rows="3" :maxlength="1000" show-count placeholder="Hi, nice to reach out, I'm Darry and will get back to you shortly." />
      </div>
      <div class="flex gap-3">
        <Button @click="opening = clone(settings!.opening)">取消</Button>
        <Button type="primary" @click="saveOpening">保存配置</Button>
      </div>
    </div>

    <div v-else-if="tab === 'boundary'" class="sc__embed"><Forbidden /></div>
  </div>
</template>

<style scoped>
.sc {
  padding: 8px 16px 18px;
}

.sc__tabs :deep(.ant-tabs-nav) {
  margin-bottom: 14px;
}

.sc__body {
  max-width: 780px;
}

.sc__block {
  padding: 16px;
  margin-bottom: 16px;
  border: 1px solid #eef0f3;
  border-radius: 10px;
}

.sc__head {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.sc__no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  font-size: 11px;
  color: #fff;
  background: #155dfc;
  border-radius: 50%;
}

.sc__tip {
  margin: 4px 0 12px;
  font-size: 12px;
  color: #6b7280;
}

.sc__label {
  margin-bottom: 6px;
  font-size: 13px;
  color: #374151;
}

.sc__embed :deep(.p-5),
.sc__embed :deep(.p-4) {
  padding: 0;
}
</style>
