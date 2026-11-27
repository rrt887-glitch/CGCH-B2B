/**
 * 搜索框里的号码规范化。
 *
 * 线上问题：会话列表显示 +36706524840，照抄搜「+36706524840」却提示没有匹配，
 * 去掉 + 搜「36706524840」才命中——后端是按号码片段 LIKE 匹配，带 + 就匹配不上。
 *
 * 规则：看起来像电话号码（可选 +、数字、空格、短横、括号）时，只留数字；
 * 其余（客户名、备注名、公司名等）原样返回，不做任何处理。
 */
export function normalizePhoneKeyword(raw: string): string {
  const keyword = (raw ?? '').trim();
  if (!keyword) return keyword;
  // 必须含数字，且整串只由 + 数字 空格 - ( ) . 组成，才当成号码
  if (!/\d/.test(keyword)) return keyword;
  if (!/^[+(\d][\d\s\-().]*$/.test(keyword)) return keyword;
  const digits = keyword.replace(/\D/g, '');
  return digits || keyword;
}
