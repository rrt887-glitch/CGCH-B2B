package cn.iocoder.yudao.module.channel.dal.dataobject.assistant;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 对话助手的一条消息
 */
@TableName("channel_assistant_message")
@KeySequence("channel_assistant_message_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelAssistantMessageDO extends TenantBaseDO {

    // 角色：user 用户提问、assistant 助手回答
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    @TableId
    private Long id;
    /**
     * 会话编号
     */
    private Long chatId;
    /**
     * 角色：user、assistant
     */
    private String role;
    /**
     * 内容
     */
    private String content;
    /**
     * 本轮调用过的工具（JSON 数组：工具名、参数），用于展示「查了什么」
     */
    private String steps;
    /**
     * 消耗的智点
     */
    private Long credits;

}
