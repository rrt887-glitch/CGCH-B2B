package cn.iocoder.yudao.framework.tenant.core.web;

/**
 * 「进入客户后台」的放行检查。
 *
 * 平台运营带着 visit-tenant-id 请求头访问某家客户的数据前，必须先通过「进入客户后台」入口登记（填写原因、记录日志），
 * 由实现方判断这次访问是否在登记的有效期内。没有实现时保持框架原有行为（只校验切换租户权限）。
 */
public interface TenantVisitGuard {

    /**
     * @param userId        登录用户编号
     * @param visitTenantId 要访问的客户（租户）编号
     * @return 是否允许
     */
    boolean isVisitAllowed(Long userId, Long visitTenantId);

}
