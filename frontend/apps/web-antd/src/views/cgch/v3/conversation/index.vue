<script lang="ts" setup>
/**
 * 会话：左栏选择账号（WhatsApp / Facebook / Instagram / 邮箱）与客户等级，中栏会话列表，右栏聊天。
 * 即时消息与邮件共用这个页面，选中邮箱时右侧切换为邮件收件箱。
 */
import type { ChannelMailApi } from '#/api/channel/mail';
import type { ChannelWaApi } from '#/api/channel/whatsapp';

import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Button, Drawer, Empty, Select, Spin, Tag } from 'ant-design-vue';
import { IconifyIcon } from '@vben/icons';

import { getMailAccountList } from '#/api/channel/mail';
import {
  getWaAccountList,
  getWaConversationPage,
  getWaInboxCounts,
} from '#/api/channel/whatsapp';
import MailWorkbench from '#/views/cgch/channel/mail/index.vue';
import ImWorkbench from '#/views/cgch/channel/im/index.vue';

defineOptions({ name: 'CgchV3Conversation' });

const route = useRoute();
const router = useRouter();

const loading = ref(true);
const filtersOpen = ref(false);
const waAccounts = ref<ChannelWaApi.Account[]>([]);
const mailAccounts = ref<ChannelMailApi.Account[]>([]);

/** 当前选择：im:全部 / im:账号编号 / mail:全部 / mail:邮箱编号 */
const selected = ref<{ id?: number; kind: 'im' | 'mail' }>({ kind: 'im' });
/** 视图（参考 Chatwoot）：我的会话 / 团队会话（主管看下属负责的） */
const view = ref<'mine' | 'team'>('mine');
const counts = ref<ChannelWaApi.InboxCounts>();
async function loadCounts() {
  counts.value = await getWaInboxCounts().catch(() => counts.value);
}
let countTimer: ReturnType<typeof setInterval> | undefined;
onBeforeUnmount(() => countTimer && clearInterval(countTimer));
function pickView(v: 'mine' | 'team') {
  openId.value = undefined;
  view.value = v;
  if (selected.value.kind !== 'im') selected.value = { kind: 'im' };
  router.replace({
    query: { ...route.query, channel: undefined, ref: undefined },
  });
}
const openId = ref<number>();
watch(
  () => route.query.channel,
  (channel) => {
    if (channel === 'mail' && selected.value.kind !== 'mail') {
      selected.value = { kind: 'mail' };
      openId.value = undefined;
    } else if (channel === undefined && selected.value.kind === 'mail') {
      selected.value = { kind: 'im' };
      openId.value = undefined;
    }
  },
);

const filters = ref<{
  levels: number[];
  group?: boolean;
  highPriority: boolean;
}>({ levels: [], highPriority: false });
const draft = ref({ ...filters.value, levels: [] as number[] });
const filterCount = computed(
  () =>
    filters.value.levels.length +
    Number(filters.value.group !== undefined) +
    Number(filters.value.highPriority),
);
function openFilters() {
  draft.value = { ...filters.value, levels: [...filters.value.levels] };
  filtersOpen.value = true;
}
function applyFilters() {
  filters.value = { ...draft.value, levels: [...draft.value.levels] };
  filtersOpen.value = false;
}
function toggleLevel(n: number) {
  draft.value.levels = draft.value.levels.includes(n)
    ? draft.value.levels.filter((v) => v !== n)
    : [...draft.value.levels, n];
}
function clearFilters() {
  filters.value = { levels: [], highPriority: false };
}
const accountKey = computed(
  () => `${selected.value.kind}:${selected.value.id ?? 'all'}`,
);
const accountOptions = computed(() => [
  ...(showIm.value ? [{ value: 'im:all', label: '全部即时消息' }] : []),
  ...waAccounts.value.map((a) => ({
    value: `im:${a.id}`,
    label: `${PLATFORM[a.platform || 'whatsapp']} · ${a.name || imLabel(a)}`,
  })),
  ...(mailAccounts.value.length
    ? [{ value: 'mail:all', label: '全部邮件' }]
    : []),
  ...mailAccounts.value.map((a) => ({ value: `mail:${a.id}`, label: a.email })),
]);
function selectAccount(value: string) {
  const [kind, id] = value.split(':');
  pick(kind as 'im' | 'mail', id === 'all' ? undefined : Number(id));
}
const PLATFORM: Record<string, string> = {
  whatsapp: 'WhatsApp',
  messenger: 'Facebook',
  instagram: 'Instagram',
  web: '官网IM',
  youtube: 'YouTube',
  tiktok: 'TikTok',
};
const imLabel = (a: ChannelWaApi.Account) =>
  a.displayPhone || a.name || a.phoneNumberId;
