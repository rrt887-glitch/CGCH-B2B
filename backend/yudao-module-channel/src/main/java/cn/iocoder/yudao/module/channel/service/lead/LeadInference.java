package cn.iocoder.yudao.module.channel.service.lead;

import cn.hutool.core.util.StrUtil;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 不依赖 AI 的信息推断：电话区号 → 国家、邮箱域名 → 官网与国家。
 */
public final class LeadInference {

    private LeadInference() {
    }

    /** 国际电话区号 → ISO 二字码（+1 北美统一记为 US） */
    private static final Map<String, String> CALLING_CODES = Map.ofEntries(
            Map.entry("1", "US"), Map.entry("7", "RU"), Map.entry("20", "EG"), Map.entry("27", "ZA"),
            Map.entry("30", "GR"), Map.entry("31", "NL"), Map.entry("32", "BE"), Map.entry("33", "FR"),
            Map.entry("34", "ES"), Map.entry("36", "HU"), Map.entry("39", "IT"), Map.entry("40", "RO"),
            Map.entry("41", "CH"), Map.entry("43", "AT"), Map.entry("44", "GB"), Map.entry("45", "DK"),
            Map.entry("46", "SE"), Map.entry("47", "NO"), Map.entry("48", "PL"), Map.entry("49", "DE"),
            Map.entry("51", "PE"), Map.entry("52", "MX"), Map.entry("54", "AR"), Map.entry("55", "BR"),
            Map.entry("56", "CL"), Map.entry("57", "CO"), Map.entry("60", "MY"), Map.entry("61", "AU"),
            Map.entry("62", "ID"), Map.entry("63", "PH"), Map.entry("64", "NZ"), Map.entry("65", "SG"),
            Map.entry("66", "TH"), Map.entry("81", "JP"), Map.entry("82", "KR"), Map.entry("84", "VN"),
            Map.entry("86", "CN"), Map.entry("90", "TR"), Map.entry("91", "IN"), Map.entry("92", "PK"),
            Map.entry("94", "LK"), Map.entry("212", "MA"), Map.entry("233", "GH"), Map.entry("234", "NG"),
            Map.entry("254", "KE"), Map.entry("351", "PT"), Map.entry("353", "IE"), Map.entry("358", "FI"),
            Map.entry("380", "UA"), Map.entry("420", "CZ"), Map.entry("852", "HK"), Map.entry("880", "BD"),
            Map.entry("886", "TW"), Map.entry("965", "KW"), Map.entry("966", "SA"), Map.entry("971", "AE"),
            Map.entry("972", "IL"), Map.entry("974", "QA"));

    /** 国家顶级域名 → ISO 二字码（.uk 对应 GB） */
    private static final Map<String, String> COUNTRY_TLD = Map.ofEntries(
            Map.entry("uk", "GB"), Map.entry("de", "DE"), Map.entry("fr", "FR"), Map.entry("it", "IT"),
            Map.entry("es", "ES"), Map.entry("nl", "NL"), Map.entry("be", "BE"), Map.entry("ch", "CH"),
            Map.entry("at", "AT"), Map.entry("se", "SE"), Map.entry("no", "NO"), Map.entry("dk", "DK"),
            Map.entry("fi", "FI"), Map.entry("pl", "PL"), Map.entry("cz", "CZ"), Map.entry("pt", "PT"),
            Map.entry("gr", "GR"), Map.entry("ie", "IE"), Map.entry("ru", "RU"), Map.entry("ua", "UA"),
            Map.entry("tr", "TR"), Map.entry("ae", "AE"), Map.entry("sa", "SA"), Map.entry("il", "IL"),
            Map.entry("in", "IN"), Map.entry("pk", "PK"), Map.entry("jp", "JP"), Map.entry("kr", "KR"),
            Map.entry("sg", "SG"), Map.entry("my", "MY"), Map.entry("th", "TH"), Map.entry("vn", "VN"),
            Map.entry("id", "ID"), Map.entry("ph", "PH"), Map.entry("au", "AU"), Map.entry("nz", "NZ"),
            Map.entry("br", "BR"), Map.entry("mx", "MX"), Map.entry("ar", "AR"), Map.entry("cl", "CL"),
            Map.entry("co", "CO"), Map.entry("za", "ZA"), Map.entry("ng", "NG"), Map.entry("eg", "EG"),
            Map.entry("ca", "CA"), Map.entry("hk", "HK"), Map.entry("tw", "TW"));

    /** 个人免费邮箱：域名不代表公司 */
    private static final Set<String> FREE_MAIL = Set.of("gmail.com", "googlemail.com", "yahoo.com", "ymail.com",
            "hotmail.com", "outlook.com", "live.com", "msn.com", "icloud.com", "me.com", "aol.com", "qq.com",
            "163.com", "126.com", "sina.com", "foxmail.com", "mail.ru", "yandex.ru", "yandex.com", "gmx.de",
            "gmx.net", "web.de", "protonmail.com", "proton.me", "zoho.com");

    /** 按区号推断国家：取最长匹配的前缀 */
    public static String countryFromPhone(String digits) {
        if (StrUtil.isBlank(digits)) {
            return null;
        }
        for (int len = 3; len >= 1; len--) {
            if (digits.length() > len) {
                String c = CALLING_CODES.get(digits.substring(0, len));
                if (c != null) {
                    return c;
                }
            }
        }
        return null;
    }

    public static String domainOf(String email) {
        return email == null || !email.contains("@") ? null : email.substring(email.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT).trim();
    }

    public static boolean isFreeMail(String domain) {
        if (domain == null) {
            return true;
        }
        if (FREE_MAIL.contains(domain)) {
            return true;
        }
        // yahoo.co.uk、hotmail.fr、outlook.de 这类国家版本
        String head = domain.contains(".") ? domain.substring(0, domain.indexOf('.')) : domain;
        return Set.of("yahoo", "hotmail", "outlook", "live", "gmx", "yandex").contains(head);
    }

    /** 企业邮箱的域名作为官网；免费邮箱返回 null */
    public static String websiteFromEmail(String email) {
        String d = domainOf(email);
        return d == null || isFreeMail(d) ? null : "https://" + d;
    }

    /** 按国家顶级域名推断国家；.com 等通用域名返回 null */
    public static String countryFromEmail(String email) {
        String d = domainOf(email);
        if (d == null || !d.contains(".")) {
            return null;
        }
        return COUNTRY_TLD.get(d.substring(d.lastIndexOf('.') + 1));
    }

    /** 根据域名猜一个公司名：mueller-gmbh.de → Mueller Gmbh */
    public static String companyFromDomain(String domain) {
        if (domain == null || isFreeMail(domain)) {
            return null;
        }
        String[] parts = domain.split("\\.");
        String core = parts.length >= 3 && parts[parts.length - 2].length() <= 3 ? parts[parts.length - 3] : parts[0];
        StringBuilder sb = new StringBuilder();
        for (String w : core.split("[-_]")) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
            }
        }
        return StrUtil.emptyToNull(sb.toString().trim());
    }

}
