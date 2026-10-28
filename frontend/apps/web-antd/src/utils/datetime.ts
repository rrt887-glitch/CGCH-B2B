import dayjs from 'dayjs';

/**
 * 后端返回的时间可能是毫秒时间戳（默认）、"2026-10-23 14:05:00" / ISO 字符串，或 [2026,10,23,14,5,0] 数组。
 * 统一转成本地时区显示；空值或无法识别时返回 empty。
 */
export function toDate(v: unknown): dayjs.Dayjs | undefined {
  if (v === null || v === undefined || v === '') return undefined;
  let d: dayjs.Dayjs;
  if (Array.isArray(v)) {
    const [y, mo = 1, day = 1, h = 0, mi = 0, s = 0] = v as number[];
    d = dayjs(new Date(y as number, mo - 1, day, h, mi, s));
  } else if (typeof v === 'number' || (typeof v === 'string' && /^\d{10,13}$/.test(v))) {
    const n = Number(v);
    d = dayjs(n < 1e12 ? n * 1000 : n);
  } else {
    d = dayjs(v as dayjs.ConfigType);
  }
  return d.isValid() ? d : undefined;
}

export function fmtDateTime(v: unknown, pattern = 'YYYY-MM-DD HH:mm', empty = '') {
  return toDate(v)?.format(pattern) ?? empty;
}

/** 列表里的短时间：今天显示 14:05，今年显示 10-23 14:05，往年显示 2025-10-23 */
export function fmtShortTime(v: unknown, empty = '') {
  const d = toDate(v);
  if (!d) return empty;
  const now = dayjs();
  if (d.isSame(now, 'day')) return d.format('HH:mm');
  if (d.isSame(now, 'year')) return d.format('MM-DD HH:mm');
  return d.format('YYYY-MM-DD');
}
