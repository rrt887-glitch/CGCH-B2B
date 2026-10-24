package cn.iocoder.yudao.module.channel.dal.dataobject.routing;

import cn.iocoder.yudao.framework.mybatis.core.type.LongListTypeHandler;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.util.List;

/**
 * 消息分流规则：新询盘来自哪些渠道、满足哪些条件时，分配给哪些业务员（多人时轮流分配）。
 * 只对新建档的询盘生效；已被转派的客户不再执行自动分配。
 */
@TableName(value = "channel_routing_rule", autoResultMap = true)
@KeySequence("channel_routing_rule_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelRoutingRuleDO extends TenantBaseDO {

    /** 渠道：WhatsApp、Facebook 主页、Instagram、官网IM、邮箱、留资邮箱（阿里国际站、中国制造网等平台询盘） */
    public static final List<String> SOURCES = List.of("whatsapp", "messenger", "instagram", "web", "mail", "platform");

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 规则名称
     */
    private String name;
    /**
     * 是否启用
     */
    private Boolean enabled;
    /**
     * 排序，越小越优先
     */
    private Integer sort;
    /**
     * 消息来源渠道，逗号分隔，为空表示全部渠道
     */
    private String sources;
    /**
     * 国家条件：in 属于、not_in 不属于；为空表示不限国家
     */
    private String countryOp;
    /**
     * 国家二字码，逗号分隔
     */
    private String countries;
    /**
     * 负责人编号列表（多人时轮流分配）
     */
    @TableField(typeHandler = LongListTypeHandler.class)
    private List<Long> assigneeIds;
    /**
     * 轮流分配的下一位（负责人列表中的下标）
     */
    private Integer nextIndex;

}
