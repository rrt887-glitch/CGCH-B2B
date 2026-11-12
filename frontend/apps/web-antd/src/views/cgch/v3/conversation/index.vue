<script lang="ts" setup>
/**
 * 会话：左栏选择账号（WhatsApp / Facebook / Instagram / 邮箱）与客户等级，中栏会话列表，右栏聊天。
 * 即时消息与邮件共用这个页面，选中邮箱时右侧切换为邮件收件箱。
 */
import type { ChannelMailApi } from '#/api/channel/mail';
import type { ChannelWaApi } from '#/api/channel/whatsapp';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { preferences } from '@vben/preferences';
import { useUserStore } from '@vben/stores';

import { Avatar, Badge, Empty, Spin } from 'ant-design-vue';

import { getMailAccountList } from '#/api/channel/mail';
import {
  getWaAccountList,
  getWaConversationPage,
  getWaInboxCounts,
} from '#/api/channel/whatsapp';
import MailWorkbench from '#/views/cgch/channel/mail/index.vue';
import ImWorkbench from '#/views/cgch/channel/im/index.vue';
import { CHANNEL_ICONS } from '#/assets/channel-icons';

defineOptions({ name: 'CgchV3Conversation' });

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

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
const unreadOf = (id?: number) =>
  id === undefined ? 0 : (counts.value?.unreadByAccount?.[id] ?? 0);
let countTimer: ReturnType<typeof setInterval> | undefined;
onBeforeUnmount(() => countTimer && clearInterval(countTimer));
function pickView(v: 'mine' | 'team') {
  openId.value = undefined;
  view.value = v;
  selected.value = { kind: 'im' };
}
const openId = ref<number>();

const GRADES = [
  { key: '', label: '全部等级' },
  { key: 'A', label: 'A类客户' },
  { key: 'B', label: 'B类客户' },
  { key: 'C', label: 'C类客户' },
  { key: 'D', label: 'D类客户' },
];
const grade = ref('');
/** 客户等级 A–D 对应 crm_customer_level 字典值 1–4 */
const LEVEL: Record<string, number> = { A: 1, B: 2, C: 3, D: 4 };

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
/** 账号前面的渠道图标（与原网站一致） */
const IM_ICON: Record<string, string> = {
  whatsapp: 'whatsapp',
  messenger: 'messenger',
  instagram: 'instagram',
  web: 'im',
  youtube: 'youtube',
  tiktok: 'tiktok',
};
const imIcon = (a: ChannelWaApi.Account) =>
  CHANNEL_ICONS[IM_ICON[a.platform || 'whatsapp'] ?? 'whatsapp'];
const mailIcon = (m: ChannelMailApi.Account) =>
  CHANNEL_ICONS[m.provider || 'custom'] ?? CHANNEL_ICONS.custom;
/** 自己没有号码时，可能有同事转交过来的会话 */
const hasTransferred = ref(false);
const showIm = computed(
  () => waAccounts.value.length > 0 || hasTransferred.value,
);
const hasAny = computed(() => showIm.value || mailAccounts.value.length > 0);
const viewKey = computed(
  () => `${selected.value.kind}-${selected.value.id ?? 'all'}-${view.value}`,
);

function pick(kind: 'im' | 'mail', id?: number) {
  openId.value = undefined;
  selected.value = { kind, id };
  filtersOpen.value = false;
}

