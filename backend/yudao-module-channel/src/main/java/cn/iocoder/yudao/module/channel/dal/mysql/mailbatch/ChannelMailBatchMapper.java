package cn.iocoder.yudao.module.channel.dal.mysql.mailbatch;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.mailbatch.ChannelMailBatchDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ChannelMailBatchMapper extends BaseMapperX<ChannelMailBatchDO> {

    default PageResult<ChannelMailBatchDO> selectPage(PageParam req, Long userId) {
        return selectPage(req, new LambdaQueryWrapperX<ChannelMailBatchDO>().eq(ChannelMailBatchDO::getUserId, userId)
                .orderByDesc(ChannelMailBatchDO::getId));
    }

    /** 发送中、到了下一封发送时间的任务（不区分租户，调用方切换租户） */
    default List<ChannelMailBatchDO> selectDue(LocalDateTime now, int limit) {
        return selectList(new LambdaQueryWrapperX<ChannelMailBatchDO>().eq(ChannelMailBatchDO::getStatus, ChannelMailBatchDO.STATUS_SENDING)
                .le(ChannelMailBatchDO::getNextSendTime, now).orderByAsc(ChannelMailBatchDO::getNextSendTime).last("LIMIT " + limit));
    }

}
