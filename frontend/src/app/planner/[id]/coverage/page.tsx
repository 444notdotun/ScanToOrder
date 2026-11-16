'use client';

import { useParams } from 'next/navigation';
import { Card, FailureCard } from '@/components/plately/ui';
import { Empty, Tile } from '@/components/staff/PageHead';
import { failureOf } from '@/lib/http';
import { useCoverage, type CoverageState } from '@/lib/planner';

const STATES: Record<CoverageState, { label: string; style: string }> = {
  NOT_ARRIVED: { label: 'Not arrived', style: 'bg-rule text-ink' },
  SEATED_NOT_ORDERED: { label: 'Seated, not ordered', style: 'bg-mustard text-ink' },
  WAITING_FOR_PLATE: { label: 'Waiting for food', style: 'bg-tomato text-white' },
  EATEN: { label: 'Has eaten', style: 'bg-good text-white' },
};

// Has everyone eaten? Every guest is in exactly one state, so the four counts add up to the list.
export default function CoveragePage() {
  const { id } = useParams<{ id: string }>();
  const coverage = useCoverage(id);
  const data = coverage.data;

  return (
    <div className="flex flex-col gap-5">
      {coverage.isError ? <FailureCard title="We couldn't load this" message={failureOf(coverage.error).message} /> : null}
      {coverage.isPending ? (
        <p role="status" className="text-soft">
          Counting…
        </p>
      ) : null}
      {data ? (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Tile label="Not arrived" value={String(data.notArrived)} />
            <Tile label="Seated, not ordered" value={String(data.seatedNotOrdered)} />
            <Tile label="Waiting for food" value={String(data.waitingForPlate)} />
            <Tile label="Has eaten" value={String(data.eaten)} sub={`of ${data.onList} on the list`} />
          </div>
          {data.onList === 0 ? <Empty>Add guests and this fills in as they arrive, order and eat.</Empty> : null}
          <ul className="grid grid-cols-1 gap-2.5 lg:grid-cols-2">
            {data.guests.map((guest) => (
              <li key={guest.guestId}>
                <Card className="flex items-center justify-between gap-3 !p-3.5">
                  <div className="min-w-0">
                    <div className="font-bold leading-snug">{guest.name}</div>
                    <div className="text-sm text-quiet">
                      {guest.tableNumber != null ? `Table ${guest.tableNumber} · Seat ${guest.seatPosition}` : 'No seat yet'} · {guest.platesUsed} of {guest.plateLimit ?? 'unlimited'} plates
                    </div>
                  </div>
                  <span className={`flex-none rounded-full px-3 py-1 text-sm font-bold ${STATES[guest.state].style}`}>{STATES[guest.state].label}</span>
                </Card>
              </li>
            ))}
          </ul>
        </>
      ) : null}
    </div>
  );
}