const avatar = computed(
  () => userStore.userInfo?.avatar || preferences.app.defaultAvatar,
);

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
    <div class="inbox-toolbar">
      <button
        type="button"
        :aria-expanded="filtersOpen"
        @click="filtersOpen = !filtersOpen"
      >
        收件箱与筛选 {{ filtersOpen ? '⌃' : '⌄' }}</button
      ><span
        >{{
          selected.kind === 'mail'
            ? '邮件'
            : view === 'team'
              ? '团队会话'
              : '我的会话'
        }}
        · {{ grade ? `${grade} 类客户` : '全部等级' }}</span
      >
    </div>
    <Spin :spinning="loading" wrapper-class-name="h-full">
      <div v-if="!loading && !hasAny" class="v3-conv__empty">
        <Empty description="还没有接入任何渠道账号">
          <a @click="router.push('/v3/channel')">去渠道页连接账号 →</a>
        </Empty>
      </div>

      <div v-else class="v3-conv__body">
        <aside v-show="filtersOpen" class="v3-conv__side">
          <div class="flex items-center gap-2 px-3 py-3">
            <Avatar :src="avatar" :size="24" />
            <span class="truncate font-semibold">会话</span>
            <span class="ml-auto truncate text-xs text-[#86868B]">{{
              userStore.userInfo?.nickname
            }}</span>
          </div>

          <div v-if="showIm" class="v3-conv__group">
            <div
              class="v3-conv__item"
              :class="{
                on: selected.kind === 'im' && !selected.id && view === 'mine',
              }"
              @click="pickView('mine')"
            >
              <span class="flex-1">我的会话</span>
              <span class="v3-conv__count">{{ counts?.open ?? '' }}</span>
            </div>
            <!-- 团队会话只有能看到别人数据的成员（超级管理员）才有；业务员只看自己的 -->
            <div
              v-if="counts?.teamOpen != null"
              class="v3-conv__item"
              :class="{
                on: selected.kind === 'im' && !selected.id && view === 'team',
              }"
              @click="pickView('team')"
            >
              <span class="flex-1">团队会话</span>
              <span class="v3-conv__count">{{ counts?.teamOpen || '' }}</span>
            </div>
          </div>

          <div v-if="waAccounts.length" class="v3-conv__label">收件箱</div>
          <div class="v3-conv__group">
            <div
              v-for="a in waAccounts"
              :key="`im-${a.id}`"
              class="v3-conv__item v3-conv__item--sub"
              :class="{ on: selected.kind === 'im' && selected.id === a.id }"
              @click="
                view = 'mine';
                pick('im', a.id);
              "
            >
              <img
                :src="imIcon(a)"
                :alt="PLATFORM[a.platform || 'whatsapp']"
                class="v3-conv__icon"
              />
              <span
                class="flex-1 truncate"
                :title="`${PLATFORM[a.platform || 'whatsapp']} · ${imLabel(a)}`"
                >{{ a.name || imLabel(a) }}</span
              >
              <span v-if="unreadOf(a.id)" class="v3-conv__badge">{{
                unreadOf(a.id)
              }}</span>
            </div>
          </div>

          <div v-if="mailAccounts.length" class="v3-conv__label">邮箱</div>
          <div class="v3-conv__group">
            <div
              v-if="mailAccounts.length"
              class="v3-conv__item"
              :class="{ on: selected.kind === 'mail' && !selected.id }"
              @click="pick('mail')"
            >
              全部邮件
            </div>
            <div
              v-for="m in mailAccounts"
              :key="`mail-${m.id}`"
              class="v3-conv__item v3-conv__item--sub"
              :class="{ on: selected.kind === 'mail' && selected.id === m.id }"
              @click="pick('mail', m.id)"
            >
              <img :src="mailIcon(m)" alt="" class="v3-conv__icon" />
              <span class="flex-1 truncate" :title="m.email">{{
                m.email
              }}</span>
              <Badge
                v-if="m.unrepliedCount"
                :count="m.unrepliedCount"
                size="small"
              />
            </div>
          </div>

          <div class="v3-conv__label">客户等级</div>
          <div class="flex flex-wrap gap-1.5 px-3 pb-3">
            <button
              v-for="g in GRADES"
              :key="g.key"
              type="button"
              class="v3-conv__chip"
              :class="{ on: grade === g.key }"
              @click="grade = g.key"
            >
              {{ g.key ? `${g.key} 类` : '全部' }}
            </button>
          </div>
        </aside>

        <section class="v3-conv__main">
          <ImWorkbench
            v-if="selected.kind === 'im'"
            :key="viewKey"
            embedded
            :account-id="selected.id"
            :level="LEVEL[grade]"
            :open-id="openId"
            :view="view"
            @changed="loadCounts"
          />
          <MailWorkbench
            v-else
            :key="viewKey"
            embedded
            :account-id="selected.id"
            :open-id="openId"
          />
        </section>
      </div>
    </Spin>
  </div>
</template>

<style scoped>
.v3-conv {
  height: calc(100vh - 32px);
}

.v3-conv :deep(.ant-spin-container) {
  height: 100%;
}

