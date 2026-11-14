package cn.iocoder.yudao.module.channel.dal.dataobject.evolution;

import cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * WhatsApp 扫码接入的出口代理：每家客户一条。
 *
 * 所有客户的号码都跑在同一台 Evolution 上，如果不配代理，消息都从平台服务器同一个 IP 出去；
 * 一家客户群发被风控，同一个 IP 上的新号码更难连上。配了代理后，Evolution 用该客户自己的 IP 连 WhatsApp，
 * 互相不牵连。密码加密存储。
 */
@TableName("channel_evolution_proxy")
@KeySequence("channel_evolution_proxy_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelEvolutionProxyDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 是否启用；关闭时直接从服务器 IP 出去
     */
    private Boolean enabled;
    /**
     * 代理主机，例如 proxy.example.com 或 1.2.3.4
     */
    private String host;
    private Integer port;
    /**
     * 协议：http、https、socks4、socks5
     */
    private String protocol;
    private String username;
    /**
     * 密码，加密存储
     */
    @TableField(typeHandler = EncryptTypeHandler.class)
    private String password;
    /**
     * 最近一次把这套代理设置到号码上的时间
     */
    private LocalDateTime lastAppliedTime;
    /**
     * 最近一次设置失败的原因（成功时清空）
     */
    private String lastError;

}
