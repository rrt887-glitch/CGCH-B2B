package cn.iocoder.yudao.module.system.dal.mysql.tenant;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantVisitLogDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantVisitLogMapper extends BaseMapperX<TenantVisitLogDO> {

    /** 按客户筛选（visitTenantId 为空表示全部），新的在前 */
    default PageResult<TenantVisitLogDO> selectPage(PageParam page, Long visitTenantId) {
        return selectPage(page, new LambdaQueryWrapperX<TenantVisitLogDO>()
                .eqIfPresent(TenantVisitLogDO::getVisitTenantId, visitTenantId)
                .orderByDesc(TenantVisitLogDO::getId));
    }

    /** 某人进入某客户、还没有主动退出的最近一条记录 */
    default TenantVisitLogDO selectLatestOpen(Long userId, Long visitTenantId) {
        return selectOne(new LambdaQueryWrapperX<TenantVisitLogDO>()
                .eq(TenantVisitLogDO::getUserId, userId)
                .eq(TenantVisitLogDO::getVisitTenantId, visitTenantId)
                .isNull(TenantVisitLogDO::getExitTime)
                .orderByDesc(TenantVisitLogDO::getId)
                .last("LIMIT 1"));
    }

}
