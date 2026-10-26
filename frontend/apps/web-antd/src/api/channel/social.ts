import { requestClient } from '#/api/request';

/** YouTube / TikTok 视频评论渠道 */
export type SocialPlatform = 'tiktok' | 'youtube';

/** 已配置授权应用的平台 */
export const getSocialPlatforms = () => requestClient.get<SocialPlatform[]>('/channel/social/platforms');

/** 授权地址（hint：TikTok 用户名，仅用于核对） */
export const getSocialAuthorizeUrl = (platform: SocialPlatform, hint?: string) =>
  requestClient.get<string>(`/channel/social/${platform}/authorize-url`, { params: { hint } });

/** 立即拉取一次评论，返回新评论数 */
export const pollSocial = (id: number) => requestClient.post<number>(`/channel/social/poll?id=${id}`);
