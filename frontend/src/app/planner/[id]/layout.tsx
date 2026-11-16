'use client';

import Link from 'next/link';
import { useParams, usePathname } from 'next/navigation';
import type { ReactNode } from 'react';
import { FailureCard } from '@/components/plately/ui';
import PageHead from '@/components/staff/PageHead';
import { failureOf } from '@/lib/http';
import { useEvent } from '@/lib/planner';

const TABS = [
  { path: '', label: 'The event' },
  { path: '/guests', label: 'Guest list' },
  { path: '/plates', label: 'Plates' },
  { path: '/tables', label: 'Tables' },
  { path: '/coverage', label: 'Has everyone eaten?' },
];

// The frame around one event: its name, how far along it is, and its sections.
export default function EventLayout({ children }: { children: ReactNode }) {
  const { id } = useParams<{ id: string }>();
  const path = usePathname();
  const event = useEvent(id);
  const base = `/planner/${id}`;
  const data = event.data;

  const line = !data
    ? 'One moment…'
    : data.status === 'DRAFT'
      ? 'A draft until it is paid for.'
      : data.status === 'ENDED'
        ? 'This event is over. Codes no longer work.'
        : `Active. ${data.guestsOnList} of ${data.paidGuests} guests on the list.`;

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-1 animate-rise flex-col gap-4 px-5 pb-6 pt-4">
      <Link href="/planner" className="w-fit text-sm font-bold text-soft underline underline-offset-4 print:hidden">
        All your events
      </Link>
      <PageHead title={data?.name ?? 'Your event'} line={line} />
      <nav aria-label="Event sections" className="overflow-x-auto print:hidden">
        <ul className="flex w-max gap-2 pb-1">
          {TABS.map((tab) => {
            const here = tab.path === '' ? path === base : path.startsWith(base + tab.path);
            return (
              <li key={tab.path}>
                <Link
                  href={base + tab.path}
                  aria-current={here ? 'page' : undefined}
                  className={`flex min-h-[48px] items-center rounded-full border-[1.5px] px-5 text-base font-bold ${
                    here ? 'border-cream bg-cream text-bottle' : 'border-cream/45 text-cream hover:bg-cream/10'
                  }`}
                >
                  {tab.label}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>
      {event.isError ? <FailureCard title="We couldn't find that event" message={failureOf(event.error).message} /> : null}
      {children}
    </main>
  );
}
