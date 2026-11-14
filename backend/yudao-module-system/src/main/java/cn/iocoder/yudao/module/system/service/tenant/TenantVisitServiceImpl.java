package cn.iocoder.yudao.module.system.service.tenant;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.web.TenantVisitGuard;
import cn.iocoder.yudao.module.system.controller.admin.tenant.vo.visit.TenantVisitEnterRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantVisitLogDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.tenant.TenantVisitLogMapper;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;

@Service
@Slf4j
public class TenantVisitServiceImpl implements TenantVisitService, TenantVisitGuard {

    /** 进入登记：cgch:tenant-visit:{用户编号}:{客户编号} → 日志编号，过期即需重新进入 */
    private static final String KEY = "cgch:tenant-visit:";
    static final Duration TTL = Duration.ofHours(2);

    @Resource
    private TenantVisitLogMapper visitLogMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    @Lazy
    private TenantService tenantService;
    @Resource
    @Lazy
    private AdminUserService userService;

    private static String key(Long userId, Long tenantId) {
        return KEY + userId + ":" + tenantId;
    }

    @Override
    public TenantVisitEnterRespVO enter(Long userId, Long tenantId, String reason) {
        if (StrUtil.isBlank(reason)) {
            throw exception(TENANT_VISIT_REASON_REQUIRED);
        }
        TenantDO tenant = tenantService.getTenant(tenantId);
        if (tenant == null) {
            throw exception(TENANT_NOT_EXISTS);
        }
        if (Objects.equals(tenantId, TenantContextHolder.getTenantId())) {
            throw exception(TENANT_VISIT_SELF);
        }
        AdminUserDO user = userService.getUser(userId);
        LocalDateTime now = LocalDateTime.now();
        TenantVisitLogDO visitLog = TenantVisitLogDO.builder()
                .userId(userId).username(user != null ? user.getUsername() : null).nickname(user != null ? user.getNickname() : null)
                .visitTenantId(tenantId).visitTenantName(tenant.getName()).reason(reason.trim())
                .ip(ServletUtils.getClientIP()).userAgent(StrUtil.maxLength(ServletUtils.getUserAgent(), 500))
                .enterTime(now).expireTime(now.plus(TTL)).build();
        visitLogMapper.insert(visitLog);
        stringRedisTemplate.opsForValue().set(key(userId, tenantId), String.valueOf(visitLog.getId()), TTL);
        log.info("[enter][平台账号 {} 进入客户 {}({})，原因：{}]", userId, tenantId, tenant.getName(), reason);
        return new TenantVisitEnterRespVO(tenantId, tenant.getName(), visitLog.getExpireTime());
    }

    @Override
    public void exit(Long userId, Long tenantId) {
        stringRedisTemplate.delete(key(userId, tenantId));
        TenantVisitLogDO open = visitLogMapper.selectLatestOpen(userId, tenantId);
        if (open != null) {
            visitLogMapper.updateById(new TenantVisitLogDO().setId(open.getId()).setExitTime(LocalDateTime.now()));
        }
    }

    @Override
    public PageResult<TenantVisitLogDO> getVisitLogPage(PageParam page, Long visitTenantId) {
        return visitLogMapper.selectPage(page, visitTenantId);
    }

    @Override
    public boolean isVisitAllowed(Long userId, Long visitTenantId) {
        if (userId == null || visitTenantId == null) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key(userId, visitTenantId)));
    }

}
