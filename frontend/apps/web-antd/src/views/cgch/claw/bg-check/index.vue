<script lang="ts" setup>
/**
 * 深度背调（Apple Intelligence 双栏情报工作台）：
 * 左侧发起背调与历史记录（支持官网 / CRM 客户 / 线索 / 社交主页），
 * 右侧结构化情报报告（核心画像、最近采购、报价与付款策略、六维洞察及溯源链接）。
 */
import type { ChannelResearchApi } from '#/api/channel/research';

import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import { Input, message, Popconfirm, Select, Spin } from 'ant-design-vue';

import {
  applyResearch,
  createResearch,
  deleteResearch,
  getResearch,
  getResearchPage,
} from '#/api/channel/research';
import { getCluePage } from '#/api/crm/clue';
import { getCustomerSimpleList } from '#/api/crm/customer';
import { fmtDateTime } from '#/utils/datetime';
import {
  countryName,
  crmAvatarBg,
  crmInitial,
} from '#/views/cgch/v3/crm/components/shared';

defineOptions({ name: 'CgchClawBgCheck' });

const route = useRoute();
const router = useRouter();

const BUSINESS_TYPE: Record<string, string> = {
  manufacturer: '生产商',
  brand: '品牌商',
  distributor: '分销商',
  wholesaler: '批发商',
  retailer: '零售商',
  'e-commerce': '电商卖家',
  trading: '贸易商',
  other: '其他',
};

const CONFIDENCE: Record<string, { pill: string; label: string }> = {
  high: { pill: 'ap-crm-pill--green', label: '资料充分' },
  medium: { pill: 'ap-crm-pill--blue', label: '资料一般' },
  low: { pill: 'ap-crm-pill--amber', label: '资料较少' },
};

const BUYER_KIND: Record<string, { pill: string; label: string }> = {
  trader: { pill: 'ap-crm-pill--purple', label: '贸易商' },
  end_user: { pill: 'ap-crm-pill--green', label: '终端工厂 / 自用' },
  unknown: { pill: 'ap-crm-pill--gray', label: '类型待定' },
};

const SOURCE_TYPE: Record<string, { pill: string; label: string }> = {
  website: { pill: 'ap-crm-pill--gray', label: '客户官网' },
  search: { pill: 'ap-crm-pill--blue', label: '联网检索' },
};

// ===== 发起 =====
const target = ref<'clue' | 'customer' | 'website'>('website');
const form = reactive({
  website: '',
  companyName: '',
  facebookUrl: '',
  linkedinUrl: '',
  customerId: undefined as number | undefined,
  clueId: undefined as number | undefined,
});
const customers = ref<{ id: number; name: string }[]>([]);
const clues = ref<{ id: number; name: string }[]>([]);
const creating = ref(false);
const showSocial = ref(false);

async function searchClues(kw: string) {
  clues.value = (
    await getCluePage({ pageNo: 1, pageSize: 20, name: kw, sceneType: 1 } as any)
  ).list as any;
}

async function start() {
  creating.value = true;
  try {
    const id = await createResearch({
      website: form.website || undefined,
      companyName: form.companyName || undefined,
      facebookUrl: form.facebookUrl || undefined,
      linkedinUrl: form.linkedinUrl || undefined,
      customerId: target.value === 'customer' ? form.customerId : undefined,
      clueId: target.value === 'clue' ? form.clueId : undefined,
    });
    message.success('已开始背调，通常需要半分钟左右');
    await loadList();
    await open(id);
  } finally {
    creating.value = false;
  }
}

// ===== 列表与详情 =====
const list = ref<ChannelResearchApi.Research[]>([]);
async function loadList() {
  list.value = (await getResearchPage({ pageNo: 1, pageSize: 30 })).list;
}
const current = ref<ChannelResearchApi.Research>();
const loading = ref(false);
let timer: ReturnType<typeof setInterval> | undefined;

async function open(id: number) {
  loading.value = true;
  try {
    current.value = await getResearch(id);
  } finally {
    loading.value = false;
  }
  if (timer) clearInterval(timer);
  if (current.value?.status === 'running') {
    timer = setInterval(async () => {
      current.value = await getResearch(id);
      if (current.value.status !== 'running') {
        clearInterval(timer);
        timer = undefined;
        loadList();
      }
    }, 2000);
  }
}
onBeforeUnmount(() => timer && clearInterval(timer));

const rep = computed(() => current.value?.report);

