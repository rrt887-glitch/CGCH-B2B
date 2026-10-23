package cn.iocoder.yudao.module.channel.dal.mysql.notify;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.notify.ChannelNotifyRuleDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelNotifyRuleMapper extends BaseMapperX<ChannelNotifyRuleDO> {

    default List<ChannelNotifyRuleDO> selectByUser(Long userId) {
        return selectList(ChannelNotifyRuleDO::getUserId, userId);
    }

}
