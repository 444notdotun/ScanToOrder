'use client';

import Image from 'next/image';
import { Suspense, useEffect, useMemo, useRef, useState } from 'react';
import { useParams, useSearchParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import OrderTracker from '@/components/guest/OrderTracker';
import { useConnection } from '@/components/plately/Connection';
import { Loader } from '@/components/plately/Logo';
import { Button, Dashes, Eyebrow, FailureCard, Field } from '@/components/plately/ui';
import { guestApi, rememberedTable, useSession, useSessionOrders } from '@/lib/guest';
import { dataOf, failureOf, http, type ApiFailure } from '@/lib/http';
import { naira } from '@/lib/money';
import { useCart } from '@/store/cartStore';

// What'll it be? Pick plates, say who they are for, pay, and the kitchen starts cooking.
function OrderScreen() {
  const { slug } = useParams<{ slug: string }>();
  const resume = useSearchParams().get('pay');
  const { lost } = useConnection();
  const session = useSession().data!;
  const orders = useSessionOrders();
  const menu = useQuery({ queryKey: ['menu', slug], queryFn: () => guestApi.menu(slug), retry: false });

  const cart = useCart((state) => state.lines);
  const change = useCart((state) => state.change);
  const [forSeat, setForSeat] = useState<string | null>(null);
  const [email, setEmail] = useState(() => rememberedTable()?.email ?? '');
  const [failure, setFailure] = useState<ApiFailure | null>(null);
  const [paying, setPaying] = useState(false);
  const [section, setSection] = useState<string | null>(null);
  // An order that was made but not paid for is reused if the guest tries again with the same plates.
  const made = useRef<{ key: string; orderId: string } | null>(null);

  const mine = session.seats.find((seat) => seat.mine);
  const withoutPhone = session.seats.filter((seat) => seat.status === 'OCCUPIED' && !seat.mine && seat.hasDevice === false);
  const target = forSeat && withoutPhone.some((seat) => seat.seatId === forSeat) ? forSeat : null;
  const pending = resume ? orders.data?.orders.find((order) => order.orderId === resume && order.status === 'PENDING_PAYMENT') : undefined;

  // Tapping Basket on another page arrives here with #basket: go straight to it.
  useEffect(() => {
    if (window.location.hash === '#basket') document.getElementById('basket')?.scrollIntoView({ block: 'start' });
  }, [menu.data]);

  const priced = useMemo(() => {
    const lines: Array<{ name: string; quantity: number; price: number }> = [];
    for (const sectionOfMenu of menu.data ?? []) {
      for (const item of sectionOfMenu.itemResponse) {
        const quantity = cart[item.itemName] ?? 0;
        if (quantity > 0) lines.push({ name: item.itemName, quantity, price: Number(item.itemPrice) });
      }
    }
    return lines;
  }, [menu.data, cart]);
  const total = priced.reduce((sum, line) => sum + line.price * line.quantity, 0);

  const add = (name: string, by: number) => {
    setFailure(null);
    change(name, by);
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
  const sections = menu.data ?? [];
  const lit = section ?? sections[0]?.categoryName;

  const jump = (name: string) => {
    setSection(name);
    document.getElementById(`menu-${name}`)?.scrollIntoView({ block: 'start', behavior: 'smooth' });
  };

  return (
    <main className="mx-auto flex w-full max-w-md flex-1 animate-rise flex-col gap-3 px-5 pb-6 md:max-w-3xl xl:max-w-5xl">
      <h1 className="mt-3 font-heading text-[34px] font-normal leading-[1.1]">{pending ? 'Nearly there.' : "What'll it be?"}</h1>

      <OrderTracker />

      <div className="flex flex-col gap-3 xl:grid xl:grid-cols-[minmax(0,1fr)_360px] xl:items-start xl:gap-8">
        <div className="flex min-w-0 flex-col gap-3">
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
              <Eyebrow>Ordering for</Eyebrow>
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
            </div>
          ) : null}

          {menu.isPending ? (
            <p className="py-6 text-center text-soft">Opening the menu…</p>
          ) : menu.isError ? (
            <FailureCard title="We can't open the menu" message={failureOf(menu.error).message} />
          ) : sections.length === 0 ? (
            <p className="py-6 text-center text-soft">The kitchen hasn&apos;t put anything on the menu yet. Ask a waiter.</p>
          ) : (
            <>
              {/* The chips stay under the You bar while the menu scrolls; tapping one jumps to its section. */}
              <nav aria-label="Menu sections" className="sticky top-[var(--you-h)] z-20 -mx-5 bg-bottle/95 px-5 py-2 backdrop-blur-sm">
                <ul className="[scrollbar-width:none] flex gap-2 overflow-x-auto">
                  {sections.map((sectionOfMenu) => (
                    <li key={sectionOfMenu.categoryName} className="flex-none">
                      <button
                        type="button"
                        aria-current={lit === sectionOfMenu.categoryName ? 'true' : undefined}
                        onClick={() => jump(sectionOfMenu.categoryName)}
                        className={`min-h-[44px] whitespace-nowrap rounded-full border-[1.5px] px-4 text-[15px] font-bold ${
                          lit === sectionOfMenu.categoryName ? 'border-cream bg-cream text-bottle' : 'border-cream/45 text-cream'
                        }`}
                      >
                        {sectionOfMenu.categoryName}
                      </button>
                    </li>
                  ))}
                </ul>
              </nav>

              {sections.map((sectionOfMenu) => (
                <section key={sectionOfMenu.categoryName} id={`menu-${sectionOfMenu.categoryName}`} aria-label={sectionOfMenu.categoryName} className="grid scroll-mt-[calc(var(--you-h)+4rem)] grid-cols-1 gap-2.5 md:grid-cols-2">
                  <h2 className="mt-2 font-heading text-[22px] font-normal md:col-span-2">{sectionOfMenu.categoryName}</h2>
                  {sectionOfMenu.itemResponse.map((item) => {
                    const quantity = cart[item.itemName] ?? 0;
                    const chosen = quantity > 0;
                    return (
                      <article
                        key={item.itemName}
                        className={`overflow-hidden rounded-[18px] border-[1.5px] ${chosen ? 'border-cream bg-cream text-bottle' : 'border-cream/30 bg-bottle/60 text-cream'}`}
                      >
                        {/* A photo only when the dish has one: with none there is no picture space at all. */}
                        {item.imageUrl ? <Image unoptimized src={item.imageUrl} alt={item.itemName} width={600} height={320} className="h-40 w-full object-cover" /> : null}
                        <div className="flex items-center justify-between gap-3 p-3.5">
                          <div className="min-w-0">
                            <div className="text-base font-bold leading-tight">{item.itemName}</div>
                            <div className="mt-1 text-[15px] font-bold">{naira(item.itemPrice)}</div>
                            {item.itemDescription ? <div className={`mt-0.5 text-sm leading-snug ${chosen ? 'text-quiet' : 'text-soft'}`}>{item.itemDescription}</div> : null}
                            {item.readyInMinutes ? <div className={`mt-1 text-[13px] font-bold ${chosen ? 'text-good' : 'text-mustard'}`}>Ready in about {item.readyInMinutes} min</div> : null}
                          </div>
                          <div className="flex flex-none items-center gap-2">
                            {chosen ? (
                              <>
                                <button type="button" aria-label={`One less ${item.itemName}`} onClick={() => add(item.itemName, -1)} className="h-11 w-11 rounded-full border-[1.5px] border-bottle text-xl font-bold text-bottle">
                                  −
                                </button>
                                <span className="w-5 text-center text-lg font-bold" aria-live="polite">
                                  {quantity}
                                </span>
                                <button type="button" aria-label={`One more ${item.itemName}`} onClick={() => add(item.itemName, 1)} className="h-11 w-11 rounded-full bg-bottle text-xl font-bold text-cream">
                                  +
                                </button>
                              </>
                            ) : (
                              <button type="button" aria-label={`Add ${item.itemName}`} onClick={() => add(item.itemName, 1)} className="min-h-[44px] rounded-full bg-cream px-5 text-[15px] font-bold text-bottle">
                                Add
                              </button>
                            )}
                          </div>
                        </div>
                      </article>
                    );
                  })}
                </section>
              ))}
            </>
          )}
        </>
      )}

        </div>

      <section id="basket" aria-label="Your basket" className="mt-2 scroll-mt-[calc(var(--you-h)+1rem)] rounded-[20px] bg-paper p-5 text-ink xl:sticky xl:top-[calc(var(--you-h)+1rem)]">
        <h2 className="mb-1 font-heading text-[22px] font-normal text-bottle">Your basket</h2>
        {!pending && target ? <p className="mb-3 text-[15px] font-bold text-hand">For {withoutPhone.find((seat) => seat.seatId === target)?.guestName ?? 'your friend'}. You pay for them.</p> : <div className="mb-2" />}
        {!pending && priced.length === 0 ? <p className="mb-3 text-[15px] text-quiet">Nothing yet. Tap Add on a plate.</p> : null}
        {!pending && priced.length > 0 ? (
          <>
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
            <Dashes className="my-4" />
          </>
        ) : null}
        <Field
          label="Email for your receipt (optional)"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          autoComplete="email"
          hint="We'll send the receipt there. Leave it empty and you still get one on this screen."
        />
        {failure ? <FailureCard className="mt-3" title={failure.title} message={failure.message} /> : null}
        <Button className="mt-4" tone="primary" big disabled={!canPay || lost || !mine} onClick={() => void pay()}>
          {payLabel}
        </Button>
        <p className="mt-2 text-center text-sm text-quiet">The kitchen starts cooking the moment you&apos;ve paid.</p>
      </section>
      </div>
    </main>
  );
}

export default function OrderPage() {
  return (
    <Suspense fallback={<Loader message="Opening the menu…" />}>
      <OrderScreen />
    </Suspense>
  );
}
