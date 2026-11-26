<script lang="ts" setup>
/**
 * 知识库：上方是企业信息（名称、行业、官网、简介，可编辑）；下方是资料文件，
 * 上传 PDF / Word / Excel / PPT 后自动解析学习，AI 回复时检索使用。也可以切到「文字条目」手动录入。
 */
import { fmtDateTime } from '#/utils/datetime';
import type { AgentApi } from '#/api/channel/agent';

import { computed, defineAsyncComponent, onMounted, reactive, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import {
  Button,
  Dropdown,
  Empty,
  Input,
  Menu,
  MenuItem,
  message,
  Modal,
  Pagination,
  Select,
  Table,
  Textarea,
  Tooltip,
  Upload,
} from 'ant-design-vue';

import { deleteKnowledgeFile, getKnowledgeFiles, relearnKnowledgeFile, uploadKnowledgeFile } from '#/api/channel/agent';

import { clone, useAgentSettings } from './use-settings';

const TextKnowledge = defineAsyncComponent(() => import('#/views/cgch/agent/knowledge/index.vue'));

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
  { value: '', label: '所有文件类型' },
  { value: 'pdf', label: 'PDF', ext: ['pdf'] },
  { value: 'word', label: 'Word', ext: ['doc', 'docx', 'rtf'] },
  { value: 'excel', label: 'Excel', ext: ['xls', 'xlsx', 'csv'] },
  { value: 'ppt', label: 'PPT', ext: ['ppt', 'pptx'] },
  { value: 'text', label: '文本', ext: ['txt', 'md', 'html', 'htm'] },
];
const ICON: Record<string, { color: string; letter: string }> = {
  pdf: { color: '#f97316', letter: 'P' },
  word: { color: '#0071e3', letter: 'W' },
  excel: { color: '#16a34a', letter: 'X' },
  ppt: { color: '#ea580c', letter: 'P' },
  text: { color: '#86868b', letter: 'T' },
};
const kindOf = (ext: string) => KINDS.find((k) => k.ext?.includes(ext))?.value || 'text';

const filtered = computed(() =>
  files.value.filter(
    (f) => (!keyword.value || f.name.toLowerCase().includes(keyword.value.toLowerCase())) && (!kind.value || kindOf(f.fileType) === kind.value),
  ),
);
const paged = computed(() => filtered.value.slice((page.no - 1) * page.size, page.no * page.size));

const columns = [
  { title: '资料', key: 'name' },
  { title: '上传时间', key: 'createTime', width: 130 },
  { title: '学习进度', key: 'status', width: 120 },
  { title: '操作', key: 'action', width: 70 },
];

async function loadFiles() {
  const version = ++fileLoadVersion;
  loading.value = true;
  loadFailed.value = false;
  try {
    const result = await getKnowledgeFiles();
    if (version !== fileLoadVersion) return;
    files.value = result;
    page.no = Math.min(page.no, Math.max(1, Math.ceil(filtered.value.length / page.size)));
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
    if (f.status === 'failed') message.warning(`${f.name}：${f.errorMsg || '学习失败'}`);
    else if (f.status === 'done') message.success(`${f.name} 学习完成，拆成 ${f.chunks} 段资料`);
    else message.info(`${f.name} 正在学习`);
    await loadFiles();
  } catch {
    // 请求层已经提示上传失败，返回 false 阻止控件再次发送。
  } finally {
    uploading.value--;
  }
  return false;
}

