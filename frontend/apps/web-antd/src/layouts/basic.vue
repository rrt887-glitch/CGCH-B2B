<script lang="ts" setup>
/**
 * 草根出海主框架：左侧图标导航（看板、会话、CRM、智能体、渠道，底部设置与头像），右侧页面内容。
 * 不使用开源框架自带的顶栏、标签页和多级菜单。
 */
import { computed } from 'vue';
import { useRoute } from 'vue-router';

import { AuthenticationLoginExpiredModal } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';
import { preferences } from '@vben/preferences';
import { useAccessStore, useUserStore } from '@vben/stores';

import { Avatar, Dropdown, Menu, MenuItem, Tooltip } from 'ant-design-vue';

import { router } from '#/router';
import { useAuthStore } from '#/store';
import LoginForm from '#/views/_core/authentication/login.vue';

const userStore = useUserStore();
const authStore = useAuthStore();
const accessStore = useAccessStore();
const route = useRoute();

interface NavItem {
  key: string;
  label: string;
  icon: string;
  path: string;
}

const NAV: NavItem[] = [
  { key: 'dashboard', label: '看板', icon: 'lucide:chart-no-axes-combined', path: '/v3/dashboard' },
  { key: 'conversation', label: '会话', icon: 'lucide:message-circle-more', path: '/v3/conversation' },
  { key: 'crm', label: 'CRM', icon: 'lucide:user-round-search', path: '/v3/crm' },
  { key: 'agent', label: '智能体', icon: 'lucide:bot', path: '/v3/agent' },
  { key: 'channel', label: '渠道', icon: 'lucide:cable', path: '/v3/channel' },
];
const SETTINGS: NavItem = { key: 'settings', label: '设置', icon: 'lucide:settings', path: '/v3/settings' };

/** 当前所在模块：按路径前缀判断，CRM 的客户/线索详情等页面也算 CRM */
const activeKey = computed(() => {
  const p = route.path;
  const hit = [...NAV, SETTINGS].find((n) => p === n.path || p.startsWith(`${n.path}/`));
  if (hit) return hit.key;
  if (p.startsWith('/crm')) return 'crm';
  if (p.startsWith('/system') || p.startsWith('/profile')) return 'settings';
  return '';
});

function go(item: NavItem) {
  if (route.path !== item.path) router.push(item.path);
}

const avatar = computed(() => userStore.userInfo?.avatar || preferences.app.defaultAvatar);
const nickname = computed(() => userStore.userInfo?.nickname || userStore.userInfo?.username || '');

function onUserMenu({ key }: { key: number | string }) {
  if (key === 'profile') router.push({ name: 'Profile' });
  if (key === 'logout') authStore.logout(false);
}
</script>

<template>
  <div class="cgch-shell">
    <aside class="cgch-rail">
      <div class="cgch-rail__logo" title="草根出海" @click="router.push('/v3/dashboard')">
        <img src="/brand/logo.png" alt="草根出海" />
      </div>
      <nav class="cgch-rail__nav">
        <button
          v-for="item in NAV"
          :key="item.key"
          type="button"
          class="cgch-rail__item"
          :class="{ 'is-active': activeKey === item.key }"
          @click="go(item)"
        >
          <IconifyIcon :icon="item.icon" class="cgch-rail__icon" />
          <span>{{ item.label }}</span>
        </button>
      </nav>
      <div class="cgch-rail__bottom">
        <button
          type="button"
          class="cgch-rail__item"
          :class="{ 'is-active': activeKey === 'settings' }"
          @click="go(SETTINGS)"
        >
          <IconifyIcon :icon="SETTINGS.icon" class="cgch-rail__icon" />
          <span>{{ SETTINGS.label }}</span>
        </button>
        <Dropdown placement="topLeft" :trigger="['click']">
          <Tooltip :title="nickname" placement="right">
            <button type="button" class="cgch-rail__avatar">
              <Avatar :src="avatar" :size="34">{{ nickname.slice(0, 1) }}</Avatar>
            </button>
          </Tooltip>
          <template #overlay>
            <Menu @click="onUserMenu">
              <div class="px-3 py-2 text-xs text-gray-500">
                <div class="text-sm font-medium text-gray-800">{{ nickname }}</div>
                <div v-if="userStore.userInfo?.email">{{ userStore.userInfo?.email }}</div>
              </div>
              <MenuItem key="profile">个人中心</MenuItem>
              <MenuItem key="logout">退出登录</MenuItem>
            </Menu>
          </template>
        </Dropdown>
      </div>
    </aside>

    <main class="cgch-main">
      <RouterView v-slot="{ Component, route: r }">
        <component :is="Component" :key="r.path" />
      </RouterView>
    </main>

    <AuthenticationLoginExpiredModal v-model:open="accessStore.loginExpired" :avatar="avatar">
      <LoginForm />
    </AuthenticationLoginExpiredModal>
  </div>
</template>

<style scoped>
/* 清爽专业：白色图标导航 + 浅灰画布（设计稿「会话工作台」左侧） */
.cgch-shell {
  --vben-content-height: calc(100vh - 24px);

  display: flex;
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background: var(--cg-canvas, #f6f7f9);
}

.cgch-rail {
  display: flex;
  flex: 0 0 72px;
  flex-direction: column;
  align-items: center;
  padding: 16px 0;
  background: #fff;
  border-right: 1px solid #e5e7eb;
}

.cgch-rail__logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  margin-bottom: 16px;
  cursor: pointer;
}

.cgch-rail__logo img {
  width: 36px;
  height: 36px;
  object-fit: contain;
  border-radius: 10px;
}

.cgch-rail__nav {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  align-items: center;
}

.cgch-rail__bottom {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
}

.cgch-rail__item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 54px;
  font-size: 11px;
  line-height: 14px;
  color: #4b5563;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 10px;
  transition:
    background 0.15s,
    color 0.15s;
}

.cgch-rail__item:hover {
  color: #111827;
  background: #f3f4f6;
}

.cgch-rail__item.is-active {
  font-weight: 600;
  color: #2563eb;
  background: #eff4ff;
}

.cgch-rail__item:focus-visible {
  outline: 2px solid #2563eb;
  outline-offset: 2px;
}

.cgch-rail__icon {
  width: 20px;
  height: 20px;
}

.cgch-rail__avatar {
  padding: 0;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 50%;
}

.cgch-main {
  flex: 1;
  min-width: 0;
  height: 100vh;
  padding: 12px;
  overflow: auto;
}
</style>
