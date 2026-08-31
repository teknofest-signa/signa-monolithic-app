import { Link } from 'react-router-dom';
import { api } from '../lib/api.js';
import { useSession } from '../lib/session.jsx';
import {
  Figure, Loading, Notice, Page, Pill, Section, Table, Empty,
  useResource, customerTone, riskTone,
} from '../ui/kit.jsx';
import { formatCount, formatMoney, formatRelative, humanise } from '../lib/format.js';

/** Spring serialises pages as { content, page: { totalElements } }. Tolerate both shapes. */
export const pageItems = (response) => response?.content ?? [];
export const pageTotal = (response) =>
  response?.page?.totalElements ?? response?.totalElements ?? pageItems(response).length;

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

  if (network.loading) return <Page title="Overview"><Loading label="Reading the network" /></Page>;

  if (network.error) {
    return (
      <Page title="Overview">
        <Notice tone="stop" title="Could not load the network">{network.error}</Notice>
      </Page>
    );
  }

  const customers = pageItems(network.data.customers);
  const banks = network.data.banks ?? [];
  const simulations = pageItems(network.data.simulations);

  const enrolled = pageTotal(network.data.customers);
  const blocked = customers.filter((customer) => customer.customerStatus === 'BLOCKED').length;
  const suspended = customers.filter((customer) => customer.customerStatus === 'SUSPENDED').length;
  const withoutKey = banks.filter((bank) => !bank.oprfEnabled).length;

  return (
    <Page title="Overview">
      <div className="figures">
        <Figure
          label="Enrolled"
          value={formatCount(enrolled)}
          note={`across ${banks.length} member ${banks.length === 1 ? 'bank' : 'banks'}`}
        />
        <Figure label="Blocked" value={formatCount(blocked)} />
        <Figure
          label="Suspended"
          value={formatCount(suspended)}
          note="matched a block elsewhere"
        />
        <Figure
          label="Member banks"
          value={formatCount(banks.length)}
          note={withoutKey > 0 ? `${withoutKey} without OPRF access` : 'all with OPRF access'}
        />
      </div>

      <Section
        title="Recently enrolled"
        actions={<Link to="/customers" className="btn btn-ghost btn-sm">All customers</Link>}
      >
        {customers.length === 0 ? (
          <Empty title="No customers" action={<Link to="/customers" className="btn btn-primary btn-sm">Enrol</Link>} />
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
        actions={<Link to="/risk" className="btn btn-ghost btn-sm">Risk simulator</Link>}
      >
        {simulations.length === 0 ? (
          <Empty title="Nothing scored" />
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
        <Section title="Privacy layer" actions={<Link to="/privacy" className="btn btn-ghost btn-sm">Details</Link>}>
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
