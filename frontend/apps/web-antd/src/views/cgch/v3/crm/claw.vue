<script lang="ts" setup>
/**
 * CRM 智枢（Apple Intelligence / macOS Sequoia 二级工作台）：
 * 左侧毛玻璃智能导航栏（弹簧滑块 + 着色图标徽章），右侧透明画布承载新建对话 / 深度背调 / 客户唤醒。
 */
import { computed, defineAsyncComponent, markRaw, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import { ApSkeleton } from '#/components/apple';
import { useSlidingIndicator } from '#/utils/motion';

defineOptions({ name: 'CgchV3Claw' });

const route = useRoute();
const router = useRouter();

const lazy = (loader: () => Promise<any>) =>
  markRaw(
    defineAsyncComponent({
      loader,
      loadingComponent: ApSkeleton,
      delay: 120,
    }),
  );

const ITEMS = [
  {
    key: 'chat',
    label: '智能问答',
    sub: '对话查询客户与消息',
    icon: 'lucide:sparkles',
    tint: 'purple',
    view: lazy(() => import('#/views/cgch/claw/chat/index.vue')),
  },
  {
    key: 'research',
    label: '深度背调',
    sub: '官网社媒情报核验',
    icon: 'lucide:radar',
    tint: 'blue',
    view: lazy(() => import('#/views/cgch/claw/bg-check/index.vue')),
  },
  {
    key: 'wake',
    label: '客户唤醒',
    sub: '沉睡老客千人千面激活',
    icon: 'lucide:mail-check',
    tint: 'green',
    view: lazy(() => import('#/views/cgch/claw/reactivate/index.vue')),
  },
];

const active = computed(
  () => ITEMS.find((i) => i.key === route.query.sub) ?? ITEMS[0]!,
);
const activeKey = computed(() => active.value.key);
const navRef = ref<HTMLElement>();
const { style: indicatorStyle } = useSlidingIndicator(
  navRef,
  activeKey,
  '.on',
);

function go(key: string) {
  if (key !== active.value.key) {
    router.replace({ query: { tab: 'salesmind', sub: key } });
  }
}
</script>

<template>
  <div class="sm">
    <aside class="sm__rail">
      <div class="sm__brand">
        <span class="sm__orb">
          <IconifyIcon icon="lucide:sparkles" class="size-4" />
        </span>
        <div class="min-w-0">
          <div class="sm__title">CRM 智枢</div>
          <div class="sm__subtitle">AI 情报 · 唤醒 · 问答</div>
        </div>
      </div>

      <div ref="navRef" class="sm__menu">
        <div
          class="sm__indicator"
          :style="indicatorStyle"
          aria-hidden="true"
        ></div>
        <div class="sm__group-label">核心能力</div>
        <button
          v-for="it in ITEMS"
          :key="it.key"
          type="button"
          class="sm__item"
          :class="{ on: active.key === it.key }"
          @click="go(it.key)"
        >
          <span class="sm__icon" :class="`is-${it.tint}`">
            <IconifyIcon :icon="it.icon" class="size-4" />
          </span>
          <span class="sm__text">
            <b>{{ it.label }}</b>
            <small>{{ it.sub }}</small>
          </span>
        </button>
      </div>

      <div class="sm__foot">
        <div class="sm__foot-pill">
          <span class="sm__foot-dot"></span>
          <span>数据权限按当前登录账号隔离</span>
        </div>
      </div>
    </aside>

    <section class="sm__main">
      <div class="sm__mobile-bar">
        <button
          v-for="it in ITEMS"
          :key="it.key"
          type="button"
          class="sm__mobile-tab"
          :class="{ on: active.key === it.key }"
          @click="go(it.key)"
        >
          <IconifyIcon :icon="it.icon" class="size-3.5" />
          <span>{{ it.label }}</span>
        </button>
      </div>

      <div :key="active.key" class="sm__pane">
        <component :is="active.view" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.sm {
  display: flex;
  height: 100%;
  min-height: 0;
}

.sm__rail {
  display: flex;
  flex: 0 0 218px;
  flex-direction: column;
  padding: 16px 12px 14px;
  background: rgb(255 255 255 / 54%);
  border-right: 0.5px solid rgb(0 0 0 / 6.5%);
  backdrop-filter: blur(24px) saturate(165%);
}

.sm__brand {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 4px 8px 14px;
  margin-bottom: 10px;
  border-bottom: 0.5px solid rgb(0 0 0 / 6%);
}

.sm__orb {
  display: grid;
  flex: none;
  place-items: center;
  width: 34px;
  height: 34px;
  color: #fff;
  background: linear-gradient(135deg, #af52de 0%, #5856d6 50%, #007aff 100%);
  border-radius: 10px;
  box-shadow:
    inset 0 0.5px 0 rgb(255 255 255 / 35%),
    0 4px 12px rgb(88 86 214 / 28%);
}

.sm__title {
  font-size: 15px;
  font-weight: 700;
  line-height: 1.2;
  color: #1d1d1f;
  letter-spacing: -0.015em;
}

.sm__subtitle {
  margin-top: 2px;
  font-size: 11px;
  color: #86868b;
}

.sm__menu {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
}

.sm__indicator {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 0;
  pointer-events: none;
  background: #fff;
  border-radius: 12px;
  box-shadow:
    0 2px 8px rgb(15 23 42 / 7%),
    0 0 0 0.5px rgb(0 0 0 / 4%);
}

.sm__group-label {
  padding: 2px 10px 6px;
  font-size: 11px;
  font-weight: 600;
  color: #86868b;
  letter-spacing: 0.02em;
}

.sm__item {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 9px 10px;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 12px;
  transition: background-color var(--ap-dur-fast) linear;
}

.sm__item:hover:not(.on) {
  background: rgb(0 0 0 / 3.5%);
}

.sm__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  transition:
    transform var(--ap-dur-fast) var(--ap-ease),
    background-color var(--ap-dur-fast),
    color var(--ap-dur-fast);
}

.sm__icon.is-purple {
  color: #7c3aed;
  background: rgb(124 58 237 / 11%);
}

.sm__icon.is-blue {
  color: #007aff;
  background: rgb(0 122 255 / 11%);
}

.sm__icon.is-green {
  color: #1f9d4c;
  background: rgb(52 199 89 / 13%);
}

.sm__item.on .sm__icon.is-purple {
  color: #fff;
  background: linear-gradient(135deg, #af52de 0%, #5856d6 100%);
  box-shadow: 0 3px 8px rgb(124 58 237 / 28%);
}

.sm__item.on .sm__icon.is-blue {
  color: #fff;
  background: linear-gradient(180deg, #3395ff 0%, #007aff 100%);
  box-shadow: 0 3px 8px rgb(0 122 255 / 26%);
}

.sm__item.on .sm__icon.is-green {
  color: #fff;
  background: linear-gradient(180deg, #34c759 0%, #24a844 100%);
  box-shadow: 0 3px 8px rgb(52 199 89 / 26%);
}

.sm__text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.sm__text b {
  font-size: 13.5px;
  font-weight: 500;
  line-height: 1.25;
  color: #3a3a3c;
}

.sm__item.on .sm__text b {
  font-weight: 600;
  color: #1d1d1f;
}

.sm__text small {
  margin-top: 2px;
  overflow: hidden;
  font-size: 11px;
  color: #8e8e93;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sm__foot {
  padding-top: 12px;
  border-top: 0.5px solid rgb(0 0 0 / 5%);
}

.sm__foot-pill {
  display: flex;
  gap: 7px;
  align-items: center;
  padding: 7px 10px;
  font-size: 11px;
  line-height: 1.35;
  color: #6e6e73;
  background: rgb(255 255 255 / 66%);
  border-radius: 10px;
  box-shadow: inset 0 0 0 0.5px rgb(0 0 0 / 5%);
}

.sm__foot-dot {
  flex: none;
  width: 6px;
  height: 6px;
  background: #34c759;
  border-radius: 50%;
  box-shadow: 0 0 0 2.5px rgb(52 199 89 / 18%);
}

.sm__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.sm__mobile-bar {
  display: none;
}

.sm__pane {
  flex: 1;
  min-height: 0;
  overflow: auto;
  animation: ap-fade-up 200ms var(--ap-ease-out) both;
}

@media (width <= 860px) {
  .sm {
    flex-direction: column;
  }

  .sm__rail {
    display: none;
  }

  .sm__mobile-bar {
    display: flex;
    gap: 6px;
    padding: 10px 14px;
    background: rgb(255 255 255 / 72%);
    border-bottom: 0.5px solid rgb(0 0 0 / 6%);
    backdrop-filter: blur(20px);
  }

  .sm__mobile-tab {
    display: inline-flex;
    flex: 1;
    gap: 6px;
    align-items: center;
    justify-content: center;
    height: 34px;
    font-size: 13px;
    font-weight: 500;
    color: #6e6e73;
    cursor: pointer;
    background: rgb(0 0 0 / 4%);
    border: 0;
    border-radius: 999px;
  }

  .sm__mobile-tab.on {
    font-weight: 600;
    color: #1d1d1f;
    background: #fff;
    box-shadow: 0 1px 4px rgb(0 0 0 / 10%);
  }
}
</style>
