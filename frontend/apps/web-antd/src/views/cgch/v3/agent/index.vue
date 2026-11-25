<script lang="ts" setup>
/**
 * 智能体：左侧 1～5 步（毛玻璃步骤栏 + 选中块弹簧滑动），中间配置带平滑过渡，
 * 右侧测试对话全程可用（1～5 步均可随时测试数字员工回复效果）。
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
  { key: 'knowledge', label: '知识库', view: lazy(() => import('./knowledge.vue')) },
  { key: 'persona', label: '角色人设', view: lazy(() => import('./persona.vue')) },
  { key: 'skills', label: '配技能', view: lazy(() => import('./skills.vue')) },
  { key: 'scripts', label: '教话术', view: lazy(() => import('./scripts.vue')) },
  { key: 'handoff', label: '转人工', view: lazy(() => import('./handoff.vue')) },
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
      <div class="agent__group">配置步骤</div>
      <button
        v-for="(s, i) in STEPS"
        :key="s.key"
        type="button"
        class="agent__step"
        :class="{ on: s.key === step.key }"
        @click="goStep(s.key)"
      >
        <span class="agent__num">{{ i + 1 }}</span>
        <span>{{ s.label }}</span>
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
  flex: 0 0 184px;
  flex-direction: column;
  gap: 2px;
  padding: 12px 8px;
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
  background: var(--ap-accent-soft);
  border-radius: 7px;
}

.agent__group {
  padding: 2px 10px 6px;
  font-size: 11px;
  font-weight: 500;
  color: var(--ap-label-3);
}

.agent__step {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 9px;
  align-items: center;
  height: 34px;
  padding: 0 10px;
  font-size: 13px;
  font-weight: 500;
  color: var(--ap-label-2);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 7px;
  transition:
    color var(--ap-dur-fast) linear,
    background-color var(--ap-dur-fast) linear;
}

.agent__step:hover:not(.on) {
  color: var(--ap-label);
  background: rgb(0 0 0 / 4%);
}

.agent__step.on {
  font-weight: 600;
  color: var(--ap-accent);
}

.agent__num {
  display: inline-flex;
  flex: 0 0 20px;
  align-items: center;
  justify-content: center;
  height: 20px;
  font-size: 11px;
  font-weight: 600;
  color: var(--ap-label-3);
  background: rgb(0 0 0 / 5%);
  border-radius: 50%;
  transition:
    background-color var(--ap-dur-fast),
    color var(--ap-dur-fast);
}

.agent__step.on .agent__num {
  color: #fff;
  background: var(--ap-accent);
}

.agent__main {
  flex: 1;
  min-width: 0;
  padding: 20px 24px;
  overflow: auto;
  background: var(--ap-surface);
}

.agent__view {
  animation: ap-fade-up 200ms var(--ap-ease-out) both;
}

.agent__test {
  flex: 0 0 316px;
  padding: 16px;
  overflow: hidden;
  background: var(--ap-bg-subbar);
  border-left: 0.5px solid var(--ap-separator);
}

@media (width <= 1280px) {
  .agent__test {
    flex-basis: 270px;
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

  .agent__group {
    display: none;
  }

  .agent__step {
    white-space: nowrap;
  }
}
</style>
