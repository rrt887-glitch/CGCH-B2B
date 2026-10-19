package cn.iocoder.yudao.module.channel.controller.admin.mail.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 邮件分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ChannelMailMessagePageReqVO extends PageParam {

    @Schema(description = "邮箱账号编号，不填表示我的全部邮箱", example = "1")
    private Long accountId;

    @Schema(description = "视图：inbox 收件箱、unreplied 未回复、sent 已发送、draft 草稿、trash 已删除", example = "inbox")
    private String view;

    @Schema(description = "关键字：主题、发件人、收件人", example = "quotation")
    private String keyword;

    @Schema(description = "关联客户编号", example = "1")
    private Long customerId;

}
