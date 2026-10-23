import type { RouteRecordRaw } from 'vue-router';

/**
 * 草根出海主导航对应的六个页面（与原网站 v3 版一致）。
 * 左侧图标导航见 layouts/basic.vue；页面内的子功能通过「页面菜单」或分步标签切换，地址带 ?tab= 参数。
 */
const routes: RouteRecordRaw[] = [
  {
    name: 'CgchV3',
    path: '/v3',
    meta: { title: '草根出海', hideInMenu: true },
    children: [
      {
        name: 'CgchV3Dashboard',
        path: 'dashboard',
        component: () => import('#/views/cgch/v3/dashboard/index.vue'),
        meta: { title: '看板', affixTab: true },
      },
      {
        name: 'CgchV3Conversation',
        path: 'conversation',
        component: () => import('#/views/cgch/v3/conversation/index.vue'),
        meta: { title: '会话' },
      },
      {
        name: 'CgchV3Crm',
        path: 'crm',
        component: () => import('#/views/cgch/v3/crm/index.vue'),
        meta: { title: 'CRM' },
      },
      {
        name: 'CgchV3Agent',
        path: 'agent',
        component: () => import('#/views/cgch/v3/agent/index.vue'),
        meta: { title: '智能体' },
      },
      {
        name: 'CgchV3Channel',
        path: 'channel',
        component: () => import('#/views/cgch/config/channels/index.vue'),
        meta: { title: '渠道' },
      },
      {
        name: 'CgchV3Settings',
        path: 'settings',
        component: () => import('#/views/cgch/v3/settings/index.vue'),
        meta: { title: '设置' },
      },
    ],
  },
];

export default routes;
