'use client';

import { useState } from 'react';
import CloseTableSheet from '@/components/staff/CloseTableSheet';
import StaffShell from '@/components/staff/StaffShell';
import Ticket, { agedMinutes } from '@/components/staff/Ticket';
import { useConnection } from '@/components/plately/Connection';
import { Button, FailureCard, Field, Sheet } from '@/components/plately/ui';
import { FAILURE_ID, useAction } from '@/hooks/useAction';
import { useNow } from '@/hooks/useNow';
import { failureOf } from '@/lib/http';
import {
  minutesText,
  reasonSaid,
  staffApi,
  useCalls,
  useFloorTables,
  useReady,
  useStock,
  useVisits,
  type FloorSeat,
  type FloorTable,
  type ServiceOrder,
  type WaiterCall,
} from '@/lib/staff';
import { useAuthStore } from '@/store/authStore';
import { useWaiterTab } from '@/store/waiterTab';
import { WaiterBar, WaiterNav } from '@/components/staff/RoleBars';

function Section({ title, count, empty, children }: { title: string; count: number; empty: string; children: React.ReactNode }) {
  return (
    <section aria-label={title} className="flex flex-col gap-3">
      <div className="flex items-baseline gap-2.5">
        <h2 className="font-heading text-[26px] font-normal">{title}</h2>
        <span className="rounded-full bg-cream/15 px-2.5 py-0.5 text-sm font-bold text-cream">{count}</span>
      </div>
      {count === 0 ? <p className="rounded-[20px] bg-paper p-4 text-[15px] text-quiet">{empty}</p> : children}
    </section>
  );
}

type Act = ReturnType<typeof useAction>;

// One plate to carry: the table and seat are the biggest thing on it, so a glance across the room is enough.
function CarryCard({ order, minutes, disabled, onDelivered }: { order: ServiceOrder; minutes: number; disabled: boolean; onDelivered: () => void }) {
  return (
    <article
      aria-label={`${order.orderNumber}, table ${order.tableNumber ?? ''}${order.seatPosition != null ? `, seat ${order.seatPosition}` : ''}`}
      className={`flex animate-fade flex-col gap-3 rounded-2xl bg-paperlight p-4 text-ink shadow-[0_8px_20px_rgba(0,0,0,0.22)] ${order.flag ? 'border-[2.5px] border-mustard' : ''}`}
    >
      <div className="flex gap-4">
        <div className="flex h-[76px] w-[76px] flex-none flex-col items-center justify-center rounded-xl bg-bottle text-cream">
          <span className="text-[10px] font-bold tracking-[0.14em]">TABLE</span>
          <span className="font-heading text-[36px] leading-none">{order.tableNumber ?? '–'}</span>
          {order.seatPosition != null ? <span className="text-[10px] font-bold tracking-[0.14em]">SEAT {order.seatPosition}</span> : null}
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-baseline justify-between gap-3 text-[12px] font-bold">
            <span className="min-w-0 break-all uppercase tracking-[0.06em] text-quiet">{order.orderNumber}</span>
            <span className="flex-none whitespace-nowrap text-good">{minutes <= 0 ? 'ready just now' : `ready ${minutesText(minutes)}`}</span>
          </div>
          <ul className="mt-1 flex flex-col text-[17px] font-bold leading-snug">
            {order.items.map((line, index) => (
              <li key={`${line}-${index}`}>{line}</li>
            ))}
          </ul>
          {order.guestName ? <div className="text-[15px] text-quiet">{order.guestName}</div> : null}
        </div>
      </div>
      {(order.instructions ?? []).map((asked) => (
        <div key={asked} className="font-hand text-[23px] font-bold leading-[1.1] text-hand">
          {asked}
        </div>
      ))}
      {order.note ? <div className="font-hand text-[23px] font-bold leading-[1.1] text-hand">{order.note}</div> : null}
      {order.flag ? <div className="text-[15px] font-bold leading-snug text-[#8A5A00]">{order.flag}</div> : null}
      <Button big tone="primary" disabled={disabled} onClick={onDelivered}>
        Delivered
      </Button>
    </article>
  );
}