/** 自己没有号码时，可能有同事转交过来的会话 */
const hasTransferred = ref(false);
const showIm = computed(
  () =>
    waAccounts.value.length > 0 ||
    hasTransferred.value ||
    // 自己没有号码，但同事公开给团队的会话也能看
    (counts.value?.teamOpen ?? 0) > 0,
);
const hasAny = computed(() => showIm.value || mailAccounts.value.length > 0);
const viewKey = computed(
  () => `${selected.value.kind}-${selected.value.id ?? 'all'}-${view.value}`,
);

function pick(kind: 'im' | 'mail', id?: number) {
  openId.value = undefined;
  selected.value = { kind, id };
  filtersOpen.value = false;
  router.replace({
    query: {
      ...route.query,
      channel: kind === 'mail' ? 'mail' : undefined,
      ref: undefined,
    },
  });
}

onMounted(async () => {
  try {
    [waAccounts.value, mailAccounts.value] = await Promise.all([
      getWaAccountList(),
      getMailAccountList(),
    ]);
    if (waAccounts.value.length === 0) {
      hasTransferred.value =
        (
          await getWaConversationPage({ pageNo: 1, pageSize: 1 }).catch(() => ({
            total: 0,
          }))
        ).total > 0;
    }
  } finally {
    loading.value = false;
  }
  // 从看板、通知跳转过来：?channel=mail|whatsapp&ref=编号
  const refId = Number(route.query.ref);
  if (
    route.query.channel === 'mail' ||
    (!showIm.value && mailAccounts.value.length)
  ) {
    selected.value = { kind: 'mail' };
  }
  if (refId) openId.value = refId;
  loadCounts();
  countTimer = setInterval(() => !document.hidden && loadCounts(), 30_000);
});
</script>

