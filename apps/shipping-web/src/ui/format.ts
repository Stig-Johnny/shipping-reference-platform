// Display formatting with Intl, for reading only. Prices are formatted from the decimal string the server sent; they
// are never parsed into floating point numbers, rounded or computed with. Times are shown in the browser's time zone
// unless a caller (a test) names one.

const LOCALE = 'en-GB';
const AMOUNT = /^(0|[1-9][0-9]{0,8})\.[0-9]{2}$/;
const SERVICE_LEVEL_LABELS: Readonly<Record<string, string>> = {
  ECONOMY: 'Economy', STANDARD: 'Standard', EXPRESS: 'Express',
};
const STATUS_LABELS: Readonly<Record<string, string>> = {
  BOOKED: 'Booked', CANCELLED: 'Cancelled', OPEN: 'Open', EXPIRED: 'Expired',
};

export interface PriceText {
  /** What the eye reads: "18.81 EUR", with tabular figures. */
  readonly visual: string;
  /** What a screen reader says: "18.81 euros". */
  readonly spoken: string;
}

/** A contract amount: a decimal string Intl.NumberFormat formats exactly, digit for digit. */
function isAmount(amount: string): amount is `${number}` {
  return AMOUNT.test(amount);
}

export function formatPrice(amount: string, currency: string): PriceText {
  const raw = { visual: `${amount} ${currency}`, spoken: `${amount} ${currency}` };
  if (!isAmount(amount)) {
    return raw;
  }
  try {
    const digits = { minimumFractionDigits: 2, maximumFractionDigits: 2 } as const;
    const visual = new Intl.NumberFormat(LOCALE, digits).format(amount);
    const spoken = new Intl.NumberFormat(LOCALE, { ...digits, style: 'currency', currency, currencyDisplay: 'name' })
      .format(amount);
    return { visual: `${visual} ${currency}`, spoken };
  } catch {
    return raw;
  }
}

function instant(iso: string): Date | undefined {
  const milliseconds = Date.parse(iso);
  return Number.isNaN(milliseconds) ? undefined : new Date(milliseconds);
}

function zone(timeZone: string | undefined): { readonly timeZone?: string } {
  return timeZone === undefined ? {} : { timeZone };
}

/** "09:30" */
export function formatTime(iso: string, timeZone?: string): string {
  const date = instant(iso);
  return date === undefined ? iso
    : new Intl.DateTimeFormat(LOCALE, { hour: '2-digit', minute: '2-digit', hourCycle: 'h23', ...zone(timeZone) })
      .format(date);
}

/** "10 Nov 2026, 09:05" */
export function formatDateTime(iso: string, timeZone?: string): string {
  const date = instant(iso);
  return date === undefined ? iso : new Intl.DateTimeFormat(LOCALE, {
    day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
    ...zone(timeZone),
  }).format(date);
}

/** Whole minutes until the instant, rounded up so that the last minute still reads 1; 0 from the instant on. */
export function minutesLeft(iso: string, now: number): number {
  const remaining = Date.parse(iso) - now;
  return remaining > 0 ? Math.ceil(remaining / 60_000) : 0;
}

export interface Validity {
  readonly until: string;
  /** The part that changes every 15 seconds; it is not announced. */
  readonly relative: string;
  readonly expired: boolean;
}

export function formatValidity(expiresAt: string, now: number, timeZone?: string): Validity {
  const time = formatTime(expiresAt, timeZone);
  const minutes = minutesLeft(expiresAt, now);
  return minutes > 0
    ? { until: `Valid until ${time}`, relative: `(in ${minutes} min)`, expired: false }
    : { until: `Expired at ${time}`, relative: '', expired: true };
}

export function formatTransitDays(days: { readonly minDays: number; readonly maxDays: number }): string {
  if (days.minDays === days.maxDays) {
    return `${days.minDays} ${days.minDays === 1 ? 'day' : 'days'}`;
  }
  return `${days.minDays} to ${days.maxDays} days`;
}

export function formatGrams(grams: number): string {
  return `${new Intl.NumberFormat(LOCALE).format(grams)} g`;
}

export interface Dimensions {
  readonly lengthMm: number;
  readonly widthMm: number;
  readonly heightMm: number;
}

export function formatDimensions(parcel: Dimensions): string {
  return `${parcel.lengthMm} × ${parcel.widthMm} × ${parcel.heightMm} mm`;
}

/** "NO 0150" */
export function formatPlace(place: { readonly countryCode: string; readonly postcode: string }): string {
  return `${place.countryCode} ${place.postcode}`;
}

export function formatServiceLevel(level: string): string {
  return new Map(Object.entries(SERVICE_LEVEL_LABELS)).get(level) ?? level;
}

export function formatStatus(status: string): string {
  return new Map(Object.entries(STATUS_LABELS)).get(status) ?? status;
}
