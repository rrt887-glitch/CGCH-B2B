package cn.iocoder.yudao.module.channel.dal.dataobject.credit;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 租户的智点余额，每个租户一条
 */
@TableName("channel_credit_account")
@KeySequence("channel_credit_account_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelCreditAccountDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 当前余额（智点）；扣费在调用之后，可能短暂为负
     */
    private Long balance;
    /**
     * 累计获得
     */
    private Long totalIn;
    /**
     * 累计消耗
     */
    private Long totalOut;

}
