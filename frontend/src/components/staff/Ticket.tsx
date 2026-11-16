'use client';

import type { ReactNode } from 'react';
import { Dashes } from '@/components/plately/ui';
import { minutesText, type ServiceOrder } from '@/lib/staff';

// Minutes since the list was fetched count on top of what the server said, so "waiting 6 min" keeps moving
// without asking the server again.
export function agedMinutes(base: number, fetchedAt: number, now: number): number {
  return base + Math.max(0, Math.floor((now - fetchedAt) / 60_000));
}

type Mood = 'plain' | 'cold' | 'problem';

const BORDER: Record<Mood, string> = {
  plain: 'border-[1.5px] border-rule',
  cold: 'border-[2.5px] border-mustard motion-safe:animate-pulse-ring',
  problem: 'border-[2.5px] border-tomato',
};

// A kitchen ticket: small radius, dashed dividers, the plate in large type.
export default function Ticket({
  order,
  minutes,
  waitLabel,
  mood = 'plain',
  flag,
  children,
}: {
  order: ServiceOrder;
  minutes: number;
  waitLabel: string;
  mood?: Mood;
  flag?: string;
  children?: ReactNode;
}) {
  const where = [
    order.tableNumber != null ? `Table ${order.tableNumber}` : null,
    order.seatPosition != null ? `Seat ${order.seatPosition}` : null,
    order.guestName ?? null,
  ]
    .filter(Boolean)
    .join(' · ');

  return (
    <article aria-label={`${order.orderNumber}, ${where}`} className={`flex flex-col gap-2 rounded-lg bg-paperlight p-4 text-ink shadow-[0_8px_20px_rgba(0,0,0,0.22)] animate-fade ${BORDER[mood]}`}>
      <div className="flex items-baseline justify-between gap-3">
        <span className="min-w-0 break-all font-heading text-lg leading-tight text-bottle">{order.orderNumber}</span>
        <span className="flex-none whitespace-nowrap text-sm font-bold text-quiet">
          {waitLabel} {minutesText(minutes)}
        </span>
      </div>
      <ul className="flex flex-col gap-0.5 text-[19px] font-bold leading-snug">
        {order.items.map((line, index) => (
          <li key={`${line}-${index}`}>{line}</li>
        ))}
      </ul>
      <div className="text-[15px] text-quiet">{where}</div>
      {(order.instructions ?? []).map((asked) => (
        <div key={asked} className="font-hand text-[23px] font-bold leading-[1.1] text-hand">
          {asked}
        </div>
      ))}
      {order.note ? <div className="font-hand text-[23px] font-bold leading-[1.1] text-hand">{order.note}</div> : null}
      {flag ? <div className={`text-[15px] font-bold leading-snug ${mood === 'problem' ? 'text-tomato' : 'text-[#8A5A00]'}`}>{flag}</div> : null}
      {children ? (
        <>
          <Dashes className="my-1" />
          <div className="flex flex-col gap-2">{children}</div>
        </>
      ) : null}
    </article>
  );
}
