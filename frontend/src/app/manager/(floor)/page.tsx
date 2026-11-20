'use client';

import Link from 'next/link';
import { useState } from 'react';
import { useConnection } from '@/components/plately/Connection';
import { Button, Card, FailureCard } from '@/components/plately/ui';
import CloseTableSheet from '@/components/staff/CloseTableSheet';
import PageHead, { Empty, Tile } from '@/components/staff/PageHead';
import { FAILURE_ID, useAction } from '@/hooks/useAction';
import { failureOf } from '@/lib/http';
import { useFloor, usePlates, useTenant } from '@/lib/manager';
import type { FloorTable } from '@/lib/staff';

// "ASSISTANCE - Could we have some water?" is filed that way: the manager reads only what was asked.
const asked = (what: string) => what.replace(/^[A-Z_]+ - /, '');

const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;

export default function FloorNowPage() {
  const { lost } = useConnection();
  const floor = useFloor();
  const tenant = useTenant();
  const plates = usePlates().data ?? [];
  const soldOut = plates.filter((item) => !item.available).length;
  const act = useAction();
  const [closing, setClosing] = useState<FloorTable | null>(null);
  const data = floor.data;

  const used = (table: FloorTable) => table.seats.filter((seat) => seat.status !== 'VACANT').length;
  const inUse = (data?.tables ?? []).filter((table) => used(table) > 0);
  const empty = (data?.tables ?? []).filter((table) => used(table) === 0);

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-1 animate-rise flex-col gap-5 px-5 pb-6 pt-4">
      <PageHead title={tenant.data?.name ?? 'Your place'} line="The whole room, as it is right now." />

      <div id={FAILURE_ID}>{act.failure && closing === null ? <FailureCard title={act.failure.title} message={act.failure.message} /> : null}</div>

      {floor.isError ? <FailureCard title="We couldn't load the floor" message={failureOf(floor.error).message} /> : null}
      {floor.isPending ? (
        <p role="status" className="text-soft">
          Looking at the room…
        </p>
      ) : null}

      {data ? (
        <>
          {/* The four numbers first, and each says what it means. */}
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Tile large label="Cooking" value={String(data.cooking)} sub={data.paid > 0 ? `${plural(data.paid, 'plate', 'plates')} waiting to start` : 'nothing waiting to start'} />
            <Tile large label="Ready to carry" value={String(data.ready)} sub={data.needVisit > 0 ? `${plural(data.needVisit, 'table needs', 'tables need')} a visit` : 'nothing needs a visit'} />
            <Tile large label="Calls waiting" value={String(data.openCalls)} sub={data.takenCalls > 0 ? `${data.takenCalls} being seen to` : 'nobody is on one yet'} />
            <Tile
              large
              label="Refunds to settle"
              value={String(data.openRefunds)}
              sub={data.overdueRefunds > 0 ? `${data.overdueRefunds} overdue` : 'none overdue'}
            />
          </div>

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-[2fr_1fr]">
            <section aria-label="Tables right now" className="flex flex-col gap-3">
              <h2 className="font-heading text-[26px] font-normal">Tables right now</h2>
              {data.tables.length === 0 ? <Empty>No tables yet. Add them under Tables and print the cards.</Empty> : null}
              {data.tables.length > 0 && inUse.length === 0 ? <Empty>No table has guests right now.</Empty> : null}
              <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                {inUse.map((table) => {
                  const label = `Table ${table.displayNumber ?? table.tableNumber}`;
                  return (
                    <Card key={table.tableId} className="flex flex-col gap-3 border-[3px] border-mustard !p-4">
                      <div className="flex items-baseline justify-between gap-3">
                        <h3 className="font-heading text-[28px] font-normal leading-none text-bottle">{label}</h3>
                        <span className="text-sm font-bold text-quiet">
                          {used(table)} of {table.seats.length} in use · {plural(table.groups, 'group', 'groups')}
                        </span>
                      </div>
                      <ul aria-label={`Seats at ${label}`} className="flex flex-wrap gap-2">
                        {table.seats.map((seat) => (
                          <li
                            key={seat.seatId}
                            title={seat.status === 'OCCUPIED' ? 'Seated' : seat.status === 'HELD' ? 'Saved for a friend' : 'Free'}
                            className={`flex h-9 w-9 items-center justify-center rounded-full text-sm font-bold ${
                              seat.status === 'OCCUPIED'
                                ? 'bg-bottle text-cream'
                                : seat.status === 'HELD'
                                  ? 'bg-mustard text-ink'
                                  : 'border-[1.5px] border-dashed border-quiet text-ink'
                            }`}
                          >
                            <span className="sr-only">{seat.status === 'OCCUPIED' ? 'Seated, seat ' : seat.status === 'HELD' ? 'Saved, seat ' : 'Free, seat '}</span>
                            {seat.position}
                          </li>
                        ))}
                      </ul>
                      <Button big tone="outline" disabled={lost || act.busy !== null} onClick={() => setClosing(table)}>
                        Close this table
                      </Button>
                    </Card>
                  );
                })}
              </div>
              {empty.length > 0 ? (
                <ul aria-label="Empty tables" className="flex flex-wrap gap-2">
                  {empty.map((table) => (
                    <li key={table.tableId} className="rounded-full border-[1.5px] border-cream/45 px-3 py-1 text-sm font-bold text-cream">
                      Table {table.displayNumber ?? table.tableNumber}
                      <span className="sr-only"> is empty</span>
                    </li>
                  ))}
                </ul>
              ) : null}
              {inUse.length > 0 ? <p className="text-sm text-soft">Filled: seated. Gold: saved for a friend. Dashed: free.</p> : null}
            </section>

            <section aria-label="Calls" className="flex flex-col gap-3">
              <h2 className="font-heading text-[26px] font-normal">Calls</h2>
              {data.calls.length === 0 ? <Empty>Nobody is calling.</Empty> : null}
              <ul className="flex flex-col gap-3">
                {data.calls.map((call) => (
                  <li key={call.callId} className="rounded-lg bg-paperlight p-3.5 text-ink shadow-[0_8px_20px_rgba(0,0,0,0.22)]">
                    <div className="font-heading text-lg text-bottle">{call.where}</div>
                    {call.what ? <div className="font-bold leading-snug">{asked(call.what)}</div> : null}
                    <div className="text-sm font-bold text-quiet">
                      {call.state === 'OPEN' ? `Waiting ${call.minutesWaiting} min` : `${call.takenBy ?? 'Someone'} is on it`}
                    </div>
                  </li>
                ))}
              </ul>
              {data.openRefunds > 0 ? (
                <Link href="/manager/refunds" className="flex min-h-[52px] items-center justify-center rounded-full bg-tomato px-5 text-base font-bold text-white">
                  Settle {data.openRefunds} refund{data.openRefunds === 1 ? '' : 's'}
                </Link>
              ) : null}
            </section>
          </div>

          {/* Stock is a single line here: the list itself lives with the menu and the pass. */}
          <p aria-label="Stock" className="flex flex-wrap items-center gap-x-2 gap-y-1 rounded-full bg-cream/10 px-5 py-2 text-[16px] font-bold">
            <span>{plates.length - soldOut} on</span>
            <span aria-hidden="true">·</span>
            <span className={soldOut > 0 ? 'text-mustard' : ''}>{soldOut} sold out</span>
            <span aria-hidden="true">·</span>
            <Link href="/manager/menu" className="flex min-h-[44px] items-center underline underline-offset-4">
              Edit
            </Link>
          </p>
        </>
      ) : null}

      <CloseTableSheet
        table={closing}
        act={act}
        onDone={() => {
          setClosing(null);
          act.clear();
        }}
      />
    </main>
  );
}
