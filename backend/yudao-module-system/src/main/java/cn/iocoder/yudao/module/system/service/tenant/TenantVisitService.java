package cn.iocoder.yudao.module.system.service.tenant;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.controller.admin.tenant.vo.visit.TenantVisitEnterRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantVisitLogDO;

/**
 * 平台运营「进入客户后台」：默认看不到客户的数据，需要支持时登记原因进入，有效期内可访问，每次进入都有记录。
 */
public interface TenantVisitService {

    /**
     * 进入客户后台：登记原因并记录日志，返回本次有效期
     */
    TenantVisitEnterRespVO enter(Long userId, Long tenantId, String reason);

    /**
     * 退出客户后台
     */
    void exit(Long userId, Long tenantId);

    /**
     * 进入记录；visitTenantId 为空表示全部客户
     */
    PageResult<TenantVisitLogDO> getVisitLogPage(PageParam page, Long visitTenantId);

}
