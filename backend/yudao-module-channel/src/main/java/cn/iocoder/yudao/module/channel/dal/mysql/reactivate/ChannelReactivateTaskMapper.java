package cn.iocoder.yudao.module.channel.dal.mysql.reactivate;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.reactivate.ChannelReactivateTaskDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChannelReactivateTaskMapper extends BaseMapperX<ChannelReactivateTaskDO> {

    default PageResult<ChannelReactivateTaskDO> selectPage(PageParam req) {
        return selectPage(req, new LambdaQueryWrapperX<ChannelReactivateTaskDO>().orderByDesc(ChannelReactivateTaskDO::getId));
    }

}
