<script lang="ts" setup>
/** 骨架屏：页面 / 子页面加载时代替转圈（variant: page | list | cards） */
withDefaults(defineProps<{ rows?: number; variant?: 'cards' | 'list' | 'page' }>(), {
  rows: 6,
  variant: 'page',
});
</script>

<template>
  <div class="ap-skel" :class="`ap-skel--${variant}`" aria-busy="true" aria-label="加载中">
    <template v-if="variant !== 'list'">
      <div class="ap-skeleton ap-skel__eyebrow"></div>
      <div class="ap-skeleton ap-skel__title"></div>
    </template>
    <div v-if="variant === 'cards'" class="ap-skel__cards">
      <div v-for="i in 4" :key="i" class="ap-skeleton ap-skel__card"></div>
    </div>
    <div class="ap-skel__rows">
      <div v-for="i in rows" :key="i" class="ap-skel__row">
        <div class="ap-skeleton ap-skel__avatar"></div>
        <div class="ap-skel__lines">
          <div class="ap-skeleton" :style="{ width: `${42 + ((i * 17) % 40)}%` }"></div>
          <div class="ap-skeleton" :style="{ width: `${60 + ((i * 11) % 30)}%` }"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ap-skel {
  padding: 8px 4px;
  animation: ap-fade 300ms 120ms both;
}

.ap-skel__eyebrow {
  width: 90px;
  height: 12px;
}

.ap-skel__title {
  width: 220px;
  height: 28px;
  margin: 10px 0 24px;
}

.ap-skel__cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.ap-skel__card {
  height: 112px;
  border-radius: var(--ap-radius-lg);
}

.ap-skel__rows {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.ap-skel__row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.ap-skel__avatar {
  flex: none;
  width: 36px;
  height: 36px;
  border-radius: 50%;
}

.ap-skel__lines {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
}

.ap-skel__lines .ap-skeleton {
  height: 11px;
}
</style>
