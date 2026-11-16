'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useQueryClient } from '@tanstack/react-query';
import { useConnection } from '@/components/plately/Connection';
import OrderTracker from '@/components/guest/OrderTracker';
import { Brand, Loader } from '@/components/plately/Logo';
import { Button, Card, Eyebrow, FailureCard, Sheet } from '@/components/plately/ui';
import { forgetTable, useMyEvent } from '@/lib/guest';
import { dataOf, failureOf, http, type ApiFailure } from '@/lib/http';
import { firstName } from '@/lib/money';

// You're in. Tap a plate and it goes straight to the kitchen: nothing to pay.
export default function PlatesPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { lost } = useConnection();
  const mine = useMyEvent();
  const [failure, setFailure] = useState<ApiFailure | null>(null);
  const [sent, setSent] = useState<string | null>(null);
  const [busyPlate, setBusyPlate] = useState<string | null>(null);
  const [leaving, setLeaving] = useState(false);

  if (mine.isPending) return <Loader message="Setting your table…" />;
  if (mine.isError) {
    const failed = failureOf(mine.error, "We couldn't open your plates");
    return (
      <main className="mx-auto flex w-full max-w-md md:max-w-xl flex-1 flex-col gap-4 px-5 pt-8">
        <Brand />
        <FailureCard title={failed.title} message={failed.message} />
        <Button tone="light" onClick={() => void mine.refetch()}>
          Try again
        </Button>
      </main>
    );
  }

  const event = mine.data;
  const limited = event.plateLimit !== null && event.plateLimit !== undefined;
  const left = limited ? Math.max(0, (event.plateLimit as number) - event.platesUsed) : null;

  const order = async (plateId: string) => {
    setBusyPlate(plateId);
    setFailure(null);
    setSent(null);
    try {
      const result = dataOf<{ plate: string; message: string }>(await http.post('/api/v1/me/event/orders', { plateId }));
      setSent(result.message);
      await queryClient.invalidateQueries();
    } catch (error) {
      setFailure(failureOf(error, "That plate didn't go through"));
      // The menu or the plates left may have changed under the guest: look again.
      void queryClient.invalidateQueries();
    } finally {
      setBusyPlate(null);
    }
  };

  const leave = async () => {
    try {
      await http.post('/api/v1/sessions/me/leave');
      forgetTable();
      queryClient.clear();
      router.replace('/');
    } catch (error) {
      setLeaving(false);
      setFailure(failureOf(error, "We couldn't free your seat"));
    }
  };

  return (
    <>
      <main className="mx-auto flex w-full max-w-md flex-1 animate-rise flex-col gap-4 px-5 pb-10 pt-6 md:max-w-3xl xl:max-w-5xl">
        <header className="flex flex-col gap-1.5">
          <Brand size={22} />
          <Eyebrow className="mt-1">
            {event.eventName}
            {event.venue ? ` · ${event.venue}` : ''}
          </Eyebrow>
          <h1 className="font-heading text-[36px] font-normal leading-[1.08]">You&apos;re in, {firstName(event.guestName)}.</h1>
          <p className="text-[17px] text-soft">
            {left === null
              ? `You can have as many plates as you like. You've had ${event.platesUsed}.`
              : left === 0
                ? `You've had all ${event.plateLimit} of your plates. The host can add more for you.`
                : `${left} of ${event.plateLimit} plates left.`}
          </p>
        </header>

        {event.platePending ? <OrderTracker /> : null}
        {event.platePending ? <p className="text-[15px] font-bold text-mustard">Order again once it arrives.</p> : null}
        {sent && event.platePending ? (
          <div role="status" className="animate-pop rounded-2xl bg-good p-3.5 text-[15px] font-bold text-white">
            {sent}
          </div>
        ) : null}
        {failure ? <FailureCard title={failure.title} message={failure.message} /> : null}

        <section className="grid grid-cols-1 gap-3 md:grid-cols-2">
          {event.plates.length === 0 ? (
            <Card>
              <p className="text-[15px] text-quiet">The kitchen hasn&apos;t put any plates up yet. Ask a host.</p>
            </Card>
          ) : (
            event.plates.map((plate) => (
              <Card key={plate.id} className="flex items-center justify-between gap-3 !p-4">
                <div className="min-w-0">
                  <div className="text-base font-bold leading-tight text-ink">{plate.name}</div>
                  {plate.description ? <div className="mt-0.5 text-sm leading-snug text-quiet">{plate.description}</div> : null}
                </div>
                <Button
                  tone="primary"
                  full={false}
                  className="flex-none"
                  disabled={lost || busyPlate !== null || Boolean(event.platePending) || left === 0}
                  onClick={() => void order(plate.id)}
                  aria-label={`Order ${plate.name}`}
                >
                  {busyPlate === plate.id ? 'Sending…' : 'This one'}
                </Button>
              </Card>
            ))
          )}
        </section>

        <p className="text-center text-sm text-soft">{event.note}</p>
        <Button tone="ghost" onClick={() => setLeaving(true)}>
          I&apos;m heading off
        </Button>
      </main>

      <Sheet open={leaving} onClose={() => setLeaving(false)} label="Leave the table">
        <h2 className="font-heading text-[28px] font-normal text-bottle">Heading off?</h2>
        <p className="mb-5 mt-2 text-[15px] leading-snug text-quiet">
          Your seat goes back to the room. Your code stays yours: an usher can seat it again on another phone if you come back.
        </p>
        <div className="flex flex-col gap-2.5">
          <Button tone="primary" disabled={lost} onClick={() => void leave()}>
            Yes, free my seat
          </Button>
          <Button tone="outline" onClick={() => setLeaving(false)}>
            Stay a little longer
          </Button>
        </div>
      </Sheet>
    </>
  );
}
