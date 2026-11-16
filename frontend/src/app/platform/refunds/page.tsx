'use client';

import { Card, FailureCard } from '@/components/plately/ui';
import PageHead, { Empty } from '@/components/staff/PageHead';
import { failureOf } from '@/lib/http';
import { naira } from '@/lib/money';
import { useProblemRefunds } from '@/lib/platform';

const REASONS: Record<string, string> = {
  KITCHEN_UNAVAILABLE: 'The kitchen could not make it',
  LATE_PAYMENT: 'Paid after the guest had left',
  DOUBLE_PAYMENT: 'Paid twice',
};

function since(iso?: string): string {
  if (!iso) return '';
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleString('en-NG', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' });
}

// Refunds a restaurant has left too long, or that a guest says never arrived. No account numbers or emails appear here.
export default function ProblemRefundsPage() {
  const refunds = useProblemRefunds();

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-1 animate-rise flex-col gap-5 px-5 pb-6 pt-4">
      <PageHead title="Refunds to chase" line="Refunds a restaurant has left more than 48 hours, or a guest says never arrived." />
      {refunds.isError ? <FailureCard title="We couldn't load the refunds" message={failureOf(refunds.error).message} /> : null}
      {refunds.data && refunds.data.length === 0 ? <Empty>Nothing to chase. Every refund is settled or still inside its 48 hours.</Empty> : null}
      <ul className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        {(refunds.data ?? []).map((refund) => (
          <li key={refund.refundId}>
            <Card className="flex flex-col gap-1.5 !p-4">
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <h2 className="font-heading text-[22px] font-normal text-bottle">{refund.tenantName}</h2>
                <span className="font-heading text-xl text-bottle">{naira(refund.amount)}</span>
              </div>
              <div className="text-[15px] text-quiet">
                {refund.orderNumber} · {REASONS[refund.reason ?? ''] ?? refund.reason}
              </div>
              <div className="text-sm text-quiet">Waiting since {since(refund.dueSince)}</div>
              {refund.overdue ? <div className="text-[15px] font-bold text-tomato">More than 48 hours.</div> : null}
              {refund.reportedMissing ? <div className="text-[15px] font-bold text-tomato">The guest says it has not reached them.</div> : null}
            </Card>
          </li>
        ))}
      </ul>
    </main>
  );
}
