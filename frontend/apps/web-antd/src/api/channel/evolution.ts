import { requestClient } from '#/api/request';

/** WhatsApp 扫码接入（Evolution API）：status 为 connecting 时附最新二维码（data URL）或配对码 */
export interface EvoSession {
  session: string;
  status: 'connecting' | 'expired' | 'open';
  qr?: string;
  pairingCode?: string;
  accountId?: number;
  displayPhone?: string;
}

/** 服务器是否已开通扫码接入 */
export const getEvoConfig = () => requestClient.get<{ enabled: boolean }>('/channel/evolution/config');

/** 开始连接：number 为空返回二维码，填手机号（含国家码）返回配对码；accountId 为重新扫码的账号 */
export const startEvoSession = (data: { accountId?: number; number?: string }) =>
  requestClient.post<EvoSession>('/channel/evolution/session', data);

/** 连接进度 */
export const getEvoSession = (session: string) =>
  requestClient.get<EvoSession>('/channel/evolution/session', { params: { session } });

/** 放弃连接 */
export const cancelEvoSession = (session: string) =>
  requestClient.delete<boolean>('/channel/evolution/session', { params: { session } });
