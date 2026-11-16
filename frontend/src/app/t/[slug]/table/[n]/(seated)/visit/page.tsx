'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import BackLink from '@/components/guest/BackLink';
import { Loader } from '@/components/plately/Logo';
import { Card, Dashes } from '@/components/plately/ui';
import { useSession, useSessionOrders } from '@/lib/guest';
import { clock } from '@/lib/time';
import { naira } from '@/lib/money';
import { orderStatusLabel } from '@/lib/labels';

function csvCell(value: string): string {
  const safe = /^[=+\-@\t\r]/.test(value) ? `'${value}` : value;
  return /[",\n\r]/.test(safe) ? `"${safe.replace(/"/g, '""')}"` : safe;
}

// Your visit: everything about tonight, in one place.
export default function VisitPage() {
  const { slug, n } = useParams<{ slug: string; n: string }>();
  const session = useSession().data!;
  const orders = useSessionOrders();

  if (orders.isPending) return <Loader message="Gathering your visit…" />;
  const data = orders.data;
  const list = data?.orders ?? [];
  const mine = session.seats.find((seat) => seat.mine);

  const summary = [
    ['Where', `${session.businessName ?? ''} · Table ${session.displayNumber}`],
    ['Your seat', mine ? `Seat ${mine.position}` : ''],
    ['Orders', String(list.length)],
    ['Paid from this phone', naira(data?.paidByYou)],
    ['Paid by the whole table', naira(data?.paidByTable)],
  ];
  const csv = [
    ['Order', 'Items', 'For', 'Amount', 'Status'].map(csvCell).join(','),
    ...list.map((order) =>
      [order.orderNumber, order.items.join('; '), order.forMe ? 'You' : (order.forName ?? ''), String(order.amount ?? ''), orderStatusLabel(order.status)].map(csvCell).join(','),
    ),
  ].join('\n');

  return (
    <main className="mx-auto flex w-full max-w-md md:max-w-xl flex-1 animate-rise flex-col gap-4 px-5 pb-8 pt-6">
      <BackLink href={`/t/${slug}/table/${n}/home`} />
      <Card>
        <h1 className="font-heading text-[30px] font-normal leading-tight text-bottle">Your visit</h1>
        <p className="mb-3 text-[15px] text-quiet">Everything about tonight, in one place.</p>
        <Dashes />
        <dl className="my-3 flex flex-col gap-1.5">
          {summary.slice(0, 3).map(([k, v]) => (
            <div key={k} className="flex justify-between gap-3 text-[15px]">
              <dt className="text-quiet">{k}</dt>
              <dd className="text-right font-bold">{v}</dd>
            </div>
          ))}
        </dl>
        <Dashes />
        <h2 className="mb-2 mt-3 font-heading text-xl font-normal text-bottle">At the table with you</h2>
        <ul className="flex flex-col gap-2">
          {session.seats.map((seat) => (
            <li key={seat.seatId} className="flex items-center gap-3">
              <span className={`flex h-8 w-8 flex-none items-center justify-center rounded-full text-sm font-bold ${seat.status === 'HELD' ? 'border-2 border-mustard bg-mustard/20 text-bottle' : 'bg-bottle text-cream'}`}>{seat.position}</span>
              <span className="text-[15px] font-bold">{seat.status === 'HELD' ? 'Saved for a friend' : seat.mine ? `${seat.guestName} (you)` : seat.guestName}</span>
            </li>
          ))}
        </ul>
        <Dashes className="mt-3" />
        <h2 className="mb-2 mt-3 font-heading text-xl font-normal text-bottle">Orders</h2>
        {list.length === 0 ? (
          <p className="text-[15px] text-quiet">Nothing ordered yet. The menu is one tap away when you&apos;re hungry.</p>
        ) : (
          <ul className="flex flex-col divide-y divide-rule">
            {list.map((order) => (
              <li key={order.orderId} className="flex items-center justify-between gap-3 py-2.5 first:pt-0">
                <div>
                  <div className="text-sm text-quiet">
                    {order.orderNumber}
                    {order.createdAt ? ` · ${clock(order.createdAt)}` : ''}
                  </div>
                  <div className="text-base font-bold">{order.items.join(', ')}</div>
                  <div className="text-sm text-quiet">
                    {order.forMe ? 'For you' : `For ${order.forName ?? `seat ${order.forSeatPosition}`}`} · {naira(order.amount)}
                  </div>
                  <div className="text-sm font-bold text-good">{orderStatusLabel(order.status)}</div>
                </div>
                {order.paymentReference ? (
                  <Link href={`/t/${slug}/table/${n}/receipt/${order.paymentReference}`} className="flex min-h-[44px] items-center rounded-full border-[1.5px] border-bottle px-4 text-[15px] font-bold text-bottle">
                    Receipt
                  </Link>
                ) : null}
              </li>
            ))}
          </ul>
        )}
        <Dashes className="mt-3" />
        <dl className="mt-3 flex flex-col gap-1.5">
          {summary.slice(3).map(([k, v]) => (
            <div key={k} className="flex justify-between gap-3 text-[15px]">
              <dt className="text-quiet">{k}</dt>
              <dd className="text-right font-bold">{v}</dd>
            </div>
          ))}
        </dl>
      </Card>
      <a
        href={`data:text/csv;charset=utf-8,${encodeURIComponent(csv)}`}
        download="plately-visit.csv"
        className="flex min-h-[48px] items-center justify-center gap-2 rounded-full border-[1.5px] border-cream/45 px-5 text-base font-bold text-cream"
      >
        Download visit summary
      </a>
    </main>
  );
}
