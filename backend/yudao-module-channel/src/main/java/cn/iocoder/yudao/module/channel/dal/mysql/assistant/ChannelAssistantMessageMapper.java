package cn.iocoder.yudao.module.channel.dal.mysql.assistant;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.assistant.ChannelAssistantMessageDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelAssistantMessageMapper extends BaseMapperX<ChannelAssistantMessageDO> {

    default List<ChannelAssistantMessageDO> selectListByChatId(Long chatId) {
        return selectList(new LambdaQueryWrapperX<ChannelAssistantMessageDO>().eq(ChannelAssistantMessageDO::getChatId, chatId)
                .orderByAsc(ChannelAssistantMessageDO::getId));
    }

}
