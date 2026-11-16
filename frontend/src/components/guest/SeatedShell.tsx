'use client';

import { useEffect, useState, type ReactNode } from 'react';
import { useParams, usePathname, useRouter } from 'next/navigation';
import Ground from '@/components/plately/Ground';
import { Loader } from '@/components/plately/Logo';
import { ConnectionBanner, ConnectionContext } from '@/components/plately/Connection';
import CallWaiterSheet from '@/components/guest/CallWaiterSheet';
import TitleBar from '@/components/guest/TitleBar';
import BottomBar, { type BarItem } from '@/components/plately/BottomBar';
import { icons } from '@/components/plately/Icons';
import { cartCount, useCart } from '@/store/cartStore';
import { forgetTable, useSession } from '@/lib/guest';
import { failureOf } from '@/lib/http';
import { useLiveStream } from '@/hooks/useLiveStream';

// Everything a seated guest sees: it needs a live seat, keeps the guest's stream open, and sends the guest
// back to the opening page when the seat is gone (they left, or staff closed the table).
export default function SeatedShell({ children }: { children: ReactNode }) {
  const router = useRouter();
  const path = usePathname();
  const { slug, n, eventCode } = useParams<{ slug: string; n: string; eventCode?: string }>();
  const basket = cartCount(useCart((state) => state.lines));
  const [calling, setCalling] = useState(false);
  const session = useSession();
  const stream = useLiveStream('guest', Boolean(session.data));

  const failure = session.isError ? failureOf(session.error) : null;
  const gone = failure?.status === 401;
  useEffect(() => {
    if (gone) {
      forgetTable();
      router.replace(failure?.message.startsWith('Your visit has ended') ? '/?ended=visit' : '/?ended=1');
    }
  }, [gone, failure?.message, router]);

  if (session.isPending || gone) {
    return (
      <Ground>
        <Loader message="Finding your table…" />
      </Ground>
    );
  }
  if (session.isError) {
    return (
      <Ground>
        <ConnectionBanner lost caughtUp={false} />
        <main className="mx-auto flex w-full max-w-md md:max-w-xl flex-1 flex-col items-center justify-center gap-3 p-8 text-center">
          <p className="font-heading text-3xl leading-tight">We can&apos;t reach your table.</p>
          <p className="text-soft">{failure?.message}</p>
          <button type="button" onClick={() => void session.refetch()} className="mt-2 min-h-[44px] rounded-full bg-cream px-6 font-bold text-bottle">
            Try again
          </button>
        </main>
      </Ground>
    );
  }

  return (
    <ConnectionContext.Provider value={{ lost: stream.lost }}>
      <Ground>
        <ConnectionBanner lost={stream.lost} caughtUp={stream.caughtUp} />
        <TitleBar session={session.data} />
        {children}
        <BottomBar
          label="Your table"
          items={guestItems({ slug, n, eventCode, path, basket, call: () => setCalling(true) })}
        />
        <CallWaiterSheet open={calling} onClose={() => setCalling(false)} session={session.data} />
      </Ground>
    </ConnectionContext.Provider>
  );
}

// A restaurant guest moves between the menu, their table, their basket and a waiter. An event guest has no basket:
// one plate at a time, straight to the kitchen.
function guestItems({ slug, n, eventCode, path, basket, call }: { slug: string; n: string; eventCode?: string; path: string; basket: number; call: () => void }): BarItem[] {
  const callItem: BarItem = { key: 'call', label: 'Call waiter', icon: icons.bell, onSelect: call };
  if (eventCode) {
    const base = `/t/${slug}/e/${eventCode}/table/${n}`;
    return [
      { key: 'plates', label: 'Plates', icon: icons.plates, href: `${base}/plates`, current: path.endsWith('/plates') },
      { key: 'table', label: 'My table', icon: icons.table, href: `${base}/my-table`, current: path.endsWith('/my-table') },
      callItem,
    ];
  }
  const base = `/t/${slug}/table/${n}`;
  return [
    { key: 'menu', label: 'Menu', icon: icons.menu, href: `${base}/order`, current: path.endsWith('/order') },
    { key: 'table', label: 'My table', icon: icons.table, href: `${base}/home`, current: !path.endsWith('/order') },
    { key: 'basket', label: 'Basket', icon: icons.basket, href: `${base}/order#basket`, count: basket },
    callItem,
  ];
}
