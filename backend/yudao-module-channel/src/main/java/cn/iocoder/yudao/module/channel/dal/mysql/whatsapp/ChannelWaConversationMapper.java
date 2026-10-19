package cn.iocoder.yudao.module.channel.dal.mysql.whatsapp;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaConversationDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;

@Mapper
public interface ChannelWaConversationMapper extends BaseMapperX<ChannelWaConversationDO> {

    default ChannelWaConversationDO selectByAccountIdAndWaId(Long accountId, String waId) {
        return selectOne(ChannelWaConversationDO::getAccountId, accountId, ChannelWaConversationDO::getWaId, waId);
    }

    default PageResult<ChannelWaConversationDO> selectPage(PageParam page, Collection<Long> accountIds,
                                                           String keyword, Boolean unreadOnly) {
        LambdaQueryWrapperX<ChannelWaConversationDO> q = new LambdaQueryWrapperX<ChannelWaConversationDO>()
                .in(ChannelWaConversationDO::getAccountId, accountIds);
        if (Boolean.TRUE.equals(unreadOnly)) {
            q.gt(ChannelWaConversationDO::getUnreadCount, 0);
        }
        if (StrUtil.isNotBlank(keyword)) {
            String kw = keyword.trim();
            q.and(w -> w.like(ChannelWaConversationDO::getWaId, kw).or()
                    .like(ChannelWaConversationDO::getProfileName, kw));
        }
        q.orderByDesc(ChannelWaConversationDO::getLastMessageTime).orderByDesc(ChannelWaConversationDO::getId);
        return selectPage(page, q);
    }

}
