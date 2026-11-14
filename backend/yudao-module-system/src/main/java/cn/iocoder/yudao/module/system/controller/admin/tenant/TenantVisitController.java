package cn.iocoder.yudao.module.system.controller.admin.tenant;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.controller.admin.tenant.vo.visit.TenantVisitEnterReqVO;
import cn.iocoder.yudao.module.system.controller.admin.tenant.vo.visit.TenantVisitEnterRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantVisitLogDO;
import cn.iocoder.yudao.module.system.service.tenant.TenantVisitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 进入客户后台")
@RestController
@RequestMapping("/system/tenant-visit")
@Validated
public class TenantVisitController {

    @Resource
    private TenantVisitService tenantVisitService;

    @PostMapping("/enter")
    @Operation(summary = "进入客户后台（平台运营）：登记原因，2 小时内有效，每次进入都有记录")
    @PreAuthorize("@ss.hasPermission('system:tenant:visit')")
    public CommonResult<TenantVisitEnterRespVO> enter(@Valid @RequestBody TenantVisitEnterReqVO reqVO) {
        return success(tenantVisitService.enter(getLoginUserId(), reqVO.getTenantId(), reqVO.getReason()));
    }

    @PostMapping("/exit")
    @Operation(summary = "退出客户后台")
    @PreAuthorize("@ss.hasPermission('system:tenant:visit')")
    public CommonResult<Boolean> exit(@RequestParam("tenantId") Long tenantId) {
        tenantVisitService.exit(getLoginUserId(), tenantId);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "进入记录（平台运营，可按客户筛选）")
    @PreAuthorize("@ss.hasPermission('system:tenant:query')")
    public CommonResult<PageResult<TenantVisitLogDO>> page(@Valid PageParam page,
                                                          @RequestParam(value = "visitTenantId", required = false) Long visitTenantId) {
        return success(tenantVisitService.getVisitLogPage(page, visitTenantId));
    }

    @GetMapping("/my-page")
    @Operation(summary = "平台进入过本公司后台的记录（客户管理员查看）")
    @PreAuthorize("@ss.hasPermission('system:user:query')")
    public CommonResult<PageResult<TenantVisitLogDO>> myPage(@Valid PageParam page) {
        return success(tenantVisitService.getVisitLogPage(page, TenantContextHolder.getRequiredTenantId()));
    }

}
