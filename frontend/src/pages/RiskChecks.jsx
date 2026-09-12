import { useMemo, useState } from 'react';
import {
  ArrowDownLeft,
  ArrowUpRight,
  Download,
  FileSpreadsheet,
  FileText,
  History,
  Info,
  Printer,
  Search,
  ShieldAlert,
  ShieldCheck,
  TrendingDown,
  TrendingUp,
  User,
  Waves,
} from 'lucide-react';
import { api } from '../lib/api.js';
import {
  Button, Empty, Loading, Notice, Page, Pill, Section, Sheet, Table,
  riskTone, useResource, useToast,
} from '../ui/kit.jsx';
import { formatMoney, formatRelative, humanise } from '../lib/format.js';
import { pageItems, pageTotal } from './Overview.jsx';

const FILTERS = [
  { value: 'ALL', label: 'All Evaluations' },
  { value: 'BLOCKED', label: 'Blocked / Flagged' },
  { value: 'APPROVED', label: 'Approved' },
];

export default function RiskChecks() {
  const toast = useToast();
  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState('ALL');
  const [query, setQuery] = useState('');
  const [selectedId, setSelectedId] = useState(null);
  const [exporting, setExporting] = useState(false);

  const checks = useResource(() => api.riskChecks.list(page, 15, filter), [page, filter]);
  const details = useResource(
    async () => (selectedId ? api.riskChecks.get(selectedId) : null),
    [selectedId],
  );

  const rows = pageItems(checks.data);
  const filteredRows = useMemo(() => {
    if (!query.trim()) return rows;
    const q = query.toLowerCase();
    return rows.filter((r) =>
      (r.userName && r.userName.toLowerCase().includes(q)) ||
      (r.userAccountId && r.userAccountId.toLowerCase().includes(q)) ||
      (r.userIban && r.userIban.toLowerCase().includes(q)) ||
      (r.recipientName && r.recipientName.toLowerCase().includes(q)) ||
      (r.reason && r.reason.toLowerCase().includes(q)),
    );
  }, [rows, query]);

  const total = pageTotal(checks.data);
  const pageCount = checks.data?.page?.totalPages ?? checks.data?.totalPages ?? 1;

  async function handleExportExcel(id, e) {
    if (e) e.stopPropagation();
    setExporting(true);
    try {
      await api.riskChecks.downloadExcel(id);
      toast.say('Excel audit sheet downloaded successfully.');
    } catch (err) {
      toast.warn(err.message);
    } finally {
      setExporting(false);
    }
  }

  async function handleExportReport(id, e) {
    if (e) e.stopPropagation();
    setExporting(true);
    try {
      await api.riskChecks.downloadReport(id);
      toast.say('Audit report opened for print / PDF export.');
    } catch (err) {
      toast.warn(err.message);
    } finally {
      setExporting(false);
    }
  }

  return (
    <Page
      title="Transaction Risk Checks"
      eyebrow="Intelligence"
      icon={ShieldAlert}
      lede="Live audit log of all transaction evaluations, rule triggers, model explanation scores, and forensic export reports."
    >
      <Section
        title={`${total} Checked Transactions`}
        icon={Waves}
        actions={(
          <div className="row" style={{ gap: 12 }}>
            <div className="search-box" style={{ display: 'flex', alignItems: 'center', background: 'var(--panel-subtle)', borderRadius: 6, padding: '4px 8px', border: '1px solid var(--border)' }}>
              <Search size={14} className="muted" style={{ marginRight: 6 }} />
              <input
                type="text"
                placeholder="Search user, IBAN, recipient..."
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                style={{ background: 'transparent', border: 'none', outline: 'none', fontSize: 13, color: 'inherit' }}
              />
            </div>

            <div className="segmented">
              {FILTERS.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  className={`btn btn-quiet btn-sm${filter === option.value ? ' on' : ''}`}
                  onClick={() => { setFilter(option.value); setPage(0); }}
                >
                  {option.label}
                </button>
              ))}
            </div>
          </div>
        )}
      >
        {checks.loading ? (
          <Loading label="Reading transaction risk checks" />
        ) : checks.error ? (
          <Notice tone="stop">{checks.error}</Notice>
        ) : filteredRows.length === 0 ? (
          <Empty
            icon={ShieldAlert}
            title={rows.length === 0 ? 'No transaction checks recorded' : 'Nothing matches that search'}
            note={rows.length === 0
              ? 'When transfers are confirmed from the client or simulator, risk assessments will appear here.'
              : 'Try clearing the search query or changing the filter.'}
          />
        ) : (
          <>
            <Table columns={['Transaction', 'Sender Account', 'Recipient', 'Score', 'Verdict', 'Flag Reason', 'When', 'Actions']}>
              {filteredRows.map((row) => (
                <tr
                  key={row.id}
                  style={{ cursor: 'pointer' }}
                  onClick={() => setSelectedId(row.id)}
                >
                  <td className="lead num">
                    {formatMoney(row.transactionAmount, row.transactionCurrency)}
                  </td>
                  <td>
                    <div style={{ fontWeight: 600 }}>{row.userName || '—'}</div>
                    <div className="mono faint" style={{ fontSize: 11 }}>{row.userIban || row.userAccountId || '—'}</div>
                  </td>
                  <td>
                    <div>{row.recipientName || '—'}</div>
                    <span className="pill mute" style={{ fontSize: 10, padding: '1px 6px' }}>{row.recipientType || 'payee'}</span>
                  </td>
                  <td className="num font-mono">
                    <span style={{ fontWeight: 700, color: row.approved ? 'var(--ok)' : 'var(--stop)' }}>
                      {(Number(row.riskScore ?? 0)).toFixed(2)}
                    </span>
                  </td>
                  <td>
                    <Pill tone={row.approved ? 'ok' : 'stop'}>
                      {row.approved ? 'Approved' : 'Blocked'}
                    </Pill>
                  </td>
                  <td className="muted" style={{ maxWidth: 220, fontSize: 12, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={row.reason}>
                    {row.reason || 'Standard automated clearance'}
                  </td>
                  <td className="muted num">{formatRelative(row.requestedAt || row.createdAt)}</td>
                  <td>
                    <div className="row" style={{ gap: 6 }} onClick={(e) => e.stopPropagation()}>
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => setSelectedId(row.id)}
                      >
                        Inspect
                      </Button>
                      {!row.approved && (
                        <>
                          <Button
                            size="sm"
                            variant="ghost"
                            icon={FileSpreadsheet}
                            title="Export Excel Sheet"
                            onClick={(e) => handleExportExcel(row.id, e)}
                          />
                          <Button
                            size="sm"
                            variant="ghost"
                            icon={FileText}
                            title="Export PDF / Print Report"
                            onClick={(e) => handleExportReport(row.id, e)}
                          />
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </Table>

            {pageCount > 1 && (
              <div className="spread" style={{ marginTop: 22 }}>
                <span className="faint" style={{ fontSize: 13 }}>Page {page + 1} of {pageCount}</span>
                <div className="row">
                  <Button size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button>
                  <Button size="sm" disabled={page + 1 >= pageCount} onClick={() => setPage(page + 1)}>Next</Button>
                </div>
              </div>
            )}
          </>
        )}
      </Section>

      {selectedId && (
        <Sheet
          title="Transaction Risk Audit"
          lede={`ID: ${selectedId}`}
          onClose={() => setSelectedId(null)}
          footer={(
            <div className="spread" style={{ width: '100%' }}>
              <Button onClick={() => setSelectedId(null)}>Close</Button>
              <div className="row" style={{ gap: 8 }}>
                <Button
                  variant="quiet"
                  icon={FileSpreadsheet}
                  disabled={exporting}
                  onClick={() => handleExportExcel(selectedId)}
                >
                  Export Excel
                </Button>
                <Button
                  variant="primary"
                  icon={Printer}
                  disabled={exporting}
                  onClick={() => handleExportReport(selectedId)}
                >
                  Export / Print PDF
                </Button>
              </div>
            </div>
          )}
        >
          {details.loading ? (
            <Loading label="Loading audit data and model factors" />
          ) : details.error ? (
            <Notice tone="stop">{details.error}</Notice>
          ) : details.data ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
              {/* Verdict Banner */}
              <div
                style={{
                  padding: '16px',
                  borderRadius: '8px',
                  border: `1.5px solid ${details.data.approved ? 'var(--ok-border, #86efac)' : 'var(--stop-border, #fca5a5)'}`,
                  background: details.data.approved ? 'rgba(34, 197, 94, 0.08)' : 'rgba(239, 68, 68, 0.08)',
                }}
              >
                <div className="spread" style={{ alignItems: 'flex-start' }}>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      {details.data.approved ? <ShieldCheck size={20} color="var(--ok)" /> : <ShieldAlert size={20} color="var(--stop)" />}
                      <span style={{ fontSize: 16, fontWeight: 800 }}>
                        {details.data.approved ? 'VERDICT: APPROVED' : 'VERDICT: BLOCKED / FAILED'}
                      </span>
                    </div>
                    {details.data.reason && (
                      <p style={{ marginTop: 6, fontSize: 13, color: 'var(--ink)' }}>
                        <strong>Flag Reason:</strong> {details.data.reason}
                      </p>
                    )}
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: 18, fontWeight: 800 }}>
                      Score {Number(details.data.riskScore ?? 0).toFixed(2)}
                    </div>
                    <span className="faint" style={{ fontSize: 11 }}>out of 1.00</span>
                  </div>
                </div>

                {/* Score Progress Bar */}
                <div style={{ marginTop: 12, height: 8, background: 'rgba(0,0,0,0.1)', borderRadius: 4, overflow: 'hidden' }}>
                  <div
                    style={{
                      height: '100%',
                      width: `${Math.min(100, Math.round(Number(details.data.riskScore ?? 0) * 100))}%`,
                      background: details.data.approved ? 'var(--ok)' : 'var(--stop)',
                      transition: 'width 0.4s ease',
                    }}
                  />
                </div>
              </div>

              {/* Transaction & Recipient */}
              <div>
                <div className="label" style={{ marginBottom: 8 }}>Transaction & Recipient</div>
                <div className="grid-2" style={{ gap: 10 }}>
                  <div className="card panel-body" style={{ background: 'var(--panel-subtle)', borderRadius: 6, padding: '10px 12px' }}>
                    <div className="faint" style={{ fontSize: 11 }}>Transfer Amount</div>
                    <div style={{ fontSize: 16, fontWeight: 700 }}>
                      {formatMoney(details.data.transaction?.amount, details.data.transaction?.currency)}
                    </div>
                  </div>
                  <div className="card panel-body" style={{ background: 'var(--panel-subtle)', borderRadius: 6, padding: '10px 12px' }}>
                    <div className="faint" style={{ fontSize: 11 }}>Recipient Name</div>
                    <div style={{ fontWeight: 600 }}>{details.data.transaction?.recipient?.name || '—'}</div>
                  </div>
                  <div className="card panel-body" style={{ background: 'var(--panel-subtle)', borderRadius: 6, padding: '10px 12px' }}>
                    <div className="faint" style={{ fontSize: 11 }}>Recipient Type</div>
                    <div><Pill tone="mute">{details.data.transaction?.recipient?.type || '—'}</Pill></div>
                  </div>
                  <div className="card panel-body" style={{ background: 'var(--panel-subtle)', borderRadius: 6, padding: '10px 12px' }}>
                    <div className="faint" style={{ fontSize: 11 }}>Recipient Reference</div>
                    <div className="mono" style={{ fontSize: 12 }}>{details.data.transaction?.recipient?.reference || '—'}</div>
                  </div>
                </div>
                {details.data.transaction?.note && (
                  <div style={{ marginTop: 8, background: 'var(--panel-subtle)', padding: '8px 12px', borderRadius: 6, fontSize: 12 }}>
                    <span className="faint">Note:</span> {details.data.transaction.note}
                  </div>
                )}
              </div>

              {/* Sender & Financial Snapshot */}
              <div>
                <div className="label" style={{ marginBottom: 8 }}>Sender Profile & Account Context</div>
                <div className="grid-2" style={{ gap: 10 }}>
                  <div className="card panel-body" style={{ background: 'var(--panel-subtle)', borderRadius: 6, padding: '10px 12px' }}>
                    <div className="faint" style={{ fontSize: 11 }}>Account Holder</div>
                    <div style={{ fontWeight: 600 }}>{details.data.user?.name || '—'}</div>
                    <div className="mono faint" style={{ fontSize: 10 }}>{details.data.user?.accountId}</div>
                  </div>
                  <div className="card panel-body" style={{ background: 'var(--panel-subtle)', borderRadius: 6, padding: '10px 12px' }}>
                    <div className="faint" style={{ fontSize: 11 }}>Account IBAN</div>
                    <div className="mono" style={{ fontSize: 12 }}>{details.data.user?.iban || '—'}</div>
                    <div className="faint" style={{ fontSize: 10 }}>{details.data.user?.accountType} • Member since {details.data.user?.memberSince}</div>
                  </div>
                  <div className="card panel-body" style={{ background: 'var(--panel-subtle)', borderRadius: 6, padding: '10px 12px' }}>
                    <div className="faint" style={{ fontSize: 11 }}>Available Balance Before Transfer</div>
                    <div style={{ fontSize: 15, fontWeight: 700 }}>
                      {formatMoney(details.data.account?.currentBalance, details.data.account?.currency)}
                    </div>
                  </div>
                  <div className="card panel-body" style={{ background: 'var(--panel-subtle)', borderRadius: 6, padding: '10px 12px' }}>
                    <div className="faint" style={{ fontSize: 11 }}>Projected Balance After Transfer</div>
                    <div style={{ fontSize: 15, fontWeight: 700, color: details.data.projectedBalance < 0 ? 'var(--stop)' : 'inherit' }}>
                      {formatMoney(details.data.projectedBalance, details.data.account?.currency)}
                    </div>
                  </div>
                </div>
              </div>

              {/* Risk Model Breakdown & Explanations */}
              <div>
                <div className="label" style={{ marginBottom: 8 }}>Risk Factors & Why Our Model Flagged This</div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {details.data.riskFactors?.map((factor) => (
                    <div
                      key={factor.code}
                      style={{
                        padding: '10px 12px',
                        borderRadius: 6,
                        border: factor.triggered ? '1px solid var(--stop-border, #fca5a5)' : '1px solid var(--border)',
                        background: factor.triggered ? 'rgba(239, 68, 68, 0.05)' : 'var(--panel-subtle)',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        gap: 12,
                      }}
                    >
                      <div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <span style={{ fontWeight: 700, fontSize: 13, color: factor.triggered ? 'var(--stop)' : 'inherit' }}>
                            {factor.title}
                          </span>
                          <Pill tone={factor.triggered ? 'stop' : 'mute'}>
                            {factor.triggered ? 'Triggered / Flagged' : 'Passed'}
                          </Pill>
                        </div>
                        <div className="muted" style={{ fontSize: 12, marginTop: 4 }}>
                          {factor.description}
                        </div>
                      </div>
                      <div style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                        <span className="mono font-mono faint" style={{ fontSize: 12 }}>
                          +{Number(factor.riskWeight ?? 0).toFixed(2)}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Spending Summary Metrics */}
              <div>
                <div className="label" style={{ marginBottom: 8 }}>Historical Spending Analysis</div>
                <div className="grid-2" style={{ gap: 10 }}>
                  <div className="card panel-body" style={{ background: 'var(--panel-subtle)', borderRadius: 6, padding: '10px 12px' }}>
                    <div className="faint" style={{ fontSize: 11 }}>Total Historical Deposits</div>
                    <div style={{ fontWeight: 700, color: 'var(--ok)' }}>
                      {formatMoney(details.data.depositTotal, details.data.account?.currency)}
                    </div>
                  </div>
                  <div className="card panel-body" style={{ background: 'var(--panel-subtle)', borderRadius: 6, padding: '10px 12px' }}>
                    <div className="faint" style={{ fontSize: 11 }}>Total Historical Withdrawals</div>
                    <div style={{ fontWeight: 700, color: 'var(--stop)' }}>
                      {formatMoney(details.data.withdrawalTotal, details.data.account?.currency)}
                    </div>
                  </div>
                  <div className="card panel-body" style={{ background: 'var(--panel-subtle)', borderRadius: 6, padding: '10px 12px' }}>
                    <div className="faint" style={{ fontSize: 11 }}>Average Past Withdrawal</div>
                    <div style={{ fontWeight: 700 }}>
                      {formatMoney(details.data.avgWithdrawal, details.data.account?.currency)}
                    </div>
                  </div>
                  <div className="card panel-body" style={{ background: 'var(--panel-subtle)', borderRadius: 6, padding: '10px 12px' }}>
                    <div className="faint" style={{ fontSize: 11 }}>Maximum Past Withdrawal</div>
                    <div style={{ fontWeight: 700 }}>
                      {formatMoney(details.data.maxWithdrawal, details.data.account?.currency)}
                    </div>
                  </div>
                </div>
              </div>

              {/* History Entries Table */}
              {details.data.historyEntries && details.data.historyEntries.length > 0 && (
                <div>
                  <div className="label" style={{ marginBottom: 8 }}>Historical Transactions Provided in Check</div>
                  <Table columns={['Counterparty', 'Category', 'Date', 'Amount']}>
                    {details.data.historyEntries.map((h, i) => (
                      <tr key={i}>
                        <td className="lead">{h.name || '—'}</td>
                        <td className="muted"><span className="pill mute" style={{ fontSize: 11 }}>{h.category || 'General'}</span></td>
                        <td className="muted num">{formatRelative(h.date)}</td>
                        <td className="num" style={{ fontWeight: 600, color: h.amt < 0 ? 'var(--stop)' : 'var(--ok)' }}>
                          {formatMoney(h.amt, details.data.account?.currency)}
                        </td>
                      </tr>
                    ))}
                  </Table>
                </div>
              )}
            </div>
          ) : null}
        </Sheet>
      )}
    </Page>
  );
}
