package cn.iocoder.yudao.module.channel.dal.mysql.ai;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiDraftDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelAiDraftMapper extends BaseMapperX<ChannelAiDraftDO> {

    /** 某封邮件 / 某个会话最新的待处理草稿 */
    default ChannelAiDraftDO selectLatestPending(String channel, Long refId) {
        List<ChannelAiDraftDO> list = selectList(new LambdaQueryWrapperX<ChannelAiDraftDO>()
                .eq(ChannelAiDraftDO::getChannel, channel).eq(ChannelAiDraftDO::getRefId, refId)
                .eq(ChannelAiDraftDO::getStatus, ChannelAiDraftDO.STATUS_PENDING)
                .orderByDesc(ChannelAiDraftDO::getId).last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    default PageResult<ChannelAiDraftDO> selectPage(PageParam page, String channel, String status) {
        return selectPage(page, new LambdaQueryWrapperX<ChannelAiDraftDO>()
                .eqIfPresent(ChannelAiDraftDO::getChannel, channel)
                .eqIfPresent(ChannelAiDraftDO::getStatus, status)
                .orderByDesc(ChannelAiDraftDO::getId));
    }

    /** 期间内 AI 判断需要转人工的记录 */
    default List<ChannelAiDraftDO> selectHandoffs(java.time.LocalDateTime start, java.time.LocalDateTime end, int limit) {
        return selectList(new LambdaQueryWrapperX<ChannelAiDraftDO>()
                .eq(ChannelAiDraftDO::getReason, ChannelAiDraftDO.REASON_HANDOFF)
                .ge(ChannelAiDraftDO::getCreateTime, start).lt(ChannelAiDraftDO::getCreateTime, end)
                .orderByDesc(ChannelAiDraftDO::getId).last("LIMIT " + limit));
    }

    /** 期间内知识库没有命中任何资料的问题 */
    default List<ChannelAiDraftDO> selectKnowledgeGaps(java.time.LocalDateTime start, java.time.LocalDateTime end, int limit) {
        return selectList(new LambdaQueryWrapperX<ChannelAiDraftDO>()
                .eq(ChannelAiDraftDO::getKnowledgeHits, 0).isNotNull(ChannelAiDraftDO::getQuestion)
                .ne(ChannelAiDraftDO::getQuestion, "")
                .ne(ChannelAiDraftDO::getReason, ChannelAiDraftDO.REASON_HANDOFF)
                .ge(ChannelAiDraftDO::getCreateTime, start).lt(ChannelAiDraftDO::getCreateTime, end)
                .orderByDesc(ChannelAiDraftDO::getId).last("LIMIT " + limit));
    }

}
