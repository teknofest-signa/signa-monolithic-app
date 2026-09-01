import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import { Building2, KeyRound, LogOut, Moon, Sun, UserRound } from 'lucide-react';
import { useSession } from '../lib/session.jsx';
import { useTheme } from './theme.jsx';
import { abbreviateHex, initials } from '../lib/format.js';
import { Dock, DockIcon, DockItem, DockLabel } from './Dock.jsx';
import { DESTINATIONS } from './nav.js';
import Mark from './Mark.jsx';

/**
 * Chrome, top and bottom.
 *
 * The bar carries identity and the two facts that have to stay visible —
 * which OPRF key is live, and which bank is being acted for — because
 * pseudonyms only match within one key, so a changed fingerprint is the first
 * thing worth noticing when matching stops working. Navigation lives in the
 * dock at the foot of the window, which leaves the full width to the work.
 */

export default function Shell() {
  const { profile, parameters, bank, signOut, isSuperAdmin } = useSession();
  const { theme, toggle } = useTheme();
  const location = useLocation();
  const navigate = useNavigate();

  const destinations = DESTINATIONS.filter((item) => !item.superAdminOnly || isSuperAdmin);

  const isLive = (item) =>
    (item.end ? location.pathname === item.to : location.pathname.startsWith(item.to));

  // Plain left-clicks route in place; anything modified is left to the browser
  // so an operator can still open a section in a new tab.
  const follow = (to) => (event) => {
    if (event.metaKey || event.ctrlKey || event.shiftKey || event.button !== 0) return;
    event.preventDefault();
    navigate(to);
  };

  return (
    <div className="shell">
      <header className="topbar">
        <Link to="/" className="brand" aria-label="Signa, overview">
          <span className="brand-glyph"><Mark size={15} /></span>
          <span className="brand-mark">SIGNA</span>
        </Link>

        <div className="topbar-meta">
          <span className="chip optional" title="Active OPRF key">
            <KeyRound size={13} strokeWidth={1.9} />
            <span className="mono">{parameters ? parameters.activeKeyId : 'unavailable'}</span>
          </span>

          {bank && (
            <span className="chip optional" title="Acting for this member bank">
              <Building2 size={13} strokeWidth={1.9} />
              <span className="mono">{abbreviateHex(bank.clientId, 8, 4)}</span>
            </span>
          )}

          <Link to="/account" className="topbar-user" title="Your account">
            <span className="avatar">{initials(profile?.username ?? profile?.email)}</span>
            <span>
              <span className="topbar-user-name">
                {profile?.username ?? profile?.email ?? 'Operator'}
              </span>
              <span className="topbar-user-role">
                {profile?.role === 'SUPER_ADMIN' ? 'Super administrator' : 'Administrator'}
              </span>
            </span>
          </Link>

          <button type="button" className="btn btn-quiet btn-sm" onClick={signOut} title="Sign out">
            <LogOut size={14} strokeWidth={1.9} />
          </button>
        </div>
      </header>

      <main className="main">
        {/* A short cross-fade on route change: enough to tell the operator the
            page changed, short enough never to be in the way. */}
        <AnimatePresence mode="wait">
          <motion.div
            key={location.pathname}
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -4 }}
            transition={{ duration: 0.18, ease: [0.22, 0.8, 0.3, 1] }}
          >
            <Outlet />
          </motion.div>
        </AnimatePresence>
      </main>

      <Dock>
        {destinations.map((item) => {
          const Icon = item.icon;
          return (
            <DockItem
              key={item.to}
              href={item.to}
              onClick={follow(item.to)}
              active={isLive(item)}
            >
              <DockLabel>{item.label}</DockLabel>
              <DockIcon><Icon strokeWidth={1.75} /></DockIcon>
            </DockItem>
          );
        })}

        <span className="dock-sep" aria-hidden="true" />

        <DockItem href="/account" onClick={follow('/account')} active={isLive({ to: '/account' })}>
          <DockLabel>Your account</DockLabel>
          <DockIcon><UserRound strokeWidth={1.75} /></DockIcon>
        </DockItem>

        <DockItem onClick={toggle} aria-label="Switch theme">
          <DockLabel>{theme === 'dark' ? 'Light mode' : 'Dark mode'}</DockLabel>
          <DockIcon>{theme === 'dark' ? <Sun strokeWidth={1.75} /> : <Moon strokeWidth={1.75} />}</DockIcon>
        </DockItem>
      </Dock>
    </div>
  );
}
