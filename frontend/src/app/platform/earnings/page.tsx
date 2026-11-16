'use client';

import { useState } from 'react';
import { Card, FailureCard } from '@/components/plately/ui';
import PageHead, { Empty, Tile } from '@/components/staff/PageHead';
import { failureOf } from '@/lib/http';
import type { Period } from '@/lib/manager';
import { kobo } from '@/lib/money';
import { useEarnings } from '@/lib/platform';

const PERIODS: Array<{ id: Period; label: string }> = [
  { id: 'today', label: 'Today' },
  { id: 'week', label: 'This week' },
  { id: 'month', label: 'This month' },
];

export default function EarningsPage() {
  const [period, setPeriod] = useState<Period>('month');
  const earnings = useEarnings(period);
  const data = earnings.data;
  const top = Math.max(1, ...(data?.byRestaurant ?? []).map((row) => row.chargeKobo));

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-1 animate-rise flex-col gap-5 px-5 pb-6 pt-4">
      <PageHead title="Earnings" line="What Plately has earned: the charge on each sale and the fee for each event." />

      <div role="group" aria-label="Period" className="flex gap-2">
        {PERIODS.map((item) => (
          <button
            key={item.id}
            type="button"
            aria-pressed={period === item.id}
            onClick={() => setPeriod(item.id)}
            className={`min-h-[48px] rounded-full border-[1.5px] px-5 text-base font-bold ${period === item.id ? 'border-cream bg-cream text-bottle' : 'border-cream/45 text-cream hover:bg-cream/10'}`}
          >
            {item.label}
          </button>
        ))}
      </div>

      {earnings.isError ? <FailureCard title="We couldn't load the earnings" message={failureOf(earnings.error).message} /> : null}
      {earnings.isPending ? (
        <p role="status" className="text-soft">
          Adding it up…
        </p>
      ) : null}

      {data ? (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Tile label="Your earnings" value={kobo(data.totalKobo)} sub="charges plus event fees" />
            <Tile label="From restaurants" value={kobo(data.chargesKobo)} sub="the charge on each sale" />
            <Tile label="From events" value={kobo(data.eventFeesKobo)} sub={`${data.eventFees.length} event${data.eventFees.length === 1 ? '' : 's'} paid for`} />
            <Tile label="Live businesses" value={String(data.liveBusinesses)} sub={`${data.totalBusinesses} in total`} />
          </div>

          <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
            <Card>
              <h2 className="mb-3 font-heading text-[22px] font-normal text-bottle">Charges by restaurant</h2>
              {data.byRestaurant.length === 0 ? <p className="text-[15px] text-quiet">No paid orders in this period.</p> : null}
              <ul className="flex flex-col gap-3">
                {data.byRestaurant.map((row) => (
                  <li key={row.name}>
                    <div className="flex items-baseline justify-between gap-3 text-[15px]">
                      <span className="font-bold text-ink">{row.name}</span>
                      <span className="font-bold text-bottle">{kobo(row.chargeKobo)}</span>
                    </div>
                    <div aria-hidden="true" className="mt-1 h-2.5 overflow-hidden rounded-full bg-rule">
                      <div className="h-full rounded-full bg-bottle" style={{ width: `${Math.round((row.chargeKobo / top) * 100)}%` }} />
                    </div>
                  </li>
                ))}
              </ul>
            </Card>

            <Card>
              <h2 className="mb-3 font-heading text-[22px] font-normal text-bottle">Event fees</h2>
              {data.eventFees.length === 0 ? <p className="text-[15px] text-quiet">No events paid for in this period.</p> : null}
              <dl className="flex flex-col">
                {data.eventFees.map((fee, index) => (
                  <div key={`${fee.eventName}-${index}`} className="flex items-baseline justify-between gap-3 border-t border-dashed border-rule2 py-2.5 first:border-t-0 first:pt-0">
                    <dt className="text-[15px] leading-snug text-quiet">
                      {fee.eventName} · {fee.guests} guests
                    </dt>
                    <dd className="font-bold text-ink">{kobo(fee.amountKobo)}</dd>
                  </div>
                ))}
              </dl>
            </Card>

            <Card>
              <h2 className="mb-3 font-heading text-[22px] font-normal text-bottle">Worth a look</h2>
              <dl className="flex flex-col">
                {[
                  ['Failed payments', String(data.failedPayments)],
                  ['Refunds started', `${data.refundsCount}, ${kobo(data.refundsKobo)}`],
                ].map(([name, value]) => (
                  <div key={name} className="flex items-baseline justify-between gap-3 border-t border-dashed border-rule2 py-2.5 first:border-t-0 first:pt-0">
                    <dt className="text-[15px] text-quiet">{name}</dt>
                    <dd className="font-bold text-ink">{value}</dd>
                  </div>
                ))}
              </dl>
            </Card>
          </div>
          {data.totalBusinesses === 0 ? <Empty>No businesses yet.</Empty> : null}
        </>
      ) : null}
    </main>
  );
}
