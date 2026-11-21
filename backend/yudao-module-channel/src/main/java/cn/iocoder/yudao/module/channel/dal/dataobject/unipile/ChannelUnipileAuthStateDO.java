package cn.iocoder.yudao.module.channel.dal.dataobject.unipile;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Unipile 托管授权的发起记录：谁（哪个成员）在哪个租户下发起连接、连的哪个渠道。
 *
 * 回调时只能拿到 Unipile 回传的 state，用它换回这里的信息，才知道新账号该归谁。
 * 一次性使用，过期作废。
 */
@TableName("channel_unipile_auth_state")
@KeySequence("channel_unipile_auth_state_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelUnipileAuthStateDO extends BaseDO {

    @TableId
    private Long id;

    /** 一次性令牌（放在 state 参数里） */
    private String token;

    /** 发起授权的租户 */
    private Long tenantId;

    /** 发起授权的成员：新账号的负责人 */
    private Long userId;

    /** 渠道：whatsapp / linkedin / instagram / facebook / telegram */
    private String provider;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 是否已使用（一次性） */
    private Boolean used;
}
