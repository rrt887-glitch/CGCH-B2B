package cn.iocoder.yudao.module.channel.dal.mysql.ai;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiCaseDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelAiCaseMapper extends BaseMapperX<ChannelAiCaseDO> {

    default List<ChannelAiCaseDO> selectEnabled() {
        return selectList(new LambdaQueryWrapperX<ChannelAiCaseDO>().eq(ChannelAiCaseDO::getEnabled, true));
    }

    default List<ChannelAiCaseDO> selectListOrdered() {
        return selectList(new LambdaQueryWrapperX<ChannelAiCaseDO>().orderByDesc(ChannelAiCaseDO::getId));
    }

}
