<script lang="ts" setup>
/** 数字滚动：从上一次的值缓动到新值（KPI、计数徽标） */
import { computed, toRef } from 'vue';

import { useCountUp } from '#/utils/motion';

const props = withDefaults(
  defineProps<{
    decimals?: number;
    duration?: number;
    placeholder?: string;
    suffix?: string;
    value?: null | number;
  }>(),
  { decimals: 0, duration: 700, placeholder: '—', suffix: '' },
);

const display = useCountUp(toRef(props, 'value'), props.duration);
const text = computed(() => {
  if (props.value === null || props.value === undefined) return props.placeholder;
  return (
    display.value.toLocaleString(undefined, {
      maximumFractionDigits: props.decimals,
      minimumFractionDigits: props.decimals,
    }) + props.suffix
  );
});
</script>

<template>
  <span class="ap-number">{{ text }}</span>
</template>

<style scoped>
.ap-number {
  font-variant-numeric: tabular-nums;
}
</style>
