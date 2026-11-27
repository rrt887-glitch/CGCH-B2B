<script lang="ts" setup>
/**
 * 会话：左栏选择账号（WhatsApp / Facebook / Instagram / LinkedIn / Telegram），中栏会话列表，右栏聊天。
 * 邮件是独立板块（左侧导航「邮件」进入，?channel=mail）：会话页只显示即时消息，邮箱只在邮件板块出现。
 */
import type { ChannelMailApi } from '#/api/channel/mail';
import type { ChannelWaApi } from '#/api/channel/whatsapp';

import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Button, Drawer, Empty, Spin, Tag } from 'ant-design-vue';
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
const loadFailed = ref(false);
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
const PLATFORM_COLOR: Record<string, string> = {
  whatsapp: '#25D366',
  messenger: '#1877F2',
  instagram: '#E4405F',
  linkedin: '#0A66C2',
  telegram: '#26A5E4',
  web: '#007AFF',
  youtube: '#FF0000',
  tiktok: '#1D1D1F',
  mail: '#5856D6',
};

const channelPills = computed(() => {
  const countsByPlat = new Map<string, number>();
  for (const a of waAccounts.value) {
    const p = a.platform || 'whatsapp';
    countsByPlat.set(p, (countsByPlat.get(p) ?? 0) + 1);
  }
  const list: { value: string; label: string; title: string; color: string }[] = [];
  if (showIm.value) {
    list.push({ value: 'im:all', label: '全部', title: '全部即时消息', color: '#007AFF' });
  }
  for (const a of waAccounts.value) {
    const p = a.platform || 'whatsapp';
    const pName = PLATFORM[p] || p;
    const accName = a.name || imLabel(a);
    const dup = (countsByPlat.get(p) ?? 0) > 1;
    list.push({
      value: `im:${a.id}`,
      label: dup ? `${pName}·${accName}` : pName,
      title: `${pName} · ${accName}`,
      color: PLATFORM_COLOR[p] || '#007AFF',
    });
  }
  return list;
});

