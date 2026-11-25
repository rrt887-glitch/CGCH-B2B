<script lang="ts" setup>
/**
 * 登录页框架（苹果极简风格）：
 * 左上品牌标识、左侧主标题与 4 枚能力胶囊、中央同心轨道 + AI 星标缓慢旋转动效、
 * 右侧纯净白卡登录表单、左下版权与条款链接。
 */
import { computed } from 'vue';

import { preferences } from '@vben/preferences';

const appName = computed(() => preferences.app.name);
const logo = computed(() => preferences.logo.source);
const year = new Date().getFullYear();

const FEATURES = [
  { label: '7×24 小时自动回复', live: true },
  { label: '多语言' },
  { label: '客户背调' },
  { label: 'CRM 自动建档' },
];
</script>

<template>
  <div class="cg-auth">
    <!-- 左上品牌 -->
    <header class="cg-auth__topbar ap-rise">
      <div class="cg-auth__brand">
        <img :src="logo" :alt="appName" />
        <span>{{ appName }}</span>
      </div>
    </header>

    <!-- 中央同心轨道与 AI 核心星标（缓慢旋转动效） -->
    <div class="cg-auth__orbit" aria-hidden="true">
      <div class="orbit-ring orbit-ring--3">
        <span class="orbit-dot orbit-dot--orange"></span>
        <span class="orbit-dot orbit-dot--purple"></span>
      </div>
      <div class="orbit-ring orbit-ring--2">
        <span class="orbit-dot orbit-dot--green"></span>
        <span class="orbit-dot orbit-dot--blue"></span>
      </div>
      <div class="orbit-ring orbit-ring--1"></div>
      <div class="orbit-core">
        <div class="orbit-core__halo"></div>
        <div class="orbit-core__disc">
          <svg viewBox="0 0 24 24" class="orbit-core__star" fill="currentColor">
            <path
              d="M12 2.5C12.5 7.5 16.5 11.5 21.5 12C16.5 12.5 12.5 16.5 12 21.5C11.5 16.5 7.5 12.5 2.5 12C7.5 11.5 11.5 7.5 12 2.5Z"
            />
          </svg>
        </div>
      </div>
    </div>

    <!-- 主体两列 -->
    <main class="cg-auth__main">
      <section class="cg-auth__hero ap-rise">
        <h1>让 AI 替你接待<br />全世界的询盘。</h1>
        <p>
          WhatsApp、邮件、Facebook、Instagram 的客户消息集中到一处，AI
          先回复、先背调、先建档，你只处理值得成交的那部分。
        </p>
        <div class="cg-auth__pills">
          <span v-for="f in FEATURES" :key="f.label" class="cg-auth__pill">
            <span v-if="f.live" class="cg-auth__live-dot"></span>
            {{ f.label }}
          </span>
        </div>
      </section>

      <section
        class="cg-auth__card ap-rise"
        style="animation-delay: 80ms"
      >
        <RouterView v-slot="{ Component, route }">
          <KeepAlive :include="['Login']">
            <component :is="Component" :key="route.fullPath" class="w-full" />
          </KeepAlive>
        </RouterView>
        <div class="cg-auth__hint">连续输错 5 次将锁定 15 分钟</div>
      </section>
    </main>

    <!-- 左下页脚 -->
    <footer class="cg-auth__foot ap-rise" style="animation-delay: 140ms">
      <span>© {{ year }} {{ appName }}</span>
      <span class="cg-auth__sep">·</span>
      <a href="/privacy" target="_blank">隐私政策</a>
      <span class="cg-auth__sep">·</span>
      <a href="/terms" target="_blank">服务条款</a>
    </footer>
  </div>
</template>

<style scoped>
.cg-auth {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 100vh;
  padding: 36px 56px 28px;
  overflow: hidden;
  font-family: var(--ap-font);
  color: var(--ap-label);
  background: #f5f6f8;
}

.cg-auth__topbar {
  position: relative;
  z-index: 2;
}

.cg-auth__brand {
  display: inline-flex;
  gap: 10px;
  align-items: center;
  font-size: 16px;
  font-weight: 700;
  color: var(--ap-label);
  letter-spacing: -0.015em;
}

.cg-auth__brand img {
  width: 30px;
  height: 30px;
  object-fit: contain;
}

/* ===== 中央同心轨道与 AI 星标 ===== */
.cg-auth__orbit {
  position: absolute;
  top: 50%;
  left: 53%;
  z-index: 0;
  width: 580px;
  height: 580px;
  pointer-events: none;
  transform: translate(-50%, -50%);
}

.orbit-ring {
  position: absolute;
  top: 50%;
  left: 50%;
  border: 1px solid rgb(0 0 0 / 5.5%);
  border-radius: 50%;
  transform: translate(-50%, -50%);
}

.orbit-ring--1 {
  width: 240px;
  height: 240px;
}

.orbit-ring--2 {
  width: 396px;
  height: 396px;
  animation: cg-orbit-cw 56s linear infinite;
}

.orbit-ring--3 {
  width: 556px;
  height: 556px;
  animation: cg-orbit-ccw 84s linear infinite;
}

.orbit-dot {
  position: absolute;
  width: 11px;
  height: 11px;
  border-radius: 50%;
}

.orbit-dot--orange {
  top: 12px;
  left: 38%;
  background: #ff9500;
  box-shadow: 0 0 12px rgb(255 149 0 / 40%);
}

.orbit-dot--purple {
  top: 44%;
  left: -5px;
  background: #af52de;
  box-shadow: 0 0 12px rgb(175 82 222 / 38%);
}

.orbit-dot--green {
  top: 38%;
  right: -5px;
  background: #34c759;
  box-shadow: 0 0 12px rgb(52 199 89 / 40%);
}

