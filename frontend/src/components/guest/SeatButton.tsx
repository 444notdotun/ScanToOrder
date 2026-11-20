'use client';

import type { SeatOnMap } from '@/lib/guest';

// One chair. Free chairs are dashed and breathe, saved chairs are gold and sway, taken chairs are paper.
// The three keys, small, at the top of the seat map: they look like the tiles they explain.
export function SeatLegend() {
  const key = 'h-4 w-4 flex-none rounded-[5px]';
  return (
    <ul aria-label="What the chairs mean" className="flex flex-wrap items-center gap-x-4 gap-y-1.5 text-[14px] font-bold text-cream">
      <li className="flex items-center gap-1.5">
        <span aria-hidden="true" className={`${key} border-2 border-dashed border-[#9DB5A6]`} />
        Free
      </li>
      <li className="flex items-center gap-1.5">
        <span aria-hidden="true" className={`${key} bg-paper`} />
        Taken
      </li>
      <li className="flex items-center gap-1.5">
        <span aria-hidden="true" className={`${key} border-2 border-mustard bg-mustard/30`} />
        Saved for a friend
      </li>
    </ul>
  );
}

export default function SeatButton({ seat, picked = false, onTap }: { seat: SeatOnMap; picked?: boolean; onTap: () => void }) {
  const free = seat.status === 'VACANT';
  const held = seat.status === 'HELD';
  const label = free ? 'Free' : held ? 'Saved' : 'Taken';
  const sub = free ? 'tap to sit here' : held ? 'for a friend. Got their code? Tap' : 'someone is sitting here';
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
      className={`flex min-h-[116px] w-full flex-col items-center justify-center gap-1 rounded-2xl px-2 py-3 text-center ${look}`}
    >
      <span className="text-[13px] font-bold tracking-[0.14em]">SEAT {seat.position}</span>
      <span className="text-[20px] font-bold leading-tight">{label}</span>
      <span className="text-[13px] leading-tight opacity-90">{sub}</span>
    </button>
  );
}
