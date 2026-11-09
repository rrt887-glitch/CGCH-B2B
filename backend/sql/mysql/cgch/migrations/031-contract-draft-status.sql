-- 之前新建的订单 / 合同没有设置审核状态（为空），无法编辑、在「未提交」里也筛不到：补为草稿（未提交）。可重复执行
SET NAMES utf8mb4;
UPDATE `crm_contract` SET `audit_status` = 0 WHERE `audit_status` IS NULL;
