<script setup lang="ts">
import type { SystemUserProfileApi } from '#/api/system/user/profile';

import { onMounted, ref } from 'vue';

import { useUserStore } from '@vben/stores';

import { Segmented } from 'ant-design-vue';

import { getAuthPermissionInfoApi } from '#/api';
import { getUserProfile } from '#/api/system/user/profile';

import BaseInfo from './modules/base-info.vue';
import ProfileUser from './modules/profile-user.vue';
import ResetPwd from './modules/reset-pwd.vue';
import UserSocial from './modules/user-social.vue';

const userStore = useUserStore();
const activeName = ref('basicInfo');

const TABS = [
  { label: '基本设置', value: 'basicInfo' },
  { label: '密码设置', value: 'resetPwd' },
  { label: '社交绑定', value: 'userSocial' },
];

/** 加载个人信息 */
const profile = ref<SystemUserProfileApi.UserProfileRespVO>();
async function loadProfile() {
  profile.value = await getUserProfile();
}

/** 刷新个人信息 */
async function refreshProfile() {
  await loadProfile();
  const authPermissionInfo = await getAuthPermissionInfoApi();
  userStore.setUserInfo(authPermissionInfo.user);
}

onMounted(loadProfile);
</script>

<template>
  <div class="ap-profile">
    <header class="ap-profile__head">
      <h1>个人中心</h1>
      <p>管理你的账号资料、登录密码与第三方绑定</p>
    </header>

    <div class="ap-profile__grid">
      <section class="ap-profile__card ap-profile__card--left">
        <ProfileUser :profile="profile" @success="refreshProfile" />
      </section>

      <section class="ap-profile__card ap-profile__card--right">
        <div class="ap-profile__tabs">
          <Segmented v-model:value="activeName" :options="TABS" />
        </div>
        <div class="ap-profile__pane">
          <BaseInfo
            v-if="activeName === 'basicInfo'"
            :profile="profile"
            @success="refreshProfile"
          />
          <ResetPwd v-else-if="activeName === 'resetPwd'" />
          <UserSocial
            v-else
            @update:active-name="activeName = $event"
          />
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.ap-profile {
  max-width: 1020px;
  padding: 26px 28px 36px;
  margin: 0 auto;
}

.ap-profile__head {
  margin-bottom: 20px;
}

.ap-profile__head h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--ap-label);
  letter-spacing: -0.015em;
}

.ap-profile__head p {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--ap-label-3);
}

.ap-profile__grid {
  display: grid;
  grid-template-columns: 330px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.ap-profile__card {
  padding: 24px;
  background: var(--ap-surface);
  border-radius: 16px;
  box-shadow: var(--ap-shadow-rest);
}

.ap-profile__tabs {
  padding-bottom: 18px;
  margin-bottom: 18px;
  border-bottom: 0.5px solid var(--ap-separator);
}

.ap-profile__pane {
  animation: ap-fade-up 240ms var(--ap-ease-out) both;
}

@media (width <= 900px) {
  .ap-profile {
    padding: 16px;
  }

  .ap-profile__grid {
    grid-template-columns: 1fr;
  }
}
</style>