const sections = computed(
  () =>
    [
      {
        key: 'painPoints',
        title: '核心痛点推测',
        tip: '交期、价格、新品开发还是行业认证',
        icon: 'lucide:flame',
        tint: 'amber',
      },
      {
        key: 'purchasingSignals',
        title: '采购意向信号',
        tip: '对方近期可能向我方采购的品类方向',
        icon: 'lucide:shopping-bag',
        tint: 'green',
      },
      {
        key: 'talkingPoints',
        title: '破冰沟通切入点',
        tip: '首次联系或跟进时最容易引起共鸣的话题',
        icon: 'lucide:message-circle-heart',
        tint: 'blue',
      },
      {
        key: 'creditSignals',
        title: '付款与信用线索',
        tip: '成立年限、团队规模与公开履约线索',
        icon: 'lucide:landmark',
        tint: 'indigo',
      },
      {
        key: 'risks',
        title: '潜在风险提示',
        tip: '报价与签约前需要重点核实的事项',
        icon: 'lucide:triangle-alert',
        tint: 'red',
      },
      {
        key: 'modelKnowledge',
        title: 'AI 补充认知（需核实）',
        tip: '大模型公开知识库补充信息，未经一手网页验证',
        icon: 'lucide:sparkles',
        tint: 'purple',
      },
    ] as const,
);

function recordTitle(r: ChannelResearchApi.Research) {
  return (
    r.companyName ||
    r.website ||
    r.facebookUrl ||
    r.linkedinUrl ||
    `背调 #${r.id}`
  );
}

function recordSub(r: ChannelResearchApi.Research) {
  return r.website || r.facebookUrl || r.linkedinUrl || '企业档案核验';
}

function ensureHref(url?: string) {
  if (!url) return '';
  return /^https?:\/\//i.test(url) ? url : `https://${url}`;
}

async function apply() {
  if (!current.value) return;
  const changed = await applyResearch(current.value.id);
  message.success(
    changed.length
      ? `已同步至客户档案：${changed.join('、')}`
      : '客户资料已是最新，无需更新',
  );
}

async function remove() {
  if (!current.value) return;
  await deleteResearch(current.value.id);
  current.value = undefined;
  message.success('背调记录已删除');
  await loadList();
}

watch(
  () => route.query.researchId,
  (v) => {
    const id = Number(v);
    if (id && id !== current.value?.id) open(id);
  },
);

onMounted(async () => {
  customers.value = (await getCustomerSimpleList()) as any;
  await loadList();
  const cid = Number(route.query.customerId);
  const lid = Number(route.query.clueId);
  if (cid) {
    target.value = 'customer';
    form.customerId = cid;
  } else if (lid) {
    target.value = 'clue';
    form.clueId = lid;
    await searchClues('');
  }
  const rid = Number(route.query.researchId);
  if (rid) await open(rid);
  else if (list.value[0]) await open(list.value[0].id);
});
</script>

