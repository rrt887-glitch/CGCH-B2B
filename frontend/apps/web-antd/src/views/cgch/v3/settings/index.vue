<script lang="ts" setup>
/** 设置：macOS 系统设置风格分组二级侧栏 */
import type { HubGroup } from '../components/types';

import SideHub from '../components/side-hub.vue';

defineOptions({ name: 'CgchV3Settings' });

const groups: HubGroup[] = [
  {
    label: 'AI 与团队',
    items: [
      {
        key: 'ai',
        label: 'AI 接待设置',
        icon: 'lucide:sparkles',
        load: () => import('#/views/cgch/agent/profile/index.vue'),
        codes: ['channel:ai:config'],
      },
      {
        key: 'members',
        label: '成员',
        icon: 'lucide:users',
        load: () => import('./members.vue'),
        codes: ['system:user:create'],
      },
      {
        key: 'routing',
        label: '消息分流设置',
        icon: 'lucide:git-branch',
        load: () => import('./routing.vue'),
      },
    ],
  },
  {
    label: '模板与物料',
    items: [
      {
        key: 'cloud',
        label: '云盘文件',
        icon: 'lucide:folder',
        load: () => import('./cloud.vue'),
      },
      {
        key: 'templates',
        label: '消息模板',
        icon: 'lucide:message-square',
        load: () => import('#/views/cgch/config/templates/index.vue'),
      },
      {
        key: 'emailTemplates',
        label: '邮件模板',
        icon: 'lucide:mail',
        load: () => import('./email-templates.vue'),
      },
      {
        key: 'quickReplies',
        label: '快捷回复',
        icon: 'lucide:zap',
        load: () => import('./quick-replies.vue'),
      },
      {
        key: 'materials',
        label: '我的物料',
        icon: 'lucide:book-open',
        load: () => import('./my-materials.vue'),
      },
    ],
  },
  {
    label: '偏好',
    items: [
      {
        key: 'notifications',
        label: '通知设置',
        icon: 'lucide:bell',
        load: () => import('./notifications.vue'),
      },
      {
        key: 'translate',
        label: '翻译设置',
        icon: 'lucide:languages',
        load: () => import('./translate.vue'),
      },
    ],
  },
];
</script>

<template>
  <SideHub :groups="groups" menu-label="设置菜单" />
</template>
