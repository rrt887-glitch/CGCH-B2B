<script lang="ts" setup>
/**
 * 通知设置（与原网站一致）：只对当前登录账号生效。每条规则 = 消息类型 + 通知方式（站内信 / 邮件）+ 开关。
 * 还没设置任何规则时，所有通知默认以站内信发送。
 */
import type { NotifyRuleApi } from '#/api/channel/notify-rule';

import { computed, onMounted, ref } from 'vue';

import { Button, message, Modal, Popconfirm, Radio, RadioGroup, Select, Switch } from 'ant-design-vue';

import { deleteNotifyRule, getNotifyRules, saveNotifyRule } from '#/api/channel/notify-rule';

const rules = ref<NotifyRuleApi.Rule[]>([]);
const types = ref<NotifyRuleApi.Type[]>([]);
const loading = ref(true);
const CHANNELS = [
  { value: 'site', label: '站内信' },
  { value: 'email', label: '邮件' },
];
const typeLabel = (v: string) => types.value.find((t) => t.value === v)?.label ?? v;
const enabledCount = computed(() => rules.value.filter((r) => r.enabled).length);

async function load() {
  loading.value = true;
  try {
    const r = await getNotifyRules();
    rules.value = r.rules;
    types.value = r.types;
  } finally {
    loading.value = false;
  }
}

const open = ref(false);
const form = ref<NotifyRuleApi.Rule>({ type: '', channel: 'site', enabled: true });

function edit(r?: NotifyRuleApi.Rule) {
  form.value = r ? { ...r } : { type: types.value[0]?.value ?? '', channel: 'site', enabled: true };
  open.value = true;
}

async function save() {
  await saveNotifyRule(form.value);
  message.success('已保存');
  open.value = false;
  await load();
}

async function toggle(r: NotifyRuleApi.Rule, v: boolean) {
  await saveNotifyRule({ ...r, enabled: v });
  r.enabled = v;
}

async function remove(r: NotifyRuleApi.Rule) {
  await deleteNotifyRule(r.id!);
  await load();
}

onMounted(load);
</script>

<template>
  <div class="nt">
    <h2 class="m-0 text-2xl font-bold text-gray-900">通知设置</h2>
    <p class="mb-6 mt-1 text-sm text-gray-500">仅当前登录账号的线索会按此账号的规则发送通知</p>

    <div class="nt__card">
      <div class="mb-4 flex items-center justify-between">
        <span class="text-lg font-bold text-gray-900">通知规则</span>
        <Button type="primary" @click="edit()">新增通知规则</Button>
      </div>
      <div class="nt__row nt__row--head">
        <span>消息类型</span><span>渠道</span><span>状态</span><span>操作</span>
      </div>
      <div v-if="!rules.length && !loading" class="py-6 text-center text-sm text-gray-500">暂无通知规则</div>
      <div v-for="r in rules" :key="r.id" class="nt__row">
        <span>{{ typeLabel(r.type) }}</span>
        <span>{{ r.channel === 'email' ? '邮件' : '站内信' }}</span>
        <span><Switch size="small" :checked="r.enabled" @change="(v: any) => toggle(r, !!v)" /></span>
        <span class="flex gap-3">
          <a @click="edit(r)">编辑</a>
          <Popconfirm title="删除这条规则？" @confirm="remove(r)"><a class="text-red-500">删除</a></Popconfirm>
        </span>
      </div>
      <div class="mt-4 text-sm text-gray-500">
        当前共有 {{ rules.length }} 条规则，
        <template v-if="!rules.length">尚未设置规则，所有通知默认以站内信发送。</template>
        <template v-else>已开启 {{ enabledCount }} 条；没有规则的消息类型不会通知。</template>
      </div>
    </div>

    <Modal v-model:open="open" :title="form.id ? '编辑通知规则' : '新增通知规则'" ok-text="保存" @ok="save">
      <div class="mb-1 text-sm">消息类型</div>
      <Select v-model:value="form.type" class="w-full" :options="types.map((t) => ({ value: t.value, label: t.label }))" />
      <div class="mt-1 text-xs text-gray-400">{{ types.find((t) => t.value === form.type)?.sample }}</div>
      <div class="mb-1 mt-4 text-sm">通知方式</div>
      <RadioGroup v-model:value="form.channel">
        <Radio v-for="c in CHANNELS" :key="c.value" :value="c.value">{{ c.label }}</Radio>
      </RadioGroup>
      <div v-if="form.channel === 'email'" class="mt-1 text-xs text-gray-400">
        用你在「渠道」里绑定的邮箱，发到你在「个人中心」填写的邮箱。
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.nt {
  max-width: 720px;
  padding: 24px 24px 18px;
}

.nt__card {
  padding: 22px 24px;
  background: #fff;
  border: 1px solid #eceef3;
  border-radius: 14px;
}

.nt__row {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 1fr;
  align-items: center;
  padding: 12px 0;
  font-size: 14px;
  color: #1f2937;
  border-bottom: 1px solid #f0f1f3;
}

.nt__row--head {
  font-size: 13px;
  color: #6b7280;
  border-top: 1px solid #f0f1f3;
}
</style>
