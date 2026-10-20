package cn.iocoder.yudao.module.channel.dal.dataobject.credit;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 智点在线充值订单
 */
@TableName("channel_credit_order")
@KeySequence("channel_credit_order_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelCreditOrderDO extends TenantBaseDO {

    @TableId
    private Long id;
    /** 下单人 */
    private Long userId;
    /** 套餐编号 */
    private Long packageId;
    /** 套餐名 */
    private String packageName;
    /** 到账智点 */
    private Long credits;
    /** 金额，单位：分 */
    private Integer price;
    /** 支付单编号 */
    private Long payOrderId;
    /** 是否已支付 */
    private Boolean payStatus;
    /** 支付时间 */
    private LocalDateTime payTime;
    /** 支付渠道 */
    private String payChannelCode;

}
