package cn.iocoder.yudao.module.channel.service.routing;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.channel.dal.dataobject.routing.ChannelRoutingRuleDO;
import cn.iocoder.yudao.module.channel.dal.mysql.routing.ChannelRoutingRuleMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.AI_LLM_FAIL;

/**
 * 消息分流：新询盘建档时，按顺序找到第一条命中的启用规则，把线索分配给规则里的业务员（多人轮流）。
 */
@Service
@Slf4j
public class ChannelRoutingService {

    @Resource
    private ChannelRoutingRuleMapper channelRoutingRuleMapper;

    public List<ChannelRoutingRuleDO> list() {
        return channelRoutingRuleMapper.selectOrdered();
    }

    public Long save(ChannelRoutingRuleDO req) {
        List<String> sources = StrUtil.split(StrUtil.nullToEmpty(req.getSources()), ',', true, true).stream()
                .filter(ChannelRoutingRuleDO.SOURCES::contains).distinct().toList();
        String op = "in".equals(req.getCountryOp()) || "not_in".equals(req.getCountryOp()) ? req.getCountryOp() : null;
        List<String> countries = StrUtil.split(StrUtil.nullToEmpty(req.getCountries()), ',', true, true).stream()
                .map(c -> c.toUpperCase(Locale.ROOT)).filter(c -> c.matches("[A-Z]{2}")).distinct().toList();
        if (op != null && countries.isEmpty()) {
            throw exception(AI_LLM_FAIL, "请选择客户国家");
        }
        List<Long> assignees = req.getAssigneeIds() == null ? List.of() : req.getAssigneeIds().stream().filter(Objects::nonNull).distinct().toList();
        if (assignees.isEmpty()) {
            throw exception(AI_LLM_FAIL, "请选择负责人");
        }
        ChannelRoutingRuleDO r = ChannelRoutingRuleDO.builder()
                .name(StrUtil.maxLength(StrUtil.blankToDefault(StrUtil.trim(req.getName()), "未命名分配规则"), 100))
                .enabled(req.getEnabled() == null || req.getEnabled())
                .sort(req.getSort() == null ? 0 : req.getSort())
                .sources(String.join(",", sources)).countryOp(op).countries(op == null ? "" : String.join(",", countries))
                .assigneeIds(assignees).build();
        if (req.getId() == null) {
            r.setNextIndex(0);
            channelRoutingRuleMapper.insert(r);
        } else {
            r.setId(req.getId());
            channelRoutingRuleMapper.updateById(r);
        }
        return r.getId();
    }

    public void delete(Long id) {
        channelRoutingRuleMapper.deleteById(id);
    }

    public void setEnabled(Long id, boolean enabled) {
        channelRoutingRuleMapper.updateById(new ChannelRoutingRuleDO().setId(id).setEnabled(enabled));
    }

    /**
     * 新询盘的负责人：第一条命中的启用规则里轮到的业务员；没有命中返回 null（沿用渠道账号所属业务员）。
     *
     * @param source      whatsapp / messenger / instagram / mail / platform
     * @param countryCode 客户国家二字码，可能为空
     */
    public synchronized Long pickOwner(String source, String countryCode) {
        String cc = StrUtil.nullToEmpty(countryCode).toUpperCase(Locale.ROOT);
        for (ChannelRoutingRuleDO r : channelRoutingRuleMapper.selectOrdered()) {
            if (!Boolean.TRUE.equals(r.getEnabled()) || r.getAssigneeIds() == null || r.getAssigneeIds().isEmpty()) {
                continue;
            }
            List<String> sources = StrUtil.split(StrUtil.nullToEmpty(r.getSources()), ',', true, true);
            if (!sources.isEmpty() && !sources.contains(source)) {
                continue;
            }
            if (r.getCountryOp() != null && !r.getCountryOp().isEmpty()) {
                List<String> countries = StrUtil.split(StrUtil.nullToEmpty(r.getCountries()), ',', true, true);
                boolean in = !cc.isEmpty() && countries.contains(cc);
                if ("in".equals(r.getCountryOp()) != in) {
                    continue; // 属于：国家必须在列表里；不属于：国家不在列表里（国家未知时不算属于）
                }
            }
            int idx = Math.floorMod(r.getNextIndex() == null ? 0 : r.getNextIndex(), r.getAssigneeIds().size());
            channelRoutingRuleMapper.updateById(new ChannelRoutingRuleDO().setId(r.getId()).setNextIndex(idx + 1));
            Long owner = r.getAssigneeIds().get(idx);
            log.info("[pickOwner][{} {} 命中分流规则「{}」，分配给用户 {}]", source, cc, r.getName(), owner);
            return owner;
        }
        return null;
    }

}
