import SeatRow from '@/components/guest/SeatRow';
import { OrderTrackerView } from '@/components/guest/OrderTracker';
import { Brand } from '@/components/plately/Logo';
import { Card } from '@/components/plately/ui';
import type { SessionOrder } from '@/lib/guest';

// Made-up on purpose: a demo restaurant and demo names, so nobody mistakes it for a real place or a real guest.
const DEMO_ORDERS: SessionOrder[] = [
  { orderId: 'demo-1', orderNumber: 'DEMO-1', status: 'PREPARING', items: ['2 x Jollof rice', '1 x Chapman'], forMe: true },
];

// The guest's "You're in" screen inside a phone frame, built from the same components the real screen uses.
export default function DemoPhone() {
  return (
    <figure className="mx-auto w-full max-w-[330px]">
      <div className="rounded-[44px] border-[10px] border-[#14110d] bg-bottle p-4 pb-5 shadow-[0_30px_60px_rgba(0,0,0,0.45)]">
        <div aria-hidden="true" className="mx-auto mb-3 h-1.5 w-16 rounded-full bg-cream/25" />
        <div className="flex flex-col gap-3">
          <header className="flex flex-col gap-1">
            <Brand size={20} />
            <div className="text-[12px] font-bold uppercase tracking-[0.12em] text-soft">Demo Kitchen · Table 4</div>
            <h3 className="font-heading text-[28px] font-normal leading-[1.08]">You&apos;re in, Ada.</h3>
          </header>
          <OrderTrackerView orders={DEMO_ORDERS} />
          <Card className="!p-4">
            <h4 className="mb-2 font-heading text-[20px] font-normal text-bottle">Your table</h4>
            <ul className="flex flex-col divide-y divide-rule">
              <SeatRow position={1} title="Ada (you)" note="This is your phone" />
              <SeatRow position={2} title="Tunde" note="On their own phone" />
              <SeatRow position={3} held title="Saved for a friend" note="8 minutes left on the hold" />
            </ul>
          </Card>
        </div>
      </div>
      <figcaption className="mt-3 text-center text-sm text-soft">What a guest sees once they&apos;ve paid. Demo restaurant and names.</figcaption>
    </figure>
  );
}
