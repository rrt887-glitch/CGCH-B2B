<script lang="ts" setup>
/**
 * 步骤 3 · 配技能（macOS 系统设置开关行 + Apple Sheet 配置弹窗）：
 * 顶部三大业务场景卡片带实时启用计数，内部采用「着色图标 + 标题说明 + 右侧 iOS 开关 + 缩进子面板」布局。
 */
import type { AgentApi } from '#/api/channel/agent';
import type { ChannelAiApi } from '#/api/channel/ai';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import {
  Checkbox,
  Input,
  InputNumber,
  message,
  Modal,
  Switch,
} from 'ant-design-vue';

import { getAiConfig, saveAiConfig } from '#/api/channel/ai';

import { clone, useAgentSettings } from './use-settings';

const router = useRouter();
const { settings, load, save } = useAgentSettings();
const skills = computed(() => settings.value?.skills);
const active = ref<'crm' | 'inquiry' | 'research'>('inquiry');
const cfg = ref<ChannelAiApi.Config>({});

const askOn = computed(() => (skills.value?.askFields?.length ?? 0) > 0);

const inquiryCount = computed(() => {
  const s = skills.value;
  if (!s) return '0/4';
  const n =
    (s.sendFiles ? 1 : 0) +
    (s.followUp ? 1 : 0) +
    (s.quote ? 1 : 0) +
    (askOn.value ? 1 : 0);
  return `${n}/4 已启用`;
});

const researchCount = computed(() =>
  skills.value?.autoResearch ? '已启用' : '未启用',
);

const crmCount = computed(() => {
  const n =
    (skills.value?.autoGrade ? 1 : 0) + (cfg.value.leadAutoCreate ? 1 : 0);
  return `${n}/2 已启用`;
});

const SKILLS = computed(() => [
  {
    key: 'inquiry' as const,
    title: '智能询盘接待',
    desc: '自动发资料、跟进、报价与追问',
    icon: 'lucide:message-square-heart',
    tint: 'blue',
    badge: inquiryCount.value,
  },
  {
    key: 'research' as const,
    title: '自动背调客户',
    desc: '新线索进线毫秒级核验背景',
    icon: 'lucide:radar',
    tint: 'purple',
    badge: researchCount.value,
  },
  {
    key: 'crm' as const,
    title: 'CRM 自动录入',
    desc: '自动建档、提取需求与客户评级',
    icon: 'lucide:database-zap',
    tint: 'green',
    badge: crmCount.value,
  },
]);

const GRADE_COLORS = [
  'ap-crm-pill--green',
  'ap-crm-pill--blue',
  'ap-crm-pill--amber',
  'ap-crm-pill--gray',
];

async function patch(p: Partial<AgentApi.Skills>) {
  if (!skills.value) return;
  const next = { ...clone(skills.value), ...p };
  await save({ skills: next });
}

// ===== 允许联网报价 =====
const searchOpen = ref(false);
const searchForm = ref({ baseUrl: '', apiKey: '' });

function openSearch() {
  searchForm.value = {
    baseUrl: settings.value?.webSearch?.baseUrl ?? '',
    apiKey: '',
  };
  searchOpen.value = true;
}

async function toggleWebQuote(v: boolean) {
  await patch({ webQuote: v });
  if (v && !settings.value?.webSearch?.hasKey) openSearch();
}

async function saveSearch() {
  const hasKey =
    !!settings.value?.webSearch?.hasKey || !!searchForm.value.apiKey.trim();
  if (!hasKey) {
    message.warning('请填写联网搜索的 API Key');
    return;
  }
  await save({
    webSearch: {
      baseUrl: searchForm.value.baseUrl.trim(),
      apiKey: searchForm.value.apiKey.trim(),
    },
  });
  if (settings.value)
    settings.value.webSearch = {
      baseUrl: searchForm.value.baseUrl.trim(),
      hasKey: true,
    };
  searchOpen.value = false;
}

// ===== 主动追问 =====
const askOpen = ref(false);
const askDraft = ref<string[]>([]);

function toggleAsk(v: boolean) {
  patch({
    askFields: v ? (settings.value?.askFieldOptions ?? []).slice(0, 3) : [],
  });
}

function editAsk() {
  askDraft.value = [...(skills.value?.askFields ?? [])];
  askOpen.value = true;
}

