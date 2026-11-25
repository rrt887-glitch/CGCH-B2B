<script lang="ts" setup>
/**
 * 草根出海主框架 · 苹果风格：
 * 左侧毛玻璃侧栏（支持折叠、选中块弹簧滑动、⌘K 全局快捷跳转、底部设置与头像卡片），右侧页面内容带平滑过渡。
 */
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { AuthenticationLoginExpiredModal } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';
import { preferences } from '@vben/preferences';
import { useAccessStore, useUserStore } from '@vben/stores';

import { Avatar, Dropdown, Menu, MenuItem, Tooltip } from 'ant-design-vue';

import { exitTenant } from '#/api/system/tenant-visit';
import { ApCommandPalette } from '#/components/apple';
import { router } from '#/router';
import { useAuthStore } from '#/store';
import { useSlidingIndicator } from '#/utils/motion';
import { endVisit, getVisitInfo } from '#/utils/tenant-visit';
import LoginForm from '#/views/_core/authentication/login.vue';

const userStore = useUserStore();
const authStore = useAuthStore();
const accessStore = useAccessStore();
const route = useRoute();

/** 全部业务页面与个人中心统一使用苹果工作台外观 */
const appleWorkspace = true;

const collapsed = ref(localStorage.getItem('cgch:rail-collapsed') === '1');
function toggleCollapsed() {
  collapsed.value = !collapsed.value;
  localStorage.setItem('cgch:rail-collapsed', collapsed.value ? '1' : '0');
}

const cmdOpen = ref(false);

interface NavItem {
  key: string;
  label: string;
  icon: string;
  path: string;
  query?: Record<string, string>;
  group?: string;
}

const NAV: NavItem[] = [
  {
    key: 'dashboard',
    label: '今天',
    icon: 'lucide:activity',
    path: '/v3/dashboard',
    group: '工作',
  },
  {
    key: 'conversation',
    label: '会话',
    icon: 'lucide:message-circle-more',
    path: '/v3/conversation',
  },
  {
    key: 'mail',
    label: '邮件',
    icon: 'lucide:mail',
    path: '/v3/conversation',
    query: { channel: 'mail' },
  },
  {
    key: 'crm',
    label: '客户',
    icon: 'lucide:users',
    path: '/v3/crm',
    query: { tab: 'customer' },
    group: '客户',
  },
  {
    key: 'agent',
    label: 'AI 助手',
    icon: 'lucide:sparkles',
    path: '/v3/agent',
    query: { tab: 'persona' },
    group: '智能',
  },
  {
    key: 'channel',
    label: '渠道',
    icon: 'lucide:link',
    path: '/v3/channel',
    group: '管理',
  },
];

const SETTINGS: NavItem = {
  key: 'settings',
  label: '设置',
  icon: 'lucide:settings',
  path: '/v3/settings',
};

/** 平台运营专用：客户管理（开户、账号数、到期、进入客户后台） */
const PLATFORM: NavItem = {
  key: 'platform',
  label: '客户管理',
  icon: 'lucide:building-2',
  path: '/v3/platform/tenants',
  group: '平台',
};

const isPlatform = computed(() =>
  (accessStore.accessCodes ?? []).includes('system:tenant:query'),
);
const navItems = computed(() => (isPlatform.value ? [...NAV, PLATFORM] : NAV));

/** 正在「进入客户后台」时，顶部提示看的是哪家客户，并可随时退出 */
const visit = computed(() =>
  accessStore.visitTenantId ? getVisitInfo() : null,
);
async function leaveVisit() {
  const v = visit.value;
  if (v) {
    await exitTenant(v.tenantId).catch(() => undefined);
  }
  endVisit();
  await router.replace('/v3/platform/tenants');
  window.location.reload();
}

/** 当前所在模块 */
const activeKey = computed(() => {
  const p = route.path;
  if (p === '/v3/conversation')
    return route.query.channel === 'mail' ? 'mail' : 'conversation';
  if (p === '/v3/crm' || p.startsWith('/crm')) return 'crm';
  if (p === '/v3/agent') return 'agent';
  if (p.startsWith('/profile')) return 'profile';
  const hit = [...NAV, SETTINGS, PLATFORM].find(
    (n) => p === n.path || p.startsWith(`${n.path}/`),
  );
  if (hit) return hit.key;
  if (p.startsWith('/system')) return 'settings';
  return '';
});

const navRef = ref<HTMLElement>();
const { style: indicatorStyle, measure } = useSlidingIndicator(
  navRef,
  activeKey,
);
watch(collapsed, () => setTimeout(measure, 290));

function go(item: NavItem) {
  router.push({ path: item.path, query: item.query });
}

