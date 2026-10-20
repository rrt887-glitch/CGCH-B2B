package cn.iocoder.yudao.module.channel.dal.mysql.ai;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiConfigDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelAiConfigMapper extends BaseMapperX<ChannelAiConfigDO> {

    /** 当前租户的配置（多租户插件自动带上租户条件） */
    default ChannelAiConfigDO selectCurrent() {
        List<ChannelAiConfigDO> list = selectList();
        return list.isEmpty() ? null : list.get(0);
    }

}