function toggleAskItem(field: string) {
  const idx = askDraft.value.indexOf(field);
  if (idx >= 0) askDraft.value.splice(idx, 1);
  else askDraft.value.push(field);
}

async function saveAsk() {
  if (!askDraft.value.length) {
    message.warning('至少选择一项，或关闭这个开关');
    return;
  }
  await patch({ askFields: askDraft.value });
  askOpen.value = false;
}

// ===== 客户评级 =====
const gradeOpen = ref(false);
const gradeDraft = ref<AgentApi.Grade[]>([]);

function editGrades() {
  gradeDraft.value = clone(skills.value?.grades ?? []);
  gradeOpen.value = true;
}

async function saveGrades() {
  await patch({
    grades: gradeDraft.value.map((g) => ({
      ...g,
      name: g.name.trim() || `${'ABCD'[g.level - 1]}类客户`,
    })),
  });
  gradeOpen.value = false;
}

// ===== 自动建档 =====
async function setLead(p: Partial<ChannelAiApi.Config>) {
  Object.assign(cfg.value, p);
  await saveAiConfig({ ...cfg.value, llmApiKey: '' });
  message.success('已保存');
}

onMounted(async () => {
  load().catch(() => {});
  cfg.value = await getAiConfig();
});
</script>

<template>
  <div class="sk">
    <header class="sk__head">
      <h2 class="sk__title">配技能</h2>
      <p class="sk__sub">
        按外贸业务场景开启数字员工的自动化技能，开关状态实时保存生效
      </p>
    </header>

    <!-- 三大场景切换卡片 -->
    <div class="sk__cards">
      <button
        v-for="s in SKILLS"
        :key="s.key"
        type="button"
        class="sk__card"
        :class="{ on: active === s.key }"
        @click="active = s.key"
      >
        <div class="flex w-full items-center justify-between">
          <span class="sk__icon" :class="`is-${s.tint}`">
            <IconifyIcon :icon="s.icon" class="size-5" />
          </span>
          <span class="sk__badge">{{ s.badge }}</span>
        </div>
        <div class="mt-3">
          <div class="sk__card-title">{{ s.title }}</div>
          <div class="sk__card-desc">{{ s.desc }}</div>
        </div>
      </button>
    </div>

    <!-- 技能详情面板 -->
    <section v-if="skills" class="sk__detail">
      <!-- 场景 1：智能询盘接待 -->
      <template v-if="active === 'inquiry'">
        <div class="sk__panel-head">
          <h3 class="sk__h">智能询盘接待</h3>
          <span class="text-xs text-[#86868B]">覆盖全渠道买家咨询自动响应与跟进</span>
        </div>

        <!-- 1. AI 发送文件 -->
        <div class="sk-item">
          <div class="sk-item__main">
            <span class="sk-item__icon is-blue">
              <IconifyIcon icon="lucide:file-up" class="size-4" />
            </span>
            <div class="min-w-0 flex-1">
              <div class="sk__name">AI 自动发送云盘资料</div>
              <div class="sk__desc">
                识别到产品目录、参数表、认证证书等诉求时，自动从企业云盘匹配文件发出（单次最多 3 份，仅发送开启了「AI 可发送」的文件）。
                <button
                  type="button"
                  class="sk-link"
                  @click="
                    router.push({ path: '/v3/settings', query: { tab: 'cloud' } })
                  "
                >
                  前往管理云盘文件 ›
                </button>
              </div>
            </div>
            <Switch
              :checked="skills.sendFiles"
              @change="(v: any) => patch({ sendFiles: !!v })"
            />
          </div>
        </div>

        <!-- 2. 智能跟进 -->
        <div class="sk-item">
          <div class="sk-item__main">
            <span class="sk-item__icon is-purple">
              <IconifyIcon icon="lucide:clock-arrow-up" class="size-4" />
            </span>
            <div class="min-w-0 flex-1">
              <div class="sk__name">未回复买家多轮跟进</div>
              <div class="sk__desc">
                AI 回复后买家迟迟未回音，在 AI 接待时段内自动发起礼貌追问（仅对「自动回复」模式且人工未介入的会话生效）。
              </div>
            </div>
            <Switch
              :checked="skills.followUp"
              @change="(v: any) => patch({ followUp: !!v })"
            />
          </div>

          <div v-if="skills.followUp" class="sk-subbox">
            <div class="flex flex-wrap items-center gap-2 text-[13px] text-[#1D1D1F]">
              <span>买家超过</span>
              <InputNumber
                :value="skills.followUpHours"
                :min="1"
                :max="720"
                size="small"
                class="w-20"
                @change="(v: any) => v && patch({ followUpHours: Number(v) })"
              />
              <span>小时未回复则自动跟进，最多连续跟进</span>
              <InputNumber
                :value="skills.followUpRounds"
                :min="1"
                :max="3"
                size="small"
                class="w-16"
                @change="(v: any) => v && patch({ followUpRounds: Number(v) })"
              />
              <span>轮</span>
            </div>
            <div class="mt-1.5 text-xs text-[#86868B]">
              注：WhatsApp 受限于客户最后一条消息后 24 小时会话窗口，间隔 ≥ 24 小时时仅对邮件执行跟进。
            </div>
          </div>
        </div>

        <!-- 3. 预估报价 -->
        <div class="sk-item">
          <div class="sk-item__main">
            <span class="sk-item__icon is-green">
              <IconifyIcon icon="lucide:badge-dollar-sign" class="size-4" />
            </span>
            <div class="min-w-0 flex-1">
              <div class="sk__name">预估区间报价与 MOQ 解答</div>
              <div class="sk__desc">
                严格依据知识库中的价格表回答参考报价与起订量；关闭后凡遇询价一律转人工处理。
              </div>
            </div>
            <Switch
              :checked="skills.quote"
              @change="(v: any) => patch({ quote: !!v })"
            />
          </div>

          <div v-if="skills.quote" class="sk-subbox">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <Checkbox
                :checked="skills.webQuote"
                @change="(e: any) => toggleWebQuote(!!e.target.checked)"
              >
                <span class="text-[13px] font-medium text-[#1D1D1F]">
                  允许联网检索市场参考价（当企业知识库未录入该产品价格时）
                </span>
              </Checkbox>

              <div v-if="skills.webQuote" class="flex items-center gap-2">
                <span
                  class="ap-crm-pill"
                  :class="
                    settings?.webSearch?.hasKey
                      ? 'ap-crm-pill--green'
                      : 'ap-crm-pill--amber'
                  "
                >
                  {{
                    settings?.webSearch?.hasKey
                      ? '● 已配置搜索密钥'
                      : '● 待配置搜索密钥'
                  }}
                </span>
                <button type="button" class="sk-edit-pill" @click="openSearch">
                  {{ settings?.webSearch?.hasKey ? '修改配置' : '立即配置' }}
                </button>
              </div>
            </div>
            <div v-if="skills.webQuote" class="mt-1 text-xs text-[#86868B]">
              AI 会向买家明确注明此为公开市场参考区间，最终成交价以业务员正式报价单为准。
            </div>
          </div>
        </div>

        <!-- 4. 主动追问客户信息 -->
        <div class="sk-item">
          <div class="sk-item__main">
            <span class="sk-item__icon is-amber">
              <IconifyIcon icon="lucide:clipboard-list" class="size-4" />
            </span>
            <div class="min-w-0 flex-1">
              <div class="sk__name">顺势追问买家采购背景</div>
              <div class="sk__desc">
                解答客户疑问的同时，自然引导买家补充关键采购要素，帮业务员提前摸清底细。
              </div>
            </div>
            <Switch
              :checked="askOn"
              @change="(v: any) => toggleAsk(!!v)"
            />
          </div>

          <div v-if="askOn" class="sk-subbox">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <div class="flex flex-wrap gap-1.5">
                <span v-for="f in skills.askFields" :key="f" class="sk__chip">
                  {{ f }}
                </span>
              </div>
              <button type="button" class="sk-edit-pill" @click="editAsk">
                <IconifyIcon icon="lucide:pencil" class="size-3" />
                <span>选择追问项</span>
              </button>
            </div>
            <Checkbox
              class="mt-2.5 text-xs text-[#6E6E73]"
              :checked="skills.askMore"
              @change="(e: any) => patch({ askMore: e.target.checked })"
            >
              除以上指定字段外，允许 AI 根据对话上下文灵活追问更多需求细节
            </Checkbox>
          </div>
        </div>
      </template>

      <!-- 场景 2：自动背调客户 -->
      <template v-else-if="active === 'research'">
        <div class="sk__panel-head">
          <h3 class="sk__h">自动背调客户</h3>
          <span class="text-xs text-[#86868B]">新线索建档后自动拉取公开商业情报</span>
        </div>

        <div class="sk-item">
          <div class="sk-item__main">
            <span class="sk-item__icon is-purple">
              <IconifyIcon icon="lucide:radar" class="size-4" />
            </span>
            <div class="min-w-0 flex-1">
              <div class="sk__name">新线索进线后自动执行深度背调</div>
              <div class="sk__desc">
                当新进线索带有企业官网或非公共域名企业邮箱时，建档后自动读取对方官网与公开渠道，生成买家性质、主营产品、采购信号与风险提示报告。您也可随时在「CRM 智枢 → 深度背调」手动发起。
              </div>
            </div>
            <Switch
              :checked="skills.autoResearch"
              @change="(v: any) => patch({ autoResearch: !!v })"
            />
          </div>
        </div>
      </template>

      <!-- 场景 3：CRM 自动录入 -->
      <template v-else>
        <div class="sk__panel-head">
          <h3 class="sk__h">CRM 自动录入与评级</h3>
          <span class="text-xs text-[#86868B]">打通全渠道询盘到 CRM 线索池的自动化闭环</span>
        </div>

        <div class="sk-item">
          <div class="sk-item__main">
            <span class="sk-item__icon is-blue">
              <IconifyIcon icon="lucide:user-round-plus" class="size-4" />
            </span>
            <div class="min-w-0 flex-1">
              <div class="sk__name">全渠道新询盘自动建档入库</div>
              <div class="sk__desc">
                当陌生买家通过 WhatsApp、邮箱、Facebook、Instagram 或官网客服首次咨询时，自动在 CRM 生成线索档案。
              </div>
            </div>
            <Switch
              :checked="!!cfg.leadAutoCreate"
              @change="(v: any) => setLead({ leadAutoCreate: !!v })"
            />
          </div>

          <div v-if="cfg.leadAutoCreate" class="sk-subbox">
            <Checkbox
              :checked="!!cfg.leadAiExtract"
              @change="(e: any) => setLead({ leadAiExtract: e.target.checked })"
            >
              <span class="text-[13px] font-medium text-[#1D1D1F]">
                使用 AI 从首轮询盘中提取公司名、国家/地区、联系人与采购需求（开启客户自动评级需勾选此项）
              </span>
            </Checkbox>
          </div>
        </div>

        <div class="sk-item">
          <div class="sk-item__main">
            <span class="sk-item__icon is-green">
              <IconifyIcon icon="lucide:award" class="size-4" />
            </span>
            <div class="min-w-0 flex-1">
              <div class="sk__name">AI 智能客户评级</div>
              <div class="sk__desc">
                结合买家规模证据、产品匹配度与下方自定义分级标准，自动写入 CRM 客户级别。
              </div>
            </div>
            <Switch
              :checked="skills.autoGrade"
              @change="(v: any) => patch({ autoGrade: !!v })"
            />
          </div>

          <div class="sk-subbox">
            <div class="mb-2.5 flex items-center justify-between">
              <span class="text-xs font-semibold text-[#6E6E73]">当前评级判定标准</span>
              <button type="button" class="sk-edit-pill" @click="editGrades">
                <IconifyIcon icon="lucide:pencil" class="size-3" />
                <span>编辑标准</span>
              </button>
            </div>
            <div class="grid grid-cols-1 gap-2 sm:grid-cols-2">
              <div v-for="(g, gi) in skills.grades" :key="g.level" class="sk__grade">
                <div class="flex items-center gap-2">
                  <span
                    class="ap-crm-pill"
                    :class="GRADE_COLORS[gi] ?? 'ap-crm-pill--gray'"
                  >
                    {{ 'ABCD'[g.level - 1] || g.level }} 级
                  </span>
                  <span class="text-[13px] font-semibold text-[#1D1D1F]">
                    {{ g.name }}
                  </span>
                </div>
                <div class="mt-1 text-xs leading-5 text-[#6E6E73]">
                  {{ g.desc || '未设置描述' }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>
    </section>

    <!-- 弹窗 1：主动追问字段（Apple Sheet） -->
    <Modal
      v-model:open="askOpen"
      title="选择主动追问的客户信息"
      :width="520"
      ok-text="保存设置"
      cancel-text="取消"
      wrap-class-name="ap-modal-sheet"
      @ok="saveAsk"
    >
      <div class="ap-sheet-lead">
        <span class="ap-sheet-icon">
          <IconifyIcon icon="lucide:clipboard-list" class="size-5" />
        </span>
        <div class="ap-sheet-subtitle">
          点选希望 AI 在接待过程中顺势了解的买家信息，建议勾选 3～5 项最核心要素。
        </div>
      </div>
      <div class="flex flex-wrap gap-2">
        <button
          v-for="o in settings?.askFieldOptions ?? []"
          :key="o"
          type="button"
          class="sk-pick-chip"
          :class="{ on: askDraft.includes(o) }"
          @click="toggleAskItem(o)"
        >
          <IconifyIcon
            :icon="askDraft.includes(o) ? 'lucide:check' : 'lucide:plus'"
            class="size-3.5"
          />
          <span>{{ o }}</span>
        </button>
      </div>
    </Modal>

    <!-- 弹窗 2：客户评级规则（Apple Sheet） -->
    <Modal
      v-model:open="gradeOpen"
      title="配置客户评级标准"
      :width="580"
      ok-text="保存标准"
      cancel-text="取消"
      wrap-class-name="ap-modal-sheet"
      @ok="saveGrades"
    >
      <div class="ap-sheet-lead">
        <span class="ap-sheet-icon ap-sheet-icon--green">
          <IconifyIcon icon="lucide:award" class="size-5" />
        </span>
        <div class="ap-sheet-subtitle">
          清晰描述各等级客户的画像特征，AI 建档时按此标准打级；信息不足以判断时保持留空不乱打级。
        </div>
      </div>
      <div class="ap-sheet-body">
        <div
          v-for="(g, gi) in gradeDraft"
          :key="g.level"
          class="flex items-center gap-2.5"
        >
          <span
            class="ap-crm-pill shrink-0"
            :class="GRADE_COLORS[gi] ?? 'ap-crm-pill--gray'"
          >
            {{ 'ABCD'[g.level - 1] || g.level }}
          </span>
          <Input v-model:value="g.name" class="w-32 shrink-0" :maxlength="20" />
          <Input
            v-model:value="g.desc"
            class="flex-1"
            :maxlength="100"
            placeholder="例如：年采购额 100 万美元以上的成熟品牌商"
          />
        </div>
      </div>
    </Modal>

    <!-- 弹窗 3：联网搜索配置（Apple Sheet） -->
    <Modal
      v-model:open="searchOpen"
      title="配置联网搜索服务"
      :width="520"
      ok-text="保存配置"
      cancel-text="取消"
      wrap-class-name="ap-modal-sheet"
      @ok="saveSearch"
    >
      <div class="ap-sheet-lead">
        <span class="ap-sheet-icon ap-sheet-icon--purple">
          <IconifyIcon icon="lucide:globe" class="size-5" />
        </span>
        <div class="ap-sheet-subtitle">
          支持 Tavily 官方接口及兼容 Tavily 协议的联网检索服务，用于补充市场参考报价与深度背调。
        </div>
      </div>
      <div class="ap-sheet-body">
        <div class="ap-sheet-field">
          <div class="ap-sheet-label">
            <span>接口地址</span>
            <span class="ap-sheet-optional">留空使用默认 Tavily</span>
          </div>
          <Input
            v-model:value="searchForm.baseUrl"
            placeholder="https://api.tavily.com"
          />
        </div>
        <div class="ap-sheet-field">
          <div class="ap-sheet-label"><span>API Key</span></div>
          <Input.Password
            v-model:value="searchForm.apiKey"
            :placeholder="
              settings?.webSearch?.hasKey ? '已加密保存，不修改请留空' : 'tvly-...'
            "
          />
        </div>
        <div class="ap-sheet-tip">
          <IconifyIcon icon="lucide:lock" class="mt-0.5 size-3.5 shrink-0" />
          <span>API Key 在服务端加密存储，前端不会明文回显；检索用量由对应搜索服务商计费。</span>
        </div>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.sk {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sk__head {
  padding: 2px 2px 0;
}

.sk__title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #1d1d1f;
  letter-spacing: -0.015em;
}

.sk__sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: #6e6e73;
}

