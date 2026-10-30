<script lang="ts" setup>
/**
 * 消息分流设置（与原网站一致）：左侧规则列表（可启停），右侧编辑「当 消息来源于… / 如果 满足以下全部条件… / 那么 将消息分配给…」。
 * 新询盘建档时，按顺序取第一条命中的启用规则；负责人有多位时轮流分配。已被转派的客户不再执行自动分配。
 */
import type { RoutingApi } from '#/api/channel/routing';

import { computed, onMounted, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Button, Empty, Input, message, Popconfirm, Select, Switch } from 'ant-design-vue';

import { deleteRoutingRule, getRoutingRules, ROUTING_SOURCES, saveRoutingRule, setRoutingRuleEnabled } from '#/api/channel/routing';
import { getSimpleUserList } from '#/api/system/user';
import { COUNTRY_OPTIONS } from '#/views/cgch/crm/shared/countries';

const rules = ref<RoutingApi.Rule[]>([]);
const users = ref<{ id: number; nickname: string }[]>([]);
const current = ref<null | RoutingApi.Rule>(null);
const saving = ref(false);

const split = (s?: string) => (s ? s.split(',').filter(Boolean) : []);
const userOptions = computed(() => users.value.map((u) => ({ value: u.id, label: u.nickname })));
const sourceText = (r: RoutingApi.Rule) =>
  split(r.sources).map((s) => ROUTING_SOURCES.find((x) => x.value === s)?.label ?? s).join('、') || '全部渠道';
const assigneeText = (r: RoutingApi.Rule) =>
  r.assigneeIds.map((id) => users.value.find((u) => u.id === id)?.nickname ?? `#${id}`).join('、');

// 编辑中的表单（数组形式更好绑定）
const sources = ref<string[]>([]);
const hasCountry = ref(false);
const countryOp = ref<'in' | 'not_in'>('in');
const countries = ref<string[]>([]);

async function load() {
  rules.value = await getRoutingRules();
}

function edit(r?: RoutingApi.Rule) {
  const v: RoutingApi.Rule = r
    ? JSON.parse(JSON.stringify(r))
    : { name: `分配规则 ${rules.value.length + 1}`, enabled: true, sources: '', countryOp: '', countries: '', assigneeIds: [], sort: rules.value.length };
  current.value = v;
  sources.value = split(v.sources);
  hasCountry.value = !!v.countryOp;
  countryOp.value = v.countryOp === 'not_in' ? 'not_in' : 'in';
  countries.value = split(v.countries);
}

async function save() {
  const v = current.value!;
  if (!v.assigneeIds.length) return message.warning('请选择负责人');
  if (hasCountry.value && !countries.value.length) return message.warning('请选择客户国家');
  saving.value = true;
  try {
    const id = await saveRoutingRule({
      ...v,
      sources: sources.value.join(','),
      countryOp: hasCountry.value ? countryOp.value : '',
      countries: hasCountry.value ? countries.value.join(',') : '',
    });
    message.success('已保存');
    await load();
    edit(rules.value.find((r) => r.id === id));
  } finally {
    saving.value = false;
  }
}

async function toggle(r: RoutingApi.Rule, v: boolean) {
  await setRoutingRuleEnabled(r.id!, v);
  r.enabled = v;
  if (current.value?.id === r.id) current.value!.enabled = v;
}

async function remove(r: RoutingApi.Rule) {
  await deleteRoutingRule(r.id!);
  if (current.value?.id === r.id) current.value = null;
  await load();
}

onMounted(async () => {
  users.value = (await getSimpleUserList()) as any;
  await load();
  if (rules.value[0]) edit(rules.value[0]);
});
</script>

