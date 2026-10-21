package cn.iocoder.yudao.module.channel.dal.mysql.assistant;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.assistant.ChannelAssistantChatDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelAssistantChatMapper extends BaseMapperX<ChannelAssistantChatDO> {

    default List<ChannelAssistantChatDO> selectListByUserId(Long userId) {
        return selectList(new LambdaQueryWrapperX<ChannelAssistantChatDO>().eq(ChannelAssistantChatDO::getUserId, userId)
                .orderByDesc(ChannelAssistantChatDO::getUpdateTime).last("LIMIT 50"));
    }

}
