package cn.iocoder.yudao.module.channel.dal.mysql.whatsapp;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaMessageDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelWaMessageMapper extends BaseMapperX<ChannelWaMessageDO> {

    default ChannelWaMessageDO selectByWaMessageId(Long accountId, String waMessageId) {
        return selectOne(ChannelWaMessageDO::getAccountId, accountId, ChannelWaMessageDO::getWaMessageId, waMessageId);
    }

    /** 会话中最近的 limit 条消息，按时间正序返回；beforeId 用于向上翻页 */
    default List<ChannelWaMessageDO> selectRecent(Long conversationId, Long beforeId, int limit) {
        List<ChannelWaMessageDO> list = selectList(new LambdaQueryWrapperX<ChannelWaMessageDO>()
                .eq(ChannelWaMessageDO::getConversationId, conversationId)
                .ltIfPresent(ChannelWaMessageDO::getId, beforeId)
                .orderByDesc(ChannelWaMessageDO::getId)
                .last("LIMIT " + limit));
        java.util.Collections.reverse(list);
        return list;
    }

}
