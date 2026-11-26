<script lang="ts" setup>
/**
 * 步骤 1 · 知识库：
 * 上方是企业名片卡（名称、行业、官网、简介，Apple Sheet 弹窗编辑）；
 * 下方分段切换「资料文件」与「文字条目」，支持按文件类型胶囊筛选、上传学习及语义状态展示。
 */
import type { AgentApi } from '#/api/channel/agent';

import { computed, defineAsyncComponent, onMounted, reactive, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import {
  Button,
  Dropdown,
  Input,
  Menu,
  MenuItem,
  message,
  Modal,
  Pagination,
  Table,
  Textarea,
  Tooltip,
  Upload,
} from 'ant-design-vue';

import {
  deleteKnowledgeFile,
  getKnowledgeFiles,
  relearnKnowledgeFile,
  uploadKnowledgeFile,
} from '#/api/channel/agent';
import { fmtDateTime } from '#/utils/datetime';

import { clone, useAgentSettings } from './use-settings';

const TextKnowledge = defineAsyncComponent(
  () => import('#/views/cgch/agent/knowledge/index.vue'),
);

const { settings, load, save } = useAgentSettings();
const view = ref<'files' | 'text'>('files');
const files = ref<AgentApi.KnowledgeFile[]>([]);
const loading = ref(true);
const loadFailed = ref(false);
let fileLoadVersion = 0;
const uploading = ref(0);
const keyword = ref('');
const kind = ref('');
const page = reactive({ no: 1, size: 20 });

const KINDS = [
  { value: '', label: '全部' },
  { value: 'pdf', label: 'PDF', ext: ['pdf'] },
  { value: 'word', label: 'Word', ext: ['doc', 'docx', 'rtf'] },
  { value: 'excel', label: '表格', ext: ['xls', 'xlsx', 'csv'] },
  { value: 'ppt', label: '演示', ext: ['ppt', 'pptx'] },
  { value: 'text', label: '文本', ext: ['txt', 'md', 'html', 'htm'] },
];

const ICON: Record<string, { bg: string; color: string; letter: string }> = {
  pdf: { bg: '#FFF1EC', color: '#EA580C', letter: 'PDF' },
  word: { bg: '#E8F2FF', color: '#007AFF', letter: 'DOC' },
  excel: { bg: '#EAF9EE', color: '#1F9D4C', letter: 'XLS' },
  ppt: { bg: '#FFF4E0', color: '#D97706', letter: 'PPT' },
  text: { bg: '#F2F3F7', color: '#6E6E73', letter: 'TXT' },
};

const kindOf = (ext: string) =>
  KINDS.find((k) => k.ext?.includes((ext || '').toLowerCase()))?.value ||
  'text';

const filtered = computed(() =>
  files.value.filter(
    (f) =>
      (!keyword.value ||
        f.name.toLowerCase().includes(keyword.value.toLowerCase())) &&
      (!kind.value || kindOf(f.fileType) === kind.value),
  ),
);
const paged = computed(() =>
  filtered.value.slice((page.no - 1) * page.size, page.no * page.size),
);

const columns = [
  { title: '资料文件', key: 'name' },
  { title: '上传日期', key: 'createTime', width: 125 },
  { title: 'AI 学习状态', key: 'status', width: 155 },
  { title: '操作', key: 'action', width: 68, align: 'right' as const },
];

async function loadFiles() {
  const version = ++fileLoadVersion;
  loading.value = true;
  loadFailed.value = false;
  try {
    const result = await getKnowledgeFiles();
    if (version !== fileLoadVersion) return;
    files.value = result;
    page.no = Math.min(
      page.no,
      Math.max(1, Math.ceil(filtered.value.length / page.size)),
    );
  } catch {
    if (version === fileLoadVersion) loadFailed.value = true;
  } finally {
    if (version === fileLoadVersion) loading.value = false;
  }
}

async function beforeUpload(file: File) {
  if (file.size === 0) {
    message.warning(`${file.name} 是空文件，请选择有内容的资料`);
    return false;
  }
  if (file.size > 20 * 1024 * 1024) {
    message.warning(`${file.name} 超过 20MB，请拆分后上传`);
    return false;
  }
  uploading.value++;
  try {
    const f = await uploadKnowledgeFile(file);
    if (f.status === 'failed')
      message.warning(`${f.name}：${f.errorMsg || '学习失败'}`);
    else if (f.status === 'done')
      message.success(`${f.name} 学习完成，拆成 ${f.chunks} 段资料`);
    else message.info(`${f.name} 正在学习`);
    await loadFiles();
  } catch {
    // 请求层已提示上传失败
  } finally {
    uploading.value--;
  }
  return false;
}

const uploadRef = ref<InstanceType<typeof Upload>>();
function pickFiles() {
  const root = uploadRef.value?.$el as HTMLElement | undefined;
  root?.querySelector<HTMLInputElement>('input[type="file"]')?.click();
}

async function onMenu(f: AgentApi.KnowledgeFile, key: string) {
  if (key === 'download') window.open(f.url, '_blank');
  if (key === 'relearn') {
    const r = await relearnKnowledgeFile(f.id);
    if (r.status === 'failed') message.warning(r.errorMsg || '学习失败');
    else message.success('已重新解析学习');
    await loadFiles();
  }
  if (key === 'delete') {
    Modal.confirm({
      title: `删除「${f.name}」？`,
      content: '删除后 AI 不再使用这份资料回答客户。',
      okType: 'danger',
      okText: '删除',
      cancelText: '取消',
      onOk: async () => {
        await deleteKnowledgeFile(f.id);
        message.success('已删除');
        await loadFiles();
      },
    });
  }
}

// ===== 企业信息 =====
const companyOpen = ref(false);
const companyForm = ref<AgentApi.Company>({
  name: '',
  industry: '',
  website: '',
  intro: '',
});
const company = computed(() => settings.value?.company);

function editCompany() {
  companyForm.value = clone(
    company.value ?? { name: '', industry: '', website: '', intro: '' },
  );
  companyOpen.value = true;
}

async function saveCompany() {
  await save({ company: companyForm.value });
  companyOpen.value = false;
}

onMounted(() => {
  load().catch(() => {});
  loadFiles();
});
</script>

<template>
  <div class="kb">
    <header class="kb__head">
      <div>
        <h2 class="kb__title">知识库</h2>
        <p class="kb__sub">
          上传产品目录、报价单、FAQ 文档或手动录入文字条目，AI 接待买家时将精准检索引用
        </p>
      </div>
    </header>

    <!-- 企业基础信息名片 -->
    <div class="kb__company">
      <div class="kb__company-icon">
        <IconifyIcon icon="lucide:building-2" class="size-5" />
      </div>
      <div class="min-w-0 flex-1">
        <template v-if="company?.name || company?.intro">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div class="flex flex-wrap items-center gap-2">
              <span class="text-[15.5px] font-bold text-[#1D1D1F]">
                {{ company.name || '未填写公司名称' }}
              </span>
              <span v-if="company.industry" class="ap-crm-pill ap-crm-pill--blue">
                {{ company.industry }}
              </span>
              <a
                v-if="company.website"
                :href="company.website"
                target="_blank"
                rel="noopener noreferrer"
                class="kb__web-chip"
              >
                <IconifyIcon icon="lucide:globe" class="size-3" />
                <span>{{ company.website }}</span>
                <IconifyIcon icon="lucide:arrow-up-right" class="size-3" />
              </a>
            </div>
            <button type="button" class="kb__edit" @click="editCompany">
              <IconifyIcon icon="lucide:pencil" class="size-3" />
              <span>编辑企业资料</span>
            </button>
          </div>
          <Tooltip
            v-if="company.intro"
            :title="company.intro"
            placement="bottomLeft"
            :overlay-style="{ maxWidth: '560px' }"
          >
            <p class="kb__intro">{{ company.intro }}</p>
          </Tooltip>
        </template>

        <div v-else class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <div class="text-[14.5px] font-semibold text-[#1D1D1F]">
              完善企业基础信息
            </div>
            <div class="mt-0.5 text-xs text-[#6E6E73]">
              填写公司名称、所属行业、官网与主营优势简介，AI 将以企业代表身份自然介绍
            </div>
          </div>
          <Button type="primary" shape="round" size="small" @click="editCompany">
            填写企业信息
          </Button>
        </div>
      </div>
    </div>

    <!-- 资料文件 / 文字条目主面板 -->
    <div class="kb__panel">
      <div class="kb__bar">
        <div class="kb__seg">
          <button
            type="button"
            :class="{ on: view === 'files' }"
            @click="view = 'files'"
          >
            <IconifyIcon icon="lucide:files" class="size-3.5" />
            <span>资料文件</span>
            <small v-if="files.length">{{ files.length }}</small>
          </button>
          <button
            type="button"
            :class="{ on: view === 'text' }"
            @click="view = 'text'"
          >
            <IconifyIcon icon="lucide:file-text" class="size-3.5" />
            <span>文字条目</span>
          </button>
        </div>

        <template v-if="view === 'files'">
          <div class="kb__search-wrap">
            <IconifyIcon icon="lucide:search" class="size-3.5 text-[#8E8E93]" />
            <input
              v-model="keyword"
              type="text"
              placeholder="搜索文件名称…"
              @input="page.no = 1"
            />
            <button
              v-if="keyword"
              type="button"
              class="kb__clear"
              @click="
                keyword = '';
                page.no = 1;
              "
            >
              <IconifyIcon icon="lucide:x" class="size-3" />
            </button>
          </div>

          <div class="flex-1"></div>

          <div class="kb__kinds">
            <button
              v-for="k in KINDS"
              :key="k.value"
              type="button"
              :class="{ on: kind === k.value }"
              @click="
                kind = k.value;
                page.no = 1;
              "
            >
              {{ k.label }}
            </button>
          </div>

          <Upload
            ref="uploadRef"
            class="kb__upload-hidden"
            :show-upload-list="false"
            :open-file-dialog-on-click="false"
            multiple
            :before-upload="beforeUpload"
            accept=".pdf,.doc,.docx,.xls,.xlsx,.csv,.ppt,.pptx,.txt,.md,.html,.htm,.rtf"
          />
          <button
            type="button"
            class="kb__upload-btn"
            :disabled="uploading > 0"
            @click="pickFiles"
          >
            <IconifyIcon
              :icon="uploading > 0 ? 'lucide:loader-2' : 'lucide:upload'"
              class="size-4"
              :class="{ 'animate-spin': uploading > 0 }"
            />
            <span>{{ uploading > 0 ? '正在学习…' : '上传资料' }}</span>
          </button>
        </template>
      </div>

      <template v-if="view === 'files'">
        <div v-if="loadFailed" class="kb__err">
          <span>资料列表加载失败</span>
          <Button size="small" @click="loadFiles">重试</Button>
        </div>

        <!-- 空状态上传引导卡 -->
        <div
          v-if="!loading && paged.length === 0 && !keyword.trim() && !kind"
          class="kb__dropzone"
          @click="pickFiles"
        >
          <div class="kb__drop-orb">
            <IconifyIcon icon="lucide:cloud-upload" class="size-6" />
          </div>
          <div class="text-[14.5px] font-semibold text-[#1D1D1F]">
            点击上传产品目录、报价表或 FAQ 文档
          </div>
          <div class="mt-1 text-xs text-[#86868B]">
            支持 PDF、Word、Excel、CSV、PPT、Markdown、TXT（单文件不超过 20MB，上传后自动切片学习）
          </div>
        </div>

        <template v-else>
          <Table
            :columns="columns"
            :data-source="paged"
            :loading="loading"
            :pagination="false"
            row-key="id"
            size="middle"
          >
            <template #bodyCell="{ column, record }">
              <div
                v-if="column.key === 'name'"
                class="flex min-w-0 items-center gap-3"
              >
                <span
                  class="kb__icon"
                  :style="{
                    background: ICON[kindOf(record.fileType)]!.bg,
                    color: ICON[kindOf(record.fileType)]!.color,
                  }"
                >
                  {{ ICON[kindOf(record.fileType)]!.letter }}
                </span>
                <div class="min-w-0">
                  <div class="truncate font-medium text-[#1D1D1F]">
                    {{ record.name }}
                  </div>
                  <div
                    v-if="record.chunks"
                    class="text-[11px] text-[#8E8E93]"
                  >
                    已切分 {{ record.chunks }} 段知识切片
                  </div>
                </div>
              </div>

              <template v-else-if="column.key === 'createTime'">
                <span class="text-xs text-[#6E6E73]">
                  {{ fmtDateTime(record.createTime, 'YYYY-MM-DD') }}
                </span>
              </template>

              <template v-else-if="column.key === 'status'">
                <span
                  v-if="record.status === 'done'"
                  class="ap-crm-pill ap-crm-pill--green"
                >
                  ● 学习完成
                </span>
                <Tooltip
                  v-else-if="record.status === 'failed'"
                  :title="record.errorMsg || '文件解析失败'"
                >
                  <span class="ap-crm-pill ap-crm-pill--red cursor-help">
                    ● 学习失败
                  </span>
                </Tooltip>
                <span v-else class="ap-crm-pill ap-crm-pill--amber">
                  ● 解析学习中
                </span>
              </template>

              <template v-else-if="column.key === 'action'">
                <Dropdown :trigger="['click']" placement="bottomRight">
                  <button type="button" class="kb__more-btn" aria-label="更多操作">
                    <IconifyIcon icon="lucide:ellipsis" class="size-4" />
                  </button>
                  <template #overlay>
                    <Menu
                      @click="({ key }: any) => onMenu(record as any, String(key))"
                    >
                      <MenuItem key="download">下载原文件</MenuItem>
                      <MenuItem key="relearn">重新解析学习</MenuItem>
                      <MenuItem key="delete" danger>删除资料</MenuItem>
                    </Menu>
                  </template>
                </Dropdown>
              </template>
            </template>
            <template #emptyText>
              {{
                keyword.trim()
                  ? `没有找到与“${keyword.trim()}”匹配的资料`
                  : kind
                    ? '当前类型下还没有资料'
                    : '还没有资料，上传产品目录、FAQ、报价规则等文件，AI 就能据此回答客户'
              }}
            </template>
          </Table>

          <div
            v-if="filtered.length > 0"
            class="mt-4 flex flex-wrap items-center justify-between gap-2 text-xs text-[#86868B]"
          >
            <span>共 {{ filtered.length }} 份资料文件</span>
            <Pagination
              v-model:current="page.no"
              v-model:page-size="page.size"
              :total="filtered.length"
              show-size-changer
              size="small"
            />
          </div>
        </template>
      </template>

      <div v-else class="kb__text">
        <TextKnowledge />
      </div>
    </div>

    <!-- 企业信息弹窗（Apple Sheet） -->
    <Modal
      v-model:open="companyOpen"
      title="编辑企业信息"
      :width="560"
      ok-text="保存信息"
      cancel-text="取消"
      wrap-class-name="ap-modal-sheet"
      @ok="saveCompany"
    >
      <div class="ap-sheet-lead">
        <span class="ap-sheet-icon">
          <IconifyIcon icon="lucide:building-2" class="size-5" />
        </span>
        <div class="ap-sheet-subtitle">
          企业名称与简介将作为常驻背景上下文注入每次对话，建议用英文准确描述主营优势。
        </div>
      </div>

      <div class="ap-sheet-body">
        <div class="ap-sheet-field">
          <div class="ap-sheet-label"><span>公司名称</span></div>
          <Input
            v-model:value="companyForm.name"
            :maxlength="200"
            placeholder="例如：Dongguan CGCH Technology Co., Ltd."
          />
        </div>
        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div class="ap-sheet-field">
            <div class="ap-sheet-label"><span>所属行业</span></div>
            <Input
              v-model:value="companyForm.industry"
              :maxlength="100"
              placeholder="例如：消费电子 (Consumer Electronics)"
            />
          </div>
          <div class="ap-sheet-field">
            <div class="ap-sheet-label">
              <span>企业官网</span>
              <span class="ap-sheet-optional">选填</span>
            </div>
            <Input
              v-model:value="companyForm.website"
              :maxlength="300"
              placeholder="https://"
            />
          </div>
        </div>
        <div class="ap-sheet-field">
          <div class="ap-sheet-label">
            <span>企业简介与核心优势</span>
            <span class="ap-sheet-optional">推荐英文</span>
          </div>
          <Textarea
            v-model:value="companyForm.intro"
            :rows="5"
            :maxlength="3000"
            show-count
            placeholder="主营产品、工厂规模、认证体系、主要出口市场等，AI 会直接引用回应买家…"
          />
        </div>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.kb {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.kb__head {
  padding: 2px 2px 0;
}

.kb__title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #1d1d1f;
  letter-spacing: -0.015em;
}

