<script lang="ts" setup>
/**
 * 登录页框架（设计稿「苹果风格 · 登录」）：居中的 LOGO + 草根出海、一句话标题，下面是毛玻璃卡片里的登录 / 注册 / 找回密码表单。
 * 背景两团缓慢飘动的蓝紫光晕；系统设置了「减少动态效果」时不动。
 */
import { computed } from 'vue';

import { preferences } from '@vben/preferences';

const appName = computed(() => preferences.app.name);
const logo = computed(() => preferences.logo.source);
</script>

<template>
  <div class="cg-auth">
    <div class="cg-auth__blob cg-auth__blob--a" aria-hidden="true"></div>
    <div class="cg-auth__blob cg-auth__blob--b" aria-hidden="true"></div>

    <div class="cg-auth__col">
      <header class="cg-auth__head cg-rise">
        <div class="cg-auth__brand">
          <img :src="logo" :alt="appName" />
          <span>{{ appName }}</span>
        </div>
        <h1>接住每一个<br /><span>海外询盘。</span></h1>
        <p>
          AI 先接待，你来拍板。<br />客户消息、采购需求与团队跟进，<br />在一个工作台里，有序连接。
        </p>
      </header>

      <section class="cg-auth__card cg-rise" style="animation-delay: 0.12s">
        <RouterView v-slot="{ Component, route }">
          <KeepAlive :include="['Login']">
            <component :is="Component" :key="route.fullPath" class="w-full" />
          </KeepAlive>
        </RouterView>
      </section>

      <footer class="cg-auth__foot cg-rise" style="animation-delay: 0.24s">
        <a href="/privacy" target="_blank">隐私政策</a>
        <a href="/terms" target="_blank">服务条款</a>
        <span>© {{ appName }}</span>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.cg-auth {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  overflow: hidden;
  font-family: var(--cg-font);
  color: var(--cg-text);
  background: var(--cg-canvas);
}

.cg-auth__blob {
  position: absolute;
  border-radius: 50%;
  animation: cg-float 14s ease-in-out infinite;
}

.cg-auth__blob--a {
  top: -180px;
  left: -140px;
  width: 620px;
  height: 620px;
  background: radial-gradient(
    circle,
    rgb(0 113 227 / 30%),
    rgb(0 113 227 / 0%) 65%
  );
}

.cg-auth__blob--b {
  right: -120px;
  bottom: -200px;
  width: 560px;
  height: 560px;
  background: radial-gradient(
    circle,
    rgb(175 82 222 / 24%),
    rgb(175 82 222 / 0%) 65%
  );
  animation-delay: -6s;
}

.cg-auth__col {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 28px;
  align-items: center;
  width: 100%;
  padding: 40px 16px;
}

.cg-auth__head {
  display: flex;
  flex-direction: column;
  gap: 14px;
  align-items: center;
  text-align: center;
}

.cg-auth__brand {
  display: flex;
  gap: 12px;
  align-items: center;
  font-size: 22px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.cg-auth__brand img {
  width: 52px;
  height: 52px;
  object-fit: contain;
  border-radius: 13px;
  box-shadow: 0 10px 30px rgb(0 113 227 / 25%);
}

.cg-auth__head h1 {
  margin: 6px 0 0;
  font-size: 44px;
  font-weight: 700;
  line-height: 1.08;
  letter-spacing: -0.03em;
}

.cg-auth__head p {
  margin: 0;
  font-size: 19px;
  color: var(--cg-text-2);
}

.cg-auth__card {
  box-sizing: border-box;
  width: 100%;
  max-width: 400px;
  padding: 28px;
  background: rgb(255 255 255 / 72%);
  border-radius: 24px;
  box-shadow:
    0 1px 2px rgb(0 0 0 / 4%),
    0 20px 50px rgb(0 0 0 / 10%);
  backdrop-filter: saturate(180%) blur(30px);
}

.cg-auth__foot {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  font-size: 12px;
  color: var(--cg-text-3);
}

.cg-auth__foot a {
  color: var(--cg-text-3);
}

@keyframes cg-float {
  0%,
  100% {
    transform: translate(0, 0);
  }

  50% {
    transform: translate(30px, -24px);
  }
}

@media (max-width: 640px) {
  .cg-auth__head h1 {
    font-size: 34px;
  }

  .cg-auth__card {
    padding: 22px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .cg-auth__blob {
    animation: none;
  }
}
</style>

<style scoped>
.cg-auth {
  background: #f2f2f7;
}
.cg-auth__blob {
  display: none;
}
.cg-auth__col {
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  min-height: 100vh;
  gap: 0;
  padding: 0;
  align-items: center;
}
.cg-auth__head {
  height: 100%;
  min-height: 100vh;
  align-items: flex-start;
  text-align: left;
  padding: 50px 64px;
  background: radial-gradient(ellipse at 20% 60%, #007aff14, transparent 70%);
}
.cg-auth__brand {
  font-size: 18px;
  gap: 12px;
}
.cg-auth__brand img {
  width: 36px;
  height: 36px;
  box-shadow: none;
  border-radius: 0;
}
.cg-auth__head h1 {
  font-size: 52px;
  line-height: 1.25;
  letter-spacing: -2px;
  margin: 110px 0 10px;
}
.cg-auth__head h1 span {
  color: #007aff;
}
.cg-auth__head p {
  font-size: 15px;
  line-height: 1.8;
}
.cg-auth__card {
  justify-self: center;
  width: calc(100% - 48px);
  max-width: 395px;
  padding: 34px;
  border: 1px solid #ffffffcc;
  background: #ffffffa8;
  border-radius: 28px;
  box-shadow: 0 8px 30px #0000000a;
}
.cg-auth__foot {
  position: absolute;
  left: 64px;
  bottom: 30px;
  font-size: 11px;
}
@media (max-width: 900px) {
  .cg-auth__col {
    grid-template-columns: 1fr;
  }
  .cg-auth__head {
    display: none;
  }
  .cg-auth__card {
    margin: 60px auto 85px;
  }
  .cg-auth__foot {
    left: 0;
    right: 0;
    justify-content: center;
  }
  .cg-auth__card {
    padding: 25px;
  }
}
</style>
