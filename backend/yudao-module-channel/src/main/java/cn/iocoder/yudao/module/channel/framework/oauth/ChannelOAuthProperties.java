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
    private Ycloud ycloud = new Ycloud();

    /**
     * YCloud（Meta 官方 WhatsApp 服务商）：客户通过 YCloud 的托管页面扫码绑定 WhatsApp（Partner Direct Link），
     * 消息经 YCloud 接口收发。Webhook 为 {redirectBase}/admin-api/channel/ycloud/webhook。
     */
    @Data
    public static class Ycloud {
        /** 接口地址 */
        private String apiBase = "https://api.ycloud.com/v2";
        /** API Key（YCloud 控制台 → Developers → API Key） */
        private String apiKey = "";
        /** Webhook 签名密钥（YCloud 控制台 → Developers → Webhook，创建接收地址时返回） */
        private String webhookSecret = "";

        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
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
