# 平台开发者应用：注册与上线清单

目标：让所有客户都能在 CGCH 里用官方授权弹窗接入 Facebook 主页、Instagram、WhatsApp 和 Outlook，不需要手动填令牌。
Gmail 暂用「应用专用密码」绑定，不申请 Google 受限权限（需要付费的第三方安全评估）。

## 前提：网站先上线

审核方会访问以下地址并实际登录体验，所以要先按 `docs/DEPLOY.md` 部署好，并启用 HTTPS：

| 用途 | 地址 |
|---|---|
| 网站 / 登录页 | `https://你的域名/` |
| 产品介绍 | `https://你的域名/about` |
| 隐私政策 | `https://你的域名/privacy` |
| 服务条款 | `https://你的域名/terms` |
| 数据删除说明 | `https://你的域名/data-deletion` |

服务器放在香港或新加坡：国内大陆的服务器访问不了 Facebook 接口，海外服务器的域名也不需要备案。
页面上的公司信息来自环境变量 `CGCH_COMPANY_NAME`、`CGCH_COMPANY_NAME_EN`、`CGCH_CONTACT_EMAIL`、`CGCH_COMPANY_ADDRESS`，要和营业执照一致。

## 一、Meta（Facebook 主页、Instagram、WhatsApp）

### 1. 创建应用
1. developers.facebook.com → 我的应用 → 创建应用，用例选「其他」，类型选「商务」，关联公司的 Business Manager（商务管理平台）。
2. 添加产品：**Facebook 登录 for Business**、**Messenger**、**Instagram**、**WhatsApp**、**Webhooks**。
3. 应用设置 → 基本：
   - 应用域名：`你的域名`
   - 隐私政策网址：`https://你的域名/privacy`
   - 服务条款网址：`https://你的域名/terms`
   - 用户数据删除：选「数据删除回调网址」，填 `https://你的域名/admin-api/channel/meta/data-deletion`
   - 应用图标（1024×1024）、类别「商务和页面」、联系邮箱
4. Facebook 登录 for Business → 设置：
   - 有效 OAuth 跳转 URI：`https://你的域名/admin-api/channel/meta/oauth/callback`
   - 取消授权回调网址：`https://你的域名/admin-api/channel/meta/deauthorize`
   - 允许的 JavaScript SDK 域名：`https://你的域名`
5. Facebook 登录 for Business → 配置 → 新建配置，类型选「WhatsApp 嵌入式注册」，记下配置编号，填到 `CGCH_META_WA_CONFIG_ID`。
   渠道页的「连接 WhatsApp」默认走**共存模式**（featureType `whatsapp_business_app_onboarding`）：客户输入正在用 WhatsApp Business App 的号码，用手机 App 扫码确认，手机照常使用，最近 6 个月聊天记录同步进来；也可以切换为开通新号码。
6. Webhooks（回调地址都填 `https://你的域名/admin-api/channel/meta/webhook`，验证令牌填 `CGCH_META_WEBHOOK_VERIFY_TOKEN`）：
   - Page：订阅 `messages`、`messaging_postbacks`、`message_echoes`、`message_deliveries`、`message_reads`
   - Instagram：订阅 `messages`
   - WhatsApp Business Account：订阅 `messages`，以及共存模式需要的 `smb_message_echoes`（手机 App 发出的消息）、`history`（聊天记录同步）、`smb_app_state_sync`（通讯录同步）
7. 把应用编号、应用密钥填到 `CGCH_META_APP_ID`、`CGCH_META_APP_SECRET`，重启后端。应用密钥只能由管理员复制，不要发到聊天里。

### 2. 商家验证（Business Verification）
商务管理平台 → 设置 → 安全中心 → 开始验证。需要：
- 营业执照（公司中文全称、地址要与填写的一致）
- 对公电话、域名邮箱或网站其中之一，用于接收验证码（域名邮箱最简单：`admin@你的域名`）
一般 1～5 个工作日。