.kb__sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: #6e6e73;
}

.kb__company {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  padding: 18px 20px;
  background: #fff;
  border-radius: 18px;
  box-shadow: var(--ap-shadow-rest);
}

.kb__company-icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 44px;
  height: 44px;
  color: #007aff;
  background: linear-gradient(180deg, #eef6ff 0%, #dfeeff 100%);
  border-radius: 13px;
  box-shadow: inset 0 0 0 0.5px rgb(0 122 255 / 14%);
}

.kb__web-chip {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  font-size: 11.5px;
  color: #007aff;
  text-decoration: none;
  background: #f2f7ff;
  border-radius: 999px;
}

.kb__edit {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 28px;
  padding: 0 11px;
  font-size: 12px;
  font-weight: 600;
  color: #3a3a3c;
  cursor: pointer;
  background: #f2f3f7;
  border: 0;
  border-radius: 999px;
  transition:
    background-color var(--ap-dur-fast),
    color var(--ap-dur-fast);
}

.kb__edit:hover {
  color: #007aff;
  background: #e8f2ff;
}

.kb__intro {
  display: -webkit-box;
  margin: 6px 0 0;
  overflow: hidden;
  -webkit-line-clamp: 2;
  font-size: 12.5px;
  line-height: 1.55;
  color: #6e6e73;
  -webkit-box-orient: vertical;
}

