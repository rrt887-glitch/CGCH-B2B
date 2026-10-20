package cn.iocoder.yudao.module.channel.service.credit;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.channel.controller.admin.credit.vo.ChannelCreditLedgerPageReqVO;
import cn.iocoder.yudao.module.channel.controller.admin.credit.vo.ChannelCreditSummaryRespVO;
import cn.iocoder.yudao.module.channel.dal.dataobject.credit.ChannelCreditAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.credit.ChannelCreditLedgerDO;
import cn.iocoder.yudao.module.channel.dal.mysql.credit.ChannelCreditAccountMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.credit.ChannelCreditLedgerMapper;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.CREDIT_AMOUNT_ZERO;

/**
 * 智点：AI 调用按 token 折算扣点。余额不足时 AI 不再调用大模型。
 */
@Service
public class ChannelCreditService {

    @Resource
    private ChannelCreditAccountMapper channelCreditAccountMapper;
    @Resource
    private ChannelCreditLedgerMapper channelCreditLedgerMapper;

    /** 每 1 智点对应的 token 数 */
    @Value("${cgch.credit.tokens-per-credit:1000}")
    private int tokensPerCredit;

    /** 当前租户的账户，没有则创建（余额 0） */
    public ChannelCreditAccountDO getOrCreateAccount() {
        ChannelCreditAccountDO a = channelCreditAccountMapper.selectCurrent();
        if (a == null) {
            a = ChannelCreditAccountDO.builder().balance(0L).totalIn(0L).totalOut(0L).build();
            channelCreditAccountMapper.insert(a);
        }
        return a;
    }

    public boolean hasBalance() {
        return getOrCreateAccount().getBalance() > 0;
    }

    /** token → 智点，向上取整，至少 1 点 */
    public long toCredits(int tokens) {
        return Math.max(1, (tokens + tokensPerCredit - 1) / Math.max(1, tokensPerCredit));
    }

    /** 记一次 AI 消耗，返回扣除的智点 */
    @Transactional(rollbackFor = Exception.class)
    public long consume(String channel, Long refId, Long userId, int tokens, String remark) {
        long credits = toCredits(tokens);
        ChannelCreditAccountDO a = getOrCreateAccount();
        channelCreditAccountMapper.addBalance(a.getId(), -credits);
        long after = channelCreditAccountMapper.selectById(a.getId()).getBalance();
        channelCreditLedgerMapper.insert(ChannelCreditLedgerDO.builder().type(ChannelCreditLedgerDO.TYPE_CONSUME)
                .amount(-credits).balanceAfter(after).channel(channel).refId(refId).userId(userId).tokens(tokens)
                .remark(StrUtil.maxLength(remark, 200)).build());
        return credits;
    }

    /** 充值（正数）或调整（负数） */
    @Transactional(rollbackFor = Exception.class)
    public long recharge(Long operatorId, long amount, String remark) {
        if (amount == 0) {
            throw exception(CREDIT_AMOUNT_ZERO);
        }
        ChannelCreditAccountDO a = getOrCreateAccount();
        channelCreditAccountMapper.addBalance(a.getId(), amount);
        long after = channelCreditAccountMapper.selectById(a.getId()).getBalance();
        channelCreditLedgerMapper.insert(ChannelCreditLedgerDO.builder()
                .type(amount > 0 ? ChannelCreditLedgerDO.TYPE_RECHARGE : ChannelCreditLedgerDO.TYPE_ADJUST)
                .amount(amount).balanceAfter(after).userId(operatorId)
                .remark(StrUtil.maxLength(StrUtil.blankToDefault(remark, amount > 0 ? "充值" : "调整"), 200)).build());
        return after;
    }

    /** 在线支付到账：记为充值，channel=pay，refId=充值订单号 */
    @Transactional(rollbackFor = Exception.class)
    public long rechargeByOrder(Long orderId, Long userId, long credits, String remark) {
        ChannelCreditAccountDO a = getOrCreateAccount();
        channelCreditAccountMapper.addBalance(a.getId(), credits);
        long after = channelCreditAccountMapper.selectById(a.getId()).getBalance();
        channelCreditLedgerMapper.insert(ChannelCreditLedgerDO.builder().type(ChannelCreditLedgerDO.TYPE_RECHARGE)
                .amount(credits).balanceAfter(after).channel("pay").refId(orderId).userId(userId)
                .remark(StrUtil.maxLength(remark, 200)).build());
        return after;
    }

    public ChannelCreditSummaryRespVO getSummary() {
        ChannelCreditAccountDO a = getOrCreateAccount();
        ChannelCreditSummaryRespVO vo = new ChannelCreditSummaryRespVO();
        vo.setBalance(a.getBalance());
        vo.setTotalIn(a.getTotalIn());
        vo.setTotalOut(a.getTotalOut());
        vo.setTokensPerCredit(tokensPerCredit);
        Map<String, Long> byChannel = new LinkedHashMap<>();
        Map<Long, Long> byUser = new LinkedHashMap<>();
        long month = 0;
        for (ChannelCreditLedgerDO l : channelCreditLedgerMapper.selectConsumeSince(LocalDate.now().withDayOfMonth(1).atStartOfDay())) {
            long c = -l.getAmount();
            month += c;
            byChannel.merge(StrUtil.blankToDefault(l.getChannel(), "other"), c, Long::sum);
            if (l.getUserId() != null) {
                byUser.merge(l.getUserId(), c, Long::sum);
            }
        }
        vo.setMonthConsumed(month);
        vo.setMonthByChannel(byChannel);
        vo.setMonthByUser(byUser);
        return vo;
    }

    public PageResult<ChannelCreditLedgerDO> getLedgerPage(ChannelCreditLedgerPageReqVO req) {
        return channelCreditLedgerMapper.selectPage(req);
    }

}
