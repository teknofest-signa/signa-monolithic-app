import { useState } from 'react';
import { Link } from 'react-router-dom';
import { CircleCheck, ScanSearch, ShieldAlert } from 'lucide-react';
import { api } from '../lib/api.js';
import { useSession } from '../lib/session.jsx';
import {
  Button, CopyValue, Field, Input, Notice, Page, Pill, Section, Select,
} from '../ui/kit.jsx';
import {
  IDENTIFIER_TYPES, blind, bytesToHex, canonicalize, deserializeElement,
  deserializeProof, finalize, hexToBytes, serializeElement,
} from '../lib/oprf.js';

const IDENTIFIER_LABELS = [
  { value: IDENTIFIER_TYPES.AZ_FIN, label: 'FIN (national ID card)' },
  { value: IDENTIFIER_TYPES.AZ_TIN, label: 'Taxpayer number' },
  { value: IDENTIFIER_TYPES.PASSPORT, label: 'Passport number' },
];

const STEPS = [
  { key: 'canon', name: 'Canonicalise' },
  { key: 'blind', name: 'Blind' },
  { key: 'evaluate', name: 'Evaluate' },
  { key: 'verify', name: 'Verify proof' },
  { key: 'finalize', name: 'Unblind' },
  { key: 'check', name: 'Check the network' },
];

/**
 * The manual version of the pre-transaction check.
 *
 * An operator types an identifier and learns whether the network is carrying
 * an adverse signal for that person — without the identifier leaving this tab,
 * and without learning which institution flagged them.
 *
 * The step list is the same one enrolment shows, and for the same reason: the
 * claim this platform makes is about where the identifier goes, and an
 * operator handling other people's national ID numbers should be able to watch
 * it not go anywhere. The last step is the only difference. Enrolment ends by
 * filing the pseudonym; this ends by asking about it.
 */