.sk__cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.sk__card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 16px 18px;
  text-align: left;
  cursor: pointer;
  background: #fff;
  border: 1.5px solid transparent;
  border-radius: 18px;
  box-shadow: var(--ap-shadow-rest);
  transition:
    border-color var(--ap-dur-fast),
    box-shadow var(--ap-dur-fast),
    transform var(--ap-dur-fast) var(--ap-ease);
}

.sk__card:hover:not(.on) {
  transform: translateY(-1.5px);
  box-shadow: var(--ap-shadow-lift);
}

.sk__card.on {
  border-color: #007aff;
  box-shadow: 0 6px 18px rgb(0 122 255 / 12%);
}

.sk__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
}

.sk__icon.is-blue {
  color: #007aff;
  background: #e8f2ff;
}

.sk__icon.is-purple {
  color: #7c3aed;
  background: #f3eeff;
}

.sk__icon.is-green {
  color: #1f9d4c;
  background: #eaf9ee;
}

.sk__badge {
  padding: 2px 9px;
  font-size: 11px;
  font-weight: 600;
  color: #6e6e73;
  background: #f2f3f7;
  border-radius: 999px;
}

.sk__card.on .sk__badge {
  color: #007aff;
  background: #e8f2ff;
}

.sk__card-title {
  font-size: 14.5px;
  font-weight: 700;
  color: #1d1d1f;
}

