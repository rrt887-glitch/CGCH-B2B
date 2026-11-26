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
        <span class="lf__icon">
          <IconifyIcon :icon="r.icon" class="size-5" />
        </span>
        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-2">
            <span class="text-[14.5px] font-semibold text-[#1D1D1F]">{{ r.title }}</span>
            <span class="ap-crm-pill ap-crm-pill--blue">规则 0{{ i + 1 }}</span>
          </div>
          <div class="mt-1 text-[13px] leading-relaxed text-[#6E6E73]">{{ r.desc }}</div>
        </div>
      </div>
    </div>
    <div v-else class="lf__soon">
      <div class="mb-2.5 grid size-12 place-items-center rounded-2xl bg-[#EEF4FF] text-[#007AFF]">
        <IconifyIcon icon="lucide:sparkles" class="size-6" />
      </div>
      <div class="text-[14px] font-semibold text-[#1D1D1F]">自定义流转规则即将上线</div>
      <div class="mt-1 text-xs text-[#86868B]">当前已启用 AI 智能建档与阶段自动推进默认规则</div>
    </div>
  </div>
</template>

<style scoped>
.lf {
  max-width: 760px;
  padding: 6px 0 12px;
}

.lf__seg {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  background: #f2f2f7;
  border-radius: 10px;
}

.lf__seg button {
  height: 30px;
  padding: 0 14px;
  font-size: 12.5px;
  font-weight: 500;
  color: #6e6e73;
  cursor: pointer;
  background: none;
  border: 0;
  border-radius: 8px;
  transition: all var(--ap-dur-fast);
}

.lf__seg button.on {
  font-weight: 600;
  color: #1d1d1f;
  background: #fff;
  box-shadow:
    0 1px 3px rgb(0 0 0 / 8%),
    0 0 0 0.5px rgb(0 0 0 / 4%);
}

.lf__card {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  padding: 16px 18px;
  background: #f8f8fb;
  border: 0.5px solid rgb(0 0 0 / 6%);
  border-radius: 16px;
}

.lf__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 40px;
  height: 40px;
  color: #007aff;
  background: #e8f2ff;
  border-radius: 12px;
}

.lf__soon {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 52px 20px;
  margin-top: 16px;
  text-align: center;
  background: #f8f8fb;
  border: 1px dashed rgb(0 0 0 / 10%);
  border-radius: 16px;
}
</style>
