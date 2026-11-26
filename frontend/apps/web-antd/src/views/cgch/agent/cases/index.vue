<script lang="ts" setup>
/**
 * 成交案例库：AI 从已成交客户的往来记录中提炼成交关键点、顾虑拆解与高转化原话；
 * 无外层冗余卡片包裹，无缝内嵌在「教话术」面板中。
 */
import type { ChannelCaseApi } from '#/api/channel/cases';

import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import {
  Button,
  Input,
  message,
  Modal,
  Popconfirm,
  Select,
  Switch,
  Textarea,
} from 'ant-design-vue';

import {
  deleteCase,
  enableCase,
  generateCase,
  getCaseCandidates,
  getCaseList,
  saveCase,
} from '#/api/channel/cases';
import { fmtDateTime } from '#/utils/datetime';
import { countryName } from '#/views/cgch/v3/crm/components/shared';

defineOptions({ name: 'CgchAgentCases' });

const router = useRouter();
const list = ref<ChannelCaseApi.Case[]>([]);
const candidates = ref<ChannelCaseApi.Candidate[]>([]);
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    [list.value, candidates.value] = await Promise.all([
      getCaseList(),
      getCaseCandidates(),
    ]);
  } finally {
    loading.value = false;
  }
}
const pendingCandidates = computed(() =>
  candidates.value.filter((c) => !c.hasCase),
);

// ===== AI 生成 =====
const pick = ref<number>();
const generating = ref(false);

async function generate() {
  if (!pick.value) {
    message.warning('请先选择一位已成交客户');
    return;
  }
  generating.value = true;
  try {
    const id = await generateCase(pick.value);
    message.success('案例已生成，请核对内容后开启参考');
    pick.value = undefined;
    await load();
    const c = list.value.find((x) => x.id === id);
    if (c) openEdit(c);
  } finally {
    generating.value = false;
  }
}

// ===== 编辑 =====
const editOpen = ref(false);
const saving = ref(false);
const form = reactive({
  id: undefined as number | undefined,
  title: '',
  industry: '',
  product: '',
  countryCode: '',
  summary: '',
  keyPoints: '',
  objections: '',
  phrases: '',
  enabled: false,
  customerName: '',
});
const lines = (s: string) =>
  s
    .split('\n')
    .map((x) => x.trim())
    .filter(Boolean);

function openEdit(c?: ChannelCaseApi.Case) {
  Object.assign(form, {
    id: c?.id,
    title: c?.title ?? '',
    industry: c?.industry ?? '',
    product: c?.product ?? '',
    countryCode: c?.countryCode ?? '',
    summary: c?.summary ?? '',
    keyPoints: (c?.keyPoints ?? []).join('\n'),
    objections: (c?.objections ?? []).join('\n'),
    phrases: (c?.phrases ?? []).join('\n'),
    enabled: c?.enabled ?? false,
    customerName: c?.customerName ?? '',
  });
  editOpen.value = true;
}

