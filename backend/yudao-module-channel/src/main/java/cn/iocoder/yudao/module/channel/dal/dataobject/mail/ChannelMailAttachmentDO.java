package cn.iocoder.yudao.module.channel.dal.dataobject.mail;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 邮件附件。收到的邮件附件在同步时保存；发信时先上传（messageId 为空），发送成功后挂到已发送邮件上。
 */
@TableName("channel_mail_attachment")
@KeySequence("channel_mail_attachment_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelMailAttachmentDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 所属邮件编号；待发送的附件为空
     */
    private Long messageId;
    /**
     * 上传者用户编号（待发送附件的归属校验）
     */
    private Long userId;
    /**
     * 文件名
     */
    private String fileName;
    /**
     * MIME 类型
     */
    private String contentType;
    /**
     * 文件大小，字节
     */
    private Long fileSize;
    /**
     * 文件访问地址
     */
    private String fileUrl;
    /**
     * 内嵌图片的 Content-ID（正文中以 cid: 引用），普通附件为空
     */
    private String contentId;
    /**
     * 是否正文内嵌图片
     */
    private Boolean inlined;

}
