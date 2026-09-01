import { Link } from 'react-router-dom';
import { ArrowRight, Building2, LayoutGrid, ShieldCheck, Users, Waves } from 'lucide-react';
import { api } from '../lib/api.js';
import { useSession } from '../lib/session.jsx';
import {
  Counter, Empty, Loading, MixBar, Notice, Page, Pill, Section, Table, TrendBars,
  useResource, customerTone, riskTone,
} from '../ui/kit.jsx';
import { formatCount, formatMoney, formatRelative, humanise, initials } from '../lib/format.js';

/** Spring serialises pages as { content, page: { totalElements } }. Tolerate both shapes. */
export const pageItems = (response) => response?.content ?? [];
export const pageTotal = (response) =>
  response?.page?.totalElements ?? response?.totalElements ?? pageItems(response).length;

const DAY = 86400000;

/**
 * New enrolments per day across the last month, read off the records already on
 * screen. Nothing extra is fetched for it, and a window whose activity lands on
 * a single day has no shape worth drawing, so that comes back empty and the
 * panel leaves the line out.
 */
function enrolmentTrend(customers, days = 30) {
  const end = new Date().setHours(23, 59, 59, 999);
  const buckets = new Array(days).fill(0);

  for (const customer of customers) {
    if (!customer.createdAt) continue;
    const age = Math.floor((end - new Date(customer.createdAt).getTime()) / DAY);
    if (age >= 0 && age < days) buckets[days - 1 - age] += 1;
  }

  return buckets.filter((count) => count > 0).length > 1 ? buckets : [];
}

