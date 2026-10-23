package cn.iocoder.yudao.module.channel.dal.mysql.dashboard;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 看板（数据分析）的明细查询：按时间顺序取消息，在内存里计算回复率、多轮沟通率等。
 * 多租户插件自动加 tenant_id 条件；accountIds / userIds 为 null 表示不限。
 */
@Mapper
public interface ChannelDashboardV3Mapper {

    String WA_ACCOUNTS = "<if test='accountIds != null'> AND m.account_id IN "
            + "<foreach collection='accountIds' item='x' open='(' separator=',' close=')'>#{x}</foreach></if>";
    String MAIL_ACCOUNTS = "<if test='accountIds != null'> AND account_id IN "
            + "<foreach collection='accountIds' item='x' open='(' separator=',' close=')'>#{x}</foreach></if>";
    String OWNERS = "<if test='userIds != null'> AND owner_user_id IN "
            + "<foreach collection='userIds' item='x' open='(' separator=',' close=')'>#{x}</foreach></if>";

    /** 即时消息（WhatsApp / Facebook / Instagram）：时间范围内有过往来的会话的消息，按会话、时间排序 */
    @Select("<script>SELECT m.conversation_id AS c, m.direction AS d, m.ai_generated AS ai, m.message_time AS t, c.intent AS intent "
            + "FROM channel_wa_message m JOIN channel_wa_conversation c ON c.id = m.conversation_id AND c.deleted = 0 "
            + "WHERE m.deleted = 0 AND m.message_time &gt;= #{start} AND m.message_time &lt; #{end}" + WA_ACCOUNTS
            + " ORDER BY m.conversation_id, m.message_time, m.id LIMIT 200000</script>")
    List<Map<String, Object>> waMessages(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                                         @Param("accountIds") Collection<Long> accountIds);

    /** 邮件：按会话线索（thread_key）归并，按时间排序；草稿箱除外 */
    @Select("<script>SELECT COALESCE(thread_key, CONCAT('m', id)) AS c, direction AS d, ai_generated AS ai, sent_time AS t, intent AS intent "
            + "FROM channel_mail_message WHERE deleted = 0 AND folder &lt;&gt; 'DRAFT' "
            + "AND sent_time &gt;= #{start} AND sent_time &lt; #{end}" + MAIL_ACCOUNTS
            + " ORDER BY c, sent_time, id LIMIT 200000</script>")
    List<Map<String, Object>> mailMessages(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                                           @Param("accountIds") Collection<Long> accountIds);

    @Select("<script>SELECT COUNT(*) FROM crm_clue WHERE deleted = 0 AND create_time &gt;= #{start} AND create_time &lt; #{end}"
            + OWNERS + "</script>")
    Long clueCount(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("userIds") Collection<Long> userIds);

    /** 商机：s 为当前阶段排序（1 初步回复 … 4 报价 … 8 赢单），e 为结束状态（1 赢单） */
    @Select("<script>SELECT b.id AS id, s.sort AS s, b.end_status AS e FROM crm_business b "
            + "LEFT JOIN crm_business_status s ON s.id = b.status_id "
            + "WHERE b.deleted = 0 AND b.create_time &gt;= #{start} AND b.create_time &lt; #{end}"
            + "<if test='userIds != null'> AND b.owner_user_id IN "
            + "<foreach collection='userIds' item='x' open='(' separator=',' close=')'>#{x}</foreach></if></script>")
    List<Map<String, Object>> businesses(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                                         @Param("userIds") Collection<Long> userIds);

    /** AI 完成的任务：深度背调、客户唤醒、助手执行（按完成记录计） */
    @Select("SELECT (SELECT COUNT(*) FROM channel_research WHERE deleted = 0 AND status = 'done' "
            + "AND create_time >= #{start} AND create_time < #{end}) "
            + "+ (SELECT COUNT(*) FROM channel_reactivate_item WHERE deleted = 0 AND status = 'sent' "
            + "AND update_time >= #{start} AND update_time < #{end}) "
            + "+ (SELECT COUNT(*) FROM channel_ai_report WHERE deleted = 0 AND create_time >= #{start} AND create_time < #{end})")
    Long aiTasks(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** AI 写入 CRM 的次数：AI 自动建档的线索（自动建档会标记为 AI 托管） */
    @Select("<script>SELECT COUNT(*) FROM crm_clue WHERE deleted = 0 AND ai_hosted = 1 "
            + "AND create_time &gt;= #{start} AND create_time &lt; #{end}" + OWNERS + "</script>")
    Long aiCrmWrites(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("userIds") Collection<Long> userIds);

}