<template>
  <div class="v3-conv" @keydown.esc="filtersOpen = false">
    <Spin :spinning="loading" wrapper-class-name="h-full">
      <div v-if="!loading && !hasAny" class="v3-conv__empty">
        <Empty description="还没有接入任何渠道账号"
          ><Button type="primary" @click="router.push('/v3/channel')"
            >连接渠道账号</Button
          ></Empty
        >
      </div>
      <div v-else class="v3-conv__main">
        <ImWorkbench
          v-if="selected.kind === 'im'"
          :key="viewKey"
          embedded
          :account-id="selected.id"
          :levels="filters.levels"
          :group="filters.group"
          :high-priority="filters.highPriority"
          :open-id="openId"
          :view="view"
          @changed="loadCounts"
        >
          <template #list-controls>
            <div class="ownership" aria-label="会话归属">
              <button
                type="button"
                :class="{ on: view === 'mine' }"
                @click="pickView('mine')"
              >
                我的
                <small
                  v-if="counts?.open != null"
                  title="全部账号的进行中会话数"
                  >{{ counts.open }}</small
                ></button
              ><button
                v-if="counts?.teamOpen != null"
                type="button"
                :class="{ on: view === 'team' }"
                @click="pickView('team')"
              >
                团队
                <small title="全部账号的团队进行中会话数">{{
                  counts.teamOpen
                }}</small>
              </button>
            </div>
            <div class="account-line">
              <Select
                :value="accountKey"
                :options="accountOptions"
                class="account-select"
                aria-label="选择收件箱账号"
                @change="(v: any) => selectAccount(String(v))"
              /><button
                type="button"
                class="filter-trigger"
                :class="{ on: filterCount > 0 }"
                aria-label="筛选会话"
                :aria-expanded="filtersOpen"
                @click="openFilters"
              >
                <IconifyIcon icon="lucide:sliders-horizontal" /><span
                  v-if="filterCount"
                  >{{ filterCount }}</span
                >
              </button>
            </div>
            <div v-if="filterCount" class="filter-chips">
              <Tag
                v-for="n in filters.levels"
                :key="n"
                closable
                @close.prevent="
                  filters.levels = filters.levels.filter((v) => v !== n)
                "
                >{{ ['A', 'B', 'C', 'D'][n - 1] }} 类</Tag
              ><Tag
                v-if="filters.group !== undefined"
                closable
                @close.prevent="filters.group = undefined"
                >{{ filters.group ? '群聊' : '联系人' }}</Tag
              ><Tag
                v-if="filters.highPriority"
                closable
                @close.prevent="filters.highPriority = false"
                >高优先级</Tag
              ><button type="button" @click="clearFilters">清空</button>
            </div>
          </template>
        </ImWorkbench>
        <MailWorkbench
          v-else
          :key="viewKey"
          embedded
          :account-id="selected.id"
          :open-id="openId"
          ><template #list-controls
            ><Select
              :value="accountKey"
              :options="accountOptions"
              class="w-full"
              aria-label="选择收件箱账号"
              @change="(v: any) => selectAccount(String(v))" /></template
        ></MailWorkbench>
      </div>
    </Spin>
    <Drawer
      v-model:open="filtersOpen"
      title="筛选会话"
      placement="right"
      :width="290"
      :mask="false"
      :get-container="false"
      :root-style="{ position: 'absolute' }"
      root-class-name="conversation-filter-drawer"
      @close="filtersOpen = false"
    >
      <p class="filter-description">
        只筛选当前账号和归属下的会话。应用后，条件会显示在列表上方。
      </p>
      <div class="filter-field">
        <label>客户等级 · 可多选</label>
        <div class="grade-options">
          <button
            v-for="(g, i) in ['A', 'B', 'C', 'D']"
            :key="g"
            type="button"
            :class="{ on: draft.levels.includes(i + 1) }"
            :aria-pressed="draft.levels.includes(i + 1)"
            @click="toggleLevel(i + 1)"
          >
            {{ g }} 类
          </button>
        </div>
        <button type="button" class="filter-reset" @click="draft.levels = []">
          不限等级
        </button>
      </div>
      <div class="filter-field">
        <label for="conversation-kind">会话类型</label
        ><select
          id="conversation-kind"
          :value="
            draft.group === undefined ? 'all' : draft.group ? 'group' : 'person'
          "
          @change="
            (e: any) =>
              (draft.group =
                e.target.value === 'all'
                  ? undefined
                  : e.target.value === 'group')
          "
        >
          <option value="all">全部类型</option>
          <option value="person">联系人</option>
          <option value="group">群聊</option>
        </select>
      </div>
      <div class="filter-field">
        <label for="conversation-priority">优先级</label
        ><select
          id="conversation-priority"
          :value="draft.highPriority ? 'high' : 'all'"
          @change="(e: any) => (draft.highPriority = e.target.value === 'high')"
        >
          <option value="all">全部优先级</option>
          <option value="high">高及紧急</option>
        </select>
      </div>
      <template #footer
        ><div class="filter-footer">
          <Button
            @click="
              draft = { levels: [], group: undefined, highPriority: false }
            "
            >重置</Button
          ><Button type="primary" @click="applyFilters">应用筛选</Button>
        </div></template
      >
    </Drawer>
  </div>
