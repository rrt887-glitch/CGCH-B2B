package cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 会话里只给团队看的记录：内部备注（客户看不到）和会话动态（指派、状态、优先级变化），在聊天记录里按时间穿插显示
 */
@TableName("channel_wa_conversation_event")
@KeySequence("channel_wa_conversation_event_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelWaConversationEventDO extends TenantBaseDO {

    public static final String KIND_NOTE = "note";
    public static final String KIND_ACTIVITY = "activity";

    @TableId
    private Long id;
    /** 会话编号 */
    private Long conversationId;
    /** 类型：note 内部备注、activity 会话动态 */
    private String kind;
    /** 内容 */
    private String content;
    /** 操作人；为空表示系统 */
    private Long userId;

}
