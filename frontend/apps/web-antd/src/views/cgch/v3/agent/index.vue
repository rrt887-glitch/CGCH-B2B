<script lang="ts" setup>
/**
 * AI 助手（Apple Intelligence 三栏训练工作台）：
 * 左侧毛玻璃步骤轨（身份头卡 + 弹簧滑块 + 步骤图标），中间透明画布承载配置白卡，
 * 右侧常驻实时对话模拟器（窄屏自动切换为悬浮「模拟测试」抽屉）。
 */
import { computed, defineAsyncComponent, markRaw, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

import { Drawer } from 'ant-design-vue';

import { ApSkeleton } from '#/components/apple';
import { useSlidingIndicator } from '#/utils/motion';

import TestChat from './test-chat.vue';
import { sharedAgentName } from './use-settings';

defineOptions({ name: 'CgchV3Agent' });

const route = useRoute();
const router = useRouter();
const mobileTestOpen = ref(false);

const lazy = (loader: () => Promise<any>) =>
  markRaw(
    defineAsyncComponent({
      loader,
      loadingComponent: ApSkeleton,
      delay: 140,
    }),
  );

const STEPS = [
  {
    key: 'knowledge',
    label: '知识库',
    sub: '企业资料与文档',
    icon: 'lucide:book-open',
    view: lazy(() => import('./knowledge.vue')),
  },
  {
    key: 'persona',
    label: '角色人设',
    sub: '名称、性格与职责',
    icon: 'lucide:sparkles',
    view: lazy(() => import('./persona.vue')),
  },
  {
    key: 'skills',
    label: '配技能',
    sub: '接待、背调与建档',
    icon: 'lucide:cpu',
    view: lazy(() => import('./skills.vue')),
  },
  {
    key: 'scripts',
    label: '教话术',
    sub: '学习真人与案例库',
    icon: 'lucide:message-square-quote',
    view: lazy(() => import('./scripts.vue')),
  },
  {
    key: 'handoff',
    label: '转人工',
    sub: '触发条件与黑名单',
    icon: 'lucide:user-round-check',
    view: lazy(() => import('./handoff.vue')),
  },
];

const SUGGESTIONS: Record<string, string[]> = {
  knowledge: [
    'Do you have certificates for the products?',
    'Could you send me a product catalog first?',
    'Which countries do you mainly export to?',
  ],
  persona: [
    'Where do your product materials come from?',
    'Is there a discount if I order 100 units?',
    'Do your products support customization?',
  ],
  skills: [
    'Can you send me the latest catalog PDF?',
    'What is the estimated FOB price for 500 pcs?',
    'We need OEM packaging with our logo.',
  ],
  scripts: [
    'Hi, I am interested in your products.',
    'What is your MOQ?',
    'Can you share the price list?',
  ],
  handoff: [
    'I want to talk to a real person.',
    'Please send me a sample.',
    'I am very disappointed with the last order.',
  ],
};

const step = computed(
  () => STEPS.find((s) => s.key === route.query.tab) ?? STEPS[0]!,
);
const activeStepKey = computed(() => step.value.key);
const stepsRef = ref<HTMLElement>();
const { style: indicatorStyle } = useSlidingIndicator(
  stepsRef,
  activeStepKey,
  '.on',
);

function goStep(key: string) {
  if (key !== step.value.key) router.replace({ query: { tab: key } });
}
</script>

<template>
  <div class="agent">
    <!-- 左侧训练步骤轨 -->
    <aside class="agent__rail">
      <div class="agent__brand">
        <span class="agent__orb">
          <IconifyIcon icon="lucide:sparkles" class="size-4" />
        </span>
        <div class="min-w-0">
          <div class="agent__brand-name" :title="sharedAgentName">
            {{ sharedAgentName }}
          </div>
          <div class="agent__brand-sub">AI 数字员工 · 5 步训练</div>
        </div>
      </div>

      <div ref="stepsRef" class="agent__steps">
        <div
          class="agent__indicator"
          :style="indicatorStyle"
          aria-hidden="true"
        ></div>
        <div class="agent__group">训练步骤</div>
        <button
          v-for="(s, i) in STEPS"
          :key="s.key"
          type="button"
          class="agent__step"
          :class="{ on: s.key === step.key }"
          @click="goStep(s.key)"
        >
          <span class="agent__num">{{ i + 1 }}</span>
          <span class="agent__step-text">
            <b>{{ s.label }}</b>
            <small>{{ s.sub }}</small>
          </span>
          <IconifyIcon :icon="s.icon" class="agent__step-icon" />
        </button>
      </div>

      <div class="agent__rail-foot">
        <div class="agent__live-pill">
          <span class="agent__live-dot"></span>
          <span>改动实时生效 · 右侧随时测试</span>
        </div>
      </div>
    </aside>

    <!-- 中间配置主区 -->
    <section class="agent__main">
      <div :key="step.key" class="agent__view">
        <component :is="step.view" />
      </div>

      <!-- 窄屏悬浮模拟测试按钮 -->
      <button
        type="button"
        class="agent__fab"
        @click="mobileTestOpen = true"
      >
        <IconifyIcon icon="lucide:sparkles" class="size-4" />
        <span>模拟测试</span>
      </button>
    </section>

    <!-- 右侧实时对话模拟器 -->
    <aside class="agent__test">
      <TestChat
        :suggestions="SUGGESTIONS[step.key] ?? SUGGESTIONS.knowledge!"
      />
    </aside>

    <Drawer
      v-model:open="mobileTestOpen"
      title="实时对话效果预览"
      width="360"
      :body-style="{ padding: 0, height: '100%' }"
    >
      <TestChat
        :suggestions="SUGGESTIONS[step.key] ?? SUGGESTIONS.knowledge!"
      />
    </Drawer>
  </div>
</template>

<style scoped>
.agent {
  display: flex;
  height: 100%;
  min-height: 0;
}

.agent__rail {
  display: flex;
  flex: 0 0 218px;
  flex-direction: column;
  padding: 16px 12px 14px;
  background: rgb(255 255 255 / 54%);
  border-right: 0.5px solid rgb(0 0 0 / 6.5%);
  backdrop-filter: blur(24px) saturate(165%);
}

.agent__brand {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 4px 8px 14px;
  margin-bottom: 10px;
  border-bottom: 0.5px solid rgb(0 0 0 / 6%);
}

.agent__orb {
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

.agent__brand-name {
  overflow: hidden;
  font-size: 14.5px;
  font-weight: 700;
  line-height: 1.25;
  color: #1d1d1f;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent__brand-sub {
  margin-top: 2px;
  font-size: 11px;
  color: #86868b;
}

.agent__steps {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
}

.agent__indicator {
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

.agent__group {
  padding: 2px 10px 6px;
  font-size: 11px;
  font-weight: 600;
  color: #86868b;
  letter-spacing: 0.02em;
}

.agent__step {
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

.agent__step:hover:not(.on) {
  background: rgb(0 0 0 / 3.5%);
}

.agent__num {
  display: inline-flex;
  flex: 0 0 24px;
  align-items: center;
  justify-content: center;
  height: 24px;
  font-size: 11.5px;
  font-weight: 700;
  color: #6e6e73;
  background: rgb(0 0 0 / 5.5%);
  border-radius: 8px;
  transition:
    background-color var(--ap-dur-fast),
    color var(--ap-dur-fast),
    box-shadow var(--ap-dur-fast);
}

.agent__step.on .agent__num {
  color: #fff;
  background: linear-gradient(180deg, #1a88ff 0%, #007aff 100%);
  box-shadow: 0 3px 8px rgb(0 122 255 / 26%);
}

.agent__step-text {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.agent__step-text b {
  font-size: 13.5px;
  font-weight: 500;
  line-height: 1.25;
  color: #3a3a3c;
}

.agent__step.on .agent__step-text b {
  font-weight: 600;
  color: #1d1d1f;
}

.agent__step-text small {
  margin-top: 2px;
  overflow: hidden;
  font-size: 11px;
  color: #8e8e93;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent__step-icon {
  flex: none;
  width: 14px;
  height: 14px;
  color: #aeaeb2;
  opacity: 0.7;
  transition: color var(--ap-dur-fast);
}

.agent__step.on .agent__step-icon {
  color: #007aff;
  opacity: 1;
}

.agent__rail-foot {
  padding-top: 12px;
  border-top: 0.5px solid rgb(0 0 0 / 5%);
}

.agent__live-pill {
  display: flex;
  gap: 7px;
  align-items: center;
  padding: 7px 10px;
  font-size: 11px;
  color: #6e6e73;
  background: rgb(255 255 255 / 66%);
  border-radius: 10px;
  box-shadow: inset 0 0 0 0.5px rgb(0 0 0 / 5%);
}

.agent__live-dot {
  flex: none;
  width: 6px;
  height: 6px;
  background: #34c759;
  border-radius: 50%;
  box-shadow: 0 0 0 2.5px rgb(52 199 89 / 18%);
}

.agent__main {
  position: relative;
  flex: 1;
  min-width: 0;
  padding: 22px 28px 36px;
  overflow: auto;
  background: transparent;
}

.agent__view {
  max-width: 860px;
  margin: 0 auto;
  animation: ap-fade-up 200ms var(--ap-ease-out) both;
}

.agent__fab {
  position: fixed;
  right: 22px;
  bottom: 22px;
  z-index: 20;
  display: none;
  gap: 6px;
  align-items: center;
  height: 40px;
  padding: 0 16px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  cursor: pointer;
  background: linear-gradient(135deg, #af52de 0%, #007aff 100%);
  border: 0;
  border-radius: 999px;
  box-shadow: 0 6px 20px rgb(88 86 214 / 34%);
}

.agent__test {
  flex: 0 0 344px;
  overflow: hidden;
  background: rgb(246 246 250 / 88%);
  border-left: 0.5px solid rgb(0 0 0 / 7%);
  backdrop-filter: blur(24px) saturate(180%);
}

@media (width <= 1280px) {
  .agent__test {
    flex-basis: 304px;
  }
}

@media (width <= 1080px) {
  .agent__test {
    display: none;
  }

  .agent__fab {
    display: inline-flex;
  }
}

@media (width <= 760px) {
  .agent {
    flex-direction: column;
  }

  .agent__rail {
    flex: 0 0 auto;
    padding: 8px 10px;
    border-right: 0;
    border-bottom: 0.5px solid rgb(0 0 0 / 6.5%);
  }

  .agent__brand,
  .agent__group,
  .agent__step-text small,
  .agent__step-icon,
  .agent__rail-foot {
    display: none;
  }

  .agent__steps {
    flex-direction: row;
    overflow-x: auto;
  }

  .agent__step {
    white-space: nowrap;
  }

  .agent__main {
    padding: 16px;
  }
}
</style>
