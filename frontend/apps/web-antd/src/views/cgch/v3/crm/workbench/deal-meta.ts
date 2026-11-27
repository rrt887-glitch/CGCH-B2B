/**
 * 外贸 B2B 客户管道核心元数据引擎：
 * 1. 六步标准销售管道（新询盘 → 已报价 → 寄样测款 → 商务谈判 → 已成交 → 复购维护）+ 暂缓/流失归档态
 * 2. 外贸多币种金额格式化与美金折算汇总（默认 USD $）
 * 3. 买家国家国旗与实时当地时区日夜状态（☀️ 上班中 / 🌆 傍晚 / 🌙 深夜）
 * 4. 4 格「📌 商务黑板」结构化编码与解析（持久化于服务端客户 remark 字段，零迁移成本且天然供 AI 读取）
 * 5. 阶段停滞老化（Stage Aging）、首响 SLA 预警与下一步建议引擎
 */
import dayjs from 'dayjs';

export type CurrencyCode = 'CNY' | 'EUR' | 'GBP' | 'USD';

export interface PipelineStageMeta {
  agingDays: number;
  bg: string;
  color: string;
  defaultAction: string;
  icon: string;
  label: string;
  short: string;
  value: number;
}

export const PIPELINE_STAGES: PipelineStageMeta[] = [
  {
    value: 1,
    label: '新询盘',
    short: '新询盘',
    color: '#007AFF',
    bg: '#E8F2FF',
    icon: 'lucide:sparkles',
    agingDays: 2,
    defaultAction: '首轮回复与发目录',
  },
  {
    value: 2,
    label: '已报价',
    short: '已报价',
    color: '#FF9500',
    bg: '#FFF4E0',
    icon: 'lucide:file-spreadsheet',
    agingDays: 7,
    defaultAction: '跟进报价反馈',
  },
  {
    value: 3,
    label: '寄样测款',
    short: '寄样',
    color: '#AF52DE',
    bg: '#F5EBFC',
    icon: 'lucide:package-check',
    agingDays: 10,
    defaultAction: '跟进样品签收测款',
  },
  {
    value: 4,
    label: '商务谈判',
    short: '谈判',
    color: '#5856D6',
    bg: '#EDEDFC',
    icon: 'lucide:handshake',
    agingDays: 7,
    defaultAction: '敲定大货条款与定金',
  },
  {
    value: 5,
    label: '已成交',
    short: '成交',
    color: '#248A3D',
    bg: '#EAF9EE',
    icon: 'lucide:circle-check-big',
    agingDays: 30,
    defaultAction: '同步交期与收尾款',
  },
  {
    value: 6,
    label: '复购维护',
    short: '复购',
    color: '#0D9488',
    bg: '#E6F9F6',
    icon: 'lucide:repeat',
    agingDays: 45,
    defaultAction: '新品推介与返单关怀',
  },
];

export const LOST_REASONS = [
  '目标价差距较大',
  'MOQ 起订量不匹配',
  '交期或认证暂不满足',
  '买家采购计划推迟',
  '已选其他供应商',
  '多次跟进暂无回音',
];

export const CURRENCIES: {
  code: CurrencyCode;
  label: string;
  rateToUsd: number;
  symbol: string;
}[] = [
  { code: 'USD', symbol: '$', label: 'USD ($)', rateToUsd: 1 },
  { code: 'EUR', symbol: '€', label: 'EUR (€)', rateToUsd: 1.08 },
  { code: 'GBP', symbol: '£', label: 'GBP (£)', rateToUsd: 1.27 },
  { code: 'CNY', symbol: '¥', label: 'RMB (¥)', rateToUsd: 0.14 },
];

export function getStageMeta(stage?: number | null, dealStatus?: boolean): PipelineStageMeta {
  const normalized = stage || (dealStatus ? 5 : 1);
  return PIPELINE_STAGES.find((s) => s.value === normalized) ?? PIPELINE_STAGES[0]!;
}

export function currencySymbol(code?: CurrencyCode | string): string {
  return CURRENCIES.find((c) => c.code === code)?.symbol ?? '$';
}

export function formatDealMoney(
  amount?: number | null,
  currency: CurrencyCode | string = 'USD',
): string {
  if (amount === undefined || amount === null || Number.isNaN(Number(amount))) {
    return '';
  }
  const n = Number(amount);
  const sym = currencySymbol(currency);
  return `${sym}${n.toLocaleString('en-US', {
    minimumFractionDigits: 0,
    maximumFractionDigits: n % 1 === 0 ? 0 : 2,
  })}`;
}

