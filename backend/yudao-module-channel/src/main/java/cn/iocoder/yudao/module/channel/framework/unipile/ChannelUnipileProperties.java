package cn.iocoder.yudao.module.channel.framework.unipile;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Unipile 接入配置（application.yaml 里 cgch.unipile.*）。
 *
 * Unipile 是托管的多渠道接入服务：WhatsApp 走「关联设备」（扫码 / 配对码），不需要 Meta 开发者账号。
 * 多租户隔离靠 Unipile 的 Scope：每个公司一个 Scope + 一个只属于该 Scope 的 API Key，
 * 平台主 Key（serviceKey）只用于开户时建 Scope、建 Key、删账号，不用于收发消息。
 */
@Configuration
@ConfigurationProperties(prefix = "cgch.unipile")
@Data
public class ChannelUnipileProperties {

    /** 是否启用 Unipile 接入 */
    private boolean enabled = false;

    /** 实例地址，形如 api8.unipile.com:13780（也可写完整 https:// 开头） */
    private String dsn = "";

    /** 平台主 Key（Service Key）：只用于建 Scope / 建租户 Key / 删除账号 */
    private String serviceKey = "";

    /** Webhook 签名密钥（Unipile 后台配置的回调地址对应的签名密钥） */
    private String webhookSecret = "";

    /** 扫码完成后的回调地址（会拼在 redirect_uri 上） */
    private String callbackPath = "/admin-api/channel/unipile/callback";

    /** 对外访问域名，例如 https://hig.global */
    private String publicBase = "";

    /** 租户 Key 的有效期（天） */
    private int keyExpireDays = 365;

    public boolean ready() {
        return enabled && StrUtil.isNotBlank(dsn) && StrUtil.isNotBlank(serviceKey);
    }

    /**
     * 基础地址，例如 https://api.unipile.com。
     * 注意 Unipile v2 的路径就是 /v2/...，不要再拼 /api（v1 才是 /api/v1）。
     */
    public String baseUrl() {
        String d = StrUtil.blankToDefault(dsn, "").trim();
        if (d.isEmpty()) {
            return "";
        }
        if (!d.startsWith("http")) {
            d = "https://" + d;
        }
        while (d.endsWith("/")) {
            d = d.substring(0, d.length() - 1);
        }
        // 兼容把控制台里显示的「https://api.unipile.com/v2/」整段贴进来的情况
        if (d.endsWith("/v2")) {
            d = d.substring(0, d.length() - 3);
        }
        return d;
    }
}
