'use client';

import { useState } from 'react';
import StaffShell from '@/components/staff/StaffShell';
import StockSection from '@/components/staff/StockSection';
import Ticket, { agedMinutes } from '@/components/staff/Ticket';
import { useConnection } from '@/components/plately/Connection';
import { Button, Dashes, FailureCard, Sheet } from '@/components/plately/ui';
import { FAILURE_ID, useAction } from '@/hooks/useAction';
import { useNow } from '@/hooks/useNow';
import { failureOf } from '@/lib/http';
import { staffApi, UNAVAILABLE_REASONS, useKitchen, type ServiceOrder, type UnavailableReason } from '@/lib/staff';

function Column({ title, count, empty, children }: { title: string; count: number; empty: string; children: React.ReactNode }) {
  return (
    <section aria-label={title} className="flex flex-col gap-3">
      <div className="flex items-baseline gap-2.5">
        <h2 className="font-heading text-[26px] font-normal">{title}</h2>
        <span className="rounded-full bg-cream/15 px-2.5 py-0.5 text-sm font-bold text-cream">{count}</span>
      </div>
      {count === 0 ? <p className="rounded-[20px] bg-paper p-4 text-[15px] text-quiet">{empty}</p> : children}
    </section>
  );
}

// Past this many minutes since it was paid, a card is marked: a clay edge and the wait in clay.
const LATE_AFTER = 15;

// One order on the pass: where it goes, who it is for, what it is, how long since it was paid, and the one thing to do.
function OrderCard({
  order,
  minutes,
  action,
  onAction,
  disabled,
  onCant,
}: {
  order: ServiceOrder;
  minutes: number;
  action: string;
  onAction: () => void;
  disabled: boolean;
  onCant: () => void;
}) {
  const late = minutes >= LATE_AFTER;
  const title = [order.tableNumber != null ? `Table ${order.tableNumber}` : null, order.seatPosition != null ? `Seat ${order.seatPosition}` : null].filter(Boolean).join(' · ');
  return (
    <article
      aria-label={`${title || order.orderNumber}${order.guestName ? `, ${order.guestName}` : ''}`}
      className={`flex animate-fade flex-col gap-2 rounded-2xl bg-paperlight p-4 text-ink shadow-[0_8px_20px_rgba(0,0,0,0.22)] ${late ? 'border-l-[6px] border-tomato' : 'border-l-[6px] border-transparent'}`}
    >
      <div className="flex items-baseline justify-between gap-3 text-[12px] font-bold">
        <span className="min-w-0 break-all uppercase tracking-[0.06em] text-quiet">{order.orderNumber}</span>
        <span className={`flex-none whitespace-nowrap ${late ? 'text-tomato' : 'text-good'}`}>{minutes <= 0 ? 'paid just now' : `paid ${minutes} min ago`}</span>
      </div>
      <h3 className="font-heading text-[26px] font-normal leading-[1.05] text-bottle">{title || order.orderNumber}</h3>
      {order.guestName ? <div className="break-words text-[15px] text-quiet">{order.guestName}</div> : null}
      <ul className="flex flex-col gap-0.5 text-[18px] font-bold leading-snug">
        {order.items.map((line, index) => (
          <li key={`${line}-${index}`}>{line}</li>
        ))}
      </ul>
      {(order.instructions ?? []).map((asked) => (
        <div key={asked} className="font-hand text-[23px] font-bold leading-[1.1] text-hand">
          {asked}
        </div>
      ))}
      {order.note ? <div className="font-hand text-[23px] font-bold leading-[1.1] text-hand">{order.note}</div> : null}
      {order.flag ? <div className="text-[15px] font-bold leading-snug text-[#8A5A00]">{order.flag}</div> : null}
      <Dashes className="my-1" />
      <button
        type="button"
        disabled={disabled}
        onClick={onAction}
        className="min-h-[56px] w-full rounded-full bg-tomato px-5 text-[18px] font-bold leading-tight text-white hover:bg-[#a23420]"
      >
        {action}
      </button>
      <button type="button" disabled={disabled} onClick={onCant} className="min-h-[44px] self-center rounded-full px-4 text-[15px] font-bold text-quiet underline underline-offset-4 hover:text-ink">
        Can&apos;t make this
      </button>
    </article>
  );
}

