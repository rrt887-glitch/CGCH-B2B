<script lang="ts" setup>
/**
 * 步骤 5 · 转人工与托管边界：
 * 苹果胶囊分段导航（客户意图 / 客户条件 / AI 工作时间 / 限频与黑名单 / 人工介入规则），
 * 明确人机协同边界，高价值成交与客诉风险第一时间交由业务员接管。
 */
import type { AgentApi } from '#/api/channel/agent';

import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { IconifyIcon } from '@vben/icons';

import {
  Button,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Select,
  Switch,
  Textarea,
} from 'ant-design-vue';

import { clone, useAgentSettings } from './use-settings';

const WorkTime = defineAsyncComponent(
  () => import('#/views/cgch/config/work-time/index.vue'),
);
const Blacklist = defineAsyncComponent(
  () => import('#/views/cgch/config/send-policy/index.vue'),
);

const { settings, load, save } = useAgentSettings();
const tab = ref<'blacklist' | 'condition' | 'intent' | 'takeover' | 'worktime'>(
  'intent',
);
const h = ref<AgentApi.Handoff>();
const pauseHours = ref(12);

const NAV_TABS = [
  { key: 'intent' as const, label: '客户意图', icon: 'lucide:sparkles' },
  { key: 'condition' as const, label: '客户条件', icon: 'lucide:user-check' },
  { key: 'worktime' as const, label: 'AI 工作时间', icon: 'lucide:clock' },
  { key: 'blacklist' as const, label: '限频与黑名单', icon: 'lucide:shield' },
  { key: 'takeover' as const, label: '人工介入规则', icon: 'lucide:hand-helping' },
];

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
  { value: 'handoff', label: '常规转人工', pill: 'ap-crm-pill--blue' },
  { value: 'missing', label: '资料缺失', pill: 'ap-crm-pill--amber' },
  { value: 'permission', label: '商务权限决策', pill: 'ap-crm-pill--purple' },
  { value: 'crisis', label: '客诉危机', pill: 'ap-crm-pill--red' },
  { value: 'compliance', label: '合规风险', pill: 'ap-crm-pill--red' },
  { value: 'other', label: '其他事项', pill: 'ap-crm-pill--gray' },
];

const groups = computed(() => {
  const m = new Map<string, AgentApi.Preset[]>();
  for (const p of settings.value?.presets ?? [])
    m.set(p.group, [...(m.get(p.group) ?? []), p]);
  return [...m.entries()];
});

function presetOn(key: string) {
  const v = h.value?.presets?.[key];
  return v === undefined ? !['ask_quote', 'ask_sample'].includes(key) : v;
}

function setPreset(key: string, v: boolean) {
  h.value!.presets = { ...h.value!.presets, [key]: v };
  persist(v ? '已开启该意图转人工' : '已关闭');
}

// ===== 自定义转人工项 =====
const customOpen = ref(false);
const customIndex = ref(-1);
const customDraft = ref<AgentApi.Custom>({
  text: '',
  type: 'handoff',
  enabled: true,
});

function editCustom(i = -1) {
  if (i < 0 && (h.value?.custom.length ?? 0) >= 20) {
    message.warning('最多添加 20 条自定义规则');
    return;
  }
  customIndex.value = i;
  customDraft.value =
    i >= 0
      ? clone(h.value!.custom[i]!)
      : { text: '', type: 'handoff', enabled: true };
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
  { value: 'deal', label: '已成交老客户' },
  { value: 'level', label: '指定客户级别' },
  { value: 'source', label: '指定客户来源' },
];
const condOpen = ref(false);
const condIndex = ref(-1);
const condDraft = ref<AgentApi.Condition>({
  name: '',
  field: 'deal',
  values: [],
  enabled: true,
});
const valueOptions = computed(() =>
  condDraft.value.field === 'level'
    ? getDictOptions(DICT_TYPE.CRM_CUSTOMER_LEVEL, 'number')
    : condDraft.value.field === 'source'
      ? getDictOptions(DICT_TYPE.CRM_CUSTOMER_SOURCE, 'number')
      : [],
);

