package cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp;

import cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * WhatsApp Business 号码（Meta 官方 Cloud API）
 */
@TableName(value = "channel_wa_account", autoResultMap = true)
@KeySequence("channel_wa_account_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = {"accessToken", "appSecret"})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelWaAccountDO extends TenantBaseDO {

    /** 平台：WhatsApp Cloud API、Facebook 主页私信（Messenger）、Instagram 私信 */
    public static final String PLATFORM_WHATSAPP = "whatsapp";
    public static final String PLATFORM_MESSENGER = "messenger";
    public static final String PLATFORM_INSTAGRAM = "instagram";
    /** 官网 IM：网站上嵌入的在线聊天窗口。phoneNumberId 存站点密钥（嵌入代码里的 k），displayPhone 存绑定域名 */
    public static final String PLATFORM_WEB = "web";
    /** 接入方式：手动填写令牌，或通过 Facebook 登录授权 */
    public static final String AUTH_MANUAL = "manual";
    public static final String AUTH_OAUTH = "oauth";
    /**
     * 扫码接入（Evolution API，WhatsApp 网页版协议）：phoneNumberId 存 Evolution 实例名，accessToken 存实例令牌，
     * appSecret 存推送校验令牌，displayPhone 存 E.164 号码，connState 存连接状态
     */
    public static final String AUTH_EVOLUTION = "evolution";
    /** 扫码接入的连接状态：已连接、连接中、已断开（手机上退出了关联设备，需要重新扫码） */
    public static final String CONN_OPEN = "open";
    public static final String CONN_CONNECTING = "connecting";
    public static final String CONN_CLOSE = "close";

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 负责该号码的用户编号
     */
    private Long userId;
    /**
     * 显示名称，例如「销售一部 WhatsApp」
     */
    private String name;
    /**
     * 平台：whatsapp（默认）、messenger、instagram
     */
    private String platform;
    /**
     * 接入方式：manual 手动填写令牌（默认）、oauth 通过 Facebook 登录授权
     */
    private String authType;
    /**
     * 通过 Facebook 登录授权接入时，授权人的 Facebook 用户编号（应用范围内的编号）；
     * 用户在 Facebook 里移除应用或申请删除数据时，据此找到并删除他接入的账号
     */
    private String oauthUserId;
    /**
     * 平台账号编号：WhatsApp 为 Phone number ID，Messenger 为主页编号（Page ID），Instagram 为专业账号编号
     */
    private String phoneNumberId;
    /**
     * WhatsApp Business Account ID，用于查询消息模板
     */
    private String wabaId;
    /**
     * 展示名称：WhatsApp 为号码（如 +86 138 0000 0000），Messenger 为主页名称，Instagram 为 @用户名
     */
    private String displayPhone;
    /**
     * 访问令牌（建议使用系统用户的永久令牌），加密存储
     */
    @TableField(typeHandler = EncryptTypeHandler.class)
    private String accessToken;
    /**
     * Meta 应用密钥，用于校验 Webhook 签名，加密存储
     */
    @TableField(typeHandler = EncryptTypeHandler.class)
    private String appSecret;
    /**
     * Webhook 验证令牌，在 Meta 后台配置回调地址时填写
     */
    private String verifyToken;
    /**
     * Graph API 地址，默认 https://graph.facebook.com
     */
    private String apiBase;
    /**
     * Graph API 版本，例如 v24.0
     */
    private String apiVersion;
    /**
     * 是否启用
     */
    private Boolean enabled;
    /**
     * 最近一次收到 Webhook 的时间
     */
    private LocalDateTime lastWebhookTime;
    /**
     * 扫码接入的连接状态：open、connecting、close；其他接入方式为空
     */
    private String connState;
    /**
     * 接待成员：这个号码的新客户分给哪些业务员（用户编号，逗号分隔，按顺序轮流分配）；为空表示都由号码所属业务员处理
     */
    private String assignUserIds;
    /**
     * 轮流分配的游标：下一个新客户分给接待成员里的第几个
     */
    private Integer assignCursor;
    /**
     * 扫码接入：最近一次同步聊天记录的时间、Evolution 里存的记录数、新导入数、因为是隐藏号码（@lid）没法导入的条数、失败原因
     */
    private java.time.LocalDateTime historySyncTime;
    private Integer historyFetched;
    private Integer historyImported;
    private Integer historyNoNumber;
    private String historySyncError;

    public boolean isEvolution() {
        return AUTH_EVOLUTION.equals(authType);
    }

    public boolean isWeb() {
        return PLATFORM_WEB.equals(platform);
    }

    public boolean isWhatsApp() {
        return platform == null || PLATFORM_WHATSAPP.equals(platform);
    }

}
