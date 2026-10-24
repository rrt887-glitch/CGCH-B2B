<script lang="ts" setup>
/**
 * 配技能：智能询盘接待、自动背调客户、CRM 自动录入（与原网站一致）。
 * 允许联网报价需要配置联网搜索（Tavily 兼容接口）的 API Key，没有配置时不会检索。
 */
import type { AgentApi } from '#/api/channel/agent';
import type { ChannelAiApi } from '#/api/channel/ai';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import { Button, Checkbox, CheckboxGroup, Input, InputNumber, message, Modal, Switch } from 'ant-design-vue';

import { getAiConfig, saveAiConfig } from '#/api/channel/ai';

import { clone, useAgentSettings } from './use-settings';

const router = useRouter();
const { settings, load, save } = useAgentSettings();
const skills = computed(() => settings.value?.skills);
const active = ref<'crm' | 'inquiry' | 'research'>('inquiry');
const cfg = ref<ChannelAiApi.Config>({});

const SKILLS = [
  { key: 'inquiry' as const, title: '智能询盘接待', desc: '全渠道，主动跟进赢单', hot: true },
  { key: 'research' as const, title: '自动背调客户', desc: '毫秒级背调，识别风险' },
  { key: 'crm' as const, title: 'CRM 自动录入', desc: '自动评级打标，管理档案' },
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
  searchForm.value = { baseUrl: settings.value?.webSearch?.baseUrl ?? '', apiKey: '' };
  searchOpen.value = true;
}

async function toggleWebQuote(v: boolean) {
  await patch({ webQuote: v });
  if (v && !settings.value?.webSearch?.hasKey) openSearch();
}

async function saveSearch() {
  const hasKey = !!settings.value?.webSearch?.hasKey || !!searchForm.value.apiKey.trim();
  if (!hasKey) {
    message.warning('请填写联网搜索的 API Key');
    return;
  }
  await save({ webSearch: { baseUrl: searchForm.value.baseUrl.trim(), apiKey: searchForm.value.apiKey.trim() } });
  if (settings.value) settings.value.webSearch = { baseUrl: searchForm.value.baseUrl.trim(), hasKey: true };
  searchOpen.value = false;
}

// ===== 主动追问 =====
const askOn = computed(() => (skills.value?.askFields?.length ?? 0) > 0);
const askOpen = ref(false);
const askDraft = ref<string[]>([]);

function toggleAsk(v: boolean) {
  patch({ askFields: v ? (settings.value?.askFieldOptions ?? []).slice(0, 3) : [] });
}

function editAsk() {
  askDraft.value = [...(skills.value?.askFields ?? [])];
  askOpen.value = true;
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
  await patch({ grades: gradeDraft.value.map((g) => ({ ...g, name: g.name.trim() || `${'ABCD'[g.level - 1]}类客户` })) });
  gradeOpen.value = false;
}

