package cn.iocoder.yudao.module.channel.dal.mysql.mail;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailAttachmentDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelMailAttachmentMapper extends BaseMapperX<ChannelMailAttachmentDO> {

    default List<ChannelMailAttachmentDO> selectListByMessageId(Long messageId) {
        return selectList(new LambdaQueryWrapperX<ChannelMailAttachmentDO>()
                .eq(ChannelMailAttachmentDO::getMessageId, messageId).orderByAsc(ChannelMailAttachmentDO::getId));
    }

}
