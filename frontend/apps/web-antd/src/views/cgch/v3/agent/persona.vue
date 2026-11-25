<script lang="ts" setup>
/**
 * 角色人设（Apple Intelligence / macOS 系统设置卡片风格）：
 * 1. 数字员工身份名片 + 5 枚可视化性格卡片直接点选（改动自动保存）
 * 2. 角色定位与职责（带一键填充行业模板胶囊 + 内置字数统计）
 * 3. 默认工作语言（iOS 分组设置行）
 */
import type { ChannelAiApi } from '#/api/channel/ai';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import { Input, message, Select, Textarea } from 'ant-design-vue';

import { getAiConfig, saveAiConfig } from '#/api/channel/ai';

import { useAgentSettings } from './use-settings';

const router = useRouter();

const PERSONALITIES = [
  { value: 'professional', label: '专业严谨', desc: '用词准确、条理清楚', icon: 'lucide:shield-check' },
  { value: 'concise', label: '高效简洁', desc: '直奔主题、少寒暄', icon: 'lucide:zap' },
  { value: 'friendly', label: '温和友好', desc: '语气亲切、拉近距离', icon: 'lucide:smile' },
  { value: 'proactive', label: '积极推进', desc: '主动推进寄样与报价', icon: 'lucide:trending-up' },
  { value: 'calm', label: '冷静专业', desc: '客诉场景保持克制', icon: 'lucide:compass' },
];

const TEMPLATES = [
  {
    label: 'ODM / OEM 工厂代表',
    text: '你代表本公司担任海外销售顾问，专注 ODM/OEM 定制生产。先准确回应买家当前问题，再顺势了解目标采购量、定制要求与目的港，逐步推进寄样与正式报价。',
  },
  {
    label: '外贸现货批发顾问',
    text: '你是资深外贸批发业务员，熟悉产品目录、MOQ、阶梯价格与交期。回答简明专业，优先提供现货规格与起订量，主动询问采购数量以便核算最优运费。',
  },
  {
    label: '技术型解决方案销售',
    text: '你是懂技术参数与行业认证的海外销售工程师。先解答客户关于规格、认证、兼容性的技术疑问，建立专业信任，再引导发送详细参数表或安排样品测试。',
  },
];

const LANGUAGES = [
  ['English', '英语 (English)'],
  ['中文', '简体中文'],
  ['Español', '西班牙语 (Español)'],
  ['Français', '法语 (Français)'],
  ['Deutsch', '德语 (Deutsch)'],
  ['Português', '葡萄牙语 (Português)'],
  ['Italiano', '意大利语 (Italiano)'],
  ['日本語', '日语 (日本語)'],
  ['한국어', '韩语 (한국어)'],
  ['Русский', '俄语 (Русский)'],
  ['العربية', '阿拉伯语 (العربية)'],
].map(([value, label]) => ({ value, label }));

const { settings, load } = useAgentSettings();
const cfg = ref<ChannelAiApi.Config>({});
const editingName = ref(false);
const nameDraft = ref('');
const saving = ref(false);

const personality = computed(
  () =>
    PERSONALITIES.find((p) => p.value === cfg.value.personality) ??
    PERSONALITIES[0]!,
);
const name = computed(() => cfg.value.agentName || 'Sales Assistant');
const dutyLen = computed(() => (cfg.value.extraInstructions || '').length);

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
  nameDraft.value = cfg.value.agentName || name.value;
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
  timer = setTimeout(() => persist({ extraInstructions: v }, ''), 700);
}

function applyTemplate(t: string) {
  cfg.value.extraInstructions = t;
  persist({ extraInstructions: t }, '已套用模板');
}

onMounted(async () => {
  cfg.value = await getAiConfig();
  load().catch(() => {});
});
</script>

