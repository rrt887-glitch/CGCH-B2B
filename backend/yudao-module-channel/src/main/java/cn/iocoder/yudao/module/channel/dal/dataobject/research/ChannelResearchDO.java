package cn.iocoder.yudao.module.channel.dal.dataobject.research;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 深度背调：根据客户公司官网的公开信息和我方往来记录，由 AI 整理成背调报告
 */
@TableName("channel_research")
@KeySequence("channel_research_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelResearchDO extends TenantBaseDO {

    // 状态：running 进行中、done 完成、failed 失败
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_FAILED = "failed";

    @TableId
    private Long id;
    /**
     * 公司名称（输入或识别结果）
     */
    private String companyName;
    /**
     * 官网
     */
    private String website;
    /**
     * 关联的 CRM 客户
     */
    private Long customerId;
    /**
     * 关联的 CRM 线索
     */
    private Long clueId;
    /**
     * 状态：running、done、failed
     */
    private String status;
    /**
     * 背调报告（JSON）
     */
    private String report;
    /**
     * 参考来源（JSON 数组）：网址、标题、摘要
     */
    private String sources;
    /**
     * 失败原因或提示
     */
    private String reasonDetail;
    /**
     * 消耗的智点
     */
    private Long credits;
    /**
     * 发起人
     */
    private Long userId;

}
