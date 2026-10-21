package cn.iocoder.yudao.module.channel.dal.dataobject.reactivate;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 客户唤醒任务：选一批久未联系的客户，由 AI 写跟进内容，业务员确认后批量发出
 */
@TableName("channel_reactivate_task")
@KeySequence("channel_reactivate_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelReactivateTaskDO extends TenantBaseDO {

    // 状态：generating 正在生成、ready 待确认、sending 发送中、done 已完成
    public static final String STATUS_GENERATING = "generating";
    public static final String STATUS_READY = "ready";
    public static final String STATUS_SENDING = "sending";
    public static final String STATUS_DONE = "done";

    @TableId
    private Long id;
    /**
     * 任务名称
     */
    private String name;
    /**
     * 筛选条件：多少天未联系
     */
    private Integer inactiveDays;
    /**
     * 渠道偏好：auto 有邮箱发邮件否则 WhatsApp、mail 只发邮件、whatsapp 只发 WhatsApp
     */
    private String channel;
    /**
     * 给 AI 的写作要求，例如介绍新品、节日问候
     */
    private String instructions;
    /**
     * WhatsApp 模板名称（超过 24 小时只能发已审核的模板）
     */
    private String waTemplateName;
    /**
     * WhatsApp 模板语言
     */
    private String waTemplateLanguage;
    /**
     * 状态：generating、ready、sending、done
     */
    private String status;
    /**
     * 创建人
     */
    private Long userId;

}
