'use client';

import Link from 'next/link';
import { useParams, usePathname } from 'next/navigation';
import type { ReactNode } from 'react';
import { FailureCard } from '@/components/plately/ui';
import { failureOf } from '@/lib/http';
import { spaced, useEvent, when, type EventStatus } from '@/lib/planner';

const TABS = [
  { path: '', label: 'Guests' },
  { path: '/plates', label: 'Plates' },
  { path: '/tables', label: 'Tables' },
  { path: '/coverage', label: 'Coverage' },
  { path: '/details', label: 'Details' },
];

const STATUS: Record<EventStatus, { label: string; style: string }> = {
  DRAFT: { label: 'Draft: not paid for yet', style: 'bg-rule text-ink' },
  ACTIVE: { label: 'Active', style: 'bg-good text-white' },
  ENDED: { label: 'Ended', style: 'bg-quiet text-white' },
};

// The frame around one event: how far along it is, when and where, its code, and one section at a time.
export default function EventLayout({ children }: { children: ReactNode }) {
  const { id } = useParams<{ id: string }>();
  const path = usePathname();
  const event = useEvent(id);
  const base = `/planner/${id}`;
  const data = event.data;

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-1 animate-rise flex-col gap-4 px-5 pb-6 pt-4">
      <section aria-label="The event" className="flex flex-wrap items-end justify-between gap-x-6 gap-y-3 print:hidden">
        <div className="min-w-0">
          {data ? <span className={`inline-block rounded-full px-3 py-1 text-sm font-bold ${STATUS[data.status].style}`}>{STATUS[data.status].label}</span> : null}
          <h1 className="mt-2 font-heading text-[38px] font-normal leading-[1.1]">{data?.name ?? 'Your event'}</h1>
          {data ? (
            <p className="mt-1 text-[17px] leading-snug text-soft">
              {when(data.startsAt)}
              {data.venue ? ` · ${data.venue}` : ''}
            </p>
          ) : null}
        </div>
        {data && data.status !== 'ENDED' ? (
          <div className="rounded-2xl bg-cream/10 px-4 py-2 sm:text-right">
            <div className="text-[12px] font-bold uppercase tracking-[0.12em] text-soft">Event code</div>
            <div className="font-heading text-[30px] leading-none tracking-[0.08em] text-cream">{spaced(data.eventCode)}</div>
          </div>
        ) : null}
      </section>

      <nav aria-label="Event sections" className="overflow-x-auto print:hidden">
        <ul className="flex w-max gap-2 pb-1">
          {TABS.map((tab) => {
            const here = tab.path === '' ? path === base : path.startsWith(base + tab.path);
            return (
              <li key={tab.path}>
                <Link
                  href={base + tab.path}
                  aria-current={here ? 'page' : undefined}
                  className={`flex min-h-[48px] items-center rounded-full border-[1.5px] px-6 text-base font-bold ${
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