.sk__card.on .sk__card-title {
  color: #007aff;
}

.sk__card-desc {
  margin-top: 3px;
  font-size: 12px;
  color: #86868b;
}

/* 详情列表 */
.sk__detail {
  padding: 22px 24px;
  background: #fff;
  border-radius: 18px;
  box-shadow: var(--ap-shadow-rest);
}

.sk__panel-head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: 14px;
  border-bottom: 0.5px solid var(--ap-separator);
}

.sk__h {
  margin: 0;
  font-size: 16.5px;
  font-weight: 700;
  color: #1d1d1f;
}

.sk-item {
  padding: 18px 0;
}

.sk-item + .sk-item {
  border-top: 0.5px solid var(--ap-separator);
}

.sk-item:last-child {
  padding-bottom: 2px;
}

.sk-item__main {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.sk-item__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 36px;
  height: 36px;
  margin-top: 1px;
  border-radius: 10px;
}

.sk-item__icon.is-blue {
  color: #007aff;
  background: #e8f2ff;
}

.sk-item__icon.is-purple {
  color: #7c3aed;
  background: #f3eeff;
}

.sk-item__icon.is-green {
  color: #1f9d4c;
  background: #eaf9ee;
}

.sk-item__icon.is-amber {
  color: #d97706;
  background: #fff4e0;
}

