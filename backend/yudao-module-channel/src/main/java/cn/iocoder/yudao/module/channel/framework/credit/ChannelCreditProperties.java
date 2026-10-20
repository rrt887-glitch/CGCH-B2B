package cn.iocoder.yudao.module.channel.framework.credit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 智点充值套餐，可在 application.yaml 的 cgch.credit.packages 覆盖。
 */
@Configuration
@ConfigurationProperties(prefix = "cgch.credit")
@Data
public class ChannelCreditProperties {

    /** 在线充值使用的支付应用标识（支付管理 → 应用信息） */
    private String payAppKey = "cgch-credit";

    private List<CreditPackage> packages = defaults();

    @Data
    public static class CreditPackage {
        private Long id;
        private String name;
        /** 到账智点（含赠送） */
        private Long credits;
        /** 价格，单位：分 */
        private Integer price;
        private String description;

        static CreditPackage of(long id, String name, long credits, int price, String description) {
            CreditPackage p = new CreditPackage();
            p.setId(id); p.setName(name); p.setCredits(credits); p.setPrice(price); p.setDescription(description);
            return p;
        }
    }

    private static List<CreditPackage> defaults() {
        List<CreditPackage> list = new ArrayList<>();
        list.add(CreditPackage.of(1, "入门包", 1_000, 9_900, "约可处理 300 封询盘回复"));
        list.add(CreditPackage.of(2, "标准包", 5_500, 39_900, "含赠送 500 智点"));
        list.add(CreditPackage.of(3, "专业包", 24_000, 129_900, "含赠送 4000 智点，适合多业务员团队"));
        return list;
    }

    public CreditPackage findPackage(Long id) {
        return packages.stream().filter(p -> p.getId().equals(id)).findFirst().orElse(null);
    }

}