.kb__panel {
  padding: 18px 20px;
  background: #fff;
  border-radius: 18px;
  box-shadow: var(--ap-shadow-rest);
}

.kb__bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
}

.kb__seg {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  background: #f2f3f7;
  border-radius: 10px;
}

.kb__seg button {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 30px;
  padding: 0 12px;
  font-size: 12.5px;
  font-weight: 500;
  color: #6e6e73;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 8px;
  transition: all var(--ap-dur-fast);
}

.kb__seg button small {
  padding: 0 5px;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  background: rgb(0 0 0 / 6%);
  border-radius: 999px;
}

.kb__seg button.on {
  font-weight: 600;
  color: #1d1d1f;
  background: #fff;
  box-shadow: 0 1px 4px rgb(0 0 0 / 8%);
}

.kb__search-wrap {
  display: flex;
  gap: 6px;
  align-items: center;
  width: 200px;
  height: 34px;
  padding: 0 11px;
  background: #f2f3f7;
  border-radius: 10px;
}

.kb__search-wrap input {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  color: #1d1d1f;
  background: transparent;
  border: 0;
  outline: none;
}

.kb__clear {
  display: grid;
  place-items: center;
  width: 16px;
  height: 16px;
  color: #fff;
  cursor: pointer;
  background: #c7c7cc;
  border: 0;
  border-radius: 50%;
}

