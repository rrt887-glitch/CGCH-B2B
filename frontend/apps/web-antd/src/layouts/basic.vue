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
        <Transition name="cg-page" appear>
          <component :is="Component" :key="r.path" />
        </Transition>
      </RouterView>
    </main>

    <AuthenticationLoginExpiredModal v-model:open="accessStore.loginExpired" :avatar="avatar">
      <LoginForm />
    </AuthenticationLoginExpiredModal>
  </div>
</template>

<style scoped>
/* 苹果风格：毛玻璃图标导航 + 浅灰画布（设计稿「苹果风格」）；当前模块用蓝色胶囊高亮，切换带弹性过渡 */
.cgch-shell {
  --vben-content-height: calc(100vh - 32px);

  display: flex;
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background: var(--cg-canvas, #f5f5f7);
}

.cgch-rail {
  z-index: 10;
  display: flex;
  flex: 0 0 76px;
  flex-direction: column;
  align-items: center;
  padding: 18px 0 16px;
  background: rgb(246 246 248 / 78%);
  border-right: 0.5px solid rgb(0 0 0 / 8%);
  backdrop-filter: saturate(180%) blur(20px);
}

.cgch-rail__logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  margin-bottom: 18px;
  cursor: pointer;
  transition: transform 0.45s var(--cg-ease);
}

.cgch-rail__logo:hover {
  transform: scale(1.06);
}

.cgch-rail__logo img {
  width: 38px;
  height: 38px;
  object-fit: contain;
  border-radius: 10px;
  box-shadow: 0 4px 12px rgb(0 113 227 / 18%);
}

.cgch-rail__nav {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  align-items: center;
}

.cgch-rail__bottom {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
}

.cgch-rail__item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  justify-content: center;
  width: 60px;
  height: 56px;
  font-size: 11px;
  line-height: 14px;
  color: #424245;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 14px;
  transition:
    background-color 0.25s var(--cg-ease),
    color 0.25s var(--cg-ease),
    transform 0.2s var(--cg-ease);
}

.cgch-rail__item:hover {
  color: #1d1d1f;
  background: rgb(0 0 0 / 5%);
}

.cgch-rail__item:active {
  transform: scale(0.94);
}

.cgch-rail__item.is-active {
  font-weight: 600;
  color: #fff;
  background: #0071e3;
  box-shadow: 0 6px 16px rgb(0 113 227 / 28%);
}

.cgch-rail__item:focus-visible {
  outline: 2px solid #0071e3;
  outline-offset: 2px;
}

.cgch-rail__icon {
  width: 21px;
  height: 21px;
}

.cgch-rail__avatar {
  padding: 0;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 50%;
  transition: transform 0.2s var(--cg-ease);
}

.cgch-rail__avatar:active {
  transform: scale(0.92);
}

.cgch-main {
  flex: 1;
  min-width: 0;
  height: 100vh;
  padding: 16px;
  overflow: auto;
}
</style>
