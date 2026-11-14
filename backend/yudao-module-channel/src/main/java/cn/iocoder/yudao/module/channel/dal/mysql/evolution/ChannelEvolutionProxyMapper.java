package cn.iocoder.yudao.module.channel.dal.mysql.evolution;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.evolution.ChannelEvolutionProxyDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelEvolutionProxyMapper extends BaseMapperX<ChannelEvolutionProxyDO> {

    /** 当前公司的代理配置（每家一条） */
    default ChannelEvolutionProxyDO selectCurrent() {
        List<ChannelEvolutionProxyDO> list = selectList();
        return list.isEmpty() ? null : list.get(0);
    }

}
