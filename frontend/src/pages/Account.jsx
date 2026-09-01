import { Building2, Network, UserRound } from 'lucide-react';
import { useSession } from '../lib/session.jsx';
import { Loading, Page, Pill, Section, adminTone } from '../ui/kit.jsx';
import { formatDateTime, humanise, imageFromBytes } from '../lib/format.js';

export default function Account() {
  const { profile, bank, detachBank, parameters } = useSession();

  if (!profile) return <Page title="Your account" eyebrow="Administration" icon={UserRound}><Loading /></Page>;

  const photo = imageFromBytes(profile.profilePhoto);

  return (
    <Page
      title="Your account"
      eyebrow="Administration"
      icon={UserRound}
      lede="Your operator record, the credential this tab is holding, and the network it is pointed at."
    >
      <Section title="Operator" icon={UserRound}>
        <div className="panel">
          <div className="panel-body">
            <div className="row" style={{ gap: 20, marginBottom: 24, alignItems: 'center' }}>
              {photo ? (
                <img
                  src={photo}
                  alt=""
                  style={{ width: 56, height: 56, borderRadius: '50%', objectFit: 'cover' }}
                />
              ) : (
                <div
                  className="display"
                  style={{
                    width: 56, height: 56, borderRadius: '50%', background: 'var(--surface-2)',
                    border: '1px solid var(--rule)', fontWeight: 600, letterSpacing: '-0.02em',
                    display: 'grid', placeItems: 'center', fontSize: 21,
                  }}
                >
                  {(profile.username ?? profile.email ?? '?').slice(0, 1).toUpperCase()}
                </div>
              )}
              <div>
                <div style={{ fontSize: 17, fontWeight: 500 }}>{profile.username}</div>
                <div className="muted">{profile.email}</div>
              </div>
            </div>

            <dl className="kv">
              <dt>Role</dt>
              <dd>{profile.role === 'SUPER_ADMIN' ? 'Super administrator' : 'Administrator'}</dd>
              <dt>Status</dt>
              <dd><Pill tone={adminTone(profile.status)}>{humanise(profile.status)}</Pill></dd>
              <dt>Member since</dt>
              <dd className="num">{formatDateTime(profile.createdAt)}</dd>
            </dl>
          </div>
        </div>
      </Section>

      <Section title="This tab" icon={Building2}>
        <div className="panel">
          <div className="panel-body">
            {bank ? (
              <>
                <dl className="kv" style={{ marginBottom: 20 }}>
                  <dt>Client id</dt>
                  <dd className="mono">{bank.clientId}</dd>
                  <dt>API key</dt>
                  <dd className="mono faint">held, not displayed</dd>
                </dl>
                <button type="button" className="btn btn-ghost btn-sm" onClick={detachBank}>
                  Clear credential
                </button>
              </>
            ) : (
              <p className="muted" style={{ fontSize: 14.5 }}>
                No member bank credential held. Enrolment and attestation need one.
              </p>
            )}
          </div>
        </div>
      </Section>

      {parameters && (
        <Section title="Connected network" icon={Network}>
          <div className="panel panel-warm">
            <div className="panel-body">
              <dl className="kv">
                <dt>Ciphersuite</dt>
                <dd className="mono">{parameters.ciphersuite}</dd>
                <dt>Active key</dt>
                <dd className="mono">{parameters.activeKeyId}</dd>
              </dl>
            </div>
          </div>
        </Section>
      )}


    </Page>
  );
}
