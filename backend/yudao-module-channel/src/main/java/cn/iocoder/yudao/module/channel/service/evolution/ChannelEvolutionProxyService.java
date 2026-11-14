package cn.iocoder.yudao.module.channel.service.evolution;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.channel.controller.admin.evolution.vo.ChannelEvolutionProxyRespVO;
import cn.iocoder.yudao.module.channel.controller.admin.evolution.vo.ChannelEvolutionProxySaveReqVO;
import cn.iocoder.yudao.module.channel.dal.dataobject.evolution.ChannelEvolutionProxyDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import cn.iocoder.yudao.module.channel.dal.mysql.evolution.ChannelEvolutionProxyMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaAccountMapper;
import cn.iocoder.yudao.module.channel.framework.oauth.ChannelOAuthProperties;
import cn.iocoder.yudao.module.channel.service.whatsapp.WaGraphClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.AI_LLM_FAIL;

/**
 * WhatsApp 扫码接入的出口代理：每家客户一个出口 IP，一家群发被风控时不牵连同服务器的其他客户。
 *
 * 代理是「平台级」配置（代理池由平台维护），所以读写都按租户编号定位、由平台运营在「客户管理」里操作；
 * 客户公司的账号没有这个权限。
 */
@Service
@Slf4j
public class ChannelEvolutionProxyService {

    @Resource
    private ChannelEvolutionProxyMapper proxyMapper;
    @Resource
    private ChannelWaAccountMapper accountMapper;
    @Resource
    private ChannelOAuthProperties channelOAuthProperties;
    @Resource
    private cn.iocoder.yudao.module.system.service.tenant.TenantService tenantService;

    private ChannelOAuthProperties.Evolution cfg() {
        return channelOAuthProperties.getEvolution();
    }

    /** 当前公司（租户上下文）的代理配置，没有配置或未启用时返回 null */
    public ChannelEvolutionProxyDO currentEnabled() {
        ChannelEvolutionProxyDO p = proxyMapper.selectCurrent();
        return p != null && Boolean.TRUE.equals(p.getEnabled()) ? p : null;
    }

    /** 取指定客户的代理配置（平台用） */
    public ChannelEvolutionProxyRespVO getProxy(Long tenantId) {
        if (tenantService.getTenant(tenantId) == null) {
            throw exception(AI_LLM_FAIL, "客户不存在");
        }
        return TenantUtils.execute(tenantId, () -> {
            ChannelEvolutionProxyDO p = proxyMapper.selectCurrent();
            ChannelEvolutionProxyRespVO vo = p == null ? new ChannelEvolutionProxyRespVO()
                    : BeanUtils.toBean(p, ChannelEvolutionProxyRespVO.class);
            vo.setTenantId(tenantId);
            vo.setHasPassword(p != null && StrUtil.isNotBlank(p.getPassword()));
            return vo;
        });
    }

    /**
     * 保存代理配置；applyNow 为 true 时立即设置到这家客户已有的号码上。
     * 返回设置失败的号码说明（成功时为空列表）。
     */
    public List<String> saveProxy(ChannelEvolutionProxySaveReqVO req) {
        Long tenantId = req.getTenantId() != null ? req.getTenantId() : TenantContextHolder.getRequiredTenantId();
        if (tenantService.getTenant(tenantId) == null) {
            throw exception(AI_LLM_FAIL, "客户不存在");
        }
        boolean enabled = Boolean.TRUE.equals(req.getEnabled());
        if (enabled) {
            if (StrUtil.isBlank(req.getHost())) {
                throw exception(AI_LLM_FAIL, "请填写代理主机");
            }
            if (req.getPort() == null) {
                throw exception(AI_LLM_FAIL, "请填写代理端口");
            }
            if (StrUtil.isBlank(req.getProtocol())) {
                throw exception(AI_LLM_FAIL, "请选择代理协议");
            }
        }
        return TenantUtils.execute(tenantId, () -> {
            ChannelEvolutionProxyDO old = proxyMapper.selectCurrent();
            ChannelEvolutionProxyDO save = ChannelEvolutionProxyDO.builder()
                    .enabled(enabled)
                    .host(StrUtil.trim(req.getHost()))
                    .port(req.getPort())
                    .protocol(StrUtil.trim(req.getProtocol()))
                    .username(StrUtil.trim(req.getUsername()))
                    .build();
            // 密码：留空表示沿用原密码；clearPassword 表示换成不需要密码的代理
            if (Boolean.TRUE.equals(req.getClearPassword())) {
                save.setPassword("");
            } else if (StrUtil.isNotBlank(req.getPassword())) {
                save.setPassword(req.getPassword());
            } else if (old != null) {
                save.setPassword(old.getPassword());
            }
            if (old == null) {
                proxyMapper.insert(save);
            } else {
                save.setId(old.getId());
                proxyMapper.updateById(save);
            }
            if (!Boolean.TRUE.equals(req.getApplyNow())) {
                return new ArrayList<String>();
            }
            return applyToTenant(tenantId);
        });
    }