const avatar = computed(
  () => userStore.userInfo?.avatar || preferences.app.defaultAvatar,
);
const nickname = computed(
  () => userStore.userInfo?.nickname || userStore.userInfo?.username || '',
);

function onUserMenu({ key }: { key: number | string }) {
  if (key === 'profile') router.push({ name: 'Profile' });
  if (key === 'logout') authStore.logout(false);
}
</script>

<template>
  <div
    class="cgch-shell"
    :class="{ 'is-apple': appleWorkspace, 'is-collapsed': collapsed }"
  >
    <aside class="cgch-rail">
      <div class="cgch-rail__top">
        <div
          class="cgch-rail__logo"
          title="草根出海"
          @click="router.push('/v3/dashboard')"
        >
          <img src="/brand/logo.png" alt="草根出海" />
          <b class="cgch-rail__brand">草根出海</b>
        </div>
        <Tooltip :title="collapsed ? '展开侧栏' : '收起侧栏'" placement="right">
          <button
            type="button"
            class="cgch-rail__collapse"
            :aria-label="collapsed ? '展开侧栏' : '收起侧栏'"
            @click="toggleCollapsed"
          >
            <IconifyIcon
              :icon="
                collapsed ? 'lucide:panel-left-open' : 'lucide:panel-left-close'
              "
              class="size-4"
            />
          </button>
        </Tooltip>
      </div>

      <Tooltip :title="collapsed ? '快捷跳转 (⌘K)' : ''" placement="right">
        <button
          type="button"
          class="cgch-rail__search"
          aria-label="快捷跳转"
          @click="cmdOpen = true"
        >
          <IconifyIcon icon="lucide:search" class="size-4 shrink-0" />
          <span class="cgch-rail__search-text">搜索</span>
          <kbd class="cgch-rail__kbd">⌘K</kbd>
        </button>
      </Tooltip>

      <nav ref="navRef" class="cgch-rail__nav">
        <div
          class="cgch-rail__indicator"
          :style="indicatorStyle"
          aria-hidden="true"
        ></div>
        <template v-for="item in navItems" :key="item.key">
          <div v-if="item.group" class="rail-group">{{ item.group }}</div>
          <Tooltip :title="collapsed ? item.label : ''" placement="right">
            <button
              type="button"
              class="cgch-rail__item"
              :class="{ 'is-active': activeKey === item.key }"
              :aria-label="item.label"
              :data-prefetch-route="item.path"
              @click="go(item)"
            >
              <IconifyIcon :icon="item.icon" class="cgch-rail__icon" />
              <span class="cgch-rail__label">{{ item.label }}</span>
            </button>
          </Tooltip>
        </template>
      </nav>

      <div class="cgch-rail__bottom">
        <Tooltip :title="collapsed ? SETTINGS.label : ''" placement="right">
          <button
            type="button"
            class="cgch-rail__item"
            :class="{ 'is-active': activeKey === 'settings' }"
            :aria-label="SETTINGS.label"
            :data-prefetch-route="SETTINGS.path"
            @click="go(SETTINGS)"
          >
            <IconifyIcon :icon="SETTINGS.icon" class="cgch-rail__icon" />
            <span class="cgch-rail__label">{{ SETTINGS.label }}</span>
          </button>
        </Tooltip>

        <Dropdown placement="topLeft" :trigger="['click']">
          <Tooltip :title="collapsed ? nickname : ''" placement="right">
            <button
              type="button"
              class="cgch-rail__avatar"
              :class="{ 'is-active': activeKey === 'profile' }"
            >
              <Avatar :src="avatar" :size="32">{{
                nickname.slice(0, 1)
              }}</Avatar>
              <div class="rail-user">
                <span class="rail-name">{{ nickname }}</span>
                <span v-if="userStore.userInfo?.email" class="rail-email">
                  {{ userStore.userInfo?.email }}
                </span>
              </div>
              <IconifyIcon
                icon="lucide:chevrons-up-down"
                class="rail-chevron"
              />
            </button>
          </Tooltip>
          <template #overlay>
            <Menu @click="onUserMenu">
              <div class="px-3 py-2 text-xs text-gray-500">
                <div class="text-sm font-medium text-gray-800">
                  {{ nickname }}
                </div>
                <div v-if="userStore.userInfo?.email">
                  {{ userStore.userInfo?.email }}
                </div>
              </div>
              <MenuItem key="profile">个人中心</MenuItem>
              <MenuItem key="logout">退出登录</MenuItem>
            </Menu>
          </template>
        </Dropdown>
      </div>
    </aside>

    <main class="cgch-main">
      <div v-if="visit" class="cgch-visit-bar" role="status">
        <span>
          正在以平台身份查看「<b>{{ visit.tenantName }}</b>」的后台，这次进入已记录，客户能看到。
        </span>
        <button type="button" class="cgch-visit-bar__exit" @click="leaveVisit">
          退出客户后台
        </button>
      </div>
      <RouterView v-slot="{ Component, route: r }">
        <Transition name="cg-page">
          <component :is="Component" :key="r.path" />
        </Transition>
      </RouterView>
    </main>

    <ApCommandPalette v-model:open="cmdOpen" />

    <AuthenticationLoginExpiredModal
      v-model:open="accessStore.loginExpired"
      :avatar="avatar"
    >
      <LoginForm />
    </AuthenticationLoginExpiredModal>
  </div>
