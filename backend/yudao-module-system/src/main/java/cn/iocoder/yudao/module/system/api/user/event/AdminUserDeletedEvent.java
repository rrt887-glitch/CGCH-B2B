package cn.iocoder.yudao.module.system.api.user.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 账号被删除的事件。
 *
 * 删掉一个成员后，他名下的会话、渠道账号、CRM 客户/线索/商机/合同/回款 都要有人接手，
 * 否则会变成「负责人已不存在」的孤儿数据（列表筛不出来、通知发不出去）。
 * 由 channel / crm 模块各自监听处理，按公司管理员接手。
 */
@Getter
public class AdminUserDeletedEvent extends ApplicationEvent {

    /** 被删除的账号 */
    private final Long userId;

    /** 该账号所属公司 */
    private final Long tenantId;

    /** 接手人（公司管理员）；没有可用接手人时为 null，表示置空为「未分配」 */
    private final Long newOwnerUserId;

    public AdminUserDeletedEvent(Object source, Long userId, Long tenantId, Long newOwnerUserId) {
        super(source);
        this.userId = userId;
        this.tenantId = tenantId;
        this.newOwnerUserId = newOwnerUserId;
    }

}
