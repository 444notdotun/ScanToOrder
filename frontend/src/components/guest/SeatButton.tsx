'use client';

import type { SeatOnMap } from '@/lib/guest';

// One chair. Free chairs are dashed, saved chairs are gold, taken chairs are paper.
// The three keys, small, at the top of the seat map: they look like the tiles they explain.
export function SeatLegend() {
  const key = 'h-3 w-3 flex-none rounded-[3px]';
  return (
    <ul aria-label="What the chairs mean" className="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1.5 text-[14px] text-cream">
      <li className="flex items-center gap-1.5">
        <span aria-hidden="true" className={`${key} border border-[#9DB5A6]`} />
        free
      </li>
      <li className="flex items-center gap-1.5">
        <span aria-hidden="true" className={`${key} bg-paper`} />
        taken
      </li>
      <li className="flex items-center gap-1.5 text-mustard">
        <span aria-hidden="true" className={`${key} bg-mustard`} />
        saved for a friend
      </li>
    </ul>
  );
}

export default function SeatButton({ seat, picked = false, compact = false, onTap }: { seat: SeatOnMap; picked?: boolean; compact?: boolean; onTap: () => void }) {
  const free = seat.status === 'VACANT';
  const held = seat.status === 'HELD';
  const label = free ? 'Free' : held ? 'Saved' : 'Taken';
  const aria = `Seat ${seat.position}, ${free ? 'free' : held ? 'saved for someone with a code' : 'taken'}`;
  const look = free
    ? `border-2 border-dashed ${picked ? 'border-cream bg-cream text-bottle' : 'border-[#9DB5A6] bg-transparent text-cream'}`
    : held
      ? 'border-2 border-mustard bg-[#3F4A25] text-mustard'
      : 'border-2 border-paper bg-paper text-bottle';
  return (
    <button
      type="button"
      onClick={onTap}
      aria-label={aria}
      className={`flex ${compact ? 'min-h-[60px]' : 'min-h-[92px]'} w-full flex-col items-center justify-center gap-0.5 rounded-[18px] px-2 py-3 text-center ${look}`}
    >
      <span className="text-[12px] font-bold tracking-[0.14em]">SEAT {seat.position}</span>
      <span className={`font-heading ${compact ? 'text-[16px]' : 'text-[24px]'} font-normal leading-tight`}>{label}</span>
    </button>
  );
}
