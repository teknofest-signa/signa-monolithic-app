import { useState } from 'react';
import { Link } from 'react-router-dom';
import { BadgeCheck, FileKey, ShieldCheck, SlidersHorizontal, TriangleAlert } from 'lucide-react';
import { api } from '../lib/api.js';
import { useSession } from '../lib/session.jsx';
import {
  Button, CopyValue, Loading, Notice, Page, Pill, Section, Table, useResource,
} from '../ui/kit.jsx';
import {
  blind, bytesToHex, canonicalize, deserializeElement, deserializeProof,
  hexToBytes, IDENTIFIER_TYPES, serializeElement, verifyProof,
} from '../lib/oprf.js';

/**
 * OPRF parameters and a live check of them.
 *
 * The attestation is not a status light: it runs a real evaluation against a
 * throwaway input and verifies the proof in this browser, so a red result means
 * something is actually wrong rather than that a health check is misconfigured.
 */
export default function PrivacyLayer() {
  const { bank } = useSession();
  const parameters = useResource(() => api.oprf.publicKey(), []);
  const [attestation, setAttestation] = useState(null);
  const [checking, setChecking] = useState(false);

  async function attest() {
    setChecking(true);
    setAttestation(null);
    try {
      const probe = canonicalize(IDENTIFIER_TYPES.AZ_FIN, `probe-${crypto.randomUUID()}`);
      const { blindedElement } = await blind(probe);
      const blindedHex = bytesToHex(serializeElement(blindedElement));

      const started = performance.now();
      const evaluation = await api.oprf.evaluate([blindedHex]);
      const elapsed = Math.round(performance.now() - started);

      const publicKey = deserializeElement(hexToBytes(evaluation.publicKey));
      const evaluatedElement = deserializeElement(hexToBytes(evaluation.evaluatedElements[0]));
      const proof = deserializeProof(evaluation.proof);

      const verified = await verifyProof(publicKey, [blindedElement], [evaluatedElement], proof);
      const matchesPublished = evaluation.publicKey.toLowerCase()
        === (parameters.data?.activePublicKey ?? '').toLowerCase();

      setAttestation({ verified, matchesPublished, elapsed, keyId: evaluation.keyId });
    } catch (failure) {
      setAttestation({ error: failure.message });
    } finally {
      setChecking(false);
    }
  }

  if (parameters.loading) {
    return (
      <Page title="Privacy layer" eyebrow="Controls" icon={ShieldCheck}>
        <Loading label="Reading parameters" />
      </Page>
    );
  }

  if (parameters.error) {
    return (
      <Page title="Privacy layer" eyebrow="Controls" icon={ShieldCheck}>
        <Notice tone="stop">{parameters.error}</Notice>
      </Page>
    );
  }

  const data = parameters.data;
  const retired = (data.keys ?? []).filter((key) => !key.active);
  const healthy = attestation && !attestation.error && attestation.verified && attestation.matchesPublished;

  return (
    <Page
      title="Privacy layer"
      eyebrow="Controls"
      icon={ShieldCheck}
      lede="The parameters every pseudonym is derived under, and a live check that the server is still answering with the key it publishes."
      actions={(
        <Button variant="primary" icon={BadgeCheck} onClick={attest} busy={checking} disabled={!bank}>
          Run attestation
        </Button>
      )}
    >
      {!bank && (
        <Notice tone="warn">
          Attestation needs a member bank credential. Add one from <Link to="/banks">Member banks</Link>.
        </Notice>
      )}

      {attestation?.error && <Notice tone="stop">{attestation.error}</Notice>}

      {attestation && !attestation.error && (
        <Notice tone={healthy ? 'ok' : 'stop'} title={healthy ? 'Key confirmed' : 'Check failed'}>
          <dl className="kv" style={{ gridTemplateColumns: '180px 1fr', marginTop: 8 }}>
            <dt>Proof</dt>
            <dd>{attestation.verified ? 'Verified' : 'Did not verify'}</dd>
            <dt>Key</dt>
            <dd>{attestation.matchesPublished ? 'Matches published' : 'Differs from published'}</dd>
            <dt>Key id</dt>
            <dd className="mono">{attestation.keyId}</dd>
            <dt>Round trip</dt>
            <dd className="num">{attestation.elapsed} ms</dd>
          </dl>
        </Notice>
      )}

      <Section title="Parameters" icon={SlidersHorizontal}>
        <div className="panel">
          <div className="panel-body">
            <dl className="kv">
              <dt>Protocol</dt>
              <dd>VOPRF, RFC 9497</dd>
              <dt>Ciphersuite</dt>
              <dd className="mono">{data.ciphersuite}</dd>
              <dt>Hash to curve</dt>
              <dd className="mono">P256_XMD:SHA-256_SSWU_RO_</dd>
              <dt>Mode</dt>
              <dd className="mono">{data.mode}</dd>
              <dt>Active key</dt>
              <dd className="mono">{data.activeKeyId}</dd>
            </dl>

            <div className="divider" />

            <div className="label" style={{ marginBottom: 8 }}>Public key</div>
            <CopyValue value={data.activePublicKey} />
          </div>
        </div>
      </Section>

      <Section
        title="Key ring"
        icon={FileKey}
        actions={retired.length > 0 ? <Pill tone="warn">Rotation in progress</Pill> : null}
      >
        <Table columns={['Key id', 'Public key', 'Status']}>
          {(data.keys ?? []).map((key) => (
            <tr key={key.keyId}>
              <td className="lead mono">{key.keyId}</td>
              <td className="hex" style={{ maxWidth: 460 }}>{key.publicKey}</td>
              <td>
                <Pill tone={key.active ? 'ok' : 'mute'}>{key.active ? 'Active' : 'Superseded'}</Pill>
              </td>
            </tr>
          ))}
        </Table>
        {retired.length > 0 && (
          <p className="field-note" style={{ marginTop: 12 }}>
            Superseded keys stay evaluable so records enrolled under them remain matchable.
            Pseudonyms are only comparable within one key.
          </p>
        )}
      </Section>

      <Section
        title="Known limitations"
        icon={TriangleAlert}
        note="Written down because a privacy layer that only lists its guarantees is marketing."
      >
        <Table columns={['Limitation', 'Mitigation']}>
          <tr>
            <td className="lead">Key holder can brute-force stored pseudonyms</td>
            <td className="muted">Threshold or HSM-held key</td>
          </tr>
          <tr>
            <td className="lead">A member bank can probe the identifier space</td>
            <td className="muted">Per-bank quotas, evaluation audit trail</td>
          </tr>
          <tr>
            <td className="lead">Pseudonyms are personal data, not anonymous</td>
            <td className="muted">Retention and access controls still apply</td>
          </tr>
          <tr>
            <td className="lead">Rate limit counters are per process</td>
            <td className="muted">Move to Redis before scaling out</td>
          </tr>
        </Table>
      </Section>
    </Page>
  );
}
