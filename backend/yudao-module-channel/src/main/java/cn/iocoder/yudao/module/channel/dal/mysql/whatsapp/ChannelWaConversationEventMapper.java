package cn.iocoder.yudao.module.channel.dal.mysql.whatsapp;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaConversationEventDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelWaConversationEventMapper extends BaseMapperX<ChannelWaConversationEventDO> {

    /** 会话最近的备注和动态，按时间正序 */
    default List<ChannelWaConversationEventDO> selectRecent(Long conversationId, int limit) {
        List<ChannelWaConversationEventDO> list = selectList(new LambdaQueryWrapperX<ChannelWaConversationEventDO>()
                .eq(ChannelWaConversationEventDO::getConversationId, conversationId)
                .orderByDesc(ChannelWaConversationEventDO::getId)
                .last("LIMIT " + limit));
        java.util.Collections.reverse(list);
        return list;
    }

}
