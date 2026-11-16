'use client';

import { useParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useConnection } from '@/components/plately/Connection';
import { Button, Card, FailureCard, Field, Sheet } from '@/components/plately/ui';
import EventDetails from '@/components/staff/EventDetails';
import { useAction } from '@/hooks/useAction';
import { kobo } from '@/lib/money';
import { plannerApi, spaced, useEvent, type PlannerEvent } from '@/lib/planner';

// What the event costs, and the one button that pays for it. A paid event can be topped up with more guests.
function Payment({ event, act }: { event: PlannerEvent; act: ReturnType<typeof useAction> }) {
  const { lost } = useConnection();
  const draft = event.status === 'DRAFT';
  const [more, setMore] = useState('50');
  const guests = draft ? event.expectedGuests : Number(more);
  const valid = Number.isInteger(guests) && guests >= 1;
  const price = useQuery({
    queryKey: ['event-price', event.id, guests],
    queryFn: () => plannerApi.price(event.id, guests),
    enabled: valid && event.status !== 'ENDED',
  });

  const pay = () =>
    act.run('pay', async () => {
      const started = await plannerApi.pay(event.id, guests);
      // Paystack sends the planner back to the payment page, which needs to know which event this was.
      try {
        window.localStorage.setItem('plately-event-pay', event.id);
      } catch {
        // Without it the planner lands on the event list instead.
      }
      window.location.assign(started.authorizationUrl);
    });

  if (event.status === 'ENDED') return null;

  return (
    <Card className="flex flex-col gap-3">
      <h2 className="font-heading text-[22px] font-normal text-bottle">{draft ? 'Price and pay' : 'Room for more guests?'}</h2>
      {!draft ? (
        <>
          <div className="flex items-center gap-3">
            <span aria-hidden="true" className="rounded-full border-2 border-good px-3 py-1 font-heading text-lg text-good">
              PAID
            </span>
            <p className="text-[17px] leading-snug text-ink">
              You have paid for {event.paidGuests} guests. {event.guestsOnList} are on the list.
            </p>
          </div>
          <Field label="More guests" inputMode="numeric" value={more} onChange={(e) => setMore(e.target.value.replace(/\D/g, ''))} hint="The list can never hold more than the number you have paid for." />
        </>
      ) : null}
      <div className="flex items-baseline justify-between gap-3 border-t border-dashed border-rule2 pt-3">
        <span className="text-[17px] text-quiet">{valid ? `For ${guests} guests` : 'Enter the number of guests to see the price'}</span>
        <span className="font-heading text-[30px] text-bottle">{price.data && valid ? kobo(price.data.amountKobo) : '–'}</span>
      </div>
      {act.failure ? <FailureCard title={act.failure.title} message={act.failure.message} /> : null}
      <Button big tone="primary" disabled={lost || act.busy !== null || !valid} onClick={() => void pay()}>
        {draft ? 'Pay and switch the event on' : `Pay for ${valid ? guests : ''} more guests`}
      </Button>
      <p className="text-sm leading-snug text-quiet">
        {draft ? 'Until it is paid the event is a draft: you can set up plates and tables, but guest codes cannot be made.' : 'You pay on Paystack, then come straight back here.'}
      </p>
    </Card>
  );
}

export default function EventPage() {
  const { id } = useParams<{ id: string }>();
  const { lost } = useConnection();
  const event = useEvent(id);
  const act = useAction();
  const payAct = useAction();
  const [ending, setEnding] = useState(false);
  const data = event.data;

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
      <div className="flex flex-col gap-6">
        {data ? (
          <Card>
            <h2 className="mb-3 font-heading text-[22px] font-normal text-bottle">About the event</h2>
            {act.failure ? <FailureCard className="mb-3" title={act.failure.title} message={act.failure.message} /> : null}
            {data.status === 'ENDED' ? (
              <p className="text-[17px] leading-snug text-quiet">This event is over, so its details can no longer change.</p>
            ) : (
              <EventDetails
                key={`${data.id}-${data.expectedGuests}-${data.seatingMode}-${data.platesPerGuest}`}
                initial={data}
                submitLabel="Save the changes"
                busy={lost || act.busy !== null}
                onSubmit={(form) => act.run('save', () => plannerApi.update(data.id, form))}
              />
            )}
          </Card>
        ) : null}
      </div>

      <div className="flex flex-col gap-6">
        {data ? <Payment event={data} act={payAct} /> : null}
        {data && data.status !== 'ENDED' ? (
          <Card className="flex flex-col gap-2">
            <h2 className="font-heading text-[22px] font-normal text-bottle">Event code</h2>
            <p className="font-heading text-[40px] tracking-[0.08em] text-bottle">{spaced(data.eventCode)}</p>
            <p className="text-[15px] leading-snug text-quiet">Guests do not type this: it is inside each table&apos;s card. Print the cards from the Tables section.</p>
          </Card>
        ) : null}
        {data && data.status !== 'ENDED' ? (
          <Card className="flex flex-col gap-2">
            <h2 className="font-heading text-[22px] font-normal text-bottle">Finished early?</h2>
            <p className="text-[15px] leading-snug text-quiet">Ending the event closes every guest&apos;s seat and stops all the codes. It ends by itself at the time you set.</p>
            <Button tone="outline" disabled={lost || act.busy !== null} onClick={() => setEnding(true)}>
              End the event now
            </Button>
          </Card>
        ) : null}
      </div>

      <Sheet open={ending} onClose={() => setEnding(false)} label="End the event">
        <h2 className="font-heading text-[28px] font-normal text-bottle">End {data?.name} now?</h2>
        <p className="mb-4 mt-2 text-[15px] leading-snug text-quiet">Every seat is closed and every code stops working. This cannot be undone.</p>
        {act.failure ? <FailureCard className="mb-3" title={act.failure.title} message={act.failure.message} /> : null}
        <div className="flex flex-col gap-2.5">
          <Button
            big
            tone="primary"
            disabled={lost || act.busy !== null}
            onClick={async () => {
              if (await act.run('end', () => plannerApi.end(id))) setEnding(false);
            }}
          >
            Yes, end it
          </Button>
          <Button big tone="outline" onClick={() => setEnding(false)}>
            Keep it going
          </Button>
        </div>
      </Sheet>
    </div>
  );
}