// ===== 自动建档（数字员工配置里的开关） =====
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
    <aside class="sk__list">
      <div v-for="s in SKILLS" :key="s.key" class="sk__item" :class="{ on: active === s.key }" @click="active = s.key">
        <div v-if="s.hot" class="sk__robot"><IconifyIcon icon="lucide:bot-message-square" class="size-7 text-[#155dfc]" /></div>
        <div>
          <div class="font-semibold text-gray-900">{{ s.title }}</div>
          <div class="mt-0.5 text-xs text-gray-500">{{ s.desc }}</div>
        </div>
      </div>
    </aside>

    <section v-if="skills" class="sk__detail">
      <template v-if="active === 'inquiry'">
        <h3 class="sk__h">智能询盘接待</h3>

        <div class="sk__row">
          <Switch size="small" :checked="skills.sendFiles" @change="(v: any) => patch({ sendFiles: !!v })" />
          <div>
            <div class="sk__name">AI 发送文件</div>
            <div class="sk__desc">
              识别到报价单、产品参数、证书等需求时，从企业云盘匹配并发送文件
              <a class="ml-1" @click="router.push({ path: '/v3/settings', query: { tab: 'cloud' } })">上传文件</a>
            </div>
            <div class="mt-1 text-xs text-gray-400">只会发送企业云盘里打开了「AI 可发送」的文件，AI 根据文件名和描述判断，一次最多 3 个。</div>
          </div>
        </div>

        <div class="sk__row">
          <Switch size="small" :checked="skills.followUp" @change="(v: any) => patch({ followUp: !!v })" />
          <div class="min-w-0 flex-1">
            <div class="sk__name">智能跟进客户</div>
            <div class="sk__desc">AI 发送消息后，如果客户迟迟未回复，AI 自动执行多轮智能跟进（仅「自动回复」模式、AI 接待时间内）</div>
            <div v-if="skills.followUp" class="sk__tags items-center text-sm">
              <span>客户</span>
              <InputNumber :value="skills.followUpHours" :min="1" :max="720" size="small" class="w-20" @change="(v: any) => v && patch({ followUpHours: Number(v) })" />
              <span>小时没回复就跟进，最多跟进</span>
              <InputNumber :value="skills.followUpRounds" :min="1" :max="3" size="small" class="w-16" @change="(v: any) => v && patch({ followUpRounds: Number(v) })" />
              <span>次</span>
            </div>
            <div v-if="skills.followUp" class="mt-1 text-xs text-gray-400">
              WhatsApp 只能在客户最后一条消息后 24 小时内主动发送，间隔设为 24 小时及以上时只跟进邮件。人工回复过的对话不会再自动跟进。
            </div>
          </div>
        </div>

        <div class="sk__row">
          <Switch size="small" :checked="skills.quote" @change="(v: any) => patch({ quote: !!v })" />
          <div>
            <div class="sk__name">预估报价</div>
            <div class="sk__desc">基于授权知识库回答产品价格和 MOQ 问题；关闭时客户询价一律转人工</div>
            <Checkbox
              class="mt-1 text-xs"
              :checked="skills.webQuote"
              :disabled="!skills.quote"
              @change="(e: any) => toggleWebQuote(!!e.target.checked)"
            >
              允许联网报价：在知识库缺少价格时，检索公开市场参考价
            </Checkbox>
            <div v-if="skills.quote && skills.webQuote" class="sk__tags text-xs text-gray-500">
              <span>{{ settings?.webSearch?.hasKey ? '已配置联网搜索' : '还没有配置联网搜索，不会检索' }}</span>
              <a @click="openSearch">{{ settings?.webSearch?.hasKey ? '修改' : '去配置' }}</a>
            </div>
            <div v-if="skills.quote && skills.webQuote" class="mt-1 text-xs text-gray-400">
              只在客户问价、且企业资料里没有价格时检索；AI 会说明这是市场参考价，最终以正式报价为准。
            </div>
          </div>
        </div>

        <div class="sk__row">
          <Switch size="small" :checked="askOn" @change="(v: any) => toggleAsk(!!v)" />
          <div class="min-w-0 flex-1">
            <div class="sk__name">会话时，主动追问客户信息</div>
            <div class="sk__desc">在会话中，智能体将重点围绕以下背景信息，主动追问客户</div>
            <div v-if="askOn" class="sk__tags">
              <div class="flex flex-wrap gap-1.5">
                <span v-for="f in skills.askFields" :key="f" class="sk__chip">{{ f }}</span>
              </div>
              <Button size="small" @click="editAsk"><IconifyIcon icon="lucide:pencil" class="mr-1 inline size-3" />编辑</Button>
            </div>
            <Checkbox class="mt-2 text-xs text-gray-500" :checked="skills.askMore" :disabled="!askOn" @change="(e: any) => patch({ askMore: e.target.checked })">
              除以上背景信息外，智能体将根据客户实际情况，追问更多需求信息
            </Checkbox>
          </div>
        </div>
      </template>

      <template v-else-if="active === 'research'">
        <h3 class="sk__h">自动背调客户</h3>
        <div class="sk__box">
          <div class="sk__name">深度背调</div>
          <div class="sk__desc">根据企业工商、贸易与公开资料，帮助销售快速完成客户背景核验</div>
          <div class="mt-4 flex items-start gap-3">
            <Switch size="small" :checked="skills.autoResearch" @change="(v: any) => patch({ autoResearch: !!v })" />
            <div>
              <div class="text-sm font-medium">新线索进线后自动背调</div>
              <div class="sk__desc">
                线索有企业官网或企业邮箱时，建档后自动读取对方官网的公开页面和往来记录，生成背调报告（概况、主营产品、采购信号、风险提示），每次消耗智点。
                也可以在「CRM → 客户列表」勾选客户后点「一键背调」。
              </div>
            </div>
          </div>
        </div>
      </template>

      <template v-else>
        <h3 class="sk__h">CRM 自动录入</h3>
        <div class="sk__row">
          <Switch size="small" :checked="skills.autoGrade" @change="(v: any) => patch({ autoGrade: !!v })" />
          <div class="min-w-0 flex-1">
            <div class="sk__name">客户评级</div>
            <div class="sk__desc">基于证据、产品匹配度和客户等级规则，给客户评级（写入客户级别）</div>
            <div class="sk__tags">
              <div class="grid flex-1 grid-cols-2 gap-2">
                <div v-for="g in skills.grades" :key="g.level" class="sk__grade">
                  <div class="text-sm font-medium">{{ g.name }}</div>
                  <div class="text-xs text-gray-500">{{ g.desc }}</div>
                </div>
              </div>
              <Button size="small" @click="editGrades"><IconifyIcon icon="lucide:pencil" class="mr-1 inline size-3" />编辑</Button>
            </div>
          </div>
        </div>
        <div class="sk__row">
          <Switch size="small" :checked="!!cfg.leadAutoCreate" @change="(v: any) => setLead({ leadAutoCreate: !!v })" />
          <div>
            <div class="sk__name">客户进线后，自动录入 CRM</div>
            <div class="sk__desc">
              作为客户进入企业销售漏斗的第一道关口，实现全渠道、全天候的即时响应。<br />
              当客户通过 WhatsApp、邮箱、Facebook、Instagram 或留资邮箱等任意渠道发起询盘时，可自动完成建档。
            </div>
            <Checkbox class="mt-2 text-xs text-gray-500" :checked="!!cfg.leadAiExtract" :disabled="!cfg.leadAutoCreate" @change="(e: any) => setLead({ leadAiExtract: e.target.checked })">
              用 AI 从询盘里提取公司、国家、联系人和需求（客户评级需要开启）
            </Checkbox>
          </div>
        </div>
      </template>
    </section>

    <Modal v-model:open="askOpen" title="主动追问的客户信息" ok-text="保存" @ok="saveAsk">
      <CheckboxGroup v-model:value="askDraft" class="grid grid-cols-3 gap-2">
        <Checkbox v-for="o in settings?.askFieldOptions ?? []" :key="o" :value="o">{{ o }}</Checkbox>
      </CheckboxGroup>
    </Modal>

    <Modal v-model:open="gradeOpen" title="客户评级规则" :width="560" ok-text="保存" @ok="saveGrades">
      <p class="mb-3 text-xs text-gray-500">写清楚什么样的客户属于哪一类，AI 建档时按这里的描述判断，判断不了时不打级。</p>
      <div v-for="g in gradeDraft" :key="g.level" class="mb-3 flex gap-2">
        <Input v-model:value="g.name" class="w-28" :maxlength="20" />
        <Input v-model:value="g.desc" :maxlength="100" placeholder="例如：年采购额 100 万美元以上的成熟品牌" />
      </div>
    </Modal>
    <Modal v-model:open="searchOpen" title="联网搜索" ok-text="保存" @ok="saveSearch">
      <p class="text-sm text-gray-500">允许联网报价时，用联网搜索检索公开市场价格。支持 Tavily 及兼容 Tavily 接口的搜索服务。</p>
      <div class="mb-1 mt-3 text-sm">接口地址</div>
      <Input v-model:value="searchForm.baseUrl" placeholder="https://api.tavily.com（留空使用默认）" />
      <div class="mb-1 mt-3 text-sm">API Key</div>
      <Input v-model:value="searchForm.apiKey" :placeholder="settings?.webSearch?.hasKey ? '已配置，不修改请留空' : 'tvly-...'" />
      <div class="mt-2 text-xs text-gray-400">API Key 加密保存，不会显示在页面上。检索费用由搜索服务商收取。</div>
    </Modal>
  </div>
