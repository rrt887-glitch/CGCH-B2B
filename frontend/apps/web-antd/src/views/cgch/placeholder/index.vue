<script lang="ts" setup>
/**
 * 通用占位页：菜单已建好、功能尚未开发的页面统一使用这个组件。
 * 按路由路径最后一段查找本页计划实现的功能清单。
 */
import { computed } from 'vue';
import { useRoute } from 'vue-router';

import { Page } from '@vben/common-ui';

import { Card, Tag } from 'ant-design-vue';

const route = useRoute();

/** 页面路径最后一段 → 计划功能、所在阶段 */
const PLANS: Record<string, { features: string[]; phase: number }> = {
  overview: {
    phase: 5,
    features: ['转化关键指标与环比', '询盘到赢单的阶段漏斗', '日期范围筛选与导出'],
  },
  schedule: { phase: 5, features: ['月历与当日时间轴', '新增与编辑日程', '跟进提醒'] },
  notice: { phase: 5, features: ['系统与业务通知列表', '已读 / 未读'] },
  'customer-overview': {
    phase: 5,
    features: ['客户视图与进度看板切换', '公司客户 / 个人客户', '最后沟通间隔'],
  },
  im: {
    phase: 3,
    features: ['多渠道统一会话列表', '消息流与客户侧栏', 'AI 托管与人工接管切换'],
  },
  mail: {
    phase: 3,
    features: ['多邮箱切换', '收件箱 / 未回复 / 已发送 / 草稿 / 已删除', '写邮件与 AI 草拟'],
  },
  report: { phase: 5, features: ['邮件工作报告', '客户 360 洞察'] },
  simulate: { phase: 4, features: ['模拟客户来信', '实时查看 AI 回复效果'] },
  chat: { phase: 5, features: ['对话式 CRM 助手', '会话与任务列表'] },
  'bg-check': { phase: 5, features: ['选择客户发起背调', '背调报告与客户评级'] },
  reactivate: { phase: 5, features: ['筛选沉睡客户', '批量外联任务与进度'] },
  profile: {
    phase: 4,
    features: ['数字员工名称与人设', '默认答复语种与个性', '人工介入后 AI 接待方式', '对话预览'],
  },
  knowledge: {
    phase: 4,
    features: ['企业介绍与产品资料', '绑定官网自动抓取', 'PDF 批量上传与解析'],
  },
  cases: { phase: 4, features: ['成交案例模板下载', '上传案例供 AI 学习'] },
  skills: {
    phase: 4,
    features: ['询盘自动接待', '线索自动建档', '人工转接', '预估报价', '智能跟进'],
  },
  forbidden: { phase: 4, features: ['系统规则与自定义规则', '按渠道生效'] },
  channels: {
    phase: 3,
    features: ['邮箱（IMAP/SMTP、Gmail、Outlook）', 'WhatsApp', 'Facebook / Instagram', '账号启停'],
  },
  'work-time': { phase: 3, features: ['按渠道设置 AI 接待时段', '重复规则'] },
  templates: { phase: 3, features: ['WhatsApp 消息模板', '审核状态'] },
  disk: { phase: 4, features: ['企业云盘与个人云盘', '文件夹管理'] },
  'send-policy': { phase: 3, features: ['按账号或域名分类来信', '跳过内部与无效来信'] },
};

const key = computed(() => route.path.split('/').filter(Boolean).pop() ?? '');
const plan = computed(() => PLANS[key.value]);
const title = computed(() => String(route.meta?.title ?? key.value));
</script>

<template>
  <Page auto-content-height>
    <Card :title="title">
      <template #extra>
        <Tag v-if="plan" color="blue">第 {{ plan.phase }} 阶段开发</Tag>
        <Tag v-else>规划中</Tag>
      </template>
      <p class="mb-3 text-gray-500">此页面已建好菜单入口，功能尚在开发。</p>
      <ul v-if="plan" class="list-disc pl-5 leading-7">
        <li v-for="item in plan.features" :key="item">{{ item }}</li>
      </ul>
    </Card>
  </Page>
</template>