async function save() {
  if (!form.title.trim()) {
    message.warning('请填写案例标题');
    return;
  }
  saving.value = true;
  try {
    await saveCase({
      ...form,
      keyPoints: lines(form.keyPoints),
      objections: lines(form.objections),
      phrases: lines(form.phrases),
    });
    message.success('案例已保存');
    editOpen.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function toggle(c: ChannelCaseApi.Case, v: boolean) {
  await enableCase(c.id!, v);
  c.enabled = v;
  message.success(v ? '已开启，AI 回复同类询盘时将自动借鉴' : '已暂停参考');
}

async function remove(c: ChannelCaseApi.Case) {
  await deleteCase(c.id!);
  message.success('案例已删除');
  await load();
}

onMounted(load);
</script>

<template>
  <div class="cs">
    <div class="cs__head">
      <div class="flex min-w-0 items-center gap-3">
        <span class="cs__icon">
          <IconifyIcon icon="lucide:trophy" class="size-5" />
        </span>
        <div>
          <div class="text-[15px] font-bold text-[#1D1D1F]">实战成交案例库</div>
          <div class="mt-0.5 text-xs text-[#6E6E73]">
            自动脱敏原客户名称、联系方式与具体价格，仅将打单思路、异议拆解与高转化话术提供给 AI 借鉴
          </div>
        </div>
      </div>
      <Button shape="round" size="small" @click="openEdit()">
        <IconifyIcon icon="lucide:plus" class="mr-1 inline size-3.5" />
        手动录入案例
      </Button>
    </div>

    <!-- AI 一键提炼横幅 -->
    <div class="cs__gen-bar">
      <div class="flex flex-wrap items-center gap-2.5 flex-1">
        <span class="ap-crm-pill ap-crm-pill--purple">
          <IconifyIcon icon="lucide:sparkles" class="size-3" />
          AI 自动复盘
        </span>
        <Select
          v-model:value="pick"
          show-search
          option-filter-prop="label"
          placeholder="选择一位已成交客户，自动读取邮件与 WhatsApp 往来…"
          class="w-72"
          :options="
            pendingCandidates.map((c) => ({
              value: c.id,
              label: c.companyName ? `${c.name}（${c.companyName}）` : c.name,
            }))
          "
          :not-found-content="
            candidates.length
              ? '全部已成交客户均已生成案例'
              : '暂无已成交客户（可在客户详情标记成交）'
          "
        />
        <Button
          type="primary"
          shape="round"
          :loading="generating"
          @click="generate"
        >
          AI 生成案例
        </Button>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-if="!loading && list.length === 0" class="cs__empty">
      <IconifyIcon icon="lucide:book-marked" class="size-7 text-[#8E8E93] opacity-60" />
      <div class="mt-2 font-semibold text-[#1D1D1F]">还没有成交案例</div>
      <div class="mt-1 text-xs text-[#86868B]">
        从上方选择一位已成交客户让 AI 自动复盘，或点击右上角手动添加标杆案例
      </div>
    </div>

    <!-- 案例卡片列表 -->
    <div v-else class="cs__grid">
      <div v-for="c in list" :key="c.id" class="cs-card">
        <div class="flex items-start justify-between gap-2">
          <div class="min-w-0">
            <div class="flex flex-wrap items-center gap-1.5">
              <span class="text-[14.5px] font-bold text-[#1D1D1F]">
                {{ c.title }}
              </span>
              <span
                v-if="c.source === 'ai'"
                class="ap-crm-pill ap-crm-pill--purple"
              >
                AI 提炼
              </span>
            </div>
            <div class="mt-1.5 flex flex-wrap gap-1">
              <span v-if="c.industry" class="ap-crm-pill ap-crm-pill--blue">
                {{ c.industry }}
              </span>
              <span v-if="c.product" class="ap-crm-pill ap-crm-pill--green">
                {{ c.product }}
              </span>
              <span v-if="c.countryCode" class="ap-crm-pill ap-crm-pill--gray">
                {{ countryName(c.countryCode) || c.countryCode }}
              </span>
            </div>
          </div>

          <div class="flex shrink-0 items-center gap-1.5">
            <span class="text-[11px] text-[#86868B]">供 AI 参考</span>
            <Switch
              :checked="c.enabled"
              size="small"
              @change="(v: any) => toggle(c, !!v)"
            />
          </div>
        </div>

        <p v-if="c.summary" class="cs-card__summary">{{ c.summary }}</p>

        <div v-if="c.keyPoints.length" class="cs-section">
          <div class="cs-section__title is-green">成交关键点</div>
          <ul>
            <li v-for="(x, i) in c.keyPoints" :key="i">{{ x }}</li>
          </ul>
        </div>

        <div v-if="c.objections.length" class="cs-section">
          <div class="cs-section__title is-amber">买家顾虑与拆解</div>
          <ul>
            <li v-for="(x, i) in c.objections" :key="i">{{ x }}</li>
          </ul>
        </div>

        <div v-if="c.phrases.length" class="cs-section">
          <div class="cs-section__title is-blue">高转化原话摘录</div>
          <div class="space-y-1">
            <div v-for="(x, i) in c.phrases" :key="i" class="cs-quote">
              “{{ x }}”
            </div>
          </div>
        </div>

        <div class="cs-card__foot">
          <span class="truncate text-[11.5px] text-[#8E8E93]">
            <a
              v-if="c.customerId"
              class="ap-crm-link text-[11.5px]"
              @click="router.push(`/crm/customer/detail/${c.customerId}`)"
            >
              来源：{{ c.customerName }}（仅内部可见）
            </a>
            <template v-else>手动录入</template>
            · {{ fmtDateTime(c.createTime, 'YYYY-MM-DD') }}
          </span>
          <div class="flex items-center gap-2.5">
            <a class="ap-crm-link" @click="openEdit(c)">编辑</a>
            <Popconfirm
              title="确定删除这个成交案例？"
              ok-text="删除"
              cancel-text="取消"
              @confirm="remove(c)"
            >
              <a class="ap-crm-link ap-crm-link--danger">删除</a>
            </Popconfirm>
          </div>
        </div>
      </div>
    </div>

    <!-- 编辑/新增案例弹窗（Apple Sheet） -->
    <Modal
      v-model:open="editOpen"
      :title="form.id ? '编辑成交案例' : '新增成交案例'"
      :width="720"
      ok-text="保存案例"
      cancel-text="取消"
      :confirm-loading="saving"
      wrap-class-name="ap-modal-sheet"
      @ok="save"
    >
      <div class="ap-sheet-lead">
        <span class="ap-sheet-icon ap-sheet-icon--green">
          <IconifyIcon icon="lucide:trophy" class="size-5" />
        </span>
        <div class="ap-sheet-subtitle">
          保存时系统会自动脱敏原客户姓名、邮箱、电话与具体成交价，确保对外沟通绝对安全。
        </div>
      </div>

      <div class="ap-sheet-body">
        <div class="ap-sheet-field">
          <div class="ap-sheet-label"><span>案例标题 *</span></div>
          <Input
            v-model:value="form.title"
            :maxlength="200"
            placeholder="例如：北美连锁商超 5000 台定制小家电首单破冰"
          />
        </div>

        <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <div class="ap-sheet-field">
            <div class="ap-sheet-label"><span>买家类型 / 行业</span></div>
            <Input
              v-model:value="form.industry"
              placeholder="例如：澳洲家电分销商"
            />
          </div>
          <div class="ap-sheet-field">
            <div class="ap-sheet-label"><span>成交产品</span></div>
            <Input v-model:value="form.product" placeholder="高速吹风机" />
          </div>
          <div class="ap-sheet-field">
            <div class="ap-sheet-label"><span>国家代码</span></div>
            <Input v-model:value="form.countryCode" placeholder="US / AU / DE" />
          </div>
        </div>

        <div class="ap-sheet-field">
          <div class="ap-sheet-label"><span>客户背景与推进过程</span></div>
          <Textarea v-model:value="form.summary" :rows="2" />
        </div>

        <div class="ap-sheet-field">
          <div class="ap-sheet-label">
            <span>成交关键点</span>
            <span class="ap-sheet-optional">每行一条</span>
          </div>
          <Textarea v-model:value="form.keyPoints" :rows="3" />
        </div>

        <div class="ap-sheet-field">
          <div class="ap-sheet-label">
            <span>买家顾虑与我方回应</span>
            <span class="ap-sheet-optional">每行一条</span>
          </div>
          <Textarea
            v-model:value="form.objections"
            :rows="3"
            placeholder="顾虑：定制开模周期太长；回应：先发公模现货 200 台测款"
          />
        </div>

        <div class="ap-sheet-field">
          <div class="ap-sheet-label">
            <span>高转化有效原话</span>
            <span class="ap-sheet-optional">每行一条</span>
          </div>
          <Textarea v-model:value="form.phrases" :rows="3" />
        </div>

        <div class="flex items-center justify-between rounded-xl bg-[#F4F5F8] px-3.5 py-2.5">
          <span class="text-[13px] font-medium text-[#1D1D1F]">
            开启供 AI 回复同类询盘时参考
          </span>
          <Switch v-model:checked="form.enabled" />
        </div>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.cs {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.cs__head {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 14px;
  border-bottom: 0.5px solid var(--ap-separator);
}

.cs__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 42px;
  height: 42px;
  color: #d97706;
  background: #fff4e0;
  border-radius: 12px;
}

.cs__gen-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 12px 15px;
  background: linear-gradient(135deg, #f6f4ff 0%, #eff6ff 100%);
  border-radius: 14px;
  box-shadow: inset 0 0 0 0.5px rgb(88 86 214 / 14%);
}

.cs__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 44px 20px;
  text-align: center;
  background: #f7f8fb;
  border-radius: 16px;
}

.cs__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.cs-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 18px;
  background: #f7f8fb;
  border-radius: 16px;
}

.cs-card__summary {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.6;
  color: #48484a;
}

.cs-section {
  padding: 10px 12px;
  background: #fff;
  border-radius: 11px;
}

.cs-section__title {
  margin-bottom: 4px;
  font-size: 11.5px;
  font-weight: 700;
}

.cs-section__title.is-green {
  color: #1f9d4c;
}

.cs-section__title.is-amber {
  color: #b25e00;
}

.cs-section__title.is-blue {
  color: #007aff;
}

.cs-section ul {
  padding-left: 16px;
  margin: 0;
  font-size: 12.5px;
  line-height: 1.55;
  color: #3a3a3c;
}

.cs-quote {
  padding: 5px 9px;
  font-size: 12px;
  font-style: italic;
  color: #3a3a3c;
  background: #f5f7fc;
  border-left: 2.5px solid #007aff;
  border-radius: 4px 8px 8px 4px;
}

.cs-card__foot {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding-top: 8px;
  margin-top: auto;
  border-top: 0.5px solid rgb(0 0 0 / 6%);
}

@media (width <= 960px) {
  .cs__grid {
    grid-template-columns: 1fr;
  }
}
</style>
