import { useMemo, useState } from 'react';
import { Ban, UserPlus, Users } from 'lucide-react';
import { api } from '../lib/api.js';
import { useSession } from '../lib/session.jsx';
import {
  Button, Empty, Loading, Notice, Page, Pill, Section, Sheet, Table,
  customerTone, useResource, useToast,
} from '../ui/kit.jsx';
import { formatDate, humanise } from '../lib/format.js';
import { pageItems, pageTotal } from './Overview.jsx';
import EnrolCustomer from './EnrolCustomer.jsx';

const FILTERS = [
  { value: 'ALL', label: 'Everyone' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'SUSPENDED', label: 'Suspended' },
  { value: 'BLOCKED', label: 'Blocked' },
];

export default function Customers() {
  const toast = useToast();
  const { bank } = useSession();
  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState('ALL');
  const [enrolling, setEnrolling] = useState(false);
  const [blocking, setBlocking] = useState(null);
  const [working, setWorking] = useState(false);

  const customers = useResource(() => api.customers.list(page, 20), [page]);
  const banks = useResource(() => api.banks.list(), []);

  const rows = pageItems(customers.data);
  const visible = useMemo(
    () => (filter === 'ALL' ? rows : rows.filter((row) => row.customerStatus === filter)),
    [rows, filter],
  );

  const total = pageTotal(customers.data);
  const pageCount = customers.data?.page?.totalPages ?? customers.data?.totalPages ?? 1;

  async function confirmBlock() {
    setWorking(true);
    try {
      await api.customers.block(blocking.id);
      toast.say(`${blocking.name} is blocked. Matching records elsewhere were suspended.`);
      setBlocking(null);
      customers.reload();
    } catch (failure) {
      toast.warn(failure.message);
    } finally {
      setWorking(false);
    }
  }

  return (
    <Page
      title="Customers"
      eyebrow="Network"
      icon={Users}
      lede="Every enrolment in the network, and the pseudonym key each one was sealed under."
      actions={(
        <Button
          variant="primary"
          icon={UserPlus}
          onClick={() => setEnrolling(true)}
          disabled={(banks.data ?? []).length === 0}
        >
          Enrol a customer
        </Button>
      )}
    >
      {banks.data && banks.data.length === 0 && (
        <Notice tone="warn">Register a member bank before enrolling.</Notice>
      )}

      {!bank && banks.data?.length > 0 && (
        <Notice>Enrolment needs a member bank credential. Add one from Member banks.</Notice>
      )}

      <Section
        title={`${total} enrolled`}
        icon={Users}
        actions={(
          <div className="segmented">
            {FILTERS.map((option) => (
              <button
                key={option.value}
                type="button"
                className={`btn btn-quiet btn-sm${filter === option.value ? ' on' : ''}`}
                onClick={() => setFilter(option.value)}
              >
                {option.label}
              </button>
            ))}
          </div>
        )}
      >
        {customers.loading ? (
          <Loading label="Reading enrolments" />
        ) : customers.error ? (
          <Notice tone="stop">{customers.error}</Notice>
        ) : visible.length === 0 ? (
          <Empty
            icon={Users}
            title={rows.length === 0 ? 'No customers' : 'Nothing matches that filter'}
            note={rows.length === 0
              ? 'Enrol the first customer to start building the network.'
              : 'Try a different status.'}
          />
        ) : (
          <>
            <Table columns={['Customer', 'Bank', 'Key', 'Status', 'Enrolled', '']}>
              {visible.map((customer) => (
                <tr key={customer.id}>
                  <td className="lead">{customer.name}</td>
                  <td className="muted">{customer.bankName}</td>
                  <td className="mono faint">{customer.oprfKeyId ?? '—'}</td>
                  <td><Pill tone={customerTone(customer.customerStatus)}>{humanise(customer.customerStatus)}</Pill></td>
                  <td className="muted num">{formatDate(customer.createdAt)}</td>
                  <td>
                    {customer.customerStatus !== 'BLOCKED' && (
                      <Button size="sm" variant="danger" icon={Ban} onClick={() => setBlocking(customer)}>
                        Block
                      </Button>
                    )}
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

      {enrolling && (
        <EnrolCustomer
          banks={banks.data ?? []}
          onClose={() => setEnrolling(false)}
          onEnrolled={() => customers.reload()}
        />
      )}

      {blocking && (
        <Sheet
          title="Block this customer"
          lede={`${blocking.name}, ${blocking.bankName}`}
          onClose={() => setBlocking(null)}
          footer={(
            <>
              <Button onClick={() => setBlocking(null)} disabled={working}>Cancel</Button>
              <Button variant="danger" icon={Ban} onClick={confirmBlock} busy={working}>Block and notify the network</Button>
            </>
          )}
        >
          <Notice tone="warn">
            Blocks this record and suspends every other enrolment with the same pseudonym,
            at every member bank.
          </Notice>
        </Sheet>
      )}
    </Page>
  );
}
