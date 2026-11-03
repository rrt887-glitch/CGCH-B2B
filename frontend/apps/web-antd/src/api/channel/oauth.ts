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

/** Facebook 扫码授权：授权地址（做成二维码）和查询结果用的 state */
export function getMetaAuthorize() {
  return requestClient.get<{ state: string; url: string }>('/channel/meta/oauth/authorize');
}

/** 查询扫码授权结果 */
export function getMetaAuthorizeStatus(state: string) {
  return requestClient.get<{ message?: string; status: 'expired' | 'fail' | 'ok' | 'pending' }>(
    '/channel/meta/oauth/status',
    { params: { state } },
  );
}

export interface WaSignupConfig {
  enabled: boolean;
  /** meta：Meta 嵌入式注册（需要自己是技术服务商） */
  provider?: 'meta';
  appId?: string;
  configId?: string;
  apiVersion?: string;
  sdkUrl?: string;
}

/** WhatsApp 嵌入式注册的前端配置 */
export function getWaSignupConfig() {
  return requestClient.get<WaSignupConfig>('/channel/meta/wa-signup/config');
}

/** 嵌入式注册完成后提交 code、号码编号与 WABA 编号；mode=coexist 表示共存模式（手机 App 扫码接入，号码编号可空） */
export function completeWaSignup(data: { code: string; mode?: 'coexist' | 'new'; phoneNumberId?: string; wabaId: string }) {
  return requestClient.post<{ displayPhone?: string; id: number; pin?: string; warning?: string }>(
    '/channel/meta/wa-signup/complete',
    data,
  );
}
