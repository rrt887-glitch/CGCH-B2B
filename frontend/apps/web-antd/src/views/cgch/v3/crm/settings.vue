<script lang="ts" setup>
/**
 * CRM 分流、标签与公海规则设置面板（三合一真实可用版）：
 * 1. 🔀 询盘自动分流：直接按「来源渠道 + 买家国家」配置新询盘自动轮流分配给哪些业务员（直连 /channel/routing-rule）
 * 2. 🏷️ 彩色客户标签：点选色点 + 输入回车 1 秒创建扁平标签墙
 * 3. 🌊 公海自动回收：超期未跟进/未成交自动释放规则
 */
import type { RoutingApi } from '#/api/channel/routing';
import type { CgchCrmApi } from '#/api/crm/cgch';

import { computed, defineAsyncComponent, onMounted, reactive, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import {
  Empty,
  Input,
  message,
  Modal,
  Popconfirm,
  Select,
  Spin,
  Switch,
} from 'ant-design-vue';

import {
  deleteRoutingRule,
  getRoutingRules,
  ROUTING_SOURCES,
  saveRoutingRule,
  setRoutingRuleEnabled,
} from '#/api/channel/routing';
import {
  createTreeNode,
  deleteTreeNode,
  getTagList,
  updateTreeNode,
} from '#/api/crm/cgch';
import { getSimpleUserList } from '#/api/system/user';
import { COUNTRY_OPTIONS } from '#/views/cgch/crm/shared/countries';

const PoolConfig = defineAsyncComponent(
  () => import('#/views/crm/customer/poolConfig/index.vue'),
);

const emit = defineEmits<{
  'tags-changed': [];
}>();

const activeTab = ref<'pool' | 'routing' | 'tags'>('routing');

// ==================== 1. 询盘自动分流规则 ====================
const routingLoading = ref(false);
const routingSaving = ref(false);
const rules = ref<RoutingApi.Rule[]>([]);
const users = ref<{ id: number; nickname: string }[]>([]);
const editingRule = ref<null | RoutingApi.Rule>(null);
const editSources = ref<string[]>([]);
const editHasCountry = ref(false);
const editCountryOp = ref<'in' | 'not_in'>('in');
const editCountries = ref<string[]>([]);

const splitCsv = (s?: string) => (s ? s.split(',').filter(Boolean) : []);
const userOptions = computed(() =>
  users.value.map((u) => ({ value: u.id, label: u.nickname })),
);

function ruleSourceSummary(r: RoutingApi.Rule) {
  const list = splitCsv(r.sources).map(
    (s) => ROUTING_SOURCES.find((x) => x.value === s)?.label ?? s,
  );
  return list.length ? list.join('、') : '全部渠道';
}

function ruleCountrySummary(r: RoutingApi.Rule) {
  if (!r.countryOp || !r.countries) return '不限国家';
  const codes = splitCsv(r.countries);
  const opLabel = r.countryOp === 'not_in' ? '排除' : '仅限';
  return `${opLabel} ${codes.slice(0, 4).join('/')}${codes.length > 4 ? ` 等${codes.length}国` : ''}`;
}

function ruleAssigneeSummary(r: RoutingApi.Rule) {
  const names = (r.assigneeIds || []).map(
    (id) => users.value.find((u) => u.id === id)?.nickname ?? `#${id}`,
  );
  if (!names.length) return '未指定';
  return names.length > 1 ? `${names.join('、')}（轮流接单）` : names[0]!;
}

async function loadRouting() {
  routingLoading.value = true;
  try {
    const [uList, rList] = await Promise.all([
      getSimpleUserList().catch(() => []),
      getRoutingRules().catch(() => []),
    ]);
    users.value = (uList as any[]) || [];
    rules.value = rList || [];
  } finally {
    routingLoading.value = false;
  }
}

function startEditRule(r?: RoutingApi.Rule) {
  const copy: RoutingApi.Rule = r
    ? JSON.parse(JSON.stringify(r))
    : {
        name: `分流规则 ${rules.value.length + 1}`,
        enabled: true,
        sources: '',
        countryOp: '',
        countries: '',
        assigneeIds: [],
        sort: rules.value.length,
      };
  editingRule.value = copy;
  editSources.value = splitCsv(copy.sources);
  editHasCountry.value = Boolean(copy.countryOp);
  editCountryOp.value = copy.countryOp === 'not_in' ? 'not_in' : 'in';
  editCountries.value = splitCsv(copy.countries);
}

async function handleSaveRule() {
  if (!editingRule.value) return;
  if (!editingRule.value.assigneeIds?.length) {
    message.warning('请至少选择一位接单负责人');
    return;
  }
  if (editHasCountry.value && !editCountries.value.length) {
    message.warning('请选择适用国家，或关闭国家筛选条件');
    return;
  }
  routingSaving.value = true;
  try {
    await saveRoutingRule({
      ...editingRule.value,
      sources: editSources.value.join(','),
      countryOp: editHasCountry.value ? editCountryOp.value : '',
      countries: editHasCountry.value ? editCountries.value.join(',') : '',
    });
    message.success('询盘分流规则已保存');
    editingRule.value = null;
    await loadRouting();
  } finally {
    routingSaving.value = false;
  }
}

async function handleToggleRule(r: RoutingApi.Rule, enabled: boolean) {
  if (!r.id) return;
  await setRoutingRuleEnabled(r.id, enabled);
  r.enabled = enabled;
  message.success(enabled ? '分流规则已启用' : '分流规则已暂停');
}

async function handleDeleteRule(r: RoutingApi.Rule) {
  if (!r.id) return;
  await deleteRoutingRule(r.id);
  if (editingRule.value?.id === r.id) editingRule.value = null;
  message.success('已删除分流规则');
  await loadRouting();
}

// ==================== 2. 彩色客户标签墙 ====================
const PRESET_COLORS = [
  '#0071E3',
  '#34C759',
  '#FF9500',
  '#FF3B30',
  '#AF52DE',
  '#5856D6',
  '#0D9488',
  '#FF2D55',
];

const QUICK_SUGGESTIONS = [
  { name: '欧美KA大客', color: '#0071E3' },
  { name: 'OEM定制意向', color: '#AF52DE' },
  { name: '广交会名片', color: '#FF9500' },
  { name: '终端品牌商', color: '#34C759' },
  { name: '价格敏感', color: '#FF3B30' },
];

const tagLoading = ref(false);
const tags = ref<CgchCrmApi.TreeNode[]>([]);
const newName = ref('');
const newColor = ref(PRESET_COLORS[0]!);
const creating = ref(false);

const editOpen = ref(false);
const editSaving = ref(false);
const editForm = reactive<{ color: string; id?: number; name: string }>({
  id: undefined,
  name: '',
  color: PRESET_COLORS[0]!,
});

async function loadTags() {
  tagLoading.value = true;
  try {
    tags.value = (await getTagList()) || [];
  } finally {
    tagLoading.value = false;
  }
}

async function handleQuickAdd(nameInput?: string, colorInput?: string) {
  const name = (nameInput ?? newName.value).trim();
  const color = colorInput ?? newColor.value;
  if (!name) {
    message.warning('请输入标签名称');
    return;
  }
  if (tags.value.some((t) => t.name === name)) {
    message.info(`标签「${name}」已存在`);
    return;
  }
  creating.value = true;
  try {
    await createTreeNode('tag', {
      name,
      color,
      parentId: 0,
      sort: 0,
    });
    if (!nameInput) newName.value = '';
    message.success(`已添加标签「${name}」`);
    await loadTags();
    emit('tags-changed');
  } finally {
    creating.value = false;
  }
}

function openEdit(t: CgchCrmApi.TreeNode) {
  editForm.id = t.id;
  editForm.name = t.name;
  editForm.color = t.color || PRESET_COLORS[0]!;
  editOpen.value = true;
}

async function handleSaveEdit() {
  if (!editForm.id || !editForm.name.trim()) return;
  editSaving.value = true;
  try {
    await updateTreeNode('tag', {
      id: editForm.id,
      name: editForm.name.trim(),
      color: editForm.color,
      parentId: 0,
      sort: 0,
    });
    editOpen.value = false;
    message.success('标签已更新');
    await loadTags();
    emit('tags-changed');
  } finally {
    editSaving.value = false;
  }
}

async function handleRemove(id?: number) {
  if (!id) return;
  await deleteTreeNode('tag', id);
  message.success('已删除标签');
  await loadTags();
  emit('tags-changed');
}

onMounted(() => {
  void loadRouting();
  void loadTags();
});
</script>

<template>
  <div class="set-stack">
    <!-- 顶部分段胶囊切换：全部为真实生效配置 -->
    <nav class="set-seg">
      <button
        type="button"
        class="set-seg-btn"
        :class="{ on: activeTab === 'routing' }"
        @click="activeTab = 'routing'"
      >
        <IconifyIcon icon="lucide:git-fork" class="size-3.5" />
        <span>询盘自动分流 ({{ rules.length }})</span>
      </button>
      <button
        type="button"
        class="set-seg-btn"
        :class="{ on: activeTab === 'tags' }"
        @click="activeTab = 'tags'"
      >
        <IconifyIcon icon="lucide:tags" class="size-3.5" />
        <span>彩色客户标签 ({{ tags.length }})</span>
      </button>
      <button
        type="button"
        class="set-seg-btn"
        :class="{ on: activeTab === 'pool' }"
        @click="activeTab = 'pool'"
      >
        <IconifyIcon icon="lucide:globe" class="size-3.5" />
        <span>公海回收规则</span>
      </button>
    </nav>

    <!-- ==================== 1. 询盘自动分流配置 ==================== -->
    <section v-if="activeTab === 'routing'" class="set-section">
      <div class="mb-3 flex items-center justify-between gap-2">
        <div class="flex items-center gap-2.5">
          <span class="set-badge is-blue">
            <IconifyIcon icon="lucide:git-fork" class="size-4" />
          </span>
          <div>
            <h4 class="m-0 text-[14.5px] font-bold text-[#1D1D1F]">
              新询盘自动轮流派单规则
            </h4>
            <p class="m-0 text-[12px] text-[#6E6E73]">
              渠道新询盘建档时按顺序匹配第一条启用规则，支持多位业务员公平轮流接单
            </p>
          </div>
        </div>
        <button
          v-if="!editingRule"
          type="button"
          class="set-add-btn"
          @click="startEditRule()"
        >
          <IconifyIcon icon="lucide:plus" class="size-3.5" />
          <span>新建分流规则</span>
        </button>
      </div>

      <!-- 新建 / 编辑分流规则卡片 -->
      <div v-if="editingRule" class="mb-3 rounded-2xl border border-[#0071E3]/30 bg-[#F5F9FF] p-4">
        <div class="mb-3 flex items-center justify-between gap-2">
          <Input
            v-model:value="editingRule.name"
            :maxlength="80"
            placeholder="规则名称（如：欧美询盘分配给 A 组）"
            class="max-w-xs font-semibold"
          />
          <label class="inline-flex items-center gap-1.5 text-[12px] text-[#3A3A3C]">
            <span>立即启用</span>
            <Switch v-model:checked="editingRule.enabled" size="small" />
          </label>
        </div>

        <div class="flex flex-col gap-3">
          <div class="rounded-xl bg-white p-3">
            <div class="mb-1.5 text-[12px] font-semibold text-[#1D1D1F]">
              ① 当询盘来源于以下渠道（留空代表全部渠道）
            </div>
            <Select
              v-model:value="editSources"
              mode="multiple"
              allow-clear
              placeholder="全部渠道（WhatsApp / 邮箱 / Facebook / Instagram / 官网IM…）"
              :options="ROUTING_SOURCES"
              class="w-full"
            />
          </div>

          <div class="rounded-xl bg-white p-3">
            <div class="mb-1.5 flex items-center justify-between">
              <span class="text-[12px] font-semibold text-[#1D1D1F]">
                ② 按买家所在国家/地区过滤（可选）
              </span>
              <Switch v-model:checked="editHasCountry" size="small" />
            </div>
            <div v-if="editHasCountry" class="flex gap-2 pt-1">
              <Select
                v-model:value="editCountryOp"
                class="w-28"
                :options="[
                  { value: 'in', label: '国家属于' },
                  { value: 'not_in', label: '国家排除' },
                ]"
              />
              <Select
                v-model:value="editCountries"
                mode="multiple"
                show-search
                option-filter-prop="label"
                placeholder="选择国家/地区（如美国、德国、英国…）"
                :options="COUNTRY_OPTIONS"
                class="flex-1"
              />
            </div>
            <div v-else class="text-[11.5px] text-[#86868B]">
              不限制买家国家，所有识别出的新询盘均适用
            </div>
          </div>

          <div class="rounded-xl bg-white p-3">
            <div class="mb-1.5 text-[12px] font-semibold text-[#1D1D1F]">
              ③ 自动分配给负责人（选多人时按顺序轮流派单）*
            </div>
            <Select
              v-model:value="editingRule.assigneeIds"
              mode="multiple"
              show-search
              option-filter-prop="label"
              placeholder="选择 1 位或多位接单业务员"
              :options="userOptions"
              class="w-full"
            />
          </div>
        </div>

        <div class="mt-3 flex justify-end gap-2">
          <button
            type="button"
            class="rounded-lg border-none bg-white px-3 py-1.5 text-[12px] font-semibold text-[#6E6E73] cursor-pointer hover:bg-[#EBEBF0]"
            @click="editingRule = null"
          >
            取消
          </button>
          <button
            type="button"
            class="set-add-btn"
            :disabled="routingSaving"
            @click="handleSaveRule"
          >
            保存分流规则
          </button>
        </div>
      </div>

      <!-- 已有规则列表 -->
      <Spin :spinning="routingLoading">
        <Empty
          v-if="!routingLoading && rules.length === 0 && !editingRule"
          :image="Empty.PRESENTED_IMAGE_SIMPLE"
          description="尚未配置自动分流规则（默认归属绑定该渠道账号的成员）"
        />
        <div v-else class="flex flex-col gap-2">
          <div
            v-for="r in rules"
            :key="r.id"
            class="flex items-center justify-between gap-3 rounded-xl border border-[#E5E5EA] bg-white p-3 transition hover:border-[#0071E3]/40"
          >
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2">
                <span class="truncate text-[13.5px] font-bold text-[#1D1D1F]">
                  {{ r.name }}
                </span>
                <span
                  class="rounded-full px-2 py-0.5 text-[10.5px] font-semibold"
                  :class="
                    r.enabled
                      ? 'bg-[#EAF9EE] text-[#1E7E34]'
                      : 'bg-[#F2F2F7] text-[#86868B]'
                  "
                >
                  {{ r.enabled ? '生效中' : '已暂停' }}
                </span>
              </div>
              <div class="mt-1 flex flex-wrap items-center gap-1.5 text-[12px] text-[#6E6E73]">
                <span class="rounded bg-[#F2F2F7] px-1.5 py-0.5 text-[11px] text-[#3A3A3C]">
                  渠道：{{ ruleSourceSummary(r) }}
                </span>
                <span class="rounded bg-[#F2F2F7] px-1.5 py-0.5 text-[11px] text-[#3A3A3C]">
                  {{ ruleCountrySummary(r) }}
                </span>
                <span class="text-[#0071E3] font-medium">
                  → {{ ruleAssigneeSummary(r) }}
                </span>
              </div>
            </div>

            <div class="flex shrink-0 items-center gap-2">
              <Switch
                size="small"
                :checked="r.enabled"
                @change="(v: any) => handleToggleRule(r, Boolean(v))"
              />
              <button
                type="button"
                class="cursor-pointer rounded-md border-none bg-[#F2F2F7] px-2.5 py-1 text-[11.5px] font-semibold text-[#3A3A3C] hover:bg-[#E5E5EA]"
                @click="startEditRule(r)"
              >
                编辑
              </button>
              <Popconfirm
                title="确定删除这条分流规则？"
                ok-text="删除"
                cancel-text="取消"
                @confirm="handleDeleteRule(r)"
              >
                <button
                  type="button"
                  class="cursor-pointer rounded-md border-none bg-transparent px-2 py-1 text-[11.5px] text-[#86868B] hover:text-[#D70015]"
                >
                  删除
                </button>
              </Popconfirm>
            </div>
          </div>
        </div>
      </Spin>
    </section>

    <!-- ==================== 2. 极简彩色客户标签墙 ==================== -->
    <section v-else-if="activeTab === 'tags'" class="set-section">
      <div class="mb-3 flex items-center justify-between gap-2">
        <div class="flex items-center gap-2.5">
          <span class="set-badge is-purple">
            <IconifyIcon icon="lucide:tags" class="size-4" />
          </span>
          <div>
            <h4 class="m-0 text-[14.5px] font-bold text-[#1D1D1F]">
              彩色客户标签
            </h4>
            <p class="m-0 text-[12px] text-[#6E6E73]">
              统一替代繁琐的多级分组，在看板、聊天侧栏与全景抽屉中一键筛选客户
            </p>
          </div>
        </div>
        <span class="rounded-full bg-[#F2F2F7] px-2.5 py-0.5 text-[11.5px] font-semibold text-[#6E6E73]">
          共 {{ tags.length }} 个
        </span>
      </div>

      <div class="set-creator">
        <div class="flex items-center gap-1.5">
          <button
            v-for="c in PRESET_COLORS"
            :key="c"
            type="button"
            class="set-dot-pick"
            :style="{
              background: c,
              boxShadow: newColor === c ? `0 0 0 2px #fff, 0 0 0 3.5px ${c}` : 'none',
            }"
            @click="newColor = c"
          />
        </div>
        <Input
          v-model:value="newName"
          :maxlength="24"
          placeholder="输入新标签名（如：欧美大客、寄样跟进），按回车即创建…"
          class="flex-1"
          @press-enter="() => handleQuickAdd()"
        />
        <button
          type="button"
          class="set-add-btn"
          :disabled="creating"
          @click="() => handleQuickAdd()"
        >
          <IconifyIcon icon="lucide:plus" class="size-3.5" />
          <span>添加标签</span>
        </button>
      </div>

      <div v-if="tags.length < 6" class="mt-2 flex flex-wrap items-center gap-1.5 text-[11.5px]">
        <span class="text-[#86868B]">外贸常用推荐：</span>
        <button
          v-for="sug in QUICK_SUGGESTIONS.filter(
            (s) => !tags.some((t) => t.name === s.name),
          )"
          :key="sug.name"
          type="button"
          class="set-sug-chip"
          @click="handleQuickAdd(sug.name, sug.color)"
        >
          + {{ sug.name }}
        </button>
      </div>

      <Spin :spinning="tagLoading">
        <div v-if="tags.length === 0 && !tagLoading" class="set-empty">
          暂未创建客户标签，可在上方输入或点击推荐词一键生成
        </div>
        <div v-else class="mt-3.5 flex flex-wrap gap-2">
          <div
            v-for="t in tags"
            :key="t.id"
            class="set-tag-pill"
            :style="{
              borderColor: `${t.color || '#0071E3'}30`,
              background: `${t.color || '#0071E3'}10`,
            }"
          >
            <span
              class="size-2.5 rounded-full"
              :style="{ background: t.color || '#0071E3' }"
            />
            <button
              type="button"
              class="set-tag-label"
              :style="{ color: t.color || '#1D1D1F' }"
              title="点击修改名称或颜色"
              @click="openEdit(t)"
            >
              {{ t.name }}
            </button>
            <Popconfirm
              :title="`确定删除标签「${t.name}」吗？`"
              ok-text="删除"
              cancel-text="取消"
              @confirm="handleRemove(t.id)"
            >
              <button type="button" class="set-tag-del" title="删除标签">
                <IconifyIcon icon="lucide:x" class="size-3" />
              </button>
            </Popconfirm>
          </div>
        </div>
      </Spin>
    </section>

    <!-- ==================== 3. 客户公海自动回收规则 ==================== -->
    <section v-else class="set-section">
      <PoolConfig />
    </section>

    <!-- 编辑标签弹窗 -->
    <Modal
      v-model:open="editOpen"
      title="修改客户标签"
      :width="380"
      centered
      :confirm-loading="editSaving"
      ok-text="保存"
      cancel-text="取消"
      wrap-class-name="ap-modal-sheet"
      @ok="handleSaveEdit"
    >
      <div class="flex flex-col gap-3 py-2">
        <div>
          <div class="mb-1 text-[12px] font-semibold text-[#3A3A3C]">标签名称</div>
          <Input v-model:value="editForm.name" :maxlength="24" />
        </div>
        <div>
          <div class="mb-1.5 text-[12px] font-semibold text-[#3A3A3C]">标签色彩</div>
          <div class="flex gap-2">
            <button
              v-for="c in PRESET_COLORS"
              :key="c"
              type="button"
              class="set-dot-pick"
              :style="{
                background: c,
                boxShadow:
                  editForm.color === c ? `0 0 0 2px #fff, 0 0 0 3.5px ${c}` : 'none',
              }"
              @click="editForm.color = c"
            />
          </div>
        </div>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.set-stack {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.set-seg {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  align-self: flex-start;
  padding: 4px;
  background: rgb(118 118 128 / 11%);
  border-radius: 12px;
}

.set-seg-btn {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  height: 31px;
  padding: 0 13px;
  font-size: 12.5px;
  font-weight: 600;
  color: #6e6e73;
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: 9px;
  transition: all 0.15s ease;
}

.set-seg-btn.on {
  color: #1d1d1f;
  background: #fff;
  box-shadow: 0 1px 3px rgb(0 0 0 / 8%);
}

.set-section {
  padding: 16px 18px;
  background: rgb(255 255 255 / 90%);
  border: 0.5px solid rgb(60 60 67 / 10%);
  border-radius: 16px;
  box-shadow: 0 1px 3px rgb(0 0 0 / 3%);
}

.set-badge {
  display: grid;
  flex: none;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
}

.set-badge.is-blue {
  color: #0071e3;
  background: #e8f2ff;
}

.set-badge.is-purple {
  color: #af52de;
  background: #f5ebfc;
}

.set-creator {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  background: #f5f5f7;
  border-radius: 12px;
}

.set-dot-pick {
  width: 18px;
  height: 18px;
  cursor: pointer;
  border: none;
  border-radius: 999px;
  transition: transform 0.14s ease;
}

.set-dot-pick:hover {
  transform: scale(1.12);
}

.set-add-btn {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  height: 32px;
  padding: 0 13px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  cursor: pointer;
  background: #0071e3;
  border: none;
  border-radius: 9px;
  transition: background 0.15s ease;
}

.set-add-btn:hover:not(:disabled) {
  background: #0077ed;
}

.set-sug-chip {
  padding: 2px 9px;
  font-size: 11px;
  font-weight: 500;
  color: #3a3a3c;
  cursor: pointer;
  background: #fff;
  border: 0.5px solid rgb(60 60 67 / 14%);
  border-radius: 999px;
  transition: all 0.14s ease;
}

.set-sug-chip:hover {
  color: #0071e3;
  border-color: #0071e3;
}

.set-empty {
  padding: 18px 0 6px;
  font-size: 12.5px;
  color: #86868b;
  text-align: center;
}

.set-tag-pill {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  height: 30px;
  padding: 0 8px 0 11px;
  border: 1px solid transparent;
  border-radius: 999px;
  transition: transform 0.14s ease;
}

.set-tag-pill:hover {
  transform: translateY(-1px);
}

.set-tag-label {
  padding: 0;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  background: transparent;
  border: none;
}

.set-tag-del {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  color: #86868b;
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: 999px;
}

.set-tag-del:hover {
  color: #d70015;
  background: rgb(255 59 48 / 12%);
}
</style>
