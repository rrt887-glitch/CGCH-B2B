package cn.iocoder.yudao.module.channel.controller.admin.credit;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.channel.dal.dataobject.credit.ChannelCreditOrderDO;
import cn.iocoder.yudao.module.channel.framework.credit.ChannelCreditProperties.CreditPackage;
import cn.iocoder.yudao.module.channel.service.credit.ChannelCreditOrderService;
import cn.iocoder.yudao.module.pay.api.notify.dto.PayOrderNotifyReqDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.servlet.ServletUtils.getClientIP;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 智点在线充值")
@RestController
@RequestMapping("/channel/credit")
@Validated
public class ChannelCreditOrderController {

    @Resource
    private ChannelCreditOrderService channelCreditOrderService;

    @GetMapping("/package/list")
    @Operation(summary = "充值套餐")
    @PreAuthorize("@ss.hasPermission('channel:credit:query')")
    public CommonResult<List<CreditPackage>> packages() {
        return success(channelCreditOrderService.listPackages());
    }

    @PostMapping("/order/create")
    @Operation(summary = "创建充值订单，返回含支付单编号的订单")
    @PreAuthorize("@ss.hasPermission('channel:credit:buy')")
    public CommonResult<ChannelCreditOrderDO> create(@RequestParam("packageId") Long packageId) {
        return success(channelCreditOrderService.createOrder(getLoginUserId(), packageId, getClientIP()));
    }

    @GetMapping("/order/get")
    @Operation(summary = "充值订单详情")
    @PreAuthorize("@ss.hasPermission('channel:credit:query')")
    public CommonResult<ChannelCreditOrderDO> get(@RequestParam("id") Long id) {
        return success(channelCreditOrderService.getOrder(id));
    }

    @GetMapping("/order/page")
    @Operation(summary = "充值订单")
    @PreAuthorize("@ss.hasPermission('channel:credit:query')")
    public CommonResult<PageResult<ChannelCreditOrderDO>> page(@Valid PageParam req) {
        return success(channelCreditOrderService.getOrderPage(req));
    }

    @PostMapping("/order/update-paid")
    @Operation(summary = "支付结果回调（由支付模块调用）")
    @PermitAll // 安全性由 Service 内向支付模块二次核对支付单保证
    public CommonResult<Boolean> updatePaid(@RequestBody PayOrderNotifyReqDTO req) {
        channelCreditOrderService.updatePaid(Long.valueOf(req.getMerchantOrderId()), req.getPayOrderId());
        return success(true);
    }

}
