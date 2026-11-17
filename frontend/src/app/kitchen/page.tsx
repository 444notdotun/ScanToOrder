'use client';

import { useState } from 'react';
import StaffShell from '@/components/staff/StaffShell';
import StockSection from '@/components/staff/StockSection';
import Ticket, { agedMinutes } from '@/components/staff/Ticket';
import { useConnection } from '@/components/plately/Connection';
import { Button, FailureCard, Sheet } from '@/components/plately/ui';
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
    <main className="mx-auto flex w-full max-w-6xl flex-1 animate-rise flex-col gap-5 px-5 pb-6 pt-4">
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
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
          <Column title="New" count={fresh.length} empty="Nothing waiting. Enjoy the quiet.">
            {fresh.map((order) => (
              <Ticket key={order.orderId} order={order} minutes={minutes(order)} waitLabel="waiting" flag={order.flag}>
                <Button big disabled={lost || busy !== null} onClick={() => void run(order.orderId, () => staffApi.start(order.orderId))}>
                  Start cooking
                </Button>
                <Button big tone="outline" disabled={lost || busy !== null} onClick={() => setCant(order)}>
                  Can&apos;t make this
                </Button>
              </Ticket>
            ))}
          </Column>
          <Column title="Cooking" count={cooking.length} empty="Nothing on the fire.">
            {cooking.map((order) => (
              <Ticket key={order.orderId} order={order} minutes={minutes(order)} waitLabel="waiting" flag={order.flag}>
                <Button big disabled={lost || busy !== null} onClick={() => void run(order.orderId, () => staffApi.markReady(order.orderId))}>
                  Ready
                </Button>
                <Button big tone="outline" disabled={lost || busy !== null} onClick={() => setCant(order)}>
                  Can&apos;t make this
                </Button>
              </Ticket>
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
      )}

      <StockSection act={act} />

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
