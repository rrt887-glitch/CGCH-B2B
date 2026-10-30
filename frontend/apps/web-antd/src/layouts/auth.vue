<script lang="ts" setup>
/**
 * 登录页框架（设计稿「登录页」）：左侧浅灰品牌区介绍产品，右侧白底放登录 / 注册 / 找回密码表单。
 * 窄屏时品牌区收起，只保留表单。
 */
import { computed } from 'vue';

import { IconifyIcon } from '@vben/icons';
import { preferences } from '@vben/preferences';

const appName = computed(() => preferences.app.name);
const logo = computed(() => preferences.logo.source);

const POINTS = [
  { icon: 'lucide:message-circle-more', title: '全渠道一个工作台', text: 'WhatsApp、Facebook、Instagram、邮件、官网客服统一收发' },
  { icon: 'lucide:bot', title: 'AI 先接待，人来拍板', text: '按知识库回答，价格和合同等关键问题转人工' },
  { icon: 'lucide:chart-no-axes-combined', title: '询盘自动进 CRM', text: '识别公司、国家和需求，按阶段跟进到成交' },
];
</script>

<template>
  <div class="cg-auth">
    <section class="cg-auth__brand">
      <div class="cg-auth__logo">
        <img :src="logo" :alt="appName" />
        <span>{{ appName }}</span>
      </div>
      <div class="cg-auth__pitch">
        <h1>让 AI 数字员工<br />帮你接住每一个海外询盘</h1>
        <p>WhatsApp、Facebook、Instagram、邮件和官网客服的询盘集中在一个工作台，AI 先接待、自动录入 CRM，业务员只处理需要人来决定的事。</p>
        <ul>
          <li v-for="p in POINTS" :key="p.title">
            <span class="cg-auth__icon"><IconifyIcon :icon="p.icon" class="size-4" /></span>
            <span class="flex flex-col gap-0.5">
              <span class="font-semibold text-[#111827]">{{ p.title }}</span>
              <span class="text-[13px] text-[#4B5563]">{{ p.text }}</span>
            </span>
          </li>
        </ul>
      </div>
      <div class="cg-auth__foot">
        <a href="/privacy" target="_blank">隐私政策</a>
        <a href="/terms" target="_blank">服务条款</a>
        <span>© {{ appName }}</span>
      </div>
    </section>

    <section class="cg-auth__form">
      <RouterView v-slot="{ Component, route }">
        <KeepAlive :include="['Login']">
          <component :is="Component" :key="route.fullPath" class="w-full max-w-[380px]" />
        </KeepAlive>
      </RouterView>
    </section>
  </div>
</template>

<style scoped>
.cg-auth {
  display: flex;
  min-height: 100vh;
  font-family: var(--cg-font);
  color: #111827;
  background: #fff;
}

.cg-auth__brand {
  display: flex;
  flex: 1 1 52%;
  flex-direction: column;
  gap: 40px;
  padding: 56px 64px;
  background: #f6f7f9;
  border-right: 1px solid #e5e7eb;
}

.cg-auth__logo {
  display: flex;
  gap: 10px;
  align-items: center;
  font-size: 18px;
  font-weight: 700;
}

.cg-auth__logo img {
  width: 36px;
  height: 36px;
  object-fit: contain;
  border-radius: 10px;
}

.cg-auth__pitch {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 20px;
  justify-content: center;
  max-width: 520px;
}

.cg-auth__pitch h1 {
  margin: 0;
  font-size: 34px;
  font-weight: 700;
  line-height: 46px;
  letter-spacing: -0.01em;
}

.cg-auth__pitch p {
  margin: 0;
  font-size: 16px;
  line-height: 26px;
  color: #4b5563;
}

.cg-auth__pitch ul {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 0;
  margin: 8px 0 0;
  list-style: none;
}

.cg-auth__pitch li {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.cg-auth__icon {
  display: flex;
  flex: 0 0 28px;
  align-items: center;
  justify-content: center;
  height: 28px;
  color: #1d4ed8;
  background: #eff4ff;
  border-radius: 8px;
}

.cg-auth__foot {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 12px;
  color: #6b7280;
}

.cg-auth__foot a {
  color: #4b5563;
}

.cg-auth__form {
  display: flex;
  flex: 1 1 48%;
  align-items: center;
  justify-content: center;
  padding: 56px 24px;
}

@media (max-width: 900px) {
  .cg-auth__brand {
    display: none;
  }
}
</style>
