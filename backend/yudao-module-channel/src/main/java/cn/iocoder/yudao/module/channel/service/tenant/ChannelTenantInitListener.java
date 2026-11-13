package cn.iocoder.yudao.module.channel.service.tenant;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.crm.dal.dataobject.business.CrmBusinessStatusDO;
import cn.iocoder.yudao.module.crm.dal.dataobject.business.CrmBusinessStatusTypeDO;
import cn.iocoder.yudao.module.crm.dal.dataobject.customer.CrmCustomerPoolConfigDO;
import cn.iocoder.yudao.module.crm.dal.mysql.business.CrmBusinessStatusMapper;
import cn.iocoder.yudao.module.crm.dal.mysql.business.CrmBusinessStatusTypeMapper;
import cn.iocoder.yudao.module.crm.dal.mysql.customer.CrmCustomerPoolConfigMapper;
import cn.iocoder.yudao.module.system.api.tenant.TenantCreatedEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 新开一家客户时，准备与平台默认相同的 CRM 初始数据（与 02-crm-seed.sql 给平台准备的一致）：
 * 外贸标准商机流程（8 个阶段）、公海规则（30 天未跟进或 90 天未成交回收，提前 3 天提醒）。
 * 在开户的同一事务里执行，失败会让开户整体回滚。
 */
@Component
@Slf4j
public class ChannelTenantInitListener {

    /** 外贸标准商机流程：阶段名称与赢单概率（%） */
    private static final Object[][] STAGES = {
            {"初步回复", 5}, {"背调", 10}, {"需求挖掘", 20}, {"报价", 35},
            {"样品", 50}, {"合同签订", 70}, {"订单执行", 90}, {"赢单", 100},
    };

    @Resource
    private CrmBusinessStatusTypeMapper businessStatusTypeMapper;
    @Resource
    private CrmBusinessStatusMapper businessStatusMapper;
    @Resource
    private CrmCustomerPoolConfigMapper customerPoolConfigMapper;

    @EventListener
    public void onTenantCreated(TenantCreatedEvent event) {
        TenantUtils.execute(event.tenantId(), () -> {
            String creator = String.valueOf(event.adminUserId());
            CrmBusinessStatusTypeDO type = CrmBusinessStatusTypeDO.builder().name("外贸标准流程").build();
            type.setCreator(creator);
            type.setUpdater(creator);
            businessStatusTypeMapper.insert(type);
            for (int i = 0; i < STAGES.length; i++) {
                CrmBusinessStatusDO s = CrmBusinessStatusDO.builder().typeId(type.getId())
                        .name((String) STAGES[i][0]).percent((Integer) STAGES[i][1]).sort(i + 1).build();
                s.setCreator(creator);
                s.setUpdater(creator);
                businessStatusMapper.insert(s);
            }
            if (customerPoolConfigMapper.selectCount() == 0) {
                CrmCustomerPoolConfigDO pool = CrmCustomerPoolConfigDO.builder().enabled(true)
                        .contactExpireDays(30).dealExpireDays(90).notifyEnabled(true).notifyDays(3).build();
                pool.setCreator(creator);
                pool.setUpdater(creator);
                customerPoolConfigMapper.insert(pool);
            }
        });
        log.info("[onTenantCreated][客户 {} 的 CRM 初始数据已准备：外贸标准商机流程、公海规则]", event.tenantId());
    }

}
