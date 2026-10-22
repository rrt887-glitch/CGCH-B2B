package cn.iocoder.yudao.module.channel.framework.oauth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 对外公开页面（首页介绍、隐私政策、服务条款、数据删除说明）里展示的运营主体信息。
 * Meta、微软、Google 审核授权应用时都会检查这些页面，所以必须填写真实的公司信息。
 */
@Configuration
@ConfigurationProperties(prefix = "cgch.site")
@Data
public class ChannelSiteProperties {

    /** 产品名称 */
    private String productName = "CGCH";
    /** 运营主体（公司全称），例如「深圳市某某科技有限公司」 */
    private String companyName = "";
    /** 公司英文名，英文版页面使用；不填则用中文名 */
    private String companyNameEn = "";
    /** 联系邮箱：用户咨询隐私问题、申请删除数据 */
    private String contactEmail = "";
    /** 公司地址 */
    private String address = "";
    /** 政策生效日期 */
    private String effectiveDate = "2026-10-22";

}
