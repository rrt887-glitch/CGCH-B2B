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

export interface WaSignupConfig {
  enabled: boolean;
  appId?: string;
  configId?: string;
  apiVersion?: string;
  sdkUrl?: string;
}

/** WhatsApp 嵌入式注册的前端配置 */
export function getWaSignupConfig() {
  return requestClient.get<WaSignupConfig>('/channel/meta/wa-signup/config');
}

/** 嵌入式注册完成后提交 code、号码编号与 WABA 编号 */
export function completeWaSignup(data: { code: string; phoneNumberId: string; wabaId: string }) {
  return requestClient.post<{ displayPhone?: string; id: number; pin?: string; warning?: string }>(
    '/channel/meta/wa-signup/complete',
    data,
  );
}
