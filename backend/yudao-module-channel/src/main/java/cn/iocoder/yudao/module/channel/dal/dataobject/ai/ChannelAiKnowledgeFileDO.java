package cn.iocoder.yudao.module.channel.dal.dataobject.ai;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 知识库资料文件：上传后解析成文字、按段落拆成知识条目供 AI 检索
 */
@TableName("channel_ai_knowledge_file")
@KeySequence("channel_ai_knowledge_file_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelAiKnowledgeFileDO extends TenantBaseDO {

    public static final String STATUS_LEARNING = "learning";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_FAILED = "failed";

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 文件名
     */
    private String name;
    /**
     * 文件访问地址
     */
    private String url;
    /**
     * 文件大小（字节）
     */
    private Long size;
    /**
     * 文件类型（扩展名，例如 pdf、docx）
     */
    private String fileType;
    /**
     * 学习进度：learning 学习中、done 学习完成、failed 学习失败
     */
    private String status;
    /**
     * 拆出的知识条目数
     */
    private Integer chunks;
    /**
     * 学习失败的原因
     */
    private String errorMsg;

}
