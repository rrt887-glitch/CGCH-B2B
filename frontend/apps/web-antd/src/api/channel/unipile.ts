import { requestClient } from '#/api/request';

/** Unipile 托管接入：一个平台 Key 下接 LinkedIn / Instagram / Facebook / Telegram / WhatsApp */
export namespace ChannelUnipileApi {
  /** 支持的渠道（对应 Unipile 的 providers） */
  export type Provider =
    | 'whatsapp'
    | 'linkedin'
    | 'instagram'
    | 'facebook'
    | 'telegram';

  export interface Credential {
    enabled: boolean;
    scopeId?: string | null;
    apiKeyMask?: string | null;
    status?: string | null;
  }

  export interface ConnectResult {
    url: string;
    provider: Provider;
    scopeId?: string | null;
    expiresInMinutes: number;
    /** 大于 0 表示 Unipile 上已有这个渠道的账号，后端直接找回了，不用再扫码 */
    recovered?: number;
  }
}

/** 本公司是否已配置 Unipile（只看脱敏信息） */
export function getUnipileCredential() {
  return requestClient.get<ChannelUnipileApi.Credential>('/channel/unipile/credential');
}

/** 生成托管授权链接：前端打开后由客户在 Unipile 页面登录/扫码 */
export function connectUnipile(provider: ChannelUnipileApi.Provider, force = false) {
  return requestClient.post<ChannelUnipileApi.ConnectResult>(
    `/channel/unipile/connect?provider=${provider}&force=${force}`,
  );
}

/** 找回 Unipile 上已存在、但后台没记录的账号（绑定报 Account already exists 时用） */
export function recoverUnipile() {
  return requestClient.post<number>('/channel/unipile/recover');
}

/** 断开并删除该渠道账号 */
export function disconnectUnipile(id: number) {
  return requestClient.delete<boolean>(`/channel/unipile/account?id=${id}`);
}

/** Facebook 直接登录（自定义流程）：第 1 步，账号密码或 cookies */
export function facebookLogin(data: {
  username?: string;
  password?: string;
  cookies?: string;
  userAgent?: string;
  selectPages?: boolean;
}) {
  return requestClient.post<FacebookAuthResult>(
    '/channel/unipile/facebook/login',
    data,
  );
}

/** Facebook 直接登录：第 2 步，回答 checkpoint（选 Page / 验证码 / SKIP） */
export function facebookSolve(data: {
  intentId: string;
  code?: string;
  credential?: string;
}) {
  return requestClient.post<FacebookAuthResult>(
    '/channel/unipile/facebook/solve',
    data,
  );
}

export interface FacebookAuthResult {
  status: 'account' | 'checkpoint';
  accountId?: string;
  name?: string;
  intentId?: string;
  type?: string;
  products?: { id: string; display_name: string }[];
  options?: { id: string }[];
  platform?: string;
  channel?: string;
  code?: string;
  raw?: any;
}
