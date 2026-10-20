package cn.iocoder.yudao.module.channel.dal.mysql.credit;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.controller.admin.credit.vo.ChannelCreditLedgerPageReqVO;
import cn.iocoder.yudao.module.channel.dal.dataobject.credit.ChannelCreditLedgerDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ChannelCreditLedgerMapper extends BaseMapperX<ChannelCreditLedgerDO> {

    default PageResult<ChannelCreditLedgerDO> selectPage(ChannelCreditLedgerPageReqVO req) {
        return selectPage(req, new LambdaQueryWrapperX<ChannelCreditLedgerDO>()
                .eqIfPresent(ChannelCreditLedgerDO::getType, req.getType())
                .eqIfPresent(ChannelCreditLedgerDO::getChannel, req.getChannel())
                .eqIfPresent(ChannelCreditLedgerDO::getUserId, req.getUserId())
                .betweenIfPresent(ChannelCreditLedgerDO::getCreateTime, req.getCreateTime())
                .orderByDesc(ChannelCreditLedgerDO::getId));
    }

    /** 某段时间内的消耗流水，用于汇总 */
    default List<ChannelCreditLedgerDO> selectConsumeSince(LocalDateTime since) {
        return selectList(new LambdaQueryWrapperX<ChannelCreditLedgerDO>()
                .eq(ChannelCreditLedgerDO::getType, ChannelCreditLedgerDO.TYPE_CONSUME)
                .ge(ChannelCreditLedgerDO::getCreateTime, since));
    }

}
