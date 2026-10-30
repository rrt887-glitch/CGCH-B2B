<script lang="ts" setup>
import { computed } from 'vue';

import { useAntdDesignTokens } from '@vben/hooks';
import { preferences, usePreferences } from '@vben/preferences';

import { App, ConfigProvider, theme } from 'ant-design-vue';

import { antdLocale } from '#/locales';

defineOptions({ name: 'App' });

const { isDark } = usePreferences();
const { tokens } = useAntdDesignTokens();

const tokenTheme = computed(() => {
  const algorithm = isDark.value
    ? [theme.darkAlgorithm]
    : [theme.defaultAlgorithm];

  // antd 紧凑模式算法
  if (preferences.app.compact) {
    algorithm.push(theme.compactAlgorithm);
  }

  // 清爽专业：文字、边框、画布颜色与字体统一（设计稿「风格规范」）；深色模式沿用算法默认值
  const brand = isDark.value
    ? {}
    : {
        colorText: '#111827',
        colorTextSecondary: '#4B5563',
        colorTextTertiary: '#6B7280',
        colorBorder: '#E5E7EB',
        colorBorderSecondary: '#F1F2F4',
        colorBgLayout: '#F6F7F9',
        colorSuccess: '#15803D',
        colorWarning: '#B45309',
        colorError: '#B91C1C',
      };
  return {
    algorithm,
    token: {
      ...tokens,
      ...brand,
      colorPrimary: '#2563EB',
      borderRadius: 8,
      borderRadiusLG: 12,
      borderRadiusSM: 6,
      controlHeight: 34,
      fontSize: 14,
      fontFamily: "Inter, -apple-system, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Noto Sans SC', system-ui, sans-serif",
    },
  };
});
</script>

<template>
  <ConfigProvider :locale="antdLocale" :theme="tokenTheme">
    <App>
      <RouterView />
    </App>
  </ConfigProvider>
</template>
