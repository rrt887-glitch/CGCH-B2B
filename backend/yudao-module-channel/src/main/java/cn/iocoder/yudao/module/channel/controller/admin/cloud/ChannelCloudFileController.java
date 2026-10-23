package cn.iocoder.yudao.module.channel.controller.admin.cloud;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.channel.dal.dataobject.cloud.ChannelCloudFileDO;
import cn.iocoder.yudao.module.channel.service.cloud.ChannelCloudFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 云盘文件")
@RestController
@RequestMapping("/channel/cloud-file")
@Validated
public class ChannelCloudFileController {

    @Resource
    private ChannelCloudFileService channelCloudFileService;

    @GetMapping("/list")
    @Operation(summary = "目录下的文件；带 keyword 时在整个云盘里按名称搜索")
    public CommonResult<List<ChannelCloudFileDO>> list(@RequestParam(value = "scope", defaultValue = "enterprise") String scope,
                                                       @RequestParam(value = "parentId", required = false) Long parentId,
                                                       @RequestParam(value = "keyword", required = false) String keyword) {
        return success(channelCloudFileService.list(getLoginUserId(), scope, parentId, keyword));
    }

    @GetMapping("/enterprise-files")
    @Operation(summary = "企业云盘里的全部文件（不含文件夹），用于选择开场白附带的文件")
    public CommonResult<List<ChannelCloudFileDO>> enterpriseFiles() {
        return success(channelCloudFileService.enterpriseFiles());
    }

    @GetMapping("/path")
    @Operation(summary = "文件夹路径")
    public CommonResult<List<ChannelCloudFileDO>> path(@RequestParam("id") Long id) {
        return success(channelCloudFileService.path(getLoginUserId(), id));
    }

    @PostMapping("/folder")
    @Operation(summary = "新建文件夹")
    public CommonResult<Long> createFolder(@RequestBody Map<String, Object> req) {
        Object parent = req.get("parentId");
        return success(channelCloudFileService.createFolder(getLoginUserId(), (String) req.get("scope"),
                parent == null ? null : Long.valueOf(parent.toString()), (String) req.get("name")));
    }

    @PostMapping("/upload")
    @Operation(summary = "上传文件")
    public CommonResult<ChannelCloudFileDO> upload(@RequestParam("file") MultipartFile file,
                                                   @RequestParam(value = "scope", defaultValue = "enterprise") String scope,
                                                   @RequestParam(value = "parentId", required = false) Long parentId,
                                                   @RequestParam(value = "description", required = false) String description) throws Exception {
        return success(channelCloudFileService.upload(getLoginUserId(), scope, parentId, file.getOriginalFilename(),
                file.getContentType(), file.getBytes(), description));
    }

    @PutMapping("/update")
    @Operation(summary = "修改名称、描述、是否允许 AI 发送")
    public CommonResult<Boolean> update(@RequestBody Map<String, Object> req) {
        Object ai = req.get("aiSendable");
        channelCloudFileService.update(getLoginUserId(), Long.valueOf(req.get("id").toString()), (String) req.get("name"),
                (String) req.get("description"), ai == null ? null : Boolean.valueOf(ai.toString()));
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除文件或文件夹")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        channelCloudFileService.delete(getLoginUserId(), id);
        return success(true);
    }

}