<template>
  <div class="rt">
    <h2 class="m-0 text-xl font-bold text-gray-900">消息分流设置</h2>
    <p class="mb-4 mt-1 text-sm text-gray-500">
      按渠道与客户国家，把新询盘自动分配给对应负责人；已被转派的客户，将不再执行任何自动分配规则
    </p>

    <div class="rt__wrap">
      <aside class="rt__list">
        <div class="mb-3 flex items-center justify-between">
          <span class="font-semibold text-gray-900">分配规则</span>
          <Button size="small" type="primary" @click="edit()"><IconifyIcon icon="lucide:plus" class="mr-1 inline size-3.5" />新建规则</Button>
        </div>
        <div v-if="!rules.length" class="py-8"><Empty :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无分配规则" /></div>
        <div
          v-for="r in rules"
          :key="r.id"
          class="rt__item"
          :class="{ on: current?.id === r.id }"
          @click="edit(r)"
        >
          <div class="min-w-0 flex-1">
            <div class="truncate font-medium text-gray-900">{{ r.name }}</div>
            <div class="mt-0.5 truncate text-xs text-gray-500">{{ sourceText(r) }} → {{ assigneeText(r) }}</div>
          </div>
          <Switch size="small" :checked="r.enabled" @click.stop @change="(v: any) => toggle(r, !!v)" />
        </div>
      </aside>

      <section class="rt__editor">
        <div v-if="!current" class="py-16"><Empty description="选择左侧规则进行编辑，或新建一条规则" /></div>
        <template v-else>
          <div class="mb-5 flex items-center gap-3">
            <Input v-model:value="current.name" :maxlength="100" class="max-w-xs font-semibold" placeholder="规则名称" />
            <label class="inline-flex items-center gap-2 text-sm text-gray-600"><Switch v-model:checked="current.enabled" size="small" />启用</label>
          </div>

          <div class="rt__step">
            <span class="rt__tag">当</span>
            <div class="flex-1">
              <div class="rt__hint">消息来源于</div>
              <Select
                v-model:value="sources"
                mode="multiple"
                class="w-full"
                allow-clear
                placeholder="请选择渠道（不选表示全部渠道）"
                :options="ROUTING_SOURCES"
              />
            </div>
          </div>

          <div class="rt__step">
            <span class="rt__tag">如果</span>
            <div class="flex-1">
              <div class="rt__hint">满足以下全部条件</div>
              <div v-if="hasCountry" class="flex flex-wrap items-center gap-2">
                <span class="rt__field">客户国家</span>
                <Select
                  v-model:value="countryOp"
                  class="w-24"
                  :options="[{ value: 'in', label: '属于' }, { value: 'not_in', label: '不属于' }]"
                />
                <Select
                  v-model:value="countries"
                  mode="multiple"
                  class="min-w-[240px] flex-1"
                  show-search
                  option-filter-prop="label"
                  placeholder="请选择国家"
                  :options="COUNTRY_OPTIONS"
                />
                <a class="text-gray-400" @click="hasCountry = false"><IconifyIcon icon="lucide:trash-2" class="size-4" /></a>
              </div>
              <a v-else class="text-sm" @click="hasCountry = true"><IconifyIcon icon="lucide:plus" class="mr-1 inline size-3.5" />添加条件</a>
              <div v-if="hasCountry" class="mt-1 text-xs text-gray-400">客户国家由 AI 从对话、邮箱域名或手机号识别；识别不到国家时不算「属于」</div>
            </div>
          </div>

          <div class="rt__step">
            <span class="rt__tag">那么</span>
            <div class="flex-1">
              <div class="rt__hint">将消息分配给</div>
              <Select
                v-model:value="current.assigneeIds"
                mode="multiple"
                class="w-full"
                show-search
                option-filter-prop="label"
                placeholder="请选择负责人"
                :options="userOptions"
              />
              <div class="mt-1 text-xs text-gray-400">选择多位负责人时，新询盘按顺序轮流分配</div>
            </div>
          </div>

          <div class="mt-6 flex items-center gap-3">
            <Button type="primary" :loading="saving" @click="save">保存</Button>
            <Button @click="current = null">取消</Button>
            <Popconfirm v-if="current.id" title="删除这条规则？" @confirm="remove(current)">
              <Button danger class="ml-auto">删除</Button>
            </Popconfirm>
          </div>
          <p class="mt-4 text-xs text-gray-400">多条规则同时命中时，按左侧顺序取第一条；没有规则命中时，询盘仍分配给接收它的渠道账号所属成员。</p>
        </template>
      </section>
    </div>
  </div>
</template>

<style scoped>
.rt {
  padding: 4px 16px 18px;
}

.rt__wrap {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 16px;
}

@media (max-width: 900px) {
  .rt__wrap {
    grid-template-columns: 1fr;
  }
}

.rt__list,
.rt__editor {
  padding: 16px;
  background: #fff;
  border: 1px solid #eceef3;
  border-radius: 12px;
}

.rt__item {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  margin-bottom: 6px;
  cursor: pointer;
  border: 1px solid transparent;
  border-radius: 8px;
}

.rt__item:hover {
  background: #f8fafc;
}

.rt__item.on {
  background: #EFF4FF;
  border-color: #c7d7fd;
}

.rt__step {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  padding: 14px 0;
  border-top: 1px dashed #eceef3;
}

.rt__tag {
  flex: none;
  width: 44px;
  padding: 3px 0;
  font-size: 13px;
  font-weight: 700;
  color: #2563EB;
  text-align: center;
  background: #EFF4FF;
  border-radius: 6px;
}

.rt__hint {
  margin-bottom: 8px;
  font-size: 13px;
  color: #6b7280;
}

.rt__field {
  padding: 4px 10px;
  font-size: 13px;
  background: #f3f4f6;
  border-radius: 6px;
}
</style>
