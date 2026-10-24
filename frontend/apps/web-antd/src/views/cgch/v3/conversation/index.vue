<script lang="ts" setup>
/**
 * 会话：左栏选择账号（WhatsApp / Facebook / Instagram / 邮箱）与客户等级，中栏会话列表，右栏聊天。
 * 即时消息与邮件共用这个页面，选中邮箱时右侧切换为邮件收件箱。
 */
import type { ChannelMailApi } from '#/api/channel/mail';
import type { ChannelWaApi } from '#/api/channel/whatsapp';

import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { preferences } from '@vben/preferences';
import { useUserStore } from '@vben/stores';

import { Avatar, Badge, Empty, Spin } from 'ant-design-vue';

import { getMailAccountList } from '#/api/channel/mail';
import { getWaAccountList } from '#/api/channel/whatsapp';
import MailWorkbench from '#/views/cgch/channel/mail/index.vue';
import ImWorkbench from '#/views/cgch/channel/im/index.vue';

defineOptions({ name: 'CgchV3Conversation' });

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const loading = ref(true);
const waAccounts = ref<ChannelWaApi.Account[]>([]);
const mailAccounts = ref<ChannelMailApi.Account[]>([]);

/** 当前选择：im:全部 / im:账号编号 / mail:全部 / mail:邮箱编号 */
const selected = ref<{ id?: number; kind: 'im' | 'mail' }>({ kind: 'im' });
const openId = ref<number>();

const GRADES = [
  { key: '', label: '全部等级' },
  { key: 'A', label: 'A类客户' },
  { key: 'B', label: 'B类客户' },
  { key: 'C', label: 'C类客户' },
  { key: 'D', label: 'D类客户' },
];
const grade = ref('');

const PLATFORM: Record<string, string> = { whatsapp: 'WhatsApp', messenger: 'Facebook', instagram: 'Instagram', web: '官网IM' };
const imLabel = (a: ChannelWaApi.Account) => `${PLATFORM[a.platform || 'whatsapp']} · ${a.displayPhone || a.name || a.phoneNumberId}`;
const hasAny = computed(() => waAccounts.value.length + mailAccounts.value.length > 0);
const viewKey = computed(() => `${selected.value.kind}-${selected.value.id ?? 'all'}`);

function pick(kind: 'im' | 'mail', id?: number) {
  openId.value = undefined;
  selected.value = { kind, id };
}

const avatar = computed(() => userStore.userInfo?.avatar || preferences.app.defaultAvatar);

onMounted(async () => {
  try {
    [waAccounts.value, mailAccounts.value] = await Promise.all([getWaAccountList(), getMailAccountList()]);
  } finally {
    loading.value = false;
  }
  // 从看板、通知跳转过来：?channel=mail|whatsapp&ref=编号
  const refId = Number(route.query.ref);
  if (route.query.channel === 'mail' || (!waAccounts.value.length && mailAccounts.value.length)) {
    selected.value = { kind: 'mail' };
  }
  if (refId) openId.value = refId;
});
</script>

<template>
  <div class="v3-conv">
    <Spin :spinning="loading" wrapper-class-name="h-full">
      <div v-if="!loading && !hasAny" class="v3-conv__empty">
        <Empty description="还没有接入任何渠道账号">
          <a @click="router.push('/v3/channel')">去渠道页连接账号 →</a>
        </Empty>
      </div>

      <div v-else class="v3-conv__body">
        <aside class="v3-conv__side">
          <div class="flex items-center gap-2 px-3 py-3">
            <Avatar :src="avatar" :size="24" />
            <span class="truncate font-medium">{{ userStore.userInfo?.nickname }}</span>
          </div>

          <div class="v3-conv__group">
            <div v-if="waAccounts.length" class="v3-conv__item" :class="{ on: selected.kind === 'im' && !selected.id }" @click="pick('im')">
              全部消息
            </div>
            <div
              v-for="a in waAccounts"
              :key="`im-${a.id}`"
              class="v3-conv__item v3-conv__item--sub"
              :class="{ on: selected.kind === 'im' && selected.id === a.id }"
              @click="pick('im', a.id)"
            >
              <span class="truncate">{{ imLabel(a) }}</span>
            </div>
            <div v-if="mailAccounts.length" class="v3-conv__item" :class="{ on: selected.kind === 'mail' && !selected.id }" @click="pick('mail')">
              全部邮件
            </div>
            <div
              v-for="m in mailAccounts"
              :key="`mail-${m.id}`"
              class="v3-conv__item v3-conv__item--sub"
              :class="{ on: selected.kind === 'mail' && selected.id === m.id }"
              @click="pick('mail', m.id)"
            >
              <span class="truncate">{{ m.email }}</span>
              <Badge v-if="m.unrepliedCount" :count="m.unrepliedCount" size="small" />
            </div>
          </div>

          <div class="mx-3 my-2 border-t border-gray-200"></div>

          <div class="v3-conv__group">
            <div
              v-for="g in GRADES"
              :key="g.key"
              class="v3-conv__item"
              :class="{ on: grade === g.key }"
              @click="grade = g.key"
            >
              {{ g.label }}
            </div>
          </div>
        </aside>

        <section class="v3-conv__main">
          <ImWorkbench v-if="selected.kind === 'im'" :key="viewKey" embedded :account-id="selected.id" :open-id="openId" />
          <MailWorkbench v-else :key="viewKey" embedded :account-id="selected.id" :open-id="openId" />
        </section>
      </div>
    </Spin>
  </div>
</template>

<style scoped>
.v3-conv {
  height: calc(100vh - 16px);
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
  gap: 8px;
  height: 100%;
}

.v3-conv__side {
  flex: 0 0 148px;
  overflow: auto;
  background: #fff;
  border-radius: 12px;
}

.v3-conv__group {
  padding: 0 8px;
}

.v3-conv__item {
  display: flex;
  gap: 6px;
  align-items: center;
  justify-content: space-between;
  padding: 7px 8px;
  font-size: 14px;
  color: #1f2937;
  cursor: pointer;
  border-radius: 6px;
}

.v3-conv__item--sub {
  padding-left: 14px;
  font-size: 13px;
  color: #4b5563;
}

.v3-conv__item:hover {
  background: #f3f6fd;
}

.v3-conv__item.on {
  background: #eaf0fe;
}

.v3-conv__main {
  flex: 1;
  min-width: 0;
  height: 100%;
}
</style>
