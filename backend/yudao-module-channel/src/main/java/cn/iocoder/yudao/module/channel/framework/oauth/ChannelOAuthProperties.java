package cn.iocoder.yudao.module.channel.framework.oauth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OAuth 授权的应用配置：邮箱（Gmail、Outlook / Microsoft 365）与 Meta（Facebook 主页、Instagram 私信）。
 * client-id / client-secret 来自在 Google Cloud Console、Microsoft Entra 注册的应用，
 * 通过环境变量 CGCH_OAUTH_GOOGLE_CLIENT_ID 等提供；未配置的服务商不显示授权按钮。
 */
@Configuration
@ConfigurationProperties(prefix = "cgch.oauth")
@Data
public class ChannelOAuthProperties {

    /** 回调地址前缀，最终回调为 {redirectBase}/admin-api/channel/oauth/{provider}/callback */
    private String redirectBase = "http://127.0.0.1:48080";

    private Provider google = Provider.google();
    private Provider microsoft = Provider.microsoft();
    private Meta meta = new Meta();
    private Evolution evolution = new Evolution();
    private Social youtube = Social.youtube();
    private Social tiktok = Social.tiktok();

    /**
     * 评论渠道：YouTube（Google Cloud 项目启用 YouTube Data API v3，回调 {redirectBase}/admin-api/channel/social/youtube/callback）、
     * TikTok（TikTok API for Business 开发者应用，开通 TikTok Accounts 权限，回调 {redirectBase}/admin-api/channel/social/tiktok/callback/）。
     * 系统定时拉取视频下的评论进「会话」，AI 或业务员回复评论。
     */
    @Data
    public static class Social {
        private String clientId = "";
        private String clientSecret = "";
        /** 授权地址；TikTok 填开发者后台 App Detail 里的「TikTok account holder authorization URL」 */
        private String authUrl = "";
        private String tokenUrl = "";
        private String apiBase = "";
        private String scope = "";
        /** 回调路径（TikTok 要求以 / 结尾） */
        private String callbackPath = "";

        public boolean configured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank()
                    && authUrl != null && !authUrl.isBlank();
        }

        static Social youtube() {
            Social s = new Social();
            s.authUrl = "https://accounts.google.com/o/oauth2/v2/auth";
            s.tokenUrl = "https://oauth2.googleapis.com/token";
            s.apiBase = "https://www.googleapis.com/youtube/v3";
            s.scope = "https://www.googleapis.com/auth/youtube.force-ssl";
            s.callbackPath = "/admin-api/channel/social/youtube/callback";
            return s;
        }

        static Social tiktok() {
            Social s = new Social();
            s.apiBase = "https://business-api.tiktok.com/open_api/v1.3";
            s.callbackPath = "/admin-api/channel/social/tiktok/callback/";
            return s;
        }
    }

    /**
     * WhatsApp 扫码接入（Evolution API，开源项目，基于 WhatsApp 网页版协议）：客户用手机 WhatsApp「关联设备」扫码或输入配对码，
     * 和登录电脑版 WhatsApp 一样。Evolution API 与系统部署在一起，推送地址为 {webhookBase}/admin-api/channel/evolution/webhook。
     * 注意：这不是 Meta 官方接口，号码有被 WhatsApp 限制的风险。
     */
    @Data
    public static class Evolution {
        /** Evolution API 地址（容器网络内） */
        private String baseUrl = "";
        /** Evolution API 全局 API Key（AUTHENTICATION_API_KEY） */
        private String apiKey = "";
        /** Evolution API 回调我们的地址前缀（容器网络内），为空时用 redirectBase */
        private String webhookBase = "";

        public boolean configured() {
            return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
        }
    }

    /**
     * Meta 应用（developers.facebook.com）：Facebook 登录授权主页与 Instagram，并接收应用级 Webhook。
     * 回调为 {redirectBase}/admin-api/channel/meta/oauth/callback，Webhook 为 {redirectBase}/admin-api/channel/meta/webhook。
     */
    @Data
    public static class Meta {
        /** 应用编号（App ID） */
        private String clientId = "";
        /** 应用密钥（App Secret），同时用于校验 Webhook 签名 */
        private String clientSecret = "";
        /**
         * WhatsApp 嵌入式注册的配置编号（Facebook 登录 for Business → 配置，类型选「WhatsApp 嵌入式注册」）；
         * 填写后客户可以在系统里用 Facebook 弹窗自己开通 WhatsApp 号码
         */
        private String waConfigId = "";
        /** Facebook JS SDK 地址（测试时可替换） */
        private String sdkUrl = "https://connect.facebook.net/en_US/sdk.js";
        /** 在 Meta 后台配置 Webhook 时填写的验证令牌 */
        private String verifyToken = "";
        private String dialogUrl = "https://www.facebook.com/v24.0/dialog/oauth";
        private String apiBase = "https://graph.facebook.com";
        private String apiVersion = "v24.0";
        private String scope = "pages_show_list,pages_messaging,pages_manage_metadata,pages_read_engagement,"
                + "instagram_basic,instagram_manage_messages,business_management";

        public boolean configured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }

    @Data
    public static class Provider {
        private String clientId = "";
        private String clientSecret = "";
        private String authUrl;
        private String tokenUrl;
        /** Google 用 userinfo 接口取邮箱；Microsoft 从 id_token 里取 */
        private String userinfoUrl;
        private String scope;
        private String imapHost;
        private int imapPort = 993;
        private boolean imapSsl = true;
        private String smtpHost;
        private int smtpPort;
        private boolean smtpSsl;

        public boolean configured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }

        static Provider google() {
            Provider p = new Provider();
            p.authUrl = "https://accounts.google.com/o/oauth2/v2/auth";
            p.tokenUrl = "https://oauth2.googleapis.com/token";
            p.userinfoUrl = "https://openidconnect.googleapis.com/v1/userinfo";
            p.scope = "https://mail.google.com/ openid email profile";
            p.imapHost = "imap.gmail.com";
            p.smtpHost = "smtp.gmail.com";
            p.smtpPort = 465;
            p.smtpSsl = true;
            return p;
        }

        static Provider microsoft() {
            Provider p = new Provider();
            p.authUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize";
            p.tokenUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/token";
            p.scope = "offline_access openid email profile https://outlook.office.com/IMAP.AccessAsUser.All https://outlook.office.com/SMTP.Send";
            p.imapHost = "outlook.office365.com";
            p.smtpHost = "smtp.office365.com";
            p.smtpPort = 587;
            p.smtpSsl = false; // 587 端口使用 STARTTLS
            return p;
        }
    }

    public Provider get(String provider) {
        return switch (provider) {
            case "google" -> google;
            case "microsoft" -> microsoft;
            default -> null;
        };
    }

}
