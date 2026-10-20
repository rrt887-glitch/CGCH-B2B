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

}
