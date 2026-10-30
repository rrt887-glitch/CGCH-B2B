<script lang="ts" setup>
/**
 * 邮件模板（与原网站一致）：按分类筛选、搜索；新建 / 编辑（标题可一键翻译，正文富文本，电脑 / 手机预览）、
 * 启用停用、设为默认、删除。写邮件时可以一键套用模板。
 */
import { fmtDateTime } from '#/utils/datetime';
import type { MailTemplateApi } from '#/api/channel/mail-template';

import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { IconifyIcon } from '@vben/icons';
import { useUserStore } from '@vben/stores';

import { Button, Empty, Input, message, Modal, Pagination, Popconfirm, Radio, RadioGroup, Select, Switch, Table, Tag } from 'ant-design-vue';

import {
  deleteMailTemplate,
  getMailTemplates,
  MAIL_TEMPLATE_CATEGORIES,
  saveMailTemplate,
  setMailTemplateDefault,
  setMailTemplateEnabled,
} from '#/api/channel/mail-template';
import { translateTexts } from '#/api/channel/translate';
import { Tinymce } from '#/components/tinymce';

const category = ref('');
const keyword = ref('');
const rows = ref<MailTemplateApi.Template[]>([]);
const loading = ref(false);
const page = reactive({ no: 1, size: 20 });
const paged = computed(() => rows.value.slice((page.no - 1) * page.size, page.no * page.size));
const catLabel = (v: string) => MAIL_TEMPLATE_CATEGORIES.find((c) => c.value === v)?.label ?? v;

const columns = [
  { title: '模板名称', key: 'name' },
  { title: '分类', key: 'category', width: 110 },
  { title: '可见范围', key: 'visibility', width: 100 },
  { title: '状态', key: 'enabled', width: 90 },
  { title: '是否默认', key: 'defaulted', width: 100 },
  { title: '最近更新时间', key: 'updateTime', width: 160 },
  { title: '操作', key: 'action', width: 170 },
];

async function load() {
  loading.value = true;
  try {
    rows.value = await getMailTemplates({ category: category.value || undefined, keyword: keyword.value.trim() || undefined });
  } finally {
    loading.value = false;
  }
}

// ===== 编辑 =====
const open = ref(false);
const saving = ref(false);
const translating = ref(false);
const view = ref<'desktop' | 'edit' | 'mobile'>('edit');
const { hasAccessByCodes } = useAccess();
const userStore = useUserStore();
/** 自己建的模板可以改；别人建的全员模板需要「全员邮件模板维护」权限 */
const canEdit = (t: MailTemplateApi.Template) =>
  !t.id || t.ownerUserId === userStore.userInfo?.id || hasAccessByCodes(['channel:mail-template:manage']);
const empty = (): MailTemplateApi.Template => ({ name: '', category: 'promo', visibility: 'all', subject: '', html: '', enabled: true, defaulted: false });
const form = ref<MailTemplateApi.Template>(empty());

function edit(t?: MailTemplateApi.Template) {
  form.value = t ? JSON.parse(JSON.stringify(t)) : empty();
  view.value = 'edit';
  open.value = true;
}

async function translateSubject() {
  if (!form.value.subject.trim()) return;
  translating.value = true;
  try {
    const [t] = await translateTexts([form.value.subject], { target: 'English' });
    if (t) form.value.subject = t;
  } finally {
    translating.value = false;
  }
}

