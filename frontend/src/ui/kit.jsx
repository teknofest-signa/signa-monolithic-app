import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import {
  animate, motion, useMotionValue, useReducedMotion, useTransform,
} from 'framer-motion';
import { Check, CircleAlert, CircleCheck, Copy, Inbox, Info, TriangleAlert } from 'lucide-react';
import { formatCount } from '../lib/format.js';

/* --------------------------------------------------------------- basics -- */

export function Button({ variant = 'ghost', size, busy, icon: Icon, children, ...rest }) {
  const classes = ['btn', `btn-${variant}`, size ? `btn-${size}` : '', busy ? 'is-busy' : '']
    .filter(Boolean)
    .join(' ');

  return (
    <button type="button" className={classes} disabled={rest.disabled || busy} {...rest}>
      {busy
        ? <span className={`spin${variant === 'primary' ? ' on-ink' : ''}`} />
        : Icon && <Icon size={size === 'sm' ? 13 : 15} strokeWidth={1.9} />}
      {children}
    </button>
  );
}

export function Field({ label, note, error, children }) {
  return (
    <label className="field">
      <span className="label">{label}</span>
      {children}
      {error ? <span className="field-error">{error}</span> : note ? <span className="field-note">{note}</span> : null}
    </label>
  );
}

export function Input({ mono, ...rest }) {
  return <input className={`input${mono ? ' mono' : ''}`} {...rest} />;
}

export function Select({ children, ...rest }) {
  return <select className="select" {...rest}>{children}</select>;
}

/** The glyph carries the tone before the words do. */
const NOTICE_ICONS = { ok: CircleCheck, warn: TriangleAlert, stop: CircleAlert };

export function Notice({ tone, title, children }) {
  const Icon = NOTICE_ICONS[tone] ?? Info;

  return (
    <div className={`notice${tone ? ` ${tone}` : ''}`}>
      <Icon size={15} strokeWidth={1.9} />
      <div>
        {title && <div className="notice-title">{title}</div>}
        {children}
      </div>
    </div>
  );
}

/** Status word plus a dot. The dot carries the meaning at a glance; the word confirms it. */
export function Pill({ tone = 'mute', children }) {
  return <span className={`pill ${tone}`}>{children}</span>;
}

const CUSTOMER_TONES = { ACTIVE: 'ok', SUSPENDED: 'warn', BLOCKED: 'stop', INACTIVE: 'mute' };
const RISK_TONES = { APPROVE: 'ok', REVIEW: 'warn', BLOCK: 'stop' };
const ADMIN_TONES = { ACTIVE: 'ok', PENDING: 'warn', INACTIVE: 'mute' };

export const customerTone = (status) => CUSTOMER_TONES[status] ?? 'mute';
export const riskTone = (status) => RISK_TONES[status] ?? 'mute';
export const adminTone = (status) => ADMIN_TONES[status] ?? 'mute';

export function Empty({ icon: Icon = Inbox, title, note, action }) {
  return (
    <div className="empty">
      <div className="empty-glyph"><Icon size={19} strokeWidth={1.6} /></div>
      <div className="empty-title">{title}</div>
      {note && <div className="empty-note">{note}</div>}
      {action && <div style={{ marginTop: 20 }}>{action}</div>}
    </div>
  );
}

export function Loading({ label = 'Loading' }) {
  return (
    <div className="empty" style={{ border: 'none', background: 'none' }}>
      <span className="spin" />
      <div className="empty-note" style={{ marginTop: 12 }}>{label}</div>
    </div>
  );
}

/* --------------------------------------------------------------- layout -- */

export function Page({ title, lede, eyebrow, icon: Icon, actions, children }) {
  return (
    <div className="page">
      <header className="page-head">
        <div>
          {(eyebrow || Icon) && (
            <div className="page-eyebrow">
              {Icon && <Icon size={14} strokeWidth={1.9} />}
              {eyebrow && <span className="label">{eyebrow}</span>}
            </div>
          )}
          <h1 className="display page-title">{title}</h1>
          {lede && <p className="page-lede">{lede}</p>}
        </div>
        {actions && <div className="row">{actions}</div>}
      </header>
      {children}
    </div>
  );
}

export function Section({ title, icon: Icon, note, actions, children }) {
  return (
    <section className="section">
      {(title || actions) && (
        <div className="section-head">
          <div>
            {title && (
              <h2 className="section-title">
                {Icon && <Icon size={15} strokeWidth={1.9} />}
                {title}
              </h2>
            )}
            {note && <p className="field-note" style={{ marginTop: 4 }}>{note}</p>}
          </div>
          {actions && <div className="row">{actions}</div>}
        </div>
      )}
      {children}
    </section>
  );
}

/* -------------------------------------------------------------- figures -- */

/**
 * A number that counts up to itself once, so a figure reads as something being
 * measured rather than something printed. Honours the reduced-motion setting.
 */
