package cn.iocoder.yudao.module.channel.dal.mysql.ai;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiReportDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;

@Mapper
public interface ChannelAiReportMapper extends BaseMapperX<ChannelAiReportDO> {

    default PageResult<ChannelAiReportDO> selectPage(PageParam req, String periodType) {
        return selectPage(req, new LambdaQueryWrapperX<ChannelAiReportDO>()
                .eqIfPresent(ChannelAiReportDO::getPeriodType, periodType)
                .orderByDesc(ChannelAiReportDO::getPeriodStart).orderByDesc(ChannelAiReportDO::getId));
    }

    default ChannelAiReportDO selectByPeriod(String periodType, LocalDate periodStart) {
        return selectOne(new LambdaQueryWrapperX<ChannelAiReportDO>()
                .eq(ChannelAiReportDO::getPeriodType, periodType).eq(ChannelAiReportDO::getPeriodStart, periodStart)
                .last("LIMIT 1"));
    }

}
