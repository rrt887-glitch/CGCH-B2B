package cn.iocoder.yudao.module.channel.dal.mysql.ai;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiKnowledgeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelAiKnowledgeMapper extends BaseMapperX<ChannelAiKnowledgeDO> {

    default List<ChannelAiKnowledgeDO> selectListByType(String type) {
        return selectList(new LambdaQueryWrapperX<ChannelAiKnowledgeDO>()
                .eqIfPresent(ChannelAiKnowledgeDO::getType, type)
                .orderByAsc(ChannelAiKnowledgeDO::getSort).orderByAsc(ChannelAiKnowledgeDO::getId));
    }

    default List<ChannelAiKnowledgeDO> selectEnabled() {
        return selectList(new LambdaQueryWrapperX<ChannelAiKnowledgeDO>()
                .eq(ChannelAiKnowledgeDO::getEnabled, true)
                .orderByAsc(ChannelAiKnowledgeDO::getSort).orderByAsc(ChannelAiKnowledgeDO::getId));
    }

}
