package cn.iocoder.yudao.module.channel.dal.mysql.dashboard;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数据看板的聚合查询。多租户插件会自动给每张表加上 tenant_id 条件；逻辑删除需手动过滤。
 */
@Mapper
public interface ChannelDashboardMapper {

    @Select("SELECT DATE_FORMAT(sent_time, '%Y-%m-%d') AS d, account_id AS a, "
            + "SUM(direction = 1) AS inbound, SUM(direction = 1 AND replied = 1) AS replied, "
            + "SUM(direction = 2) AS outbound, SUM(direction = 2 AND ai_generated = 1) AS ai "
            + "FROM channel_mail_message WHERE deleted = 0 AND folder <> 'DRAFT' "
            + "AND sent_time >= #{start} AND sent_time < #{end} GROUP BY d, a")
    List<Map<String, Object>> mailStats(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT DATE_FORMAT(message_time, '%Y-%m-%d') AS d, account_id AS a, "
            + "SUM(direction = 1) AS inbound, SUM(direction = 2) AS outbound, SUM(direction = 2 AND ai_generated = 1) AS ai "
            + "FROM channel_wa_message WHERE deleted = 0 AND message_time >= #{start} AND message_time < #{end} GROUP BY d, a")
    List<Map<String, Object>> waStats(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 每个有来信的会话：首条来信时间、最后一条回复时间，用于计算回复率 */
    @Select("SELECT conversation_id AS c, MIN(CASE WHEN direction = 1 THEN message_time END) AS first_in, "
            + "MAX(CASE WHEN direction = 2 THEN message_time END) AS last_out "
            + "FROM channel_wa_message WHERE deleted = 0 AND message_time >= #{start} AND message_time < #{end} "
            + "GROUP BY conversation_id HAVING first_in IS NOT NULL")
    List<Map<String, Object>> waConversations(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT status AS s, reason AS r, COUNT(*) AS n FROM channel_ai_draft "
            + "WHERE deleted = 0 AND create_time >= #{start} AND create_time < #{end} GROUP BY status, reason")
    List<Map<String, Object>> drafts(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS d, -SUM(amount) AS n FROM channel_credit_ledger "
            + "WHERE deleted = 0 AND type = 'consume' AND create_time >= #{start} AND create_time < #{end} GROUP BY d")
    List<Map<String, Object>> credits(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT source AS s, COUNT(*) AS n, SUM(transform_status = 1) AS converted FROM crm_clue "
            + "WHERE deleted = 0 AND create_time >= #{start} AND create_time < #{end} GROUP BY source")
    List<Map<String, Object>> clues(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(*) FROM crm_customer WHERE deleted = 0 AND create_time >= #{start} AND create_time < #{end}")
    Long customers(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

}
