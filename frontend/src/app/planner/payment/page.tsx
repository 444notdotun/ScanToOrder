'use client';

import Link from 'next/link';
import { Suspense, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'next/navigation';
import { Card, FailureCard } from '@/components/plately/ui';
import { Loader } from '@/components/plately/Logo';
import { failureOf } from '@/lib/http';
import { plannerApi } from '@/lib/planner';

// Where Paystack sends a planner back to: ask whether the payment went through, then point at the event.
function Returned() {
  const params = useSearchParams();
  const reference = params.get('reference') || params.get('trxref');
  // Which event this was is kept on this device while the planner is away paying.
  const [eventId] = useState(() => {
    try {
      return window.localStorage.getItem('plately-event-pay');
    } catch {
      return null;
    }
  });
  const check = useQuery({
    queryKey: ['event-payment', reference],
    enabled: Boolean(reference),
    retry: false,
    queryFn: () => plannerApi.verify(reference as string),
  });
  const back = eventId ? `/planner/${eventId}/guests` : '/planner';

  if (!reference) {
    return (
      <Card className="flex flex-col gap-3">
        <h1 className="font-heading text-[30px] font-normal text-bottle">Nothing to check here</h1>
        <p className="text-[17px] leading-snug text-quiet">This page opens after you pay for an event.</p>
        <Link href="/planner" className="font-bold underline underline-offset-4">
          Back to your events
        </Link>
      </Card>
    );
  }
  if (check.isPending) return <Loader message="Checking your payment…" />;
  if (check.isError) {
    return (
      <div className="flex flex-col gap-3">
        <FailureCard title="We couldn't check your payment" message={failureOf(check.error).message} />
        <Link href="/planner" className="font-bold underline underline-offset-4">
          Back to your events
        </Link>
      </div>
    );
  }
  const paid = check.data.paymentStatus === 'SUCCESSFUL';
  return (
    <Card className="flex animate-pop flex-col gap-3">
      <h1 className="font-heading text-[30px] font-normal text-bottle">{paid ? 'Paid. The event is on.' : 'That payment has not gone through'}</h1>
      <p className="text-[17px] leading-snug text-quiet">
        {paid ? 'Guest codes can be made now. Add your guests and share their codes.' : 'Nothing was taken, or it is still being confirmed. You can try again from the event.'}
      </p>
      <Link href={paid ? back : eventId ? `/planner/${eventId}` : '/planner'} className="flex min-h-[52px] items-center justify-center rounded-full bg-tomato px-5 text-base font-bold text-white">
        {paid ? 'Add your guests' : 'Back to the event'}
      </Link>
    </Card>
  );
}

export default function PlannerPaymentPage() {
  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 animate-rise flex-col justify-center gap-5 px-5 pb-6 pt-4">
      <Suspense fallback={<Loader message="One moment…" />}>
        <Returned />
      </Suspense>
    </main>
  );
}
