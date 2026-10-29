package cn.iocoder.yudao.module.channel.dal.mysql.quickreply;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.quickreply.ChannelQuickReplyDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelQuickReplyMapper extends BaseMapperX<ChannelQuickReplyDO> {

    /** 我能用的：全员的 + 我自己的，常用的在前 */
    default List<ChannelQuickReplyDO> selectVisible(Long userId, String keyword) {
        LambdaQueryWrapperX<ChannelQuickReplyDO> q = new LambdaQueryWrapperX<>();
        q.and(w -> w.eq(ChannelQuickReplyDO::getScope, ChannelQuickReplyDO.SCOPE_ENTERPRISE)
                .or().eq(ChannelQuickReplyDO::getOwnerUserId, userId));
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(w -> w.like(ChannelQuickReplyDO::getTitle, kw).or().like(ChannelQuickReplyDO::getContent, kw));
        }
        q.orderByAsc(ChannelQuickReplyDO::getSort).orderByDesc(ChannelQuickReplyDO::getUseCount).orderByDesc(ChannelQuickReplyDO::getId);
        return selectList(q);
    }

    default void incrementUse(Long id) {
        update(null, new LambdaUpdateWrapper<ChannelQuickReplyDO>().eq(ChannelQuickReplyDO::getId, id)
                .setSql("use_count = IFNULL(use_count, 0) + 1"));
    }

}
