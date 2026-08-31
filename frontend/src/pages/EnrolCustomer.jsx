import { useState } from 'react';
import { api } from '../lib/api.js';
import { useSession } from '../lib/session.jsx';
import {
  Button, CopyValue, Field, Input, Notice, Select, Sheet,
} from '../ui/kit.jsx';
import {
  IDENTIFIER_TYPES, canonicalize, blind, serializeElement, deserializeElement,
  deserializeProof, finalize, bytesToHex, hexToBytes,
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
  { key: 'enrol', name: 'Enrol' },
];

/**
 * Enrolment, with the protocol running in this browser tab.
 *
 * The identifier typed below never leaves the machine. What goes over the wire
 * is a point on a curve that the server cannot invert, and the server's reply is
 * checked against the key it publishes before anything is trusted. The step list
 * is not decoration: an operator handling other people's national ID numbers
 * should be able to watch where they go.
 */
export default function EnrolCustomer({ banks, onClose, onEnrolled }) {
  const { parameters, bank: bankCredential } = useSession();

  const [name, setName] = useState('');
  const [bankId, setBankId] = useState(banks[0]?.id ?? '');
  const [identifierType, setIdentifierType] = useState(IDENTIFIER_TYPES.AZ_FIN);
  const [identifier, setIdentifier] = useState('');

  const [stage, setStage] = useState(null);
  const [done, setDone] = useState([]);
  const [error, setError] = useState(null);
  const [trace, setTrace] = useState(null);
  const [complete, setComplete] = useState(false);

  const busy = stage !== null;
  const canSubmit = name.trim() && bankId && identifier.trim() && bankCredential && !busy;

  async function run(event) {
    event.preventDefault();
    setError(null);
    setDone([]);
    setTrace(null);

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

      // Pin against the key this session started with. A server that switched
      // keys mid-session would isolate everything enrolled after the switch.
      if (parameters?.activePublicKey
        && evaluation.publicKey.toLowerCase() !== parameters.activePublicKey.toLowerCase()
        && !parameters.keys?.some((key) => key.publicKey.toLowerCase() === evaluation.publicKey.toLowerCase())) {
        throw new Error('The server answered under a key this console does not recognise. Enrolment stopped.');
      }

      setStage('finalize');
      // finalize verifies the proof before it unblinds anything, so a bad proof
      // never reaches the pseudonym.
      const pseudonymBytes = await finalize(input, blindScalar, evaluatedElement, blindedElement, publicKey, proof);
      const pseudonym = bytesToHex(pseudonymBytes);
      setDone((current) => [...current, 'verify', 'finalize']);
      setTrace((current) => ({
        ...current,
        evaluatedHex: evaluation.evaluatedElements[0],
        proof: evaluation.proof,
        keyId: evaluation.keyId,
        pseudonym,
      }));

      setStage('enrol');
      await api.customers.enrol({
        name: name.trim(),
        pseudonym,
        oprfKeyId: evaluation.keyId,
        bankId,
      });
      setDone((current) => [...current, 'enrol']);

      setComplete(true);
      setStage(null);
      onEnrolled();
    } catch (failure) {
      setError(failure.message);
      setStage(null);
    }
  }

  if (complete) {
    return (
      <Sheet
        title="Enrolled"
        lede={name.trim()}
        onClose={onClose}
        footer={<Button variant="primary" onClick={onClose}>Done</Button>}
      >
        <div className="label" style={{ marginBottom: 8 }}>Pseudonym</div>
        <CopyValue value={trace.pseudonym} />

        <div className="divider" />

        <dl className="kv" style={{ gridTemplateColumns: '150px 1fr' }}>
          <dt>Key</dt>
          <dd className="mono">{trace.keyId}</dd>
          <dt>Sent</dt>
          <dd className="hex">{trace.blindedHex}</dd>
          <dt>Not sent</dt>
          <dd className="mono">{trace.canonical}</dd>
        </dl>
      </Sheet>
    );
  }

  return (
    <Sheet
      title="Enrol a customer"
      onClose={onClose}
      footer={(
        <>
          <Button onClick={onClose} disabled={busy}>Cancel</Button>
          <Button variant="primary" onClick={run} busy={busy} disabled={!canSubmit}>
            Derive and enrol
          </Button>
        </>
      )}
    >
      {!bankCredential && (
        <Notice tone="warn">
          No member bank credential in this tab. Add one from Member banks.
        </Notice>
      )}

      {error && <Notice tone="stop">{error}</Notice>}

      <form onSubmit={run}>
        <Field label="Customer name" note="Display label only. Not part of the pseudonym.">
          <Input
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Aygün Məmmədova"
            disabled={busy}
            required
          />
        </Field>

        <Field label="Member bank">
          <Select value={bankId} onChange={(event) => setBankId(event.target.value)} disabled={busy}>
            {banks.map((bank) => <option key={bank.id} value={bank.id}>{bank.name}</option>)}
          </Select>
        </Field>

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
              </div>
            </div>
          );
        })}
      </div>
    </Sheet>
  );
}
