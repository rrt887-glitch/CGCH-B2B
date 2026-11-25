<script lang="ts" setup>
/**
 * AI 助手：左侧毛玻璃步骤导航（带步骤说明与弹簧滑块），中间透明画布承载精致白卡，
 * 右侧 Apple Intelligence 风格实时对话模拟器（1～5 步全程可测）。
 */
import { computed, defineAsyncComponent, markRaw, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { ApSkeleton } from '#/components/apple';
import { useSlidingIndicator } from '#/utils/motion';

import TestChat from './test-chat.vue';

defineOptions({ name: 'CgchV3Agent' });

const route = useRoute();
const router = useRouter();

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
    view: lazy(() => import('./knowledge.vue')),
  },
  {
    key: 'persona',
    label: '角色人设',
    sub: '名称、性格与职责',
    view: lazy(() => import('./persona.vue')),
  },
  {
    key: 'skills',
    label: '配技能',
    sub: '接待、背调与建档',
    view: lazy(() => import('./skills.vue')),
  },
  {
    key: 'scripts',
    label: '教话术',
    sub: '学习真人与开场白',
    view: lazy(() => import('./scripts.vue')),
  },
  {
    key: 'handoff',
    label: '转人工',
    sub: '触发条件与黑名单',
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
    <aside ref="stepsRef" class="agent__steps">
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
      </button>
    </aside>

    <section class="agent__main">
      <div :key="step.key" class="agent__view">
        <component :is="step.view" />
      </div>
    </section>

    <aside class="agent__test">
      <TestChat :suggestions="SUGGESTIONS[step.key] ?? SUGGESTIONS.knowledge!" />
    </aside>
  </div>
</template>

<style scoped>
.agent {
  display: flex;
  gap: 0;
  height: 100%;
  min-height: 0;
}

.agent__steps {
  position: relative;
  display: flex;
  flex: 0 0 198px;
  flex-direction: column;
  gap: 3px;
  padding: 14px 10px;
  background: rgb(255 255 255 / 48%);
  border-right: 0.5px solid rgb(0 0 0 / 6.5%);
  backdrop-filter: blur(22px) saturate(160%);
}

.agent__indicator {
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

.agent__group {
  padding: 2px 10px 8px;
  font-size: 11px;
  font-weight: 600;
  color: var(--ap-label-3);
  letter-spacing: 0.02em;
}

.agent__step {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 8px 10px;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 10px;
  transition: background-color var(--ap-dur-fast) linear;
}

.agent__step:hover:not(.on) {
  background: rgb(0 0 0 / 3.5%);
}

.agent__num {
  display: inline-flex;
  flex: 0 0 22px;
  align-items: center;
  justify-content: center;
  height: 22px;
  font-size: 11.5px;
  font-weight: 600;
  color: var(--ap-label-3);
  background: rgb(0 0 0 / 5.5%);
  border-radius: 50%;
  transition:
    background-color var(--ap-dur-fast),
    color var(--ap-dur-fast);
}

.agent__step.on .agent__num {
  color: #fff;
  background: var(--ap-accent);
  box-shadow: 0 2px 6px rgb(0 122 255 / 28%);
}

.agent__step-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.agent__step-text b {
  font-size: 13px;
  font-weight: 500;
  line-height: 1.3;
  color: var(--ap-label-2);
}

.agent__step.on .agent__step-text b {
  font-weight: 600;
  color: var(--ap-label);
}

.agent__step-text small {
  margin-top: 1px;
  overflow: hidden;
  font-size: 11px;
  color: var(--ap-label-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent__main {
  flex: 1;
  min-width: 0;
  padding: 22px 28px 32px;
  overflow: auto;
  background: transparent;
}

.agent__view {
  max-width: 840px;
  margin: 0 auto;
  animation: ap-fade-up 200ms var(--ap-ease-out) both;
}

.agent__test {
  flex: 0 0 336px;
  overflow: hidden;
  background: rgb(246 246 249 / 86%);
  border-left: 0.5px solid rgb(0 0 0 / 7%);
  backdrop-filter: blur(24px) saturate(180%);
}

@media (width <= 1280px) {
  .agent__test {
    flex-basis: 292px;
  }
}

@media (width <= 1080px) {
  .agent__test {
    display: none;
  }
}

@media (width <= 760px) {
  .agent {
    flex-direction: column;
  }

  .agent__steps {
    flex: 0 0 auto;
    flex-direction: row;
    padding: 8px;
    overflow-x: auto;
  }

  .agent__group,
  .agent__step-text small {
    display: none;
  }

  .agent__step {
    white-space: nowrap;
  }

  .agent__main {
    padding: 14px;
  }
}
</style>
