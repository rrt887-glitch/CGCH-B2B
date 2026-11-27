import { describe, expect, it } from 'vitest';

import { normalizePhoneKeyword } from '../phone-keyword';

/**
 * 线上：列表写着 +36706524840，照抄搜索却「没有找到匹配的会话」，
 * 去掉 + 才能命中。这里锁住号码规范化规则。
 */
describe('normalizePhoneKeyword', () => {
  it('带 + 的号码去掉 +', () => {
    expect(normalizePhoneKeyword('+36706524840')).toBe('36706524840');
  });

  it('分隔符全部去掉', () => {
    expect(normalizePhoneKeyword('+36 70 652 4840')).toBe('36706524840');
    expect(normalizePhoneKeyword('(36) 70-652-4840')).toBe('36706524840');
    expect(normalizePhoneKeyword('9811689467')).toBe('9811689467');
  });

  it('客户名 / 备注名原样保留', () => {
    expect(normalizePhoneKeyword('Acme Trading')).toBe('Acme Trading');
    expect(normalizePhoneKeyword('张三')).toBe('张三');
    expect(normalizePhoneKeyword('QA-no-lead-1127')).toBe('QA-no-lead-1127');
  });

  it('空值与空白', () => {
    expect(normalizePhoneKeyword('')).toBe('');
    expect(normalizePhoneKeyword('   ')).toBe('');
  });
});
