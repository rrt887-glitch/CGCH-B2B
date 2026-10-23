package cn.iocoder.yudao.module.channel.service.template;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaUpdateWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.template.ChannelMailTemplateDO;
import cn.iocoder.yudao.module.channel.dal.mysql.template.ChannelMailTemplateMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.AI_LLM_FAIL;

/**
 * 邮件模板：全员可见的模板所有人可用、可编辑；「仅自己」的模板只有创建人能看到。
 */
@Service
public class ChannelMailTemplateService {

    private static final Set<String> CATEGORIES = Set.of(ChannelMailTemplateDO.CATEGORY_PROMO, ChannelMailTemplateDO.CATEGORY_NOTICE,
            ChannelMailTemplateDO.CATEGORY_TRANSACTION);

    @Resource
    private ChannelMailTemplateMapper channelMailTemplateMapper;

    public List<ChannelMailTemplateDO> list(Long userId, String category, String keyword, Boolean enabled) {
        return channelMailTemplateMapper.selectVisible(userId, StrUtil.emptyToNull(category), StrUtil.trimToNull(keyword), enabled);
    }

    public ChannelMailTemplateDO get(Long userId, Long id) {
        ChannelMailTemplateDO t = channelMailTemplateMapper.selectById(id);
        if (t == null || (ChannelMailTemplateDO.VISIBILITY_SELF.equals(t.getVisibility()) && !Objects.equals(t.getOwnerUserId(), userId))) {
            throw exception(AI_LLM_FAIL, "模板不存在");
        }
        return t;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long save(Long userId, ChannelMailTemplateDO req) {
        if (StrUtil.isBlank(req.getName())) {
            throw exception(AI_LLM_FAIL, "请输入模板名称");
        }
        if (StrUtil.isBlank(req.getSubject()) && StrUtil.isBlank(req.getHtml())) {
            throw exception(AI_LLM_FAIL, "请填写邮件标题或正文");
        }
        ChannelMailTemplateDO t = ChannelMailTemplateDO.builder()
                .name(StrUtil.maxLength(req.getName().trim(), 100))
                .category(CATEGORIES.contains(req.getCategory()) ? req.getCategory() : ChannelMailTemplateDO.CATEGORY_PROMO)
                .visibility(ChannelMailTemplateDO.VISIBILITY_SELF.equals(req.getVisibility()) ? ChannelMailTemplateDO.VISIBILITY_SELF : ChannelMailTemplateDO.VISIBILITY_ALL)
                .subject(StrUtil.maxLength(StrUtil.nullToEmpty(req.getSubject()), 250))
                .html(StrUtil.maxLength(StrUtil.nullToEmpty(req.getHtml()), 200_000))
                .enabled(req.getEnabled() == null || req.getEnabled())
                .defaulted(Boolean.TRUE.equals(req.getDefaulted()))
                .build();
        if (req.getId() == null) {
            t.setOwnerUserId(userId);
            channelMailTemplateMapper.insert(t);
        } else {
            get(userId, req.getId());
            t.setId(req.getId());
            channelMailTemplateMapper.updateById(t);
        }
        if (Boolean.TRUE.equals(t.getDefaulted())) {
            clearOtherDefaults(t);
        }
        return t.getId();
    }

    /** 每个分类只有一个默认模板 */
    private void clearOtherDefaults(ChannelMailTemplateDO t) {
        channelMailTemplateMapper.update(null, new LambdaUpdateWrapperX<ChannelMailTemplateDO>()
                .set(ChannelMailTemplateDO::getDefaulted, false)
                .eq(ChannelMailTemplateDO::getCategory, t.getCategory())
                .ne(ChannelMailTemplateDO::getId, t.getId()));
    }

    public void setEnabled(Long userId, Long id, boolean enabled) {
        get(userId, id);
        channelMailTemplateMapper.updateById(new ChannelMailTemplateDO().setId(id).setEnabled(enabled));
    }

    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long userId, Long id) {
        ChannelMailTemplateDO t = get(userId, id);
        channelMailTemplateMapper.updateById(new ChannelMailTemplateDO().setId(id).setDefaulted(true));
        clearOtherDefaults(t);
    }

    public void delete(Long userId, Long id) {
        get(userId, id);
        channelMailTemplateMapper.deleteById(id);
    }

}