function Orders({ act, part, onSwap }: { act: Act; part: 'carry' | 'visits'; onSwap: (order: ServiceOrder) => void }) {
  const { lost } = useConnection();
  const ready = useReady();
  const visits = useVisits();
  const now = useNow();
  const carry = ready.data ?? [];
  const visit = visits.data ?? [];

  if (ready.isError || visits.isError) {
    return <FailureCard title="We couldn't load the orders" message={failureOf(ready.error ?? visits.error).message} />;
  }
  if (ready.isPending || visits.isPending) {
    return (
      <p role="status" className="text-soft">
        Fetching the orders…
      </p>
    );
  }

  return (
    <div className="grid grid-cols-1 gap-6">
      {part === 'carry' ? (
      <Section title="To carry" count={carry.length} empty="Nothing ready yet. The kitchen will send it here.">
        {carry.map((order) => (
          <CarryCard
            key={order.orderId}
            order={order}
            minutes={agedMinutes(order.minutesWaiting, ready.dataUpdatedAt, now)}
            disabled={lost || act.busy !== null}
            onDelivered={() => void act.run(order.orderId, () => staffApi.deliver(order.orderId))}
          />
        ))}
      </Section>
      ) : (
      <Section title="Needs a visit" count={visit.length} empty="No table needs you for an order.">
        {visit.map((order) => {
          const minutes = agedMinutes(order.minutesWaiting, visits.dataUpdatedAt, now);
          if (order.step === 'UNAVAILABLE') {
            return (
              <Ticket
                key={order.orderId}
                order={order}
                minutes={minutes}
                waitLabel="waiting"
                mood="problem"
                flag={`The kitchen can't make this: ${reasonSaid(order.unavailableReason)}. Go and offer something else.`}
              >
                <Button big disabled={lost || act.busy !== null} onClick={() => onSwap(order)}>
                  They picked something else
                </Button>
                <Button big tone="outline" disabled={lost || act.busy !== null} onClick={() => void act.run(order.orderId, () => staffApi.refundRequest(order.orderId))}>
                  They want their money back
                </Button>
              </Ticket>
            );
          }
          return (
            <Ticket key={order.orderId} order={order} minutes={minutes} waitLabel="waiting" flag="Refund asked. The manager settles it with the guest." />
          );
        })}
      </Section>
      )}
    </div>
  );
}

function Calls({ act, columns = true }: { act: Act; columns?: boolean }) {
  const { lost } = useConnection();
  const calls = useCalls();
  const now = useNow();
  const profile = useAuthStore((state) => state.workerProfile);
  const myFirstName = (profile?.fullName ?? profile?.username ?? '').trim().split(/\s+/)[0];

  if (calls.isError) return <FailureCard title="We couldn't load the calls" message={failureOf(calls.error).message} />;
  if (calls.isPending) {
    return (
      <p role="status" className="text-soft">
        Fetching the calls…
      </p>
    );
  }
  if (calls.data.length === 0) {
    return <p className="rounded-[20px] bg-paper p-4 text-[15px] text-quiet">Nobody is calling. When a guest taps for help it lands here.</p>;
  }

  const stateOf = (call: WaiterCall) => {
    if (call.state === 'OPEN') return `Waiting ${minutesText(agedMinutes(call.minutesWaiting, calls.dataUpdatedAt, now))}`;
    return call.takenBy === myFirstName ? 'You are on it' : `${call.takenBy ?? 'Someone'} is on it`;
  };

  return (
    <ul className={`grid grid-cols-1 gap-3 ${columns ? 'md:grid-cols-2' : ''}`}>
      {calls.data.map((call) => {
        const mine = call.state === 'TAKEN' && call.takenBy === myFirstName;
        return (
          <li
            key={call.callId}
            className={`flex items-center justify-between gap-3 rounded-lg bg-paperlight p-4 text-ink shadow-[0_8px_20px_rgba(0,0,0,0.22)] ${call.state === 'OPEN' ? 'border-2 border-mustard' : 'border-[1.5px] border-rule'}`}
          >
            <div className="min-w-0">
              <div className="font-heading text-xl text-bottle">{call.where}</div>
              {call.what ? <div className="text-[17px] font-bold leading-snug">{asked(call.what)}</div> : null}
              <div className="text-sm font-bold text-quiet">{stateOf(call)}</div>
            </div>
            {call.state === 'OPEN' ? (
              <Button full={false} big tone="primary" disabled={lost || act.busy !== null} onClick={() => void act.run(call.callId, () => staffApi.take(call.callId))}>
                I&apos;ll go
              </Button>
            ) : mine ? (
              <Button full={false} big tone="primary" disabled={lost || act.busy !== null} onClick={() => void act.run(call.callId, () => staffApi.done(call.callId))}>
                Sorted
              </Button>
            ) : null}
          </li>
        );
      })}
    </ul>
  );
}

