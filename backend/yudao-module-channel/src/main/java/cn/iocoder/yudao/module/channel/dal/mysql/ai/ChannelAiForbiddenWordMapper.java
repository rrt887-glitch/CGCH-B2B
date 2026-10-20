package cn.iocoder.yudao.module.channel.dal.mysql.ai;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiForbiddenWordDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelAiForbiddenWordMapper extends BaseMapperX<ChannelAiForbiddenWordDO> {

    default List<ChannelAiForbiddenWordDO> selectEnabled() {
        return selectList(ChannelAiForbiddenWordDO::getEnabled, true);
    }

    default ChannelAiForbiddenWordDO selectByWord(String word) {
        return selectOne(ChannelAiForbiddenWordDO::getWord, word);
    }

}