function Pass() {
  const { lost } = useConnection();
  const kitchen = useKitchen();
  const now = useNow();
  const act = useAction();
  const { busy, failure, run } = act;
  const [cant, setCant] = useState<ServiceOrder | null>(null);

  const orders = kitchen.data ?? [];
  const fetchedAt = kitchen.dataUpdatedAt;
  const byStep = (step: string) => orders.filter((order) => order.step === step);
  const fresh = byStep('PAID');
  const cooking = byStep('PREPARING');
  const ready = byStep('READY');

  const minutes = (order: ServiceOrder) => agedMinutes(order.minutesWaiting, fetchedAt, now);

  return (
    <main className="mx-auto flex w-full max-w-[1360px] flex-1 animate-rise flex-col gap-5 px-5 pb-6 pt-4">
      <div>
        <h1 className="font-heading text-[38px] font-normal leading-[1.1]">The pass</h1>
        <p className="mt-1 text-[17px] text-soft">Everything here is paid for. Oldest first.</p>
      </div>

      <div id={FAILURE_ID}>{failure ? <FailureCard title={failure.title} message={failure.message} /> : null}</div>

      {kitchen.isPending ? (
        <p role="status" className="text-soft">
          Fetching the orders…
        </p>
      ) : kitchen.isError ? (
        <FailureCard title="We couldn't load the orders" message={failureOf(kitchen.error).message} />
      ) : (
        <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1fr)_300px] xl:items-start">
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3 md:items-start">
            <Column title="New" count={fresh.length} empty="Nothing waiting. Enjoy the quiet.">
              {fresh.map((order) => (
                <OrderCard
                  key={order.orderId}
                  order={order}
                  minutes={minutes(order)}
                  action="Start cooking"
                  disabled={lost || busy !== null}
                  onAction={() => void run(order.orderId, () => staffApi.start(order.orderId))}
                  onCant={() => setCant(order)}
                />
              ))}
            </Column>
            <Column title="Cooking" count={cooking.length} empty="Nothing on the fire.">
              {cooking.map((order) => (
                <OrderCard
                  key={order.orderId}
                  order={order}
                  minutes={minutes(order)}
                  action="Ready"
                  disabled={lost || busy !== null}
                  onAction={() => void run(order.orderId, () => staffApi.markReady(order.orderId))}
                  onCant={() => setCant(order)}
                />
              ))}
            </Column>
            <Column title="Ready" count={ready.length} empty="Nothing waiting to be carried.">
              {ready.map((order) => (
                <Ticket key={order.orderId} order={order} minutes={minutes(order)} waitLabel="ready" flag={order.flag}>
                  <p className="text-[15px] text-quiet">Waiting for a waiter to carry it over.</p>
                </Ticket>
              ))}
            </Column>
          </div>
          <StockSection act={act} />
        </div>
      )}


      <Sheet open={cant !== null} onClose={() => setCant(null)} label="Why it can't be made">
        <h2 className="font-heading text-[28px] font-normal text-bottle">What&apos;s stopping it?</h2>
        <p className="mb-4 mt-2 text-[15px] leading-snug text-quiet">
          {cant ? `${cant.orderNumber}: ${cant.items.join(', ')}${cant.guestName ? ` for ${cant.guestName}` : ''}${cant.tableNumber != null ? ` at Table ${cant.tableNumber}` : ''}. ` : ''}
          A waiter will go to the table and offer something else.
        </p>
        <div className="flex flex-col gap-2.5">
          {UNAVAILABLE_REASONS.map(({ reason, label }) => (
            <Button
              key={reason}
              big
              tone={reason === 'RAN_OUT' ? 'primary' : 'bottle'}
              disabled={lost || busy !== null}
              onClick={() => {
                const order = cant;
                setCant(null);
                if (order) void run(order.orderId, () => staffApi.unavailable(order.orderId, reason as UnavailableReason));
              }}
            >
              {label}
            </Button>
          ))}
          <Button big tone="outline" onClick={() => setCant(null)}>
            Never mind
          </Button>
        </div>
      </Sheet>
    </main>
  );
}

export default function KitchenPage() {
  return (
    <StaffShell allow={['CHEF', 'MANAGER']}>
      <Pass />
    </StaffShell>
  );
}