export default function Overview() {
  const { parameters } = useSession();

  const network = useResource(async () => {
    const [customers, banks, simulations] = await Promise.all([
      api.customers.list(0, 250),
      api.banks.list(),
      api.simulation.history(0, 6).catch(() => null),
    ]);
    return { customers, banks, simulations };
  }, []);

  if (network.loading) {
    return <Page title="Overview" eyebrow="Network" icon={LayoutGrid}><Loading label="Reading the network" /></Page>;
  }

  if (network.error) {
    return (
      <Page title="Overview" eyebrow="Network" icon={LayoutGrid}>
        <Notice tone="stop" title="Could not load the network">{network.error}</Notice>
      </Page>
    );
  }

  const customers = pageItems(network.data.customers);
  const banks = network.data.banks ?? [];
  const simulations = pageItems(network.data.simulations);

  const enrolled = pageTotal(network.data.customers);
  const withoutKey = banks.filter((bank) => !bank.oprfEnabled).length;

  // The mix is measured over the records actually loaded, which is all of them
  // until the network outgrows one page. Past that the headline stays the true
  // total and the panel says which of the two it is showing.
  const count = (status) => customers.filter((customer) => customer.customerStatus === status).length;
  const blocked = count('BLOCKED');
  const suspended = count('SUSPENDED');
  const inactive = count('INACTIVE');
  const active = Math.max(0, customers.length - blocked - suspended - inactive);
  const partial = enrolled > customers.length;

  const trend = enrolmentTrend(customers);

  return (
    <Page
      title="Overview"
      eyebrow="Network"
      icon={LayoutGrid}
      lede="Where the network stands right now: who is enrolled, what has been blocked, and what the privacy layer is running under."
    >
      <div className={`stat-grid${banks.length === 0 ? ' solo' : ''}`}>
        {/* Enrolled is the headline and the bar beneath it is the point: three
            separate counts give you three numbers, one bar gives you the shape
            of the population. */}
        {enrolled === 0 ? (
          <Empty
            icon={banks.length === 0 ? Building2 : Users}
            title={banks.length === 0 ? 'The network is empty' : 'Nobody enrolled yet'}
            note={banks.length === 0
              ? 'Register a member bank to open the network, then enrol the first customers.'
              : `${formatCount(banks.length)} member ${banks.length === 1 ? 'bank is' : 'banks are'} registered and waiting for a first enrolment.`}
            action={banks.length === 0
              ? <Link to="/banks" className="btn btn-primary btn-sm">Register a bank</Link>
              : <Link to="/customers" className="btn btn-primary btn-sm">Enrol a customer</Link>}
          />
        ) : (
          <section className="panel stat-hero">
            <div className="panel-body">
              <div className="stat-head">
                <span className="stat-chip"><Users size={14} strokeWidth={1.9} /></span>
                <span className="label">Enrolled</span>
                <Link to="/customers" className="btn btn-quiet btn-sm stat-head-action">
                  All customers <ArrowRight size={12} strokeWidth={1.9} />
                </Link>
              </div>

              <div className="stat-value"><Counter value={enrolled} /></div>
              <div className="stat-note">
                across {formatCount(banks.length)} member {banks.length === 1 ? 'bank' : 'banks'}
              </div>

              {trend.length > 0 && (
                <div className="stat-spark">
                  <TrendBars points={trend} label="New enrolments over the last 30 days" />
                  <span className="stat-spark-note">New enrolments, last 30 days</span>
                </div>
              )}

              <MixBar
                parts={[
                  { label: 'Active', value: active, tone: 'ok' },
                  { label: 'Suspended', value: suspended, tone: 'warn' },
                  { label: 'Blocked', value: blocked, tone: 'stop' },
                  { label: 'Inactive', value: inactive, tone: 'mute' },
                ]}
              />

              {partial && (
                <p className="field-note stat-caveat">
                  Mix measured across the {formatCount(customers.length)} most recent records.
                </p>
              )}
            </div>
          </section>
        )}

        {banks.length > 0 && (
          <section className="panel stat-side">
            <div className="panel-body">
              <div className="stat-head">
                <span className="stat-chip"><Building2 size={14} strokeWidth={1.9} /></span>
                <span className="label">Member banks</span>
              </div>

              <div className="stat-value sm"><Counter value={banks.length} /></div>
              <div className={`stat-note${withoutKey > 0 ? ' warn' : ''}`}>
                {withoutKey > 0
                  ? `${formatCount(withoutKey)} without OPRF access`
                  : 'all with OPRF access'}
              </div>

              {/* Who is actually on the network, by name. A bank that has lost
                  OPRF access is marked, because it stops matching. */}
              <ul className="bank-list">
                {banks.slice(0, 6).map((bank) => (
                  <li key={bank.id}>
                    <span
                      className={`bank-chip${bank.oprfEnabled ? '' : ' off'}`}
                      title={bank.oprfEnabled ? undefined : 'No OPRF access'}
                    >
                      {initials(bank.name)}
                    </span>
                    <span className="bank-name" title={bank.name}>{bank.name}</span>
                  </li>
                ))}
              </ul>

              {banks.length > 6 && (
                <Link to="/banks" className="btn btn-quiet btn-sm bank-more">
                  {formatCount(banks.length - 6)} more <ArrowRight size={12} strokeWidth={1.9} />
                </Link>
              )}
            </div>
          </section>
        )}
      </div>

      <Section
        title="Recently enrolled"
        icon={Users}
        actions={(
          <Link to="/customers" className="btn btn-ghost btn-sm">
            All customers <ArrowRight size={13} strokeWidth={1.9} />
          </Link>
        )}
      >
        {customers.length === 0 ? (
          <Empty
            icon={Users}
            title="No customers yet"
            note="Enrolments appear here as member banks add them."
            action={<Link to="/customers" className="btn btn-primary btn-sm">Enrol a customer</Link>}
          />
        ) : (
          <Table columns={['Customer', 'Bank', 'Status', 'Enrolled']}>
            {customers.slice(0, 6).map((customer) => (
              <tr key={customer.id}>
                <td className="lead">{customer.name}</td>
                <td className="muted">{customer.bankName}</td>
                <td><Pill tone={customerTone(customer.customerStatus)}>{humanise(customer.customerStatus)}</Pill></td>
                <td className="muted num">{formatRelative(customer.createdAt)}</td>
              </tr>
            ))}
          </Table>
        )}
      </Section>

      <Section
        title="Latest risk decisions"
        icon={Waves}
        actions={(
          <Link to="/risk" className="btn btn-ghost btn-sm">
            Risk simulator <ArrowRight size={13} strokeWidth={1.9} />
          </Link>
        )}
      >
        {simulations.length === 0 ? (
          <Empty icon={Waves} title="Nothing scored yet" note="Run a transaction through the simulator to see decisions here." />
        ) : (
          <Table columns={['Amount', 'Channel', 'Type', 'Score', 'Decision', 'When']}>
            {simulations.map((simulation) => (
              <tr key={simulation.id}>
                <td className="lead num">{formatMoney(simulation.amount, simulation.currency)}</td>
                <td className="muted">{humanise(simulation.transactionChannel)}</td>
                <td className="muted">{humanise(simulation.transactionType)}</td>
                <td className="num muted">{Number(simulation.transactionFraudScore ?? 0).toFixed(2)}</td>
                <td><Pill tone={riskTone(simulation.transactionFraudStatus)}>{humanise(simulation.transactionFraudStatus)}</Pill></td>
                <td className="muted num">{formatRelative(simulation.createdAt)}</td>
              </tr>
            ))}
          </Table>
        )}
      </Section>

      {parameters && (
        <Section
          title="Privacy layer"
          icon={ShieldCheck}
          actions={(
            <Link to="/privacy" className="btn btn-ghost btn-sm">
              Details <ArrowRight size={13} strokeWidth={1.9} />
            </Link>
          )}
        >
          <div className="panel panel-warm">
            <div className="panel-body">
              <dl className="kv">
                <dt>Ciphersuite</dt>
                <dd className="mono">{parameters.ciphersuite}</dd>
                <dt>Active key</dt>
                <dd className="mono">{parameters.activeKeyId}</dd>
                <dt>Keys in ring</dt>
                <dd className="num">{parameters.keys?.length ?? 1}</dd>
              </dl>
            </div>
          </div>
        </Section>
      )}
    </Page>
  );
}