export function toUsdAmount(
  amount?: number | null,
  currency: CurrencyCode | string = 'USD',
): number {
  if (!amount || Number.isNaN(Number(amount))) return 0;
  const rate = CURRENCIES.find((c) => c.code === currency)?.rateToUsd ?? 1;
  return Math.round(Number(amount) * rate);
}

/** 4 格商务黑板 + 归档/防撞单结构 */
export interface DealBoard {
  amount?: number;
  archived?: boolean;
  collisionHint?: string;
  currency: CurrencyCode;
  lostReason?: string;
  note: string;
  productQty: string;
  quoteTerms: string;
  sampleInfo: string;
  screeningTag?: 'spam' | 'uncertain';
}

const BOARD_PREFIX = '[商务黑板]';

/**
 * 从客户/线索 remark 中无损解析出「4 格商务黑板」与普通备注。
 * 同时兼容后端自动建档生成的「需求：xxx，数量 yyy」及「[同企防撞单]」标记。
 */
export function parseDealBoard(rawRemark?: null | string): DealBoard {
  const board: DealBoard = {
    currency: 'USD',
    amount: undefined,
    productQty: '',
    quoteTerms: '',
    sampleInfo: '',
    archived: false,
    lostReason: '',
    note: '',
  };
  if (!rawRemark) return board;

  const lines = rawRemark.split(/\r?\n/);
  const keptLines: string[] = [];

  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (line.startsWith(BOARD_PREFIX)) {
      const body = line.slice(BOARD_PREFIX.length).trim();
      for (const seg of body.split('|')) {
        const idx = seg.indexOf(':');
        if (idx <= 0) continue;
        const k = seg.slice(0, idx).trim();
        const v = seg.slice(idx + 1).trim();
        if (!v) continue;
        if (k === '预估' || k === '金额') {
          const m = v.match(/^(USD|EUR|GBP|CNY)?\s*([\d.]+)$/i);
          if (m) {
            if (m[1]) board.currency = m[1].toUpperCase() as CurrencyCode;
            board.amount = Number(m[2]);
          }
        } else if (k === '币种' && ['CNY', 'EUR', 'GBP', 'USD'].includes(v.toUpperCase())) {
          board.currency = v.toUpperCase() as CurrencyCode;
        } else if (k === '产品') {
          board.productQty = v;
        } else if (k === '报价') {
          board.quoteTerms = v;
        } else if (k === '样品') {
          board.sampleInfo = v;
        } else if (k === '归档') {
          board.archived = v === '1' || v === 'true' || v.startsWith('暂缓');
          const rm = v.match(/暂缓\((.+)\)/);
          if (rm?.[1]) board.lostReason = rm[1];
        } else if (k === '原因') {
          board.lostReason = v;
        }
      }
      continue;
    }

    if (line.includes('[AI初筛:疑似推销]')) {
      board.screeningTag = 'spam';
    } else if (line.includes('[AI初筛:待确认意向]')) {
      board.screeningTag = 'uncertain';
    }

    if (line.startsWith('[同企防撞单]')) {
      board.collisionHint = line.replace('[同企防撞单]', '').trim();
      continue;
    }

    // 兼容历史自动建档行：「需求：吹风机，数量 500 pcs」
    if (!board.productQty && line.startsWith('需求：')) {
      board.productQty = line
        .replace(/^需求：/, '')
        .replace(/，数量\s*/, ' · ')
        .trim();
    }

    keptLines.push(rawLine);
  }

  board.note = keptLines
    .join('\n')
    .replace(/\[AI初筛:[^\]]+\]\s*/g, '')
    .trim();

  return board;
}

const cleanToken = (s?: string) =>
  (s || '').replaceAll('|', '/').replaceAll(/\r?\n/g, ' ').trim();

