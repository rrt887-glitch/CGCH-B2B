import { requestClient } from '#/api/request';

/** 已配置授权应用的服务商：google、microsoft */
export function getOAuthProviders() {
  return requestClient.get<string[]>('/channel/oauth/providers');
}

/** 获取授权地址，在新窗口打开 */
export function getOAuthAuthorizeUrl(provider: string) {
  return requestClient.get<string>(`/channel/oauth/${provider}/authorize-url`);
}

/** Facebook 登录授权地址（接入 Facebook 主页与关联的 Instagram） */
export function getMetaAuthorizeUrl() {
  return requestClient.get<string>('/channel/meta/oauth/authorize-url');
}
