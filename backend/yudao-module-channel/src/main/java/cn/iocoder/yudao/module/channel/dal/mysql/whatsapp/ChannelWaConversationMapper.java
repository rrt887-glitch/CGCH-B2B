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
     * 团队会话：处理人是同事（不是我）且在我的数据权限范围内。
     * teamUsers 为 null 表示可看全部（管理员）；teamAccounts 是这些同事名下的号码；myAccounts 是我名下的号码。
     */
    default PageResult<ChannelWaConversationDO> selectTeamPage(cn.iocoder.yudao.module.channel.controller.admin.whatsapp.vo.ChannelWaConversationPageReqVO req,
                                                               Long userId, Collection<Long> teamUsers, Collection<Long> teamAccounts,
                                                               Collection<Long> myAccounts) {
        LambdaQueryWrapperX<ChannelWaConversationDO> q = teamQuery(userId, teamUsers, teamAccounts, myAccounts);
        if (req.getAccountId() != null) {
            q.eq(ChannelWaConversationDO::getAccountId, req.getAccountId());
        }
        return selectPage(req, applyFilters(q, req));
    }

    /**
     * 会话列表：我号码上的会话（没转交给别人的）+ 转交给我的会话
     *
     * @param accountIds 我的号码；accountId 筛选时只含这一个
     * @param onlyMine   true 时只看这些号码上的，不含别的号码转交给我的
     */
    default PageResult<ChannelWaConversationDO> selectPage(cn.iocoder.yudao.module.channel.controller.admin.whatsapp.vo.ChannelWaConversationPageReqVO req,
                                                           Collection<Long> accountIds, Long userId, boolean onlyMine) {
        LambdaQueryWrapperX<ChannelWaConversationDO> q = mineQuery(accountIds, userId, onlyMine);
        return selectPage(req, applyFilters(q, req));
    }

    /** 团队会话的范围（不含筛选条件），参数含义同 selectTeamPage */
    default LambdaQueryWrapperX<ChannelWaConversationDO> teamQuery(Long userId, Collection<Long> teamUsers, Collection<Long> teamAccounts,
                                                                   Collection<Long> myAccounts) {
        LambdaQueryWrapperX<ChannelWaConversationDO> q = new LambdaQueryWrapperX<>();
        if (teamUsers == null) {
            q.and(w -> w.nested(x -> x.isNotNull(ChannelWaConversationDO::getOwnerUserId).ne(ChannelWaConversationDO::getOwnerUserId, userId))
                    .or().nested(x -> x.isNull(ChannelWaConversationDO::getOwnerUserId).notIn(ChannelWaConversationDO::getAccountId, myAccounts)));
        } else {
            // 范围内同事负责的会话，或者「公开给团队」的会话（不是我自己负责的）
            q.and(w -> w.in(ChannelWaConversationDO::getOwnerUserId, teamUsers)
                    .or().nested(x -> x.isNull(ChannelWaConversationDO::getOwnerUserId).in(ChannelWaConversationDO::getAccountId, teamAccounts))
                    .or().nested(x -> x.eq(ChannelWaConversationDO::getTeamPublic, true)
                            .and(y -> y.nested(z -> z.isNotNull(ChannelWaConversationDO::getOwnerUserId).ne(ChannelWaConversationDO::getOwnerUserId, userId))
                                    .or().nested(z -> z.isNull(ChannelWaConversationDO::getOwnerUserId).notIn(ChannelWaConversationDO::getAccountId, myAccounts)))));
        }
        return q;
    }

    /** 我的会话的范围（不含筛选条件），参数含义同 selectPage */
    default LambdaQueryWrapperX<ChannelWaConversationDO> mineQuery(Collection<Long> accountIds, Long userId, boolean onlyMine) {
        LambdaQueryWrapperX<ChannelWaConversationDO> q = new LambdaQueryWrapperX<>();
        q.and(w -> {
            w.nested(x -> x.in(ChannelWaConversationDO::getAccountId, accountIds)
                    .and(y -> y.isNull(ChannelWaConversationDO::getOwnerUserId).or().eq(ChannelWaConversationDO::getOwnerUserId, userId)));
            if (!onlyMine) {
                w.or().eq(ChannelWaConversationDO::getOwnerUserId, userId);
            }
        });
        return q;
    }

    /** 归档、收藏、未读、未回复、客户等级、关键词筛选，按收藏、最近消息排序 */
    default LambdaQueryWrapperX<ChannelWaConversationDO> applyFilters(LambdaQueryWrapperX<ChannelWaConversationDO> q,
                                                                     cn.iocoder.yudao.module.channel.controller.admin.whatsapp.vo.ChannelWaConversationPageReqVO req) {
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
            // 最后一条消息是客户发的（按消息编号判断，避免同一秒内收发时按时间分不清先后）
            q.apply("(SELECT m.direction FROM channel_wa_message m WHERE m.conversation_id = channel_wa_conversation.id"
                    + " AND m.deleted = 0 ORDER BY m.id DESC LIMIT 1) = 1");
        }
        if ("open".equals(req.getStatus())) {
            q.and(w -> w.isNull(ChannelWaConversationDO::getStatus).or().eq(ChannelWaConversationDO::getStatus, "open"));
        } else if ("pending".equals(req.getStatus()) || "resolved".equals(req.getStatus())) {
            q.eq(ChannelWaConversationDO::getStatus, req.getStatus());
        }
        if (req.getCustomerId() != null) {
            q.eq(ChannelWaConversationDO::getCustomerId, req.getCustomerId());
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
        return q;
    }

}
