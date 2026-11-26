import { createApp, watchEffect } from 'vue';
import VueDOMPurifyHTML from 'vue-dompurify-html';

import { registerAccessDirective } from '@vben/access';
import { registerLoadingDirective } from '@vben/common-ui/es/loading';
import { preferences } from '@vben/preferences';
import { initStores } from '@vben/stores';
import '@vben/styles';
import '@vben/styles/antd';
import './styles/apple/tokens.css';
import './styles/apple/components.css';
import './styles/apple/motion.css';
import './styles/workspace-design.css';
// 图标打包进网站，不再从国外图标网站临时下载（见 build/offline-icons.ts）
import 'virtual:cgch-offline-icons';

import { useTitle } from '@vueuse/core';

import { $t, setupI18n } from '#/locales';
import { bindFormCreateApp } from '#/plugins/form-create/lazy';
import { registerMotionDirectives } from '#/utils/motion';
import { setupHoverPrefetch } from '#/utils/prefetch';
import { setupSegmentedKeyboard } from '#/utils/segmented-enter';
import { setupVxeTableA11y } from '#/utils/vxe-a11y';
import { initComponentAdapter } from './adapter/component';
import { initSetupVbenForm } from './adapter/form';
import App from './app.vue';
import { router } from './router';

async function bootstrap(namespace: string) {
  // 初始化组件适配器
  await initComponentAdapter();

  // 初始化表单组件
  await initSetupVbenForm();

  // // 设置弹窗的默认配置
  // setDefaultModalProps({
  //   fullscreenButton: false,
  // });
  // // 设置抽屉的默认配置
  // setDefaultDrawerProps({
  //   zIndex: 1020,
  // });

  const app = createApp(App);
  app.use(VueDOMPurifyHTML);

  // 注册v-loading指令
  registerLoadingDirective(app, {
    loading: 'loading', // 在这里可以自定义指令名称，也可以明确提供false表示不注册这个指令
    spinning: 'spinning',
  });

  // 国际化 i18n 配置
  await setupI18n(app);

  // 配置 pinia-store
  await initStores(app, { namespace });

  // 安装权限指令
  registerAccessDirective(app);

  // 注册苹果风格动效指令（v-reveal）
  registerMotionDirectives(app);

  // 初始化 tippy
  const { initTippy } = await import('@vben/common-ui/es/tippy');
  initTippy(app);

  // 配置路由及路由守卫
  app.use(router);

  // formCreate：只在流程审批、表单构建页面用到，进入这些页面前再加载（见 router/guard.ts）
  bindFormCreateApp(app);

  // 配置Motion插件
  const { MotionPlugin } = await import('@vben/plugins/motion');
  app.use(MotionPlugin);

  // 动态更新标题
  watchEffect(() => {
    if (preferences.app.dynamicTitle) {
      const routeTitle = router.currentRoute.value.meta?.title;
      const pageTitle =
        (routeTitle ? `${$t(routeTitle)} - ` : '') + preferences.app.name;
      useTitle(pageTitle);
    }
  });

  app.mount('#app');

  // 表格工具栏 / 分页按钮只有图标字形，读屏念不出操作名，这里补 aria-label
  setupVxeTableA11y();

  // Segmented 是原生 radio，回车不会选中，这里统一补上
  setupSegmentedKeyboard();

  // 鼠标悬停菜单时预取页面分包：跨境链路下能省掉 1 个往返的等待
  setupHoverPrefetch();
}

export { bootstrap };
