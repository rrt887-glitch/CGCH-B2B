package cn.iocoder.yudao.module.channel.controller.admin.reactivate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 创建客户唤醒任务")
@Data
public class ChannelReactivateTaskCreateReqVO {

    @Schema(description = "任务名称，不填自动生成")
    private String name;
    @Schema(description = "筛选时使用的未联系天数（记录用）", example = "90")
    private Integer inactiveDays;
    @Schema(description = "选中的客户编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> customerIds;
    @Schema(description = "渠道：auto 有邮箱发邮件否则 WhatsApp、mail、whatsapp", example = "auto")
    private String channel;
    @Schema(description = "给 AI 的写作要求", example = "介绍我们 2027 年的新款吹风机，可免费寄样")
    private String instructions;
    @Schema(description = "WhatsApp 模板名称（发 WhatsApp 时必填）", example = "hello_world")
    private String waTemplateName;
    @Schema(description = "WhatsApp 模板语言", example = "en_US")
    private String waTemplateLanguage;

}
