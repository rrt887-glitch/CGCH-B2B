package cn.iocoder.yudao.module.channel.dal.mysql.mail;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.controller.admin.mail.vo.ChannelMailMessagePageReqVO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailMessageDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;

@Mapper
public interface ChannelMailMessageMapper extends BaseMapperX<ChannelMailMessageDO> {

    /** 列表视图：inbox 收件箱、unreplied 未回复、sent 已发送、draft 草稿、trash 已删除 */
    default PageResult<ChannelMailMessageDO> selectPage(ChannelMailMessagePageReqVO req, Collection<Long> accountIds) {
        LambdaQueryWrapperX<ChannelMailMessageDO> q = new LambdaQueryWrapperX<ChannelMailMessageDO>()
                .in(ChannelMailMessageDO::getAccountId, accountIds)
                .eqIfPresent(ChannelMailMessageDO::getCustomerId, req.getCustomerId());
        String view = StrUtil.blankToDefault(req.getView(), "inbox");
        switch (view) {
            case "unreplied" -> q.eq(ChannelMailMessageDO::getFolder, ChannelMailMessageDO.FOLDER_INBOX)
                    .eq(ChannelMailMessageDO::getReplied, false);
            case "sent" -> q.eq(ChannelMailMessageDO::getFolder, ChannelMailMessageDO.FOLDER_SENT);
            case "draft" -> q.eq(ChannelMailMessageDO::getFolder, ChannelMailMessageDO.FOLDER_DRAFT);
            case "trash" -> q.eq(ChannelMailMessageDO::getFolder, ChannelMailMessageDO.FOLDER_TRASH);
            default -> q.eq(ChannelMailMessageDO::getFolder, ChannelMailMessageDO.FOLDER_INBOX);
        }
        if (StrUtil.isNotBlank(req.getKeyword())) {
            String kw = req.getKeyword().trim();
            q.and(w -> w.like(ChannelMailMessageDO::getSubject, kw).or()
                    .like(ChannelMailMessageDO::getFromAddress, kw).or()
                    .like(ChannelMailMessageDO::getFromName, kw).or()
                    .like(ChannelMailMessageDO::getToAddresses, kw));
        }
        q.orderByDesc(ChannelMailMessageDO::getSentTime).orderByDesc(ChannelMailMessageDO::getId);
        return selectPage(req, q);
    }

    default ChannelMailMessageDO selectByAccountIdAndMessageId(Long accountId, String messageId) {
        return selectOne(ChannelMailMessageDO::getAccountId, accountId, ChannelMailMessageDO::getMessageId, messageId);
    }

    default ChannelMailMessageDO selectByAccountIdAndImapUid(Long accountId, Long imapUid) {
        return selectOne(ChannelMailMessageDO::getAccountId, accountId, ChannelMailMessageDO::getImapUid, imapUid);
    }

    default Long selectUnrepliedCount(Collection<Long> accountIds) {
        return selectCount(new LambdaQueryWrapperX<ChannelMailMessageDO>()
                .in(ChannelMailMessageDO::getAccountId, accountIds)
                .eq(ChannelMailMessageDO::getFolder, ChannelMailMessageDO.FOLDER_INBOX)
                .eq(ChannelMailMessageDO::getReplied, false));
    }

}