.kb__kinds {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
}

.kb__kinds button {
  height: 28px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 500;
  color: #6e6e73;
  cursor: pointer;
  background: #f5f6fa;
  border: 0;
  border-radius: 999px;
  transition: all var(--ap-dur-fast);
}

.kb__kinds button:hover:not(.on) {
  color: #1d1d1f;
  background: #ebedf3;
}

.kb__kinds button.on {
  font-weight: 600;
  color: #007aff;
  background: #e8f2ff;
}

.kb__upload-hidden {
  display: none;
}

.kb__upload-btn {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  height: 34px;
  padding: 0 15px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  cursor: pointer;
  background: linear-gradient(180deg, #1a88ff 0%, #007aff 100%);
  border: 0;
  border-radius: 999px;
  box-shadow: 0 2px 8px rgb(0 122 255 / 24%);
  transition: transform var(--ap-dur-fast) var(--ap-ease);
}

.kb__upload-btn:active:not(:disabled) {
  transform: scale(0.97);
}

.kb__err {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 10px 14px;
  margin-bottom: 12px;
  font-size: 13px;
  color: #d70015;
  background: #ffeceb;
  border-radius: 12px;
}

.kb__dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 24px;
  text-align: center;
  cursor: pointer;
  background: #f8f9fc;
  border: 1.5px dashed #d1d5db;
  border-radius: 16px;
  transition:
    border-color var(--ap-dur-fast),
    background-color var(--ap-dur-fast);
}

.kb__dropzone:hover {
  background: #f2f7ff;
  border-color: #007aff;
}

.kb__drop-orb {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  margin-bottom: 12px;
  color: #007aff;
  background: #e8f2ff;
  border-radius: 14px;
}

.kb__icon {
  display: inline-flex;
  flex: 0 0 34px;
  align-items: center;
  justify-content: center;
  height: 30px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.02em;
  border-radius: 8px;
}

.kb__more-btn {
  display: inline-grid;
  place-items: center;
  width: 28px;
  height: 28px;
  color: #8e8e93;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 8px;
  transition:
    background-color var(--ap-dur-fast),
    color var(--ap-dur-fast);
}

.kb__more-btn:hover {
  color: #1d1d1f;
  background: #f2f3f7;
}
</style>
