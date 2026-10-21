package cn.iocoder.yudao.module.channel.dal.dataobject.ai;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDate;

/**
 * AI 工作报告（日报 / 周报）
 */
@TableName("channel_ai_report")
@KeySequence("channel_ai_report_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelAiReportDO extends TenantBaseDO {

    public static final String TYPE_DAILY = "daily";
    public static final String TYPE_WEEKLY = "weekly";

    @TableId
    private Long id;
    /**
     * 类型：daily 日报、weekly 周报
     */
    private String periodType;
    /**
     * 统计开始日期（含）
     */
    private LocalDate periodStart;
    /**
     * 统计结束日期（含）
     */
    private LocalDate periodEnd;
    /**
     * 统计数据（JSON）：本期与上期汇总、转人工清单、知识库缺口
     */
    private String stats;
    /**
     * 文字总结
     */
    private String summary;
    /**
     * 总结是否由大模型撰写；否则为按模板生成
     */
    private Boolean aiGenerated;
    /**
     * 撰写总结消耗的智点
     */
    private Long credits;
    /**
     * 生成方式：auto 定时、manual 手动（trigger 是 MySQL 保留字，故用 generate_mode）
     */
    private String generateMode;
    /**
     * 手动生成的操作人
     */
    private Long userId;

}
