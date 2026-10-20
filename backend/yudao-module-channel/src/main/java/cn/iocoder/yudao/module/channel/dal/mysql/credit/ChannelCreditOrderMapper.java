package cn.iocoder.yudao.module.channel.dal.mysql.credit;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.credit.ChannelCreditOrderDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChannelCreditOrderMapper extends BaseMapperX<ChannelCreditOrderDO> {

    default PageResult<ChannelCreditOrderDO> selectPage(PageParam req) {
        return selectPage(req, new LambdaQueryWrapperX<ChannelCreditOrderDO>().orderByDesc(ChannelCreditOrderDO::getId));
    }

    /** 仅当订单仍为未支付时更新，防止重复到账 */
    default int updateByIdAndUnpaid(Long id, ChannelCreditOrderDO update) {
        return update(update, new LambdaUpdateWrapper<ChannelCreditOrderDO>()
                .eq(ChannelCreditOrderDO::getId, id).eq(ChannelCreditOrderDO::getPayStatus, false));
    }

}
