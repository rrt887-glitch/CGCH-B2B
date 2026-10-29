package cn.iocoder.yudao.module.channel.service.cloud;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.cloud.ChannelCloudFileDO;
import cn.iocoder.yudao.module.channel.dal.mysql.cloud.ChannelCloudFileMapper;
import cn.iocoder.yudao.module.channel.service.file.ChannelFileHelper;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.AI_LLM_FAIL;

/**
 * 云盘文件：企业云盘全员共享，可设置为允许 AI 发给客户；个人云盘只有本人能看到。
 */
@Service
public class ChannelCloudFileService {

    @Resource
    private ChannelCloudFileMapper channelCloudFileMapper;
    @Resource
    private ChannelFileHelper channelFileHelper;
    @Resource
    private SecurityFrameworkService securityFrameworkService;

    /** 维护企业云盘权限：企业云盘的文件 AI 会发给客户，只有管理员等有权限的人能增删改 */
    public static final String PERMISSION_MANAGE = "channel:cloud:manage";

    private void checkWritable(String scope) {
        if (ChannelCloudFileDO.SCOPE_ENTERPRISE.equals(scope) && !securityFrameworkService.hasPermission(PERMISSION_MANAGE)) {
            throw exception(cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN);
        }
    }

    private static String scope(String s) {
        return ChannelCloudFileDO.SCOPE_PERSONAL.equals(s) ? ChannelCloudFileDO.SCOPE_PERSONAL : ChannelCloudFileDO.SCOPE_ENTERPRISE;
    }

    private static Long owner(String scope, Long userId) {
        return ChannelCloudFileDO.SCOPE_PERSONAL.equals(scope) ? userId : null;
    }

    public List<ChannelCloudFileDO> list(Long userId, String scope, Long parentId, String keyword) {
        String s = scope(scope);
        return channelCloudFileMapper.selectChildren(s, owner(s, userId), parentId == null ? 0L : parentId, StrUtil.trimToNull(keyword));
    }

    /** 从根目录到这个文件夹的路径（面包屑） */
    public List<ChannelCloudFileDO> path(Long userId, Long folderId) {
        List<ChannelCloudFileDO> out = new ArrayList<>();
        Long id = folderId;
        for (int i = 0; id != null && id != 0 && i < 20; i++) {
            ChannelCloudFileDO f = get(userId, id);
            out.add(0, f);
            id = f.getParentId();
        }
        return out;
    }

    /** 读取并校验访问权限：个人云盘只有本人能访问 */
    public ChannelCloudFileDO get(Long userId, Long id) {
        ChannelCloudFileDO f = channelCloudFileMapper.selectById(id);
        if (f == null || (ChannelCloudFileDO.SCOPE_PERSONAL.equals(f.getScope()) && !Objects.equals(f.getOwnerUserId(), userId))) {
            throw exception(AI_LLM_FAIL, "文件不存在");
        }
        return f;
    }

    private void checkParent(Long userId, String scope, Long parentId) {
        if (parentId == null || parentId == 0) {
            return;
        }
        ChannelCloudFileDO p = get(userId, parentId);
        if (!Boolean.TRUE.equals(p.getFolder()) || !scope.equals(p.getScope())) {
            throw exception(AI_LLM_FAIL, "上级文件夹不存在");
        }
    }

    public Long createFolder(Long userId, String scope, Long parentId, String name) {
        String s = scope(scope);
        checkWritable(s);
        if (StrUtil.isBlank(name)) {
            throw exception(AI_LLM_FAIL, "请输入文件夹名称");
        }
        checkParent(userId, s, parentId);
        ChannelCloudFileDO f = ChannelCloudFileDO.builder().scope(s).ownerUserId(owner(s, userId)).parentId(parentId == null ? 0L : parentId)
                .folder(true).name(StrUtil.maxLength(name.trim(), 200)).description("").aiSendable(false).size(0L).build();
        channelCloudFileMapper.insert(f);
        return f.getId();
    }

