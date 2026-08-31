import { NavLink, Outlet } from 'react-router-dom';
import { useSession } from '../lib/session.jsx';
import { abbreviateHex, initials } from '../lib/format.js';

const SECTIONS = [
  {
    label: 'Network',
    items: [
      { to: '/', end: true, label: 'Overview' },
      { to: '/customers', label: 'Customers' },
      { to: '/banks', label: 'Member banks' },
    ],
  },
  {
    label: 'Controls',
    items: [
      { to: '/privacy', label: 'Privacy layer' },
      { to: '/risk', label: 'Risk simulator' },
    ],
  },
  {
    label: 'Administration',
    items: [
      { to: '/operators', label: 'Operators', superAdminOnly: true },
      { to: '/account', label: 'Your account' },
    ],
  },
];

export default function Shell() {
  const { profile, parameters, bank, signOut, isSuperAdmin } = useSession();

  return (
    <div className="shell">
      <nav className="sidebar">
        <div className="brand">
          <div className="brand-mark">SIGNA</div>
        </div>

        <div className="nav">
          {SECTIONS.map((section) => {
            const items = section.items.filter((item) => !item.superAdminOnly || isSuperAdmin);
            if (items.length === 0) return null;

            return (
              <div className="nav-group" key={section.label}>
                <span className="label">{section.label}</span>
                {items.map((item) => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.end}
                    className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
                  >
                    {item.label}
                  </NavLink>
                ))}
              </div>
            );
          })}
        </div>

        <div className="sidebar-foot">
          {/* The key fingerprint sits where the operator can always see it.
              Pseudonyms only match within one key, so a changed fingerprint is
              the first thing worth noticing when matching stops working. */}
          <div className="label" style={{ marginBottom: 6 }}>Active OPRF key</div>
          <div className="mono faint" style={{ marginBottom: 16 }}>
            {parameters ? parameters.activeKeyId : 'unavailable'}
          </div>

          {bank && (
            <div style={{ marginBottom: 16 }}>
              <div className="label" style={{ marginBottom: 6 }}>Acting for</div>
              <div className="mono faint">{abbreviateHex(bank.clientId, 12, 4)}</div>
            </div>
          )}

          <div className="spread">
            <div className="grow" style={{ minWidth: 0 }}>
              <div style={{ fontSize: 13.5, fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {profile?.username ?? initials(profile?.email)}
              </div>
              <div className="faint" style={{ fontSize: 11.5 }}>
                {profile?.role === 'SUPER_ADMIN' ? 'Super administrator' : 'Administrator'}
              </div>
            </div>
            <button type="button" className="btn btn-quiet btn-sm" onClick={signOut}>Sign out</button>
          </div>
        </div>
      </nav>

      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}
