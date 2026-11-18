-- 037：清理重复的线索规则
--
-- 默认规则是「第一次访问时插入、只判断表是否为空」，并发首次访问会各插一套（生产上平台租户已经出现 2 组重复）。
-- 这里按「公司 + 名称 + 匹配字段 + 关键词」去重，只保留最早的一条。可重复执行。

DELETE r1 FROM channel_lead_rule r1
    JOIN channel_lead_rule r2
      ON r1.tenant_id = r2.tenant_id
     AND r1.name = r2.name
     AND r1.match_field = r2.match_field
     AND r1.keyword = r2.keyword
     AND r1.id > r2.id;
