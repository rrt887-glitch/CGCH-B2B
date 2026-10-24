import { requestClient } from '#/api/request';

/** 官网 IM：绑定网站（域名 + 官网名称）。站点本身作为渠道账号（platform = web）出现在账号列表里，phoneNumberId 是站点密钥 */
export const createWebSite = (data: { domain: string; name: string }) => requestClient.post<number>('/channel/web-im/site', data);

export const updateWebSite = (data: { domain: string; id: number; name: string }) => requestClient.put('/channel/web-im/site', data);

/** 嵌入代码：放到网站每个页面的 </body> 之前 */
export function webImEmbedCode(siteKey: string) {
  // 与 Webhook 回调地址一致：部署时 Nginx 把 /admin-api 转到后端
  return `<script src="${window.location.origin}/admin-api/channel/web-im/widget.js?k=${siteKey}" async></script>`;
}