    /** 把这套代理设置到该客户全部扫码接入的号码上，返回失败的号码说明 */
    public List<String> applyToTenant(Long tenantId) {
        List<String> errors = TenantUtils.execute(tenantId, () -> {
            ChannelEvolutionProxyDO p = proxyMapper.selectCurrent();
            EvolutionClient.ProxyConfig config = toConfig(p);
            List<String> failed = new ArrayList<>();
            for (ChannelWaAccountDO a : evolutionAccounts()) {
                try {
                    EvolutionClient.setProxy(cfg(), a.getPhoneNumberId(), config);
                } catch (WaGraphClient.WaApiException e) {
                    failed.add(StrUtil.blankToDefault(a.getDisplayPhone(), a.getName()) + "：" + e.getMessage());
                    log.warn("[applyToTenant][号码 {} 设置代理失败] {}", a.getDisplayPhone(), e.getMessage());
                }
            }
            return failed;
        });
        markApplied(tenantId, errors);
        return errors;
    }

    /** 客户自己看到的代理状态：只告诉有没有配、从哪个主机出去，不暴露账号密码 */
    public Map<String, Object> myProxySummary() {
        ChannelEvolutionProxyDO p = proxyMapper.selectCurrent();
        if (p == null || !Boolean.TRUE.equals(p.getEnabled())) {
            return Map.<String, Object>of("enabled", false);
        }
        return Map.<String, Object>of("enabled", true, "host", StrUtil.blankToDefault(p.getHost(), ""),
                "protocol", StrUtil.blankToDefault(p.getProtocol(), ""));
    }

    /** 扫码建实例、还没扫码确认时设置代理；没有配置代理时什么都不做 */
    public void applyToNewInstance(String instanceName) {
        ChannelEvolutionProxyDO p = currentEnabled();
        if (p == null) {
            return;
        }
        try {
            EvolutionClient.setProxy(cfg(), instanceName, toConfig(p));
        } catch (WaGraphClient.WaApiException e) {
            throw new WaGraphClient.WaApiException("设置出口代理失败（" + p.getHost() + "）：" + e.getMessage());
        }
    }

    private List<ChannelWaAccountDO> evolutionAccounts() {
        return accountMapper.selectList().stream().filter(a -> a.isEvolution() && StrUtil.isNotBlank(a.getPhoneNumberId())).toList();
    }

    private static EvolutionClient.ProxyConfig toConfig(ChannelEvolutionProxyDO p) {
        if (p == null || !Boolean.TRUE.equals(p.getEnabled())) {
            return new EvolutionClient.ProxyConfig(false, null, null, null, null, null);
        }
        return new EvolutionClient.ProxyConfig(true, p.getHost(), p.getPort(), p.getProtocol(), p.getUsername(), p.getPassword());
    }

    private void markApplied(Long tenantId, List<String> errors) {
        TenantUtils.execute(tenantId, () -> {
            ChannelEvolutionProxyDO p = proxyMapper.selectCurrent();
            if (p == null) {
                return;
            }
            ChannelEvolutionProxyDO update = new ChannelEvolutionProxyDO().setId(p.getId()).setLastAppliedTime(LocalDateTime.now());
            update.setLastError(errors.isEmpty() ? "" : StrUtil.maxLength(String.join("；", errors), 500));
            proxyMapper.updateById(update);
        });
    }

}
