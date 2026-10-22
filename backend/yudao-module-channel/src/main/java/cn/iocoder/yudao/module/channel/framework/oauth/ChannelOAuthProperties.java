package cn.iocoder.yudao.module.channel.framework.oauth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 邮箱 OAuth 授权（Gmail、Outlook / Microsoft 365）的应用配置。
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
