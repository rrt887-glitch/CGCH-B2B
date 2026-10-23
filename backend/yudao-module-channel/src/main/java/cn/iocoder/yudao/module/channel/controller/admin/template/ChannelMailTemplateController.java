package cn.iocoder.yudao.module.channel.controller.admin.template;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.channel.dal.dataobject.template.ChannelMailTemplateDO;
import cn.iocoder.yudao.module.channel.service.template.ChannelMailTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 邮件模板")
@RestController
@RequestMapping("/channel/mail-template")
@Validated
public class ChannelMailTemplateController {

    @Resource
    private ChannelMailTemplateService channelMailTemplateService;

    @GetMapping("/list")
    @Operation(summary = "当前用户能看到的模板")
    public CommonResult<List<ChannelMailTemplateDO>> list(@RequestParam(value = "category", required = false) String category,
                                                          @RequestParam(value = "keyword", required = false) String keyword,
                                                          @RequestParam(value = "enabled", required = false) Boolean enabled) {
        return success(channelMailTemplateService.list(getLoginUserId(), category, keyword, enabled));
    }

    @GetMapping("/get")
    @Operation(summary = "模板详情")
    public CommonResult<ChannelMailTemplateDO> get(@RequestParam("id") Long id) {
        return success(channelMailTemplateService.get(getLoginUserId(), id));
    }

    @PostMapping("/save")
    @Operation(summary = "新建或修改模板")
    public CommonResult<Long> save(@RequestBody ChannelMailTemplateDO req) {
        return success(channelMailTemplateService.save(getLoginUserId(), req));
    }

    @PutMapping("/enabled")
    @Operation(summary = "启用 / 停用")
    public CommonResult<Boolean> enabled(@RequestParam("id") Long id, @RequestParam("enabled") Boolean enabled) {
        channelMailTemplateService.setEnabled(getLoginUserId(), id, enabled);
        return success(true);
    }

    @PutMapping("/default")
    @Operation(summary = "设为该分类的默认模板")
    public CommonResult<Boolean> setDefault(@RequestParam("id") Long id) {
        channelMailTemplateService.setDefault(getLoginUserId(), id);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除模板")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        channelMailTemplateService.delete(getLoginUserId(), id);
        return success(true);
    }

}
