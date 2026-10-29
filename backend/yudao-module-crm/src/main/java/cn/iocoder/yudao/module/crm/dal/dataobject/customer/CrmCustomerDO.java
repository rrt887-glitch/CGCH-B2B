package cn.iocoder.yudao.module.crm.dal.dataobject.customer;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import java.util.List;
import cn.iocoder.yudao.framework.mybatis.core.type.LongListTypeHandler;
import com.baomidou.mybatisplus.annotation.TableField;
import cn.iocoder.yudao.module.crm.enums.DictTypeConstants;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * CRM 客户 DO
 *
 * @author Wanwan
 */
@TableName(value = "crm_customer", autoResultMap = true)
@KeySequence("crm_customer_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmCustomerDO extends BaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 客户名称
     */
    private String name;

    /**
     * 跟进状态
     */
    private Boolean followUpStatus;
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

    /**
     * 负责人的用户编号
     *
     * 关联 AdminUserDO 的 id 字段
     */
    private Long ownerUserId;
    /**
     * 成为负责人的时间
     */
    private LocalDateTime ownerTime;

    /**
     * 锁定状态
     */
    private Boolean lockStatus;
    /**
     * 成交状态
     */
    private Boolean dealStatus;

    /**
     * 手机
     */
    private String mobile;
    /**
     * 电话
     */
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
     * email
     */
    private String email;
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
     * 备注
     */
    private String remark;


    // ========== CGCH 外贸字段 ==========

    /**
     * 国家/地区，ISO 3166-1 二字码，例如 US、DE
     */
    private String countryCode;
    /**
     * 公司名称
     */
    private String companyName;
    /**
     * 公司官网
     */
    private String website;
    /**
     * WhatsApp 号码，含国际区号
     */
    private String whatsapp;
    /**
     * 客户分组编号，关联 crm_customer_group
     */
    private Long groupId;
    /**
     * 标签编号列表，关联 crm_tag
     */
    @TableField(typeHandler = LongListTypeHandler.class)
    private List<Long> tagIds;
    /**
     * 是否由 AI 数字员工托管接待
     */
    private Boolean aiHosted;
    /**
     * 是否特别关注
     */
    private Boolean starred;
    /**
     * 客户阶段，对应字典 crm_customer_stage（新询盘、报价、样品、谈判、成交、复购，可在字典里调整）
     */
    private Integer stage;

}
