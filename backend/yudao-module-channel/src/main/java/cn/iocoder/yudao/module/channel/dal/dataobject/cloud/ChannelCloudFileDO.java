package cn.iocoder.yudao.module.channel.dal.dataobject.cloud;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 云盘文件：企业云盘（全员可见，AI 可发给客户）与个人云盘（只有本人可见），支持文件夹
 */
@TableName("channel_cloud_file")
@KeySequence("channel_cloud_file_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelCloudFileDO extends TenantBaseDO {

    public static final String SCOPE_ENTERPRISE = "enterprise";
    public static final String SCOPE_PERSONAL = "personal";

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 所属云盘：enterprise 企业云盘、personal 个人云盘
     */
    private String scope;
    /**
     * 个人云盘的所属用户
     */
    private Long ownerUserId;
    /**
     * 上级文件夹编号，根目录为 0
     */
    private Long parentId;
    /**
     * 是否为文件夹
     */
    private Boolean folder;
    /**
     * 名称
     */
    private String name;
    /**
     * 描述（AI 据此判断什么时候发送这个文件）
     */
    private String description;
    /**
     * 文件访问地址
     */
    private String url;
    /**
     * 文件大小（字节）
     */
    private Long size;
    /**
     * 文件类型
     */
    private String mimeType;
    /**
     * 是否允许 AI 发给客户（仅企业云盘的文件）
     */
    private Boolean aiSendable;

}
