import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, token as tokenStore, bankCredential } from './api.js';

/**
 * Who is signed in, and what the server's OPRF parameters are.
 *
 * The public key is loaded once here rather than per screen. It is what every
 * DLEQ proof gets checked against, so a single fetch keeps the whole session
 * pinned to one key: if the server started answering under a different one
 * mid-session, enrolment would fail verification instead of quietly producing
 * pseudonyms nobody else can match.
 */

const SessionContext = createContext(null);

export function SessionProvider({ children }) {
  const [profile, setProfile] = useState(null);
  const [parameters, setParameters] = useState(null);
  const [status, setStatus] = useState('loading');
  const [bank, setBank] = useState(() => bankCredential.get());

  const loadProfile = useCallback(async () => {
    if (!tokenStore.get()) {
      setProfile(null);
      setStatus('anonymous');
      return;
    }
    try {
      setProfile(await api.me.profile());
      setStatus('ready');
    } catch {
      tokenStore.clear();
      setProfile(null);
      setStatus('anonymous');
    }
  }, []);

  useEffect(() => { loadProfile(); }, [loadProfile]);

  useEffect(() => {
    // Unauthenticated, so it also doubles as the reachability check the sign-in
    // screen uses before anyone types a password.
    api.oprf.publicKey().then(setParameters).catch(() => setParameters(null));
  }, []);

  const signIn = useCallback(async (email, password) => {
    const { token } = await api.auth.signIn(email, password);
    tokenStore.set(token);
    await loadProfile();
  }, [loadProfile]);

  const signOut = useCallback(() => {
    tokenStore.clear();
    bankCredential.clear();
    setBank(null);
    setProfile(null);
    setStatus('anonymous');
  }, []);

  const attachBank = useCallback((credential) => {
    bankCredential.set(credential);
    setBank(credential);
  }, []);

  const detachBank = useCallback(() => {
    bankCredential.clear();
    setBank(null);
  }, []);

  const value = useMemo(() => ({
    profile,
    parameters,
    status,
    bank,
    signIn,
    signOut,
    attachBank,
    detachBank,
    refreshProfile: loadProfile,
    isSuperAdmin: profile?.role === 'SUPER_ADMIN',
  }), [profile, parameters, status, bank, signIn, signOut, attachBank, detachBank, loadProfile]);

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession() {
  const context = useContext(SessionContext);
  if (!context) throw new Error('useSession must be used inside a SessionProvider');
  return context;
}