<template>
  <div class="bgc">
    <!-- 左栏：发起背调 + 背调记录 -->
    <aside class="bgc__left">
      <div class="bgc-card bgc-card--form">
        <div class="bgc-card__head">
          <span class="bgc-card__badge">
            <IconifyIcon icon="lucide:radar" class="size-4" />
          </span>
          <div>
            <div class="bgc-card__title">发起深度背调</div>
            <div class="bgc-card__sub">结合官网、联网搜索与往来记录核验</div>
          </div>
        </div>

        <div class="bgc-seg">
          <button
            type="button"
            :class="{ on: target === 'website' }"
            @click="target = 'website'"
          >
            官网 / 公司
          </button>
          <button
            type="button"
            :class="{ on: target === 'customer' }"
            @click="target = 'customer'"
          >
            CRM 客户
          </button>
          <button
            type="button"
            :class="{ on: target === 'clue' }"
            @click="target = 'clue'"
          >
            线索
          </button>
        </div>

        <div class="bgc-fields">
          <div v-if="target === 'customer'" class="bgc-field">
            <label>选择 CRM 客户</label>
            <Select
              v-model:value="form.customerId"
              show-search
              option-filter-prop="label"
              placeholder="搜索并选择客户…"
              :options="customers.map((c) => ({ value: c.id, label: c.name }))"
            />
          </div>

          <div v-if="target === 'clue'" class="bgc-field">
            <label>选择线索</label>
            <Select
              v-model:value="form.clueId"
              show-search
              :filter-option="false"
              placeholder="输入线索名称搜索…"
              :options="clues.map((c) => ({ value: c.id, label: c.name }))"
              @search="searchClues"
              @focus="searchClues('')"
            />
          </div>

          <div class="bgc-field">
            <label>
              <span>客户官网</span>
              <small>选填</small>
            </label>
            <Input
              v-model:value="form.website"
              placeholder="例如：example.com"
              allow-clear
            >
              <template #prefix>
                <IconifyIcon icon="lucide:globe" class="size-3.5 text-[#8E8E93]" />
              </template>
            </Input>
          </div>

          <div v-if="target === 'website'" class="bgc-field">
            <label>
              <span>公司名称</span>
              <small>官网 / 社媒 / 公司名至少填一项</small>
            </label>
            <Input
              v-model:value="form.companyName"
              placeholder="例如：Acme Home Appliances Ltd."
              allow-clear
            >
              <template #prefix>
                <IconifyIcon
                  icon="lucide:building-2"
                  class="size-3.5 text-[#8E8E93]"
                />
              </template>
            </Input>
          </div>

          <button
            type="button"
            class="bgc-social-toggle"
            @click="showSocial = !showSocial"
          >
            <span>补充 Facebook / LinkedIn 社交主页</span>
            <IconifyIcon
              :icon="showSocial ? 'lucide:chevron-up' : 'lucide:chevron-down'"
              class="size-3.5"
            />
          </button>

          <template v-if="showSocial || form.facebookUrl || form.linkedinUrl">
            <div class="bgc-field">
              <label><span>Facebook 主页</span><small>选填</small></label>
              <Input
                v-model:value="form.facebookUrl"
                placeholder="facebook.com/xxx 或 @xxx"
                allow-clear
              />
            </div>
            <div class="bgc-field">
              <label><span>LinkedIn 主页</span><small>选填</small></label>
              <Input
                v-model:value="form.linkedinUrl"
                placeholder="linkedin.com/company/xxx"
                allow-clear
              />
            </div>
          </template>
        </div>

        <button
          type="button"
          class="bgc-submit"
          :disabled="creating"
          @click="start"
        >
          <IconifyIcon
            :icon="creating ? 'lucide:loader-2' : 'lucide:sparkles'"
            class="size-4"
            :class="{ 'animate-spin': creating }"
          />
          <span>{{ creating ? '正在创建背调任务…' : '开始 AI 深度背调' }}</span>
        </button>

        <div class="bgc-note">
          <IconifyIcon icon="lucide:info" class="mt-0.5 size-3.5 shrink-0 text-[#007AFF]" />
          <span>
            没有官网的客户仅填 Facebook / LinkedIn 主页也可背调，AI 会分析主页简介与动态并标注社交来源。
          </span>
        </div>
      </div>

      <!-- 历史背调列表 -->
      <div class="bgc-card bgc-card--list">
        <div class="bgc-list-head">
          <span>背调记录</span>
          <span class="bgc-count">{{ list.length }}</span>
        </div>

        <div class="bgc-list-scroll">
          <div v-if="list.length === 0" class="bgc-empty-mini">
            <IconifyIcon icon="lucide:inbox" class="size-5 opacity-45" />
            <span>暂无背调记录</span>
          </div>

          <div
            v-for="r in list"
            :key="r.id"
            class="bgc-item"
            :class="{ on: r.id === current?.id }"
            @click="open(r.id)"
          >
            <span
              class="ap-crm-avatar"
              :style="{ background: crmAvatarBg(recordTitle(r)) }"
            >
              {{ crmInitial(recordTitle(r)) }}
            </span>
            <div class="min-w-0 flex-1">
              <div class="flex items-center justify-between gap-2">
                <span class="bgc-item__title">{{ recordTitle(r) }}</span>
                <span
                  v-if="r.status === 'running'"
                  class="ap-crm-pill ap-crm-pill--blue"
                >
                  进行中
                </span>
                <span
                  v-else-if="r.status === 'failed'"
                  class="ap-crm-pill ap-crm-pill--red"
                >
                  失败
                </span>
              </div>
              <div class="bgc-item__sub">
                <span class="truncate">{{ recordSub(r) }}</span>
                <span>· {{ fmtDateTime(r.createTime, 'MM-DD HH:mm') }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </aside>

    <!-- 右栏：背调报告主画布 -->
    <section class="bgc__right">
      <Spin
        :spinning="loading || current?.status === 'running'"
        :tip="
          current?.status === 'running'
            ? 'AI 正在抓取官网、联网检索并生成深度分析报告…'
            : undefined
        "
      >
        <div v-if="!current" class="bgc-empty-stage">
          <div class="bgc-empty-stage__orb">
            <IconifyIcon icon="lucide:radar" class="size-7" />
          </div>
          <h3>全方位洞察海外买家底细</h3>
          <p>
            在左侧输入客户官网、公司名或社交主页，AI 将在 30 秒内为您还原买家类型（终端工厂 / 贸易商）、采购规模、痛点切入话术与报价付款建议。
          </p>
        </div>

        <div v-else class="bgc-report">
          <div v-if="current.status === 'failed'" class="bgc-banner is-error">
            <IconifyIcon icon="lucide:octagon-alert" class="size-4 shrink-0" />
            <span>背调未能完成：{{ current.reasonDetail || '请检查网址或稍后重试' }}</span>
          </div>
          <div v-else-if="current.reasonDetail" class="bgc-banner is-warn">
            <IconifyIcon icon="lucide:triangle-alert" class="size-4 shrink-0" />
            <span>{{ current.reasonDetail }}</span>
          </div>

          <!-- 报告主头卡 -->
          <div v-if="rep" class="bgc-hero">
            <div class="bgc-hero__top">
              <div class="flex min-w-0 items-start gap-3.5">
                <span
                  class="ap-crm-avatar bgc-hero__avatar"
                  :style="{
                    background: crmAvatarBg(
                      rep.companyName || current.companyName || current.website,
                    ),
                  }"
                >
                  {{
                    crmInitial(
                      rep.companyName || current.companyName || current.website,
                    )
                  }}
                </span>
                <div class="min-w-0">
                  <h2 class="bgc-hero__name">
                    {{ rep.companyName || current.companyName || '客户深度背调报告' }}
                  </h2>
                  <div class="mt-2 flex flex-wrap items-center gap-1.5">
                    <span
                      v-if="rep.buyerKind?.kind"
                      class="ap-crm-pill"
                      :class="
                        BUYER_KIND[rep.buyerKind.kind]?.pill ??
                        'ap-crm-pill--gray'
                      "
                    >
                      {{
                        BUYER_KIND[rep.buyerKind.kind]?.label ??
                        rep.buyerKind.kind
                      }}
                    </span>
                    <span
                      v-if="rep.businessType"
                      class="ap-crm-pill ap-crm-pill--blue"
                    >
                      {{ BUSINESS_TYPE[rep.businessType] ?? rep.businessType }}
                    </span>
                    <span
                      v-if="rep.countryCode"
                      class="ap-crm-pill ap-crm-pill--gray"
                    >
                      <IconifyIcon icon="lucide:map-pin" class="size-3" />
                      {{ countryName(rep.countryCode) || rep.countryCode.toUpperCase() }}
                    </span>
                    <span
                      v-if="rep.confidence"
                      class="ap-crm-pill"
                      :class="
                        CONFIDENCE[rep.confidence]?.pill ?? 'ap-crm-pill--gray'
                      "
                    >
                      {{ CONFIDENCE[rep.confidence]?.label }}
                    </span>
                  </div>
                </div>
              </div>

              <div class="bgc-hero__actions">
                <button
                  v-if="current.customerId"
                  type="button"
                  class="bgc-btn"
                  @click="router.push(`/crm/customer/detail/${current.customerId}`)"
                >
                  <IconifyIcon icon="lucide:external-link" class="size-3.5" />
                  <span>查看客户</span>
                </button>
                <button
                  v-if="current.clueId"
                  type="button"
                  class="bgc-btn"
                  @click="router.push(`/crm/clue/detail/${current.clueId}`)"
                >
                  <IconifyIcon icon="lucide:external-link" class="size-3.5" />
                  <span>查看线索</span>
                </button>
                <button
                  v-if="current.customerId || current.clueId"
                  type="button"
                  class="bgc-btn bgc-btn--primary"
                  @click="apply"
                >
                  <IconifyIcon icon="lucide:check" class="size-3.5" />
                  <span>补充到客户资料</span>
                </button>
                <Popconfirm
                  title="确认删除这份背调报告？"
                  ok-text="删除"
                  cancel-text="取消"
                  @confirm="remove"
                >
                  <button type="button" class="bgc-btn bgc-btn--danger">
                    <IconifyIcon icon="lucide:trash-2" class="size-3.5" />
                    <span>删除</span>
                  </button>
                </Popconfirm>
              </div>
            </div>

            <!-- 核心综述 -->
            <div v-if="rep.summary" class="bgc-summary">
              <div class="bgc-summary__tag">
                <IconifyIcon icon="lucide:sparkles" class="size-3.5" />
                <span>AI 核心综述</span>
              </div>
              <p>{{ rep.summary }}</p>
            </div>

            <!-- 买家定性与最近采购情报 -->
            <div class="bgc-kv-grid">
              <div class="bgc-kv-item bgc-kv-item--span">
                <div class="bgc-kv-label">买家身份研判（贸易商 vs 终端工厂）</div>
                <div class="bgc-kv-val">
                  <b>
                    {{
                      rep.buyerKind?.kind
                        ? (BUYER_KIND[rep.buyerKind.kind]?.label ??
                          rep.buyerKind.kind)
                        : '待核实'
                    }}
                  </b>
                  <span v-if="rep.buyerKind?.reason" class="text-[#6E6E73]">
                    — {{ rep.buyerKind.reason }}
                  </span>
                </div>
              </div>

              <div class="bgc-kv-item bgc-kv-item--span">
                <div class="bgc-kv-label">近期采购线索</div>
                <div class="bgc-kv-val">
                  <template
                    v-if="
                      rep.purchasing &&
                      (rep.purchasing.suppliers?.length ||
                        rep.purchasing.products?.length ||
                        rep.purchasing.volume)
                    "
                  >
                    <div class="flex flex-wrap gap-x-5 gap-y-1">
                      <span v-if="rep.purchasing.suppliers?.length">
                        <b>采购来源：</b>{{ rep.purchasing.suppliers.join('、') }}
                      </span>
                      <span v-if="rep.purchasing.products?.length">
                        <b>采购品类：</b>{{ rep.purchasing.products.join('、') }}
                      </span>
                      <span v-if="rep.purchasing.volume">
                        <b>预估采购量：</b>{{ rep.purchasing.volume }}
                      </span>
                    </div>
                    <div
                      v-if="rep.purchasing.basis"
                      class="mt-1 text-xs text-[#86868B]"
                    >
                      研判依据：{{ rep.purchasing.basis }}
                    </div>
                  </template>
                  <span v-else class="text-[#8E8E93]">暂无公开采购记录</span>
                </div>
              </div>

              <div class="bgc-kv-item">
                <div class="bgc-kv-label">主营产品</div>
                <div class="bgc-kv-val">
                  {{ rep.mainProducts?.join('、') || '—' }}
                </div>
              </div>
              <div class="bgc-kv-item">
                <div class="bgc-kv-label">覆盖市场</div>
                <div class="bgc-kv-val">
                  {{ rep.markets?.join('、') || '—' }}
                </div>
              </div>
              <div class="bgc-kv-item">
                <div class="bgc-kv-label">企业规模线索</div>
                <div class="bgc-kv-val">{{ rep.scale || '—' }}</div>
              </div>
              <div class="bgc-kv-item">
                <div class="bgc-kv-label">行业认证资质</div>
                <div class="bgc-kv-val">
                  {{ rep.certifications?.join('、') || '—' }}
                </div>
              </div>
              <div class="bgc-kv-item">
                <div class="bgc-kv-label">官网与社媒主页</div>
                <div class="bgc-kv-val flex flex-wrap gap-2">
                  <a
                    v-if="current.website"
                    :href="ensureHref(current.website)"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="bgc-ext-link"
                  >
                    <IconifyIcon icon="lucide:globe" class="size-3" />
                    <span>官网 ↗</span>
                  </a>
                  <a
                    v-if="current.facebookUrl"
                    :href="ensureHref(current.facebookUrl)"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="bgc-ext-link"
                  >
                    <span>Facebook ↗</span>
                  </a>
                  <a
                    v-if="current.linkedinUrl"
                    :href="ensureHref(current.linkedinUrl)"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="bgc-ext-link"
                  >
                    <span>LinkedIn ↗</span>
                  </a>
                  <span
                    v-if="
                      !current.website &&
                      !current.facebookUrl &&
                      !current.linkedinUrl
                    "
                    class="text-[#8E8E93]"
                  >
                    —
                  </span>
                </div>
              </div>
              <div class="bgc-kv-item">
                <div class="bgc-kv-label">公开联系方式</div>
                <div class="bgc-kv-val">
                  {{ rep.companyContacts?.join('；') || '—' }}
                </div>
              </div>
            </div>
          </div>

          <!-- 三列核心商务建议（报价 / 付款 / 推荐产品） -->
          <div
            v-if="
              rep &&
              (rep.quoteAdvice ||
                rep.paymentAdvice ||
                rep.recommendedProducts?.length)
            "
            class="bgc-advice-grid"
          >
            <div class="bgc-subcard">
              <div class="bgc-subcard__head">
                <span class="bgc-subcard__icon is-blue">
                  <IconifyIcon icon="lucide:badge-dollar-sign" class="size-4" />
                </span>
                <span>报价策略建议</span>
              </div>
              <p class="bgc-subcard__text">
                {{ rep.quoteAdvice || '资料不足，暂无具体报价梯度建议' }}
              </p>
            </div>

            <div class="bgc-subcard">
              <div class="bgc-subcard__head">
                <span class="bgc-subcard__icon is-green">
                  <IconifyIcon icon="lucide:shield-check" class="size-4" />
                </span>
                <span>账期与付款建议</span>
              </div>
              <p class="bgc-subcard__text">
                {{ rep.paymentAdvice || '资料不足，建议按常规预付款条件执行' }}
              </p>
            </div>

            <div class="bgc-subcard">
              <div class="bgc-subcard__head">
                <span class="bgc-subcard__icon is-purple">
                  <IconifyIcon icon="lucide:package-check" class="size-4" />
                </span>
                <span>推荐主推产品</span>
              </div>
              <ul v-if="rep.recommendedProducts?.length" class="bgc-bullets">
                <li v-for="(p, i) in rep.recommendedProducts" :key="i">
                  <b>{{ p.name }}</b>
                  <span v-if="p.reason" class="text-[#6E6E73]">
                    ：{{ p.reason }}
                  </span>
                </li>
              </ul>
              <p v-else class="bgc-subcard__empty">知识库暂无直接匹配的产品</p>
            </div>
          </div>

          <!-- 六维深度洞察卡片 -->
          <div v-if="rep" class="bgc-insight-grid">
            <div v-for="s in sections" :key="s.key" class="bgc-subcard">
              <div class="bgc-subcard__head">
                <span class="bgc-subcard__icon" :class="`is-${s.tint}`">
                  <IconifyIcon :icon="s.icon" class="size-4" />
                </span>
                <div class="min-w-0">
                  <div>{{ s.title }}</div>
                  <small>{{ s.tip }}</small>
                </div>
              </div>
              <ul v-if="rep[s.key]?.length" class="bgc-bullets">
                <li v-for="(x, i) in rep[s.key]" :key="i">{{ x }}</li>
              </ul>
              <p v-else class="bgc-subcard__empty">暂无明显线索</p>
            </div>
          </div>

          <!-- 参考来源溯源 -->
          <div v-if="current.sources?.length" class="bgc-subcard">
            <div class="bgc-subcard__head mb-3">
              <span class="bgc-subcard__icon is-blue">
                <IconifyIcon icon="lucide:link-2" class="size-4" />
              </span>
              <div>
                <div>情报参考来源（{{ current.sources.length }}）</div>
                <small>点击可查看原始公开网页与检索摘要</small>
              </div>
            </div>
            <div class="bgc-sources">
              <a
                v-for="src in current.sources"
                :key="src.url"
                :href="src.url"
                target="_blank"
                rel="noopener noreferrer"
                class="bgc-source"
              >
                <div class="flex items-center justify-between gap-2">
                  <div class="flex min-w-0 items-center gap-2">
                    <span
                      v-if="src.type"
                      class="ap-crm-pill"
                      :class="
                        SOURCE_TYPE[src.type]?.pill ?? 'ap-crm-pill--gray'
                      "
                    >
                      {{ SOURCE_TYPE[src.type]?.label ?? src.type }}
                    </span>
                    <span class="truncate text-[13px] font-semibold text-[#1D1D1F]">
                      {{ src.title || src.url }}
                    </span>
                  </div>
                  <IconifyIcon
                    icon="lucide:arrow-up-right"
                    class="size-3.5 shrink-0 text-[#8E8E93]"
                  />
                </div>
                <div class="mt-1 truncate text-[11.5px] text-[#007AFF]">
                  {{ src.url }}
                </div>
                <div
                  v-if="src.snippet"
                  class="mt-1 line-clamp-2 text-xs leading-5 text-[#6E6E73]"
                >
                  {{ src.snippet }}
                </div>
              </a>
            </div>
          </div>
        </div>
      </Spin>
    </section>
  </div>