### 3. 成为 WhatsApp 技术服务商（Tech Provider）
商家验证通过后，在应用的 WhatsApp → 开始使用 中按提示完成「技术服务商」引导（同意 WhatsApp 商业条款、填写业务信息）。
完成后客户才能通过嵌入式注册开通自己的号码。

### 4. 应用审核（App Review）
申请「高级访问权限」（Advanced Access）：

| 权限 | 用途说明要点 |
|---|---|
| `pages_show_list` | 授权后列出用户管理的主页，让用户选择接入哪些 |
| `pages_manage_metadata` | 为主页订阅消息推送 |
| `pages_messaging` | 在统一收件箱里接收并回复主页私信 |
| `instagram_basic` | 读取关联的 Instagram 专业账号的用户名 |
| `instagram_manage_messages` | 接收并回复 Instagram 私信 |
| `business_management` | 读取用户的商务账户，用于 WhatsApp 嵌入式注册 |
| `whatsapp_business_management` | 为客户的 WhatsApp 企业账户订阅推送、查询号码 |
| `whatsapp_business_messaging` | 代客户收发 WhatsApp 消息 |

每项都要录一段屏幕录像（英文界面更容易通过），流程：
1. 打开网站登录 → 渠道管理 → 点「Facebook 登录授权」→ 在 Facebook 弹窗中勾选主页并同意；
2. 用另一个 Facebook 账号给主页发私信 → 在消息工作台看到这条消息 → 回复 → 对方收到；
3. Instagram 同样演示一遍；
4. WhatsApp：渠道页点「连接 WhatsApp」→ 开始连接 → 在 Meta 弹窗输入号码并用 WhatsApp Business App 扫码 → 用另一部手机发消息 → 会话页收到并回复，手机 App 上也能看到这条回复。

还要提供一个测试账号（网站的用户名和密码）给审核员。一般 3～10 个工作日，被拒绝会写明原因，修改后可重新提交。

## 一（补充）、WhatsApp 经 Kapso 接入（推荐）

Meta 开发者后台暂时不给我们的账号开放 WhatsApp，所以 WhatsApp 改为经 Kapso（kapso.com）接入：
用 Kapso 自己的 Meta 应用完成 Meta 官方授权，我们不需要自己建 WhatsApp 应用，也不需要成为 Meta 技术服务商。
客户在「渠道 → WhatsApp → 连接新账号」点「开始连接」，在 Kapso 托管的绑定页里用 Facebook 授权并扫码绑定号码
（支持共存模式：手机 WhatsApp Business App 照常使用）。

1. 注册 kapso.com，建一个项目；Kapso 控制台 → API Keys：创建 API Key，填到 `.env` 的 `CGCH_KAPSO_API_KEY`。
2. 自己生成一个随机串填到 `CGCH_KAPSO_WEBHOOK_SECRET`（`deploy/install.sh` 已自动生成）。
3. Kapso 控制台 → Integrations → Webhooks → Platform webhooks：添加地址 `https://你的域名/admin-api/channel/kapso/webhook`，
   密钥填同一个随机串，订阅 `whatsapp.phone_number.created`、`whatsapp.phone_number.deleted`、`whatsapp.phone_number.offboarded`。
   号码绑定后系统会自动给该号码配置消息推送（Meta 原始格式），不用手动加。
4. Meta 消息费结算方式 `CGCH_KAPSO_BILLING_MODE`：留空用 Kapso 默认；`partner_managed` 由我们在 Kapso 预充值代付；
   `customer_managed` 由客户在 Meta 自己绑定付款方式。
5. 重启服务。免费版只能绑 1 个号码，正式使用请升级 Pro（$25/月含 3 个号码）或 Platform（$299/月含 50 个号码）。

配置了 `CGCH_META_WA_CONFIG_ID`（自己成为 Meta 技术服务商）时，优先走 Meta 嵌入式注册。

