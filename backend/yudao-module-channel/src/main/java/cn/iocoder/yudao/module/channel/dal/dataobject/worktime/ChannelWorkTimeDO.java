package cn.iocoder.yudao.module.channel.dal.dataobject.worktime;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 工作时间：决定 AI 在上班时间和下班时间分别怎么接待。每个租户一条。
 */
@TableName("channel_work_time")
@KeySequence("channel_work_time_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelWorkTimeDO extends TenantBaseDO {

    /** 工作时间内：业务员优先，超时未回复由 AI 接手 */
    public static final String IN_HUMAN_FIRST = "human_first";
    /** 工作时间内：AI 立即回复 */
    public static final String IN_AI = "ai";
    /** 工作时间内：AI 只起草，由业务员发送 */
    public static final String IN_DRAFT = "draft";
    /** 非工作时间：AI 自动回复 */
    public static final String OFF_AI = "ai";
    /** 非工作时间：AI 只起草 */
    public static final String OFF_DRAFT = "draft";

    @TableId
    private Long id;
    /**
     * 是否启用工作时间；关闭时按数字员工的回复模式全天处理
     */
    private Boolean enabled;
    /**
     * 公司所在时区，例如 Asia/Shanghai
     */
    private String timezone;
    /**
     * 每周安排（JSON 数组）：[{"day":1,"enabled":true,"ranges":"09:00-12:00,13:30-18:00"}, ...]，day 1=周一 … 7=周日
     */
    private String schedule;
    /**
     * 节假日（JSON 数组，yyyy-MM-dd），当天全天休息
     */
    private String holidays;
    /**
     * 调休上班日（JSON 数组，yyyy-MM-dd），当天按周一的时间上班
     */
    private String extraWorkdays;
    /**
     * 工作时间内的处理方式：human_first、ai、draft
     */
    private String inHoursPolicy;
    /**
     * 业务员优先时，客户来信后等待多少分钟无人回复再由 AI 接手
     */
    private Integer takeoverMinutes;
    /**
     * 非工作时间的处理方式：ai、draft
     */
    private String offHoursPolicy;

}
