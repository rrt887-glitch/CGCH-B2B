package cn.iocoder.yudao.module.system.controller.admin.user.vo.profile;

import cn.iocoder.yudao.module.system.enums.PasswordPolicy;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import jakarta.validation.constraints.NotEmpty;

@Schema(description = "管理后台 - 用户个人中心更新密码 Request VO")
@Data
public class UserProfileUpdatePasswordReqVO {

    @Schema(description = "旧密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456")
    @NotEmpty(message = "旧密码不能为空")
    @Length(min = 4, max = 64, message = "密码长度为 4-64 位")
    private String oldPassword;

    @Schema(description = "新密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "654321")
    @NotEmpty(message = "新密码不能为空")
    @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
    private String newPassword;

}
