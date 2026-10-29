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

    /**
     * 会话列表：我号码上的会话（没转交给别人的）+ 转交给我的会话
     *
     * @param accountIds 我的号码；accountId 筛选时只含这一个
     * @param onlyMine   true 时只看这些号码上的，不含别的号码转交给我的
     */
    default PageResult<ChannelWaConversationDO> selectPage(cn.iocoder.yudao.module.channel.controller.admin.whatsapp.vo.ChannelWaConversationPageReqVO req,
                                                           Collection<Long> accountIds, Long userId, boolean onlyMine) {
        LambdaQueryWrapperX<ChannelWaConversationDO> q = new LambdaQueryWrapperX<>();
        q.and(w -> {
            w.nested(x -> x.in(ChannelWaConversationDO::getAccountId, accountIds)
                    .and(y -> y.isNull(ChannelWaConversationDO::getOwnerUserId).or().eq(ChannelWaConversationDO::getOwnerUserId, userId)));
            if (!onlyMine) {
                w.or().eq(ChannelWaConversationDO::getOwnerUserId, userId);
            }
        });
        if (Boolean.TRUE.equals(req.getArchived())) {
            q.eq(ChannelWaConversationDO::getArchived, true);
        } else {
            q.and(w -> w.isNull(ChannelWaConversationDO::getArchived).or().eq(ChannelWaConversationDO::getArchived, false));
        }
        if (Boolean.TRUE.equals(req.getStarred())) {
            q.eq(ChannelWaConversationDO::getStarred, true);
        }
        if (Boolean.TRUE.equals(req.getUnreadOnly())) {
            q.gt(ChannelWaConversationDO::getUnreadCount, 0);
        }
        if (Boolean.TRUE.equals(req.getUnreplied())) {
            q.isNotNull(ChannelWaConversationDO::getLastInboundTime).apply("last_inbound_time >= last_message_time");
        }
        if (req.getLevel() != null) {
            q.inSql(ChannelWaConversationDO::getCustomerId,
                    "SELECT id FROM crm_customer WHERE deleted = 0 AND level = " + req.getLevel().intValue());
        }
        if (StrUtil.isNotBlank(req.getKeyword())) {
            String kw = req.getKeyword().trim();
            q.and(w -> w.like(ChannelWaConversationDO::getWaId, kw).or()
                    .like(ChannelWaConversationDO::getProfileName, kw).or()
                    .like(ChannelWaConversationDO::getRemarkName, kw));
        }
        q.orderByDesc(ChannelWaConversationDO::getStarred).orderByDesc(ChannelWaConversationDO::getLastMessageTime)
                .orderByDesc(ChannelWaConversationDO::getId);
        return selectPage(req, q);
    }

}
