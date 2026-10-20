package cn.iocoder.yudao.module.channel.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - AI 数字员工配置；保存时 llmApiKey 不填表示不变，查询时不返回")
@Data
public class ChannelAiConfigVO {

    @Schema(description = "数字员工名称", example = "Lily")
    @Size(max = 64)
    private String agentName;

    @Schema(description = "个性：professional、concise、friendly、proactive、calm", example = "professional")
    private String personality;

    @Schema(description = "默认答复语种", example = "English")
    private String defaultLanguage;

    @Schema(description = "大模型接口地址（OpenAI 兼容）", example = "https://api.deepseek.com/v1")
    private String llmBaseUrl;

    @Schema(description = "大模型 API Key")
    private String llmApiKey;

    @Schema(description = "模型名称", example = "deepseek-chat")
    private String llmModel;

    @Schema(description = "邮件回复模式：off、draft、auto", example = "draft")
    private String mailMode;

    @Schema(description = "WhatsApp 回复模式：off、draft、auto", example = "auto")
    private String waMode;

    @Schema(description = "邮件签名")
    @Size(max = 1000)
    private String mailSignature;

    @Schema(description = "业务员人工回复后 AI 暂停自动回复的小时数，0 表示不暂停", example = "12")
    @Min(0) @Max(720)
    private Integer humanPauseHours;

    @Schema(description = "补充要求")
    @Size(max = 1000)
    private String extraInstructions;

    @Schema(description = "新询盘自动建 CRM 线索", example = "true")
    private Boolean leadAutoCreate;

    @Schema(description = "建档时用 AI 提取公司、国家、需求产品（消耗智点）", example = "true")
    private Boolean leadAiExtract;

    @Schema(description = "是否已填写 API Key（仅查询返回）")
    private Boolean hasApiKey;

}
