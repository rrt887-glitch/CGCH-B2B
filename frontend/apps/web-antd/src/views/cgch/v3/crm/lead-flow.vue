<script lang="ts" setup>
/** 线索流转规则（与原网站一致）：默认规则说明；自定义规则即将上线 */
import { ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

const mode = ref<'custom' | 'default'>('default');
const RULES = [
  { title: '进线自动建档', icon: 'lucide:user-plus', desc: '当 AI 将进线识别为销售询盘或商务合作时，系统自动创建或关联线索、客户及商机。' },
  { title: '客户信息自动补充', icon: 'lucide:file-pen-line', desc: '根据沟通中识别到的公司、联系人及联系方式等信息，持续完善客户资料。' },
  { title: '商机阶段自动推进', icon: 'lucide:trending-up', desc: '根据沟通进展及信息完善情况，自动推进商机阶段；人工锁定阶段后，不再自动推进。' },
];
</script>

<template>
  <div class="lf">
    <div class="lf__seg">
      <button type="button" :class="{ on: mode === 'default' }" @click="mode = 'default'">使用默认规则</button>
      <button type="button" :class="{ on: mode === 'custom' }" @click="mode = 'custom'">使用自定义规则</button>
    </div>
    <div v-if="mode === 'default'" class="mt-4 grid gap-3">
      <div v-for="(r, i) in RULES" :key="r.title" class="lf__card">
        <span class="lf__no">{{ i + 1 }}</span>
        <IconifyIcon :icon="r.icon" class="size-5 flex-none text-blue-600" />
        <div>
          <div class="font-semibold text-gray-900">{{ r.title }}</div>
          <div class="mt-1 text-sm text-gray-500">{{ r.desc }}</div>
        </div>
      </div>
    </div>
    <div v-else class="lf__soon">
      <IconifyIcon icon="lucide:hourglass" class="mb-2 size-8 text-gray-300" />
      <div>自定义规则即将上线，当前请先使用默认规则</div>
    </div>
  </div>
</template>

<style scoped>
.lf {
  max-width: 760px;
  padding: 4px 0 12px;
}

.lf__seg {
  display: inline-flex;
  padding: 3px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.lf__seg button {
  padding: 4px 14px;
  font-size: 13px;
  color: #6b7280;
  cursor: pointer;
  background: none;
  border: 0;
  border-radius: 6px;
}

.lf__seg button.on {
  color: #2563EB;
  background: #EFF4FF;
}

.lf__card {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 16px 18px;
  background: #fff;
  border: 1px solid #eceef3;
  border-radius: 12px;
}

.lf__no {
  flex: none;
  width: 22px;
  height: 22px;
  font-size: 12px;
  font-weight: 700;
  line-height: 22px;
  color: #fff;
  text-align: center;
  background: #2563EB;
  border-radius: 50%;
}

.lf__soon {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56px 0;
  margin-top: 16px;
  font-size: 14px;
  color: #6b7280;
  border: 1px dashed #e5e7eb;
  border-radius: 12px;
}
</style>