</template>

<style scoped>
.sk {
  display: flex;
  min-height: 100%;
}

.sk__list {
  flex: 0 0 190px;
  padding: 52px 12px 12px;
  border-right: 1px solid #f0f1f3;
}

.sk__item {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 12px;
  margin-bottom: 8px;
  cursor: pointer;
  border: 1px solid transparent;
  border-radius: 10px;
}

.sk__item:hover {
  background: #f8fafc;
}

.sk__item.on {
  background: linear-gradient(135deg, #f5f8ff, #fdf2f8);
  border-color: #f5c2d7;
}

.sk__robot {
  display: flex;
  flex: 0 0 44px;
  align-items: center;
  justify-content: center;
  height: 44px;
  background: #fff;
  border-radius: 12px;
}

.sk__detail {
  flex: 1;
  min-width: 0;
  padding: 20px 24px;
}

.sk__h {
  margin: 0 0 22px;
  font-size: 15px;
  font-weight: 700;
  color: #111827;
}

.sk__row {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  margin-bottom: 26px;
}

.sk__row > :first-child {
  margin-top: 2px;
}

.sk__name {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.sk__desc {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.6;
  color: #4b5563;
}

.sk__tags {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 10px 12px;
  margin-top: 10px;
  background: #f7f8fa;
  border-radius: 8px;
}

.sk__chip {
  padding: 1px 8px;
  font-size: 12px;
  color: #374151;
  background: #e9ebef;
  border-radius: 4px;
}

.sk__grade {
  padding: 8px 10px;
  background: #fff;
  border-radius: 6px;
}

.sk__box {
  padding: 16px;
  background: #f7f8fa;
  border-radius: 10px;
}

@media (width <= 760px) {
  .sk {
    flex-direction: column;
  }

  .sk__list {
    padding-top: 12px;
    border-right: 0;
  }
}
</style>
