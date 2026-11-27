import { describe, expect, it } from 'vitest';

import {
  analyzeCustomerHealth,
  corporateDomain,
  countryFlag,
  extractDealHintsFromTexts,
  formatDealMoney,
  getBuyerLocalClock,
  parseDealBoard,
  serializeDealBoard,
  toUsdAmount,
} from './deal-meta';

describe('CRM foreign-trade deal-meta engine', () => {
  it('round-trips 4-grid DealBoard and collision hint inside remark', () => {
    const encoded = serializeDealBoard({
      currency: 'EUR',
      amount: 24500,
      productQty: '高速吹风机 · 2,000 pcs',
      quoteTerms: 'FOB Ningbo €12.25',
      sampleInfo: 'DHL 5829103812',
      archived: true,
      lostReason: '买家采购计划推迟',
      collisionHint: '域名 nordic.de 另有客户 Anna（王经理）',
      note: '偏好深空灰礼盒包装',
    });

    const parsed = parseDealBoard(encoded);
    expect(parsed.currency).toBe('EUR');
    expect(parsed.amount).toBe(24500);
    expect(parsed.productQty).toBe('高速吹风机 · 2,000 pcs');
    expect(parsed.quoteTerms).toBe('FOB Ningbo €12.25');
    expect(parsed.sampleInfo).toBe('DHL 5829103812');
    expect(parsed.archived).toBe(true);
    expect(parsed.lostReason).toBe('买家采购计划推迟');
    expect(parsed.collisionHint).toContain('nordic.de');
    expect(parsed.note).toBe('偏好深空灰礼盒包装');
  });

  it('formats foreign-trade currencies and converts to USD summary', () => {
    expect(formatDealMoney(18500, 'USD')).toBe('$18,500');
    expect(formatDealMoney(1200.5, 'EUR')).toBe('€1,200.5');
    expect(toUsdAmount(10000, 'EUR')).toBeGreaterThan(10000);
  });

  it('computes country flags, local clocks, and corporate domains', () => {
    expect(countryFlag('US')).toBe('🇺🇸');
    const clock = getBuyerLocalClock('DE', new Date('2026-06-15T12:00:00Z'));
    expect(clock?.period).toBe('day');
    expect(corporateDomain('buyer@gmail.com')).toBeUndefined();
    expect(corporateDomain('purchasing@nordic-tools.de')).toBe('nordic-tools.de');
  });

  it('extracts quote terms, MOQ quantities, and courier numbers from chat text', () => {
    const hints = extractDealHintsFromTexts([
      'Hi, our target order is 2,000 pcs with custom logo.',
      'We quoted FOB Shenzhen $18.50, total $37,000.',
      'Sample shipped via DHL 9988776655.',
    ]);
    expect(hints.productQty).toContain('2,000 pcs');
    expect(hints.quoteTerms).toContain('FOB');
    expect(hints.amount).toBe(37000);
    expect(hints.sampleInfo).toContain('DHL');
  });

  it('flags overdue and stagnant stages accurately', () => {
    const overdue = analyzeCustomerHealth({
      stage: 2,
      contactNextTime: '2020-01-01T10:00:00Z',
    });
    expect(overdue.overdueDays).toBeGreaterThan(0);
    expect(overdue.nextTone).toBe('red');
  });
});
