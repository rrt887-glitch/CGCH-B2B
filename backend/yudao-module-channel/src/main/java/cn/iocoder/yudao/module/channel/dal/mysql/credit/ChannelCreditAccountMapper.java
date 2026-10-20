package cn.iocoder.yudao.module.channel.dal.mysql.credit;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.credit.ChannelCreditAccountDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChannelCreditAccountMapper extends BaseMapperX<ChannelCreditAccountDO> {

    default ChannelCreditAccountDO selectCurrent() {
        List<ChannelCreditAccountDO> list = selectList();
        return list.isEmpty() ? null : list.get(0);
    }

    /** 原子增减余额：delta 为正表示增加 */
    default void addBalance(Long id, long delta) {
        LambdaUpdateWrapper<ChannelCreditAccountDO> w = new LambdaUpdateWrapper<ChannelCreditAccountDO>()
                .eq(ChannelCreditAccountDO::getId, id)
                .setSql("balance = balance + " + delta)
                .setSql(delta >= 0 ? "total_in = total_in + " + delta : "total_out = total_out + " + (-delta));
        update(null, w);
    }

}