/**
 * 打开文件选择框。
 *
 * 不能直接写成 <Upload><Button>上传资料</Button></Upload>：ant-design-vue 的
 * rc-upload 会把子节点包进一个 tabindex="0"、role="button" 的容器里，
 * 于是键盘导航和读屏会看到两个同名「上传资料」按钮，同一次操作重复获得焦点。
 * 这里改为隐藏 Upload 本体，用我们自己的按钮去点它内部的 file input。
 */
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
    else message.success('已重新学习');
    await loadFiles();
  }
  if (key === 'delete') {
    Modal.confirm({
      title: `删除「${f.name}」？`,
      content: '删除后 AI 不再使用这份资料回答客户。',
      okType: 'danger',
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
const companyForm = ref<AgentApi.Company>({ name: '', industry: '', website: '', intro: '' });
const company = computed(() => settings.value?.company);

function editCompany() {
  companyForm.value = clone(company.value ?? { name: '', industry: '', website: '', intro: '' });
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
        <p class="kb__sub">上传产品目录、报价单、FAQ 或录入文字条目，AI 接待时自动检索引用</p>
      </div>
    </header>

    <div class="kb__company">
      <div class="kb__company-icon">
        <IconifyIcon icon="lucide:building-2" class="size-5" />
      </div>
      <div class="min-w-0 flex-1">
        <template v-if="company?.name || company?.intro">
          <div class="flex flex-wrap items-center gap-2">
            <span class="text-[15px] font-semibold text-[#1D1D1F]">{{ company.name || '未填写公司名称' }}</span>
            <span v-if="company.industry" class="kb__tag">{{ company.industry }}</span>
            <button type="button" class="kb__edit" @click="editCompany">
              <IconifyIcon icon="lucide:pencil" class="size-3" />编辑
            </button>
          </div>
          <a v-if="company.website" :href="company.website" target="_blank" class="mt-0.5 block text-xs text-[#007AFF]">{{ company.website }}</a>
          <Tooltip :title="company.intro" placement="bottomLeft" :overlay-style="{ maxWidth: '560px' }">
            <p class="kb__intro">{{ company.intro }}</p>
          </Tooltip>
        </template>
        <div v-else class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <div class="text-[14px] font-semibold text-[#1D1D1F]">企业基础信息</div>
            <div class="mt-0.5 text-xs text-[#86868B]">填写公司名称、行业与主营简介，AI 会以企业代表身份向买家自我介绍</div>
          </div>
          <Button type="primary" size="small" @click="editCompany">填写企业信息</Button>
        </div>
      </div>
    </div>

    <div class="kb__panel">

    <div class="kb__bar">
      <div class="kb__seg">
        <button type="button" :class="{ on: view === 'files' }" @click="view = 'files'">资料文件</button>
        <button type="button" :class="{ on: view === 'text' }" @click="view = 'text'">文字条目</button>
      </div>
      <template v-if="view === 'files'">
        <Input v-model:value="keyword" allow-clear class="kb__search" placeholder="搜索文件名称..." @change="page.no = 1">
          <template #suffix><IconifyIcon icon="lucide:search" class="size-3.5 text-gray-400" /></template>
        </Input>
        <div class="flex-1"></div>
        <Select v-model:value="kind" class="w-32" :options="KINDS" @change="page.no = 1" />
        <Upload
          ref="uploadRef"
          class="kb__upload-hidden"
          :show-upload-list="false"
          :open-file-dialog-on-click="false"
          multiple
          :before-upload="beforeUpload"
          accept=".pdf,.doc,.docx,.xls,.xlsx,.csv,.ppt,.pptx,.txt,.md,.html,.htm,.rtf"
        />
        <Button type="primary" :loading="uploading > 0" @click="pickFiles"><IconifyIcon icon="lucide:upload" class="mr-1 inline size-4" />上传资料</Button>
      </template>
    </div>

    <template v-if="view === 'files'">
      <div v-if="loadFailed" class="mb-2 flex items-center gap-2 text-sm text-red-600">
        资料列表加载失败
        <Button size="small" @click="loadFiles">重试</Button>
      </div>
      <Table :columns="columns" :data-source="paged" :loading="loading" :pagination="false" row-key="id" size="middle">
        <template #bodyCell="{ column, record }">
          <div v-if="column.key === 'name'" class="flex min-w-0 items-center gap-2">
            <span class="kb__icon" :style="{ background: ICON[kindOf(record.fileType)]!.color }">{{ ICON[kindOf(record.fileType)]!.letter }}</span>
            <span class="truncate">{{ record.name }}</span>
          </div>
          <template v-else-if="column.key === 'createTime'">{{ fmtDateTime(record.createTime, 'YYYY/MM/DD') }}</template>
          <template v-else-if="column.key === 'status'">
            <span v-if="record.status === 'done'" class="text-green-600">学习完成</span>
            <Tooltip v-else-if="record.status === 'failed'" :title="record.errorMsg">
              <span class="cursor-help text-red-500">学习失败</span>
            </Tooltip>
            <span v-else class="text-amber-500">学习中</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <Dropdown :trigger="['click']" placement="bottomRight">
              <IconifyIcon icon="lucide:ellipsis" class="size-5 cursor-pointer text-gray-400" />
              <template #overlay>
                <Menu @click="({ key }: any) => onMenu(record as any, String(key))">
                  <MenuItem key="download">下载</MenuItem>
                  <MenuItem key="relearn">重新学习</MenuItem>
                  <MenuItem key="delete" danger>删除</MenuItem>
                </Menu>
              </template>
            </Dropdown>
          </template>
        </template>
        <template #emptyText>
          <Empty
            :image="Empty.PRESENTED_IMAGE_SIMPLE"
            :description="
              keyword.trim()
                ? `没有找到与“${keyword.trim()}”匹配的资料`
                : kind
                  ? '当前类型下还没有资料'
                  : '还没有资料，上传产品目录、FAQ、报价规则等文件，AI 就能据此回答客户'
            "
          />
        </template>
      </Table>
      <div class="mt-4 flex items-center justify-end gap-2 text-sm text-gray-500">
        共 {{ filtered.length }} 项数据
        <Pagination v-model:current="page.no" v-model:page-size="page.size" :total="filtered.length" show-size-changer show-quick-jumper size="small" />
      </div>
    </template>
    <div v-else class="kb__text"><TextKnowledge /></div>
    </div>

    <Modal v-model:open="companyOpen" title="企业信息" :width="560" ok-text="保存" @ok="saveCompany">
      <div class="space-y-3">
        <div><div class="mb-1 text-sm">公司名称</div><Input v-model:value="companyForm.name" :maxlength="200" placeholder="例如：东莞市草根出海信息科技有限公司" /></div>
        <div><div class="mb-1 text-sm">所属行业</div><Input v-model:value="companyForm.industry" :maxlength="100" placeholder="例如：消费电子(Consumer Electronics)" /></div>
        <div><div class="mb-1 text-sm">官网</div><Input v-model:value="companyForm.website" :maxlength="300" placeholder="https://" /></div>
        <div>
          <div class="mb-1 text-sm">企业简介</div>
          <Textarea v-model:value="companyForm.intro" :rows="6" :maxlength="3000" show-count placeholder="主营产品、优势、服务的市场等，最好用英文写，AI 会直接引用" />
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
  color: var(--ap-label);
  letter-spacing: -0.015em;
}

.kb__sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--ap-label-3);
}

.kb__company {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 18px 20px;
  background: var(--ap-surface);
  border-radius: 16px;
  box-shadow: var(--ap-shadow-rest);
}

.kb__company-icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 42px;
  height: 42px;
  color: var(--ap-accent);
  background: var(--ap-accent-soft);
  border-radius: 12px;
}