## 一（补充二）、YouTube、TikTok 视频评论

客户在「渠道 → YouTube / TikTok → 连接新账号」授权后，系统每 3 分钟拉取一次视频下的新评论，每条评论是「会话」里的一个对话，
AI 按智能体设置自动回复评论、给评论的人建线索；业务员也可以在会话里直接回复。首次授权只导入历史评论，不会对历史评论自动回复。

**YouTube**：Google Cloud Console → 新建（或沿用）项目 → 启用 YouTube Data API v3 → OAuth 同意屏幕加入
`https://www.googleapis.com/auth/youtube.force-ssl` 范围（敏感范围，正式上线前需要 Google 审核）→ 创建「Web 应用」OAuth 客户端，
授权回调填 `https://你的域名/admin-api/channel/social/youtube/callback`，把客户端编号和密钥填到 `CGCH_YOUTUBE_CLIENT_ID`、`CGCH_YOUTUBE_CLIENT_SECRET`。
每天默认 1 万配额，每个频道每次拉取约用 2 个单位，回复一条评论用 50 个单位。

**TikTok**：TikTok API for Business（business-api.tiktok.com）→ 注册开发者 → 先填写 Accounts API Access Application Form →
创建开发者应用并勾选「TikTok Accounts」权限（需要审核）→ 在 App Detail 里添加 TikTok account holder redirect URL
`https://你的域名/admin-api/channel/social/tiktok/callback/`（必须以 / 结尾），把 App ID、Secret 和生成的 TikTok account holder authorization URL
填到 `CGCH_TIKTOK_APP_ID`、`CGCH_TIKTOK_APP_SECRET`、`CGCH_TIKTOK_AUTH_URL`。只支持 TikTok 企业号，欧盟、英国、瑞士的账号暂不支持评论接口。

## 二、微软（Outlook / Microsoft 365）

1. portal.azure.com → Microsoft Entra ID → 应用注册 → 新注册：
   - 名称：产品名
   - 受支持的账户类型：任何组织目录中的账户和个人 Microsoft 账户
   - 重定向 URI（Web）：`https://你的域名/admin-api/channel/oauth/microsoft/callback`
2. API 权限 → 添加：Office 365 Exchange Online（或 Microsoft Graph）委托权限 `IMAP.AccessAsUser.All`、`SMTP.Send`，以及 `offline_access`、`openid`、`email`、`profile`。
3. 证书和密码 → 新建客户端密码（有效期最长 24 个月，到期前要更换），和「应用程序（客户端）ID」一起填到 `CGCH_OAUTH_MICROSOFT_CLIENT_ID`、`CGCH_OAUTH_MICROSOFT_CLIENT_SECRET`。
4. **发布者验证**：用公司名义免费加入「Microsoft AI 云合作伙伴计划」拿到合作伙伴编号，在应用的「品牌打造和属性」里填写并验证。
   不做的话，授权页会显示「未验证」，很多企业租户会禁止员工授权未验证的应用。
5. 品牌打造：填主页、服务条款、隐私政策地址和图标。

## 三、Google（Gmail，暂缓）

当前方案：Gmail 用户开启两步验证后生成「应用专用密码」，在渠道管理里选「谷歌邮箱 → 用授权码」绑定，免费且现在就能用。
以后如需一键授权：在 Google Cloud 建 OAuth 客户端，申请 `https://mail.google.com/` 受限范围的验证，并通过 Google 指定机构的 CASA 安全评估（每年付费）。

## 需要你本人完成的事项

- 登录 Facebook / 微软账号，接受各平台的开发者条款
- 上传营业执照、接收验证码，完成商家验证与发布者验证
- 付款购买服务器和域名
- 把应用密钥、客户端密码复制到服务器的 `.env`，不要发到聊天里
- 审核需要的测试账号与屏幕录像：录像可以由你操作、我写好脚本
