package cn.iocoder.yudao.module.system.enums;

/**
 * 密码规则：新设置的密码至少 8 位，必须同时包含字母和数字。
 * 登录时不套用这条规则，老账号的短密码照样能登录，下次修改时再按新规则。
 */
public interface PasswordPolicy {

    /** 8~64 位，至少一个字母、至少一个数字 */
    String REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$";

    String MESSAGE = "密码至少 8 位，且必须同时包含字母和数字";

    /** 同一账号连续输错多少次后锁定 */
    int MAX_LOGIN_FAILURES = 5;

    /** 锁定多少分钟 */
    int LOCK_MINUTES = 15;

    /** 记录登录失败次数的缓存键：按租户 + 账号（不区分大小写） */
    static String loginFailKey(Long tenantId, String username) {
        return "cgch:login-fail:" + (tenantId == null ? 0 : tenantId) + ":"
                + (username == null ? "" : username.trim().toLowerCase());
    }

}
