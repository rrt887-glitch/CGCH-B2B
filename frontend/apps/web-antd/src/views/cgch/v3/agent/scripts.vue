<script lang="ts" setup>
/**
 * 步骤 4 · 教话术：
 * 苹果胶囊分段导航（学习真人销售 / 成交案例库 / 固定开场白 / 言语边界），
 * 支持按成员账号与渠道精细圈选样本对话并一键提炼企业专属销售风格。
 */
import type {
  AgentApi,
  LearnChannel,
  LearnConversation,
  LearnMember,
} from '#/api/channel/agent';
import type { CloudApi } from '#/api/channel/cloud';

import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import {
  Button,
  Checkbox,
  InputNumber,
  message,
  Select,
  Switch,
  Textarea,
} from 'ant-design-vue';

import {
  getLearnConversations,
  getLearnScope,
  learnNow,
} from '#/api/channel/agent';
import { getEnterpriseFiles } from '#/api/channel/cloud';
import { fmtDateTime } from '#/utils/datetime';
import { crmAvatarBg, crmInitial } from '#/views/cgch/v3/crm/components/shared';

import { clone, useAgentSettings } from './use-settings';

const Cases = defineAsyncComponent(
  () => import('#/views/cgch/agent/cases/index.vue'),
);
const Forbidden = defineAsyncComponent(
  () => import('#/views/cgch/agent/forbidden/index.vue'),
);

const { settings, load, save } = useAgentSettings();
const tab = ref<'boundary' | 'faq' | 'learn' | 'opening'>('learn');

const NAV_TABS = [
  { key: 'learn' as const, label: '学习真人销售', icon: 'lucide:graduation-cap' },
  { key: 'faq' as const, label: '成交案例库', icon: 'lucide:trophy' },
  { key: 'opening' as const, label: '固定开场白', icon: 'lucide:hand' },
  { key: 'boundary' as const, label: '言语边界', icon: 'lucide:shield-ban' },
];

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
    ensureScope();
    if (learn.value.scope === 'some')
      Promise.resolve().then(loadScope).catch(() => {});
    style.value = s.salesStyle;
    opening.value = { ...clone(s.opening), fileIds: s.opening.fileIds ?? [] };
  },
  { immediate: true },
);

const PLATFORM_LABEL: Record<string, string> = {
  whatsapp: 'WhatsApp',
  messenger: 'Facebook',
  instagram: 'Instagram',
  web: '官网客服',
  mail: '邮箱',
};

const members = ref<LearnMember[]>([]);
const activeMember = ref<number>();
const convs = ref<LearnConversation[]>([]);
const currentMember = computed(() =>
  members.value.find((m) => m.userId === activeMember.value),
);
const isPicked = (c: LearnChannel) =>
  (c.type === 'wa'
    ? learn.value!.waAccountIds
    : learn.value!.mailAccountIds
  ).includes(c.id);
const pickedMembers = computed(
  () => members.value.filter((m) => m.channels.some(isPicked)).length,
);

function ensureScope() {
  const l = learn.value!;
  l.scope ??= 'all';
  l.waAccountIds ??= [];
  l.mailAccountIds ??= [];
  l.conversations ??= [];
  l.goals ??= [];
}

async function loadScope() {
  if (!members.value.length) {
    members.value = await getLearnScope();
    activeMember.value ??= members.value[0]?.userId;
  }
  await loadConvs();
}

async function loadConvs() {
  const l = learn.value!;
  convs.value =
    l.waAccountIds.length || l.mailAccountIds.length
      ? await getLearnConversations(l.waAccountIds, l.mailAccountIds, l.days)
      : [];
  const keys = new Set(convs.value.map((c) => c.key));
  l.conversations = l.conversations.filter((k) => keys.has(k));
}

async function setScope(v: 'all' | 'some') {
  ensureScope();
  learn.value!.scope = v;
  learnDirty.value = true;
  if (v === 'some') await loadScope();
}

async function pickChannel(c: LearnChannel, on: boolean) {
  const list =
    c.type === 'wa' ? learn.value!.waAccountIds : learn.value!.mailAccountIds;
  const i = list.indexOf(c.id);
  if (on && i < 0) list.push(c.id);
  if (!on && i >= 0) list.splice(i, 1);
  learnDirty.value = true;
  await loadConvs();
}

