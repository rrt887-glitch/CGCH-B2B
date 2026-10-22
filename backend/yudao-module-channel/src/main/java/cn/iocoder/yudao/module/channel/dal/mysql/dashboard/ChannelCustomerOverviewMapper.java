package cn.iocoder.yudao.module.channel.dal.mysql.dashboard;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 客户概览的聚合查询。userIds 为 null 表示可看全部（含公海客户），否则只统计这些负责人名下的客户。
 */
@Mapper
public interface ChannelCustomerOverviewMapper {

    String OWNER = "<if test='userIds != null'> AND owner_user_id IN "
            + "<foreach collection='userIds' item='x' open='(' separator=',' close=')'>#{x}</foreach></if>";

    @Select("<script>SELECT COUNT(*) AS total, SUM(deal_status = 1) AS dealt, SUM(create_time &gt;= #{since}) AS created, "
            + "SUM(owner_user_id IS NULL) AS pool, SUM(ai_hosted = 1) AS hosted, "
            + "SUM(contact_last_time &gt;= #{now} - INTERVAL 7 DAY) AS d7, "
            + "SUM(contact_last_time &lt; #{now} - INTERVAL 7 DAY AND contact_last_time &gt;= #{now} - INTERVAL 30 DAY) AS d30, "
            + "SUM(contact_last_time &lt; #{now} - INTERVAL 30 DAY AND contact_last_time &gt;= #{now} - INTERVAL 90 DAY) AS d90, "
            + "SUM(contact_last_time &lt; #{now} - INTERVAL 90 DAY) AS older, SUM(contact_last_time IS NULL) AS never, "
            + "SUM(contact_next_time IS NOT NULL AND contact_next_time &lt; #{now}) AS overdue "
            + "FROM crm_customer WHERE deleted = 0" + OWNER + "</script>")
    Map<String, Object> totals(@Param("now") LocalDateTime now, @Param("since") LocalDateTime since,
                               @Param("userIds") Collection<Long> userIds);

    @Select("<script>SELECT IFNULL(NULLIF(country_code, ''), '?') AS k, COUNT(*) AS n, SUM(deal_status = 1) AS dealt "
            + "FROM crm_customer WHERE deleted = 0" + OWNER + " GROUP BY k ORDER BY n DESC LIMIT 15</script>")
    List<Map<String, Object>> byCountry(@Param("userIds") Collection<Long> userIds);

    @Select("<script>SELECT IFNULL(source, 0) AS k, COUNT(*) AS n, SUM(deal_status = 1) AS dealt "
            + "FROM crm_customer WHERE deleted = 0" + OWNER + " GROUP BY k ORDER BY n DESC</script>")
    List<Map<String, Object>> bySource(@Param("userIds") Collection<Long> userIds);

    @Select("<script>SELECT IFNULL(level, 0) AS k, COUNT(*) AS n, SUM(deal_status = 1) AS dealt "
            + "FROM crm_customer WHERE deleted = 0" + OWNER + " GROUP BY k ORDER BY k</script>")
    List<Map<String, Object>> byLevel(@Param("userIds") Collection<Long> userIds);

    /** 最近 12 个月每月新增客户 */
    @Select("<script>SELECT DATE_FORMAT(create_time, '%Y-%m') AS m, COUNT(*) AS n FROM crm_customer "
            + "WHERE deleted = 0 AND create_time &gt;= #{since}" + OWNER + " GROUP BY m ORDER BY m</script>")
    List<Map<String, Object>> monthly(@Param("since") LocalDateTime since, @Param("userIds") Collection<Long> userIds);

    /** 商机按阶段：进行中的商机数与金额（end_status 为空表示未结束） */
    @Select("<script>SELECT s.id AS id, s.name AS name, s.sort AS sort, s.percent AS percent, "
            + "COUNT(b.id) AS n, IFNULL(SUM(b.total_price), 0) AS amount "
            + "FROM crm_business_status s LEFT JOIN crm_business b ON b.status_id = s.id AND b.deleted = 0 "
            + "AND b.end_status IS NULL<if test='userIds != null'> AND b.owner_user_id IN "
            + "<foreach collection='userIds' item='x' open='(' separator=',' close=')'>#{x}</foreach></if> "
            + "WHERE s.deleted = 0 AND s.type_id = #{typeId} GROUP BY s.id, s.name, s.sort, s.percent ORDER BY s.sort</script>")
    List<Map<String, Object>> funnel(@Param("typeId") Long typeId, @Param("userIds") Collection<Long> userIds);

    /** 已结束的商机：end_status 1 赢单、2 输单、3 无效 */
    @Select("<script>SELECT end_status AS k, COUNT(*) AS n, IFNULL(SUM(total_price), 0) AS amount FROM crm_business "
            + "WHERE deleted = 0 AND end_status IS NOT NULL AND status_type_id = #{typeId}"
            + "<if test='userIds != null'> AND owner_user_id IN <foreach collection='userIds' item='x' open='(' separator=',' close=')'>#{x}</foreach></if>"
            + " GROUP BY end_status</script>")
    List<Map<String, Object>> ended(@Param("typeId") Long typeId, @Param("userIds") Collection<Long> userIds);

    @Select("SELECT id FROM crm_business_status_type WHERE deleted = 0 ORDER BY id LIMIT 1")
    Long defaultStatusType();

    /** 期间内往来消息最多的客户（邮件 + WhatsApp） */
    @Select("<script>SELECT c.id AS id, c.name AS name, c.company_name AS company, c.country_code AS country, "
            + "c.owner_user_id AS ownerUserId, c.deal_status AS dealt, c.contact_last_time AS lastContact, "
            + "(SELECT COUNT(*) FROM channel_mail_message m WHERE m.deleted = 0 AND m.customer_id = c.id AND m.sent_time &gt;= #{since}) AS mails, "
            + "(SELECT COUNT(*) FROM channel_wa_message w JOIN channel_wa_conversation v ON v.id = w.conversation_id AND v.deleted = 0 "
            + " WHERE w.deleted = 0 AND v.customer_id = c.id AND w.message_time &gt;= #{since}) AS was "
            + "FROM crm_customer c WHERE c.deleted = 0"
            + "<if test='userIds != null'> AND c.owner_user_id IN <foreach collection='userIds' item='x' open='(' separator=',' close=')'>#{x}</foreach></if>"
            + " ORDER BY mails + was DESC, c.id DESC LIMIT 10</script>")
    List<Map<String, Object>> topActive(@Param("since") LocalDateTime since, @Param("userIds") Collection<Long> userIds);

}
