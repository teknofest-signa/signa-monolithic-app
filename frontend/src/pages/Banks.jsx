import { useState } from 'react';
import { api } from '../lib/api.js';
import { useSession } from '../lib/session.jsx';
import {
  Button, CopyValue, Empty, Field, Input, Loading, Notice, Page, Pill,
  Section, Sheet, Table, useResource, useToast,
} from '../ui/kit.jsx';
import { formatDate } from '../lib/format.js';

export default function Banks() {
  const toast = useToast();
  const { bank: attached, attachBank, detachBank, isSuperAdmin } = useSession();
  const banks = useResource(() => api.banks.list(), []);

  const [registering, setRegistering] = useState(false);
  const [issued, setIssued] = useState(null);
  const [attaching, setAttaching] = useState(null);
  const [removing, setRemoving] = useState(null);
  const [working, setWorking] = useState(false);

  async function rotate(bank) {
    setWorking(true);
    try {
      setIssued(await api.banks.rotateKey(bank.id));
      toast.say(`${bank.name}: new key issued, previous key revoked.`);
      banks.reload();
    } catch (failure) {
      toast.warn(failure.message);
    } finally {
      setWorking(false);
    }
  }

  async function toggleAccess(bank) {
    try {
      await api.banks.setOprfAccess(bank.id, !bank.oprfEnabled);
      toast.say(bank.oprfEnabled
        ? `${bank.name} suspended from the privacy layer.`
        : `${bank.name} restored.`);
      banks.reload();
    } catch (failure) {
      toast.warn(failure.message);
    }
  }

  async function confirmRemove() {
    setWorking(true);
    try {
      await api.banks.remove(removing.id);
      toast.say(`${removing.name} removed.`);
      setRemoving(null);
      banks.reload();
    } catch (failure) {
      toast.warn(failure.message);
    } finally {
      setWorking(false);
    }
  }

  return (
    <Page
      title="Member banks"
      actions={<Button variant="primary" onClick={() => setRegistering(true)}>Register a bank</Button>}
    >
      {attached && (
        <Notice tone="ok">
          <div className="spread">
            <span>Acting for <span className="mono">{attached.clientId}</span></span>
            <Button size="sm" onClick={() => { detachBank(); toast.say('Credential cleared.'); }}>
              Clear
            </Button>
          </div>
        </Notice>
      )}

      <Section title={`${(banks.data ?? []).length} registered`}>
        {banks.loading ? (
          <Loading label="Reading the member list" />
        ) : banks.error ? (
          <Notice tone="stop">{banks.error}</Notice>
        ) : (banks.data ?? []).length === 0 ? (
          <Empty
            title="No member banks"
            action={<Button variant="primary" size="sm" onClick={() => setRegistering(true)}>Register a bank</Button>}
          />
        ) : (
          <Table columns={['Bank', 'Client id', 'OPRF access', 'Joined', '']}>
            {banks.data.map((bank) => (
              <tr key={bank.id}>
                <td className="lead">{bank.name}</td>
                <td className="mono faint">{bank.clientId ?? '—'}</td>
                <td>
                  <Pill tone={bank.oprfEnabled ? 'ok' : 'stop'}>
                    {bank.oprfEnabled ? 'Enabled' : 'Suspended'}
                  </Pill>
                </td>
                <td className="muted num">{formatDate(bank.createdAt)}</td>
                <td>
                  <div className="row" style={{ justifyContent: 'flex-end' }}>
                    <Button size="sm" onClick={() => setAttaching(bank)}>Use credential</Button>
                    {isSuperAdmin && (
                      <>
                        <Button size="sm" onClick={() => rotate(bank)} disabled={working}>Rotate key</Button>
                        <Button size="sm" onClick={() => toggleAccess(bank)}>
                          {bank.oprfEnabled ? 'Suspend' : 'Restore'}
                        </Button>
                        <Button size="sm" variant="danger" onClick={() => setRemoving(bank)}>Remove</Button>
                      </>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </Table>
        )}
      </Section>

      {registering && (
        <RegisterBank
          onClose={() => setRegistering(false)}
          onRegistered={(credentials) => { setRegistering(false); setIssued(credentials); banks.reload(); }}
        />
      )}

      {issued && <IssuedCredential credentials={issued} onClose={() => setIssued(null)} onUse={attachBank} />}

      {attaching && (
        <AttachCredential
          bank={attaching}
          onClose={() => setAttaching(null)}
          onAttach={(credential) => {
            attachBank(credential);
            setAttaching(null);
            toast.say(`Acting for ${attaching.name}.`);
          }}
        />
      )}

      {removing && (
        <Sheet
          title="Remove this bank"
          lede={removing.name}
          onClose={() => setRemoving(null)}
          footer={(
            <>
              <Button onClick={() => setRemoving(null)} disabled={working}>Cancel</Button>
              <Button variant="danger" onClick={confirmRemove} busy={working}>Remove from the network</Button>
            </>
          )}
        >
          <Notice tone="stop" title="Cannot be undone">
            Deletes the institution and its credential. To stop evaluations only, suspend
            access instead and the audit trail stays intact.
          </Notice>
        </Sheet>
      )}
    </Page>
  );
}

function RegisterBank({ onClose, onRegistered }) {
  const [name, setName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      onRegistered(await api.banks.create(name.trim()));
    } catch (failure) {
      setError(failure.message);
      setBusy(false);
    }
  }

  return (
    <Sheet
      title="Register a member bank"
      lede="Credentials are issued immediately and shown once."
      onClose={onClose}
      footer={(
        <>
          <Button onClick={onClose} disabled={busy}>Cancel</Button>
          <Button variant="primary" onClick={submit} busy={busy} disabled={!name.trim()}>Register</Button>
        </>
      )}
    >
      {error && <Notice tone="stop">{error}</Notice>}
      <form onSubmit={submit}>
        <Field label="Institution name">
          <Input
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Kapital Bank"
            disabled={busy}
            required
          />
        </Field>
      </form>
    </Sheet>
  );
}

function IssuedCredential({ credentials, onClose, onUse }) {
  return (
    <Sheet
      title="Credential issued"
      lede={credentials.name}
      onClose={onClose}
      footer={(
        <>
          <Button onClick={onClose}>Close</Button>
          <Button
            variant="primary"
            onClick={() => {
              onUse({ clientId: credentials.clientId, apiKey: credentials.apiKey });
              onClose();
            }}
          >
            Use it in this tab
          </Button>
        </>
      )}
    >
      <Notice tone="warn" title="Shown once">
        Only a digest is stored. A lost key must be rotated, not recovered.
      </Notice>

      <div className="label" style={{ marginBottom: 8 }}>Client id</div>
      <CopyValue value={credentials.clientId} className="mono" />

      <div style={{ height: 20 }} />

      <div className="label" style={{ marginBottom: 8 }}>API key</div>
      <CopyValue value={credentials.apiKey} />
    </Sheet>
  );
}

function AttachCredential({ bank, onClose, onAttach }) {
  const [apiKey, setApiKey] = useState('');

  return (
    <Sheet
      title="Act for this bank"
      lede={bank.name}
      onClose={onClose}
      footer={(
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button
            variant="primary"
            disabled={!apiKey.trim()}
            onClick={() => onAttach({ clientId: bank.clientId, apiKey: apiKey.trim() })}
          >
            Hold for this tab
          </Button>
        </>
      )}
    >
      <Notice>
        Held in session storage for this tab only. Sent to the evaluation endpoint,
        never with your operator token.
      </Notice>

      <Field label="Client id">
        <Input mono value={bank.clientId ?? ''} readOnly />
      </Field>

      <Field label="API key">
        <Input
          mono
          type="password"
          value={apiKey}
          onChange={(event) => setApiKey(event.target.value)}
          placeholder="signa_sk_…"
          autoComplete="off"
        />
      </Field>
    </Sheet>
  );
}
