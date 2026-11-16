'use client';

import { useParams } from 'next/navigation';
import { useState, type FormEvent } from 'react';
import { useConnection } from '@/components/plately/Connection';
import { Button, Card, FailureCard, Field, Sheet } from '@/components/plately/ui';
import { Empty } from '@/components/staff/PageHead';
import QrCard from '@/components/staff/QrCard';
import { FAILURE_ID, useAction } from '@/hooks/useAction';
import { failureOf } from '@/lib/http';
import { plannerApi, useEventTables, type EventTable } from '@/lib/planner';

export default function EventTablesPage() {
  const { id } = useParams<{ id: string }>();
  const { lost } = useConnection();
  const tables = useEventTables(id);
  const act = useAction();
  const [seats, setSeats] = useState(8);
  const [number, setNumber] = useState('');
  const [problem, setProblem] = useState<string | null>(null);
  const [removing, setRemoving] = useState<EventTable | null>(null);
  const rows = tables.data ?? [];

  const add = async (e: FormEvent) => {
    e.preventDefault();
    const wanted = number.trim() ? Number(number) : undefined;
    if (wanted !== undefined && (!Number.isInteger(wanted) || wanted < 1)) {
      setProblem('A table number is 1 or more. Leave it empty for the next free one.');
      return;
    }
    setProblem(null);
    if (await act.run('add', () => plannerApi.addTable(id, seats, wanted))) setNumber('');
  };

  return (
    <div className="flex flex-col gap-5">
      <div id={FAILURE_ID}>{act.failure && removing === null ? <FailureCard title={act.failure.title} message={act.failure.message} /> : null}</div>

      <div className="grid grid-cols-1 gap-6 print:hidden lg:grid-cols-[1fr_2fr]">
        <Card className="h-fit">
          <h2 className="mb-3 font-heading text-[22px] font-normal text-bottle">Add a table</h2>
          <form noValidate onSubmit={(e) => void add(e)} className="flex flex-col gap-3.5">
            <div>
              <div id="seat-count" className="mb-1.5 text-sm font-bold text-bottle">
                Seats
              </div>
              <div role="group" aria-labelledby="seat-count" className="flex items-center gap-3">
                <button type="button" aria-label="One seat fewer" disabled={seats <= 1} onClick={() => setSeats(seats - 1)} className="h-12 w-12 rounded-full border-[1.5px] border-bottle text-2xl font-bold text-bottle disabled:opacity-40">
                  −
                </button>
                <div aria-live="polite" className="min-w-[4.5rem] text-center text-lg font-bold text-ink">
                  {seats} {seats === 1 ? 'seat' : 'seats'}
                </div>
                <button type="button" aria-label="One seat more" disabled={seats >= 20} onClick={() => setSeats(seats + 1)} className="h-12 w-12 rounded-full bg-bottle text-2xl font-bold text-cream disabled:opacity-40">
                  +
                </button>
              </div>
            </div>
            <Field label="Table number (optional)" inputMode="numeric" value={number} onChange={(e) => setNumber(e.target.value)} error={problem ?? undefined} hint="Tables are numbered from 1 for each event." />
            <Button type="submit" tone="primary" disabled={lost || act.busy !== null}>
              Add table
            </Button>
          </form>
        </Card>

        <section aria-label="The tables" className="flex flex-col gap-2.5">
          {tables.isError ? <FailureCard title="We couldn't load the tables" message={failureOf(tables.error).message} /> : null}
          {tables.data && rows.length === 0 ? <Empty>No tables yet. Add your first one and its card appears below.</Empty> : null}
          <ul className="flex flex-col gap-2.5">
            {rows.map((row) => (
              <li key={row.tableId} className="flex items-center gap-3 rounded-lg bg-paperlight p-3.5 text-ink shadow-[0_8px_20px_rgba(0,0,0,0.22)]">
                <div className="min-w-0 flex-1">
                  <div className="font-heading text-xl text-bottle">Table {row.displayNumber ?? row.tableNumber}</div>
                  <div className="text-sm text-quiet">
                    {row.capacity} {row.capacity === 1 ? 'seat' : 'seats'}
                  </div>
                </div>
                <Button full={false} tone="outline" aria-label={`Remove table ${row.displayNumber ?? row.tableNumber}`} disabled={lost || act.busy !== null} onClick={() => setRemoving(row)}>
                  Remove
                </Button>
              </li>
            ))}
          </ul>
        </section>
      </div>

      {rows.length > 0 ? (
        <section aria-label="Table cards" className="flex flex-col gap-3">
          <div className="flex flex-wrap items-end justify-between gap-3 print:hidden">
            <div>
              <h2 className="font-heading text-[26px] font-normal">Your table cards</h2>
              <p className="text-[15px] text-soft">Guests scan the card at their table, then type their own code. Download one, or print them all.</p>
            </div>
            <Button full={false} tone="light" onClick={() => window.print()}>
              Print all
            </Button>
          </div>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 print:grid-cols-3">
            {rows.map((row) => (
              <QrCard key={row.tableId} table={row} />
            ))}
          </div>
        </section>
      ) : null}

      <Sheet open={removing !== null} onClose={() => { setRemoving(null); act.clear(); }} label="Remove this table">
        <h2 className="font-heading text-[28px] font-normal text-bottle">Remove Table {removing?.displayNumber ?? removing?.tableNumber}?</h2>
        <p className="mb-4 mt-2 text-[15px] leading-snug text-quiet">Its card stops working.</p>
        {act.failure ? <FailureCard className="mb-3" title={act.failure.title} message={act.failure.message} /> : null}
        <div className="flex flex-col gap-2.5">
          <Button
            big
            tone="primary"
            disabled={lost || act.busy !== null}
            onClick={async () => {
              if (removing && (await act.run(removing.tableId, () => plannerApi.removeTable(id, removing.tableId)))) setRemoving(null);
            }}
          >
            Yes, remove it
          </Button>
          <Button big tone="outline" onClick={() => { setRemoving(null); act.clear(); }}>
            Keep it
          </Button>
        </div>
      </Sheet>
    </div>
  );
}