<template>
  <div class="ps">
    <header class="ps__head">
      <div>
        <h2 class="ps__title">角色人设</h2>
        <p class="ps__sub">定义数字员工对外接待时的身份、沟通语气与核心职责，修改后自动生效</p>
      </div>
      <span class="ps__autosave">
        <span class="ps__autosave-dot" :class="{ 'is-saving': saving }"></span>
        {{ saving ? '保存中…' : '自动保存' }}
      </span>
    </header>

    <!-- 卡片 1：身份名片 + 沟通性格 -->
    <section class="ps-card">
      <div class="ps-hero">
        <div class="ps-avatar">
          <IconifyIcon icon="lucide:sparkles" class="size-6" />
        </div>
        <div class="min-w-0 flex-1">
          <div class="flex flex-wrap items-center gap-2">
            <Input
              v-if="editingName"
              v-model:value="nameDraft"
              size="small"
              class="w-48"
              :maxlength="64"
              autofocus
              @blur="saveName"
              @press-enter="saveName"
            />
            <template v-else>
              <span class="ps-name">{{ name }}</span>
              <button
                type="button"
                class="ps-edit-btn"
                title="修改对外自称"
                @click="startName"
              >
                <IconifyIcon icon="lucide:pencil" class="size-3.5" />
                <span>改名</span>
              </button>
            </template>
          </div>
          <div class="ps-industry">
            <span>所属行业：</span>
            <span v-if="settings?.company?.industry" class="font-medium text-[#1D1D1F]">
              {{ settings.company.industry }}
            </span>
            <button
              v-else
              type="button"
              class="ps-link"
              @click="router.replace({ query: { tab: 'knowledge' } })"
            >
              未填写 · 去「知识库」补充企业信息 ›
            </button>
          </div>
        </div>
      </div>

      <div class="ps-divider"></div>

      <div class="ps-section-label">沟通性格</div>
      <div class="ps-tones">
        <button
          v-for="p in PERSONALITIES"
          :key="p.value"
          type="button"
          class="ps-tone"
          :class="{ 'is-active': p.value === personality.value }"
          @click="persist({ personality: p.value })"
        >
          <div class="ps-tone__top">
            <IconifyIcon :icon="p.icon" class="ps-tone__icon" />
            <span class="ps-tone__name">{{ p.label }}</span>
          </div>
          <div class="ps-tone__desc">{{ p.desc }}</div>
        </button>
      </div>
    </section>

    <!-- 卡片 2：角色定位与职责 -->
    <section class="ps-card">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div>
          <div class="ps-card__title">角色定位与职责</div>
          <div class="ps-card__desc">告诉数字员工代表谁、接待优先级以及推进目标</div>
        </div>
        <span class="ps-count">{{ dutyLen }} / 500</span>
      </div>

      <div class="ps-tpls">
        <span class="ps-tpls__label">快速套用：</span>
        <button
          v-for="t in TEMPLATES"
          :key="t.label"
          type="button"
          class="ps-tpl"
          @click="applyTemplate(t.text)"
        >
          {{ t.label }}
        </button>
      </div>

      <Textarea
        :value="cfg.extraInstructions"
        :rows="5"
        :maxlength="500"
        class="ps-textarea"
        placeholder="例如：你代表 XX 公司，是专业的 ODM/OEM 供应商。接待时先回应客户当前问题，再按需了解采购条件；依据企业资料逐步推进至报价、寄样。"
        @update:value="onDuty"
      />
    </section>

    <!-- 卡片 3：默认工作语言 -->
    <section class="ps-card ps-card--row">
      <div class="min-w-0 flex-1">
        <div class="ps-card__title">默认工作语言</div>
        <div class="ps-card__desc">
          {{ name }} 精通 92 种语言，会自动按买家来信语种回复；仅当无法识别语种时使用此默认语言。
        </div>
      </div>
      <Select
        :value="cfg.defaultLanguage || 'English'"
        class="w-48 shrink-0"
        :options="LANGUAGES"
        @change="(v: any) => persist({ defaultLanguage: String(v) })"
      />
    </section>
  </div>
</template>

<style scoped>
.ps {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.ps__head {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-end;
  justify-content: space-between;
  padding: 2px 2px 4px;
}

.ps__title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--ap-label);
  letter-spacing: -0.015em;
}

.ps__sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--ap-label-3);
}

.ps__autosave {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 4px 10px;
  font-size: 12px;
  color: var(--ap-label-3);
  background: rgb(255 255 255 / 72%);
  border-radius: 999px;
  box-shadow: inset 0 0 0 0.5px rgb(0 0 0 / 7%);
}

.ps__autosave-dot {
  width: 6px;
  height: 6px;
  background: #34c759;
  border-radius: 50%;
}

