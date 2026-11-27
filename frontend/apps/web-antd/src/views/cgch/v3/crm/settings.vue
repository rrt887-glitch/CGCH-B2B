<script lang="ts" setup>
/**
 * CRM 规则与标签中心（极简瘦身版）：
 * - 彻底砍掉空壳「线索流转规则」与重叠的「客户分组」树
 * - 上半区：Linear / Apple 备忘录风格的「彩色客户标签墙」（选色 + 输入回车即建，点击即改，零树形层级负担）
 * - 下半区：公海自动回收规则卡片，一屏直达无套娃 Tab
 */
import type { CgchCrmApi } from '#/api/crm/cgch';

import { defineAsyncComponent, onMounted, reactive, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Input, message, Modal, Popconfirm, Spin } from 'ant-design-vue';

import {
  createTreeNode,
  deleteTreeNode,
  getTagList,
  updateTreeNode,
} from '#/api/crm/cgch';

const PoolConfig = defineAsyncComponent(
  () => import('#/views/crm/customer/poolConfig/index.vue'),
);

const emit = defineEmits<{
  'tags-changed': [];
}>();

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

const loading = ref(false);
const tags = ref<CgchCrmApi.TreeNode[]>([]);
const newName = ref('');
const newColor = ref(PRESET_COLORS[0]!);
const creating = ref(false);

// 编辑单个标签弹窗
const editOpen = ref(false);
const editSaving = ref(false);
const editForm = reactive<{ color: string; id?: number; name: string }>({
  id: undefined,
  name: '',
  color: PRESET_COLORS[0]!,
});

async function loadTags() {
  loading.value = true;
  try {
    tags.value = (await getTagList()) || [];
  } finally {
    loading.value = false;
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
  void loadTags();
});
</script>

<template>
  <div class="set-stack">
    <!-- ==================== 区块 1：极简彩色客户标签墙 ==================== -->
    <section class="set-section">
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

      <!-- 一行快建器 -->
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

      <!-- 外贸常用预设一键点亮（当标签较少时贴心推荐） -->
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

      <!-- 彩色胶囊墙 -->
      <Spin :spinning="loading">
        <div v-if="tags.length === 0 && !loading" class="set-empty">
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

    <!-- ==================== 区块 2：客户公海自动回收规则 ==================== -->
    <section class="set-section">
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
  gap: 16px;
}

.set-section {
  padding: 16px 18px;
  background: rgb(255 255 255 / 86%);
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
