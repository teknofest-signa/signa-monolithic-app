import { useState } from 'react';
import { CircleUserRound, UserMinus, UserPlus } from 'lucide-react';
import { api } from '../lib/api.js';
import { useSession } from '../lib/session.jsx';
import {
  Button, Empty, Field, Input, Loading, Notice, Page, Pill, Section, Select,
  Sheet, Table, adminTone, useResource, useToast,
} from '../ui/kit.jsx';
import { formatDate, humanise } from '../lib/format.js';

export default function Operators() {
  const toast = useToast();
  const { profile } = useSession();
  const admins = useResource(() => api.admins.list(), []);

  const [inviting, setInviting] = useState(false);
  const [removing, setRemoving] = useState(null);
  const [working, setWorking] = useState(false);

  async function confirmRemove() {
    setWorking(true);
    try {
      await api.admins.remove(removing.id);
      toast.say(`${removing.username} no longer has access.`);
      setRemoving(null);
      admins.reload();
    } catch (failure) {
      toast.warn(failure.message);
    } finally {
      setWorking(false);
    }
  }

  return (
    <Page
      title="Operators"
      eyebrow="Administration"
      icon={CircleUserRound}
      lede="Who can sign in to this console, and what each of them is allowed to change."
      actions={<Button variant="primary" icon={UserPlus} onClick={() => setInviting(true)}>Invite an operator</Button>}
    >
      <Section title={`${(admins.data ?? []).length} with access`} icon={CircleUserRound}>
        {admins.loading ? (
          <Loading label="Reading the roster" />
        ) : admins.error ? (
          <Notice tone="stop">{admins.error}</Notice>
        ) : (admins.data ?? []).length === 0 ? (
          <Empty icon={CircleUserRound} title="Nobody else has access" note="Invite a colleague to share the console." />
        ) : (
          <Table columns={['Name', 'Email', 'Role', 'Status', 'Since', '']}>
            {admins.data.map((admin) => (
              <tr key={admin.id}>
                <td className="lead">
                  {admin.username}
                  {admin.id === profile?.id && <span className="faint" style={{ fontWeight: 400 }}> — you</span>}
                </td>
                <td className="muted">{admin.email}</td>
                <td className="muted">{admin.role === 'SUPER_ADMIN' ? 'Super administrator' : 'Administrator'}</td>
                <td><Pill tone={adminTone(admin.status)}>{humanise(admin.status)}</Pill></td>
                <td className="muted num">{formatDate(admin.createdAt)}</td>
                <td>
                  {admin.id !== profile?.id && (
                    <Button size="sm" variant="danger" icon={UserMinus} onClick={() => setRemoving(admin)}>Revoke</Button>
                  )}
                </td>
              </tr>
            ))}
          </Table>
        )}
      </Section>

      {inviting && (
        <InviteOperator
          onClose={() => setInviting(false)}
          onInvited={() => { setInviting(false); admins.reload(); toast.say('Invitation sent.'); }}
        />
      )}

      {removing && (
        <Sheet
          title="Revoke access"
          lede={`${removing.username}, ${removing.email}`}
          onClose={() => setRemoving(null)}
          footer={(
            <>
              <Button onClick={() => setRemoving(null)} disabled={working}>Cancel</Button>
              <Button variant="danger" icon={UserMinus} onClick={confirmRemove} busy={working}>Revoke access</Button>
            </>
          )}
        >
          <Notice tone="warn">Access is revoked immediately. Their records remain.</Notice>
        </Sheet>
      )}
    </Page>
  );
}

function InviteOperator({ onClose, onInvited }) {
  const [form, setForm] = useState({ email: '', username: '', role: 'ADMIN' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const set = (key) => (event) => setForm((current) => ({ ...current, [key]: event.target.value }));

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.admins.invite({ ...form, email: form.email.trim(), username: form.username.trim() });
      onInvited();
    } catch (failure) {
      setError(failure.message);
      setBusy(false);
    }
  }

  return (
    <Sheet
      title="Invite an operator"
      lede="They set their own password from the emailed link."
      onClose={onClose}
      footer={(
        <>
          <Button onClick={onClose} disabled={busy}>Cancel</Button>
          <Button
            variant="primary"
            icon={UserPlus}
            onClick={submit}
            busy={busy}
            disabled={!form.email.trim() || !form.username.trim()}
          >
            Send invitation
          </Button>
        </>
      )}
    >
      {error && <Notice tone="stop">{error}</Notice>}

      <form onSubmit={submit}>
        <Field label="Name">
          <Input value={form.username} onChange={set('username')} placeholder="Nigar Əliyeva" disabled={busy} required />
        </Field>

        <Field label="Work email">
          <Input type="email" value={form.email} onChange={set('email')} placeholder="nigar@signa.az" disabled={busy} required />
        </Field>

        <Field label="Role" note="Super administrators can rotate bank keys and suspend access.">
          <Select value={form.role} onChange={set('role')} disabled={busy}>
            <option value="ADMIN">Administrator</option>
            <option value="SUPER_ADMIN">Super administrator</option>
          </Select>
        </Field>
      </form>
    </Sheet>
  );
}
