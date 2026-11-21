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
  }
}

/** 本公司是否已配置 Unipile（只看脱敏信息） */
export function getUnipileCredential() {
  return requestClient.get<ChannelUnipileApi.Credential>('/channel/unipile/credential');
}

/** 生成托管授权链接：前端打开后由客户在 Unipile 页面登录/扫码 */
export function connectUnipile(provider: ChannelUnipileApi.Provider) {
  return requestClient.post<ChannelUnipileApi.ConnectResult>(
    `/channel/unipile/connect?provider=${provider}`,
  );
}

/** 断开并删除该渠道账号 */
export function disconnectUnipile(id: number) {
  return requestClient.delete<boolean>(`/channel/unipile/account?id=${id}`);
}