</template>

<style scoped>
.cgch-shell {
  --vben-content-height: calc(100vh - 28px);

  display: flex;
  gap: 14px;
  width: 100%;
  height: 100vh;
  padding: 14px 16px;
  overflow: hidden;
  background:
    radial-gradient(ellipse at 4% 0%, rgb(0 122 255 / 9%), transparent 42%),
    radial-gradient(ellipse at 96% 96%, rgb(175 82 222 / 7%), transparent 40%),
    var(--ap-bg);
}

.cgch-rail {
  z-index: 10;
  display: flex;
  flex: 0 0 var(--ap-rail-width);
  flex-direction: column;
  padding: 14px 10px 10px;
  background: var(--ap-glass);
  border: 0.5px solid rgb(255 255 255 / 85%);
  border-radius: var(--ap-radius-xl);
  box-shadow: var(--ap-shadow-rest);
  backdrop-filter: var(--ap-blur);
  transition: flex-basis var(--ap-dur) var(--ap-ease);
}

.is-collapsed .cgch-rail {
  flex-basis: var(--ap-rail-collapsed);
  padding-inline: 8px;
}

.cgch-rail__top {
  display: flex;
  gap: 6px;
  align-items: center;
  justify-content: space-between;
  margin: 2px 4px 12px;
}

.is-collapsed .cgch-rail__top {
  flex-direction: column;
  gap: 8px;
  margin-inline: 0;
}

.cgch-rail__logo {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
  cursor: pointer;
  transition: transform var(--ap-dur) var(--ap-ease);
}

.cgch-rail__logo:hover {
  transform: scale(1.02);
}

.cgch-rail__logo img {
  flex: none;
  width: 30px;
  height: 30px;
  object-fit: contain;
  border-radius: 8px;
  box-shadow: 0 3px 10px rgb(0 122 255 / 18%);
}

.cgch-rail__brand {
  font-size: 15px;
  font-weight: 700;
  color: var(--ap-label);
  letter-spacing: -0.02em;
  white-space: nowrap;
}

.cgch-rail__collapse {
  display: grid;
  flex: none;
  place-items: center;
  width: 28px;
  height: 28px;
  color: var(--ap-label-3);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 8px;
  transition:
    background-color var(--ap-dur-fast),
    color var(--ap-dur-fast),
    transform var(--ap-dur-fast) var(--ap-ease);
}

.cgch-rail__collapse:hover {
  color: var(--ap-label);
  background: var(--ap-fill-4);
}

.cgch-rail__collapse:active {
  transform: scale(0.92);
}

.cgch-rail__search {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
  height: 34px;
  padding: 0 10px;
  margin-bottom: 10px;
  font-size: 13px;
  color: var(--ap-label-3);
  cursor: pointer;
  background: var(--ap-fill-4);
  border: 0;
  border-radius: 10px;
  transition:
    background-color var(--ap-dur-fast),
    color var(--ap-dur-fast),
    transform var(--ap-dur-fast) var(--ap-ease);
}

.cgch-rail__search:hover {
  color: var(--ap-label-2);
  background: var(--ap-fill-3);
}

.cgch-rail__search:active {
  transform: scale(0.98);
}

.cgch-rail__search-text {
  flex: 1;
  text-align: left;
}

.cgch-rail__kbd {
  padding: 1px 5px;
  font-family: var(--ap-font-mono);
  font-size: 10px;
  color: var(--ap-label-3);
  background: rgb(255 255 255 / 70%);
  border-radius: 5px;
}

.cgch-rail__nav {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-height: 0;
  overflow: auto;
}

.cgch-rail__indicator {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 0;
  pointer-events: none;
  background: var(--ap-surface);
  border-radius: 10px;
  box-shadow:
    0 1px 3px rgb(0 0 0 / 6%),
    0 0 0 0.5px rgb(0 0 0 / 4%);
}

.rail-group {
  padding: 12px 12px 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--ap-label-3);
  letter-spacing: 0.02em;
}