/** 将 4 格商务黑板与自由备注序列化回可读字符串，存入服务端 remark 字段 */
export function serializeDealBoard(board: Partial<DealBoard>, existingCollision?: string): string {
  const segs: string[] = [];
  const cur = board.currency || 'USD';
  if (board.amount !== undefined && board.amount !== null && !Number.isNaN(Number(board.amount)) && Number(board.amount) > 0) {
    segs.push(`预估:${cur} ${Number(board.amount)}`);
  } else if (cur !== 'USD') {
    segs.push(`币种:${cur}`);
  }
  if (cleanToken(board.productQty)) segs.push(`产品:${cleanToken(board.productQty)}`);
  if (cleanToken(board.quoteTerms)) segs.push(`报价:${cleanToken(board.quoteTerms)}`);
  if (cleanToken(board.sampleInfo)) segs.push(`样品:${cleanToken(board.sampleInfo)}`);
  if (board.archived) {
    const reason = cleanToken(board.lostReason);
    segs.push(`归档:${reason ? `暂缓(${reason})` : '暂缓'}`);
  }

  const out: string[] = [];
  if (segs.length) {
    out.push(`${BOARD_PREFIX} ${segs.join(' | ')}`);
  }
  const collision = cleanToken(board.collisionHint ?? existingCollision);
  if (collision) {
    out.push(`[同企防撞单] ${collision}`);
  }
  if (board.note?.trim()) {
    out.push(board.note.trim());
  }
  return out.join('\n').slice(0, 980);
}

/** ISO 二字国家码转 Emoji 国旗 */
export function countryFlag(code?: null | string): string {
  if (!code || code.length !== 2) return '🌐';
  const upper = code.toUpperCase();
  if (!/^[A-Z]{2}$/.test(upper)) return '🌐';
  return String.fromCodePoint(
    0x1f1e6 + upper.charCodeAt(0) - 65,
    0x1f1e6 + upper.charCodeAt(1) - 65,
  );
}

/** 主要外贸伙伴国家 → 代表商业时区映射 */
const COUNTRY_TIMEZONES: Record<string, string> = {
  US: 'America/New_York',
  CA: 'America/Toronto',
  MX: 'America/Mexico_City',
  BR: 'America/Sao_Paulo',
  AR: 'America/Argentina/Buenos_Aires',
  CL: 'America/Santiago',
  CO: 'America/Bogota',
  PE: 'America/Lima',
  GB: 'Europe/London',
  IE: 'Europe/Dublin',
  PT: 'Europe/Lisbon',
  DE: 'Europe/Berlin',
  FR: 'Europe/Paris',
  IT: 'Europe/Rome',
  ES: 'Europe/Madrid',
  NL: 'Europe/Amsterdam',
  BE: 'Europe/Brussels',
  CH: 'Europe/Zurich',
  AT: 'Europe/Vienna',
  PL: 'Europe/Warsaw',
  CZ: 'Europe/Prague',
  SE: 'Europe/Stockholm',
  NO: 'Europe/Oslo',
  DK: 'Europe/Copenhagen',
  FI: 'Europe/Helsinki',
  GR: 'Europe/Athens',
  TR: 'Europe/Istanbul',
  RU: 'Europe/Moscow',
  UA: 'Europe/Kyiv',
  RO: 'Europe/Bucharest',
  HU: 'Europe/Budapest',
  AE: 'Asia/Dubai',
  SA: 'Asia/Riyadh',
  QA: 'Asia/Qatar',
  KW: 'Asia/Kuwait',
  IL: 'Asia/Jerusalem',
  EG: 'Africa/Cairo',
  MA: 'Africa/Casablanca',
  ZA: 'Africa/Johannesburg',
  NG: 'Africa/Lagos',
  KE: 'Africa/Nairobi',
  GH: 'Africa/Accra',
  IN: 'Asia/Kolkata',
  PK: 'Asia/Karachi',
  BD: 'Asia/Dhaka',
  LK: 'Asia/Colombo',
  SG: 'Asia/Singapore',
  MY: 'Asia/Kuala_Lumpur',
  TH: 'Asia/Bangkok',
  VN: 'Asia/Ho_Chi_Minh',
  ID: 'Asia/Jakarta',
  PH: 'Asia/Manila',
  JP: 'Asia/Tokyo',
  KR: 'Asia/Seoul',
  CN: 'Asia/Shanghai',
  HK: 'Asia/Hong_Kong',
  TW: 'Asia/Taipei',
  AU: 'Australia/Sydney',
  NZ: 'Pacific/Auckland',
};

export interface BuyerLocalClock {
  icon: string;
  label: string;
  period: 'day' | 'eve' | 'night';
  time: string;
}

