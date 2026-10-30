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
    /** 归档：不填或 false 看未归档的，true 只看已归档的 */
    private Boolean archived;
    /** 只看收藏的 */
    private Boolean starred;
    /** 只看客户最后发言、我方还没回复的 */
    private Boolean unreplied;
    /** 客户等级（crm_customer_level 字典值：1 A 类 … 4 D 类） */
    private Integer level;

    @Schema(description = "团队会话：看数据权限范围内其他同事负责的会话（主管用）", example = "true")
    private Boolean team;

}
