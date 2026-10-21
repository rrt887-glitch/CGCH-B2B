package cn.iocoder.yudao.module.channel.dal.mysql.policy;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.policy.ChannelSuppressionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ChannelSuppressionMapper extends BaseMapperX<ChannelSuppressionDO> {

    default List<ChannelSuppressionDO> selectMatches(String channel, Collection<String> values) {
        return selectList(new LambdaQueryWrapperX<ChannelSuppressionDO>().eq(ChannelSuppressionDO::getChannel, channel)
                .in(ChannelSuppressionDO::getAddress, values));
    }

    default ChannelSuppressionDO selectOne(String channel, String value) {
        return selectOne(new LambdaQueryWrapperX<ChannelSuppressionDO>().eq(ChannelSuppressionDO::getChannel, channel)
                .eq(ChannelSuppressionDO::getAddress, value).last("LIMIT 1"));
    }

    default PageResult<ChannelSuppressionDO> selectPage(PageParam req, String channel, String reason, String keyword) {
        return selectPage(req, new LambdaQueryWrapperX<ChannelSuppressionDO>().eqIfPresent(ChannelSuppressionDO::getChannel, channel)
                .eqIfPresent(ChannelSuppressionDO::getReason, reason).likeIfPresent(ChannelSuppressionDO::getAddress, keyword)
                .orderByDesc(ChannelSuppressionDO::getId));
    }

}