/** 计算买家所在国当前当地时间及是否处于工作黄金联系窗口 */
export function getBuyerLocalClock(
  countryCode?: null | string,
  now = new Date(),
): BuyerLocalClock | undefined {
  if (!countryCode) return undefined;
  const tz = COUNTRY_TIMEZONES[countryCode.toUpperCase()];
  if (!tz) return undefined;
  try {
    const parts = new Intl.DateTimeFormat('en-GB', {
      timeZone: tz,
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).formatToParts(now);
    const hh = Number(parts.find((p) => p.type === 'hour')?.value ?? 0);
    const mm = parts.find((p) => p.type === 'minute')?.value ?? '00';
    const time = `${String(hh).padStart(2, '0')}:${mm}`;
    if (hh >= 8 && hh < 18) {
      return { time, period: 'day', icon: '☀️', label: '当地工作时段' };
    }
    if (hh >= 18 && hh < 22) {
      return { time, period: 'eve', icon: '🌆', label: '当地傍晚' };
    }
    return { time, period: 'night', icon: '🌙', label: '当地深夜' };
  } catch {
    return undefined;
  }
}

export interface CustomerHealth {
  awaitingFirstReply: boolean;
  dueToday: boolean;
  idleDays: number;
  nextLabel: string;
  nextTone: 'amber' | 'blue' | 'gray' | 'green' | 'red';
  overdueDays: number;
  stagnant: boolean;
  waitHours: number;
}

/** 综合诊断单个客户的跟进紧迫度、首响超时与阶段停滞预警 */
export function analyzeCustomerHealth(
  row: any,
  board?: DealBoard,
): CustomerHealth {
  const stageMeta = getStageMeta(row?.stage, row?.dealStatus);
  const now = dayjs();

  if (board?.archived) {
    return {
      overdueDays: 0,
      dueToday: false,
      awaitingFirstReply: false,
      waitHours: 0,
      idleDays: 0,
      stagnant: false,
      nextLabel: board.lostReason ? `已暂缓 · ${board.lostReason}` : '已暂缓归档',
      nextTone: 'gray',
    };
  }

  let overdueDays = 0;
  let dueToday = false;
  if (row?.contactNextTime) {
    const next = dayjs(row.contactNextTime);
    if (next.isValid()) {
      if (next.isBefore(now, 'day')) {
        overdueDays = Math.max(1, now.startOf('day').diff(next.startOf('day'), 'day'));
      } else if (next.isSame(now, 'day')) {
        dueToday = true;
      }
    }
  }

  const lastTouch = row?.contactLastTime || row?.updateTime || row?.createTime;
  const idleDays = lastTouch && dayjs(lastTouch).isValid()
    ? Math.max(0, now.diff(dayjs(lastTouch), 'day'))
    : 0;

  const created = row?.createTime ? dayjs(row.createTime) : undefined;
  const waitHours = created?.isValid() ? Math.max(0, now.diff(created, 'hour')) : 0;
  const awaitingFirstReply =
    (row?.stage || 1) === 1 && !row?.followUpStatus && waitHours >= 1 && waitHours <= 72;

  const stagnant =
    stageMeta.value >= 2 &&
    stageMeta.value <= 4 &&
    idleDays >= stageMeta.agingDays;

  if (overdueDays > 0) {
    return {
      overdueDays,
      dueToday: false,
      awaitingFirstReply,
      waitHours,
      idleDays,
      stagnant,
      nextLabel: `逾期 ${overdueDays} 天未联系`,
      nextTone: 'red',
    };
  }
  if (awaitingFirstReply) {
    return {
      overdueDays: 0,
      dueToday,
      awaitingFirstReply: true,
      waitHours,
      idleDays,
      stagnant,
      nextLabel: `待首响 · 已等 ${waitHours}h`,
      nextTone: 'red',
    };
  }
  if (dueToday) {
    return {
      overdueDays: 0,
      dueToday: true,
      awaitingFirstReply: false,
      waitHours,
      idleDays,
      stagnant,
      nextLabel: '今日待联系',
      nextTone: 'amber',
    };
  }
  if (row?.poolDay !== undefined && row?.poolDay !== null && row.poolDay <= 3 && !row?.dealStatus) {
    return {
      overdueDays: 0,
      dueToday: false,
      awaitingFirstReply: false,
      waitHours,
      idleDays,
      stagnant,
      nextLabel: `剩 ${row.poolDay} 天掉公海`,
      nextTone: 'amber',
    };
  }
  if (stagnant) {
    return {
      overdueDays: 0,
      dueToday: false,
      awaitingFirstReply: false,
      waitHours,
      idleDays,
      stagnant: true,
      nextLabel: `停滞 ${idleDays} 天 · 建议追单`,
      nextTone: 'amber',
    };
  }
  if (row?.contactNextTime && dayjs(row.contactNextTime).isValid()) {
    return {
      overdueDays: 0,
      dueToday: false,
      awaitingFirstReply: false,
      waitHours,
      idleDays,
      stagnant: false,
      nextLabel: `${dayjs(row.contactNextTime).format('MM-DD')} · ${stageMeta.defaultAction}`,
      nextTone: 'blue',
    };
  }
  return {
    overdueDays: 0,
    dueToday: false,
    awaitingFirstReply: false,
    waitHours,
    idleDays,
    stagnant: false,
    nextLabel: stageMeta.defaultAction,
    nextTone: stageMeta.value >= 5 ? 'green' : 'blue',
  };
}

/** 从近期沟通消息与摘要中智能抓取报价条款、金额、数量与快递单号线索（用于抽屉一键提取） */
export function extractDealHintsFromTexts(texts: string[]): Partial<DealBoard> {
  const joined = texts.filter(Boolean).join('\n');
  if (!joined.trim()) return {};
  const hints: Partial<DealBoard> = {};

  // 提取贸易术语与价格，例如 FOB Shenzhen $18.5 / EXW USD 12.00
  const termMatch = joined.match(
    /\b(FOB|CIF|EXW|DDP|CFR)\s*[A-Za-z\s]{0,16}?(?:USD|\$|EUR|€)?\s*\d+(?:\.\d+)?/i,
  );
  if (termMatch) {
    hints.quoteTerms = termMatch[0].trim();
  }

  // 提取较大金额作为预估单值，或单价×数量
  const moneyMatches = [
    ...joined.matchAll(/(?:\$|USD\s*)(\d{1,3}(?:,\d{3})+|\d{3,7})(?:\.\d{1,2})?/gi),
  ];
  if (moneyMatches.length > 0) {
    const nums = moneyMatches
      .map((m) => Number((m[1] || '').replaceAll(',', '')))
      .filter((n) => n >= 100 && n <= 10_000_000);
    if (nums.length) {
      hints.currency = 'USD';
      hints.amount = Math.max(...nums);
    }
  }

  // 提取采购数量，例如 2000 pcs / 500 units / 1000 sets
  const qtyMatch = joined.match(
    /\b(\d{1,3}(?:,\d{3})*|\d+)\s*(pcs|pieces|units|sets|cartons|boxes|kg|tons)\b/i,
  );
  if (qtyMatch) {
    hints.productQty = qtyMatch[0].trim();
  }

  // 提取国际快递与样品信息
  const expressMatch = joined.match(
    /\b(DHL|FedEx|UPS|TNT|SF Express|顺丰)[\s:#-]*([A-Z0-9]{8,22})?/i,
  );
  if (expressMatch) {
    hints.sampleInfo = expressMatch[0].trim();
  } else if (/\bsample\b|样品|寄样|打样/i.test(joined)) {
    hints.sampleInfo = '沟通中提及寄样/测款需求';
  }

  return hints;
}

/** 提取企业邮箱域名（忽略常见个人免费邮箱），用于前端同企业撞单提示 */
const COMMON_FREE_DOMAINS = new Set([
  'gmail.com',
  'googlemail.com',
  'yahoo.com',
  'hotmail.com',
  'outlook.com',
  'live.com',
  'icloud.com',
  'aol.com',
  'qq.com',
  '163.com',
  '126.com',
  'foxmail.com',
  'proton.me',
  'protonmail.com',
  'mail.ru',
  'yandex.com',
]);

export function corporateDomain(email?: null | string, website?: null | string): string | undefined {
  if (email && email.includes('@')) {
    const d = email.split('@')[1]?.toLowerCase().trim();
    if (d && !COMMON_FREE_DOMAINS.has(d) && !/^(yahoo|hotmail|outlook|live)\./.test(d)) {
      return d;
    }
  }
  if (website) {
    const clean = website
      .toLowerCase()
      .replace(/^https?:\/\//, '')
      .replace(/^www\./, '')
      .split('/')[0]
      ?.trim();
    if (clean && clean.includes('.') && !COMMON_FREE_DOMAINS.has(clean)) {
      return clean;
    }
  }
  return undefined;
}
