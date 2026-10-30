<script lang="ts" setup>
/** 角色人设：数字员工名称与性格、角色定位与职责、默认工作语言（与原网站一致），改动自动保存 */
import type { ChannelAiApi } from '#/api/channel/ai';

import { computed, onMounted, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Input, message, Popover, Select, Textarea } from 'ant-design-vue';

import { getAiConfig, saveAiConfig } from '#/api/channel/ai';

import { useAgentSettings } from './use-settings';

const PERSONALITIES = [
  { value: 'professional', label: '专业严谨', desc: '用词准确、条理清楚' },
  { value: 'concise', label: '高效简洁', desc: '直奔主题、少寒暄' },
  { value: 'friendly', label: '温和友好', desc: '语气亲切' },
  { value: 'proactive', label: '积极推进', desc: '主动提出寄样、报价等下一步' },
  { value: 'calm', label: '冷静专业', desc: '客户情绪激动时保持克制' },
];
const LANGUAGES = [
  ['English', '英语'],
  ['中文', '简体中文'],
  ['Español', '西班牙语'],
  ['Français', '法语'],
  ['Deutsch', '德语'],
  ['Português', '葡萄牙语'],
  ['Italiano', '意大利语'],
  ['日本語', '日语'],
  ['한국어', '韩语'],
  ['Русский', '俄语'],
  ['العربية', '阿拉伯语'],
].map(([value, label]) => ({ value, label }));

const { settings, load } = useAgentSettings();
const cfg = ref<ChannelAiApi.Config>({});
const editingName = ref(false);
const nameDraft = ref('');
const saving = ref(false);

const personality = computed(() => PERSONALITIES.find((p) => p.value === cfg.value.personality) ?? PERSONALITIES[0]!);
const name = computed(() => cfg.value.agentName || '数字员工');

async function persist(patch: Partial<ChannelAiApi.Config>, tip = '已保存') {
  saving.value = true;
  try {
    Object.assign(cfg.value, patch);
    await saveAiConfig({ ...cfg.value, llmApiKey: '' });
    if (tip) message.success(tip);
  } finally {
    saving.value = false;
  }
}

function startName() {
  nameDraft.value = cfg.value.agentName || '';
  editingName.value = true;
}

async function saveName() {
  editingName.value = false;
  const v = nameDraft.value.trim();
  if (v && v !== cfg.value.agentName) await persist({ agentName: v });
}

let timer: any;
function onDuty(v: string) {
  cfg.value.extraInstructions = v;
  clearTimeout(timer);
  timer = setTimeout(() => persist({ extraInstructions: v }, ''), 800);
}

onMounted(async () => {
  cfg.value = await getAiConfig();
  load().catch(() => {});
});
</script>

<template>
  <div class="ps">
    <h2 class="ps__title">角色人设</h2>

    <div class="ps__card">
      <div class="ps__avatar"><IconifyIcon icon="lucide:bot" class="size-7 text-[#2563EB]" /></div>
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-2">
          <Input
            v-if="editingName"
            v-model:value="nameDraft"
            size="small"
            class="w-40"
            :maxlength="64"
            autofocus
            @blur="saveName"
            @press-enter="saveName"
          />
          <span v-else class="text-base font-semibold text-gray-900">{{ name }}</span>
          <Popover trigger="click" placement="bottom">
            <template #content>
              <div class="w-56">
                <div
                  v-for="p in PERSONALITIES"
                  :key="p.value"
                  class="ps__option"
                  :class="{ on: p.value === personality.value }"
                  @click="persist({ personality: p.value })"
                >
                  <div class="text-sm">{{ p.label }}</div>
                  <div class="text-xs text-gray-400">{{ p.desc }}</div>
                </div>
              </div>
            </template>
            <span class="ps__tag">{{ personality.label }}</span>
          </Popover>
          <IconifyIcon v-if="!editingName" icon="lucide:pencil" class="size-3.5 cursor-pointer text-gray-400 hover:text-[#2563EB]" @click="startName" />
        </div>
        <div class="mt-1 text-xs leading-5 text-gray-500">
          所属行业：{{ settings?.company?.industry || '未填写（在「知识库」上方的企业信息里填写）' }}
        </div>
      </div>
    </div>

    <div class="ps__label">角色定位与职责</div>
    <Textarea
      :value="cfg.extraInstructions"
      :rows="6"
      :maxlength="500"
      show-count
      placeholder="例如：你代表 XX 公司，是专业的 ODM/OEM 供应商。接待时先回应客户当前问题，再按需了解采购条件；依据企业资料逐步推进至报价、寄样。"
      @update:value="onDuty"
    />

    <div class="ps__label mt-6">默认工作语言</div>
    <Select :value="cfg.defaultLanguage || 'English'" class="w-48" :options="LANGUAGES" @change="(v: any) => persist({ defaultLanguage: String(v) })" />
    <p class="mt-3 text-xs leading-5 text-gray-500">
      {{ name }} 精通 92 种语言，会使用客户的语言进行回复；<br />仅当无法识别客户语言时，使用{{ LANGUAGES.find((l) => l.value === (cfg.defaultLanguage || 'English'))?.label }}回复。
    </p>
  </div>
</template>

<style scoped>
.ps {
  max-width: 760px;
  padding: 18px 16px;
}

.ps__title {
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 700;
  color: #111827;
}

.ps__card {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  margin-bottom: 18px;
}

.ps__avatar {
  display: flex;
  flex: 0 0 48px;
  align-items: center;
  justify-content: center;
  height: 48px;
  background: #f9fafb;
  border-radius: 50%;
}

.ps__tag {
  padding: 0 6px;
  font-size: 11px;
  line-height: 18px;
  color: #7c3aed;
  cursor: pointer;
  background: #f3e8ff;
  border-radius: 4px;
}

.ps__option {
  padding: 6px 8px;
  cursor: pointer;
  border-radius: 6px;
}

.ps__option:hover,
.ps__option.on {
  background: #F3F4F6;
}

.ps__label {
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}
</style>
