package cn.iocoder.yudao.module.channel.controller.admin.file;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.channel.service.file.ChannelFileSecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 历史附件安全迁移：把早期「文件名可猜」的附件改成随机名，避免被遍历下载。
 * 只有平台管理员能执行；建议先 dryRun 看数量，再正式跑。
 */
@Tag(name = "管理后台 - 附件安全迁移")
@RestController
@RequestMapping("/channel/files")
public class ChannelFileSecurityController {

    @Resource
    private ChannelFileSecurityService channelFileSecurityService;

    @PostMapping("/security-migrate")
    @Operation(summary = "把历史附件改成不可猜的文件名（dryRun=true 只统计不修改）")
    @Parameter(name = "dryRun", description = "是否只统计", example = "true")
    @PreAuthorize("@ss.hasPermission('infra:file:delete')")
    public CommonResult<Map<String, Object>> migrate(
            @RequestParam(value = "dryRun", required = false, defaultValue = "true") Boolean dryRun) {
        ChannelFileSecurityService.Result result = channelFileSecurityService.migrate(Boolean.TRUE.equals(dryRun));
        return success(channelFileSecurityService.stats(result));
    }
}
