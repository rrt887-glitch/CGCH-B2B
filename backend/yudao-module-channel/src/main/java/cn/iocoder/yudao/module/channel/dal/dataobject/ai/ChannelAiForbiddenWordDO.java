package cn.iocoder.yudao.module.channel.dal.dataobject.ai;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 违禁词：AI 回复中出现时不会自动发出，转为草稿等人工确认
 */
@TableName("channel_ai_forbidden_word")
@KeySequence("channel_ai_forbidden_word_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelAiForbiddenWordDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 词语，不区分大小写
     */
    private String word;
    /**
     * 说明，例如「不能承诺质量保证」
     */
    private String remark;
    /**
     * 是否启用
     */
    private Boolean enabled;

}