.ps__autosave-dot.is-saving {
  background: #ff9500;
}

.ps-card {
  padding: 20px 22px;
  background: var(--ap-surface);
  border-radius: 16px;
  box-shadow: var(--ap-shadow-rest);
}

.ps-card--row {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
}

.ps-hero {
  display: flex;
  gap: 14px;
  align-items: center;
}

.ps-avatar {
  display: grid;
  flex: none;
  place-items: center;
  width: 50px;
  height: 50px;
  color: #fff;
  background: linear-gradient(135deg, #7455e6 0%, #007aff 100%);
  border-radius: 14px;
  box-shadow: 0 4px 14px rgb(92 86 230 / 26%);
}

.ps-name {
  font-size: 17px;
  font-weight: 700;
  color: var(--ap-label);
}

.ps-edit-btn {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 3px 8px;
  font-size: 12px;
  color: var(--ap-label-2);
  cursor: pointer;
  background: #f5f5f7;
  border: 0;
  border-radius: 6px;
  transition:
    background-color var(--ap-dur-fast),
    color var(--ap-dur-fast);
}

.ps-edit-btn:hover {
  color: var(--ap-accent);
  background: var(--ap-accent-soft);
}

.ps-industry {
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--ap-label-3);
}

.ps-link {
  padding: 0;
  font-size: 12.5px;
  color: var(--ap-accent);
  cursor: pointer;
  background: transparent;
  border: 0;
}

.ps-link:hover {
  text-decoration: underline;
}

.ps-divider {
  height: 0.5px;
  margin: 18px 0 14px;
  background: var(--ap-separator);
}

.ps-section-label {
  margin-bottom: 10px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--ap-label-2);
}

.ps-tones {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}

.ps-tone {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 11px 12px;
  text-align: left;
  cursor: pointer;
  background: #f7f7fa;
  border: 1.5px solid transparent;
  border-radius: 12px;
  transition:
    background-color var(--ap-dur-fast),
    border-color var(--ap-dur-fast),
    transform var(--ap-dur-fast) var(--ap-ease),
    box-shadow var(--ap-dur-fast);
}

.ps-tone:hover:not(.is-active) {
  background: #f0f1f5;
}

.ps-tone:active {
  transform: scale(0.98);
}

.ps-tone.is-active {
  background: rgb(0 122 255 / 6%);
  border-color: var(--ap-accent);
  box-shadow: 0 2px 8px rgb(0 122 255 / 10%);
}

.ps-tone__top {
  display: flex;
  gap: 6px;
  align-items: center;
}

.ps-tone__icon {
  flex: none;
  width: 15px;
  height: 15px;
  color: var(--ap-label-3);
}

.ps-tone.is-active .ps-tone__icon {
  color: var(--ap-accent);
}

.ps-tone__name {
  font-size: 13px;
  font-weight: 600;
  color: var(--ap-label);
}

.ps-tone.is-active .ps-tone__name {
  color: var(--ap-accent);
}

.ps-tone__desc {
  font-size: 11.5px;
  line-height: 1.4;
  color: var(--ap-label-3);
}

.ps-card__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ap-label);
}

.ps-card__desc {
  margin-top: 3px;
  font-size: 12.5px;
  line-height: 1.55;
  color: var(--ap-label-3);
}

.ps-count {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--ap-label-3);
}

.ps-tpls {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin: 12px 0 10px;
}

.ps-tpls__label {
  font-size: 12px;
  color: var(--ap-label-3);
}

.ps-tpl {
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 500;
  color: var(--ap-label-2);
  cursor: pointer;
  background: #f5f5f7;
  border: 0;
  border-radius: 999px;
  transition:
    background-color var(--ap-dur-fast),
    color var(--ap-dur-fast);
}

.ps-tpl:hover {
  color: var(--ap-accent);
  background: var(--ap-accent-soft);
}

.ps-textarea {
  padding: 12px 14px !important;
  font-size: 13.5px !important;
  line-height: 1.65 !important;
  background: #f7f7fa !important;
  border-radius: 12px !important;
}

.ps-textarea:focus {
  background: #fff !important;
}

@media (width <= 960px) {
  .ps-tones {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
