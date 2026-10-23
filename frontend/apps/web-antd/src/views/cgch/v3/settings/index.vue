<script lang="ts" setup>
/** 设置：订阅与用量、AI 接待设置（大模型、自动回复模式）、成员与部门、云盘文件、消息模板、邮件模板、消息分流、通知、翻译，通过「设置菜单」切换 */
import type { HubGroup } from '../components/types';

import MenuHub from '../components/menu-hub.vue';

defineOptions({ name: 'CgchV3Settings' });

const soon = () => import('../components/coming-soon.vue');
const groups: HubGroup[] = [
  {
    label: '设置',
    items: [
      { key: 'usage', label: '订阅与用量', load: () => import('#/views/cgch/config/credits/index.vue') },
      { key: 'ai', label: 'AI 接待设置', load: () => import('#/views/cgch/agent/profile/index.vue') },
      { key: 'members', label: '成员与部门', load: () => import('#/views/system/user/index.vue') },
      { key: 'cloud', label: '云盘文件', load: () => import('./cloud.vue') },
      { key: 'templates', label: '消息模板', load: () => import('#/views/cgch/config/templates/index.vue') },
      { key: 'emailTemplates', label: '邮件模板', load: soon },
      { key: 'routing', label: '消息分流设置', load: soon },
      { key: 'notifications', label: '通知设置', load: soon },
      { key: 'translate', label: '翻译设置', load: soon },
    ],
  },
];
</script>

<template>
  <MenuHub :groups="groups" menu-label="设置菜单" />
</template>
