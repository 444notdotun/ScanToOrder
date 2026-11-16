'use client';

import { useSessionOrders, type SessionOrder } from '@/lib/guest';

const STEPS = ['Paid', 'Cooking', 'Ready', 'On its way'];
// Where each order status sits on the bar. Once the kitchen has it ready it is the waiters' job to bring it over.
const STEP_OF: Record<string, number> = { PAID: 0, PREPARING: 1, READY: 3 };

// "2 x Jollof rice" is "Jollof rice"; several lines read "Jollof rice and 1 more".
function dishOf(order: SessionOrder): { label: string; many: boolean } {
  const names = order.items.map((line) => line.replace(/^\d+\s*x\s*/i, ''));
  if (names.length === 0) return { label: order.orderNumber, many: false };
  return names.length === 1 ? { label: names[0], many: false } : { label: `${names[0]} and ${names.length - 1} more`, many: true };
}

export function lineFor(order: SessionOrder): string {
  const { label, many } = dishOf(order);
  const verb = many ? 'are' : 'is';
  if (order.status === 'PAID') return `Your ${label} ${verb} with the kitchen`;
  if (order.status === 'PREPARING') return `Your ${label} ${verb} cooking`;
  return `Your ${label} ${verb} ready and on the way to you`;
}

// A step bar for each open order. It moves by itself: the guest's live stream refetches the orders on every notice.
export default function OrderTracker({ className = '' }: { className?: string }) {
  const orders = useSessionOrders();
  const open = (orders.data?.orders ?? []).filter((order) => order.status in STEP_OF);
  if (open.length === 0) return null;

  return (
    <section aria-label="Your orders in progress" className={`flex flex-col gap-3 ${className}`}>
      {open.map((order) => {
        const at = STEP_OF[order.status];
        return (
          <div key={order.orderId} className="rounded-[20px] bg-paper p-4 text-ink shadow-[0_10px_28px_rgba(0,0,0,0.22)]">
            <div className="text-[12px] font-bold uppercase tracking-[0.12em] text-quiet">{order.forMe ? 'For you' : `For ${order.forName ?? `seat ${order.forSeatPosition}`}`}</div>
            <p role="status" className="break-words text-[16px] font-bold leading-snug text-bottle">
              {lineFor(order)}
            </p>
            <ol aria-label="Progress" className="mt-3 flex items-start">
              {STEPS.map((step, index) => {
                const done = index < at;
                const now = index === at;
                return (
                  <li key={step} aria-current={now ? 'step' : undefined} className="relative flex flex-1 flex-col items-center gap-1.5 text-center">
                    {index > 0 ? <span aria-hidden="true" className={`absolute right-1/2 top-[11px] h-[3px] w-full ${index <= at ? 'bg-bottle' : 'bg-rule'}`} /> : null}
                    <span
                      aria-hidden="true"
                      className={`relative z-10 flex h-[24px] w-[24px] items-center justify-center rounded-full border-2 text-[12px] font-bold ${
                        done ? 'border-bottle bg-bottle text-cream' : now ? 'border-bottle bg-mustard text-bottle motion-safe:animate-breathe' : 'border-rule2 bg-paperlight text-quiet'
                      }`}
                    >
                      {done ? '✓' : index + 1}
                    </span>
                    <span className={`text-[12px] leading-tight ${now ? 'font-bold text-bottle' : done ? 'font-bold text-quiet' : 'text-quiet'}`}>
                      {step}
                      <span className="sr-only">{done ? ', done' : now ? ', now' : ', next'}</span>
                    </span>
                  </li>
                );
              })}
            </ol>
          </div>
        );
      })}
    </section>
  );
}
