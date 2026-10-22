package cn.iocoder.yudao.server.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 生产环境（prod）启动前检查密钥配置，不合格直接拒绝启动，避免带着公开过的或空的密钥上线。
 * <ul>
 *     <li>字段加密密钥 CGCH_FIELD_ENCRYPT_KEY：必填、至少 16 位、不能是仓库里出现过的值</li>
 *     <li>数据库密码 DB_PASSWORD：必填、不能是 123456 这类默认值</li>
 *     <li>Redis 未设置密码、开启了 API 加密却没配密钥：给出警告</li>
 * </ul>
 */
public class ProductionSecretsCheck implements EnvironmentPostProcessor {

    /** 曾经出现在公开仓库里、或仅供开发使用的值 */
    private static final Set<String> KNOWN = Set.of("WLWohCayruUlMUWn", "local-only-insecure-key", "dev-only-insecure-key",
            "Z579dnaRSE9uJ2vgpjnILyP0S6grC98z", "qpizJVFuBjkrWRIw1FrVlf6aECC2trdh", "change-me", "please-change-me");
    private static final Set<String> WEAK_DB = Set.of("123456", "root", "password", "admin", "admin123", "12345678");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        if (!Arrays.asList(env.getActiveProfiles()).contains("prod")) {
            return;
        }
        List<String> errors = new ArrayList<>();
        String fieldKey = env.getProperty("mybatis-plus.encryptor.password", "");
        if (fieldKey.isBlank()) {
            errors.add("未设置字段加密密钥 CGCH_FIELD_ENCRYPT_KEY（用于加密邮箱密码、WhatsApp 令牌、大模型 Key 等）");
        } else if (fieldKey.length() < 16) {
            errors.add("字段加密密钥 CGCH_FIELD_ENCRYPT_KEY 太短，至少 16 位");
        } else if (KNOWN.contains(fieldKey)) {
            errors.add("字段加密密钥 CGCH_FIELD_ENCRYPT_KEY 使用了公开过的值，请重新生成（例如 openssl rand -base64 24）");
        }
        String dbPassword = env.getProperty("spring.datasource.dynamic.datasource.master.password",
                env.getProperty("spring.datasource.datasource.master.password", ""));
        String dbUrl = env.getProperty("DB_URL", "");
        if (dbUrl.isBlank()) {
            errors.add("未设置数据库地址 DB_URL");
        }
        String dbPwdEnv = env.getProperty("DB_PASSWORD", dbPassword);
        if (dbPwdEnv == null || dbPwdEnv.isBlank()) {
            errors.add("未设置数据库密码 DB_PASSWORD");
        } else if (WEAK_DB.contains(dbPwdEnv)) {
            errors.add("数据库密码 DB_PASSWORD 过于简单，请更换");
        }
        if (env.getProperty("REDIS_PASSWORD", "").isBlank()) {
            System.err.println("[安全提醒] 未设置 REDIS_PASSWORD：请确保 Redis 只在内网可访问");
        }
        if ("true".equalsIgnoreCase(env.getProperty("yudao.api-encrypt.enable", "false"))) {
            String rk = env.getProperty("yudao.api-encrypt.request-key", "");
            String sk = env.getProperty("yudao.api-encrypt.response-key", "");
            if (rk.isBlank() || sk.isBlank() || KNOWN.contains(rk) || KNOWN.contains(sk)) {
                errors.add("已开启 API 加密，但 CGCH_API_ENCRYPT_REQUEST_KEY / CGCH_API_ENCRYPT_RESPONSE_KEY 未设置或使用了公开过的值");
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("生产环境安全检查未通过，已拒绝启动：\n - " + String.join("\n - ", errors)
                    + "\n请参考仓库根目录的 .env.example 和 docs/DEPLOY.md 设置环境变量。");
        }
    }

}
