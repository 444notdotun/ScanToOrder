'use client';

import Link from 'next/link';
import { Card, FailureCard } from '@/components/plately/ui';
import PageHead, { Empty } from '@/components/staff/PageHead';
import { failureOf } from '@/lib/http';
import { useEvents, when, type EventStatus } from '@/lib/planner';

const STATUS: Record<EventStatus, { label: string; style: string }> = {
  DRAFT: { label: 'Draft', style: 'bg-rule text-ink' },
  ACTIVE: { label: 'Active', style: 'bg-good text-white' },
  ENDED: { label: 'Ended', style: 'bg-quiet text-white' },
};

export default function EventsPage() {
  const events = useEvents();

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-1 animate-rise flex-col gap-5 px-5 pb-6 pt-4">
      <PageHead title="Your events" line="A date, a room, a guest list. Then everyone eats.">
        <Link href="/planner/new" className="flex min-h-[52px] items-center justify-center rounded-full bg-tomato px-6 text-base font-bold text-white">
          Create an event
        </Link>
      </PageHead>

      {events.isError ? <FailureCard title="We couldn't load your events" message={failureOf(events.error).message} /> : null}
      {events.isPending ? (
        <p role="status" className="text-soft">
          Fetching your events…
        </p>
      ) : null}
      {events.data && events.data.length === 0 ? <Empty>No events yet. Create your first one: it needs a date, a venue, the number of guests you expect and the plates you will serve.</Empty> : null}

      <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {(events.data ?? []).map((event) => (
          <li key={event.id}>
            <Link href={`/planner/${event.id}`} className="block rounded-[20px] focus-visible:outline-offset-4">
              <Card className="flex h-full flex-col gap-1.5 transition-transform hover:-translate-y-0.5">
                <div className="flex items-start justify-between gap-3">
                  <h2 className="font-heading text-[24px] font-normal leading-tight text-bottle">{event.name}</h2>
                  <span className={`flex-none rounded-full px-3 py-1 text-sm font-bold ${STATUS[event.status].style}`}>{STATUS[event.status].label}</span>
                </div>
                <div className="text-[15px] text-quiet">{when(event.startsAt)}</div>
                {event.venue ? <div className="text-[15px] text-quiet">{event.venue}</div> : null}
                <div className="mt-1 text-[15px] font-bold text-ink">
                  {event.status === 'DRAFT' ? 'Not paid for yet' : `${event.guestsOnList} of ${event.paidGuests} guests on the list`}
                </div>
              </Card>
            </Link>
          </li>
        ))}
      </ul>
    </main>
  );
}
