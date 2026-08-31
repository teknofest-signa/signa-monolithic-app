import { useState } from 'react';
import { api } from '../lib/api.js';
import {
  Button, Empty, Field, Input, Loading, Notice, Page, Pill, Section, Select,
  Table, riskTone, useResource, useToast,
} from '../ui/kit.jsx';
import { formatMoney, formatRelative, humanise } from '../lib/format.js';
import { pageItems } from './Overview.jsx';

const CHANNELS = ['MOBILE_APP', 'WEB_BANKING', 'ATM', 'POINT_OF_SALE', 'BANK_API'];
const TYPES = ['TRANSFER', 'DEPOSIT', 'WITHDRAWAL', 'PAYMENT'];
const CURRENCIES = ['AZN', 'USD', 'EUR', 'TRY'];

const BLANK = {
  amount: '2500',
  currency: 'AZN',
  transactionType: 'TRANSFER',
  transactionChannel: 'MOBILE_APP',
  isNewBeneficiary: false,
  isCrossBorderTransaction: false,
};

export default function RiskSimulator() {
  const toast = useToast();
  const [form, setForm] = useState(BLANK);
  const [result, setResult] = useState(null);
  const [busy, setBusy] = useState(false);

  const history = useResource(() => api.simulation.history(0, 12), []);

  const set = (key) => (event) => {
    const value = event.target.type === 'checkbox' ? event.target.checked : event.target.value;
    setForm((current) => ({ ...current, [key]: value }));
  };

  async function run(event) {
    event.preventDefault();
    setBusy(true);
    try {
      setResult(await api.simulation.run({
        simulationType: 'TRANSACTION',
        fromAccountId: crypto.randomUUID(),
        toAccountId: crypto.randomUUID(),
        amount: Number(form.amount),
        currency: form.currency,
        transactionType: form.transactionType,
        transactionChannel: form.transactionChannel,
        transactionTime: new Date().toISOString(),
        isNewBeneficiary: form.isNewBeneficiary,
        isCrossBorderTransaction: form.isCrossBorderTransaction,
      }));
      history.reload();
    } catch (failure) {
      toast.warn(failure.message);
    } finally {
      setBusy(false);
    }
  }

  const score = Number(result?.transactionFraudScore ?? 0);

  return (
    <Page
      title="Risk simulator"
    >
      <Notice>Scored by the current rule set. The learned model is not wired in yet.</Notice>

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 420px) 1fr', gap: 48, alignItems: 'start' }}>
        <form onSubmit={run}>
          <div className="grid-2">
            <Field label="Amount">
              <Input type="number" min="0" step="0.01" value={form.amount} onChange={set('amount')} required />
            </Field>
            <Field label="Currency">
              <Select value={form.currency} onChange={set('currency')}>
                {CURRENCIES.map((code) => <option key={code} value={code}>{code}</option>)}
              </Select>
            </Field>
          </div>

          <Field label="Type">
            <Select value={form.transactionType} onChange={set('transactionType')}>
              {TYPES.map((type) => <option key={type} value={type}>{humanise(type)}</option>)}
            </Select>
          </Field>

          <Field label="Channel">
            <Select value={form.transactionChannel} onChange={set('transactionChannel')}>
              {CHANNELS.map((channel) => <option key={channel} value={channel}>{humanise(channel)}</option>)}
            </Select>
          </Field>

          <div style={{ marginTop: 24, marginBottom: 24 }}>
            <label className="check">
              <input type="checkbox" checked={form.isNewBeneficiary} onChange={set('isNewBeneficiary')} />
              <span>The beneficiary has not been paid before</span>
            </label>
            <label className="check">
              <input type="checkbox" checked={form.isCrossBorderTransaction} onChange={set('isCrossBorderTransaction')} />
              <span>The money leaves the country</span>
            </label>
          </div>

          <Button type="submit" variant="primary" size="lg" busy={busy} style={{ width: '100%' }}>
            Score this transaction
          </Button>
        </form>

        <div>
          {result ? (
            <div className="panel">
              <div className="panel-body">
                <div className="label">Decision</div>
                <div className="display" style={{ fontSize: 46, marginTop: 12, marginBottom: 4 }}>
                  {humanise(result.transactionFraudStatus)}
                </div>
                <Pill tone={riskTone(result.transactionFraudStatus)}>
                  score {score.toFixed(2)}
                </Pill>

                <div className="bar" style={{ marginTop: 26 }}>
                  <i style={{ width: `${Math.min(100, score * 100)}%` }} />
                </div>

                <dl className="kv" style={{ marginTop: 26, gridTemplateColumns: '1fr auto' }}>
                  <dt>Approve</dt>
                  <dd className="num faint">below 0.50</dd>
                  <dt>Review</dt>
                  <dd className="num faint">0.50 to 0.79</dd>
                  <dt>Block</dt>
                  <dd className="num faint">0.80 and above</dd>
                </dl>
              </div>
            </div>
          ) : (
            <div className="panel panel-warm">
              <div className="panel-body">
                <Empty title="No result yet" />
              </div>
            </div>
          )}
        </div>
      </div>

      <Section title="Recent decisions">
        {history.loading ? (
          <Loading label="Reading the log" />
        ) : pageItems(history.data).length === 0 ? (
          <Empty title="No decisions recorded" />
        ) : (
          <Table columns={['Amount', 'Type', 'Channel', 'Flags', 'Score', 'Decision', 'When']}>
            {pageItems(history.data).map((row) => (
              <tr key={row.id}>
                <td className="lead num">{formatMoney(row.amount, row.currency)}</td>
                <td className="muted">{humanise(row.transactionType)}</td>
                <td className="muted">{humanise(row.transactionChannel)}</td>
                <td className="muted" style={{ fontSize: 13 }}>
                  {[row.isCrossBorderTransaction && 'cross-border', row.isNewBeneficiary && 'new beneficiary']
                    .filter(Boolean).join(', ') || '—'}
                </td>
                <td className="num muted">{Number(row.transactionFraudScore ?? 0).toFixed(2)}</td>
                <td><Pill tone={riskTone(row.transactionFraudStatus)}>{humanise(row.transactionFraudStatus)}</Pill></td>
                <td className="muted num">{formatRelative(row.createdAt)}</td>
              </tr>
            ))}
          </Table>
        )}
      </Section>
    </Page>
  );
}
