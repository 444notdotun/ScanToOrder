'use client';

import type { SeatOnMap } from '@/lib/guest';

// One chair. Free chairs are dashed and breathe, saved chairs are gold and sway, taken chairs are paper.
export default function SeatButton({ seat, picked = false, onTap }: { seat: SeatOnMap; picked?: boolean; onTap: () => void }) {
  const free = seat.status === 'VACANT';
  const held = seat.status === 'HELD';
  const label = free ? 'Free' : held ? 'Saved' : 'Taken';
  const sub = free ? 'tap to sit' : held ? 'got a code?' : 'seated';
  const aria = `Seat ${seat.position}, ${free ? 'free' : held ? 'saved for someone with a code' : 'taken'}`;
  const look = free
    ? `border-2 border-dashed ${picked ? 'border-cream bg-cream text-bottle' : 'border-[#9DB5A6] bg-transparent text-cream'} animate-breathe`
    : held
      ? 'border-2 border-mustard bg-mustard/15 text-mustard animate-sway'
      : 'border-2 border-paper bg-paper text-bottle';
  return (
    <button
      type="button"
      onClick={onTap}
      aria-label={aria}
      className={`flex min-h-[84px] w-full flex-col items-center justify-center gap-0.5 rounded-2xl px-2 py-2 ${look}`}
    >
      <span className="text-[11px] font-bold tracking-[0.14em]">SEAT {seat.position}</span>
      <span className="text-[17px] font-bold">{label}</span>
      <span className="text-xs opacity-80">{sub}</span>
    </button>
  );
}