async function save() {
  if (!form.value.name.trim()) {
    message.warning('请输入模板名称');
    return;
  }
  saving.value = true;
  try {
    await saveMailTemplate(form.value);
    message.success(form.value.id ? '已保存' : '模板已创建');
    open.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function toggle(t: MailTemplateApi.Template, v: boolean) {
  await setMailTemplateEnabled(t.id!, v);
  t.enabled = v;
}

async function makeDefault(t: MailTemplateApi.Template) {
  await setMailTemplateDefault(t.id!);
  message.success(`已设为「${catLabel(t.category)}」的默认模板`);
  await load();
}

async function remove(t: MailTemplateApi.Template) {
  await deleteMailTemplate(t.id!);
  message.success('已删除');
  await load();
}

const previewDoc = computed(
  () =>
    `<!doctype html><html><head><meta charset="utf-8"><style>body{font:14px/1.6 -apple-system,Segoe UI,Arial,sans-serif;margin:16px;color:#222;word-break:break-word}img{max-width:100%}</style></head><body>${form.value.html}</body></html>`,
);

onMounted(load);
</script>

<template>
  <div class="et">
    <h2 class="m-0 text-xl font-bold text-gray-900">邮件模板</h2>
    <p class="mb-4 mt-1 text-sm text-gray-500">管理邮件营销模板，支持新建、编辑、停用与删除</p>

    <div class="mb-3 flex flex-wrap items-center gap-3">
      <Select v-model:value="category" class="w-32" :options="[{ value: '', label: '全部' }, ...MAIL_TEMPLATE_CATEGORIES]" @change="load" />
      <Input v-model:value="keyword" allow-clear class="w-72" placeholder="搜索模板名称" @press-enter="load" @change="(e: any) => !e.target.value && load()">
        <template #suffix><IconifyIcon icon="lucide:search" class="size-3.5 cursor-pointer text-gray-400" @click="load" /></template>
      </Input>
      <div class="flex-1"></div>
      <Button class="et__dark" @click="edit()">新建模板 <IconifyIcon icon="lucide:plus" class="ml-1 inline size-4" /></Button>
    </div>

    <Table :columns="columns" :data-source="paged" :loading="loading" :pagination="false" row-key="id" size="middle" :scroll="{ x: 860 }">
      <template #bodyCell="{ column, record }">
        <a v-if="column.key === 'name'" class="font-medium" @click="edit(record as any)">{{ record.name }}</a>
        <template v-else-if="column.key === 'category'">{{ catLabel(record.category) }}</template>
        <template v-else-if="column.key === 'visibility'">{{ record.visibility === 'self' ? '仅自己' : '全员' }}</template>
        <Switch v-else-if="column.key === 'enabled'" size="small" :disabled="!canEdit(record as any)" :checked="!!record.enabled" @change="(v: any) => toggle(record as any, !!v)" />
        <template v-else-if="column.key === 'defaulted'">
          <Tag v-if="record.defaulted" color="blue" class="m-0">默认</Tag>
          <a v-else class="text-xs" @click="makeDefault(record as any)">设为默认</a>
        </template>
        <template v-else-if="column.key === 'updateTime'">{{ fmtDateTime(record.updateTime) }}</template>
        <div v-else-if="column.key === 'action'" class="flex gap-3">
          <template v-if="canEdit(record as any)">
            <a @click="edit(record as any)">编辑</a>
            <Popconfirm title="删除这个模板？" @confirm="remove(record as any)"><a class="text-red-500">删除</a></Popconfirm>
          </template>
          <span v-else class="text-xs text-gray-400">管理员维护</span>
        </div>
      </template>
      <template #emptyText><Empty :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无邮件模板" /></template>
    </Table>
    <div class="mt-4 flex items-center justify-end gap-2 text-sm text-gray-500">
      共 {{ rows.length }} 项数据
      <Pagination v-model:current="page.no" v-model:page-size="page.size" :total="rows.length" show-size-changer show-quick-jumper size="small" />
    </div>

    <Modal v-model:open="open" :title="form.id ? '编辑模板' : '创建模板'" :width="920" :footer="null" :body-style="{ paddingTop: '8px' }" centered>
      <div class="grid grid-cols-1 gap-3 md:grid-cols-3">
        <div class="md:col-span-2">
          <div class="et__label">模板名称</div>
          <Input v-model:value="form.name" :maxlength="100" placeholder="请输入模板名称" />
        </div>
        <div>
          <div class="et__label">分类</div>
          <Select v-model:value="form.category" class="w-full" :options="MAIL_TEMPLATE_CATEGORIES" />
        </div>
      </div>

      <div class="et__box mt-3">
        <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
          <div class="et__seg">
            <button type="button" :class="{ on: view === 'edit' }" @click="view = 'edit'"><IconifyIcon icon="lucide:pencil" class="size-3.5" />编辑</button>
            <button type="button" :class="{ on: view === 'desktop' }" @click="view = 'desktop'"><IconifyIcon icon="lucide:monitor" class="size-3.5" />电脑</button>
            <button type="button" :class="{ on: view === 'mobile' }" @click="view = 'mobile'"><IconifyIcon icon="lucide:smartphone" class="size-3.5" />手机</button>
          </div>
          <RadioGroup v-model:value="form.visibility" size="small">
            <Radio value="all">全员可见</Radio>
            <Radio value="self">仅自己</Radio>
          </RadioGroup>
        </div>
        <div class="mb-3 flex gap-2">
          <Input v-model:value="form.subject" :maxlength="250" placeholder="请输入邮件标题" />
          <Button :loading="translating" @click="translateSubject"><IconifyIcon icon="lucide:languages" class="mr-1 inline size-4" />翻译</Button>
        </div>
        <Tinymce v-if="view === 'edit'" v-model="form.html" :height="380" />
        <div v-else class="flex justify-center rounded bg-gray-100 p-3">
          <iframe
            :srcdoc="previewDoc"
            sandbox=""
            class="rounded border-0 bg-white shadow-sm"
            :style="{ width: view === 'mobile' ? '375px' : '100%', height: '420px' }"
          ></iframe>
        </div>
      </div>

      <div class="mt-4 flex items-center justify-end gap-3">
        <label class="mr-auto inline-flex items-center gap-2 text-sm"><Switch v-model:checked="form.defaulted" size="small" />设为该分类的默认模板</label>
        <Button @click="open = false">取消</Button>
        <Button type="primary" :loading="saving" @click="save">{{ form.id ? '保存' : '创建模板' }}</Button>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.et {
  padding: 4px 16px 18px;
}

.et__dark {
  color: #fff;
  background: #111827;
  border-color: #111827;
}

.et__dark:hover {
  color: #fff !important;
  background: #1f2937 !important;
}

.et__label {
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #111827;
}

.et__box {
  padding: 12px;
  border: 1px solid #eef0f3;
  border-radius: 10px;
}

.et__seg {
  display: inline-flex;
  padding: 3px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.et__seg button {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 3px 10px;
  font-size: 13px;
  color: #6b7280;
  cursor: pointer;
  background: none;
  border: 0;
  border-radius: 6px;
}

.et__seg button.on {
  color: #2563EB;
  background: #eef3fe;
}
</style>
