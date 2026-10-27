import type { App } from 'vue';

/**
 * form-create（表单设计器、动态表单）体积很大，只有流程审批（/bpm）和表单构建（/infra/build）页面用到，
 * 所以不在启动时加载，进入这些页面前再加载并注册，其他页面（包括登录页）首次打开快很多。
 */
let app: App | undefined;
let loading: Promise<void> | undefined;

export function bindFormCreateApp(target: App) {
  app = target;
}

export function needsFormCreate(path: string) {
  return /^\/(?:bpm|infra\/build)(?:\/|$)/.test(path);
}

export function ensureFormCreate(): Promise<void> {
  if (!app) {
    return Promise.resolve();
  }
  if (!loading) {
    const target = app;
    loading = import('./index')
      .then(({ setupFormCreate }) => setupFormCreate(target))
      .catch((error) => {
        loading = undefined;
        throw error;
      });
  }
  return loading;
}
