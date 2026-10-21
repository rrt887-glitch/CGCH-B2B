package cn.iocoder.yudao.module.channel.service.ai;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.channel.service.event.ChannelInboundEvent;
import cn.iocoder.yudao.module.channel.service.lead.ChannelLeadService;
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
 * 收到新消息后异步处理：先线索自动建档，再交给 AI 回复，不阻塞收信与 Webhook。
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
    @Resource
    private ChannelLeadService channelLeadService;
    @Resource
    private cn.iocoder.yudao.module.channel.service.policy.ChannelSendPolicyService channelSendPolicyService;
    @Resource
    private cn.iocoder.yudao.module.channel.service.notify.ChannelNotifyService channelNotifyService;

    @Value("${cgch.ai.whatsapp-debounce-ms:3000}")
    private long whatsappDebounceMs;

    @EventListener
    public void onInbound(ChannelInboundEvent e) {
        long delay = "whatsapp".equals(e.channel()) ? whatsappDebounceMs : 0;
        executor.schedule(() -> {
            try {
                TenantUtils.execute(e.tenantId(), () -> {
                    // 0. 退订请求、退信：加入名单，不再建线索或回复
                    try {
                        if (channelSendPolicyService.onInbound(e.channel(), e.refId(), e.messageId())) {
                            return;
                        }
                    } catch (Exception ex) {
                        log.warn("[onInbound][{} {}] 退订识别失败: {}", e.channel(), e.refId(), ex.getMessage());
                    }
                    // 1. 线索自动建档（失败不影响 AI 回复）
                    try {
                        if ("mail".equals(e.channel())) {
                            channelLeadService.handleMail(e.refId());
                        } else {
                            channelLeadService.handleWhatsApp(e.refId());
                        }
                    } catch (Exception ex) {
                        log.warn("[onInbound][{} {}] 线索建档失败: {}", e.channel(), e.refId(), ex.getMessage());
                    }
                    // 2. 客户唤醒的回复提醒
                    try {
                        channelNotifyService.onInbound(e.channel(), e.refId(), e.messageId());
                    } catch (Exception ex) {
                        log.warn("[onInbound][{} {}] 回复提醒失败: {}", e.channel(), e.refId(), ex.getMessage());
                    }
                    // 3. AI 回复
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
