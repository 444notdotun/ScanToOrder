'use client';

import { Suspense, useEffect, useMemo, useRef, useState } from 'react';
import { useParams, useSearchParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import BackLink from '@/components/guest/BackLink';
import { useConnection } from '@/components/plately/Connection';
import { Loader } from '@/components/plately/Logo';
import { Button, Dashes, Eyebrow, FailureCard, Field } from '@/components/plately/ui';
import { guestApi, rememberedTable, useSession, useSessionOrders } from '@/lib/guest';
import { dataOf, failureOf, http, type ApiFailure } from '@/lib/http';
import { naira } from '@/lib/money';

// What'll it be? Pick plates, say who they are for, pay, and the kitchen starts cooking.
function OrderScreen() {
  const { slug, n } = useParams<{ slug: string; n: string }>();
  const resume = useSearchParams().get('pay');
  const { lost } = useConnection();
  const session = useSession().data!;
  const orders = useSessionOrders();
  const menu = useQuery({ queryKey: ['menu', slug], queryFn: () => guestApi.menu(slug), retry: false });

  const [cart, setCart] = useState<Record<string, number>>({});
  const [forSeat, setForSeat] = useState<string | null>(null);
  const [email, setEmail] = useState(() => rememberedTable()?.email ?? '');
  const [failure, setFailure] = useState<ApiFailure | null>(null);
  const [paying, setPaying] = useState(false);
  // An order that was made but not paid for is reused if the guest tries again with the same plates.
  const made = useRef<{ key: string; orderId: string } | null>(null);
  const failureCard = useRef<HTMLDivElement>(null);
  // The pay button is pinned to the bottom, so a failure is brought into view.
  useEffect(() => {
    if (failure) failureCard.current?.scrollIntoView({ block: 'center' });
  }, [failure]);

  const mine = session.seats.find((seat) => seat.mine);
  const withoutPhone = session.seats.filter((seat) => seat.status === 'OCCUPIED' && !seat.mine && seat.hasDevice === false);
  const target = forSeat && withoutPhone.some((seat) => seat.seatId === forSeat) ? forSeat : null;
  const pending = resume ? orders.data?.orders.find((order) => order.orderId === resume && order.status === 'PENDING_PAYMENT') : undefined;

  const priced = useMemo(() => {
    const lines: Array<{ name: string; quantity: number; price: number }> = [];
    for (const section of menu.data ?? []) {
      for (const item of section.itemResponse) {
        const quantity = cart[item.itemName] ?? 0;
        if (quantity > 0) lines.push({ name: item.itemName, quantity, price: Number(item.itemPrice) });
      }
    }
    return lines;
  }, [menu.data, cart]);
  const total = priced.reduce((sum, line) => sum + line.price * line.quantity, 0);

  const change = (name: string, by: number) => {
    setFailure(null);
    setCart((current) => {
      const next = Math.max(0, Math.min(20, (current[name] ?? 0) + by));
      const copy = { ...current };
      if (next === 0) delete copy[name];
      else copy[name] = next;
      return copy;
    });
  };

  const goToBank = async (orderId: string) => {
    const response = await http.post('/api/v1/payments/initialize', { orderId, customerEmail: email.trim() || undefined });
    const { authorizationUrl } = dataOf<{ authorizationUrl: string }>(response);
    window.location.assign(authorizationUrl);
  };

  const pay = async () => {
    if (email.trim() && !/^\S+@\S+\.\S+$/.test(email.trim())) {
      setFailure({ status: null, title: '', message: 'That email does not look right. Leave it empty if you would rather not give one.', offline: false });
      return;
    }
    setPaying(true);
    setFailure(null);
    try {
      if (pending) {
        await goToBank(pending.orderId);
        return;
      }
      const key = JSON.stringify([priced.map((l) => [l.name, l.quantity]), target]);
      let orderId = made.current?.key === key ? made.current.orderId : null;
      if (!orderId) {
        const created = await http.post('/api/v1/orders', {
          seatId: target ?? undefined,
          orderItems: priced.map((line) => ({ itemName: line.name, quantity: line.quantity })),
        });
        orderId = dataOf<{ orderId: string }>(created).orderId;
        made.current = { key, orderId };
      }
      await goToBank(orderId);
    } catch (error) {
      setPaying(false);
      const failed = failureOf(error, "That didn't go through");
      setFailure(failed);
      // A plate that ran out while the guest was choosing: show the menu as it is now.
      if (failed.status === 409) void menu.refetch();
    }
  };

  if (paying) return <Loader message="Taking you to your bank…" />;

  const canPay = pending ? true : priced.length > 0;
  const payLabel = pending ? `Pay ${naira(pending.amount)}` : priced.length === 0 ? 'Pick a plate first' : `Pay ${naira(total)}`;

  return (
    <>
      <main className="mx-auto flex w-full max-w-md md:max-w-xl flex-1 animate-rise flex-col gap-3 px-5 pb-52 pt-6">
        <BackLink href={`/t/${slug}/table/${n}/home`} />
        <h1 className="font-heading text-[34px] font-normal leading-[1.1]">{pending ? 'Nearly there.' : "What'll it be?"}</h1>

        {pending ? (
          <div className="rounded-[20px] bg-paper p-5 text-ink">
            <div className="text-base font-bold">{pending.items.join(', ')}</div>
            <div className="text-sm text-quiet">
              {pending.forMe ? 'For you' : `For ${pending.forName ?? `seat ${pending.forSeatPosition}`}`} · {naira(pending.amount)}
            </div>
          </div>
        ) : (
          <>
            {withoutPhone.length > 0 ? (
              <div className="flex flex-col gap-2">
                <Eyebrow>Who is this order for?</Eyebrow>
                <div className="flex flex-wrap gap-2">
                  {[{ seatId: null as string | null, label: 'Me' }, ...withoutPhone.map((seat) => ({ seatId: seat.seatId as string | null, label: seat.guestName ?? `Seat ${seat.position}` }))].map((choice) => {
                    const on = (target ?? null) === choice.seatId;
                    return (
                      <button
                        key={choice.seatId ?? 'me'}
                        type="button"
                        aria-pressed={on}
                        onClick={() => setForSeat(choice.seatId)}
                        className={`min-h-[44px] rounded-full border-[1.5px] px-4 text-[15px] font-bold ${on ? 'border-cream bg-cream text-bottle' : 'border-cream/45 text-cream'}`}
                      >
                        {choice.label}
                      </button>
                    );
                  })}
                </div>
                <div aria-hidden="true" className="h-px bg-cream/20" />
              </div>
            ) : null}

            {menu.isPending ? (
              <p className="py-6 text-center text-soft">Opening the menu…</p>
            ) : menu.isError ? (
              <FailureCard title="We can't open the menu" message={failureOf(menu.error).message} />
            ) : menu.data.length === 0 ? (
              <p className="py-6 text-center text-soft">The kitchen hasn&apos;t put anything on the menu yet. Ask a waiter.</p>
            ) : (
              menu.data.map((section) => (
                <section key={section.categoryName} className="flex flex-col gap-2.5">
                  <h2 className="mt-2 font-heading text-[22px] font-normal">{section.categoryName}</h2>
                  {section.itemResponse.map((item) => {
                    const quantity = cart[item.itemName] ?? 0;
                    return (
                      <div
                        key={item.itemName}
                        className={`flex items-center justify-between gap-3 rounded-[18px] border-[1.5px] p-3.5 ${quantity > 0 ? 'border-cream bg-cream text-bottle' : 'border-cream/30 bg-bottle/60 text-cream'}`}
                      >
                        <div className="min-w-0">
                          <div className="text-base font-bold leading-tight">{item.itemName}</div>
                          {item.itemDescription ? <div className={`mt-0.5 text-sm leading-snug ${quantity > 0 ? 'text-quiet' : 'text-soft'}`}>{item.itemDescription}</div> : null}
                          <div className="mt-1 text-[15px] font-bold">{naira(item.itemPrice)}</div>
                        </div>
                        <div className="flex flex-none items-center gap-2">
                          {quantity > 0 ? (
                            <>
                              <button type="button" aria-label={`One less ${item.itemName}`} onClick={() => change(item.itemName, -1)} className="h-11 w-11 rounded-full border-[1.5px] border-bottle text-xl font-bold text-bottle">
                                −
                              </button>
                              <span className="w-5 text-center text-lg font-bold" aria-live="polite">
                                {quantity}
                              </span>
                            </>
                          ) : null}
                          <button type="button" aria-label={`Add ${item.itemName}`} onClick={() => change(item.itemName, 1)} className={`h-11 w-11 rounded-full text-xl font-bold ${quantity > 0 ? 'bg-bottle text-cream' : 'bg-cream text-bottle'}`}>
                            +
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </section>
              ))
            )}
          </>
        )}

        <div className="mt-2 rounded-[20px] bg-paper p-5 text-ink">
          <Field
            label="Email for your receipt (optional)"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            hint="We'll send the receipt there. Leave it empty and you still get one on this screen."
          />
          {!pending && priced.length > 0 ? (
            <>
              <Dashes className="my-4" />
              {priced.map((line) => (
                <div key={line.name} className="flex justify-between gap-3 py-0.5 text-[15px]">
                  <span>
                    {line.quantity} × {line.name}
                  </span>
                  <span className="font-bold">{naira(line.price * line.quantity)}</span>
                </div>
              ))}
              <div className="mt-2 flex justify-between border-t border-dashed border-rule2 pt-2 text-base font-bold">
                <span>Total</span>
                <span>{naira(total)}</span>
              </div>
            </>
          ) : null}
        </div>

        <div ref={failureCard}>{failure ? <FailureCard title={failure.title} message={failure.message} /> : null}</div>
        <p className="text-center text-sm text-soft">The kitchen starts cooking the moment you&apos;ve paid.</p>
      </main>

      <div className="fixed inset-x-0 bottom-0 z-20 bg-gradient-to-t from-bottle via-bottle/95 to-transparent px-5 pb-[calc(1.25rem+env(safe-area-inset-bottom)+var(--corner-clear))] pt-8">
        <div className="mx-auto max-w-md md:max-w-xl">
          <Button tone="primary" big disabled={!canPay || lost || !mine} onClick={() => void pay()}>
            {payLabel}
          </Button>
        </div>
      </div>
    </>
  );
}

export default function OrderPage() {
  return (
    <Suspense fallback={<Loader message="Opening the menu…" />}>
      <OrderScreen />
    </Suspense>
  );
}
