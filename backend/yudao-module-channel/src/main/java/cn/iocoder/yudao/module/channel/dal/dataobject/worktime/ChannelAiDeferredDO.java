package cn.iocoder.yudao.module.channel.dal.dataobject.worktime;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 等待 AI 接手的消息：工作时间内业务员优先，到点仍无人回复时由 AI 处理。存库以免服务重启丢失。
 */
@TableName("channel_ai_deferred")
@KeySequence("channel_ai_deferred_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelAiDeferredDO extends TenantBaseDO {

    public static final String STATUS_WAITING = "waiting";
    /** 正在处理（已被某个实例领取） */
    public static final String STATUS_PROCESSING = "processing";
    /** 到点时业务员已回复，AI 不再处理 */
    public static final String STATUS_HUMAN_REPLIED = "human_replied";
    /** 已交给 AI 处理 */
    public static final String STATUS_AI_HANDLED = "ai_handled";
    /** 客户又发了新消息，由新消息重新计时 */
    public static final String STATUS_SUPERSEDED = "superseded";

    @TableId
    private Long id;
    /**
     * 渠道：mail、whatsapp
     */
    private String channel;
    /**
     * 邮件编号或 WhatsApp 会话编号
     */
    private Long refId;
    /**
     * 触发的消息编号（WhatsApp 为消息编号，邮件同 refId）
     */
    private Long messageId;
    /**
     * 到期时间
     */
    private LocalDateTime dueTime;
    /**
     * 状态：waiting、human_replied、ai_handled、superseded
     */
    private String status;

}
