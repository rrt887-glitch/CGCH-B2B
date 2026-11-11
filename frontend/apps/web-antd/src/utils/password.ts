/**
 * 密码规则（和后端 PasswordPolicy 保持一致）：新设置的密码至少 8 位，必须同时包含字母和数字。
 * 登录时不套用这条规则，老账号的短密码照样能登录。
 */
export const PASSWORD_REGEX = /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/;

export const PASSWORD_TIP = '密码至少 8 位，且必须同时包含字母和数字';

export function isStrongPassword(value?: null | string) {
  return !!value && PASSWORD_REGEX.test(value);
}
