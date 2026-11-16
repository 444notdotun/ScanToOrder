'use client';

import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useId, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import OrderTracker from '@/components/guest/OrderTracker';
import { Brand } from '@/components/plately/Logo';
import { useConnection } from '@/components/plately/Connection';
import { Button, Card, FailureCard, Handwriting, Sheet, Switch } from '@/components/plately/ui';
import { forgetTable, guestApi, useSession, useSessionOrders, type SessionSeat } from '@/lib/guest';
import { failureOf, http, type ApiFailure } from '@/lib/http';
import { naira, firstName } from '@/lib/money';
import { orderStatusLabel, spacedCode } from '@/lib/labels';
import { useNow } from '@/hooks/useNow';

// Your table: who is here, who is being waited for, what has been ordered.
export default function HomePage() {
  const { slug, n } = useParams<{ slug: string; n: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();
  const { lost } = useConnection();
  const now = useNow();
  const session = useSession().data!;
  const orders = useSessionOrders();
  const switchLabel = useId();

  const [saving, setSaving] = useState(false);
  const [picks, setPicks] = useState<string[]>([]);
  const [failure, setFailure] = useState<ApiFailure | null>(null);
  const [busy, setBusy] = useState(false);
  const [leaving, setLeaving] = useState(false);

  const mine = session.seats.find((seat) => seat.mine);
  const held = session.seats.filter((seat) => seat.status === 'HELD');
  const map = useQuery({ queryKey: ['seatmap', slug, n], queryFn: () => guestApi.seatMap(slug, n), enabled: saving });
  const freeSeats = (map.data?.seats ?? []).filter((seat) => seat.status === 'VACANT');

  const run = async (work: () => Promise<void>, title?: string) => {
    setBusy(true);
    setFailure(null);
    try {
      await work();
      await queryClient.invalidateQueries();
    } catch (error) {
      setFailure(failureOf(error, title));
    } finally {
      setBusy(false);
    }
  };

  const confirmHold = async () => {
    if (picks.length === 0) {
      setFailure({ status: null, title: '', message: 'Tap at least one chair to save.', offline: false });
      return;
    }
    await run(async () => {
      await http.post('/api/v1/sessions/me/holds', { seatIds: picks });
      setPicks([]);
      setSaving(false);
    }, 'We saved none of them');
  };

  const letGo = (seat: SessionSeat) => run(async () => void (await http.delete(`/api/v1/sessions/me/holds/${seat.seatId}`)));

  const leave = async () => {
    setBusy(true);
    try {
      await http.post('/api/v1/sessions/me/leave');
      forgetTable();
      queryClient.clear();
      router.replace('/scan');
    } catch (error) {
      setLeaving(false);
      setFailure(failureOf(error, "We couldn't free your seat"));
    } finally {
      setBusy(false);
    }
  };

  const title = (seat: SessionSeat) => (seat.status === 'HELD' ? 'Saved for a friend' : seat.mine ? `${seat.guestName ?? 'You'} (you)` : (seat.guestName ?? 'Guest'));
  const note = (seat: SessionSeat) => {
    if (seat.status === 'HELD') {
      const left = seat.heldUntil ? Math.max(0, Math.round((new Date(seat.heldUntil).getTime() - now) / 60000)) : 0;
      return `${left} minute${left === 1 ? '' : 's'} left on the hold`;
    }
    if (seat.mine) return 'This is your phone';
    return seat.hasDevice === false ? 'No phone. You can order for them' : 'On their own phone';
  };

  return (
    <>
      <main className="mx-auto flex w-full max-w-md flex-1 animate-rise flex-col gap-4 px-5 pb-6 pt-6 md:max-w-3xl xl:max-w-5xl">
        <header className="flex flex-col gap-1.5">
          <Brand size={22} />
          <h1 className="font-heading text-[36px] font-normal leading-[1.08]">You&apos;re in, {firstName(mine?.guestName)}.</h1>
          <p className="text-[17px] text-soft">Order when you&apos;re ready. Nobody&apos;s rushing you.</p>
        </header>

        {failure ? <FailureCard title={failure.title} message={failure.message} /> : null}

        <OrderTracker />

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 md:items-start">
        <Card>
          <h2 className="mb-3 font-heading text-[22px] font-normal text-bottle">Your table</h2>
          <ul className="flex flex-col divide-y divide-rule">
            {session.seats.map((seat) => (
              <li key={seat.seatId} className="py-3 first:pt-0">
                <div className="flex items-center gap-3">
                  <div className={`flex h-10 w-10 flex-none items-center justify-center rounded-full text-base font-bold ${seat.status === 'HELD' ? 'border-2 border-mustard bg-mustard/20 text-bottle' : 'bg-bottle text-cream'}`}>
                    {seat.position}
                  </div>
                  <div>
                    <div className="text-base font-bold text-ink">{title(seat)}</div>
                    <div className="text-sm text-quiet">{note(seat)}</div>
                  </div>
                </div>
                {seat.status === 'HELD' ? (
                  <div className="mt-2.5 flex gap-2">
                    <Link href={`/t/${slug}/table/${n}/guest/${seat.seatId}`} className="flex min-h-[44px] flex-1 items-center justify-center rounded-full bg-bottle px-4 text-center text-[15px] font-bold text-cream">
                      My guest is here
                    </Link>
                    <Button tone="outline" full={false} className="flex-1" disabled={busy || lost} onClick={() => void letGo(seat)}>
                      Let it go
                    </Button>
                  </div>
                ) : null}
              </li>
            ))}
          </ul>

          {session.holdsEnabled ? (
            <div className="mt-4 border-t border-dashed border-rule2 pt-4">
              <div className="flex items-center justify-between gap-3">
                <div id={switchLabel} className="text-base font-bold text-bottle">
                  Saving a chair for a friend?
                </div>
                <Switch
                  checked={saving}
                  labelledBy={switchLabel}
                  onChange={(next) => {
                    setSaving(next);
                    setPicks([]);
                    setFailure(null);
                  }}
                />
              </div>
              {saving ? (
                <div className="mt-3 flex flex-col gap-3">
                  {map.isPending ? (
                    <p className="text-sm text-quiet">Looking at the chairs…</p>
                  ) : freeSeats.length > 0 ? (
                    <>
                      <div className="text-sm font-bold text-quiet">Tap the chairs to keep. Each one is saved for 10 minutes.</div>
                      <div className="flex flex-wrap gap-2">
                        {freeSeats.map((seat) => {
                          const on = picks.includes(seat.seatId);
                          return (
                            <button
                              key={seat.seatId}
                              type="button"
                              aria-pressed={on}
                              onClick={() => {
                                setFailure(null);
                                setPicks(on ? picks.filter((id) => id !== seat.seatId) : [...picks, seat.seatId]);
                              }}
                              className={`min-h-[44px] rounded-full border-[1.5px] px-4 text-[15px] font-bold ${on ? 'border-bottle bg-bottle text-cream' : 'border-rule2 bg-paperlight text-bottle'}`}
                            >
                              Seat {seat.position}
                              <span className="ml-1.5 text-xs font-normal">{on ? 'Saving this one' : 'Free'}</span>
                            </button>
                          );
                        })}
                      </div>
                      <Button tone="primary" disabled={busy || lost} onClick={() => void confirmHold()}>
                        {picks.length === 0 ? 'Save seats for friends' : `Save ${picks.length} seat${picks.length === 1 ? '' : 's'} for friends`}
                      </Button>
                    </>
                  ) : (
                    <p className="text-sm text-quiet">There are no free chairs left at this table to save.</p>
                  )}
                </div>
              ) : null}
              {held.length > 0 ? (
                <div className="mt-4 animate-pop rounded-2xl border-[1.5px] border-dashed border-hand/50 bg-paperlight p-4 text-center">
                  <div className="text-sm font-bold text-quiet">Give your friends this code.</div>
                  <div className="my-1 font-heading text-[40px] tracking-[0.08em] text-bottle">{spacedCode(session.joinCode)}</div>
                  <Handwriting onPaper className="!text-[20px]">
                    They scan the table, tap a saved chair and type it in.
                  </Handwriting>
                </div>
              ) : null}
            </div>
          ) : null}
        </Card>

        {orders.data && orders.data.orders.length > 0 ? (
          <Card>
            <h2 className="mb-3 font-heading text-[22px] font-normal text-bottle">Your orders</h2>
            <ul className="flex flex-col divide-y divide-rule">
              {orders.data.orders.map((order) => (
                <li key={order.orderId} className="flex items-center justify-between gap-3 py-3 first:pt-0">
                  <div>
                    <div className="text-base font-bold text-ink">{order.items.join(', ') || order.orderNumber}</div>
                    <div className="text-sm text-quiet">
                      {order.forMe ? 'For you' : `For ${order.forName ?? `seat ${order.forSeatPosition}`}`} · {naira(order.amount)}
                    </div>
                    <div className={`text-sm font-bold ${order.status === 'UNAVAILABLE' ? 'text-hand' : 'text-good'}`}>{orderStatusLabel(order.status)}</div>
                  </div>
                  {order.paymentReference ? (
                    <Link href={`/t/${slug}/table/${n}/receipt/${order.paymentReference}`} aria-label={`Receipt for ${order.orderNumber}`} className="flex min-h-[44px] items-center rounded-full border-[1.5px] border-bottle px-4 text-[15px] font-bold text-bottle">
                      Receipt
                    </Link>
                  ) : order.status === 'PENDING_PAYMENT' ? (
                    <Link href={`/t/${slug}/table/${n}/order?pay=${order.orderId}`} className="flex min-h-[44px] items-center rounded-full bg-tomato px-4 text-[15px] font-bold text-white">
                      Pay now
                    </Link>
                  ) : null}
                </li>
              ))}
            </ul>
          </Card>
        ) : null}

        </div>

        <div className="flex gap-2.5">
          <Link href={`/t/${slug}/table/${n}/visit`} className="flex min-h-[44px] flex-1 items-center justify-center rounded-full border-[1.5px] border-cream/45 px-4 text-center text-[15px] font-bold text-cream">
            Your visit
          </Link>
          <Button tone="ghost" className="flex-1" onClick={() => setLeaving(true)}>
            Leave the table
          </Button>
        </div>
      </main>

      <Sheet open={leaving} onClose={() => setLeaving(false)} label="Leave the table">
        <h2 className="font-heading text-[28px] font-normal text-bottle">Heading off?</h2>
        <p className="mb-5 mt-2 text-[15px] leading-snug text-quiet">
          Your chair goes back to the room. Anything you&apos;ve already paid for stays with the waiters, and we can&apos;t refund it.
        </p>
        <div className="flex flex-col gap-2.5">
          <Button tone="primary" disabled={busy || lost} onClick={() => void leave()}>
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
