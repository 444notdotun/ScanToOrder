'use client';

import Link from 'next/link';
import { Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import Ground from '@/components/plately/Ground';
import { Brand, Loader, Mark, Watermark } from '@/components/plately/Logo';
import { Button, FailureCard } from '@/components/plately/ui';
import { rememberedTable } from '@/lib/guest';
import { dataOf, failureOf, http, sessionEnded } from '@/lib/http';
import { useLiveStream } from '@/hooks/useLiveStream';
import { ConnectionBanner } from '@/components/plately/Connection';

interface Checked {
  reference: string;
  paymentStatus: 'PENDING' | 'SUCCESSFUL' | 'FAILED';
  orderStatus?: string;
  amount?: number;
}

// Where the bank sends the guest back to. Plately asks the bank what happened; it never takes the browser's word.
function Returned() {
  const params = useSearchParams();
  const reference = params.get('reference') || params.get('trxref');
  const table = rememberedTable();
  const stream = useLiveStream('guest', Boolean(reference));
  const home = table ? `/t/${table.slug}/table/${table.n}/home` : '/';

  const check = useQuery({
    queryKey: ['payment', reference],
    enabled: Boolean(reference),
    retry: false,
    queryFn: async () => dataOf<Checked>(await http.get(`/api/v1/payments/verify/${reference}`)),
  });

  if (!reference) {
    return (
      <Ground>
        <main className="mx-auto flex w-full max-w-md md:max-w-xl flex-1 flex-col justify-center gap-4 px-6">
          <Brand />
          <h1 className="font-heading text-[32px] font-normal leading-tight">We can&apos;t tell which payment this was.</h1>
          <FailureCard message="Go back to your table. If you paid, your order is already with the kitchen." />
          <Link href={home} className="flex min-h-[48px] items-center justify-center rounded-full bg-cream font-bold text-bottle">
            Back to your table
          </Link>
        </main>
      </Ground>
    );
  }

  if (check.isPending) {
    return (
      <Ground>
        <Loader message="Checking with your bank…" />
      </Ground>
    );
  }

  if (check.isError) {
    const failure = failureOf(check.error, "We couldn't check your payment");
    const gone = sessionEnded(failure);
    return (
      <Ground>
        <main className="mx-auto flex w-full max-w-md md:max-w-xl flex-1 flex-col justify-center gap-4 px-6">
          <Brand />
          <h1 className="font-heading text-[32px] font-normal leading-tight">{gone ? 'Your seat has been freed.' : "We couldn't check that payment."}</h1>
          <FailureCard title={failure.title} message={failure.message} />
          <Button tone="light" onClick={() => void check.refetch()}>
            Check again
          </Button>
          <Link href={gone ? '/' : home} className="text-center text-sm font-bold text-cream underline underline-offset-4">
            {gone ? 'Scan the table again' : 'Back to your table'}
          </Link>
        </main>
      </Ground>
    );
  }

  const paid = check.data.paymentStatus === 'SUCCESSFUL';
  const failed = check.data.paymentStatus === 'FAILED';
  const receipt = table ? `/t/${table.slug}/table/${table.n}/receipt/${reference}` : home;

  return (
    <Ground>
      <ConnectionBanner lost={stream.lost} caughtUp={stream.caughtUp} />
      {paid ? <Watermark className="-bottom-[70px] -right-24" /> : null}
      <main className="relative mx-auto flex w-full max-w-md md:max-w-xl flex-1 animate-rise flex-col items-center justify-center gap-4 px-7 text-center">
        {paid ? (
          <>
            <div className="animate-pop">
              <Mark size={96} ring="#F0B94A" />
            </div>
            <h1 className="font-heading text-[36px] font-normal leading-[1.1]">Paid. It&apos;s with the kitchen.</h1>
            <p className="text-[17px] text-soft">Sit back. A real person brings it over.</p>
            <div className="mt-2 flex w-full flex-col gap-2.5">
              <Link href={receipt} className="flex min-h-[52px] items-center justify-center rounded-full bg-tomato text-base font-bold text-white">
                See your receipt
              </Link>
              <Link href={home} className="flex min-h-[48px] items-center justify-center rounded-full border-[1.5px] border-cream/45 text-base font-bold text-cream">
                Back to your table
              </Link>
            </div>
          </>
        ) : failed ? (
          <>
            <h1 className="font-heading text-[34px] font-normal leading-[1.1]">That payment didn&apos;t go through.</h1>
            <FailureCard title="Nothing was charged" message="Go back to your order and try again, or use another card." />
            <Link href={home} className="flex min-h-[52px] w-full items-center justify-center rounded-full bg-cream font-bold text-bottle">
              Back to your table
            </Link>
          </>
        ) : (
          <>
            <h1 className="font-heading text-[34px] font-normal leading-[1.1]">Your bank hasn&apos;t said yet.</h1>
            <p className="text-[17px] text-soft">This page updates by itself the moment it does. You can also ask again.</p>
            <Button tone="light" onClick={() => void check.refetch()}>
              Check again
            </Button>
            <Link href={home} className="text-sm font-bold text-cream underline underline-offset-4">
              Back to your table
            </Link>
          </>
        )}
      </main>
    </Ground>
  );
}

export default function OrderSuccessPage() {
  return (
    <Suspense fallback={null}>
      <Returned />
    </Suspense>
  );
}