.orbit-dot--blue {
  bottom: 10px;
  left: 60%;
  background: #38bdf8;
  box-shadow: 0 0 12px rgb(56 189 248 / 42%);
}

.orbit-core {
  position: absolute;
  top: 50%;
  left: 50%;
  display: grid;
  place-items: center;
  transform: translate(-50%, -50%);
}

.orbit-core__halo {
  position: absolute;
  width: 104px;
  height: 104px;
  background: rgb(0 122 255 / 16%);
  border-radius: 50%;
  animation: cg-halo-pulse 4.6s ease-in-out infinite;
}

.orbit-core__disc {
  position: relative;
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  color: #fff;
  background: linear-gradient(145deg, #1a8cff, #007aff);
  border-radius: 50%;
  box-shadow: 0 10px 26px rgb(0 122 255 / 32%);
}

.orbit-core__star {
  width: 28px;
  height: 28px;
}

@keyframes cg-orbit-cw {
  from {
    transform: translate(-50%, -50%) rotate(0deg);
  }

  to {
    transform: translate(-50%, -50%) rotate(360deg);
  }
}

@keyframes cg-orbit-ccw {
  from {
    transform: translate(-50%, -50%) rotate(0deg);
  }

  to {
    transform: translate(-50%, -50%) rotate(-360deg);
  }
}

@keyframes cg-halo-pulse {
  0%,
  100% {
    opacity: 0.72;
    transform: scale(1);
  }

  50% {
    opacity: 1;
    transform: scale(1.08);
  }
}

/* ===== 主体两列 ===== */
.cg-auth__main {
  position: relative;
  z-index: 2;
  display: flex;
  gap: 48px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  max-width: 1320px;
  margin: auto;
}

.cg-auth__hero {
  max-width: 520px;
}

.cg-auth__hero h1 {
  margin: 0;
  font-size: 48px;
  font-weight: 800;
  line-height: 1.18;
  color: #1d1d1f;
  letter-spacing: -0.025em;
}

.cg-auth__hero p {
  max-width: 470px;
  margin: 22px 0 0;
  font-size: 15px;
  line-height: 1.75;
  color: #6e6e73;
}

.cg-auth__pills {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 26px;
}

.cg-auth__pill {
  display: inline-flex;
  gap: 7px;
  align-items: center;
  height: 32px;
  padding: 0 14px;
  font-size: 12.5px;
  font-weight: 500;
  color: #3a3a3c;
  background: #fff;
  border-radius: 999px;
  box-shadow:
    0 1px 2px rgb(0 0 0 / 4%),
    0 0 0 0.5px rgb(0 0 0 / 6%);
}

.cg-auth__live-dot {
  width: 7px;
  height: 7px;
  background: #34c759;
  border-radius: 50%;
  box-shadow: 0 0 0 3px rgb(52 199 89 / 18%);
}

/* ===== 右侧登录白卡 ===== */
.cg-auth__card {
  box-sizing: border-box;
  flex: none;
  width: 392px;
  max-width: 100%;
  padding: 36px 32px 28px;
  background: #fff;
  border-radius: 24px;
  box-shadow:
    0 0 0 0.5px rgb(0 0 0 / 4%),
    0 16px 48px rgb(0 0 0 / 7%);
}

.cg-auth__card :deep(h2) {
  margin-bottom: 6px !important;
  font-size: 24px !important;
  font-weight: 700 !important;
  color: #1d1d1f !important;
  letter-spacing: -0.015em;
}

.cg-auth__card :deep(.text-muted-foreground) {
  font-size: 13px;
  color: #86868b !important;
}

.cg-auth__card :deep(input) {
  height: 44px !important;
  padding-inline: 14px !important;
  font-size: 14px !important;
  background: #eceef2 !important;
  border-color: transparent !important;
  border-radius: 11px !important;
  box-shadow: none !important;
  transition:
    background-color 180ms linear,
    box-shadow 180ms var(--ap-ease) !important;
}

.cg-auth__card :deep(input:focus) {
  background: #fff !important;
  box-shadow:
    0 0 0 3px rgb(0 122 255 / 16%),
    inset 0 0 0 1px #007aff !important;
}

.cg-auth__card :deep(button[type='submit']),
.cg-auth__card :deep(.ant-btn-primary),
.cg-auth__card :deep([aria-label='login']) {
  height: 44px !important;
  font-size: 15px !important;
  font-weight: 600 !important;
  border-radius: 11px !important;
}

.cg-auth__hint {
  margin-top: 14px;
  font-size: 12px;
  color: #8e8e93;
  text-align: center;
}

/* ===== 页脚 ===== */
.cg-auth__foot {
  position: relative;
  z-index: 2;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  color: #86868b;
}

.cg-auth__foot a {
  color: #86868b;
  transition: color 140ms;
}

.cg-auth__foot a:hover {
  color: var(--ap-label);
}

.cg-auth__sep {
  opacity: 0.5;
}

@media (width <= 1080px) {
  .cg-auth {
    padding: 24px 28px;
  }

  .cg-auth__orbit {
    display: none;
  }

  .cg-auth__hero h1 {
    font-size: 38px;
  }
}

@media (width <= 860px) {
  .cg-auth__main {
    flex-direction: column;
    gap: 28px;
    justify-content: center;
  }

  .cg-auth__hero {
    display: none;
  }

  .cg-auth__card {
    width: 100%;
    max-width: 400px;
    padding: 28px 24px 24px;
  }

  .cg-auth__foot {
    justify-content: center;
  }
}

@media (prefers-reduced-motion: reduce) {
  .orbit-ring--2,
  .orbit-ring--3,
  .orbit-core__halo {
    animation: none;
  }
}
</style>
