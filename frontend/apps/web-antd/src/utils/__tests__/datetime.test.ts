import { describe, expect, it } from 'vitest';

import { fmtDateTime, toDate } from '../datetime';

describe('fmtDateTime', () => {
  const ms = new Date(2026, 9, 23, 14, 5, 9).getTime();

  it('formats millisecond timestamps returned by the backend', () => {
    expect(fmtDateTime(ms)).toBe('2026-10-23 14:05');
    expect(fmtDateTime(ms, 'HH:mm')).toBe('14:05');
    expect(fmtDateTime(String(ms), 'MM-DD')).toBe('10-23');
  });

  it('formats second timestamps, strings and LocalDateTime arrays', () => {
    expect(fmtDateTime(Math.floor(ms / 1000), 'YYYY-MM-DD HH:mm:ss')).toBe('2026-10-23 14:05:09');
    expect(fmtDateTime('2026-10-23 14:05:09', 'MM-DD HH:mm')).toBe('10-23 14:05');
    expect(fmtDateTime([2026, 10, 23, 14, 5, 9], 'YYYY-MM-DD HH:mm:ss')).toBe('2026-10-23 14:05:09');
  });

  it('returns the placeholder for empty or invalid values', () => {
    expect(fmtDateTime(undefined)).toBe('');
    expect(fmtDateTime(null, 'HH:mm', '—')).toBe('—');
    expect(fmtDateTime('', 'HH:mm', '-')).toBe('-');
    expect(toDate('not a date')).toBeUndefined();
  });
});
