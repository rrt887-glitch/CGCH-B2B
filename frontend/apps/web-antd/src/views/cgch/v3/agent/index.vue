<script lang="ts" setup>
/**
 * 智能体：按 1～5 步配置数字员工（知识库、角色人设、配技能、教话术、转人工），右侧随时测试回复效果。
 */
import { computed, defineAsyncComponent, markRaw } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Spin, TabPane, Tabs } from 'ant-design-vue';

import TestChat from './test-chat.vue';

defineOptions({ name: 'CgchV3Agent' });

const route = useRoute();
const router = useRouter();

const lazy = (loader: () => Promise<any>) => markRaw(defineAsyncComponent({ loader, loadingComponent: Spin, delay: 120 }));
const soon = lazy(() => import('../components/coming-soon.vue'));

interface Sub {
  key: string;
  label: string;
  view: any;
}
interface Step {
  key: string;
  label: string;
  subs: Sub[];
}

const STEPS: Step[] = [
  { key: 'knowledge', label: '知识库', subs: [{ key: 'main', label: '', view: lazy(() => import('#/views/cgch/agent/knowledge/index.vue')) }] },
  { key: 'persona', label: '角色人设', subs: [{ key: 'main', label: '', view: lazy(() => import('#/views/cgch/agent/profile/index.vue')) }] },
  { key: 'skills', label: '配技能', subs: [{ key: 'main', label: '', view: soon }] },
  {
    key: 'scripts',
    label: '教话术',
    subs: [
      { key: 'learn', label: '学习真人销售', view: soon },
      { key: 'faq', label: 'FAQ', view: lazy(() => import('#/views/cgch/agent/cases/index.vue')) },
      { key: 'opening', label: '固定开场白', view: soon },
      { key: 'boundary', label: '言语边界', view: lazy(() => import('#/views/cgch/agent/forbidden/index.vue')) },
    ],
  },
  {
    key: 'handoff',
    label: '转人工',
    subs: [
      { key: 'intent', label: '根据客户意图', view: soon },
      { key: 'condition', label: '根据客户条件', view: soon },
      { key: 'worktime', label: 'AI 工作时间', view: lazy(() => import('#/views/cgch/config/work-time/index.vue')) },
      { key: 'blacklist', label: 'AI 黑名单', view: lazy(() => import('#/views/cgch/config/send-policy/index.vue')) },
      { key: 'takeover', label: '人工介入 AI 托管', view: soon },
    ],
  },
];

const SUGGESTIONS = [
  'Which countries do you mainly export to?',
  'Do you support OEM and ODM cooperation?',
  'What is the sample request process?',
];

const step = computed(() => STEPS.find((s) => s.key === route.query.tab) ?? STEPS[0]!);
const subKey = computed(() => {
  const k = String(route.query.sub || '');
  return step.value.subs.some((s) => s.key === k) ? k : step.value.subs[0]!.key;
});
const sub = computed(() => step.value.subs.find((s) => s.key === subKey.value)!);

function goStep(key: string) {
  router.replace({ query: { tab: key } });
}
function goSub(key: number | string) {
  router.replace({ query: { tab: step.value.key, sub: String(key) } });
}
</script>

<template>
  <div class="v3-agent">
    <section class="v3-agent__main">
      <div class="v3-agent__steps">
        <button
          v-for="(s, i) in STEPS"
          :key="s.key"
          type="button"
          class="v3-agent__step"
          :class="{ on: s.key === step.key }"
          @click="goStep(s.key)"
        >
          <span class="v3-agent__num">{{ i + 1 }}</span>
          {{ s.label }}
        </button>
      </div>
      <Tabs v-if="step.subs.length > 1" :active-key="subKey" class="px-4" @change="goSub">
        <TabPane v-for="s in step.subs" :key="s.key" :tab="s.label" />
      </Tabs>
      <div class="v3-agent__body">
        <component :is="sub.view" :key="`${step.key}-${sub.key}`" />
      </div>
    </section>
    <aside class="v3-agent__test">
      <TestChat :suggestions="SUGGESTIONS" />
    </aside>
  </div>
</template>

<style scoped>
.v3-agent {
  display: flex;
  gap: 8px;
  height: calc(100vh - 16px);
}

.v3-agent__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  background: #fff;
  border-radius: 12px;
}

.v3-agent__steps {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
  padding: 14px 16px 6px;
}

.v3-agent__step {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 6px 14px;
  font-size: 14px;
  color: #4b5563;
  cursor: pointer;
  background: #f3f5fa;
  border: 0;
  border-radius: 999px;
}

.v3-agent__step.on {
  color: #155dfc;
  background: #eaf0fe;
}

.v3-agent__num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  font-size: 11px;
  color: #fff;
  background: #c3cad8;
  border-radius: 50%;
}

.v3-agent__step.on .v3-agent__num {
  background: #155dfc;
}

.v3-agent__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.v3-agent__test {
  flex: 0 0 340px;
  overflow: hidden;
  background: #fff;
  border-radius: 12px;
}

@media (width <= 1200px) {
  .v3-agent__test {
    display: none;
  }
}
</style>
