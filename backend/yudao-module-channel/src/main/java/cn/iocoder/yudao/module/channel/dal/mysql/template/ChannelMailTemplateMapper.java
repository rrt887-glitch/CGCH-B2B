package cn.iocoder.yudao.module.channel.dal.mysql.template;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.template.ChannelMailTemplateDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelMailTemplateMapper extends BaseMapperX<ChannelMailTemplateDO> {

    /** 当前用户能看到的模板：全员可见的和自己的 */
    default List<ChannelMailTemplateDO> selectVisible(Long userId, String category, String keyword, Boolean enabled) {
        return selectList(new LambdaQueryWrapperX<ChannelMailTemplateDO>()
                .and(w -> w.eq(ChannelMailTemplateDO::getVisibility, ChannelMailTemplateDO.VISIBILITY_ALL)
                        .or().eq(ChannelMailTemplateDO::getOwnerUserId, userId))
                .eqIfPresent(ChannelMailTemplateDO::getCategory, category)
                .likeIfPresent(ChannelMailTemplateDO::getName, keyword)
                .eqIfPresent(ChannelMailTemplateDO::getEnabled, enabled)
                .orderByDesc(ChannelMailTemplateDO::getDefaulted).orderByDesc(ChannelMailTemplateDO::getUpdateTime));
    }

}
