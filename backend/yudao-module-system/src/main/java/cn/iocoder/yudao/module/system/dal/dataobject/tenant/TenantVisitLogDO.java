package cn.iocoder.yudao.module.system.dal.dataobject.tenant;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 平台运营「进入客户后台」记录。平台级数据，不属于任何一家客户（不按租户隔离）；
 * 客户管理员可以查看进入过自己公司的记录。
 */
@TableName("system_tenant_visit_log")
@KeySequence("system_tenant_visit_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TenantIgnore
public class TenantVisitLogDO extends BaseDO {

    @TableId
    private Long id;
    /** 进入的人（平台账号） */
    private Long userId;
    private String username;
    private String nickname;
    /** 进入的客户（租户） */
    private Long visitTenantId;
    private String visitTenantName;
    /** 进入原因（必填，例如「客户反馈 WhatsApp 收不到消息」） */
    private String reason;
    private String ip;
    private String userAgent;
    private LocalDateTime enterTime;
    /** 本次进入的有效期，到期需要重新进入 */
    private LocalDateTime expireTime;
    /** 主动退出的时间；为空表示到期自动结束或仍在有效期内 */
    private LocalDateTime exitTime;

}
