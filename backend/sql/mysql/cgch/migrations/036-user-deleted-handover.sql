-- 036：修正历史孤儿数据
--
-- 背景：以前删掉成员时没有处理他名下的业务数据，导致会话/渠道账号/CRM 的负责人指向已删除的账号
-- （例如 HIG 有 32 条会话的负责人是被删掉的 249）。这里统一转给该公司的管理员（开户时填的联系人）。
-- 可重复执行：转完之后不再有「负责人已删除」的行，再跑是空操作。

-- 1. 会话负责人
UPDATE channel_wa_conversation c
    JOIN system_users u ON u.id = c.owner_user_id AND u.deleted = b'1'
    JOIN system_tenant t ON t.id = c.tenant_id
    JOIN system_users a ON a.id = t.contact_user_id AND a.deleted = b'0'
SET c.owner_user_id = t.contact_user_id
WHERE c.owner_user_id IS NOT NULL
  AND t.contact_user_id IS NOT NULL
  AND t.contact_user_id <> c.owner_user_id;

-- 2. 渠道账号归属
UPDATE channel_wa_account wa
    JOIN system_users u ON u.id = wa.user_id AND u.deleted = b'1'
    JOIN system_tenant t ON t.id = wa.tenant_id
    JOIN system_users a ON a.id = t.contact_user_id AND a.deleted = b'0'
SET wa.user_id = t.contact_user_id
WHERE wa.user_id IS NOT NULL
  AND t.contact_user_id IS NOT NULL;

-- 3. CRM：客户 / 线索 / 商机 / 合同 / 回款 的负责人
UPDATE crm_customer x
    JOIN system_users u ON u.id = x.owner_user_id AND u.deleted = b'1'
    JOIN system_tenant t ON t.id = x.tenant_id
    JOIN system_users a ON a.id = t.contact_user_id AND a.deleted = b'0'
SET x.owner_user_id = t.contact_user_id
WHERE x.owner_user_id IS NOT NULL AND t.contact_user_id IS NOT NULL;

UPDATE crm_clue x
    JOIN system_users u ON u.id = x.owner_user_id AND u.deleted = b'1'
    JOIN system_tenant t ON t.id = x.tenant_id
    JOIN system_users a ON a.id = t.contact_user_id AND a.deleted = b'0'
SET x.owner_user_id = t.contact_user_id
WHERE x.owner_user_id IS NOT NULL AND t.contact_user_id IS NOT NULL;

UPDATE crm_business x
    JOIN system_users u ON u.id = x.owner_user_id AND u.deleted = b'1'
    JOIN system_tenant t ON t.id = x.tenant_id
    JOIN system_users a ON a.id = t.contact_user_id AND a.deleted = b'0'
SET x.owner_user_id = t.contact_user_id
WHERE x.owner_user_id IS NOT NULL AND t.contact_user_id IS NOT NULL;

UPDATE crm_contract x
    JOIN system_users u ON u.id = x.owner_user_id AND u.deleted = b'1'
    JOIN system_tenant t ON t.id = x.tenant_id
    JOIN system_users a ON a.id = t.contact_user_id AND a.deleted = b'0'
SET x.owner_user_id = t.contact_user_id
WHERE x.owner_user_id IS NOT NULL AND t.contact_user_id IS NOT NULL;

UPDATE crm_receivable x
    JOIN system_users u ON u.id = x.owner_user_id AND u.deleted = b'1'
    JOIN system_tenant t ON t.id = x.tenant_id
    JOIN system_users a ON a.id = t.contact_user_id AND a.deleted = b'0'
SET x.owner_user_id = t.contact_user_id
WHERE x.owner_user_id IS NOT NULL AND t.contact_user_id IS NOT NULL;

-- 4. 已删除账号的协作权限记录
DELETE p FROM crm_permission p
    JOIN system_users u ON u.id = p.user_id AND u.deleted = b'1';
