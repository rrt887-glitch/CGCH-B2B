<script lang="ts" setup>
/**
 * Spotlight 风格全局快捷跳转（⌘K / Ctrl+K）
 * 支持拼音/关键词模糊搜索模块、子页与常用操作，键盘上下键选择、回车跳转。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';
import { useAccessStore } from '@vben/stores';

const open = defineModel<boolean>('open', { default: false });

const router = useRouter();
const accessStore = useAccessStore();
const inputRef = ref<HTMLInputElement>();
const query = ref('');
const selected = ref(0);

interface CommandItem {
  group: string;
  icon: string;
  keywords: string;
  label: string;
  path: string;
  platformOnly?: boolean;
  query?: Record<string, string>;
}

const COMMANDS: CommandItem[] = [
  { group: '工作', label: '今天 · 数据分析', icon: 'lucide:activity', path: '/v3/dashboard', keywords: 'dashboard today kanban 看板 首页 数据' },
  { group: '工作', label: '今天 · 经营分析', icon: 'lucide:bar-chart-3', path: '/v3/dashboard', query: { tab: 'business' }, keywords: 'business 经营 报表 业务员' },
  { group: '工作', label: '今天 · 客户概览', icon: 'lucide:users', path: '/v3/dashboard', query: { tab: 'customers' }, keywords: 'customer overview 客户 概览' },
  { group: '工作', label: '会话', icon: 'lucide:message-circle-more', path: '/v3/conversation', keywords: 'chat im whatsapp messenger instagram 聊天 消息' },
  { group: '工作', label: '邮件', icon: 'lucide:mail', path: '/v3/conversation', query: { channel: 'mail' }, keywords: 'email mail 邮箱 收件箱' },

  { group: '客户 CRM', label: '客户列表', icon: 'lucide:users', path: '/v3/crm', query: { tab: 'customer' }, keywords: 'crm customer 客户' },
  { group: '客户 CRM', label: '线索列表', icon: 'lucide:user-plus', path: '/v3/crm', query: { tab: 'lead' }, keywords: 'lead 线索' },
  { group: '客户 CRM', label: 'CRM 智枢', icon: 'lucide:sparkles', path: '/v3/crm', query: { tab: 'salesmind' }, keywords: 'claw 背调 唤醒 research wake' },
  { group: '客户 CRM', label: '跟进日程', icon: 'lucide:calendar', path: '/v3/crm', query: { tab: 'agenda' }, keywords: 'agenda schedule 日程 跟进' },
  { group: '客户 CRM', label: '商机', icon: 'lucide:briefcase', path: '/v3/crm', query: { tab: 'business' }, keywords: 'deal pipeline 商机 看板' },
  { group: '客户 CRM', label: '订单管理', icon: 'lucide:file-text', path: '/v3/crm', query: { tab: 'order' }, keywords: 'order 订单 成交' },
  { group: '客户 CRM', label: '邮件群发', icon: 'lucide:send', path: '/v3/crm', query: { tab: 'mail-batch' }, keywords: 'batch mail 群发 营销' },
  { group: '客户 CRM', label: '公海客户', icon: 'lucide:globe', path: '/v3/crm', query: { tab: 'customer-pool' }, keywords: 'pool 公海' },
  { group: '客户 CRM', label: '客户查重', icon: 'lucide:search', path: '/v3/crm', query: { tab: 'dedup' }, keywords: 'dedup 查重 去重' },
  { group: '客户 CRM', label: 'CRM 设置', icon: 'lucide:sliders-horizontal', path: '/v3/crm', query: { tab: 'settings' }, keywords: 'tag group 标签 分组 公海规则' },

  { group: 'AI 助手', label: '知识库', icon: 'lucide:book-open', path: '/v3/agent', query: { tab: 'knowledge' }, keywords: 'knowledge 知识库 资料 文档' },
  { group: 'AI 助手', label: '角色人设', icon: 'lucide:sparkles', path: '/v3/agent', query: { tab: 'persona' }, keywords: 'persona 人设 数字员工' },
  { group: 'AI 助手', label: '配技能', icon: 'lucide:zap', path: '/v3/agent', query: { tab: 'skills' }, keywords: 'skills 技能 报价 跟进' },
  { group: 'AI 助手', label: '教话术', icon: 'lucide:message-square', path: '/v3/agent', query: { tab: 'scripts' }, keywords: 'scripts faq 话术 开场白' },
  { group: 'AI 助手', label: '转人工', icon: 'lucide:user-round', path: '/v3/agent', query: { tab: 'handoff' }, keywords: 'handoff 转人工 黑名单 工作时间' },

  { group: '管理与设置', label: '渠道管理', icon: 'lucide:link', path: '/v3/channel', keywords: 'channel inbox whatsapp mail linkedin facebook 渠道 收件箱' },
  { group: '管理与设置', label: 'AI 接待设置', icon: 'lucide:settings', path: '/v3/settings', keywords: 'settings ai 大模型 回复模式' },
  { group: '管理与设置', label: '成员管理', icon: 'lucide:users', path: '/v3/settings', query: { tab: 'members' }, keywords: 'members team 成员 业务员' },
  { group: '管理与设置', label: '云盘文件', icon: 'lucide:folder', path: '/v3/settings', query: { tab: 'cloud' }, keywords: 'cloud files 云盘 文件' },
  { group: '管理与设置', label: '邮件模板', icon: 'lucide:mail', path: '/v3/settings', query: { tab: 'emailTemplates' }, keywords: 'template 邮件模板' },
  { group: '管理与设置', label: '快捷回复', icon: 'lucide:zap', path: '/v3/settings', query: { tab: 'quickReplies' }, keywords: 'quick reply 快捷回复' },
  { group: '管理与设置', label: '消息分流设置', icon: 'lucide:git-branch', path: '/v3/settings', query: { tab: 'routing' }, keywords: 'routing 分流 分配规则' },
  { group: '管理与设置', label: '通知与翻译设置', icon: 'lucide:bell', path: '/v3/settings', query: { tab: 'notifications' }, keywords: 'notify translate 通知 翻译' },
  { group: '管理与设置', label: '客户管理（平台）', icon: 'lucide:building-2', path: '/v3/platform/tenants', keywords: 'tenant platform 租户 开户', platformOnly: true },
  { group: '账户', label: '个人中心', icon: 'lucide:user-round', path: '/profile', keywords: 'profile password 个人中心 密码' },
];

const isPlatform = computed(() =>
  (accessStore.accessCodes ?? []).includes('system:tenant:query'),
);

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase();
  return COMMANDS.filter((c) => {
    if (c.platformOnly && !isPlatform.value) return false;
    if (!q) return true;
    return `${c.label} ${c.group} ${c.keywords}`.toLowerCase().includes(q);
  });
});

watch(filtered, () => {
  selected.value = 0;
});

watch(open, async (v) => {
  if (v) {
    query.value = '';
    selected.value = 0;
    await nextTick();
    inputRef.value?.focus();
  }
});

function run(item: CommandItem) {
  open.value = false;
  router.push({ path: item.path, query: item.query });
}

function onKeydown(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault();
    open.value = !open.value;
    return;
  }
  if (!open.value) return;
  if (e.key === 'Escape') {
    e.preventDefault();
    open.value = false;
  } else if (e.key === 'ArrowDown') {
    e.preventDefault();
    selected.value = (selected.value + 1) % Math.max(1, filtered.value.length);
  } else if (e.key === 'ArrowUp') {
    e.preventDefault();
    selected.value =
      (selected.value - 1 + Math.max(1, filtered.value.length)) %
      Math.max(1, filtered.value.length);
  } else if (e.key === 'Enter' && filtered.value[selected.value]) {
    e.preventDefault();
    run(filtered.value[selected.value]!);
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown));
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown));
</script>

<template>
  <Teleport to="body">
    <Transition name="ap-cmd">
      <div v-if="open" class="ap-cmd-mask" @click.self="open = false">
        <div class="ap-cmd" role="dialog" aria-label="快捷跳转">
          <div class="ap-cmd__bar">
            <IconifyIcon icon="lucide:search" class="ap-cmd__search-icon" />
            <input
              ref="inputRef"
              v-model="query"
              class="ap-cmd__input"
              placeholder="搜索页面、功能或设置…"
              autocomplete="off"
            />
            <kbd class="ap-cmd__kbd">ESC</kbd>
          </div>
          <div class="ap-cmd__list">
            <div v-if="!filtered.length" class="ap-cmd__empty">
              没有找到匹配的页面
            </div>
            <button
              v-for="(item, idx) in filtered"
              :key="`${item.path}-${item.label}`"
              type="button"
              class="ap-cmd__item"
              :class="{ 'is-selected': idx === selected }"
              @mouseenter="selected = idx"
              @click="run(item)"
            >
              <span class="ap-cmd__icon">
                <IconifyIcon :icon="item.icon" class="size-4" />
              </span>
              <span class="ap-cmd__label">{{ item.label }}</span>
              <span class="ap-cmd__group">{{ item.group }}</span>
            </button>
          </div>
          <div class="ap-cmd__foot">
            <span><kbd>↑</kbd><kbd>↓</kbd> 选择</span>
            <span><kbd>↵</kbd> 打开</span>
            <span><kbd>⌘K</kbd> 随时唤起</span>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.ap-cmd-mask {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 13vh;
  background: rgb(0 0 0 / 24%);
  backdrop-filter: blur(8px);
}

.ap-cmd {
  display: flex;
  flex-direction: column;
  width: min(560px, calc(100vw - 32px));
  max-height: 66vh;
  overflow: hidden;
  background: var(--ap-glass-strong);
  border-radius: var(--ap-radius-xl);
  box-shadow: var(--ap-shadow-pop);
  backdrop-filter: var(--ap-blur);
}

.ap-cmd__bar {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 14px 18px;
  border-bottom: 0.5px solid var(--ap-separator);
}

.ap-cmd__search-icon {
  flex: none;
  width: 18px;
  height: 18px;
  color: var(--ap-label-3);
}

.ap-cmd__input {
  flex: 1;
  min-width: 0;
  font-size: 16px;
  color: var(--ap-label);
  background: transparent;
  border: 0;
  outline: none;
}

.ap-cmd__input::placeholder {
  color: var(--ap-label-4);
}

.ap-cmd__kbd {
  padding: 2px 6px;
  font-family: var(--ap-font-mono);
  font-size: 11px;
  color: var(--ap-label-3);
  background: var(--ap-fill-4);
  border-radius: 5px;
}

.ap-cmd__list {
  flex: 1;
  padding: 8px;
  overflow: auto;
}

.ap-cmd__empty {
  padding: 36px 0;
  font-size: var(--ap-text-subhead);
  color: var(--ap-label-3);
  text-align: center;
}

.ap-cmd__item {
  display: flex;
  gap: 10px;
  align-items: center;
  width: 100%;
  padding: 9px 12px;
  font-size: var(--ap-text-callout);
  color: var(--ap-label);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 10px;
  transition: background-color 120ms linear;
}

.ap-cmd__item.is-selected {
  color: #fff;
  background: var(--ap-accent);
}

.ap-cmd__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 26px;
  height: 26px;
  color: var(--ap-accent);
  background: var(--ap-accent-soft);
  border-radius: 7px;
}

.ap-cmd__item.is-selected .ap-cmd__icon {
  color: #fff;
  background: rgb(255 255 255 / 20%);
}

.ap-cmd__label {
  flex: 1;
  font-weight: 500;
}

.ap-cmd__group {
  font-size: var(--ap-text-footnote);
  color: var(--ap-label-3);
}

.ap-cmd__item.is-selected .ap-cmd__group {
  color: rgb(255 255 255 / 78%);
}

.ap-cmd__foot {
  display: flex;
  gap: 16px;
  padding: 9px 18px;
  font-size: 11px;
  color: var(--ap-label-3);
  border-top: 0.5px solid var(--ap-separator);
}

.ap-cmd__foot kbd {
  padding: 1px 5px;
  margin-right: 4px;
  font-family: var(--ap-font-mono);
  background: var(--ap-fill-4);
  border-radius: 4px;
}

.ap-cmd-enter-active,
.ap-cmd-leave-active {
  transition: opacity 200ms var(--ap-ease);
}

.ap-cmd-enter-active .ap-cmd,
.ap-cmd-leave-active .ap-cmd {
  transition:
    transform 360ms var(--ap-spring),
    opacity 200ms var(--ap-ease);
}

.ap-cmd-enter-from,
.ap-cmd-leave-to {
  opacity: 0;
}

.ap-cmd-enter-from .ap-cmd,
.ap-cmd-leave-to .ap-cmd {
  opacity: 0;
  transform: scale(0.95) translateY(-10px);
}
</style>
