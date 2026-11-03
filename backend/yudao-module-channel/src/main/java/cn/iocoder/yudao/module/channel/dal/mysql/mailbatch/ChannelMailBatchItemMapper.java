package cn.iocoder.yudao.module.channel.dal.mysql.mailbatch;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.mailbatch.ChannelMailBatchItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mapper
public interface ChannelMailBatchItemMapper extends BaseMapperX<ChannelMailBatchItemDO> {

    default List<ChannelMailBatchItemDO> selectListByBatch(Long batchId, String status) {
        LambdaQueryWrapperX<ChannelMailBatchItemDO> q = new LambdaQueryWrapperX<>();
        q.eq(ChannelMailBatchItemDO::getBatchId, batchId);
        q.eqIfPresent(ChannelMailBatchItemDO::getStatus, status);
        q.orderByAsc(ChannelMailBatchItemDO::getId);
        return selectList(q);
    }

    default ChannelMailBatchItemDO selectNextPending(Long batchId) {
        return selectOne(new LambdaQueryWrapperX<ChannelMailBatchItemDO>().eq(ChannelMailBatchItemDO::getBatchId, batchId)
                .eq(ChannelMailBatchItemDO::getStatus, ChannelMailBatchItemDO.STATUS_PENDING)
                .orderByAsc(ChannelMailBatchItemDO::getId).last("LIMIT 1"));
    }

    default ChannelMailBatchItemDO selectByToken(String token) {
        return selectOne(ChannelMailBatchItemDO::getToken, token);
    }

    /** 各状态数量 */
    default Map<String, Integer> countByStatus(Long batchId) {
        Map<String, Integer> m = new HashMap<>();
        selectListByBatch(batchId, null).forEach(i -> m.merge(i.getStatus(), 1, Integer::sum));
        return m;
    }

}
