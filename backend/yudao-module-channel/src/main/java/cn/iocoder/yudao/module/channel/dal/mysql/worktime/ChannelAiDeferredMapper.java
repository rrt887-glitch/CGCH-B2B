package cn.iocoder.yudao.module.channel.dal.mysql.worktime;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.worktime.ChannelAiDeferredDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ChannelAiDeferredMapper extends BaseMapperX<ChannelAiDeferredDO> {

    /** 所有租户中已到期的等待项（调用方需忽略租户） */
    default List<ChannelAiDeferredDO> selectDue(LocalDateTime now, int limit) {
        return selectList(new LambdaQueryWrapperX<ChannelAiDeferredDO>()
                .eq(ChannelAiDeferredDO::getStatus, ChannelAiDeferredDO.STATUS_WAITING)
                .le(ChannelAiDeferredDO::getDueTime, now)
                .orderByAsc(ChannelAiDeferredDO::getDueTime).last("LIMIT " + limit));
    }

    /** 同一对象之前还在等待的项作废（客户又来了新消息，重新计时） */
    default void supersede(String channel, Long refId) {
        update(new ChannelAiDeferredDO().setStatus(ChannelAiDeferredDO.STATUS_SUPERSEDED),
                new LambdaUpdateWrapper<ChannelAiDeferredDO>().eq(ChannelAiDeferredDO::getChannel, channel)
                        .eq(ChannelAiDeferredDO::getRefId, refId)
                        .eq(ChannelAiDeferredDO::getStatus, ChannelAiDeferredDO.STATUS_WAITING));
    }

    /** 仅当仍在等待时改状态，返回是否抢到（多实例部署时防止重复处理） */
    default boolean finish(Long id, String status) {
        return update(new ChannelAiDeferredDO().setStatus(status),
                new LambdaUpdateWrapper<ChannelAiDeferredDO>().eq(ChannelAiDeferredDO::getId, id)
                        .eq(ChannelAiDeferredDO::getStatus, ChannelAiDeferredDO.STATUS_WAITING)) > 0;
    }

    default ChannelAiDeferredDO selectLatest(String channel, Long refId) {
        return selectOne(new LambdaQueryWrapperX<ChannelAiDeferredDO>().eq(ChannelAiDeferredDO::getChannel, channel)
                .eq(ChannelAiDeferredDO::getRefId, refId).orderByDesc(ChannelAiDeferredDO::getId).last("LIMIT 1"));
    }

}