.rail-group:first-of-type {
  padding-top: 4px;
}

.cgch-rail__item {
  position: relative;
  z-index: 1;
  display: flex;
  flex-shrink: 0;
  gap: 10px;
  align-items: center;
  width: 100%;
  height: 38px;
  padding: 0 12px;
  font-size: 14px;
  font-weight: 500;
  color: var(--ap-label-2);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 10px;
  transition:
    color var(--ap-dur-fast) linear,
    background-color var(--ap-dur-fast) linear,
    transform var(--ap-dur-fast) var(--ap-ease);
}

.cgch-rail__item:hover:not(.is-active) {
  color: var(--ap-label);
  background: rgb(0 0 0 / 4%);
}

.cgch-rail__item:active {
  transform: scale(0.97);
}

.cgch-rail__item.is-active {
  font-weight: 600;
  color: var(--ap-label);
}

.cgch-rail__bottom .cgch-rail__item.is-active {
  background: var(--ap-surface);
  box-shadow:
    0 1px 3px rgb(0 0 0 / 6%),
    0 0 0 0.5px rgb(0 0 0 / 4%);
}

.cgch-rail__icon {
  flex: none;
  width: 18px;
  height: 18px;
  color: var(--ap-label-3);
  transition:
    color var(--ap-dur-fast) linear,
    transform var(--ap-dur) var(--ap-spring);
}

.cgch-rail__item:hover .cgch-rail__icon {
  color: var(--ap-label);
}

.cgch-rail__item.is-active .cgch-rail__icon {
  color: var(--ap-accent);
  transform: scale(1.06);
}

.cgch-rail__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cgch-rail__bottom {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-top: 8px;
  margin-top: 8px;
  border-top: 0.5px solid var(--ap-separator);
}

.cgch-rail__avatar {
  display: flex;
  gap: 10px;
  align-items: center;
  width: 100%;
  padding: 6px 8px;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 12px;
  transition:
    background-color var(--ap-dur-fast),
    transform var(--ap-dur-fast) var(--ap-ease);
}

.cgch-rail__avatar:hover,
.cgch-rail__avatar.is-active {
  background: rgb(0 0 0 / 4.5%);
}

.cgch-rail__avatar:active {
  transform: scale(0.98);
}

.rail-user {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.rail-name {
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  color: var(--ap-label);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rail-email {
  overflow: hidden;
  font-size: 11px;
  color: var(--ap-label-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rail-chevron {
  flex: none;
  width: 14px;
  height: 14px;
  color: var(--ap-label-3);
}

.is-collapsed .cgch-rail__brand,
.is-collapsed .cgch-rail__search-text,
.is-collapsed .cgch-rail__kbd,
.is-collapsed .rail-group,
.is-collapsed .cgch-rail__label,
.is-collapsed .rail-user,
.is-collapsed .rail-chevron {
  display: none;
}

.is-collapsed .cgch-rail__search,
.is-collapsed .cgch-rail__item,
.is-collapsed .cgch-rail__avatar {
  justify-content: center;
  padding-inline: 0;
}

.cgch-main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  height: 100%;
  padding: 0;
  overflow: auto;
}

/* 平台运营正在查看某家客户的后台 */
.cgch-visit-bar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--ap-orange-text);
  background: rgb(255 244 229 / 92%);
  border: 0.5px solid rgb(255 149 0 / 35%);
  border-radius: var(--ap-radius-md);
  backdrop-filter: var(--ap-blur);
}

.cgch-visit-bar__exit {
  padding: 5px 14px;
  font-size: 13px;
  font-weight: 500;
  color: #fff;
  cursor: pointer;
  background: var(--ap-orange);
  border: 0;
  border-radius: var(--ap-radius-pill);
  transition: transform var(--ap-dur-fast) var(--ap-ease);
}

.cgch-visit-bar__exit:active {
  transform: scale(0.96);
}

@media (width <= 1200px) {
  .cgch-rail {
    flex-basis: 188px;
  }
}

@media (width <= 760px) {
  .cgch-shell {
    gap: 8px;
    padding: 8px;
  }

  .cgch-rail {
    flex-basis: 58px;
    padding: 12px 5px;
  }

  .cgch-rail__brand,
  .cgch-rail__collapse,
  .cgch-rail__search-text,
  .cgch-rail__kbd,
  .rail-group,
  .cgch-rail__label,
  .rail-user,
  .rail-chevron {
    display: none;
  }

  .cgch-rail__top {
    justify-content: center;
    margin-inline: 0;
  }

  .cgch-rail__search,
  .cgch-rail__item,
  .cgch-rail__avatar {
    justify-content: center;
    padding-inline: 0;
  }
}
</style>
