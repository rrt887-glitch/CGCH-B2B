package cn.iocoder.yudao.module.channel.controller.admin.translate;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.channel.service.translate.ChannelTranslateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 翻译")
@RestController
@RequestMapping("/channel/translate")
@Validated
public class ChannelTranslateController {

    public record TranslateReq(List<String> texts, String target, String sample) {
    }

    @Resource
    private ChannelTranslateService channelTranslateService;

    @PostMapping
    @Operation(summary = "批量翻译：target 目标语言（默认中文）；sample 不为空时译成与它相同的语言")
    public CommonResult<List<String>> translate(@RequestBody TranslateReq req) {
        return success(channelTranslateService.translate(getLoginUserId(), req.texts(), req.target(), req.sample()));
    }

}
