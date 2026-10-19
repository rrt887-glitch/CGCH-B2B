package cn.iocoder.yudao.module.channel.controller.admin.whatsapp.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 发送 WhatsApp 消息 Request VO；填 text 发文本，填 templateName 发模板")
@Data
public class ChannelWaSendReqVO {

    @Schema(description = "会话编号；与 accountId + to 二选一", example = "1")
    private Long conversationId;

    @Schema(description = "业务号码编号（新建会话时用）", example = "1")
    private Long accountId;

    @Schema(description = "客户号码，含国家码（新建会话时用）", example = "+14155550100")
    private String to;

    @Schema(description = "文本内容（24 小时窗口内）")
    @Size(max = 4096, message = "文本不能超过 4096 个字符")
    private String text;

    @Schema(description = "模板名称")
    private String templateName;

    @Schema(description = "模板语言", example = "en_US")
    private String templateLanguage;

    @Schema(description = "模板正文变量，依次对应 {{1}}、{{2}}…")
    private List<String> templateParams;

    @Schema(description = "模板预览文本（用于在聊天记录里显示）")
    private String templatePreview;

}