function pickConv(key: string, on: boolean) {
  const l = learn.value!.conversations;
  const i = l.indexOf(key);
  if (on && i < 0) l.push(key);
  if (!on && i >= 0) l.splice(i, 1);
  learnDirty.value = true;
}

function toggleGoal(goal: string) {
  const g = learn.value!.goals;
  const i = g.indexOf(goal);
  if (i >= 0) g.splice(i, 1);
  else g.push(goal);
  learnDirty.value = true;
}

async function saveLearn() {
  if (
    learn.value?.scope === 'some' &&
    !learn.value.waAccountIds.length &&
    !learn.value.mailAccountIds.length
  ) {
    message.warning('请选择要学习的账号和渠道');
    throw new Error('scope');
  }
  await save({ learn: learn.value });
  learnDirty.value = false;
}

async function toggleLearn(v: boolean) {
  learn.value!.enabled = v;
  await save({ learn: learn.value }, v ? '已启用真人话术学习' : '已停用');
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
    message.success(`已从 ${r.samples} 段人工对话中提炼话术要点`);
  } finally {
    learning.value = false;
  }
}

const styleDirty = computed(
  () => settings.value && style.value !== settings.value.salesStyle,
);

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
    <header class="sc__page-head">
      <h2 class="sc__title">教话术</h2>
      <p class="sc__sub">
        让 AI 萃取优秀业务员的真实沟通习惯、成交案例、固定开场白与合规词红线
      </p>
    </header>

    <!-- 苹果胶囊分段导航 -->
    <div class="sc__nav">
      <button
        v-for="t in NAV_TABS"
        :key="t.key"
        type="button"
        class="sc__nav-btn"
        :class="{ on: tab === t.key }"
        @click="tab = t.key"
      >
        <IconifyIcon :icon="t.icon" class="size-4" />
        <span>{{ t.label }}</span>
      </button>
    </div>

    <!-- Tab 1：学习真人销售 -->
    <div v-if="tab === 'learn' && learn" class="sc__card">
      <!-- 主开关横幅 -->
      <div class="sc__master">
        <div class="flex min-w-0 items-center gap-3">
          <span class="sc__master-icon is-purple">
            <IconifyIcon icon="lucide:graduation-cap" class="size-5" />
          </span>
          <div class="min-w-0">
            <div class="flex items-center gap-2">
              <span class="text-[15px] font-bold text-[#1D1D1F]">
                从历史真人对话提炼销售风格
              </span>
              <span
                class="ap-crm-pill"
                :class="
                  learn.enabled ? 'ap-crm-pill--green' : 'ap-crm-pill--gray'
                "
              >
                {{ learn.enabled ? '已启用' : '未启用' }}
              </span>
            </div>
            <div class="mt-0.5 text-xs text-[#6E6E73]">
              学习结果对整个企业生效，AI 回复买家时会自动融合提炼出的挖需节奏与语气习惯
            </div>
          </div>
        </div>
        <Switch
          :checked="learn.enabled"
          @change="(v: any) => toggleLearn(!!v)"
        />
      </div>

      <!-- 1. 学习范围 -->
      <div class="sc__block">
        <div class="sc__head">
          <span class="sc__no">1</span>
          <span>选择样本数据范围</span>
        </div>
        <p class="sc__tip">指定从哪些销售账号和渠道的历史人工对话中抽取优秀样本</p>

        <div class="sc__scope">
          <button
            type="button"
            class="sc__scope-card"
            :class="{ on: (learn.scope || 'all') === 'all' }"
            @click="setScope('all')"
          >
            <div class="flex items-center justify-between">
              <span class="font-semibold text-[#1D1D1F]">
                全部账号 · 全部渠道（默认）
              </span>
              <span class="sc__radio"></span>
            </div>
            <div class="mt-1 text-xs text-[#6E6E73]">
              综合学习企业下全部已接入渠道的人工销售对话
            </div>
          </button>

          <button
            type="button"
            class="sc__scope-card"
            :class="{ on: learn.scope === 'some' }"
            @click="setScope('some')"
          >
            <div class="flex items-center justify-between">
              <span class="font-semibold text-[#1D1D1F]">
                指定销冠账号 · 指定渠道
              </span>
              <span class="sc__radio"></span>
            </div>
            <div class="mt-1 text-xs text-[#6E6E73]">
              只挑选表现最优秀的业务员账号与高质量会话作为标杆样本
            </div>
          </button>
        </div>

        <!-- 三栏级联选择器 -->
        <div v-if="learn.scope === 'some'" class="sc__picker">
          <div class="sc__col">
            <div class="sc__col-head">
              <span>1. 成员账号</span>
              <small>已选 {{ pickedMembers }}</small>
            </div>
            <div v-if="!members.length" class="sc__empty">
              暂无已接入渠道的成员
            </div>
            <div
              v-for="m in members"
              :key="m.userId"
              class="sc__item"
              :class="{ on: activeMember === m.userId }"
              @click="activeMember = m.userId"
            >
              <span
                class="ap-crm-avatar sc__mini-avatar"
                :style="{ background: crmAvatarBg(m.name) }"
              >
                {{ crmInitial(m.name) }}
              </span>
              <span class="flex-1 truncate font-medium">{{ m.name }}</span>
              <span class="text-[11px] text-[#8E8E93] tabular-nums">
                {{ m.channels.filter(isPicked).length }}/{{ m.channels.length }}
              </span>
            </div>
          </div>

          <div class="sc__col">
            <div class="sc__col-head">
              <span>2. 沟通渠道</span>
              <small>
                已选 {{ learn.waAccountIds.length + learn.mailAccountIds.length }}
              </small>
            </div>
            <div v-if="!currentMember" class="sc__empty">
              点击左侧成员查看渠道
            </div>
            <label
              v-for="c in currentMember?.channels ?? []"
              :key="`${c.type}${c.id}`"
              class="sc__item"
            >
              <Checkbox
                :checked="isPicked(c)"
                @change="(e: any) => pickChannel(c, !!e.target.checked)"
              />
              <span class="sc__ptag">
                {{ PLATFORM_LABEL[c.platform] ?? c.platform }}
              </span>
              <span class="flex-1 truncate">{{ c.label }}</span>
            </label>
          </div>

          <div class="sc__col">
            <div class="sc__col-head">
              <span>3. 指定会话</span>
              <small>
                {{
                  learn.conversations.length
                    ? `已选 ${learn.conversations.length}`
                    : '不勾选默认全部'
                }}
              </small>
            </div>
            <div
              v-if="!learn.waAccountIds.length && !learn.mailAccountIds.length"
              class="sc__empty"
            >
              请先勾选左侧成员的沟通渠道
            </div>
            <div v-else-if="!convs.length" class="sc__empty">
              最近 {{ learn.days }} 天暂无人工回复会话
            </div>
            <label v-for="c in convs" :key="c.key" class="sc__item">
              <Checkbox
                :checked="learn.conversations.includes(c.key)"
                @change="(e: any) => pickConv(c.key, !!e.target.checked)"
              />
              <div class="min-w-0 flex-1">
                <div class="truncate text-xs font-medium text-[#1D1D1F]">
                  <span class="sc__ptag">{{ c.channel }}</span>{{ c.title }}
                </div>
                <div class="truncate text-[11px] text-[#8E8E93]">
                  {{ c.preview }}
                </div>
              </div>
            </label>
          </div>
        </div>

        <div
          v-if="learn.scope === 'some'"
          class="mt-2.5 text-xs text-[#6E6E73]"
        >
          当前范围：<b>{{ pickedMembers }}</b> 个成员账号 ·
          <b>{{ learn.waAccountIds.length + learn.mailAccountIds.length }}</b>
          个渠道{{
            learn.conversations.length
              ? ` · ${learn.conversations.length} 段精选会话`
              : ''
          }}
        </div>
      </div>

      <!-- 2. 学习内容与质量 -->
      <div class="sc__block">
        <div class="sc__head">
          <span class="sc__no">2</span>
          <span>学习周期与样本过滤</span>
        </div>
        <p class="sc__tip">控制时间跨度、提炼维度与高质量双向对话过滤条件</p>

        <div class="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-xl bg-white p-3.5">
          <div>
            <div class="text-[13px] font-semibold text-[#1D1D1F]">回溯时间窗口</div>
            <div class="text-xs text-[#86868B]">
              每次按最近 N 天的对话重新提炼，过时旧样本自然淘汰
            </div>
          </div>
          <div class="flex items-center gap-2 text-sm text-[#1D1D1F]">
            <span>最近</span>
            <InputNumber
              v-model:value="learn.days"
              :min="1"
              :max="365"
              class="w-24"
              @change="learnDirty = true"
            />
            <span>天</span>
          </div>
        </div>

        <div class="mb-4">
          <div class="sc__label">提炼维度</div>
          <div class="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
            <button
              type="button"
              class="sc__goal-card"
              :class="{ on: learn.goals.includes('needs') }"
              @click="toggleGoal('needs')"
            >
              <div class="flex items-center justify-between">
                <span class="font-semibold text-[#1D1D1F]">挖需与推进策略</span>
                <IconifyIcon
                  :icon="
                    learn.goals.includes('needs')
                      ? 'lucide:check-circle-2'
                      : 'lucide:circle'
                  "
                  class="size-4"
                  :class="
                    learn.goals.includes('needs')
                      ? 'text-[#007AFF]'
                      : 'text-[#C7C7CC]'
                  "
                />
              </div>
              <div class="mt-1 text-xs text-[#6E6E73]">
                学习业务员如何识别意向、追问采购量并推进报价寄样
              </div>
            </button>

            <button
              type="button"
              class="sc__goal-card"
              :class="{ on: learn.goals.includes('style') }"
              @click="toggleGoal('style')"
            >
              <div class="flex items-center justify-between">
                <span class="font-semibold text-[#1D1D1F]">沟通语气与句式习惯</span>
                <IconifyIcon
                  :icon="
                    learn.goals.includes('style')
                      ? 'lucide:check-circle-2'
                      : 'lucide:circle'
                  "
                  class="size-4"
                  :class="
                    learn.goals.includes('style')
                      ? 'text-[#007AFF]'
                      : 'text-[#C7C7CC]'
                  "
                />
              </div>
              <div class="mt-1 text-xs text-[#6E6E73]">
                模仿团队常用的称呼礼仪、段落长短与专业表达风格
              </div>
            </button>
          </div>
        </div>

        <div class="flex items-center justify-between gap-3 rounded-xl bg-white p-3.5">
          <div>
            <div class="text-[13px] font-semibold text-[#1D1D1F]">
              仅学习获得买家回复的双向对话
            </div>
            <div class="text-xs text-[#86868B]">
              自动过滤石沉大海的单向开发信，确保样本具备真实互动价值
            </div>
          </div>
          <Switch
            :checked="learn.repliedOnly"
            @change="
              (v: any) => {
                learn!.repliedOnly = !!v;
                learnDirty = true;
              }
            "
          />
        </div>
      </div>

      <!-- 操作按钮组 -->
      <div class="flex flex-wrap items-center gap-3">
        <Button shape="round" :disabled="!learnDirty" @click="saveLearn">
          保存范围设置
        </Button>
        <Button
          type="primary"
          shape="round"
          :loading="learning"
          :disabled="!learn.enabled"
          @click="runLearn"
        >
          <IconifyIcon icon="lucide:sparkles" class="mr-1 inline size-4" />
          立即提炼话术
        </Button>
        <span
          v-if="learn.lastLearnTime"
          class="ap-crm-pill ap-crm-pill--gray"
        >
          上次学习：{{ fmtDateTime(learn.lastLearnTime) }} · 共
          {{ learn.lastSamples }} 段对话
        </span>
      </div>

      <!-- 3. 学到的话术要点 -->
      <div class="sc__block mt-5 mb-0">
        <div class="flex flex-wrap items-center justify-between gap-2 mb-2">
          <div>
            <div class="sc__head">已提炼的企业专属话术要点</div>
            <p class="sc__tip mb-0">
              AI 回复客户时将严格参考以下要点；您可以直接手动润色（重新点击「立即提炼」会覆盖）。
            </p>
          </div>
          <Button
            type="primary"
            size="small"
            shape="round"
            :disabled="!styleDirty"
            @click="save({ salesStyle: style })"
          >
            保存手动修改
          </Button>
        </div>
        <Textarea
          v-model:value="style"
          :rows="7"
          :maxlength="3000"
          show-count
          placeholder="尚未提炼话术，开启上方开关并点击「立即提炼话术」即可自动总结…"
        />
      </div>
    </div>

    <!-- Tab 2：成交案例库 -->
    <div v-else-if="tab === 'faq'" class="sc__card">
      <Cases />
    </div>

    <!-- Tab 3：固定开场白 -->
    <div v-else-if="tab === 'opening' && opening" class="sc__card">
      <div class="sc__master">
        <div class="flex min-w-0 items-center gap-3">
          <span class="sc__master-icon is-blue">
            <IconifyIcon icon="lucide:hand" class="size-5" />
          </span>
          <div>
            <div class="flex items-center gap-2">
              <span class="text-[15px] font-bold text-[#1D1D1F]">
                新买家首轮接触固定开场白
              </span>
              <span
                class="ap-crm-pill"
                :class="
                  opening.enabled ? 'ap-crm-pill--green' : 'ap-crm-pill--gray'
                "
              >
                {{ opening.enabled ? '已启用' : '未启用' }}
              </span>
            </div>
            <div class="mt-0.5 text-xs text-[#6E6E73]">
              新买家首次发来咨询时，先发送标准品牌问候与宣传册，再针对具体问题作答
            </div>
          </div>
        </div>
        <Switch
          :checked="opening.enabled"
          @change="(v: any) => (opening!.enabled = !!v)"
        />
      </div>

      <div class="sc__block">
        <div class="sc__head">首轮问候语与随附资料</div>
        <p class="sc__tip">
          问候语与附件按顺序分条发出（合计最多 3 条消息，适用于 WhatsApp / Facebook 等即时会话）
        </p>

        <div class="sc__label">问候文案</div>
        <Textarea
          v-model:value="opening.greeting"
          :rows="4"
          :maxlength="1000"
          show-count
          placeholder="Hi, thanks for reaching out! I'm Becky from XX. How can I help you today?"
        />

        <div class="sc__label mt-4">随附企业云盘文件（选填，最多 2 份）</div>
        <Select
          v-model:value="opening.fileIds"
          mode="multiple"
          class="w-full"
          placeholder="选择首次接待自动附带的产品画册或公司简介 PDF…"
          :options="cloudFiles.map((f) => ({ value: f.id, label: f.name }))"
          :not-found-content="'企业云盘暂无文件，请先前往「设置 → 云盘文件」上传'"
          @change="
            (v: any) =>
              Array.isArray(v) &&
              v.length > 2 &&
              (opening!.fileIds = v.slice(0, 2))
          "
        />
      </div>

      <div class="sc__block">
        <div class="sc__head">转人工礼貌安抚话术</div>
        <p class="sc__tip">
          当 AI 判断问题超出权限需转交真人业务员时，先向买家发送的安抚提示（留空则静默转交不发消息）
        </p>
        <Textarea
          v-model:value="opening.fallback"
          :rows="3"
          :maxlength="1000"
          show-count
          placeholder="Thanks for the details! Let me loop in our senior sales manager to prepare an accurate quote for you shortly."
        />
      </div>

      <div class="flex justify-end gap-2.5">
        <Button shape="round" @click="opening = clone(settings!.opening)">
          重置更改
        </Button>
        <Button type="primary" shape="round" @click="saveOpening">
          保存开场白配置
        </Button>
      </div>
    </div>

    <!-- Tab 4：言语边界 -->
    <div v-else-if="tab === 'boundary'" class="sc__card">
      <Forbidden />
    </div>
  </div>
