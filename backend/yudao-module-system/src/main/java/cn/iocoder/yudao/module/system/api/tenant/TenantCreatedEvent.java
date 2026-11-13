package cn.iocoder.yudao.module.system.api.tenant;

/**
 * 新开一家客户（租户）后发布的事件，在开户的同一个事务里同步执行。
 * 其他模块监听它来给新公司准备初始数据（例如 CRM 的商机阶段、公海规则）；监听方失败会让开户整体回滚。
 *
 * @param tenantId    新公司的租户编号
 * @param adminUserId 新公司管理员的用户编号
 */
public record TenantCreatedEvent(Long tenantId, Long adminUserId) {
}
