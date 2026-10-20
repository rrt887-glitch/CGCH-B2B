package cn.iocoder.yudao.module.channel.service.credit;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.channel.dal.dataobject.credit.ChannelCreditOrderDO;
import cn.iocoder.yudao.module.channel.dal.mysql.credit.ChannelCreditOrderMapper;
import cn.iocoder.yudao.module.channel.framework.credit.ChannelCreditProperties;
import cn.iocoder.yudao.module.channel.framework.credit.ChannelCreditProperties.CreditPackage;
import cn.iocoder.yudao.module.pay.api.order.PayOrderApi;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderCreateReqDTO;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderRespDTO;
import cn.iocoder.yudao.module.pay.enums.order.PayOrderStatusEnum;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.*;

/**
 * 智点在线充值：选套餐 → 创建支付单 → 收银台支付 → 支付模块回调 → 到账。
 */
@Service
@Slf4j
public class ChannelCreditOrderService {

    @Resource
    private ChannelCreditOrderMapper channelCreditOrderMapper;
    @Resource
    private ChannelCreditService channelCreditService;
    @Resource
    private ChannelCreditProperties channelCreditProperties;
    @Resource
    private PayOrderApi payOrderApi;

    public List<CreditPackage> listPackages() {
        return channelCreditProperties.getPackages();
    }

    @Transactional(rollbackFor = Exception.class)
    public ChannelCreditOrderDO createOrder(Long userId, Long packageId, String userIp) {
        CreditPackage pkg = channelCreditProperties.findPackage(packageId);
        if (pkg == null) {
            throw exception(CREDIT_PACKAGE_NOT_FOUND);
        }
        ChannelCreditOrderDO order = ChannelCreditOrderDO.builder().userId(userId).packageId(pkg.getId())
                .packageName(pkg.getName()).credits(pkg.getCredits()).price(pkg.getPrice()).payStatus(false).build();
        channelCreditOrderMapper.insert(order);
        Long payOrderId = payOrderApi.createOrder(new PayOrderCreateReqDTO()
                .setAppKey(channelCreditProperties.getPayAppKey()).setUserIp(StrUtil.blankToDefault(userIp, "127.0.0.1"))
                .setUserId(userId).setUserType(UserTypeEnum.ADMIN.getValue())
                .setMerchantOrderId(order.getId().toString())
                .setSubject("智点充值 · " + pkg.getName()).setBody(pkg.getCredits() + " 智点")
                .setPrice(pkg.getPrice()).setExpireTime(LocalDateTime.now().plusHours(2)));
        channelCreditOrderMapper.updateById(new ChannelCreditOrderDO().setId(order.getId()).setPayOrderId(payOrderId));
        order.setPayOrderId(payOrderId);
        return order;
    }

    /** 支付模块回调；重复回调直接返回 */
    @Transactional(rollbackFor = Exception.class)
    public void updatePaid(Long id, Long payOrderId) {
        ChannelCreditOrderDO order = channelCreditOrderMapper.selectById(id);
        if (order == null) {
            log.error("[updatePaid][充值订单({}) 不存在，支付单({})]", id, payOrderId);
            throw exception(CREDIT_ORDER_NOT_FOUND);
        }
        if (!Objects.equals(order.getPayOrderId(), payOrderId)) {
            throw exception(CREDIT_ORDER_PAY_MISMATCH);
        }
        if (Boolean.TRUE.equals(order.getPayStatus())) {
            return;
        }
        PayOrderRespDTO pay = payOrderApi.getOrder(payOrderId);
        if (pay == null || !Objects.equals(pay.getMerchantOrderId(), id.toString())) {
            throw exception(CREDIT_ORDER_PAY_MISMATCH);
        }
        if (!PayOrderStatusEnum.isSuccess(pay.getStatus())) {
            throw exception(CREDIT_ORDER_NOT_PAID);
        }
        if (!Objects.equals(pay.getPrice(), order.getPrice())) {
            throw exception(CREDIT_ORDER_PRICE_MISMATCH);
        }
        int n = channelCreditOrderMapper.updateByIdAndUnpaid(id, new ChannelCreditOrderDO().setPayStatus(true)
                .setPayTime(LocalDateTime.now()).setPayChannelCode(pay.getChannelCode()));
        if (n == 0) {
            return; // 并发回调，另一次已到账
        }
        channelCreditService.rechargeByOrder(id, order.getUserId(), order.getCredits(),
                "在线充值 · " + order.getPackageName() + " · 订单 " + id);
    }

    public ChannelCreditOrderDO getOrder(Long id) {
        return channelCreditOrderMapper.selectById(id);
    }

    public PageResult<ChannelCreditOrderDO> getOrderPage(PageParam req) {
        return channelCreditOrderMapper.selectPage(req);
    }

}