</template>

<style scoped>
.sc {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sc__page-head {
  padding: 2px 2px 0;
}

.sc__title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #1d1d1f;
  letter-spacing: -0.015em;
}

.sc__sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: #6e6e73;
}

.sc__nav {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  align-self: flex-start;
  padding: 4px;
  background: rgb(255 255 255 / 72%);
  border-radius: 13px;
  box-shadow: var(--ap-shadow-rest);
  backdrop-filter: blur(20px);
}

.sc__nav-btn {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  height: 34px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 500;
  color: #6e6e73;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 10px;
  transition: all var(--ap-dur-fast);
}

.sc__nav-btn:hover:not(.on) {
  color: #1d1d1f;
  background: rgb(0 0 0 / 3.5%);
}

.sc__nav-btn.on {
  font-weight: 600;
  color: #1d1d1f;
  background: #fff;
  box-shadow: 0 2px 6px rgb(0 0 0 / 8%);
}

.sc__card {
  padding: 20px 22px;
  background: #fff;
  border-radius: 18px;
  box-shadow: var(--ap-shadow-rest);
}

.sc__master {
  display: flex;
  gap: 14px;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 16px;
  margin-bottom: 16px;
  border-bottom: 0.5px solid var(--ap-separator);
}

.sc__master-icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 12px;
}

