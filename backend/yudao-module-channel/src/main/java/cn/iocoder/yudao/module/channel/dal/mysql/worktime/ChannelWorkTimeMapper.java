package cn.iocoder.yudao.module.channel.dal.mysql.worktime;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.worktime.ChannelWorkTimeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelWorkTimeMapper extends BaseMapperX<ChannelWorkTimeDO> {

    default ChannelWorkTimeDO selectCurrent() {
        List<ChannelWorkTimeDO> list = selectList();
        return list.isEmpty() ? null : list.get(0);
    }

}
