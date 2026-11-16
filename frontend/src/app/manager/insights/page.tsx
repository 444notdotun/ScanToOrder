'use client';

import { useState } from 'react';
import { Card, FailureCard } from '@/components/plately/ui';
import PageHead, { Tile } from '@/components/staff/PageHead';
import { failureOf } from '@/lib/http';
import { useInsights, type Insights, type Period } from '@/lib/manager';
import { kobo } from '@/lib/money';

const PERIODS: Array<{ id: Period; label: string }> = [
  { id: 'today', label: 'Today' },
  { id: 'week', label: 'This week' },
  { id: 'month', label: 'This month' },
];

// 14 reads as "2 pm", 0 as "12 am".
function hourLabel(hour: number): string {
  return `${hour % 12 || 12} ${hour < 12 ? 'am' : 'pm'}`;
}

function minutes(value?: number | null): string | null {
  return value == null ? null : `${Math.round(value)} min`;
}

function Bars({ title, rows, empty }: { title: string; rows: Array<{ key: string; value: number }>; empty: string }) {
  const top = Math.max(1, ...rows.map((row) => row.value));
  return (
    <Card>
      <h2 className="mb-3 font-heading text-[22px] font-normal text-bottle">{title}</h2>
      {rows.length === 0 ? <p className="text-[15px] text-quiet">{empty}</p> : null}
      <ul className="flex flex-col gap-3">
        {rows.map((row) => (
          <li key={row.key}>
            <div className="flex items-baseline justify-between gap-3 text-[15px]">
              <span className="font-bold text-ink">{row.key}</span>
              <span className="font-bold text-bottle">{row.value}</span>
            </div>
            <div aria-hidden="true" className="mt-1 h-2.5 overflow-hidden rounded-full bg-rule">
              <div className="h-full rounded-full bg-bottle" style={{ width: `${Math.round((row.value / top) * 100)}%` }} />
            </div>
          </li>
        ))}
      </ul>
    </Card>
  );
}

function Figures({ data }: { data: Insights }) {
  const answered = minutes(data.callToTakenMinutes);
  const facts: Array<[string, string]> = [
    ['Average order', kobo(data.averageOrderKobo)],
    ['Paid to delivered', minutes(data.paidToDeliveredMinutes) ? `${minutes(data.paidToDeliveredMinutes)} on average` : 'Nothing delivered yet'],
    ['Guests seated', String(data.guestsSeated)],
    ['Seats saved for friends', `${data.seatsHeld}, ${data.seatsHeldTakenUp} taken up`],
    ['Waiter calls', answered ? `${data.waiterCalls}, answered in ${answered}` : String(data.waiterCalls)],
  ];
  const hours = data.busiestHours.filter((row) => row.orders > 0).map((row) => ({ key: hourLabel(row.hour), value: row.orders }));

  return (
    <>
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Tile label="Sales" value={kobo(data.salesKobo)} sub={`${data.orders} order${data.orders === 1 ? '' : 's'}`} />
        <Tile label="Plately's charge" value={kobo(data.platelyChargeKobo)} />
        <Tile label="You received" value={kobo(data.receivedKobo)} sub="sent to your bank" />
        <Tile
          label="Refunds"
          value={kobo(data.refundsKobo)}
          sub={`${data.ordersKitchenCouldNotMake} order${data.ordersKitchenCouldNotMake === 1 ? '' : 's'} the kitchen could not make`}
        />
      </div>
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <Bars title="Best plates" rows={data.bestPlates.map((plate) => ({ key: plate.name, value: plate.quantity }))} empty="No plates yet." />
        <Bars title="Busiest hours" rows={hours} empty="No orders yet." />
        <Card>
          <h2 className="mb-3 font-heading text-[22px] font-normal text-bottle">Orders, tables and service</h2>
          <dl className="flex flex-col">
            {facts.map(([name, value]) => (
              <div key={name} className="flex items-baseline justify-between gap-3 border-t border-dashed border-rule2 py-2.5 first:border-t-0 first:pt-0">
                <dt className="text-[15px] text-quiet">{name}</dt>
                <dd className="text-right font-bold text-ink">{value}</dd>
              </div>
            ))}
          </dl>
        </Card>
      </div>
    </>
  );
}

export default function InsightsPage() {
  const [period, setPeriod] = useState<Period>('today');
  const insights = useInsights(period);

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-1 animate-rise flex-col gap-5 px-5 pb-6 pt-4">
      <PageHead title="Insights" line="How the place has been doing." />

      <div role="group" aria-label="Period" className="flex gap-2">
        {PERIODS.map((item) => (
          <button
            key={item.id}
            type="button"
            aria-pressed={period === item.id}
            onClick={() => setPeriod(item.id)}
            className={`min-h-[48px] rounded-full border-[1.5px] px-5 text-base font-bold ${
              period === item.id ? 'border-cream bg-cream text-bottle' : 'border-cream/45 text-cream hover:bg-cream/10'
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>

      {insights.isError ? <FailureCard title="We couldn't load the figures" message={failureOf(insights.error).message} /> : null}
      {insights.isPending ? (
        <p role="status" className="text-soft">
          Adding it up…
        </p>
      ) : null}
      {insights.data && !insights.data.hasData ? (
        <Card>
          <h2 className="font-heading text-[26px] font-normal text-bottle">Nothing to show yet</h2>
          <p className="mt-1 text-[17px] leading-snug text-quiet">
            Your sales, busiest hours and best plates appear here after your first paid order. Open for orders and put the cards on the tables.
          </p>
        </Card>
      ) : null}
      {insights.data?.hasData ? <Figures data={insights.data} /> : null}
    </main>
  );
}
