package cn.iocoder.yudao.module.crm.service.user;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.crm.dal.dataobject.business.CrmBusinessDO;
import cn.iocoder.yudao.module.crm.dal.dataobject.clue.CrmClueDO;
import cn.iocoder.yudao.module.crm.dal.dataobject.contract.CrmContractDO;
import cn.iocoder.yudao.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.yudao.module.crm.dal.dataobject.permission.CrmPermissionDO;
import cn.iocoder.yudao.module.crm.dal.dataobject.receivable.CrmReceivableDO;
import cn.iocoder.yudao.module.crm.dal.mysql.business.CrmBusinessMapper;
import cn.iocoder.yudao.module.crm.dal.mysql.clue.CrmClueMapper;
import cn.iocoder.yudao.module.crm.dal.mysql.contract.CrmContractMapper;
import cn.iocoder.yudao.module.crm.dal.mysql.customer.CrmCustomerMapper;
import cn.iocoder.yudao.module.crm.dal.mysql.permission.CrmPermissionMapper;
import cn.iocoder.yudao.module.crm.dal.mysql.receivable.CrmReceivableMapper;
import cn.iocoder.yudao.module.system.api.user.event.AdminUserDeletedEvent;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 账号被删后，把他名下的客户 / 线索 / 商机 / 合同 / 回款交给公司管理员，
 * 并清掉他的协作权限，避免「负责人已不存在」。
 */
@Slf4j
@Component
public class CrmUserDeletedListener {

    @Resource
    private CrmCustomerMapper customerMapper;
    @Resource
    private CrmClueMapper clueMapper;
    @Resource
    private CrmBusinessMapper businessMapper;
    @Resource
    private CrmContractMapper contractMapper;
    @Resource
    private CrmReceivableMapper receivableMapper;
    @Resource
    private CrmPermissionMapper permissionMapper;

    @EventListener
    public void onUserDeleted(AdminUserDeletedEvent event) {
        try {
            TenantUtils.execute(event.getTenantId(), () -> {
                Long userId = event.getUserId();
                Long newOwner = event.getNewOwnerUserId();
                int customer = customerMapper.update(null, new LambdaUpdateWrapper<CrmCustomerDO>()
                        .eq(CrmCustomerDO::getOwnerUserId, userId).set(CrmCustomerDO::getOwnerUserId, newOwner));
                int clue = clueMapper.update(null, new LambdaUpdateWrapper<CrmClueDO>()
                        .eq(CrmClueDO::getOwnerUserId, userId).set(CrmClueDO::getOwnerUserId, newOwner));
                int business = businessMapper.update(null, new LambdaUpdateWrapper<CrmBusinessDO>()
                        .eq(CrmBusinessDO::getOwnerUserId, userId).set(CrmBusinessDO::getOwnerUserId, newOwner));
                int contract = contractMapper.update(null, new LambdaUpdateWrapper<CrmContractDO>()
                        .eq(CrmContractDO::getOwnerUserId, userId).set(CrmContractDO::getOwnerUserId, newOwner));
                int receivable = receivableMapper.update(null, new LambdaUpdateWrapper<CrmReceivableDO>()
                        .eq(CrmReceivableDO::getOwnerUserId, userId).set(CrmReceivableDO::getOwnerUserId, newOwner));
                // 协作权限：这个人已经不存在了，清掉
                permissionMapper.delete(new LambdaQueryWrapper<CrmPermissionDO>().eq(CrmPermissionDO::getUserId, userId));
                log.info("[onUserDeleted][公司({}) 账号({}) 的客户 {}、线索 {}、商机 {}、合同 {}、回款 {} 转给 ({})]",
                        event.getTenantId(), userId, customer, clue, business, contract, receivable, newOwner);
            });
        } catch (Exception ex) {
            log.warn("[onUserDeleted][账号({}) CRM 数据交接失败] {}", event.getUserId(), ex.getMessage());
        }
    }

}
