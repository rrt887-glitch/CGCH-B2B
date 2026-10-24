package cn.iocoder.yudao.module.channel.dal.mysql.routing;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.routing.ChannelRoutingRuleDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelRoutingRuleMapper extends BaseMapperX<ChannelRoutingRuleDO> {

    default List<ChannelRoutingRuleDO> selectOrdered() {
        return selectList(new LambdaQueryWrapperX<ChannelRoutingRuleDO>()
                .orderByAsc(ChannelRoutingRuleDO::getSort).orderByAsc(ChannelRoutingRuleDO::getId));
    }

}
