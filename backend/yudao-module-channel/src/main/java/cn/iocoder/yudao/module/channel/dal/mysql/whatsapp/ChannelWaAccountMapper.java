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

    /** YCloud 接入的号码：按 E.164 号码查（跨租户时在 TenantUtils.executeIgnore 中调用） */
    default ChannelWaAccountDO selectYcloudByPhone(String phoneE164) {
        return selectOne(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<ChannelWaAccountDO>()
                .eq(ChannelWaAccountDO::getAuthType, ChannelWaAccountDO.AUTH_YCLOUD)
                .eq(ChannelWaAccountDO::getDisplayPhone, phoneE164).last("LIMIT 1"));
    }

    default ChannelWaAccountDO selectByPhoneNumberId(String phoneNumberId) {
        return selectOne(ChannelWaAccountDO::getPhoneNumberId, phoneNumberId);
    }

}
