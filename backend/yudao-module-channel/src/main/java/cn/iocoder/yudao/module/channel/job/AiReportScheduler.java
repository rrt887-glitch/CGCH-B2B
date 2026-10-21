package cn.iocoder.yudao.module.channel.job;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiConfigDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiReportDO;
import cn.iocoder.yudao.module.channel.dal.mysql.ai.ChannelAiConfigMapper;
import cn.iocoder.yudao.module.channel.service.report.ChannelAiReportService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * 每天早上为开启了「自动生成」的租户生成昨天的日报；周一另生成上周周报。已生成过的不重复生成。
 * 时间由 cgch.ai.report-cron 控制（默认每天 07:30），cgch.ai.report-enabled=false 可关闭。
 */
@Component
@ConditionalOnProperty(prefix = "cgch.ai", name = "report-enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class AiReportScheduler {

    @Resource
    private ChannelAiConfigMapper channelAiConfigMapper;
    @Resource
    private ChannelAiReportService channelAiReportService;

    @Scheduled(cron = "${cgch.ai.report-cron:0 30 7 * * ?}")
    public void run() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<ChannelAiConfigDO> configs = TenantUtils.executeIgnore(() -> channelAiConfigMapper.selectList());
        for (ChannelAiConfigDO cfg : configs) {
            if (!Boolean.TRUE.equals(cfg.getReportAuto())) {
                continue;
            }
            try {
                TenantUtils.execute(cfg.getTenantId(), () -> {
                    if (!channelAiReportService.exists(ChannelAiReportDO.TYPE_DAILY, yesterday)) {
                        channelAiReportService.generate(ChannelAiReportDO.TYPE_DAILY, yesterday, ChannelAiReportService.MODE_AUTO, null);
                    }
                    if (LocalDate.now().getDayOfWeek() == DayOfWeek.MONDAY
                            && !channelAiReportService.exists(ChannelAiReportDO.TYPE_WEEKLY, yesterday)) {
                        channelAiReportService.generate(ChannelAiReportDO.TYPE_WEEKLY, yesterday, ChannelAiReportService.MODE_AUTO, null);
                    }
                });
            } catch (Exception e) {
                log.warn("[run][租户({}) 生成工作报告失败] {}", cfg.getTenantId(), e.getMessage());
            }
        }
    }

}
