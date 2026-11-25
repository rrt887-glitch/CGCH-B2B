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

  // 苹果风格：文字、分隔线、画布、系统色与字体统一（设计稿「苹果风格 · 规范与动效」）；深色模式沿用算法默认值
  const brand = isDark.value
    ? {}
    : {
        colorText: '#1D1D1F',
        colorTextSecondary: '#6E6E73',
        colorTextTertiary: '#86868B',
        colorTextQuaternary: '#A1A1A6',
        colorBorder: '#D2D2D7',
        colorBorderSecondary: '#EBEBF0',
        colorSplit: 'rgba(0,0,0,0.08)',
        colorBgLayout: '#F5F5F7',
        colorFillTertiary: 'rgba(118,118,128,0.08)',
        colorFillSecondary: 'rgba(118,118,128,0.12)',
        colorSuccess: '#34C759',
        colorWarning: '#FF9500',
        colorError: '#FF3B30',
        colorInfo: '#007AFF',
        colorLink: '#007AFF',
        colorLinkHover: '#0A84FF',
      };
  return {
    algorithm,
    token: {
      ...tokens,
      ...brand,
      colorPrimary: '#007AFF',
      borderRadius: 10,
      borderRadiusLG: 18,
      borderRadiusSM: 8,
      borderRadiusXS: 6,
      controlHeight: 36,
      fontSize: 14,
      lineWidth: 1,
      motionEaseOut: 'cubic-bezier(0.32, 0.72, 0, 1)',
      motionEaseInOut: 'cubic-bezier(0.32, 0.72, 0, 1)',
      motionDurationMid: '0.28s',
      motionDurationSlow: '0.45s',
      boxShadow: '0 0 0 0.5px rgba(0,0,0,0.04), 0 4px 14px rgba(0,0,0,0.06)',
      boxShadowSecondary: '0 0 0 0.5px rgba(0,0,0,0.06), 0 12px 32px rgba(0,0,0,0.12)',
      fontFamily: "-apple-system, BlinkMacSystemFont, 'SF Pro Text', 'SF Pro Display', 'PingFang SC', 'Hiragino Sans GB', 'Helvetica Neue', 'Microsoft YaHei', 'Noto Sans SC', system-ui, sans-serif",
    },
    components: {
      Button: { primaryShadow: 'none', defaultShadow: 'none', dangerShadow: 'none', fontWeight: 500 },
      Segmented: { itemSelectedBg: '#FFFFFF', trackBg: 'rgba(120,120,128,0.12)', borderRadius: 9, borderRadiusSM: 7 },
      Switch: { colorPrimary: '#34C759', colorPrimaryHover: '#30B350' },
      Table: { headerBg: '#FBFBFD', headerColor: '#86868B', rowHoverBg: 'rgba(0,0,0,0.025)', borderColor: 'rgba(60,60,67,0.12)' },
      Card: { headerFontSize: 17 },
      Modal: { borderRadiusLG: 22 },
      Tag: { borderRadiusSM: 999 },
    },
  };
});
</script>

<template>
  <ConfigProvider :auto-insert-space-in-button="false" :locale="antdLocale" :theme="tokenTheme">
    <App>
      <RouterView />
    </App>
  </ConfigProvider>
</template>
