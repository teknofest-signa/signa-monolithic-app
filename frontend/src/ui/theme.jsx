import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

/**
 * Light and dark, remembered.
 *
 * The first paint is handled by an inline script in index.html; this provider
 * only takes over afterwards so the two never disagree. Operators who have
 * never chosen follow the system, and stop following it the moment they do.
 */

const KEY = 'signa.theme';
const ThemeContext = createContext(null);

const systemTheme = () =>
  (window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');

const stored = () => {
  try {
    const value = localStorage.getItem(KEY);
    return value === 'dark' || value === 'light' ? value : null;
  } catch {
    return null;
  }
};

export function ThemeProvider({ children }) {
  const [choice, setChoice] = useState(stored);
  const theme = choice ?? (typeof window === 'undefined' ? 'light' : systemTheme());

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
  }, [theme]);

  useEffect(() => {
    if (choice) return undefined;
    const media = window.matchMedia('(prefers-color-scheme: dark)');
    const sync = () => setChoice(null);
    media.addEventListener('change', sync);
    return () => media.removeEventListener('change', sync);
  }, [choice]);

  const toggle = useCallback(() => {
    setChoice((current) => {
      const next = (current ?? systemTheme()) === 'dark' ? 'light' : 'dark';
      try {
        localStorage.setItem(KEY, next);
      } catch {
        /* private browsing; the choice just will not survive the reload */
      }
      return next;
    });
  }, []);

  const value = useMemo(() => ({ theme, toggle }), [theme, toggle]);

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('useTheme must be used inside a ThemeProvider');
  return context;
}
