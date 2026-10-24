<script lang="ts" setup>
/**
 * 官网IM「网站绑定」（与原网站一致）：说明 + 已绑定网站列表 + 新增绑定（绑定域名、官网名称）。
 * 绑定后给出嵌入代码，放到网站每个页面即可在右下角显示聊天窗口；访客消息进入「会话」，由 AI 或业务员回复。
 */
import type { ChannelWaApi } from '#/api/channel/whatsapp';

import { reactive, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Button, Empty, Input, message, Modal } from 'ant-design-vue';

import { createWebSite, updateWebSite, webImEmbedCode } from '#/api/channel/web-im';

defineProps<{ sites: ChannelWaApi.Account[] }>();
const emit = defineEmits<{ changed: [] }>();
const open = defineModel<boolean>('open', { default: false });

const formOpen = ref(false);
const saving = ref(false);
const form = reactive({ id: undefined as number | undefined, domain: '', name: '' });

function add() {
  Object.assign(form, { id: undefined, domain: '', name: '' });
  formOpen.value = true;
}

function edit(s: ChannelWaApi.Account) {
  Object.assign(form, { id: s.id, domain: s.displayPhone ?? '', name: s.name ?? '' });
  formOpen.value = true;
}

async function save() {
  if (!form.domain.trim()) return message.warning('请输入绑定域名');
  if (!form.name.trim()) return message.warning('请输入官网名称');
  saving.value = true;
  try {
    await (form.id ? updateWebSite({ id: form.id, domain: form.domain, name: form.name }) : createWebSite(form));
    message.success(form.id ? '已保存' : '绑定成功，把嵌入代码放到网站上即可');
    formOpen.value = false;
    emit('changed');
  } finally {
    saving.value = false;
  }
}

async function copy(s: ChannelWaApi.Account) {
  await navigator.clipboard.writeText(webImEmbedCode(s.phoneNumberId));
  message.success('嵌入代码已复制');
}

defineExpose({ add, edit });
</script>

<template>
  <Modal v-model:open="open" :footer="null" :width="760" centered :body-style="{ padding: 0 }" wrap-class-name="wi-wrap">
    <div class="wi">
      <h2 class="wi__title">网站绑定</h2>
      <div class="wi__sub">Instructions for Use</div>
      <p class="wi__desc">绑定网站域名,示例值(www.aib2b.com)</p>

      <div v-if="!sites.length" class="py-4"><Empty :image="Empty.PRESENTED_IMAGE_SIMPLE" description="还没有绑定网站" /></div>
      <div v-for="s in sites" :key="s.id" class="wi__site">
        <div class="flex items-center gap-2">
          <IconifyIcon icon="lucide:globe" class="size-4 text-blue-600" />
          <span class="font-semibold text-gray-900">{{ s.name }}</span>
          <span class="text-sm text-gray-500">{{ s.displayPhone }}</span>
          <span v-if="!s.enabled" class="text-xs text-gray-400">（已停用）</span>
          <a class="ml-auto text-sm" @click="edit(s)">修改</a>
        </div>
        <div class="wi__code">
          <code>{{ webImEmbedCode(s.phoneNumberId) }}</code>
          <Button size="small" @click="copy(s)"><IconifyIcon icon="lucide:copy" class="mr-1 inline size-3.5" />复制</Button>
        </div>
        <div class="text-xs text-gray-400">把这段代码放到网站每个页面的 &lt;/body&gt; 之前，右下角会出现聊天按钮；访客消息出现在「会话」里，AI 按「智能体」的设置自动接待。</div>
      </div>

      <a class="wi__add" @click="add">新增</a>
    </div>

    <Modal v-model:open="formOpen" :title="form.id ? '修改绑定' : '新增绑定'" :width="500" centered>
      <div class="wi__row">
        <label><i>*</i> 绑定域名：</label>
        <Input v-model:value="form.domain" placeholder="请输入绑定域名" />
      </div>
      <div class="wi__row">
        <label><i>*</i> 官网名称：</label>
        <Input v-model:value="form.name" :maxlength="60" placeholder="请输入官网名称" />
      </div>
      <template #footer>
        <Button @click="formOpen = false">取 消</Button>
        <Button type="primary" :loading="saving" @click="save">确 定</Button>
      </template>
    </Modal>
  </Modal>
</template>

<style scoped>
.wi {
  min-height: 272px;
  padding: 48px 48px 36px;
  background: linear-gradient(120deg, #f8fafc, #eef2f7);
  border-radius: 8px;
}

.wi__title {
  margin: 0;
  font-size: 30px;
  font-weight: 500;
  color: #1d4ed8;
}

.wi__sub {
  margin-top: 4px;
  font-size: 20px;
  color: #1d4ed8;
}

.wi__desc {
  margin: 26px 0 14px;
  font-size: 15px;
  color: #1d4ed8;
}

.wi__site {
  padding: 14px 16px;
  margin-bottom: 10px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
}

.wi__code {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
  margin: 10px 0 6px;
  font-size: 12px;
  background: #f3f4f6;
  border-radius: 6px;
}

.wi__code code {
  flex: 1;
  overflow-x: auto;
  white-space: nowrap;
}

.wi__add {
  display: inline-block;
  padding: 4px 20px;
  font-size: 14px;
  color: #6b7280;
}

.wi__row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin: 14px 0;
}

.wi__row label {
  flex: none;
  width: 92px;
  text-align: right;
}

.wi__row i {
  font-style: normal;
  color: #ef4444;
}
</style>
