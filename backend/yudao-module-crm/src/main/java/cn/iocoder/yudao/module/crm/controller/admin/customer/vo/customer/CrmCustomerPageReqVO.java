package cn.iocoder.yudao.module.crm.controller.admin.customer.vo.customer;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.crm.enums.common.CrmSceneTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - CRM 客户分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CrmCustomerPageReqVO extends PageParam {

    /**
     * 联系状态 - 今日需联系
     */
    public static final int CONTACT_TODAY = 1;
    /**
     * 联系状态 - 已逾期
     */
    public static final int CONTACT_EXPIRED = 2;
    /**
     * 联系状态 - 已联系
     */
    public static final int CONTACT_ALREADY = 3;

    @Schema(description = "客户名称", example = "赵六")
    private String name;

    @Schema(description = "手机", example = "18000000000")
    private String mobile;

    @Schema(description = "所属行业", example = "1")
    private Integer industryId;

    @Schema(description = "客户等级", example = "1")
    private Integer level;

    @Schema(description = "客户来源", example = "1")
    private Integer source;

    @Schema(description = "场景类型", example = "1")
    @InEnum(CrmSceneTypeEnum.class)
    private Integer sceneType; // 场景类型，为 null 时则表示全部

    @Schema(description = "是否为公海数据", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    private Boolean pool; // null 则表示为不是公海数据

    @Schema(description = "联系状态", example = "1")
    private Integer contactStatus; // backlog 查询条件

    @Schema(description = "跟进状态", example = "true")
    private Boolean followUpStatus;


    // ========== CGCH 外贸筛选 ==========

    @Schema(description = "国家/地区二字码", example = "US")
    private String countryCode;

    @Schema(description = "客户分组编号", example = "1")
    private Long groupId;

    @Schema(description = "是否 AI 托管", example = "true")
    private Boolean aiHosted;


    @Schema(description = "标签编号", example = "1")
    private Long tagId;

    @Schema(description = "是否特别关注", example = "true")
    private Boolean starred;

    @Schema(description = "客户阶段（字典 crm_customer_stage）", example = "1")
    private Integer stage;

    @Schema(description = "最近联系时间", example = "[2023-01-01 00:00:00, 2023-01-31 23:59:59]")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] contactLastTime;

}