.v3-conv__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: #fff;
  border-radius: 12px;
}

.v3-conv__body {
  display: flex;
  gap: 12px;
  height: 100%;
}

.v3-conv__side {
  flex: 0 0 212px;
  overflow: auto;
  background: #fff;
  border-radius: 18px;
  box-shadow: var(--cg-shadow);
}

.v3-conv__group {
  padding: 0 8px;
}

.v3-conv__item {
  transition:
    background-color 0.25s var(--cg-ease),
    color 0.25s var(--cg-ease);
  display: flex;
  gap: 6px;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  font-size: 13px;
  color: #424245;
  cursor: pointer;
  border-radius: 8px;
}

.v3-conv__icon {
  flex: none;
  width: 16px;
  height: 16px;
  object-fit: contain;
  border-radius: 4px;
}

.v3-conv__item--sub {
  padding-left: 14px;
  font-size: 13px;
  color: #6e6e73;
}

.v3-conv__item:hover {
  color: #1d1d1f;
  background: #f2f2f7;
}

.v3-conv__item.on {
  font-weight: 600;
  color: #fff;
  background: #0071e3;
}

.v3-conv__label {
  padding: 14px 18px 6px;
  font-size: 12px;
  color: #86868b;
}

.v3-conv__count {
  font-size: 12px;
  color: #86868b;
}

.v3-conv__badge {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
  color: #fff;
  text-align: center;
  background: #0071e3;
  border-radius: 9px;
}

.v3-conv__chip {
  padding: 2px 9px;
  font-size: 12px;
  color: #424245;
  cursor: pointer;
  background: #fff;
  border: 1px solid #e5e5ea;
  border-radius: 4px;
}

.v3-conv__chip.on {
  color: #0066cc;
  background: #e8f2fc;
  border-color: #b8d7f5;
}

.v3-conv__item.on .v3-conv__count {
  color: rgb(255 255 255 / 85%);
}

.v3-conv__item.on .v3-conv__badge {
  color: #0071e3;
  background: #fff;
}

.v3-conv__main {
  flex: 1;
  min-width: 0;
  height: 100%;
}

/* 窄屏：左侧导航改成顶部一行可横向滑动的标签，下面是会话区（会话区内部再按列表 / 聊天切换） */
@media (max-width: 1023px) {
  .v3-conv__body {
    flex-direction: column;
    gap: 8px;
  }

  .v3-conv__side {
    display: flex;
    flex: none;
    gap: 4px;
    align-items: center;
    padding: 6px;
    overflow: auto hidden;
    border-radius: 14px;
  }

  .v3-conv__side > div:first-child,
  .v3-conv__label {
    display: none;
  }

  .v3-conv__group {
    display: flex;
    flex: none;
    gap: 4px;
    padding: 0;
  }

  .v3-conv__side > .flex-wrap {
    flex-wrap: nowrap;
    padding: 0 0 0 6px;
  }

  .v3-conv__item {
    flex: none;
    white-space: nowrap;
  }

  .v3-conv__item--sub span.flex-1 {
    max-width: 140px;
  }

  .v3-conv__chip {
    flex: none;
    white-space: nowrap;
  }

  .v3-conv__main {
    flex: 1;
    min-height: 0;
  }
}
</style>

<style scoped>
.v3-conv {
  position: relative;
  height: 100%;
  padding-top: 42px;
}
.inbox-toolbar {
  position: absolute;
  inset: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 18px;
  height: 34px;
  padding: 0 6px;
}
.inbox-toolbar button {
  border: 0;
  background: #ffffff9e;
  border-radius: 99px;
  padding: 5px 14px;
  color: #007aff;
  cursor: pointer;
}
.inbox-toolbar span {
  font-size: 12px;
  color: #86868b;
}
.v3-conv__side {
  position: absolute;
  top: 42px;
  left: 0;
  width: 240px;
  max-height: calc(100% - 42px);
  z-index: 20;
  border: 1px solid #e5e5ea;
  box-shadow: 0 15px 45px #0002;
}
.v3-conv__body {
  gap: 0;
}
.v3-conv__main {
  height: 100%;
  border-radius: 22px;
  overflow: hidden;
  background: white;
}
</style>
