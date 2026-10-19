package cn.iocoder.yudao.module.channel.dal.mysql.mail;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailAccountDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelMailAccountMapper extends BaseMapperX<ChannelMailAccountDO> {

    default List<ChannelMailAccountDO> selectListByUserId(Long userId) {
        return selectList(new LambdaQueryWrapperX<ChannelMailAccountDO>()
                .eq(ChannelMailAccountDO::getUserId, userId)
                .orderByAsc(ChannelMailAccountDO::getId));
    }

    default List<ChannelMailAccountDO> selectListByEnabled() {
        return selectList(ChannelMailAccountDO::getEnabled, true);
    }

    default ChannelMailAccountDO selectByEmail(String email) {
        return selectOne(ChannelMailAccountDO::getEmail, email);
    }

}
