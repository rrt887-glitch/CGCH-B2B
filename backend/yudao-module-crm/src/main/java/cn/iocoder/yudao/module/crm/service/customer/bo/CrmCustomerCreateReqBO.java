package cn.iocoder.yudao.module.crm.service.customer.bo;

import cn.iocoder.yudao.framework.common.validation.Mobile;
import cn.iocoder.yudao.framework.common.validation.Telephone;
import cn.iocoder.yudao.module.crm.enums.DictTypeConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户创建 Create Req BO
 *
 * @author HUIHUI
 */
@Data
public class CrmCustomerCreateReqBO {

    /**
     * 客户名称
     */
    @NotEmpty(message = "客户名称不能为空")
    private String name;
    /**
     * 跟进状态
     */
    private Boolean followUpStatus;
    /**
     * 锁定状态
     */
    private Boolean lockStatus;
    /**
     * 成交状态
     */
    private Boolean dealStatus;
    /**
     * 所属行业
     *
     * 对应字典 {@link DictTypeConstants#CRM_CUSTOMER_INDUSTRY}
     */
    private Integer industryId;
    /**
     * 客户等级
     *
     * 对应字典 {@link DictTypeConstants#CRM_CUSTOMER_LEVEL}
     */
    private Integer level;
    /**
     * 客户来源
     *
     * 对应字典 {@link DictTypeConstants#CRM_CUSTOMER_SOURCE}
     */
    private Integer source;

    /**
     * 手机
     */
    @Mobile
    private String mobile;
    /**
     * 电话
     */
    @Telephone
    private String telephone;
    /**
     * QQ
     */
    private String qq;
    /**
     * wechat
     */
    private String wechat;

    /**
     * 邮箱
     */
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 客户描述
     */
    @Size(max = 4096, message = "客户描述长度不能超过 4096 个字符")
    private String description;
    /**
     * 备注
     */
    private String remark;
    /**
     * 负责人的用户编号
     *
     * 关联 AdminUserDO 的 id 字段
     */
    private Long ownerUserId;
    /**
     * 所在地
     *
     * 关联 {@link cn.iocoder.yudao.framework.ip.core.Area#getId()} 字段
     */
    private Integer areaId;
    /**
     * 详细地址
     */
    private String detailAddress;

    /**
     * 最后跟进时间
     */
    private LocalDateTime contactLastTime;
    /**
     * 最后跟进内容
     */
    private String contactLastContent;
    /**
     * 下次联系时间
     */
    private LocalDateTime contactNextTime;


    // ========== 外贸字段：线索转客户时一并带过去 ==========

    /** 国家（ISO 二字码） */
    private String countryCode;
    /** 公司名称 */
    private String companyName;
    /** 官网 */
    private String website;
    /** WhatsApp 号码 */
    private String whatsapp;
    /** 客户分组 */
    private Long groupId;
    /** 标签 */
    private java.util.List<Long> tagIds;
    /** 是否由 AI 托管 */
    private Boolean aiHosted;

}