.sc__master-icon.is-purple {
  color: #7c3aed;
  background: #f3eeff;
}

.sc__master-icon.is-blue {
  color: #007aff;
  background: #e8f2ff;
}

.sc__block {
  padding: 16px 18px;
  margin-bottom: 16px;
  background: #f7f8fb;
  border-radius: 15px;
}

.sc__head {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 14.5px;
  font-weight: 700;
  color: #1d1d1f;
}

.sc__no {
  display: inline-grid;
  place-items: center;
  width: 20px;
  height: 20px;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  background: #007aff;
  border-radius: 50%;
}

.sc__tip {
  margin: 4px 0 12px;
  font-size: 12px;
  color: #6e6e73;
}

.sc__label {
  margin-bottom: 6px;
  font-size: 12.5px;
  font-weight: 600;
  color: #3a3a3c;
}

.sc__scope {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.sc__scope-card,
.sc__goal-card {
  padding: 12px 14px;
  text-align: left;
  cursor: pointer;
  background: #fff;
  border: 1.5px solid transparent;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgb(0 0 0 / 4%);
  transition: all var(--ap-dur-fast);
}

.sc__scope-card.on,
.sc__goal-card.on {
  background: rgb(0 122 255 / 5%);
  border-color: #007aff;
}

.sc__radio {
  width: 16px;
  height: 16px;
  border: 1.5px solid #c7c7cc;
  border-radius: 50%;
}

.sc__scope-card.on .sc__radio {
  border-color: #007aff;
  border-width: 5px;
}

/* 三栏挑选器 */
.sc__picker {
  display: grid;
  grid-template-columns: 1fr 1fr 1.35fr;
  gap: 10px;
  margin-top: 12px;
}

.sc__col {
  min-height: 190px;
  max-height: 300px;
  padding: 10px;
  overflow-y: auto;
  background: #fff;
  border-radius: 12px;
  box-shadow: inset 0 0 0 0.5px rgb(0 0 0 / 6%);
}

.sc__col-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px 8px;
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 700;
  color: #1d1d1f;
  border-bottom: 0.5px solid var(--ap-separator);
}

.sc__col-head small {
  font-size: 11px;
  font-weight: 500;
  color: #8e8e93;
}

.sc__empty {
  padding: 36px 8px;
  font-size: 12px;
  color: #aeaeb2;
  text-align: center;
}

.sc__item {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 7px 8px;
  margin-bottom: 2px;
  font-size: 12.5px;
  cursor: pointer;
  border-radius: 9px;
  transition: background-color var(--ap-dur-fast);
}

.sc__item:hover {
  background: #f5f6fa;
}

.sc__item.on {
  color: #007aff;
  background: #e8f2ff;
}

.sc__mini-avatar {
  width: 24px;
  height: 24px;
  font-size: 11px;
}

.sc__ptag {
  flex: none;
  padding: 1px 6px;
  margin-right: 4px;
  font-size: 10.5px;
  font-weight: 600;
  color: #5856d6;
  background: #ededfc;
  border-radius: 5px;
}

@media (width <= 900px) {
  .sc__picker,
  .sc__scope {
    grid-template-columns: 1fr;
  }
}
</style>
