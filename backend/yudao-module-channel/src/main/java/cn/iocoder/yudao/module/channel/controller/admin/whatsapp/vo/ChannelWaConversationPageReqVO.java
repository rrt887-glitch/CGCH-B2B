package cn.iocoder.yudao.module.channel.controller.admin.whatsapp.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ChannelWaConversationPageReqVO extends PageParam {

    /** 业务号码编号，不填表示我的全部号码 */
    private Long accountId;
    /** 号码或昵称 */
    private String keyword;
    /** 只看有未读的 */
    private Boolean unreadOnly;

}
