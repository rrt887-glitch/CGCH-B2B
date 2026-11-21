package cn.iocoder.yudao.module.channel.dal.dataobject.unipile;

import cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 每个公司一条：Unipile 的隔离边界（Scope）与该 Scope 专属的 API Key。
 *
 * 业务请求只用这里的 Key，平台主 Key 只用于开户，做到「一家公司的 Key 看不到另一家的账号」。
 */
@TableName(value = "channel_unipile_credential", autoResultMap = true)
@KeySequence("channel_unipile_credential_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = {"apiKey"})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelUnipileCredentialDO extends TenantBaseDO {

    @TableId
    private Long id;

    /** Scope 编号，形如 scope_xxx */
    private String scopeId;

    /** 该 Scope 专属的 API Key（加密存储） */
    @TableField(typeHandler = EncryptTypeHandler.class)
    private String apiKey;

    /** 展示用脱敏 Key，形如 pk_****abcd */
    private String apiKeyMask;

    /** active / disabled */
    private String status;

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_DISABLED = "disabled";
}
