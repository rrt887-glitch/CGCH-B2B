package cn.iocoder.yudao.module.channel.job;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiConfigDO;
import cn.iocoder.yudao.module.channel.dal.mysql.ai.ChannelAiConfigMapper;
import cn.iocoder.yudao.module.channel.service.ai.ChannelAiService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 智能跟进：定时检查开启了「智能跟进客户」的企业，客户迟迟未回复 AI 的消息时自动跟进。
 * 检查间隔由 cgch.ai.follow-up-interval-ms 控制（默认 30 分钟）。
 */
@Component
@Slf4j
public class AiFollowUpScheduler {

    @Resource
    private ChannelAiConfigMapper channelAiConfigMapper;
    @Resource
    private ChannelAiService channelAiService;

    @Scheduled(initialDelayString = "${cgch.ai.follow-up-interval-ms:1800000}", fixedDelayString = "${cgch.ai.follow-up-interval-ms:1800000}")
    public void run() {
        List<ChannelAiConfigDO> configs = TenantUtils.executeIgnore(() -> channelAiConfigMapper.selectList(
                new LambdaQueryWrapperX<ChannelAiConfigDO>().like(ChannelAiConfigDO::getSkillsJson, "\"followUp\":true")));
        for (ChannelAiConfigDO c : configs) {
            try {
                TenantUtils.execute(c.getTenantId(), () -> {
                    int n = channelAiService.runFollowUps();
                    if (n > 0) {
                        log.info("[run][租户 {} 发出 {} 条智能跟进]", c.getTenantId(), n);
                    }
                });
            } catch (Exception e) {
                log.warn("[run][租户 {} 智能跟进失败] {}", c.getTenantId(), e.getMessage());
            }
        }
    }

}
