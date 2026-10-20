package cn.iocoder.yudao.module.channel.service.ai;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.channel.service.event.ChannelInboundEvent;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 收到新消息后异步交给 AI 处理，不阻塞收信与 Webhook。
 * WhatsApp 延迟几秒再处理：客户连发多条时只回复一次。
 */
@Component
@Slf4j
public class ChannelAiInboundListener {

    private final AtomicInteger seq = new AtomicInteger();
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4, r -> {
        Thread t = new Thread(r, "channel-ai-" + seq.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    @Resource
    private ChannelAiService aiService;

    @Value("${cgch.ai.whatsapp-debounce-ms:3000}")
    private long whatsappDebounceMs;

    @EventListener
    public void onInbound(ChannelInboundEvent e) {
        long delay = "whatsapp".equals(e.channel()) ? whatsappDebounceMs : 0;
        executor.schedule(() -> {
            try {
                TenantUtils.execute(e.tenantId(), () -> {
                    if ("mail".equals(e.channel())) {
                        aiService.handleMailInbound(e.refId());
                    } else {
                        aiService.handleWhatsAppInbound(e.refId(), e.messageId());
                    }
                });
            } catch (Exception ex) {
                log.warn("[onInbound][{} {}] AI 处理失败: {}", e.channel(), e.refId(), ex.getMessage());
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

}