</template>

<style scoped>
.bgc {
  display: flex;
  gap: 16px;
  height: 100%;
  padding: 16px 18px;
}

.bgc__left {
  display: flex;
  flex: 0 0 332px;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
}

.bgc__right {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
}

.bgc-card {
  background: rgb(255 255 255 / 88%);
  border-radius: 18px;
  box-shadow: var(--ap-shadow-rest);
  backdrop-filter: blur(20px) saturate(165%);
}

.bgc-card--form {
  padding: 16px;
}

.bgc-card--list {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 180px;
  padding: 14px 12px;
  overflow: hidden;
}

.bgc-card__head {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
}

.bgc-card__badge {
  display: grid;
  flex: none;
  place-items: center;
  width: 34px;
  height: 34px;
  color: #fff;
  background: linear-gradient(135deg, #5856d6 0%, #007aff 100%);
  border-radius: 10px;
  box-shadow: 0 3px 10px rgb(0 122 255 / 24%);
}

.bgc-card__title {
  font-size: 15px;
  font-weight: 700;
  color: #1d1d1f;
}

.bgc-card__sub {
  font-size: 11.5px;
  color: #86868b;
}

.bgc-seg {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 2px;
  padding: 3px;
  margin-bottom: 12px;
  background: #f2f3f7;
  border-radius: 10px;
}

.bgc-seg button {
  height: 28px;
  font-size: 12px;
  font-weight: 500;
  color: #6e6e73;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 8px;
  transition: all var(--ap-dur-fast);
}

.bgc-seg button.on {
  font-weight: 600;
  color: #1d1d1f;
  background: #fff;
  box-shadow: 0 1px 4px rgb(0 0 0 / 8%);
}

.bgc-fields {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.bgc-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.bgc-field label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 600;
  color: #3a3a3c;
}

.bgc-field label small {
  font-size: 11px;
  font-weight: 400;
  color: #8e8e93;
}

.bgc-social-toggle {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  align-self: flex-start;
  padding: 2px 0;
  font-size: 12px;
  font-weight: 500;
  color: #007aff;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.bgc-submit {
  display: flex;
  gap: 7px;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 38px;
  margin-top: 14px;
  font-size: 13.5px;
  font-weight: 600;
  color: #fff;
  cursor: pointer;
  background: linear-gradient(135deg, #5856d6 0%, #007aff 100%);
  border: 0;
  border-radius: 11px;
  box-shadow:
    inset 0 0.5px 0 rgb(255 255 255 / 28%),
    0 4px 12px rgb(0 122 255 / 24%);
  transition:
    transform var(--ap-dur-fast) var(--ap-ease),
    opacity var(--ap-dur-fast);
}

.bgc-submit:active:not(:disabled) {
  transform: scale(0.98);
}

.bgc-submit:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.bgc-note {
  display: flex;
  gap: 7px;
  align-items: flex-start;
  padding: 9px 11px;
  margin-top: 10px;
  font-size: 11.5px;
  line-height: 1.5;
  color: #6e6e73;
  background: #f5f6fa;
  border-radius: 10px;
}

.bgc-list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 6px 8px;
  font-size: 12.5px;
  font-weight: 700;
  color: #1d1d1f;
}

.bgc-count {
  padding: 1px 7px;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  color: #6e6e73;
  background: #f2f3f7;
  border-radius: 999px;
}

.bgc-list-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.bgc-empty-mini {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: center;
  padding: 28px 12px;
  font-size: 12px;
  color: #8e8e93;
}

.bgc-item {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 8px 10px;
  margin-bottom: 3px;
  cursor: pointer;
  border-radius: 12px;
  transition:
    background-color var(--ap-dur-fast),
    box-shadow var(--ap-dur-fast);
}

.bgc-item:hover:not(.on) {
  background: rgb(0 0 0 / 3.5%);
}

.bgc-item.on {
  background: #fff;
  box-shadow:
    0 2px 8px rgb(15 23 42 / 7%),
    0 0 0 1px rgb(0 122 255 / 22%);
}

.bgc-item__title {
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  color: #1d1d1f;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bgc-item.on .bgc-item__title {
  color: #007aff;
}

.bgc-item__sub {
  display: flex;
  gap: 4px;
  align-items: center;
  margin-top: 2px;
  font-size: 11px;
  color: #8e8e93;
}

/* 右侧空状态 */
.bgc-empty-stage {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 440px;
  padding: 40px 28px;
  text-align: center;
  background: rgb(255 255 255 / 82%);
  border-radius: 20px;
  box-shadow: var(--ap-shadow-rest);
}

.bgc-empty-stage__orb {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  margin-bottom: 14px;
  color: #fff;
  background: linear-gradient(135deg, #5856d6 0%, #007aff 100%);
  border-radius: 18px;
  box-shadow: 0 8px 24px rgb(0 122 255 / 24%);
}

.bgc-empty-stage h3 {
  margin: 0;
  font-size: 19px;
  font-weight: 700;
  color: #1d1d1f;
}

.bgc-empty-stage p {
  max-width: 480px;
  margin: 8px 0 0;
  font-size: 13.5px;
  line-height: 1.65;
  color: #6e6e73;
}

/* 报告主体 */
.bgc-report {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-bottom: 24px;
}

.bgc-banner {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 12px 16px;
  font-size: 13px;
  font-weight: 500;
  border-radius: 14px;
}

.bgc-banner.is-error {
  color: #d70015;
  background: #ffeceb;
}

.bgc-banner.is-warn {
  color: #b25e00;
  background: #fff4e0;
}

.bgc-hero {
  padding: 20px 22px;
  background: #fff;
  border-radius: 20px;
  box-shadow: var(--ap-shadow-rest);
}

.bgc-hero__top {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: flex-start;
  justify-content: space-between;
}

.bgc-hero__avatar {
  width: 44px;
  height: 44px;
  font-size: 18px;
}

.bgc-hero__name {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  line-height: 1.25;
  color: #1d1d1f;
  letter-spacing: -0.015em;
}

.bgc-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.bgc-btn {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 32px;
  padding: 0 12px;
  font-size: 12.5px;
  font-weight: 600;
  color: #3a3a3c;
  cursor: pointer;
  background: #f2f3f7;
  border: 0;
  border-radius: 999px;
  transition:
    background-color var(--ap-dur-fast),
    transform var(--ap-dur-fast);
}

.bgc-btn:hover {
  background: #e6e8ef;
}

.bgc-btn:active {
  transform: scale(0.97);
}

.bgc-btn--primary {
  color: #fff;
  background: linear-gradient(180deg, #1a88ff 0%, #007aff 100%);
  box-shadow: 0 2px 8px rgb(0 122 255 / 24%);
}

.bgc-btn--primary:hover {
  background: linear-gradient(180deg, #2b92ff 0%, #0a84ff 100%);
}

.bgc-btn--danger {
  color: #d70015;
  background: #ffeceb;
}

.bgc-btn--danger:hover {
  background: #ffdcd9;
}

.bgc-summary {
  padding: 14px 16px;
  margin-top: 16px;
  background: linear-gradient(135deg, #f5f3ff 0%, #eff6ff 100%);
  border-radius: 14px;
  box-shadow: inset 0 0 0 0.5px rgb(88 86 214 / 14%);
}

.bgc-summary__tag {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  margin-bottom: 6px;
  font-size: 11.5px;
  font-weight: 700;
  color: #5856d6;
}

.bgc-summary p {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.7;
  color: #1d1d1f;
}

/* iOS 分组档案键值网格 */
.bgc-kv-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.bgc-kv-item {
  padding: 11px 14px;
  background: #f7f8fb;
  border-radius: 12px;
}

.bgc-kv-item--span {
  grid-column: 1 / -1;
}

.bgc-kv-label {
  font-size: 11.5px;
  font-weight: 600;
  color: #86868b;
}

.bgc-kv-val {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.55;
  color: #1d1d1f;
}

.bgc-ext-link {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 2px 9px;
  font-size: 12px;
  font-weight: 500;
  color: #007aff;
  background: #e8f2ff;
  border-radius: 999px;
}

/* 策略三栏与六维洞察 */
.bgc-advice-grid,
.bgc-insight-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.bgc-subcard {
  padding: 16px 18px;
  background: #fff;
  border-radius: 16px;
  box-shadow: var(--ap-shadow-rest);
}

.bgc-subcard__head {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 700;
  color: #1d1d1f;
}

.bgc-subcard__head small {
  display: block;
  margin-top: 1px;
  font-size: 11px;
  font-weight: 400;
  color: #86868b;
}

.bgc-subcard__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
}

.bgc-subcard__icon.is-blue {
  color: #007aff;
  background: #e8f2ff;
}

.bgc-subcard__icon.is-green {
  color: #1f9d4c;
  background: #eaf9ee;
}

.bgc-subcard__icon.is-purple {
  color: #af52de;
  background: #f5ebfc;
}

.bgc-subcard__icon.is-amber {
  color: #d97706;
  background: #fff4e0;
}

.bgc-subcard__icon.is-indigo {
  color: #5856d6;
  background: #ededfc;
}

.bgc-subcard__icon.is-red {
  color: #d70015;
  background: #ffeceb;
}

.bgc-subcard__text {
  margin: 0;
  font-size: 13px;
  line-height: 1.65;
  color: #3a3a3c;
}

.bgc-subcard__empty {
  margin: 0;
  font-size: 12.5px;
  color: #aeaeb2;
}

.bgc-bullets {
  padding: 0;
  margin: 0;
  list-style: none;
}

.bgc-bullets li {
  position: relative;
  padding-left: 14px;
  margin-bottom: 6px;
  font-size: 13px;
  line-height: 1.6;
  color: #3a3a3c;
}

.bgc-bullets li:last-child {
  margin-bottom: 0;
}

.bgc-bullets li::before {
  position: absolute;
  top: 8px;
  left: 2px;
  width: 5px;
  height: 5px;
  content: '';
  background: #007aff;
  border-radius: 50%;
}

/* 来源列表 */
.bgc-sources {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.bgc-source {
  display: block;
  padding: 11px 13px;
  text-decoration: none;
  background: #f7f8fb;
  border-radius: 12px;
  transition:
    background-color var(--ap-dur-fast),
    box-shadow var(--ap-dur-fast);
}

.bgc-source:hover {
  background: #f0f4ff;
  box-shadow: inset 0 0 0 1px rgb(0 122 255 / 22%);
}

@media (width <= 1200px) {
  .bgc-advice-grid,
  .bgc-insight-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 960px) {
  .bgc {
    flex-direction: column;
    overflow-y: auto;
  }

  .bgc__left {
    flex: none;
    width: 100%;
  }

  .bgc__right {
    overflow: visible;
  }

  .bgc-kv-grid,
  .bgc-advice-grid,
  .bgc-insight-grid,
  .bgc-sources {
    grid-template-columns: 1fr;
  }
}
</style>
