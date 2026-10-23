package cn.iocoder.yudao.module.channel.dal.mysql.ai;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiKnowledgeFileDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelAiKnowledgeFileMapper extends BaseMapperX<ChannelAiKnowledgeFileDO> {

    default List<ChannelAiKnowledgeFileDO> selectAll() {
        return selectList(new LambdaQueryWrapperX<ChannelAiKnowledgeFileDO>().orderByDesc(ChannelAiKnowledgeFileDO::getId));
    }

}
