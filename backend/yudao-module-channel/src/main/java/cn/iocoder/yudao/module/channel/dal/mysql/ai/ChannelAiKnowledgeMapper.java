package cn.iocoder.yudao.module.channel.dal.mysql.ai;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiKnowledgeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelAiKnowledgeMapper extends BaseMapperX<ChannelAiKnowledgeDO> {

    /** 公司物料（不含个人物料） */
    default List<ChannelAiKnowledgeDO> selectListByType(String type) {
        return selectList(new LambdaQueryWrapperX<ChannelAiKnowledgeDO>()
                .eqIfPresent(ChannelAiKnowledgeDO::getType, type)
                .and(w -> w.isNull(ChannelAiKnowledgeDO::getScope).or().ne(ChannelAiKnowledgeDO::getScope, ChannelAiKnowledgeDO.SCOPE_PERSONAL))
                .orderByAsc(ChannelAiKnowledgeDO::getSort).orderByAsc(ChannelAiKnowledgeDO::getId));
    }

    default List<ChannelAiKnowledgeDO> selectPersonal(Long userId) {
        return selectList(new LambdaQueryWrapperX<ChannelAiKnowledgeDO>()
                .eq(ChannelAiKnowledgeDO::getScope, ChannelAiKnowledgeDO.SCOPE_PERSONAL)
                .eq(ChannelAiKnowledgeDO::getOwnerUserId, userId)
                .orderByAsc(ChannelAiKnowledgeDO::getSort).orderByAsc(ChannelAiKnowledgeDO::getId));
    }

    /** AI 回复 userId 负责的会话时可用的资料：公司物料 + 本人的个人物料 */
    default List<ChannelAiKnowledgeDO> selectEnabledFor(Long userId) {
        return selectEnabled().stream().filter(k -> k.usableBy(userId)).toList();
    }

    default List<ChannelAiKnowledgeDO> selectEnabled() {
        return selectList(new LambdaQueryWrapperX<ChannelAiKnowledgeDO>()
                .eq(ChannelAiKnowledgeDO::getEnabled, true)
                .orderByAsc(ChannelAiKnowledgeDO::getSort).orderByAsc(ChannelAiKnowledgeDO::getId));
    }

}
