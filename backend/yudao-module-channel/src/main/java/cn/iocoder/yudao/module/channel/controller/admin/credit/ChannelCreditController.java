package cn.iocoder.yudao.module.channel.controller.admin.credit;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.channel.controller.admin.credit.vo.ChannelCreditLedgerPageReqVO;
import cn.iocoder.yudao.module.channel.controller.admin.credit.vo.ChannelCreditRechargeReqVO;
import cn.iocoder.yudao.module.channel.controller.admin.credit.vo.ChannelCreditSummaryRespVO;
import cn.iocoder.yudao.module.channel.dal.dataobject.credit.ChannelCreditLedgerDO;
import cn.iocoder.yudao.module.channel.service.credit.ChannelCreditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 智点")
@RestController
@RequestMapping("/channel/credit")
@Validated
public class ChannelCreditController {

    @Resource
    private ChannelCreditService channelCreditService;

    @GetMapping("/summary")
    @Operation(summary = "余额与本月消耗")
    @PreAuthorize("@ss.hasPermission('channel:credit:query')")
    public CommonResult<ChannelCreditSummaryRespVO> summary() {
        return success(channelCreditService.getSummary());
    }

    @GetMapping("/ledger/page")
    @Operation(summary = "智点流水")
    @PreAuthorize("@ss.hasPermission('channel:credit:query')")
    public CommonResult<PageResult<ChannelCreditLedgerDO>> ledger(@Valid ChannelCreditLedgerPageReqVO req) {
        return success(channelCreditService.getLedgerPage(req));
    }

    @PostMapping("/recharge")
    @Operation(summary = "充值或调整余额（管理员），返回调整后余额")
    @PreAuthorize("@ss.hasPermission('channel:credit:recharge')")
    public CommonResult<Long> recharge(@Valid @RequestBody ChannelCreditRechargeReqVO req) {
        return success(channelCreditService.recharge(getLoginUserId(), req.getAmount(), req.getRemark()));
    }

}