// The backend files a call as "ASSISTANCE - Could we have some water?": the waiter reads only what was asked.
function asked(what: string): string {
  return what.replace(/^[A-Z_]+ - /, '');
}

function seatLine(seat: FloorSeat): { title: string; note: string } {
  if (seat.status === 'OCCUPIED') return { title: seat.guestFirstName ?? 'Someone', note: 'Seated' };
  if (seat.status === 'HELD') {
    const minutes = Math.max(1, Math.ceil((seat.secondsLeft ?? 0) / 60));
    return { title: 'Saved for a friend', note: `Held for ${minutes} more minute${minutes === 1 ? '' : 's'}` };
  }
  return { title: 'Free', note: 'Nobody here' };
}

function Tables({ act, onClose }: { act: Act; onClose: (table: FloorTable) => void }) {
  const { lost } = useConnection();
  const tables = useFloorTables();

  if (tables.isError) return <FailureCard title="We couldn't load the tables" message={failureOf(tables.error).message} />;
  if (tables.isPending) {
    return (
      <p role="status" className="text-soft">
        Fetching the tables…
      </p>
    );
  }
  if (tables.data.length === 0) {
    return <p className="rounded-[20px] bg-paper p-4 text-[15px] text-quiet">No tables are set up yet. The manager adds them.</p>;
  }

  const used = (table: FloorTable) => table.seats.filter((seat) => seat.status !== 'VACANT').length;
  const active = tables.data.filter((table) => used(table) > 0);
  const empty = tables.data.filter((table) => used(table) === 0);

  return (
    <div className="flex flex-col gap-5">
      {active.length === 0 ? <p className="rounded-[20px] bg-paper p-4 text-[15px] text-quiet">No table has guests right now.</p> : null}
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
        {active.map((table) => {
          const label = `Table ${table.displayNumber ?? table.tableNumber}`;
          const seated = table.seats.filter((seat) => seat.status === 'OCCUPIED');
          return (
            <section key={table.tableId} aria-label={label} className="flex flex-col gap-2 rounded-[20px] border-[3px] border-mustard bg-paper p-4 text-ink shadow-[0_10px_28px_rgba(0,0,0,0.22)]">
              <div className="flex items-baseline justify-between gap-3">
                <h2 className="font-heading text-[36px] font-normal leading-none text-bottle">{label}</h2>
                <span className="text-sm font-bold text-quiet">
                  {used(table)} of {table.seats.length} in use
                </span>
              </div>
              <ul className="flex flex-col">
                {table.seats.map((seat) => {
                  const line = seatLine(seat);
                  return (
                    <li key={seat.seatId} className="flex items-center gap-3 border-t border-dashed border-rule2 py-2">
                      <span
                        aria-hidden="true"
                        className={`flex h-12 w-12 flex-none items-center justify-center rounded-full font-heading text-[24px] ${
                          seat.status === 'OCCUPIED' ? 'bg-bottle text-cream' : seat.status === 'HELD' ? 'bg-mustard text-ink' : 'border-[1.5px] border-dashed border-quiet text-ink'
                        }`}
                      >
                        {seat.position}
                      </span>
                      <div className="min-w-0 flex-1">
                        <div className="text-[13px] font-bold uppercase tracking-[0.1em] text-quiet">Seat {seat.position}</div>
                        <div className="font-bold leading-tight">{line.title}</div>
                        <div className="text-sm text-quiet">{line.note}</div>
                      </div>
                    </li>
                  );
                })}
              </ul>
              {/* Free a seat and close the table sit side by side, so neither is pushed off the screen. */}
              <div className="mt-1 flex flex-wrap gap-2">
                {seated.map((seat) => (
                  <Button
                    key={seat.seatId}
                    full={false}
                    tone="outline"
                    big
                    className="min-w-[132px] flex-1"
                    aria-label={`Free seat ${seat.position} at ${label.toLowerCase()}`}
                    disabled={lost || act.busy !== null}
                    onClick={() => void act.run(seat.seatId, () => staffApi.release(seat.seatId))}
                  >
                    Free seat {seat.position}
                  </Button>
                ))}
                <Button full={false} big tone="bottle" className="min-w-[132px] flex-1" disabled={lost || act.busy !== null} onClick={() => onClose(table)}>
                  Close this table
                </Button>
              </div>
            </section>
          );
        })}
      </div>

      {empty.length > 0 ? (
        <section aria-label="Empty tables" className="flex flex-col gap-2">
          <h2 className="text-[13px] font-bold uppercase tracking-[0.12em] text-soft">Empty tables, ready for guests</h2>
          <ul className="flex flex-wrap gap-2">
            {empty.map((table) => (
              <li key={table.tableId} className="rounded-full border-[1.5px] border-cream/45 px-3.5 py-1.5 text-[15px] font-bold text-cream">
                Table {table.displayNumber ?? table.tableNumber}
                <span className="ml-1.5 font-normal text-soft">{table.seats.length} {table.seats.length === 1 ? 'seat' : 'seats'}</span>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </div>
  );
}

// The tables in use at a glance, beside the plates to carry: seat dots, Free seat and Close this table side by side, empty tables as chips.
function TableGlance({ act, onClose }: { act: Act; onClose: (table: FloorTable) => void }) {
  const { lost } = useConnection();
  const tables = useFloorTables();
  if (tables.isError) return <FailureCard title="We couldn't load the tables" message={failureOf(tables.error).message} />;
  if (tables.isPending) {
    return (
      <p role="status" className="text-soft">
        Fetching the tables…
      </p>
    );
  }
  const used = (table: FloorTable) => table.seats.filter((seat) => seat.status !== 'VACANT').length;
  const active = tables.data.filter((table) => used(table) > 0);
  const empty = tables.data.filter((table) => used(table) === 0);
  return (
    <div className="flex flex-col gap-3">
      {tables.data.length === 0 ? <p className="rounded-[20px] bg-paper p-4 text-[15px] text-quiet">No tables are set up yet. The manager adds them.</p> : null}
      {tables.data.length > 0 && active.length === 0 ? <p className="rounded-[20px] bg-paper p-4 text-[15px] text-quiet">No table has guests right now.</p> : null}
      {active.map((table) => {
        const label = `Table ${table.displayNumber ?? table.tableNumber}`;
        const seated = table.seats.filter((seat) => seat.status === 'OCCUPIED');
        return (
          <article key={table.tableId} aria-label={label} className="flex flex-col gap-3 rounded-[20px] border-[3px] border-mustard bg-paper p-4 text-ink shadow-[0_10px_28px_rgba(0,0,0,0.22)]">
            <div className="flex items-baseline justify-between gap-3">
              <h3 className="font-heading text-[24px] font-normal leading-none text-bottle">{label}</h3>
              <span className="text-sm text-quiet">
                {used(table)} of {table.seats.length} in use
              </span>
            </div>
            <ul aria-label={`Seats at ${label}`} className="flex flex-wrap gap-2">
              {table.seats.map((seat) => (
                <li
                  key={seat.seatId}
                  title={seat.status === 'OCCUPIED' ? 'Seated' : seat.status === 'HELD' ? 'Saved for a friend' : 'Free'}
                  className={`flex h-8 w-8 items-center justify-center rounded-full text-sm font-bold ${
                    seat.status === 'OCCUPIED' ? 'bg-bottle text-cream' : seat.status === 'HELD' ? 'bg-mustard text-ink' : 'border-[1.5px] border-dashed border-quiet text-ink'
                  }`}
                >
                  <span className="sr-only">{seat.status === 'OCCUPIED' ? 'Seated, seat ' : seat.status === 'HELD' ? 'Saved, seat ' : 'Free, seat '}</span>
                  {seat.position}
                </li>
              ))}
            </ul>
            <div className="flex flex-wrap gap-2">
              {seated.map((seat) => (
                <Button
                  key={seat.seatId}
                  full={false}
                  tone="outline"
                  className="min-w-[110px] flex-1"
                  aria-label={`Free seat ${seat.position} at ${label.toLowerCase()}`}
                  disabled={lost || act.busy !== null}
                  onClick={() => void act.run(seat.seatId, () => staffApi.release(seat.seatId))}
                >
                  Free seat {seat.position}
                </Button>
              ))}
              <Button full={false} tone="bottle" className="min-w-[110px] flex-1" disabled={lost || act.busy !== null} onClick={() => onClose(table)}>
                Close this table
              </Button>
            </div>
          </article>
        );
      })}
      {empty.length > 0 ? (
        <ul aria-label="Empty tables" className="flex flex-wrap gap-2">
          {empty.map((table) => (
            <li key={table.tableId} className="rounded-full border-[1.5px] border-cream/45 px-3 py-1 text-sm font-bold text-cream">
              Table {table.displayNumber ?? table.tableNumber} · empty
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}

// Choose what the guest will have instead. Plates can be counted up, since an order can hold several.
function SwapSheet({ order, act, onDone }: { order: ServiceOrder | null; act: Act; onDone: () => void }) {
  const { lost } = useConnection();
  const stock = useStock();
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [note, setNote] = useState('');
  const [problem, setProblem] = useState<string | null>(null);

  const plates = (stock.data ?? []).filter((item) => item.available);
  const chosen = plates.filter((item) => (counts[item.itemId] ?? 0) > 0);
  const change = (id: string, by: number) => setCounts((now) => ({ ...now, [id]: Math.max(0, Math.min(20, (now[id] ?? 0) + by)) }));

  const send = async () => {
    if (!order) return;
    if (chosen.length === 0) {
      setProblem('Choose at least one plate for the guest.');
      return;
    }
    const worked = await act.run(order.orderId, () =>
      staffApi.swap(order.orderId, chosen.map((item) => ({ itemName: item.itemName, quantity: counts[item.itemId] })), note),
    );
    if (worked) {
      setCounts({});
      setNote('');
      onDone();
    }
  };

  return (
    <Sheet open={order !== null} onClose={onDone} label="What they chose instead">
      <h2 className="font-heading text-[28px] font-normal text-bottle">What did they choose?</h2>
      <p className="mb-4 mt-2 text-[15px] leading-snug text-quiet">
        {order ? `${order.orderNumber}: ${order.items.join(', ')}${order.guestName ? ` for ${order.guestName}` : ''}. ` : ''}
        It goes back to the kitchen as a new ticket. Any price difference is settled at the table.
      </p>
      {stock.isPending ? <p className="text-quiet">Fetching the menu…</p> : null}
      {stock.isError ? <FailureCard title="We couldn't load the menu" message={failureOf(stock.error).message} /> : null}
      <ul className="flex flex-col">
        {plates.map((item) => (
          <li key={item.itemId} className="flex items-center justify-between gap-3 border-t border-dashed border-rule2 py-2">
            <span className="font-bold leading-tight">{item.itemName}</span>
            <span className="flex items-center gap-2">
              <button
                type="button"
                aria-label={`One fewer ${item.itemName}`}
                disabled={(counts[item.itemId] ?? 0) === 0}
                onClick={() => change(item.itemId, -1)}
                className="h-11 w-11 rounded-full border-[1.5px] border-bottle text-xl font-bold text-bottle disabled:opacity-40"
              >
                −
              </button>
              <span className="w-6 text-center text-lg font-bold" aria-live="polite">
                {counts[item.itemId] ?? 0}
              </span>
              <button type="button" aria-label={`One more ${item.itemName}`} onClick={() => change(item.itemId, 1)} className="h-11 w-11 rounded-full bg-bottle text-xl font-bold text-cream">
                +
              </button>
            </span>
          </li>
        ))}
      </ul>
      <Field className="mt-3" label="Note (optional)" hint="For example, that the difference was settled at the table." maxLength={300} value={note} onChange={(event) => setNote(event.target.value)} />
      {problem && chosen.length === 0 ? <p className="mt-2 text-sm font-bold text-hand">{problem}</p> : null}
      {act.failure ? <FailureCard className="mt-3" title={act.failure.title} message={act.failure.message} /> : null}
      <div className="mt-4 flex flex-col gap-2.5">
        <Button big tone="primary" disabled={lost || act.busy !== null} onClick={() => void send()}>
          Send to the kitchen
        </Button>
        <Button big tone="outline" onClick={onDone}>
          Never mind
        </Button>
      </div>
    </Sheet>
  );
}

function Floor() {
  const act = useAction();
  const { tab } = useWaiterTab();
  const [swapping, setSwapping] = useState<ServiceOrder | null>(null);
  const [closing, setClosing] = useState<FloorTable | null>(null);

  // A failure shows at the top, except while a sheet is open: the sheet shows its own.
  const sheetOpen = swapping !== null || closing !== null;

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-1 animate-rise flex-col gap-4 px-5 pb-6 pt-4">
      <div>
        <h1 className="font-heading text-[38px] font-normal leading-[1.1]">The floor</h1>
        <p className="mt-1 text-[17px] text-soft">Hot food first, then whoever is waving.</p>
      </div>

      <div id={FAILURE_ID}>{act.failure && !sheetOpen ? <FailureCard title={act.failure.title} message={act.failure.message} /> : null}</div>

      {tab === 'carry' ? (
        // On a wide screen the plates to carry, the calls and the table in use share one screen, so nothing is a tap away.
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)] lg:items-start">
          <div className="flex flex-col gap-6">
            <Orders act={act} part="carry" onSwap={setSwapping} />
            <section aria-label="Calls" className="hidden flex-col gap-3 lg:flex">
              <h2 className="font-heading text-[26px] font-normal">Calls</h2>
              <Calls act={act} columns={false} />
            </section>
          </div>
          <section aria-label="Tables" className="hidden flex-col gap-3 lg:flex">
            <h2 className="font-heading text-[26px] font-normal">Tables</h2>
            <TableGlance act={act} onClose={setClosing} />
          </section>
        </div>
      ) : null}
      {tab === 'visits' ? <Orders act={act} part="visits" onSwap={setSwapping} /> : null}
      {tab === 'calls' ? <Calls act={act} /> : null}
      {tab === 'tables' ? <Tables act={act} onClose={setClosing} /> : null}

      <SwapSheet order={swapping} act={act} onDone={() => { setSwapping(null); act.clear(); }} />
      <CloseTableSheet table={closing} act={act} onDone={() => { setClosing(null); act.clear(); }} />
    </main>
  );
}

export default function WaiterPage() {
  return (
    <StaffShell allow={['WAITER', 'MANAGER']} nav={<WaiterNav />}>
      <Floor />
      <WaiterBar />
    </StaffShell>
  );
}
