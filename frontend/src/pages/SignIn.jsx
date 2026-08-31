import { useState } from 'react';
import { useSession } from '../lib/session.jsx';
import { api } from '../lib/api.js';
import { Button, Field, Input, Notice } from '../ui/kit.jsx';

export default function SignIn() {
  const { signIn, parameters } = useSession();
  const [mode, setMode] = useState('signIn');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      if (mode === 'signIn') {
        await signIn(email.trim(), password);
      } else {
        await api.auth.forgotPassword(email.trim());
        setSent(true);
      }
    } catch (failure) {
      setError(failure.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="gate">
      <div className="gate-card">
        <div className="gate-mark">SIGNA</div>

        <div className="gate-panel">
          {error && <Notice tone="stop">{error}</Notice>}
          {sent && <Notice tone="ok">Reset link sent, if that address has an account.</Notice>}
          {!parameters && !error && (
            <Notice tone="warn">Producer service unreachable.</Notice>
          )}

          <form onSubmit={submit}>
            <Field label="Email">
              <Input
                type="email"
                autoComplete="username"
                autoFocus
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
            </Field>

            {mode === 'signIn' && (
              <Field label="Password">
                <Input
                  type="password"
                  autoComplete="current-password"
                  required
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                />
              </Field>
            )}

            <Button
              type="submit"
              variant="primary"
              size="lg"
              busy={busy}
              style={{ width: '100%', marginTop: 4 }}
            >
              {mode === 'signIn' ? 'Sign in' : 'Send reset link'}
            </Button>
          </form>

          <div className="divider" style={{ margin: '20px 0 16px' }} />

          <button
            type="button"
            className="btn btn-quiet btn-sm"
            style={{ padding: 0 }}
            onClick={() => { setMode(mode === 'signIn' ? 'forgot' : 'signIn'); setError(null); setSent(false); }}
          >
            {mode === 'signIn' ? 'Forgot password' : 'Back to sign in'}
          </button>
        </div>

        {/* The key fingerprint is operational, not decorative: pseudonyms only
            match within one key, so an operator checks it against what their
            institution was issued. */}
        <div className="gate-foot">
          <span>{window.location.host}</span>
          <span className="mono" style={{ fontSize: 12 }}>
            {parameters ? `${parameters.ciphersuite} · ${parameters.activeKeyId}` : '—'}
          </span>
        </div>
      </div>
    </div>
  );
}
