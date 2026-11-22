'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import Ground from '@/components/plately/Ground';
import { Brand, Loader } from '@/components/plately/Logo';
import TableProblem from '@/components/guest/TableProblem';
import { Eyebrow, FailureCard, Handwriting } from '@/components/plately/ui';
import ClaimSheet from '@/components/guest/ClaimSheet';
import SeatButton, { SeatLegend } from '@/components/guest/SeatButton';
import { guestApi, rememberTable, type SeatOnMap } from '@/lib/guest';
import { failureOf } from '@/lib/http';

// Scan the table, then tap the chair you're in.
export default function TableMapPage() {
  const { slug, n } = useParams<{ slug: string; n: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();
  const [picked, setPicked] = useState<SeatOnMap | null>(null);
  const [notice, setNotice] = useState<{ title?: string; message: string } | null>(null);

  // Someone who is already seated goes straight to their table.
  const session = useQuery({ queryKey: ['session'], queryFn: guestApi.session, retry: false });
  useEffect(() => {
    if (session.data) router.replace(`/t/${slug}/table/${n}/home`);
  }, [session.data, router, slug, n]);

  const map = useQuery({
    queryKey: ['seatmap', slug, n],
    queryFn: () => guestApi.seatMap(slug, n),
    retry: false,
    // Nobody is seated yet, so there is no stream: look again when the guest comes back to the screen.
    refetchOnWindowFocus: true,
  });

  if (map.isPending || session.isPending || session.data) {
    return (
      <Ground>
        <Loader message="Finding your table…" />
      </Ground>
    );
  }

  if (map.isError) {
    return <TableProblem failure={failureOf(map.error)} tableNumber={n} onRetry={() => void map.refetch()} />;
  }

  const table = map.data;
  const seats = [...table.seats].sort((a, b) => a.position - b.position);
  const full = seats.length > 0 && seats.every((seat) => seat.status !== 'VACANT');
  const half = Math.ceil(seats.length / 2);
  const top = seats.slice(0, half);
  const bottom = seats.slice(half);
  const label = `${table.businessName ? table.businessName + ' · ' : ''}Table ${table.displayNumber}`;

  const tap = (seat: SeatOnMap) => {
    setNotice(null);
    if (seat.status === 'OCCUPIED') {
      setNotice({ title: 'That chair is taken', message: `Someone is already sitting in seat ${seat.position}. Try another chair.` });
      return;
    }
    setPicked(seat);
  };

  const seated = (email: string) => {
    rememberTable({ slug, n, email: email || undefined });
    void queryClient.invalidateQueries();
    router.replace(`/t/${slug}/table/${n}/home`);
  };

  return (
    <Ground>
      <main className="mx-auto flex w-full max-w-md md:max-w-xl flex-1 animate-rise flex-col gap-2 px-6 pb-7 pt-7">
        <Brand size={24} />
        <Eyebrow className="mt-3">{label}</Eyebrow>
        <h1 className="font-heading text-[36px] font-normal leading-[1.08]">Where are you sitting?</h1>
        <Handwriting className="-rotate-[1.5deg] origin-left">Tap your chair so the food finds you.</Handwriting>
        <SeatLegend />

        <div className="mt-3 flex flex-col items-center gap-3">
          <div className="grid w-full gap-3" style={{ gridTemplateColumns: `repeat(${Math.max(1, Math.min(top.length, 4))}, minmax(0, 1fr))` }}>
            {top.map((seat) => (
              <SeatButton key={seat.seatId} seat={seat} onTap={() => tap(seat)} />
            ))}
          </div>
          <div className="flex w-full flex-col items-center justify-center rounded-[20px] border border-cream/25 bg-cream/10 px-4 py-4 text-center">
            <div className="text-[13px] font-bold uppercase tracking-[0.14em] text-cream">Table {table.displayNumber}</div>
            <div className="text-[15px] text-soft">
              seats {table.Capacity ?? table.capacity ?? seats.length} · the card on your table
            </div>
          </div>
          {bottom.length > 0 ? (
            <div className="grid w-full gap-3" style={{ gridTemplateColumns: `repeat(${Math.max(1, Math.min(bottom.length, 4))}, minmax(0, 1fr))` }}>
              {bottom.map((seat) => (
                <SeatButton key={seat.seatId} seat={seat} onTap={() => tap(seat)} />
              ))}
            </div>
          ) : null}
        </div>

        {notice ? <FailureCard title={notice.title} message={notice.message} /> : null}
        {full ? (
          <p className="rounded-2xl bg-cream/10 p-3.5 text-[15px] leading-snug">Every chair here is taken. A waiter can find you somewhere good.</p>
        ) : null}
      </main>

      {picked ? (
        <ClaimSheet
          key={picked.seatId}
          mode={picked.status === 'HELD' ? 'join' : 'claim'}
          open
          onClose={() => {
            setPicked(null);
            void map.refetch();
          }}
          seatLabel={`${label} · Seat ${picked.position}`}
          seatId={picked.seatId}
          tableNumber={table.tableNumber}
          onSeated={seated}
        />
      ) : null}
    </Ground>
  );
}
