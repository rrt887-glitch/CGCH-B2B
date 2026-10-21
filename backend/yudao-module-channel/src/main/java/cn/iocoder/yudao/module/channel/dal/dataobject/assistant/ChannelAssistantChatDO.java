package cn.iocoder.yudao.module.channel.dal.dataobject.assistant;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 对话助手的一次会话（每个业务员只能看到自己的会话）
 */
@TableName("channel_assistant_chat")
@KeySequence("channel_assistant_chat_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelAssistantChatDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 所属用户
     */
    private Long userId;
    /**
     * 标题（取第一句提问）
     */
    private String title;

}
