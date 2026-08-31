/** Presentation helpers. Nothing here touches the network or the protocol. */

const DATE = new Intl.DateTimeFormat('en-GB', {
  day: '2-digit', month: 'short', year: 'numeric',
});

const DATE_TIME = new Intl.DateTimeFormat('en-GB', {
  day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
});

export const formatDate = (value) => (value ? DATE.format(new Date(value)) : '—');
export const formatDateTime = (value) => (value ? DATE_TIME.format(new Date(value)) : '—');

export function formatRelative(value) {
  if (!value) return '—';
  const seconds = Math.round((Date.now() - new Date(value).getTime()) / 1000);
  if (seconds < 60) return 'just now';
  if (seconds < 3600) return `${Math.floor(seconds / 60)} min ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} h ago`;
  if (seconds < 604800) return `${Math.floor(seconds / 86400)} d ago`;
  return formatDate(value);
}

export const formatMoney = (amount, currency = 'AZN') =>
  new Intl.NumberFormat('en-GB', {
    style: 'currency', currency, minimumFractionDigits: 2, maximumFractionDigits: 2,
  }).format(Number(amount ?? 0));

export const formatCount = (value) => new Intl.NumberFormat('en-GB').format(Number(value ?? 0));

/**
 * Shortens a hex value for display while keeping both ends, which is what an
 * operator actually compares when checking a key fingerprint by eye.
 */
export function abbreviateHex(hex, lead = 10, tail = 6) {
  if (!hex) return '—';
  if (hex.length <= lead + tail + 1) return hex;
  return `${hex.slice(0, lead)}…${hex.slice(-tail)}`;
}

/**
 * Readable labels for the backend enums.
 *
 * Naive title-casing turns ATM into "Atm" and BANK_API into "Bank api", which
 * reads as machine output. Anything with a real name gets one here.
 */
const LABELS = {
  ATM: 'ATM',
  BANK_API: 'Bank API',
  POINT_OF_SALE: 'Point of sale',
  WEB_BANKING: 'Web banking',
  MOBILE_APP: 'Mobile app',
  SUPER_ADMIN: 'Super administrator',
  ADMIN: 'Administrator',
};

/** Turns SCREAMING_SNAKE enum values into something readable. */
export const humanise = (value) => {
  if (!value) return '';
  const key = value.toString();
  if (LABELS[key]) return LABELS[key];
  return key.toLowerCase().replace(/_/g, ' ').replace(/^./, (c) => c.toUpperCase());
};

export const initials = (name) =>
  (name ?? '?')
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('');

/** The byte[] the backend serialises for logos and avatars arrives base64. */
export const imageFromBytes = (value) =>
  (value ? `data:image/*;base64,${value}` : null);
