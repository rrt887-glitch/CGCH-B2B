package cn.iocoder.yudao.module.channel.controller.admin.whatsapp.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Schema(description = "管理后台 - 收件箱数量 Response VO")
@Data
public class ChannelWaInboxCountsRespVO {

    @Schema(description = "我的会话：进行中")
    private long open;
    @Schema(description = "我的会话：待处理")
    private long pending;
    @Schema(description = "我的会话：已解决")
    private long resolved;
    @Schema(description = "团队会话：进行中（主管可见范围内同事负责的）")
    private long teamOpen;
    @Schema(description = "每个号码的未读消息数：号码编号 → 未读条数")
    private Map<Long, Integer> unreadByAccount;

}
