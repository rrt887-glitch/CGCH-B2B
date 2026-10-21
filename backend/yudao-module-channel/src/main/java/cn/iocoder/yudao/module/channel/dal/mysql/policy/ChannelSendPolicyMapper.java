package cn.iocoder.yudao.module.channel.dal.mysql.policy;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.policy.ChannelSendPolicyDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelSendPolicyMapper extends BaseMapperX<ChannelSendPolicyDO> {

    default ChannelSendPolicyDO selectCurrent() {
        List<ChannelSendPolicyDO> list = selectList();
        return list.isEmpty() ? null : list.get(0);
    }

}