export default function Screening() {
  const { parameters, bank: bankCredential } = useSession();

  const [identifierType, setIdentifierType] = useState(IDENTIFIER_TYPES.AZ_FIN);
  const [identifier, setIdentifier] = useState('');

  const [stage, setStage] = useState(null);
  const [done, setDone] = useState([]);
  const [error, setError] = useState(null);
  const [trace, setTrace] = useState(null);
  const [result, setResult] = useState(null);

  const busy = stage !== null;
  const canSubmit = identifier.trim() && bankCredential && !busy;

  async function run(event) {
    event.preventDefault();
    setError(null);
    setDone([]);
    setTrace(null);
    setResult(null);

    try {
      setStage('canon');
      const input = canonicalize(identifierType, identifier);
      const canonical = new TextDecoder().decode(input);
      setDone((current) => [...current, 'canon']);

      setStage('blind');
      const { blind: blindScalar, blindedElement } = await blind(input);
      const blindedHex = bytesToHex(serializeElement(blindedElement));
      setTrace({ canonical, blindedHex });
      setDone((current) => [...current, 'blind']);

      setStage('evaluate');
      const evaluation = await api.oprf.evaluate([blindedHex]);
      setDone((current) => [...current, 'evaluate']);

      setStage('verify');
      const publicKey = deserializeElement(hexToBytes(evaluation.publicKey));
      const evaluatedElement = deserializeElement(hexToBytes(evaluation.evaluatedElements[0]));
      const proof = deserializeProof(evaluation.proof);

      // Pin against the key this session started with. A server answering
      // under a key of its own would isolate this console's pseudonyms from
      // everyone else's, and every check would come back clear.
      if (parameters?.activePublicKey
        && evaluation.publicKey.toLowerCase() !== parameters.activePublicKey.toLowerCase()
        && !parameters.keys?.some((key) => key.publicKey.toLowerCase() === evaluation.publicKey.toLowerCase())) {
        throw new Error('The server answered under a key this console does not recognise. Check stopped.');
      }

      setStage('finalize');
      const pseudonymBytes = await finalize(input, blindScalar, evaluatedElement, blindedElement, publicKey, proof);
      const pseudonym = bytesToHex(pseudonymBytes);
      setDone((current) => [...current, 'verify', 'finalize']);
      setTrace((current) => ({ ...current, keyId: evaluation.keyId, pseudonym }));

      setStage('check');
      const verdict = await api.screening.check(pseudonym, evaluation.keyId);
      setDone((current) => [...current, 'check']);

      setResult(verdict);
      setStage(null);
    } catch (failure) {
      setError(failure.message);
      setStage(null);
    }
  }

  const flagged = result?.status === 'FLAGGED';

  return (
    <Page
      title="Screening"
      eyebrow="Network"
      icon={ScanSearch}
      lede="Ask whether the network is carrying an adverse signal for a person, before you deal with them. The identifier is blinded in this tab and never sent."
    >
      {!bankCredential && (
        <Notice tone="warn">
          Screening needs a member bank credential. Add one from <Link to="/banks">Member banks</Link>.
        </Notice>
      )}

      {error && <Notice tone="stop">{error}</Notice>}

      <Section title="Person to check" icon={ScanSearch}>
        <form onSubmit={run}>
          <div className="grid-2">
            <Field label="Identifier type">
              <Select
                value={identifierType}
                onChange={(event) => setIdentifierType(event.target.value)}
                disabled={busy}
              >
                {IDENTIFIER_LABELS.map((option) => (
                  <option key={option.value} value={option.value}>{option.label}</option>
                ))}
              </Select>
            </Field>

            <Field label="Identifier" note="Blinded in this tab before sending.">
              <Input
                mono
                value={identifier}
                onChange={(event) => setIdentifier(event.target.value)}
                placeholder="7GK4M2Q"
                autoComplete="off"
                spellCheck={false}
                disabled={busy}
                required
              />
            </Field>
          </div>

          <div className="row" style={{ marginTop: 4 }}>
            {/* type=submit and no onClick: a button inside a form defaults to
                submitting it, so wiring both would run the protocol twice. */}
            <Button
              type="submit"
              variant="primary"
              icon={ScanSearch}
              busy={busy}
              disabled={!canSubmit}
            >
              Check the network
            </Button>
          </div>
        </form>

        <div className="divider" />

        <div className="label" style={{ marginBottom: 10 }}>Protocol</div>
        <div className="steps">
          {STEPS.map((step, index) => {
            const isDone = done.includes(step.key);
            const isActive = stage === step.key;
            return (
              <div key={step.key} className={`step${isDone ? ' done' : ''}${isActive ? ' active' : ''}`}>
                <div className="step-index">{isDone ? '✓' : index + 1}</div>
                <div className="step-body">
                  <div className="step-name">{step.name}</div>
                  {isActive && <div className="step-detail"><span className="spin" /> working</div>}
                  {isDone && step.key === 'blind' && trace?.blindedHex && (
                    <div className="step-detail mono" style={{ wordBreak: 'break-all' }}>{trace.blindedHex}</div>
                  )}
                  {isDone && step.key === 'verify' && (
                    <div className="step-detail">Accepted against the published key.</div>
                  )}
                  {isDone && step.key === 'check' && (
                    <div className="step-detail">The pseudonym went to the network. The identifier did not.</div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </Section>

      {result && (
        <Section title="Result" icon={flagged ? ShieldAlert : CircleCheck}>
          <div className="row" style={{ gap: 14, marginBottom: 18 }}>
            <Pill tone={flagged ? 'stop' : 'ok'}>{result.status}</Pill>
            {result.enrolledWithYou && <Pill tone="cool">Your customer</Pill>}
          </div>

          {flagged ? (
            <Notice tone="stop" title="Flagged in the network">
              At least one member institution has blocked this person, or the network suspended
              them because another institution did. Which institution, under what name, and how
              many, are deliberately not disclosed.
            </Notice>
          ) : (
            <Notice tone="ok" title="Nothing against this person">
              No member institution is carrying an adverse signal. This does not mean the person
              is unknown to the network — clear and unknown are the same answer here, on purpose,
              so that a check cannot be used to find out where someone banks.
            </Notice>
          )}

          <div className="divider" />

          <div className="label" style={{ marginBottom: 8 }}>Pseudonym sent</div>
          <CopyValue value={trace.pseudonym} />

          <div className="divider" />

          <dl className="kv" style={{ gridTemplateColumns: '150px 1fr' }}>
            <dt>Key</dt>
            <dd className="mono">{result.oprfKeyId}</dd>
            <dt>Enrolled with you</dt>
            <dd>{result.enrolledWithYou ? 'Yes' : 'No'}</dd>
            <dt>Not sent</dt>
            <dd className="mono">{trace.canonical}</dd>
          </dl>
        </Section>
      )}
    </Page>
  );
}
