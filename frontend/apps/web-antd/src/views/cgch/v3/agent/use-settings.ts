import type { AgentApi } from '#/api/channel/agent';

import { ref } from 'vue';

import { message } from 'ant-design-vue';

import { getAgentSettings, saveAgentSettings } from '#/api/channel/agent';

/** 智能体配置：各步骤共用一份，保存时只提交改动的部分 */
const settings = ref<AgentApi.Settings>();
let loading: Promise<AgentApi.Settings> | undefined;

export function useAgentSettings() {
  async function load(force = false) {
    if (settings.value && !force) return settings.value;
    loading ??= getAgentSettings().finally(() => (loading = undefined));
    settings.value = await loading;
    return settings.value;
  }

  async function save(part: Parameters<typeof saveAgentSettings>[0], tip = '已保存') {
    await saveAgentSettings(part);
    if (settings.value) Object.assign(settings.value, JSON.parse(JSON.stringify(part)));
    if (tip) message.success(tip);
  }

  return { settings, load, save };
}

/** 深拷贝，编辑表单时不直接改共享数据 */
export function clone<T>(v: T): T {
  return JSON.parse(JSON.stringify(v));
}
