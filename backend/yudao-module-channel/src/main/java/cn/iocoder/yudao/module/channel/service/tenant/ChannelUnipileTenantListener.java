package cn.iocoder.yudao.module.channel.service.tenant;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.channel.service.unipile.ChannelUnipileService;
import cn.iocoder.yudao.module.system.api.tenant.TenantCreatedEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 开户时就把 Unipile 的隔离边界准备好：为新客户建一个 Scope + 专属 Key。
 *
 * 和 CRM 初始化不同，这里**不能**放在开户事务里同步执行：
 * 建 Scope / 建 Key 是两次外部 HTTP 调用，Unipile 抖动时会让整个开户失败。
 * 所以改成「事务提交后、异步执行」，失败只记日志；客户第一次点「连接」时
 * {@link ChannelUnipileService#credential()} 还会兜底再建一次。
 */
@Component
@Slf4j
public class ChannelUnipileTenantListener {

    @Resource
    private ChannelUnipileService channelUnipileService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTenantCreated(TenantCreatedEvent event) {
        if (!channelUnipileService.enabled()) {
            return;
        }
        try {
            TenantUtils.execute(event.tenantId(), () -> channelUnipileService.credential());
            log.info("[onTenantCreated][租户 {} 已预建 Unipile Scope]", event.tenantId());
        } catch (Exception e) {
            log.warn("[onTenantCreated][租户 {} 预建 Unipile 凭证失败，等首次连接时再建：{}]",
                    event.tenantId(), e.getMessage());
        }
    }
}
