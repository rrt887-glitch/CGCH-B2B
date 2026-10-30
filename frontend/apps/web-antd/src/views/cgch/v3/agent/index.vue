<script lang="ts" setup>
/**
 * 智能体：左侧 1～5 步（知识库、角色人设、配技能、教话术、转人工），中间为配置，
 * 右侧随时测试数字员工的回复效果（配技能页不显示）。与原网站布局一致。
 */
import { computed, defineAsyncComponent, markRaw } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Spin } from 'ant-design-vue';

import TestChat from './test-chat.vue';

defineOptions({ name: 'CgchV3Agent' });

const route = useRoute();
const router = useRouter();

const lazy = (loader: () => Promise<any>) => markRaw(defineAsyncComponent({ loader, loadingComponent: Spin, delay: 120 }));

const STEPS = [
  { key: 'knowledge', label: '知识库', view: lazy(() => import('./knowledge.vue')), chat: true },
  { key: 'persona', label: '角色人设', view: lazy(() => import('./persona.vue')), chat: true },
  { key: 'skills', label: '配技能', view: lazy(() => import('./skills.vue')), chat: false },
  { key: 'scripts', label: '教话术', view: lazy(() => import('./scripts.vue')), chat: true },
  { key: 'handoff', label: '转人工', view: lazy(() => import('./handoff.vue')), chat: true },
];

const SUGGESTIONS: Record<string, string[]> = {
  knowledge: ['Do you have certificates for the products?', 'Could you send me a product catalog first?', 'Which countries do you mainly export to?'],
  persona: ['Where do your product materials come from?', 'Is there a discount if I order 100 units?', 'Do your products support customization?'],
  scripts: ['Hi, I am interested in your products.', 'What is your MOQ?', 'Can you share the price list?'],
  handoff: ['I want to talk to a real person.', 'Please send me a sample.', 'I am very disappointed with the last order.'],
};

const step = computed(() => STEPS.find((s) => s.key === route.query.tab) ?? STEPS[0]!);

function goStep(key: string) {
  if (key !== step.value.key) router.replace({ query: { tab: key } });
}
</script>

<template>
  <div class="agent">
    <aside class="agent__steps">
      <button
        v-for="(s, i) in STEPS"
        :key="s.key"
        type="button"
        class="agent__step"
        :class="{ on: s.key === step.key }"
        @click="goStep(s.key)"
      >
        <span class="agent__num">{{ i + 1 }}</span>{{ s.label }}
      </button>
    </aside>
    <section class="agent__main">
      <component :is="step.view" :key="step.key" />
    </section>
    <aside v-if="step.chat" class="agent__test">
      <TestChat :suggestions="SUGGESTIONS[step.key] ?? SUGGESTIONS.knowledge!" />
    </aside>
  </div>
</template>

<style scoped>
.agent {
  display: flex;
  gap: 6px;
  height: calc(100vh - 24px);
}

.agent__steps {
  display: flex;
  flex: 0 0 148px;
  flex-direction: column;
  gap: 4px;
  padding: 52px 8px 12px;
  background: rgb(255 255 255 / 75%);
  border-radius: 12px;
}

.agent__step {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 8px 12px;
  font-size: 14px;
  color: #1f2937;
  text-align: left;
  cursor: pointer;
  background: none;
  border: 0;
  border-radius: 8px;
}

.agent__step:hover {
  background: #F3F4F6;
}

.agent__step.on {
  color: #2563EB;
  background: #EFF4FF;
}

.agent__num {
  display: inline-flex;
  flex: 0 0 18px;
  align-items: center;
  justify-content: center;
  height: 18px;
  font-size: 11px;
  color: #6b7280;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 50%;
}

.agent__step.on .agent__num {
  color: #2563EB;
  border-color: #2563EB;
}

.agent__main {
  flex: 1;
  min-width: 0;
  overflow: auto;
  background: #fff;
  border-radius: 12px;
}

.agent__test {
  flex: 0 0 340px;
  overflow: hidden;
  background: #fff;
  border-radius: 12px;
}

@media (width <= 1200px) {
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

  .agent__step {
    white-space: nowrap;
  }
}
</style>
