'use client';

import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useConnection } from '@/components/plately/Connection';
import { Button, Card, FailureCard, Field } from '@/components/plately/ui';
import { useAction } from '@/hooks/useAction';
import { useTenant } from '@/lib/manager';
import { kobo } from '@/lib/money';
import { plannerApi, type PlannerEvent } from '@/lib/planner';

const EMAIL_REFUSED = "Paystack couldn't use that email";

// What the event costs and the button that pays for it: the first payment switches the event on, later ones add guests.
// The receipt goes to the business email unless the planner types another.
export default function EventPayment({ event }: { event: PlannerEvent }) {
  const { lost } = useConnection();
  const tenant = useTenant();
  const act = useAction();
  const draft = event.status === 'DRAFT';
  const [more, setMore] = useState('50');
  const [typed, setTyped] = useState<string | null>(null);
  const email = typed ?? tenant.data?.email ?? '';
  const guests = draft ? event.expectedGuests : Number(more);
  const valid = Number.isInteger(guests) && guests >= 1;
  const price = useQuery({
    queryKey: ['event-price', event.id, guests],
    queryFn: () => plannerApi.price(event.id, guests),
    enabled: valid && event.status !== 'ENDED',
  });
  const emailRefused = act.failure?.message.startsWith(EMAIL_REFUSED) ?? false;

  const pay = () =>
    act.run('pay', async () => {
      const started = await plannerApi.pay(event.id, guests, email);
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
      <Field
        label="Send the receipt to"
        type="email"
        autoComplete="email"
        value={email}
        onChange={(e) => setTyped(e.target.value)}
        error={emailRefused ? "Paystack couldn't use that email for the receipt. Check it and try again." : undefined}
        hint={emailRefused ? undefined : 'Prefilled with your business email. Change it if the receipt should go elsewhere.'}
      />
      {act.failure && !emailRefused ? <FailureCard title={act.failure.title} message={act.failure.message} /> : null}
      <Button big tone="primary" disabled={lost || act.busy !== null || !valid} onClick={() => void pay()}>
        {draft ? 'Pay and switch the event on' : `Pay for ${valid ? guests : ''} more guests`}
      </Button>
      <p className="text-sm leading-snug text-quiet">
        {draft ? 'Until it is paid the event is a draft: you can set up plates and tables, but guest codes cannot be made.' : 'You pay on Paystack, then come straight back here.'}
      </p>
    </Card>
  );
}
