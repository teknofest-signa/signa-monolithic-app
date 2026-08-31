import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

/* --------------------------------------------------------------- basics -- */

export function Button({ variant = 'ghost', size, busy, children, ...rest }) {
  const classes = ['btn', `btn-${variant}`, size ? `btn-${size}` : '', busy ? 'is-busy' : '']
    .filter(Boolean)
    .join(' ');

  return (
    <button type="button" className={classes} disabled={rest.disabled || busy} {...rest}>
      {busy && <span className={`spin${variant === 'primary' ? ' on-ink' : ''}`} />}
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

export function Notice({ tone, title, children }) {
  return (
    <div className={`notice${tone ? ` ${tone}` : ''}`}>
      {title && <div className="notice-title">{title}</div>}
      {children}
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

export function Empty({ title, note, action }) {
  return (
    <div className="empty">
      <div className="empty-title">{title}</div>
      {note && <div className="empty-note">{note}</div>}
      {action && <div style={{ marginTop: 20 }}>{action}</div>}
    </div>
  );
}

export function Loading({ label = 'Loading' }) {
  return (
    <div className="empty">
      <span className="spin" />
      <div className="empty-note" style={{ marginTop: 12 }}>{label}</div>
    </div>
  );
}

/* --------------------------------------------------------------- layout -- */

export function Page({ title, lede, actions, children }) {
  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1 className="display page-title">{title}</h1>
          {lede && <p className="page-lede">{lede}</p>}
        </div>
        {actions && <div className="row">{actions}</div>}
      </header>
      {children}
    </div>
  );
}

export function Section({ title, note, actions, children }) {
  return (
    <section className="section">
      {(title || actions) && (
        <div className="section-head">
          <div>
            {title && <h2 className="section-title">{title}</h2>}
            {note && <p className="field-note" style={{ marginTop: 4 }}>{note}</p>}
          </div>
          {actions && <div className="row">{actions}</div>}
        </div>
      )}
      {children}
    </section>
  );
}

export function Figure({ label, value, note }) {
  return (
    <div className="figure">
      <div className="label">{label}</div>
      <div className="figure-value">{value}</div>
      {note && <div className="figure-note">{note}</div>}
    </div>
  );
}

export function Table({ columns, children }) {
  return (
    <table className="table">
      <thead>
        <tr>{columns.map((column) => <th key={column}>{column}</th>)}</tr>
      </thead>
      <tbody>{children}</tbody>
    </table>
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
          <div key={toast.id} className={`toast${toast.tone ? ` ${toast.tone}` : ''}`}>{toast.message}</div>
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
