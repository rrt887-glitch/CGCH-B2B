import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

/** 开源框架自带的分析页、工作台已移除，首页为草根出海看板（见 cgch.ts） */
const routes: RouteRecordRaw[] = [
  {
    name: 'Profile',
    path: '/profile',
    component: () => import('#/views/_core/profile/index.vue'),
    meta: {
      icon: 'ant-design:profile-outlined',
      title: $t('ui.widgets.profile'),
      hideInMenu: true,
    },
  },
];

export default routes;
