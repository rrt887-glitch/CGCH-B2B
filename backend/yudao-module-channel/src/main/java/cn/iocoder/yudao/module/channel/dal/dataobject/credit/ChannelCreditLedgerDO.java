package cn.iocoder.yudao.module.channel.dal.dataobject.credit;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 智点流水
 */
@TableName("channel_credit_ledger")
@KeySequence("channel_credit_ledger_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelCreditLedgerDO extends TenantBaseDO {

    public static final String TYPE_RECHARGE = "recharge";
    public static final String TYPE_CONSUME = "consume";
    public static final String TYPE_ADJUST = "adjust";

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 类型：recharge 充值、consume 消耗、adjust 调整
     */
    private String type;
    /**
     * 变动数量，获得为正、消耗为负
     */
    private Long amount;
    /**
     * 变动后余额
     */
    private Long balanceAfter;
    /**
     * 产生消耗的渠道：mail、whatsapp
     */
    private String channel;
    /**
     * 关联对象：AI 回复记录编号
     */
    private Long refId;
    /**
     * 相关业务员（手动起草为操作人，自动回复为号码/邮箱所属人）
     */
    private Long userId;
    /**
     * 本次消耗的 token 数
     */
    private Integer tokens;
    /**
     * 备注
     */
    private String remark;

}
