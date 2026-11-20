package cn.iocoder.yudao.module.channel.dal.mysql.unipile;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.unipile.ChannelUnipileCredentialDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelUnipileCredentialMapper extends BaseMapperX<ChannelUnipileCredentialDO> {

    /** 当前公司的 Unipile 凭证（每家一条） */
    default ChannelUnipileCredentialDO selectCurrent() {
        List<ChannelUnipileCredentialDO> list = selectList();
        return list.isEmpty() ? null : list.get(0);
    }

}
