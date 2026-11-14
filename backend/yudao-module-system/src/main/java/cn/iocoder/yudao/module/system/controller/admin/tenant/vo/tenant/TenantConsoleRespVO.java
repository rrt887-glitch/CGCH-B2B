package cn.iocoder.yudao.module.system.controller.admin.tenant.vo.tenant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 平台「客户管理」列表：在租户信息之外，带上管理员账号和已用账号数。
 * 只给平台运营看开户信息，不包含客户的任何业务数据。
 */
@Schema(description = "管理后台 - 客户管理列表 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class TenantConsoleRespVO extends TenantRespVO {

    @Schema(description = "管理员登录账号", example = "boss@example.com")
    private String adminUsername;

    @Schema(description = "管理员姓名", example = "张三")
    private String adminNickname;

    @Schema(description = "已建账号数", example = "3")
    private Long usedAccountCount;

    @Schema(description = "套餐名称", example = "客户标准版")
    private String packageName;

    @Schema(description = "是否平台自己（系统租户）", example = "false")
    private Boolean platform;

}