.sk__name {
  font-size: 14.5px;
  font-weight: 600;
  color: #1d1d1f;
}

.sk__desc {
  margin-top: 3px;
  font-size: 12.5px;
  line-height: 1.6;
  color: #6e6e73;
}

.sk-link {
  padding: 0;
  margin-left: 4px;
  font-size: 12.5px;
  font-weight: 600;
  color: #007aff;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.sk-subbox {
  padding: 13px 15px;
  margin: 12px 0 0 50px;
  background: #f7f8fb;
  border-radius: 13px;
}

.sk__chip {
  display: inline-flex;
  align-items: center;
  height: 25px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 500;
  color: #1d1d1f;
  background: #fff;
  border-radius: 999px;
  box-shadow: 0 1px 2px rgb(0 0 0 / 6%);
}

.sk-edit-pill {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  height: 26px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 600;
  color: #007aff;
  cursor: pointer;
  background: #e8f2ff;
  border: 0;
  border-radius: 999px;
  transition: background-color var(--ap-dur-fast);
}

.sk-edit-pill:hover {
  background: #dceaff;
}

.sk__grade {
  padding: 10px 12px;
  background: #fff;
  border-radius: 11px;
  box-shadow: 0 1px 2px rgb(0 0 0 / 4%);
}

.sk-pick-chip {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 34px;
  padding: 0 13px;
  font-size: 13px;
  font-weight: 500;
  color: #3a3a3c;
  cursor: pointer;
  background: #f4f5f8;
  border: 1.5px solid transparent;
  border-radius: 999px;
  transition: all var(--ap-dur-fast);
}

.sk-pick-chip.on {
  font-weight: 600;
  color: #007aff;
  background: #e8f2ff;
  border-color: rgb(0 122 255 / 35%);
}

@media (width <= 860px) {
  .sk__cards {
    grid-template-columns: 1fr;
  }

  .sk-subbox {
    margin-left: 0;
  }
}
</style>
