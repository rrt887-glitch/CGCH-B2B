<script lang="ts" setup>
/**
 * 知识库 · 文字条目（企业介绍 / 产品资料 / 常见问答 + 实时语义与关键词检索测试）：
 * 无外层冗余卡片包裹，无缝内嵌在知识库步骤面板中。
 */
import type { ChannelAiApi } from '#/api/channel/ai';

import { computed, onMounted, reactive, ref, watch } from 'vue';

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
  Table,
  Textarea,
  Tooltip,
} from 'ant-design-vue';

import {
  categoryLabel,
  deleteKnowledge,
  getAiConfig,
  getKnowledgeList,
  MATERIAL_CATEGORIES,
  reindexKnowledge,
  saveKnowledge,
  searchKnowledge,
} from '#/api/channel/ai';

defineOptions({ name: 'CgchAgentKnowledge' });

type Kind = ChannelAiApi.Knowledge['type'];
const TABS: {
  contentLabel: string;
  key: Kind;
  label: string;
  tip: string;
  titleLabel: string;
}[] = [
  {
    key: 'company',
    label: '企业介绍',
    titleLabel: '标题',
    contentLabel: '内容',
    tip: '每次回复都会带上，建议写清公司名称、主营产品、工厂规模、出口市场与认证，控制在 1000 字以内。',
  },
  {
    key: 'product',
    label: '产品资料',
    titleLabel: '产品 / 主题',
    contentLabel: '资料详情',
    tip: '按产品或主题分条填写规格、价格区间、MOQ、交期、包装与付款方式，买家问到相关内容时自动匹配引用。',
  },
  {
    key: 'faq',
    label: '常见问答',
    titleLabel: '买家常问问题',
    contentLabel: '标准回答',
    tip: '把买家高频提问与标准答复逐条录入，AI 遇到同类问题会优先按此回答。',
  },
];

const active = ref<Kind>('company');
const loading = ref(false);
const all = ref<ChannelAiApi.Knowledge[]>([]);
const category = ref<string>();
const hasCategory = computed(() => active.value !== 'company');
const rows = computed(() =>
  all.value.filter(
    (k) =>
      k.type === active.value &&
      (!hasCategory.value || !category.value || k.category === category.value),
  ),
);
const tab = computed(() => TABS.find((t) => t.key === active.value)!);

const embeddingModel = ref<string>();
const indexed = (k: ChannelAiApi.Knowledge) =>
  !!embeddingModel.value && k.embeddingModel === embeddingModel.value;
const unindexed = computed(
  () => all.value.filter((k) => k.enabled && !indexed(k)).length,
);
const reindexing = ref(false);

async function reindex() {
  reindexing.value = true;
  try {
    const n = await reindexKnowledge();
    message.success(
      n ? `已为 ${n} 条资料建立语义索引` : '所有资料都已建立索引',
    );
    await load();
  } finally {
    reindexing.value = false;
  }
}

// 检索测试
const q = ref('');
const searching = ref(false);
const hits = ref<ChannelAiApi.SearchHit[]>();
const searchFailed = ref(false);
let searchVersion = 0;

watch(
  q,
  () => {
    searchVersion++;
    hits.value = undefined;
    searchFailed.value = false;
    searching.value = false;
  },
  { flush: 'sync' },
);

async function search() {
  const query = q.value.trim();
  if (!query) return;
  const version = ++searchVersion;
  hits.value = undefined;
  searchFailed.value = false;
  searching.value = true;
  try {
    const result = await searchKnowledge(query);
    if (version === searchVersion) hits.value = result;
  } catch {
    if (version === searchVersion) searchFailed.value = true;
  } finally {
    if (version === searchVersion) searching.value = false;
  }
}

const columns = computed(() => [
  { title: tab.value.titleLabel, dataIndex: 'title', key: 'title', width: 220 },
  ...(hasCategory.value ? [{ title: '分类', key: 'category', width: 105 }] : []),
  { title: tab.value.contentLabel, key: 'content' },
  { title: '启用', key: 'enabled', width: 76 },
  ...(embeddingModel.value
    ? [{ title: '语义索引', key: 'index', width: 96 }]
    : []),
  { title: '操作', key: 'action', width: 110, align: 'right' as const },
]);