const mailAccountPills = computed(() => [
  { value: 'mail:all', label: '全部邮箱', title: '全部邮箱', color: PLATFORM_COLOR.mail! },
  ...mailAccounts.value.map((a) => ({
    value: `mail:${a.id}`,
    label: a.email.split('@')[0] || a.email,
    title: a.email,
    color: PLATFORM_COLOR.mail!,
  })),
]);
function selectAccount(value: string) {
  const [kind, id] = value.split(':');
  pick(kind as 'im' | 'mail', id === 'all' ? undefined : Number(id));
}
const PLATFORM: Record<string, string> = {
  whatsapp: 'WhatsApp',
  messenger: 'Facebook',
  instagram: 'Instagram',
  linkedin: 'LinkedIn',
  telegram: 'Telegram',
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

async function loadInbox() {
  loading.value = true;
  loadFailed.value = false;
  try {
    [waAccounts.value, mailAccounts.value] = await Promise.all([
      getWaAccountList(),
      getMailAccountList(),
    ]);
    if (waAccounts.value.length > 0) {
      hasTransferred.value = false;
      void loadCounts();
    } else {
      const [conversations] = await Promise.all([
        getWaConversationPage({ pageNo: 1, pageSize: 1 }),
        loadCounts(),
      ]);
      hasTransferred.value = conversations.total > 0;
    }
    // 从看板、通知跳转过来：?channel=mail|whatsapp&ref=编号
    if (route.query.channel === 'mail' || (!showIm.value && mailAccounts.value.length)) {
      selected.value = { kind: 'mail' };
    }
    const refId = Number(route.query.ref);
    if (refId) openId.value = refId;
  } catch {
    loadFailed.value = true;
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void loadInbox();
  countTimer = setInterval(() => !document.hidden && loadCounts(), 30_000);
});
</script>

<template>
  <div class="v3-conv" @keydown.esc="filtersOpen = false">
    <Spin :spinning="loading" wrapper-class-name="h-full">
      <div v-if="loading" class="v3-conv__empty"></div>
      <div v-else-if="loadFailed" class="v3-conv__empty">
        <Empty description="会话加载失败">
          <Button type="primary" @click="loadInbox">重试</Button>
        </Empty>
      </div>
      <div v-else-if="!hasAny" class="v3-conv__empty">
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
            <div class="conv-subhead">
              <div class="ownership" aria-label="会话归属">
                <button
                  type="button"
                  :class="{ on: view === 'mine' }"
                  @click="pickView('mine')"
                >
                  <span>我的</span>
                  <small
                    v-if="counts?.open != null"
                    title="全部账号的进行中会话数"
                    >{{ counts.open }}</small
                  >
                </button>
                <button
                  v-if="counts?.teamOpen != null"
                  type="button"
                  :class="{ on: view === 'team' }"
                  @click="pickView('team')"
                >
                  <span>团队</span>
                  <small title="全部账号的团队进行中会话数">{{
                    counts.teamOpen
                  }}</small>
                </button>
              </div>
              <button
                type="button"
                class="filter-trigger"
                :class="{ on: filterCount > 0 }"
                aria-label="筛选会话"
                :aria-expanded="filtersOpen"
                title="按客户等级、群聊、优先级筛选"
                @click="openFilters"
              >
                <IconifyIcon icon="lucide:sliders-horizontal" />
                <span v-if="filterCount">{{ filterCount }}</span>
              </button>
            </div>
            <div class="channel-pills" role="group" aria-label="选择渠道">
              <button
                v-for="c in channelPills"
                :key="c.value"
                type="button"
                class="channel-pill"
                :class="{ on: accountKey === c.value }"
                :title="c.title"
                @click="selectAccount(c.value)"
              >
                <span
                  class="channel-pill__dot"
                  :style="{ background: c.color }"
                ></span>
                <span>{{ c.label }}</span>
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
          ><template #list-controls>
            <div class="channel-pills" role="group" aria-label="选择邮箱">
              <button
                v-for="c in mailAccountPills"
                :key="c.value"
                type="button"
                class="channel-pill"
                :class="{ on: accountKey === c.value }"
                :title="c.title"
                @click="selectAccount(c.value)"
              >
                <span
                  class="channel-pill__dot"
                  :style="{ background: c.color }"
                ></span>
                <span>{{ c.label }}</span>
              </button>
            </div>
          </template></MailWorkbench>
      </div>
    </Spin>
    <Drawer
      v-model:open="filtersOpen"
      title="筛选会话"
      placement="right"
      :width="300"
      :mask="false"
      :get-container="false"
      :root-style="{ position: 'absolute' }"
      root-class-name="conversation-filter-drawer"
      @close="filtersOpen = false"
    >
      <p class="filter-description">
        只筛选当前账号和归属下的会话。应用后，筛选标签会展示在会话列表顶部。
      </p>
      <div class="filter-field">
        <div class="filter-field__head">
          <label>客户等级（可多选）</label>
          <button
            v-if="draft.levels.length"
            type="button"
            class="filter-reset"
            @click="draft.levels = []"
          >
            不限
          </button>
        </div>
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
      </div>
      <div class="filter-field">
        <label>会话类型</label>
        <div class="seg-options">
          <button
            type="button"
            :class="{ on: draft.group === undefined }"
            @click="draft.group = undefined"
          >
            全部
          </button>
          <button
            type="button"
            :class="{ on: draft.group === false }"
            @click="draft.group = false"
          >
            单聊联系人
          </button>
          <button
            type="button"
            :class="{ on: draft.group === true }"
            @click="draft.group = true"
          >
            群聊
          </button>
        </div>
      </div>
      <div class="filter-field">
        <label>优先级</label>
        <div class="seg-options seg-options--2">
          <button
            type="button"
            :class="{ on: !draft.highPriority }"
            @click="draft.highPriority = false"
          >
            全部优先级
          </button>
          <button
            type="button"
            :class="{ on: draft.highPriority }"
            @click="draft.highPriority = true"
          >
            高及紧急
          </button>
        </div>
      </div>
      <template #footer
        ><div class="filter-footer">
          <Button
            shape="round"
            @click="
              draft = { levels: [], group: undefined, highPriority: false }
            "
            >重置</Button
          ><Button type="primary" shape="round" @click="applyFilters"
            >应用筛选</Button
          >
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
  padding: 10px 12px 10px 4px;
}
.v3-conv :deep(.ant-spin-nested-loading),
.v3-conv :deep(.ant-spin-container) {
  height: 100%;
}
.v3-conv__main {
  height: 100%;
  min-height: 0;
  background: var(--ap-surface);
  border-radius: 20px;
  overflow: hidden;
  box-shadow:
    0 0 0 0.5px rgb(0 0 0 / 5%),
    0 8px 28px rgb(15 23 42 / 5%);
}
.v3-conv__empty {
  display: grid;
  place-items: center;
  height: 100%;
  background: var(--ap-surface);
  border-radius: 20px;
  box-shadow: var(--ap-shadow-rest);
}
.conv-subhead {
  display: flex;
  align-items: center;
  gap: 6px;
}
.ownership {
  display: flex;
  flex: 1;
  background: rgb(118 118 128 / 10%);
  padding: 2.5px;
  border-radius: 9px;
  gap: 2px;
}
.ownership button {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 27px;
  border: 0;
  background: transparent;
  border-radius: 7px;
  font-size: 12px;
  font-weight: 500;
  padding: 0 8px;
  color: var(--ap-label-2);
  cursor: pointer;
  transition:
    background-color var(--ap-dur-fast),
    color var(--ap-dur-fast),
    box-shadow var(--ap-dur-fast);
}
.ownership button:hover:not(.on) {
  color: var(--ap-label);
}
.ownership button.on {
  background: #fff;
  box-shadow:
    0 1px 3px rgb(0 0 0 / 8%),
    0 0 0 0.5px rgb(0 0 0 / 4%);
  color: var(--ap-label);
  font-weight: 600;
}
.ownership small {
  font-size: 11px;
  font-weight: 600;
  color: var(--ap-label-3);
  font-variant-numeric: tabular-nums;
}
.ownership button.on small {
  color: var(--ap-accent);
}
.channel-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  min-width: 0;
}
.channel-pill {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 26px;
  padding: 0 9px;
  font-size: 11.5px;
  font-weight: 500;
  color: var(--ap-label-2);
  white-space: nowrap;
  cursor: pointer;
  background: var(--ap-surface);
  border: 0;
  border-radius: 999px;
  box-shadow: inset 0 0 0 0.5px rgb(0 0 0 / 10%);
  transition:
    background-color var(--ap-dur-fast) linear,
    color var(--ap-dur-fast) linear,
    box-shadow var(--ap-dur-fast) linear,
    transform var(--ap-dur-fast) var(--ap-ease);
}
.channel-pill:hover:not(.on) {
  color: var(--ap-label);
  background: rgb(255 255 255 / 90%);
  box-shadow: inset 0 0 0 0.5px rgb(0 0 0 / 18%);
}
.channel-pill:active {
  transform: scale(0.96);
}
.channel-pill.on {
  font-weight: 600;
  color: #fff;
  background: #1d1d1f;
  box-shadow: 0 1px 4px rgb(0 0 0 / 14%);
}
.channel-pill--back {
  color: var(--ap-accent);
  background: rgb(0 122 255 / 8%);
  box-shadow: none;
}
.channel-pill--back:hover {
  color: #0066d6 !important;
  background: rgb(0 122 255 / 14%) !important;
  box-shadow: none !important;
}
.channel-pill__dot {
  flex: none;
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
.channel-pill.on .channel-pill__dot {
  box-shadow: 0 0 0 1.5px rgb(255 255 255 / 45%);
}
.filter-trigger {
  position: relative;
  display: grid;
  flex: none;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 9px;
  background: rgb(118 118 128 / 10%);
  color: var(--ap-label-2);
  cursor: pointer;
  transition:
    background-color var(--ap-dur-fast),
    color var(--ap-dur-fast),
    transform var(--ap-dur-fast) var(--ap-ease);
}
.filter-trigger:hover {
  background: rgb(118 118 128 / 16%);
  color: var(--ap-label);
}
.filter-trigger:active {
  transform: scale(0.94);
}
.filter-trigger :deep(svg) {
  width: 15px;
  height: 15px;
}
.filter-trigger.on {
  background: rgb(0 122 255 / 12%);
  color: var(--ap-accent);
}
.filter-trigger span {
  position: absolute;
  right: -4px;
  top: -4px;
  background: #007aff;
  border: 2px solid #f6f6f9;
  border-radius: 99px;
  min-width: 16px;
  height: 16px;
  line-height: 12px;
  font-size: 9px;
  font-weight: 700;
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
  background: rgb(0 122 255 / 10%);
  border: 0;
  border-radius: 999px;
  padding-inline: 8px;
  color: #007aff;
  font-size: 11px;
  font-weight: 500;
}
.filter-chips button,
.filter-reset {
  font-size: 11px;
  color: var(--ap-accent);
  background: none;
  border: 0;
  padding: 2px 4px;
  cursor: pointer;
}
.filter-description {
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--ap-label-3);
  border-bottom: 0.5px solid var(--ap-separator);
  padding-bottom: 16px;
  margin-bottom: 4px;
}
.filter-field {
  margin: 20px 0;
}
.filter-field__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.filter-field__head label {
  margin-bottom: 0 !important;
}
.filter-field label {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--ap-label);
  display: block;
  margin-bottom: 10px;
}
.grade-options {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
}
.grade-options button,
.seg-options button {
  height: 34px;
  border: 0.5px solid rgb(0 0 0 / 10%);
  border-radius: 9px;
  font-size: 12px;
  font-weight: 500;
  background: #f5f5f7;
  color: var(--ap-label-2);
  cursor: pointer;
  transition: all var(--ap-dur-fast) var(--ap-ease);
}
.grade-options button:hover:not(.on),
.seg-options button:hover:not(.on) {
  color: var(--ap-label);
  background: #ededf2;
}
.grade-options button.on,
.seg-options button.on {
  border-color: rgb(0 122 255 / 35%);
  color: #007aff;
  font-weight: 600;
  background: rgb(0 122 255 / 10%);
}
.seg-options {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
}
.seg-options--2 {
  grid-template-columns: repeat(2, 1fr);
}
.filter-footer {
  display: flex;
  gap: 8px;
}
.filter-footer > * {
  flex: 1;
}
.v3-conv :deep(.conversation-filter-drawer .ant-drawer-content-wrapper) {
  box-shadow: -12px 0 36px rgb(15 23 42 / 8%);
}
.v3-conv :deep(.conversation-filter-drawer .ant-drawer-content) {
  background: rgb(255 255 255 / 94%);
  backdrop-filter: blur(24px);
  border-left: 0.5px solid var(--ap-separator);
}
.v3-conv button:focus-visible {
  outline: 2px solid #007aff;
  outline-offset: 2px;
}
@media (width <= 760px) {
  .v3-conv {
    padding: 0;
  }
  .v3-conv__main {
    border-radius: 0;
  }
  .ownership button {
    min-height: 36px;
  }
  .filter-trigger {
    width: 38px;
    height: 38px;
  }
  .grade-options button,
  .seg-options button,
  .filter-footer :deep(.ant-btn) {
    min-height: 40px;
  }
  .v3-conv :deep(.ant-drawer-content-wrapper) {
    max-width: 90vw !important;
  }
}
</style>
