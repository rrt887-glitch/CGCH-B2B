package cn.iocoder.yudao.module.channel.dal.mysql.lead;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.lead.ChannelLeadRuleDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelLeadRuleMapper extends BaseMapperX<ChannelLeadRuleDO> {

    default List<ChannelLeadRuleDO> selectOrdered() {
        return selectList(new LambdaQueryWrapperX<ChannelLeadRuleDO>().orderByAsc(ChannelLeadRuleDO::getSort).orderByAsc(ChannelLeadRuleDO::getId));
    }

}
