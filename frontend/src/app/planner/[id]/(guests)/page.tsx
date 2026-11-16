'use client';

import { useParams } from 'next/navigation';
import { useState, type FormEvent } from 'react';
import { useConnection } from '@/components/plately/Connection';
import { Button, Card, FailureCard, Field, SelectField, Sheet } from '@/components/plately/ui';
import EventPayment from '@/components/staff/EventPayment';
import { Empty } from '@/components/staff/PageHead';
import { FAILURE_ID, useAction } from '@/hooks/useAction';
import { failureOf } from '@/lib/http';
import { plannerApi, spaced, useEvent, useEventGuests, useEventTables, type EventGuest } from '@/lib/planner';

export default function EventGuestsPage() {
  const { id } = useParams<{ id: string }>();
  const { lost } = useConnection();
  const event = useEvent(id);
  const guests = useEventGuests(id);
  const tables = useEventTables(id);
  const act = useAction();
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [tableId, setTableId] = useState('');
  const [problem, setProblem] = useState<string | null>(null);
  const [removing, setRemoving] = useState<EventGuest | null>(null);
  const [sameName, setSameName] = useState<string | null>(null);
  const [csvFailure, setCsvFailure] = useState<string | null>(null);

  const data = event.data;
  const list = guests.data ?? [];
  const full = data ? list.length >= data.paidGuests : false;
  const assigned = data?.seatingMode === 'ASSIGNED';

  const add = async (e: FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setProblem('Who are you inviting?');
      return;
    }
    setProblem(null);
    setSameName(null);
    const typed = name.trim();
    const twin = list.some((guest) => guest.name.toLowerCase() === typed.toLowerCase());
    const worked = await act.run('add', () =>
      plannerApi.addGuest(id, { name: typed, phone: phone.trim() || undefined, email: email.trim() || undefined, tableId: assigned && tableId ? tableId : undefined }),
    );
    if (worked) {
      setSameName(twin ? typed : null);
      setName('');
      setPhone('');
      setEmail('');
    }
  };

  // The list as a file, to share the codes yourself.
  const download = async () => {
    setCsvFailure(null);
    try {
      const url = URL.createObjectURL(await plannerApi.exportCsv(id));
      const link = document.createElement('a');
      link.href = url;
      link.download = 'guests.csv';
      link.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      setCsvFailure(failureOf(error).message);
    }
  };

  const status = (guest: EventGuest) => {
    const where = guest.tableNumber != null ? `Table ${guest.tableNumber} · Seat ${guest.seatPosition} · ` : '';
    return `${where}${guest.platesUsed} of ${guest.plateLimit == null ? 'unlimited' : guest.plateLimit} plates used`;
  };

  return (
    <div className="flex flex-col gap-5">
      <div id={FAILURE_ID}>{act.failure && removing === null ? <FailureCard title={act.failure.title} message={act.failure.message} /> : null}</div>
      {sameName ? (
        <p role="status" className="animate-fade text-[15px] leading-snug text-mustard">
          There are now two guests called {sameName}. Their codes are different, so both are fine.
        </p>
      ) : null}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_2fr]">
        <Card className="h-fit">
          <h2 className="mb-3 font-heading text-[22px] font-normal text-bottle">Add a guest</h2>
          {data?.status === 'DRAFT' ? (
            <p className="mb-3 text-[15px] leading-snug text-hand">
              Pay for the event first. Until then it is a draft and codes cannot be made.{' '}
              <a href="#more-guests" className="font-bold underline underline-offset-4">
                Go and pay
              </a>
            </p>
          ) : null}
          <form noValidate onSubmit={(e) => void add(e)} className="flex flex-col gap-3.5">
            <Field label="Guest name" value={name} onChange={(e) => setName(e.target.value)} error={problem ?? undefined} placeholder="Mrs Folake Adeyemi" autoComplete="off" />
            <Field label="Phone (optional)" type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} autoComplete="off" />
            <Field label="Email (optional)" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="off" />
            {assigned ? (
              <SelectField label="Table" value={tableId} onChange={(e) => setTableId(e.target.value)} hint="They get the next free seat there.">
                <option value="">Next free seat anywhere</option>
                {(tables.data ?? []).map((table) => (
                  <option key={table.tableId} value={table.tableId}>
                    Table {table.displayNumber ?? table.tableNumber}
                  </option>
                ))}
              </SelectField>
            ) : null}
            <Button type="submit" tone="primary" disabled={lost || act.busy !== null}>
              Add guest
            </Button>
            {full && data?.status === 'ACTIVE' ? (
              <a href="#more-guests" className="flex min-h-[48px] items-center justify-center rounded-full border-[1.5px] border-bottle px-5 font-bold text-bottle">
                Pay for more guests
              </a>
            ) : null}
          </form>
        </Card>

        <section aria-label="The guest list" className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-[17px] font-bold">{data?.status === 'DRAFT' ? 'The event is not paid for yet' : `${list.length} of ${data?.paidGuests ?? 0} paid places used`}</p>
            <Button full={false} tone="light" disabled={list.length === 0} onClick={() => void download()}>
              Download the list
            </Button>
          </div>
          {csvFailure ? <FailureCard title="We couldn't make the file" message={csvFailure} /> : null}
          {guests.isError ? <FailureCard title="We couldn't load the guests" message={failureOf(guests.error).message} /> : null}
          {guests.data && list.length === 0 ? <Empty>Nobody on the list yet. Each guest you add gets their own code, which is their seat and their meal.</Empty> : null}
          <ul className="flex flex-col gap-2.5">
            {list.map((guest) => (
              <li key={guest.id} className="flex flex-wrap items-center gap-3 rounded-lg bg-paperlight p-3.5 text-ink shadow-[0_8px_20px_rgba(0,0,0,0.22)]">
                <div className="font-heading text-[26px] tracking-[0.06em] text-bottle">{spaced(guest.code)}</div>
                <div className="min-w-[160px] flex-1">
                  <div className="font-bold leading-snug">{guest.name}</div>
                  <div className="text-sm text-quiet">{status(guest)}</div>
                </div>
                <div className="flex flex-wrap gap-2">
                  {guest.plateLimit != null ? (
                    <Button full={false} tone="outline" disabled={lost || act.busy !== null} onClick={() => void act.run(guest.id, () => plannerApi.setLimit(id, guest.id, guest.plateLimit! + 1))}>
                      One more plate
                    </Button>
                  ) : null}
                  <Button full={false} tone="outline" aria-label={`Remove ${guest.name}`} disabled={lost || act.busy !== null} onClick={() => setRemoving(guest)}>
                    Remove
                  </Button>
                </div>
              </li>
            ))}
          </ul>
          <p className="text-sm text-soft">Download the list to share the codes yourself. Emailing codes from Plately is coming soon.</p>
        </section>
      </div>

      {data ? (
        <div id="more-guests" className="max-w-xl scroll-mt-4">
          <EventPayment event={data} />
        </div>
      ) : null}

      <Sheet open={removing !== null} onClose={() => { setRemoving(null); act.clear(); }} label="Remove this guest">
        <h2 className="font-heading text-[28px] font-normal text-bottle">Take {removing?.name} off the list?</h2>
        <p className="mb-4 mt-2 text-[15px] leading-snug text-quiet">Their code stops working. A guest who has already sat down cannot be removed.</p>
        {act.failure ? <FailureCard className="mb-3" title={act.failure.title} message={act.failure.message} /> : null}
        <div className="flex flex-col gap-2.5">
          <Button
            big
            tone="primary"
            disabled={lost || act.busy !== null}
            onClick={async () => {
              if (removing && (await act.run(removing.id, () => plannerApi.removeGuest(id, removing.id)))) setRemoving(null);
            }}
          >
            Yes, remove them
          </Button>
          <Button big tone="outline" onClick={() => { setRemoving(null); act.clear(); }}>
            Keep them
          </Button>
        </div>
      </Sheet>
    </div>
  );
}

