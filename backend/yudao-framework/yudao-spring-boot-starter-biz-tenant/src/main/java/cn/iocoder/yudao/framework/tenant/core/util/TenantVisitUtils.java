package cn.iocoder.yudao.framework.tenant.core.util;

import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

/**
 * 「进入客户后台」的数据范围校验。
 *
 * 平台运营经「进入客户后台」访问某家客户时，权限校验会被整体跳过（跨租户无法用角色判断），
 * 于是任何带租户编号参数的接口都能被改参数去操作别家客户，审计日志还会记成「正在访问的那家」。
 * 这里统一兜住：进入客户后台期间只能操作当前这家客户的编号。
 */
public class TenantVisitUtils {

    private TenantVisitUtils() {
    }

    /**
     * 进入客户后台时，只允许传当前正在访问的租户编号
     *
     * @param tenantId 接口参数里的租户编号；为空表示不涉及具体租户，直接放行
     */
    public static void assertInVisitScope(Long tenantId) {
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        if (user == null) {
            return;
        }
        Long visitTenantId = user.getVisitTenantId();
        if (visitTenantId == null || Objects.equals(visitTenantId, user.getTenantId())) {
            return; // 不在「进入客户后台」状态：平台自己的权限已由 @PreAuthorize 控制
        }
        if (tenantId != null && !Objects.equals(tenantId, visitTenantId)) {
            throw exception0(GlobalErrorCodeConstants.FORBIDDEN.getCode(),
                    "进入客户后台时只能操作当前客户的数据，请先退出再进入目标客户");
        }
    }

}
