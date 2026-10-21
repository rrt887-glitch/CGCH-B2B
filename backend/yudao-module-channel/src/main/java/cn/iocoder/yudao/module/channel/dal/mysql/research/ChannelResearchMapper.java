package cn.iocoder.yudao.module.channel.dal.mysql.research;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.research.ChannelResearchDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChannelResearchMapper extends BaseMapperX<ChannelResearchDO> {

    default PageResult<ChannelResearchDO> selectPage(PageParam req, Long customerId, Long clueId, Long userId) {
        return selectPage(req, new LambdaQueryWrapperX<ChannelResearchDO>()
                .eqIfPresent(ChannelResearchDO::getCustomerId, customerId).eqIfPresent(ChannelResearchDO::getClueId, clueId)
                .eqIfPresent(ChannelResearchDO::getUserId, userId).orderByDesc(ChannelResearchDO::getId));
    }

}
