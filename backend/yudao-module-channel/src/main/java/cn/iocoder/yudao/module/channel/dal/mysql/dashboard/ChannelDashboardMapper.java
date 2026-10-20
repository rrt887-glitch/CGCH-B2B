package cn.iocoder.yudao.module.channel.dal.mysql.dashboard;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 数据看板的聚合查询。多租户插件会自动给每张表加上 tenant_id 条件；逻辑删除需手动过滤。
 * accountIds / userIds 为 null 表示不限（可看全部数据）；非 null 时只统计这些账号 / 业务员。
 */
@Mapper
public interface ChannelDashboardMapper {

    String ACCOUNTS = "<if test='accountIds != null'> AND account_id IN "
            + "<foreach collection='accountIds' item='x' open='(' separator=',' close=')'>#{x}</foreach></if>";

    @Select("<script>SELECT DATE_FORMAT(sent_time, '%Y-%m-%d') AS d, account_id AS a, "
            + "SUM(direction = 1) AS inbound, SUM(direction = 1 AND replied = 1) AS replied, "
            + "SUM(direction = 2) AS outbound, SUM(direction = 2 AND ai_generated = 1) AS ai "
            + "FROM channel_mail_message WHERE deleted = 0 AND folder &lt;&gt; 'DRAFT' "
            + "AND sent_time &gt;= #{start} AND sent_time &lt; #{end}" + ACCOUNTS + " GROUP BY d, a</script>")
    List<Map<String, Object>> mailStats(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                                        @Param("accountIds") Collection<Long> accountIds);

    @Select("<script>SELECT DATE_FORMAT(message_time, '%Y-%m-%d') AS d, account_id AS a, "
            + "SUM(direction = 1) AS inbound, SUM(direction = 2) AS outbound, SUM(direction = 2 AND ai_generated = 1) AS ai "
            + "FROM channel_wa_message WHERE deleted = 0 AND message_time &gt;= #{start} AND message_time &lt; #{end}"
            + ACCOUNTS + " GROUP BY d, a</script>")
    List<Map<String, Object>> waStats(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                                      @Param("accountIds") Collection<Long> accountIds);

    /** 每个有来信的会话：首条来信时间、最后一条回复时间，用于计算回复率 */
    @Select("<script>SELECT conversation_id AS c, MIN(CASE WHEN direction = 1 THEN message_time END) AS first_in, "
            + "MAX(CASE WHEN direction = 2 THEN message_time END) AS last_out "
            + "FROM channel_wa_message WHERE deleted = 0 AND message_time &gt;= #{start} AND message_time &lt; #{end}"
            + ACCOUNTS + " GROUP BY conversation_id HAVING first_in IS NOT NULL</script>")
    List<Map<String, Object>> waConversations(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                                              @Param("accountIds") Collection<Long> accountIds);

    /** 不限范围时直接按草稿表统计 */
    @Select("SELECT status AS s, reason AS r, COUNT(*) AS n FROM channel_ai_draft "
            + "WHERE deleted = 0 AND create_time >= #{start} AND create_time < #{end} GROUP BY status, reason")
    List<Map<String, Object>> drafts(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 限定账号时：邮件草稿经邮件找到账号 */
    @Select("<script>SELECT d.status AS s, d.reason AS r, COUNT(*) AS n FROM channel_ai_draft d "
            + "JOIN channel_mail_message m ON m.id = d.ref_id AND m.deleted = 0 "
            + "WHERE d.deleted = 0 AND d.channel = 'mail' AND d.create_time &gt;= #{start} AND d.create_time &lt; #{end} "
            + "AND m.account_id IN <foreach collection='accountIds' item='x' open='(' separator=',' close=')'>#{x}</foreach> "
            + "GROUP BY d.status, d.reason</script>")
    List<Map<String, Object>> mailDrafts(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                                         @Param("accountIds") Collection<Long> accountIds);

    /** 限定账号时：WhatsApp 草稿经会话找到账号 */
    @Select("<script>SELECT d.status AS s, d.reason AS r, COUNT(*) AS n FROM channel_ai_draft d "
            + "JOIN channel_wa_conversation c ON c.id = d.ref_id AND c.deleted = 0 "
            + "WHERE d.deleted = 0 AND d.channel = 'whatsapp' AND d.create_time &gt;= #{start} AND d.create_time &lt; #{end} "
            + "AND c.account_id IN <foreach collection='accountIds' item='x' open='(' separator=',' close=')'>#{x}</foreach> "
            + "GROUP BY d.status, d.reason</script>")
    List<Map<String, Object>> waDrafts(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                                       @Param("accountIds") Collection<Long> accountIds);

    @Select("<script>SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS d, -SUM(amount) AS n FROM channel_credit_ledger "
            + "WHERE deleted = 0 AND type = 'consume' AND create_time &gt;= #{start} AND create_time &lt; #{end}"
            + "<if test='userIds != null'> AND user_id IN "
            + "<foreach collection='userIds' item='x' open='(' separator=',' close=')'>#{x}</foreach></if> GROUP BY d</script>")
    List<Map<String, Object>> credits(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                                      @Param("userIds") Collection<Long> userIds);

    @Select("<script>SELECT source AS s, COUNT(*) AS n, SUM(transform_status = 1) AS converted FROM crm_clue "
            + "WHERE deleted = 0 AND create_time &gt;= #{start} AND create_time &lt; #{end}"
            + "<if test='userIds != null'> AND owner_user_id IN "
            + "<foreach collection='userIds' item='x' open='(' separator=',' close=')'>#{x}</foreach></if> GROUP BY source</script>")
    List<Map<String, Object>> clues(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                                    @Param("userIds") Collection<Long> userIds);

    @Select("<script>SELECT COUNT(*) FROM crm_customer WHERE deleted = 0 AND create_time &gt;= #{start} AND create_time &lt; #{end}"
            + "<if test='userIds != null'> AND owner_user_id IN "
            + "<foreach collection='userIds' item='x' open='(' separator=',' close=')'>#{x}</foreach></if></script>")
    Long customers(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                   @Param("userIds") Collection<Long> userIds);

}
