package cn.iocoder.yudao.module.channel.service.whatsapp;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaConversationDO;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaAccountMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaConversationMapper;
import cn.iocoder.yudao.module.system.api.user.event.AdminUserDeletedEvent;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 账号被删后，把他名下的会话和渠道账号交给公司管理员，避免「负责人已不存在」。
 */
@Slf4j
@Component
public class ChannelUserDeletedListener {

    @Resource
    private ChannelWaConversationMapper conversationMapper;
    @Resource
    private ChannelWaAccountMapper accountMapper;

    @EventListener
    public void onUserDeleted(AdminUserDeletedEvent event) {
        try {
            TenantUtils.execute(event.getTenantId(), () -> {
                Long userId = event.getUserId();
                Long newOwner = event.getNewOwnerUserId();
                int conv = conversationMapper.update(null, new LambdaUpdateWrapper<ChannelWaConversationDO>()
                        .eq(ChannelWaConversationDO::getOwnerUserId, userId)
                        .set(ChannelWaConversationDO::getOwnerUserId, newOwner));
                int acc = accountMapper.update(null, new LambdaUpdateWrapper<ChannelWaAccountDO>()
                        .eq(ChannelWaAccountDO::getUserId, userId)
                        .set(ChannelWaAccountDO::getUserId, newOwner));
                log.info("[onUserDeleted][公司({}) 账号({}) 的 {} 条会话、{} 个渠道账号转给 ({})]",
                        event.getTenantId(), userId, conv, acc, newOwner);
            });
        } catch (Exception ex) {
            log.warn("[onUserDeleted][账号({}) 会话交接失败] {}", event.getUserId(), ex.getMessage());
        }
    }

}
