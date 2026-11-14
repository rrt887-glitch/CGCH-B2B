package cn.iocoder.yudao.module.system.enums;

import cn.hutool.core.util.StrUtil;

import java.util.regex.Pattern;

/**
 * 登录账号规则：新建的账号必须是邮箱或手机号，并且在整个平台内唯一（不同公司之间也不能重复），
 * 这样登录时只填账号和密码，系统就能找到账号所属的公司，登录页不用再选公司。
 *
 * 老账号（字母和数字，例如 admin）照样能登录和修改资料，只是新建时不再允许。
 */
public interface LoginAccountPolicy {

    /** 邮箱 */
    String EMAIL = "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}";

    /** 手机号：可带国际区号的 +，6~20 位数字 */
    String MOBILE = "\\+?[0-9]{6,20}";

    /** 老账号：4~30 位字母或数字 */
    String LEGACY = "[a-zA-Z0-9]{4,30}";

    /** 登录、修改资料时接受的账号格式（含老账号） */
    String LOGIN_REGEX = "^(" + LEGACY + "|" + EMAIL + "|" + MOBILE + ")$";

    String LOGIN_MESSAGE = "账号请填写邮箱或手机号";

    /** 账号最长 64 位（邮箱可能较长） */
    int MAX_LENGTH = 64;

    Pattern NEW_ACCOUNT = Pattern.compile("^(" + EMAIL + "|" + MOBILE + ")$");

    String NEW_ACCOUNT_MESSAGE = "登录账号请使用邮箱或手机号";

    /** 新建账号是否符合规则：邮箱或手机号 */
    static boolean isValidNewAccount(String username) {
        return StrUtil.isNotBlank(username) && username.length() <= MAX_LENGTH
                && NEW_ACCOUNT.matcher(username.trim()).matches();
    }

}