async function load() {
  loading.value = true;
  try {
    all.value = await getKnowledgeList();
  } finally {
    loading.value = false;
  }
}

const open = ref(false);
const saving = ref(false);
const form = reactive<ChannelAiApi.Knowledge>({
  type: 'company',
  title: '',
  content: '',
  enabled: true,
  sort: 0,
});
const contentError = ref('');

function openForm(row?: ChannelAiApi.Knowledge) {
  Object.assign(
    form,
    {
      id: undefined,
      type: active.value,
      title: '',
      content: '',
      enabled: true,
      sort: 0,
      category: hasCategory.value ? category.value : undefined,
    },
    row ?? {},
  );
  contentError.value = '';
  open.value = true;
}

watch(
  () => form.content,
  (v) => {
    if (v?.trim()) contentError.value = '';
  },
);

async function save() {
  if (!form.content?.trim()) {
    contentError.value = `请填写${tab.value.contentLabel}`;
    message.warning(contentError.value);
    return;
  }
  saving.value = true;
  try {
    await saveKnowledge({ ...form });
    message.success('已保存');
    open.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function toggle(row: ChannelAiApi.Knowledge, enabled: boolean) {
  await saveKnowledge({ ...row, enabled });
  row.enabled = enabled;
}

async function remove(row: ChannelAiApi.Knowledge) {
  await deleteKnowledge(row.id!);
  message.success('已删除');
  await load();
}

onMounted(async () => {
  embeddingModel.value = (await getAiConfig()).embeddingModel || undefined;
  await load();
});
</script>

<template>
  <div class="tk">
    <!-- 顶部子标签与新增栏 -->
    <div class="tk__toolbar">
      <div class="tk__pills">
        <button
          v-for="t in TABS"
          :key="t.key"
          type="button"
          class="tk__pill"
          :class="{ on: active === t.key }"
          @click="active = t.key"
        >
          <span>{{ t.label }}</span>
          <small>{{ all.filter((k) => k.type === t.key).length }}</small>
        </button>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <Select
          v-if="hasCategory"
          v-model:value="category"
          allow-clear
          class="w-36"
          placeholder="全部分类"
          :options="MATERIAL_CATEGORIES"
        />
        <Tooltip
          v-if="embeddingModel"
          :title="`当前向量模型：${embeddingModel}`"
        >
          <Button size="small" shape="round" :loading="reindexing" @click="reindex">
            建立语义索引<span v-if="unindexed">（{{ unindexed }}）</span>
          </Button>
        </Tooltip>
        <button type="button" class="tk__add-btn" @click="openForm()">
          <IconifyIcon icon="lucide:plus" class="size-3.5" />
          <span>新增{{ tab.label }}</span>
        </button>
      </div>
    </div>

    <div class="tk__callout">
      <IconifyIcon icon="lucide:shield-check" class="mt-0.5 size-4 shrink-0 text-[#007AFF]" />
      <div>
        <div class="font-medium text-[#1D1D1F]">{{ tab.tip }}</div>
        <div class="mt-0.5 text-[#6E6E73]">
          AI 仅依据此处收录的资料回答具体价格、MOQ 与交期；未收录事项会自动回复「确认后答复」，绝不编造承诺。
        </div>
      </div>
    </div>

    <Table
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      :pagination="false"
      row-key="id"
      size="small"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'title'">
          <span class="font-semibold text-[#1D1D1F]">
            {{ record.title || '（未命名条目）' }}
          </span>
        </template>
        <template v-else-if="column.key === 'content'">
          <div class="line-clamp-3 whitespace-pre-wrap text-xs leading-5 text-[#48484A]">
            {{ record.content }}
          </div>
        </template>
        <template v-else-if="column.key === 'category'">
          <span v-if="record.category" class="ap-crm-pill ap-crm-pill--blue">
            {{ categoryLabel(record.category) }}
          </span>
          <span v-else class="text-[#C7C7CC]">—</span>
        </template>
        <template v-else-if="column.key === 'index'">
          <span
            class="ap-crm-pill"
            :class="
              indexed(record as any)
                ? 'ap-crm-pill--green'
                : 'ap-crm-pill--gray'
            "
          >
            {{ indexed(record as any) ? '已索引' : '待建立' }}
          </span>
        </template>
        <template v-else-if="column.key === 'enabled'">
          <Switch
            :checked="record.enabled"
            size="small"
            @change="(v: any) => toggle(record as any, !!v)"
          />
        </template>
        <template v-else-if="column.key === 'action'">
          <div class="flex items-center justify-end gap-2.5">
            <a class="ap-crm-link" @click="openForm(record as any)">编辑</a>
            <Popconfirm
              title="确定删除这条资料？"
              ok-text="删除"
              cancel-text="取消"
              @confirm="remove(record as any)"
            >
              <a class="ap-crm-link ap-crm-link--danger">删除</a>
            </Popconfirm>
          </div>
        </template>
      </template>
      <template #emptyText>暂无{{ tab.label }}，点击右上角即可录入</template>
    </Table>

    <!-- 检索测试卡片 -->
    <div class="tk__tester">
      <div class="flex items-center gap-2 mb-1">
        <span class="tk__tester-badge">
          <IconifyIcon icon="lucide:search-check" class="size-3.5" />
        </span>
        <span class="text-[14px] font-bold text-[#1D1D1F]">知识命中测试</span>
        <span class="ap-crm-pill ap-crm-pill--purple">
          {{ embeddingModel ? '关键词 + 语义双路检索' : '关键词检索模式' }}
        </span>
      </div>
      <p class="mb-3 text-xs leading-5 text-[#6E6E73]">
        输入一句买家可能会问的话，预览 AI 回复时命中的参考条目（企业介绍每次常驻携带，此处不重复列出）。
      </p>

      <div class="tk__search-bar">
        <IconifyIcon icon="lucide:search" class="size-4 text-[#8E8E93]" />
        <input
          v-model="q"
          type="text"
          placeholder="例如：How many pieces must I buy at minimum?"
          @keydown.enter="search"
        />
        <Button
          type="primary"
          shape="round"
          size="small"
          :loading="searching"
          @click="search"
        >
          测试检索
        </Button>
      </div>

      <div v-if="searchFailed" class="mt-3 text-xs text-[#D70015]">
        检索请求失败，请稍后重试。
      </div>
      <div
        v-else-if="hits && hits.length === 0"
        class="mt-3 rounded-xl bg-white px-4 py-3 text-xs text-[#6E6E73]"
      >
        未命中任何知识条目，AI 遇到此类提问将回复「确认后答复」。
      </div>

      <div v-if="hits?.length" class="mt-3 space-y-2">
        <div v-for="(h, i) in hits" :key="h.id" class="tk__hit">
          <div class="flex flex-wrap items-center gap-1.5">
            <span class="tk__hit-no">{{ i + 1 }}</span>
            <span class="text-[13px] font-semibold text-[#1D1D1F]">
              {{ h.title || '（无标题）' }}
            </span>
            <span class="ap-crm-pill ap-crm-pill--gray">
              {{ TABS.find((t) => t.key === h.type)?.label || '资料切片' }}
            </span>
            <span v-if="h.category" class="ap-crm-pill ap-crm-pill--blue">
              {{ categoryLabel(h.category) }}
            </span>
            <span
              v-if="h.scope === 'personal'"
              class="ap-crm-pill ap-crm-pill--purple"
            >
              个人物料
            </span>
            <span
              v-if="h.keywordScore > 0"
              class="ap-crm-pill ap-crm-pill--blue"
            >
              关键词 {{ h.keywordScore }}
            </span>
            <span
              v-if="h.semantic"
              class="ap-crm-pill"
              :class="
                h.semanticScore >= 0.35
                  ? 'ap-crm-pill--green'
                  : 'ap-crm-pill--gray'
              "
            >
              语义 {{ h.semanticScore.toFixed(2) }}
            </span>
          </div>
          <div class="mt-1 line-clamp-2 pl-6 text-xs leading-5 text-[#6E6E73]">
            {{ h.content }}
          </div>
        </div>
      </div>
    </div>

    <!-- 新增/修改文字条目弹窗（Apple Sheet） -->
    <Modal
      v-model:open="open"
      :title="form.id ? `编辑${tab.label}` : `新增${tab.label}`"
      :width="660"
      ok-text="保存条目"
      cancel-text="取消"
      :confirm-loading="saving"
      wrap-class-name="ap-modal-sheet"
      @ok="save"
    >
      <div class="ap-sheet-lead">
        <span class="ap-sheet-icon">
          <IconifyIcon icon="lucide:book-plus" class="size-5" />
        </span>
        <div class="ap-sheet-subtitle">{{ tab.tip }}</div>
      </div>

      <div class="ap-sheet-body">
        <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <div class="ap-sheet-field sm:col-span-2">
            <div class="ap-sheet-label"><span>{{ tab.titleLabel }}</span></div>
            <Input v-model:value="form.title" :maxlength="200" />
          </div>
          <div v-if="hasCategory" class="ap-sheet-field">
            <div class="ap-sheet-label">
              <span>所属分类</span>
              <span class="ap-sheet-optional">选填</span>
            </div>
            <Select
              v-model:value="form.category"
              allow-clear
              placeholder="不分类"
              :options="MATERIAL_CATEGORIES"
            />
          </div>
          <div v-else class="ap-sheet-field">
            <div class="ap-sheet-label">
              <span>排序权重</span>
              <span class="ap-sheet-optional">越小越靠前</span>
            </div>
            <InputNumber v-model:value="form.sort" :min="0" class="w-full" />
          </div>
        </div>

        <div class="ap-sheet-field">
          <div class="ap-sheet-label">
            <span>{{ tab.contentLabel }} *</span>
          </div>
          <Textarea
            v-model:value="form.content"
            :rows="9"
            show-count
            :maxlength="8000"
            :status="contentError ? 'error' : undefined"
          />
          <div v-if="contentError" class="text-xs text-[#D70015]">
            {{ contentError }}
          </div>
        </div>

        <div v-if="hasCategory" class="ap-sheet-field w-40">
          <div class="ap-sheet-label">
            <span>排序权重</span>
            <span class="ap-sheet-optional">越小越靠前</span>
          </div>
          <InputNumber v-model:value="form.sort" :min="0" class="w-full" />
        </div>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.tk {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-top: 2px;
}

.tk__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
}

.tk__pills {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tk__pill {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  height: 30px;
  padding: 0 12px;
  font-size: 12.5px;
  font-weight: 500;
  color: #6e6e73;
  cursor: pointer;
  background: #f5f6fa;
  border: 0;
  border-radius: 999px;
  transition: all var(--ap-dur-fast);
}

.tk__pill small {
  padding: 0 6px;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  background: rgb(0 0 0 / 6%);
  border-radius: 999px;
}

.tk__pill.on {
  font-weight: 600;
  color: #007aff;
  background: #e8f2ff;
}

.tk__pill.on small {
  color: #007aff;
  background: rgb(0 122 255 / 14%);
}

.tk__add-btn {
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

.tk__callout {
  display: flex;
  gap: 9px;
  align-items: flex-start;
  padding: 11px 14px;
  font-size: 12px;
  line-height: 1.55;
  background: #f5f8ff;
  border-radius: 12px;
  box-shadow: inset 0 0 0 0.5px rgb(0 122 255 / 12%);
}

/* 检索测试区 */
.tk__tester {
  padding: 16px 18px;
  margin-top: 4px;
  background: #f7f8fb;
  border-radius: 16px;
}

.tk__tester-badge {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  color: #fff;
  background: linear-gradient(135deg, #af52de 0%, #007aff 100%);
  border-radius: 7px;
}

.tk__search-bar {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 5px 6px 5px 12px;
  background: #fff;
  border-radius: 999px;
  box-shadow:
    0 1px 4px rgb(0 0 0 / 5%),
    0 0 0 0.5px rgb(0 0 0 / 8%);
}

.tk__search-bar input {
  flex: 1;
  min-width: 0;
  height: 28px;
  font-size: 13px;
  color: #1d1d1f;
  background: transparent;
  border: 0;
  outline: none;
}

.tk__hit {
  padding: 10px 13px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgb(0 0 0 / 4%);
}

.tk__hit-no {
  display: inline-grid;
  place-items: center;
  width: 18px;
  height: 18px;
  font-size: 11px;
  font-weight: 700;
  color: #007aff;
  background: #e8f2ff;
  border-radius: 50%;
}
</style>
