package cn.iocoder.yudao.module.channel.dal.mysql.reactivate;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.reactivate.ChannelReactivateItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ChannelReactivateItemMapper extends BaseMapperX<ChannelReactivateItemDO> {

    default List<ChannelReactivateItemDO> selectListByTaskId(Long taskId) {
        return selectList(new LambdaQueryWrapperX<ChannelReactivateItemDO>().eq(ChannelReactivateItemDO::getTaskId, taskId)
                .orderByAsc(ChannelReactivateItemDO::getId));
    }

    default List<ChannelReactivateItemDO> selectListByTaskIdAndStatus(Long taskId, String status) {
        return selectList(new LambdaQueryWrapperX<ChannelReactivateItemDO>().eq(ChannelReactivateItemDO::getTaskId, taskId)
                .eq(ChannelReactivateItemDO::getStatus, status).orderByAsc(ChannelReactivateItemDO::getId));
    }

    /** 这些客户中还在进行中（未发送、未跳过、未失败）的唤醒项 */
    default List<ChannelReactivateItemDO> selectActiveByCustomerIds(Collection<Long> customerIds) {
        return selectList(new LambdaQueryWrapperX<ChannelReactivateItemDO>().in(ChannelReactivateItemDO::getCustomerId, customerIds)
                .in(ChannelReactivateItemDO::getStatus, ChannelReactivateItemDO.STATUS_GENERATING,
                        ChannelReactivateItemDO.STATUS_DRAFT, ChannelReactivateItemDO.STATUS_APPROVED));
    }

}