export function Counter({ value, format = formatCount }) {
  const still = useReducedMotion();
  const raw = useMotionValue(still ? value : 0);
  const shown = useTransform(raw, (current) => format(Math.round(current)));

  useEffect(() => {
    if (still) {
      raw.set(value);
      return undefined;
    }
    const controls = animate(raw, value, { duration: 0.85, ease: [0.22, 0.8, 0.3, 1] });
    return () => controls.stop();
  }, [value, still, raw]);

  return <motion.span>{shown}</motion.span>;
}

/**
 * Recent movement, one bar per period. Counts are discrete, so bars are the
 * honest mark for them — an area chart implies a continuous quantity between
 * the points and turns sparse days into a smear. A series with nothing to say
 * — fewer than two periods — renders nothing at all.
 */
export function TrendBars({ points, label }) {
  if (!points || points.length < 2) return null;

  const peak = Math.max(...points, 1);
  const last = points.length - 1;

  return (
    <div className="trend" role="img" aria-label={label}>
      {points.map((value, index) => (
        <span
          // eslint-disable-next-line react/no-array-index-key
          key={index}
          className={index === last ? 'now' : undefined}
          style={{ height: `${Math.max(7, (value / peak) * 100)}%` }}
        />
      ))}
    </div>
  );
}

/**
 * Parts of one whole, at the proportions they actually hold. Three separate
 * counts tell you three numbers; one bar tells you the shape of the population.
 */
export function MixBar({ parts }) {
  const shown = parts.filter((part) => part.value > 0);
  if (shown.length === 0) return null;

  return (
    <>
      <div className="mix">
        {shown.map((part) => (
          <span
            key={part.label}
            className={`mix-part ${part.tone}`}
            style={{ flexGrow: part.value }}
            title={`${part.label}: ${formatCount(part.value)}`}
          />
        ))}
      </div>

      <ul className="mix-legend">
        {shown.map((part) => (
          <li key={part.label}>
            <i className={part.tone} />
            {part.label}
            <b>{formatCount(part.value)}</b>
          </li>
        ))}
      </ul>
    </>
  );
}

export function Table({ columns, children }) {
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>{columns.map((column) => <th key={column}>{column}</th>)}</tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}

/** Right-hand drawer. Closes on Escape, because forms that trap you are rude. */
export function Sheet({ title, lede, onClose, footer, children }) {
  useEffect(() => {
    const onKey = (event) => { if (event.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, [onClose]);

  return (
    <div className="scrim" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <aside className="sheet" role="dialog" aria-modal="true" aria-label={title}>
        <header className="sheet-head">
          <h2 className="sheet-title">{title}</h2>
          {lede && <p className="sheet-lede">{lede}</p>}
        </header>
        {children}
        {footer && <div className="sheet-foot">{footer}</div>}
      </aside>
    </div>
  );
}

/* --------------------------------------------------------------- toasts -- */

const ToastContext = createContext(null);

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  const push = useCallback((message, tone) => {
    const id = Math.random().toString(36).slice(2);
    setToasts((current) => [...current, { id, message, tone }]);
    setTimeout(() => setToasts((current) => current.filter((toast) => toast.id !== id)), 4200);
  }, []);

  const value = useMemo(() => ({
    say: (message) => push(message),
    warn: (message) => push(message, 'bad'),
  }), [push]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toast-dock">
        {toasts.map((toast) => (
          <div key={toast.id} className={`toast${toast.tone ? ` ${toast.tone}` : ''}`}>
            {toast.tone === 'bad'
              ? <CircleAlert size={15} strokeWidth={2} />
              : <CircleCheck size={15} strokeWidth={2} />}
            {toast.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) throw new Error('useToast must be used inside a ToastProvider');
  return context;
}

/* ---------------------------------------------------------------- hooks -- */

/** Loads once, exposes a reload, and never leaves a stale error on screen. */
export function useResource(loader, dependencies = []) {
  const [state, setState] = useState({ data: null, error: null, loading: true });

  const reload = useCallback(async () => {
    setState((current) => ({ ...current, loading: true, error: null }));
    try {
      setState({ data: await loader(), error: null, loading: false });
    } catch (error) {
      setState({ data: null, error: error.message ?? 'Something went wrong.', loading: false });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, dependencies);

  useEffect(() => { reload(); }, [reload]);

  return { ...state, reload };
}

/** A value the operator can lift out with one click, for keys and hex. */
export function CopyValue({ value, label = 'Copy', className = 'hex' }) {
  const [copied, setCopied] = useState(false);

  return (
    <div className="row" style={{ alignItems: 'flex-start', gap: 12 }}>
      <div className={`grow ${className}`}>{value}</div>
      <Button
        size="sm"
        icon={copied ? Check : Copy}
        onClick={() => {
          navigator.clipboard?.writeText(value);
          setCopied(true);
          setTimeout(() => setCopied(false), 1600);
        }}
      >
        {copied ? 'Copied' : label}
      </Button>
    </div>
  );
}