function condText(c: AgentApi.Condition) {
  if (c.field === 'deal') return 'CRM 中已标记为成交的客户';
  const opts =
    c.field === 'level'
      ? getDictOptions(DICT_TYPE.CRM_CUSTOMER_LEVEL, 'number')
      : getDictOptions(DICT_TYPE.CRM_CUSTOMER_SOURCE, 'number');
  const labels = c.values.map(
    (v) => opts.find((o: any) => o.value === v)?.label ?? v,
  );
  return `${c.field === 'level' ? '客户级别' : '客户来源'}属于：${labels.join('、') || '—'}`;
}

function editCond(i = -1) {
  condIndex.value = i;
  condDraft.value =
    i >= 0
      ? clone(h.value!.conditions[i]!)
      : { name: '', field: 'deal', values: [], enabled: true };
  condOpen.value = true;
}

async function saveCond() {
  const c = condDraft.value;
  if (c.field !== 'deal' && !c.values.length) {
    message.warning('请选择匹配的取值');
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
  await persist('条件已删除');
}

// ===== 人工介入 AI 托管 =====
async function saveTakeover() {
  await save({ handoff: h.value, humanPauseHours: pauseHours.value });
}

onMounted(() => load().catch(() => {}));
</script>

<template>
  <div class="ho">
    <header class="ho__page-head">
      <h2 class="ho__title">转人工与托管边界</h2>
      <p class="ho__sub">
        设定高价值议价、客诉预警、VIP 老客户等需真人接手的边界，以及工作时段与防封号发送保护
      </p>
    </header>

    <!-- 苹果胶囊分段导航 -->
    <div class="ho__nav">
      <button
        v-for="t in NAV_TABS"
        :key="t.key"
        type="button"
        class="ho__nav-btn"
        :class="{ on: tab === t.key }"
        @click="tab = t.key"
      >
        <IconifyIcon :icon="t.icon" class="size-4" />
        <span>{{ t.label }}</span>
      </button>
    </div>

    <!-- Tab 1：根据客户意图 -->
    <div v-if="tab === 'intent' && h" class="ho__card">
      <div class="ho__master">
        <div class="flex min-w-0 items-center gap-3">
          <span class="ho__master-icon is-blue">
            <IconifyIcon icon="lucide:user-round-check" class="size-5" />
          </span>
          <div>
            <div class="flex items-center gap-2">
              <span class="text-[15px] font-bold text-[#1D1D1F]">
                AI 识别买家意图自动转人工
              </span>
              <span
                class="ap-crm-pill"
                :class="
                  h.intentEnabled ? 'ap-crm-pill--green' : 'ap-crm-pill--gray'
                "
              >
                {{ h.intentEnabled ? '已启用' : '未启用' }}
              </span>
            </div>
            <div class="mt-0.5 text-xs text-[#6E6E73]">
              对企业内全部渠道账号生效；命中下方任一开启项时停止自动发出，推送到「今天 → 等你处理」
            </div>
          </div>
        </div>
        <Switch
          :checked="h.intentEnabled"
          @change="
            (v: any) => {
              h!.intentEnabled = !!v;
              persist(v ? '已启用意图转人工' : '已停用');
            }
          "
        />
      </div>

      <!-- 自定义转人工 -->
      <div class="ho__block">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <div>
            <div class="ho__h">
              自定义触发场景
              <span class="ml-1 text-xs font-normal text-[#8E8E93]">
                {{ h.custom.length }} / 20
              </span>
            </div>
            <p class="ho__tip">
              用自然语言描述您企业特有的转人工情形；同时命中多项时优先按靠前规则归类
            </p>
          </div>
          <button type="button" class="ho__add-btn" @click="editCustom()">
            <IconifyIcon icon="lucide:plus" class="size-3.5" />
            <span>添加规则</span>
          </button>
        </div>

        <div v-if="!h.custom.length" class="ho__empty-inline">
          尚未添加自定义规则，可点击右上角补充（如「客户询问独家代理权」）
        </div>

        <div v-else class="space-y-2">
          <div v-for="(c, i) in h.custom" :key="i" class="ho__row-card">
            <div class="min-w-0 flex-1">
              <span class="text-[13.5px] font-medium text-[#1D1D1F]">
                {{ c.text }}
              </span>
            </div>
            <span
              class="ap-crm-pill"
              :class="
                TYPES.find((t) => t.value === c.type)?.pill ??
                'ap-crm-pill--gray'
              "
            >
              {{ TYPES.find((t) => t.value === c.type)?.label }}
            </span>
            <a class="ap-crm-link" @click="editCustom(i)">编辑</a>
            <Popconfirm
              title="删除这条转人工规则？"
              ok-text="删除"
              cancel-text="取消"
              @confirm="removeCustom(i)"
            >
              <a class="ap-crm-link ap-crm-link--danger">删除</a>
            </Popconfirm>
            <Switch
              size="small"
              :checked="c.enabled"
              @change="
                (v: any) => {
                  c.enabled = !!v;
                  persist();
                }
              "
            />
          </div>
        </div>
      </div>

      <!-- 预设意图分组 -->
      <div v-for="[group, items] in groups" :key="group" class="ho__block">
        <div class="ho__h mb-2.5">{{ group }}</div>
        <div class="space-y-1.5">
          <div v-for="p in items" :key="p.key" class="ho__row-card">
            <span class="min-w-0 flex-1 text-[13.5px] text-[#1D1D1F]">
              {{ p.text }}
            </span>
            <Switch
              size="small"
              :checked="presetOn(p.key)"
              :disabled="!h.intentEnabled"
              @change="(v: any) => setPreset(p.key, !!v)"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- Tab 2：根据客户条件 -->
    <div v-else-if="tab === 'condition' && h" class="ho__card">
      <div class="ho__master">
        <div class="flex min-w-0 items-center gap-3">
          <span class="ho__master-icon is-purple">
            <IconifyIcon icon="lucide:crown" class="size-5" />
          </span>
          <div>
            <div class="text-[15px] font-bold text-[#1D1D1F]">
              重点客户与老客户保护条件
            </div>
            <div class="mt-0.5 text-xs text-[#6E6E73]">
              满足下方任一档案条件的客户来信时，AI 仅为您拟好回复草稿，不直接发出，由专属业务员确认
            </div>
          </div>
        </div>
        <button type="button" class="ho__add-btn" @click="editCond()">
          <IconifyIcon icon="lucide:plus" class="size-3.5" />
          <span>新建客户条件</span>
        </button>
      </div>

      <div v-if="!h.conditions.length" class="ho__empty-box">
        <IconifyIcon icon="lucide:users-round" class="size-7 text-[#8E8E93] opacity-55" />
        <div class="mt-2 font-semibold text-[#1D1D1F]">尚未配置客户条件规则</div>
        <div class="mt-1 text-xs text-[#86868B]">
          建议将「已成交老客户」或「A 类大客户」设为仅起草不自动发送，由业务员亲自维护关系
        </div>
      </div>

      <div v-else class="space-y-2.5">
        <div v-for="(c, i) in h.conditions" :key="i" class="ho__cond-card">
          <div class="min-w-0 flex-1">
            <div class="text-[14px] font-semibold text-[#1D1D1F]">{{ c.name }}</div>
            <div class="mt-0.5 text-xs text-[#6E6E73]">{{ condText(c) }}</div>
          </div>
          <a class="ap-crm-link" @click="editCond(i)">编辑</a>
          <Popconfirm
            title="删除这条客户条件？"
            ok-text="删除"
            cancel-text="取消"
            @confirm="removeCond(i)"
          >
            <a class="ap-crm-link ap-crm-link--danger">删除</a>
          </Popconfirm>
          <Switch
            :checked="c.enabled"
            @change="
              (v: any) => {
                c.enabled = !!v;
                persist();
              }
            "
          />
        </div>
      </div>
    </div>

    <!-- Tab 3：AI 工作时间 -->
    <div v-else-if="tab === 'worktime'">
      <WorkTime />
    </div>

    <!-- Tab 4：限频与黑名单 -->
    <div v-else-if="tab === 'blacklist'">
      <Blacklist />
    </div>

    <!-- Tab 5：人工介入规则 -->
    <div v-else-if="tab === 'takeover' && h" class="ho__card">
      <div class="ho__master">
        <div class="flex min-w-0 items-center gap-3">
          <span class="ho__master-icon is-green">
            <IconifyIcon icon="lucide:hand-helping" class="size-5" />
          </span>
          <div>
            <div class="text-[15px] font-bold text-[#1D1D1F]">
              真人销售跨端介入后的托管礼让规则
            </div>
            <div class="mt-0.5 text-xs text-[#6E6E73]">
              当业务员直接用手机 WhatsApp 或外部邮箱客户端回复某位买家时，系统自动调整该会话的 AI 托管状态，防止人机抢话
            </div>
          </div>
        </div>
      </div>

      <div class="space-y-3">
        <div
          class="ho__mode-card"
          :class="{ on: h.takeoverMode === 'pause' }"
          @click="h.takeoverMode = 'pause'"
        >
          <span class="ho__radio"></span>
          <div class="min-w-0 flex-1">
            <div class="flex flex-wrap items-center gap-2 font-semibold text-[#1D1D1F]">
              <span>智能暂停托管，超时自动恢复（推荐）</span>
            </div>
            <div class="mt-1.5 flex flex-wrap items-center gap-2 text-xs text-[#6E6E73]">
              <span>检测到业务员手动发信后暂停 AI 自动回复，静默等待</span>
              <InputNumber
                v-model:value="pauseHours"
                :min="1"
                :max="720"
                size="small"
                class="w-20"
                :disabled="h.takeoverMode !== 'pause'"
                @click.stop
              />
              <span>小时后自动恢复 AI 值守</span>
            </div>
          </div>
        </div>

        <div
          class="ho__mode-card"
          :class="{ on: h.takeoverMode === 'off' }"
          @click="h.takeoverMode = 'off'"
        >
          <span class="ho__radio"></span>
          <div class="min-w-0 flex-1">
            <div class="font-semibold text-[#1D1D1F]">
              永久移交真人，关闭该买家的 AI 托管
            </div>
            <div class="mt-1 text-xs text-[#6E6E73]">
              一旦业务员介入，后续往来全部由业务员亲自跟进，除非在会话详情中手动重开托管
            </div>
          </div>
        </div>

        <div
          class="ho__mode-card"
          :class="{ on: h.takeoverMode === 'none' }"
          @click="h.takeoverMode = 'none'"
        >
          <span class="ho__radio"></span>
          <div class="min-w-0 flex-1">
            <div class="font-semibold text-[#1D1D1F]">
              不改变状态，AI 继续全天候自动回复
            </div>
            <div class="mt-1 text-xs text-[#6E6E73]">
              即使业务员在手机或邮箱客户端发过消息，买家后续提问仍由 AI 自动应答
            </div>
          </div>
        </div>
      </div>

      <div class="mt-5 flex justify-end">
        <Button type="primary" shape="round" @click="saveTakeover">
          保存介入规则
        </Button>
      </div>
    </div>

    <!-- 弹窗 1：自定义转人工项（Apple Sheet） -->
    <Modal
      v-model:open="customOpen"
      :title="customIndex >= 0 ? '编辑转人工规则' : '添加转人工规则'"
      :width="520"
      ok-text="保存规则"
      cancel-text="取消"
      wrap-class-name="ap-modal-sheet"
      @ok="saveCustom"
    >
      <div class="ap-sheet-lead">
        <span class="ap-sheet-icon">
          <IconifyIcon icon="lucide:user-round-check" class="size-5" />
        </span>
        <div class="ap-sheet-subtitle">
          直接用自然语言描述买家表现出什么意图时应转交真人处理。
        </div>
      </div>
      <div class="ap-sheet-body">
        <div class="ap-sheet-field">
          <div class="ap-sheet-label"><span>触发条件描述 *</span></div>
          <Textarea
            v-model:value="customDraft.text"
            :rows="3"
            :maxlength="200"
            show-count
            placeholder="例如：买家询问能否签某国独家代理协议，或要求定制开私模"
          />
        </div>
        <div class="ap-sheet-field">
          <div class="ap-sheet-label"><span>在待办工作台归类为</span></div>
          <Select
            v-model:value="customDraft.type"
            :options="TYPES.map((t) => ({ value: t.value, label: t.label }))"
          />
        </div>
      </div>
    </Modal>

    <!-- 弹窗 2：新建客户条件（Apple Sheet） -->
    <Modal
      v-model:open="condOpen"
      :title="condIndex >= 0 ? '编辑客户条件' : '新建客户条件'"
      :width="520"
      ok-text="保存条件"
      cancel-text="取消"
      wrap-class-name="ap-modal-sheet"
      @ok="saveCond"
    >
      <div class="ap-sheet-lead">
        <span class="ap-sheet-icon ap-sheet-icon--purple">
          <IconifyIcon icon="lucide:crown" class="size-5" />
        </span>
        <div class="ap-sheet-subtitle">
          匹配该条件的客户来信时，AI 只为您生成草稿供确认，不直接自动发出。
        </div>
      </div>
      <div class="ap-sheet-body">
        <div class="ap-sheet-field">
          <div class="ap-sheet-label">
            <span>规则名称</span>
            <span class="ap-sheet-optional">选填，留空自动生成</span>
          </div>
          <Input
            v-model:value="condDraft.name"
            :maxlength="40"
            placeholder="例如：A 级重点客户保护"
          />
        </div>
        <div class="ap-sheet-field">
          <div class="ap-sheet-label"><span>匹配维度</span></div>
          <Select
            v-model:value="condDraft.field"
            :options="FIELDS"
            @change="condDraft.values = []"
          />
        </div>
        <div v-if="condDraft.field !== 'deal'" class="ap-sheet-field">
          <div class="ap-sheet-label"><span>匹配取值（可多选）</span></div>
          <Select
            v-model:value="condDraft.values"
            mode="multiple"
            placeholder="选择对应的级别或来源…"
            :options="valueOptions"
          />
        </div>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.ho {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.ho__page-head {
  padding: 2px 2px 0;
}

.ho__title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #1d1d1f;
  letter-spacing: -0.015em;
}

.ho__sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: #6e6e73;
}