    public ChannelCloudFileDO upload(Long userId, String scope, Long parentId, String fileName, String mimeType, byte[] content, String description) {
        String s = scope(scope);
        checkWritable(s);
        checkParent(userId, s, parentId);
        if (content == null || content.length == 0) {
            throw exception(AI_LLM_FAIL, "文件是空的");
        }
        if (content.length > ChannelFileHelper.MAX_BYTES) {
            throw exception(AI_LLM_FAIL, "文件超过 25MB");
        }
        String name = StrUtil.blankToDefault(FileUtil.getName(StrUtil.nullToEmpty(fileName)), "file");
        String type = StrUtil.blankToDefault(mimeType, "application/octet-stream");
        String url = channelFileHelper.save(content, name, "cloud", type);
        ChannelCloudFileDO f = ChannelCloudFileDO.builder().scope(s).ownerUserId(owner(s, userId)).parentId(parentId == null ? 0L : parentId)
                .folder(false).name(StrUtil.maxLength(name, 200)).description(StrUtil.maxLength(StrUtil.nullToEmpty(description), 250))
                .url(url).size((long) content.length).mimeType(StrUtil.maxLength(type, 100))
                .aiSendable(ChannelCloudFileDO.SCOPE_ENTERPRISE.equals(s)).build();
        channelCloudFileMapper.insert(f);
        return f;
    }

    /** 修改名称、描述、是否允许 AI 发送 */
    public void update(Long userId, Long id, String name, String description, Boolean aiSendable) {
        ChannelCloudFileDO f = get(userId, id);
        checkWritable(f.getScope());
        ChannelCloudFileDO upd = new ChannelCloudFileDO().setId(id);
        if (StrUtil.isNotBlank(name)) {
            upd.setName(StrUtil.maxLength(name.trim(), 200));
        }
        if (description != null) {
            upd.setDescription(StrUtil.maxLength(description.trim(), 250));
        }
        if (aiSendable != null && !Boolean.TRUE.equals(f.getFolder())) {
            upd.setAiSendable(aiSendable && ChannelCloudFileDO.SCOPE_ENTERPRISE.equals(f.getScope()));
        }
        channelCloudFileMapper.updateById(upd);
    }

    /** 删除；文件夹连同里面的文件一起删除 */
    public void delete(Long userId, Long id) {
        ChannelCloudFileDO f = get(userId, id);
        checkWritable(f.getScope());
        List<Long> ids = new ArrayList<>(List.of(f.getId()));
        for (int i = 0; i < ids.size() && i < 5000; i++) {
            Long cur = ids.get(i);
            channelCloudFileMapper.selectList(new LambdaQueryWrapperX<ChannelCloudFileDO>().eq(ChannelCloudFileDO::getParentId, cur))
                    .forEach(c -> ids.add(c.getId()));
        }
        channelCloudFileMapper.deleteByIds(ids);
    }

    public List<ChannelCloudFileDO> enterpriseFiles() {
        return channelCloudFileMapper.selectList(new LambdaQueryWrapperX<ChannelCloudFileDO>()
                .eq(ChannelCloudFileDO::getScope, ChannelCloudFileDO.SCOPE_ENTERPRISE).eq(ChannelCloudFileDO::getFolder, false)
                .orderByDesc(ChannelCloudFileDO::getUpdateTime).last("LIMIT 500"));
    }

    /** AI 可以发给客户的文件（企业云盘里允许 AI 发送的） */
    public List<ChannelCloudFileDO> aiSendable() {
        return channelCloudFileMapper.selectAiSendable(50);
    }

    /** 按编号取企业云盘的文件（不含文件夹），顺序与传入一致 */
    public List<ChannelCloudFileDO> getEnterpriseFiles(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<ChannelCloudFileDO> list = channelCloudFileMapper.selectByIds(ids);
        return ids.stream().map(id -> list.stream().filter(f -> f.getId().equals(id)).findFirst().orElse(null))
                .filter(f -> f != null && !Boolean.TRUE.equals(f.getFolder()) && ChannelCloudFileDO.SCOPE_ENTERPRISE.equals(f.getScope()))
                .toList();
    }

    public byte[] content(ChannelCloudFileDO f) throws Exception {
        return channelFileHelper.load(f.getUrl());
    }

}