.kb__edit {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 2px 8px;
  font-size: 12px;
  color: var(--ap-label-2);
  cursor: pointer;
  background: #f5f5f7;
  border: 0;
  border-radius: 6px;
}

.kb__edit:hover {
  color: var(--ap-accent);
  background: var(--ap-accent-soft);
}

.kb__panel {
  padding: 18px 20px;
  background: var(--ap-surface);
  border-radius: 16px;
  box-shadow: var(--ap-shadow-rest);
}

.kb__tag {
  padding: 1px 8px;
  font-size: 11.5px;
  font-weight: 500;
  color: var(--ap-accent);
  background: var(--ap-accent-soft);
  border-radius: 999px;
}

.kb__intro {
  display: -webkit-box;
  margin: 4px 0 0;
  overflow: hidden;
  -webkit-line-clamp: 1;
  font-size: 12.5px;
  color: var(--ap-label-3);
  -webkit-box-orient: vertical;
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
  padding: 2px;
  background: var(--ap-fill-3);
  border-radius: 8px;
}

.kb__seg button {
  padding: 4px 12px;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--ap-label-2);
  cursor: pointer;
  background: none;
  border: 0;
  border-radius: 6px;
  transition: all var(--ap-dur-fast);
}

.kb__seg button.on {
  font-weight: 600;
  color: var(--ap-label);
  background: #fff;
  box-shadow: 0 1px 2px rgb(0 0 0 / 8%);
}

.kb__search {
  width: 220px;
}

.kb__upload-hidden {
  display: none;
}

.kb__icon {
  display: inline-flex;
  flex: 0 0 24px;
  align-items: center;
  justify-content: center;
  height: 24px;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  border-radius: 6px;
}

.kb__text :deep(.p-5),
.kb__text :deep(.p-4) {
  padding: 0;
}
</style>
