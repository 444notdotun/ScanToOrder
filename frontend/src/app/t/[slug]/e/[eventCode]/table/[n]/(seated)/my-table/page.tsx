'use client';

import { Brand, Loader } from '@/components/plately/Logo';
import { Card, Eyebrow, FailureCard } from '@/components/plately/ui';
import { useMyEvent, useSession } from '@/lib/guest';
import { failureOf } from '@/lib/http';

// An event guest's table: who is sitting with them, and how many plates they have had.
export default function MyTablePage() {
  const session = useSession().data!;
  const mine = useMyEvent();
  const seat = session.seats.find((candidate) => candidate.mine);
  const others = session.seats.filter((candidate) => !candidate.mine && candidate.status === 'OCCUPIED');

  if (mine.isPending) return <Loader message="Setting your table…" />;
  if (mine.isError) {
    const failed = failureOf(mine.error, "We couldn't open your table");
    return (
      <main className="mx-auto flex w-full max-w-md flex-1 flex-col gap-4 px-5 pt-8 md:max-w-xl">
        <FailureCard title={failed.title} message={failed.message} />
      </main>
    );
  }
  const event = mine.data;
  const limited = event.plateLimit !== null && event.plateLimit !== undefined;

  return (
    <main className="mx-auto flex w-full max-w-md flex-1 animate-rise flex-col gap-4 px-5 pb-6 pt-6 md:max-w-xl">
      <header className="flex flex-col gap-1.5">
        <Brand size={22} />
        <Eyebrow className="mt-1">
          {event.eventName}
          {event.venue ? ` · ${event.venue}` : ''}
        </Eyebrow>
        <h1 className="font-heading text-[34px] font-normal leading-[1.1]">
          Table {session.displayNumber}
          {seat ? `, Seat ${seat.position}` : ''}
        </h1>
      </header>

      <Card>
        <h2 className="mb-2 font-heading text-[22px] font-normal text-bottle">Your plates</h2>
        <p className="text-[17px] leading-snug text-ink">
          You have had {event.platesUsed}
          {limited ? ` of ${event.plateLimit}` : ''}. {limited && event.platesUsed >= (event.plateLimit as number) ? 'The host can add more for you.' : 'Order one at a time from Plates.'}
        </p>
      </Card>

      <Card>
        <h2 className="mb-3 font-heading text-[22px] font-normal text-bottle">Sitting with you</h2>
        {others.length === 0 ? <p className="text-[15px] text-quiet">Nobody else has sat down at this table yet.</p> : null}
        <ul className="flex flex-col divide-y divide-rule">
          {others.map((other) => (
            <li key={other.seatId} className="flex items-center gap-3 py-2.5 first:pt-0">
              <span className="flex h-9 w-9 flex-none items-center justify-center rounded-full bg-bottle text-sm font-bold text-cream">{other.position}</span>
              <span className="text-base font-bold text-ink">{other.guestName ?? 'A guest'}</span>
            </li>
          ))}
        </ul>
      </Card>
    </main>
  );
}
