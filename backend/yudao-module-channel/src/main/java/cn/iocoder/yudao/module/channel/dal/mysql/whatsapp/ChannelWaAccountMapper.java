package cn.iocoder.yudao.module.channel.dal.mysql.whatsapp;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelWaAccountMapper extends BaseMapperX<ChannelWaAccountDO> {

    default List<ChannelWaAccountDO> selectListByUserId(Long userId) {
        return selectList(new LambdaQueryWrapperX<ChannelWaAccountDO>()
                .eq(ChannelWaAccountDO::getUserId, userId).orderByAsc(ChannelWaAccountDO::getId));
    }

    default ChannelWaAccountDO selectByPhoneNumberId(String phoneNumberId) {
        return selectOne(ChannelWaAccountDO::getPhoneNumberId, phoneNumberId);
    }

}
