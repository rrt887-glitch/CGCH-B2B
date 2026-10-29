package cn.iocoder.yudao.module.channel.service.quickreply;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.channel.dal.dataobject.quickreply.ChannelQuickReplyDO;
import cn.iocoder.yudao.module.channel.dal.mysql.quickreply.ChannelQuickReplyMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.AI_LLM_FAIL;

/**
 * 快捷回复：全员快捷回复只有有「全员快捷回复维护」权限的人能增删改，个人快捷回复只有本人能看到和修改
 */
@Service
public class ChannelQuickReplyService {

    public static final String PERMISSION_MANAGE = "channel:quick-reply:manage";

    @Resource
    private ChannelQuickReplyMapper channelQuickReplyMapper;
    @Resource
    private SecurityFrameworkService securityFrameworkService;

    public List<ChannelQuickReplyDO> list(Long userId, String keyword) {
        return channelQuickReplyMapper.selectVisible(userId, StrUtil.trimToNull(keyword));
    }

    private ChannelQuickReplyDO getEditable(Long userId, Long id) {
        ChannelQuickReplyDO r = id == null ? null : channelQuickReplyMapper.selectById(id);
        boolean mine = r != null && ChannelQuickReplyDO.SCOPE_PERSONAL.equals(r.getScope()) && Objects.equals(r.getOwnerUserId(), userId);
        boolean shared = r != null && ChannelQuickReplyDO.SCOPE_ENTERPRISE.equals(r.getScope());
        if (!mine && !shared) {
            throw exception(AI_LLM_FAIL, "快捷回复不存在");
        }
        if (shared) {
            checkManage();
        }
        return r;
    }

    private void checkManage() {
        if (!securityFrameworkService.hasPermission(PERMISSION_MANAGE)) {
            throw exception(GlobalErrorCodeConstants.FORBIDDEN);
        }
    }

    public Long save(Long userId, ChannelQuickReplyDO req) {
        if (StrUtil.isBlank(req.getTitle()) || StrUtil.isBlank(req.getContent())) {
            throw exception(AI_LLM_FAIL, "请填写标题和内容");
        }
        String scope = ChannelQuickReplyDO.SCOPE_ENTERPRISE.equals(req.getScope()) ? ChannelQuickReplyDO.SCOPE_ENTERPRISE
                : ChannelQuickReplyDO.SCOPE_PERSONAL;
        if (ChannelQuickReplyDO.SCOPE_ENTERPRISE.equals(scope)) {
            checkManage();
        }
        ChannelQuickReplyDO r = ChannelQuickReplyDO.builder().scope(scope)
                .ownerUserId(ChannelQuickReplyDO.SCOPE_PERSONAL.equals(scope) ? userId : null)
                .title(StrUtil.maxLength(req.getTitle().trim(), 100)).content(StrUtil.maxLength(req.getContent().trim(), 4000))
                .sort(req.getSort() == null ? 0 : req.getSort()).build();
        if (req.getId() == null) {
            r.setUseCount(0);
            channelQuickReplyMapper.insert(r);
        } else {
            getEditable(userId, req.getId());
            r.setId(req.getId());
            channelQuickReplyMapper.updateById(r);
        }
        return r.getId();
    }

    public void delete(Long userId, Long id) {
        getEditable(userId, id);
        channelQuickReplyMapper.deleteById(id);
    }

    /** 业务员用了一次：计数，常用的排在前面 */
    public void used(Long userId, Long id) {
        ChannelQuickReplyDO r = id == null ? null : channelQuickReplyMapper.selectById(id);
        if (r != null && (ChannelQuickReplyDO.SCOPE_ENTERPRISE.equals(r.getScope()) || Objects.equals(r.getOwnerUserId(), userId))) {
            channelQuickReplyMapper.incrementUse(id);
        }
    }

}
