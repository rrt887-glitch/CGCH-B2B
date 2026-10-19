package cn.iocoder.yudao.module.channel.job;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailAccountDO;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailAccountMapper;
import cn.iocoder.yudao.module.channel.service.mail.ChannelMailService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定时收信：遍历所有租户中已启用的邮箱，逐个同步收件箱。
 * 间隔由 cgch.mail.sync-interval-ms 控制（默认 5 分钟），cgch.mail.sync-enabled=false 可关闭。
 */
@Component
@ConditionalOnProperty(prefix = "cgch.mail", name = "sync-enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class MailSyncScheduler {

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Resource
    private ChannelMailAccountMapper accountMapper;
    @Resource
    private ChannelMailService mailService;

    @Scheduled(initialDelayString = "${cgch.mail.sync-initial-delay-ms:60000}",
            fixedDelayString = "${cgch.mail.sync-interval-ms:300000}")
    public void syncAll() {
        if (!running.compareAndSet(false, true)) {
            return; // 上一轮还没结束
        }
        try {
            List<ChannelMailAccountDO> accounts = TenantUtils.executeIgnore(accountMapper::selectListByEnabled);
            int total = 0;
            for (ChannelMailAccountDO account : accounts) {
                try {
                    total += TenantUtils.execute(account.getTenantId(), () -> mailService.syncAccount(account));
                } catch (Exception e) {
                    // 失败原因已记录在账号上，继续下一个
                    log.debug("[syncAll][{}] 跳过: {}", account.getEmail(), e.getMessage());
                }
            }
            if (total > 0) {
                log.info("[syncAll] 本轮共收到 {} 封新邮件，邮箱 {} 个", total, accounts.size());
            }
        } finally {
            running.set(false);
        }
    }

}