.ho__nav {
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

.ho__nav-btn {
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

.ho__nav-btn:hover:not(.on) {
  color: #1d1d1f;
  background: rgb(0 0 0 / 3.5%);
}

.ho__nav-btn.on {
  font-weight: 600;
  color: #1d1d1f;
  background: #fff;
  box-shadow: 0 2px 6px rgb(0 0 0 / 8%);
}

.ho__card {
  padding: 20px 22px;
  background: #fff;
  border-radius: 18px;
  box-shadow: var(--ap-shadow-rest);
}

.ho__master {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 16px;
  margin-bottom: 16px;
  border-bottom: 0.5px solid var(--ap-separator);
}

.ho__master-icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 12px;
}

.ho__master-icon.is-blue {
  color: #007aff;
  background: #e8f2ff;
}

.ho__master-icon.is-purple {
  color: #7c3aed;
  background: #f3eeff;
}

.ho__master-icon.is-green {
  color: #1f9d4c;
  background: #eaf9ee;
}

.ho__block {
  padding: 16px 18px;
  margin-bottom: 14px;
  background: #f7f8fb;
  border-radius: 15px;
}

.ho__block:last-child {
  margin-bottom: 0;
}

.ho__h {
  font-size: 14.5px;
  font-weight: 700;
  color: #1d1d1f;
}

.ho__tip {
  margin: 3px 0 10px;
  font-size: 12px;
  color: #6e6e73;
}

.ho__add-btn {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 32px;
  padding: 0 14px;
  font-size: 12.5px;
  font-weight: 600;
  color: #fff;
  cursor: pointer;
  background: linear-gradient(180deg, #1a88ff 0%, #007aff 100%);
  border: 0;
  border-radius: 999px;
  box-shadow: 0 2px 6px rgb(0 122 255 / 22%);
}

.ho__empty-inline {
  padding: 14px;
  font-size: 12.5px;
  color: #8e8e93;
  text-align: center;
  background: #fff;
  border-radius: 11px;
}

.ho__row-card {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 10px 13px;
  background: #fff;
  border-radius: 11px;
  box-shadow: 0 1px 2px rgb(0 0 0 / 3%);
}

.ho__empty-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 44px 20px;
  text-align: center;
  background: #f7f8fb;
  border-radius: 15px;
}

.ho__cond-card {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 13px 16px;
  background: #f7f8fb;
  border-radius: 13px;
}

/* 介入礼让卡片 */
.ho__mode-card {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 15px 16px;
  cursor: pointer;
  background: #f7f8fb;
  border: 1.5px solid transparent;
  border-radius: 14px;
  transition: all var(--ap-dur-fast);
}

.ho__mode-card.on {
  background: rgb(0 122 255 / 5%);
  border-color: #007aff;
}

.ho__radio {
  flex: none;
  width: 17px;
  height: 17px;
  margin-top: 2px;
  background: #fff;
  border: 1.5px solid #c7c7cc;
  border-radius: 50%;
}

.ho__mode-card.on .ho__radio {
  border-color: #007aff;
  border-width: 5px;
}
</style>
