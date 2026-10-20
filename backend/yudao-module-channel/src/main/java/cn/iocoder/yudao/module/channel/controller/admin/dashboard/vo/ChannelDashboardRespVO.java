package cn.iocoder.yudao.module.channel.controller.admin.dashboard.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 数据看板")
@Data
public class ChannelDashboardRespVO {

    @Schema(description = "统计天数")
    private Integer days;
    @Schema(description = "开始日期 yyyy-MM-dd")
    private String startDate;
    @Schema(description = "本期")
    private Totals current;
    @Schema(description = "上一个同等长度周期，用于环比")
    private Totals previous;
    @Schema(description = "按天")
    private List<Day> daily = new ArrayList<>();
    @Schema(description = "待人工处理的 AI 草稿原因分布：reason → 数量")
    private Map<String, Long> pendingReasons = new LinkedHashMap<>();
    @Schema(description = "按业务员（渠道账号所属人）")
    private List<UserRow> users = new ArrayList<>();

    @Data
    public static class Totals {
        private long mailIn;
        private long mailReplied;
        private long mailOut;
        private long mailAiOut;
        private long waIn;
        private long waConversations;
        private long waRepliedConversations;
        private long waOut;
        private long waAiOut;
        private long aiDrafts;
        private long aiSent;
        private long aiPending;
        private long aiDiscarded;
        private long aiHandoff;
        private long creditsConsumed;
        private long cluesCreated;
        private long cluesFromMail;
        private long cluesFromWhatsApp;
        private long cluesConverted;
        private long customersCreated;
        /** 新询盘 = 来信数 + 有来信的 WhatsApp 会话数 */
        private long inquiries;
        /** 回复率（0~1）：已回复的邮件 + 已回复的会话 / 新询盘 */
        private double replyRate;
        /** AI 发送占比（0~1）：AI 发出的消息 / 全部发出的消息 */
        private double aiShare;
    }

    @Data
    public static class Day {
        private String date;
        private long mailIn;
        private long waIn;
        private long humanOut;
        private long aiOut;
        private long credits;
    }

    @Data
    public static class UserRow {
        private Long userId;
        private long mailIn;
        private long waIn;
        private long out;
        private long aiOut;
    }

}