</template>
<style scoped>
.v3-conv {
  position: relative;
  height: 100%;
  min-height: 0;
}
.v3-conv :deep(.ant-spin-container) {
  height: 100%;
}
.v3-conv__main {
  height: 100%;
  min-height: 0;
  background: white;
  border-radius: 22px;
  overflow: hidden;
}
.v3-conv__empty {
  display: grid;
  place-items: center;
  height: 100%;
  background: white;
  border-radius: 22px;
}
.ownership {
  display: flex;
  background: #78788013;
  padding: 3px;
  border-radius: 10px;
  gap: 3px;
}
.ownership button {
  flex: 1;
  border: 0;
  background: transparent;
  border-radius: 8px;
  font-size: 12px;
  padding: 7px;
  color: #777780;
  cursor: pointer;
}
.ownership button.on {
  background: white;
  box-shadow: 0 1px 4px #0001;
  color: #202025;
  font-weight: 600;
}
.ownership small {
  margin-left: 7px;
  font-size: 10px;
  font-weight: 400;
  color: #9999a2;
}
.account-line {
  display: flex;
  align-items: center;
  gap: 8px;
}
.account-select {
  flex: 1;
  min-width: 0;
}
.filter-trigger {
  position: relative;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid #e5e5ed;
  border-radius: 10px;
  background: white;
  color: #777780;
  cursor: pointer;
}
.filter-trigger :deep(svg) {
  width: 16px;
  height: 16px;
}
.filter-trigger.on {
  background: #e8f2ff;
  color: #007aff;
  border-color: #c5dfff;
}
.filter-trigger span {
  position: absolute;
  right: -4px;
  top: -4px;
  background: #007aff;
  border: 2px solid #f8f8fb;
  border-radius: 99px;
  min-width: 16px;
  height: 16px;
  line-height: 12px;
  font-size: 9px;
  color: white;
}
.filter-chips {
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: wrap;
}
.filter-chips :deep(.ant-tag) {
  margin: 0;
  background: #e6f1ff;
  border: 0;
  color: #007aff;
  font-size: 10px;
}
.filter-chips button,
.filter-reset {
  font-size: 10px;
  color: #8a8a93;
  background: none;
  border: 0;
  padding: 4px;
  cursor: pointer;
}
.filter-description {
  font-size: 12px;
  line-height: 1.8;
  color: #9696a1;
  border-bottom: 1px solid #eeeef4;
  padding-bottom: 22px;
}
.filter-field {
  margin: 24px 0;
}
.filter-field label {
  font-size: 12px;
  color: #64646f;
  display: block;
  margin-bottom: 11px;
}
.grade-options {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
}
.grade-options button {
  padding: 8px 0;
  border: 1px solid #e7e7ef;
  border-radius: 8px;
  font-size: 11px;
  background: white;
  color: #92929d;
  cursor: pointer;
}
.grade-options button.on {
  border-color: #007aff66;
  color: #007aff;
  background: #007aff0b;
}
.filter-field select {
  border: 1px solid #e7e7ef;
  background: #fafafc;
  border-radius: 9px;
  padding: 10px;
  width: 100%;
  font-size: 12px;
  color: #74747e;
}
.filter-footer {
  display: flex;
  gap: 8px;
}
.filter-footer > * {
  flex: 1;
}
.v3-conv :deep(.conversation-filter-drawer .ant-drawer-content-wrapper) {
  box-shadow: -12px 0 40px #22223b08;
}
.v3-conv :deep(.conversation-filter-drawer .ant-drawer-content) {
  border-left: 1px solid #ededf4;
}
.v3-conv button:focus-visible {
  outline: 2px solid #007aff;
  outline-offset: 2px;
}
@media (width <= 760px) {
  .ownership button {
    min-height: 39px;
  }
  .filter-trigger {
    width: 44px;
    height: 44px;
  }
  .account-select :deep(.ant-select-selector) {
    min-height: 44px;
    align-items: center;
  }
  .grade-options button,
  .filter-footer :deep(.ant-btn),
  .filter-field select {
    min-height: 44px;
  }
  .v3-conv :deep(.ant-drawer-content-wrapper) {
    max-width: 90vw !important;
  }
}
</style>
