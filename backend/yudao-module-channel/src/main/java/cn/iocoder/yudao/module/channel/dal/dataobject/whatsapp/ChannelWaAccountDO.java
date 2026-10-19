package cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp;

import cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * WhatsApp Business 号码（Meta 官方 Cloud API）
 */
@TableName(value = "channel_wa_account", autoResultMap = true)
@KeySequence("channel_wa_account_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = {"accessToken", "appSecret"})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelWaAccountDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 负责该号码的用户编号
     */
    private Long userId;
    /**
     * 显示名称，例如「销售一部 WhatsApp」
     */
    private String name;
    /**
     * Meta 的 Phone number ID
     */
    private String phoneNumberId;
    /**
     * WhatsApp Business Account ID，用于查询消息模板
     */
    private String wabaId;
    /**
     * 号码，例如 +86 138 0000 0000，展示用
     */
    private String displayPhone;
    /**
     * 访问令牌（建议使用系统用户的永久令牌），加密存储
     */
    @TableField(typeHandler = EncryptTypeHandler.class)
    private String accessToken;
    /**
     * Meta 应用密钥，用于校验 Webhook 签名，加密存储
     */
    @TableField(typeHandler = EncryptTypeHandler.class)
    private String appSecret;
    /**
     * Webhook 验证令牌，在 Meta 后台配置回调地址时填写
     */
    private String verifyToken;
    /**
     * Graph API 地址，默认 https://graph.facebook.com
     */
    private String apiBase;
    /**
     * Graph API 版本，例如 v24.0
     */
    private String apiVersion;
    /**
     * 是否启用
     */
    private Boolean enabled;
    /**
     * 最近一次收到 Webhook 的时间
     */
    private LocalDateTime lastWebhookTime;

}
