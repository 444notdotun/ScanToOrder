'use client';

import SeatButton from '@/components/guest/SeatButton';
import { Brand } from '@/components/plately/Logo';
import { Button, Handwriting } from '@/components/plately/ui';
import { naira } from '@/lib/money';
import type { SeatOnMap } from '@/lib/guest';

// A made-up menu, marked SAMPLE: three dishes that add up to the button.
const PLATES = [
  { name: 'Jollof rice & grilled chicken', price: 4500 },
  { name: 'Pounded yam & egusi', price: 5200 },
  { name: 'Chapman', price: 1800 },
];
const TOTAL = PLATES.reduce((sum, plate) => sum + plate.price, 0);
const SEATS: SeatOnMap[] = [1, 2, 3, 4].map((position) => ({ seatId: `demo-${position}`, seatNumber: String(position), position, status: 'VACANT' }));

// The guest's screen in a phone frame, from the same seat tiles, button and money format the real screens use. It is for looking at:
// nothing inside it can be tapped or focused.
export default function HeroPhone() {
  return (
    <figure className="mx-auto w-full max-w-[320px]">
      <div className="rounded-[44px] border-[10px] border-[#10281F] bg-bottle p-4 shadow-[0_30px_60px_rgba(0,0,0,0.45)]">
        <div inert className="flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <Brand size={18} />
            <span className="rounded-full border border-mustard px-2.5 py-0.5 text-[11px] font-bold tracking-[0.12em] text-mustard">SAMPLE</span>
          </div>
          <div>
            <div className="text-[12px] font-bold uppercase tracking-[0.12em] text-soft">Table 7</div>
            <h3 className="font-heading text-[24px] font-normal leading-[1.1]">Where are you sitting?</h3>
          </div>
          <div className="grid grid-cols-4 gap-2">
            {SEATS.map((seat) => (
              <SeatButton key={seat.seatId} seat={seat} compact picked={seat.position === 2} onTap={() => undefined} />
            ))}
          </div>
          <div className="rounded-[20px] bg-paper p-4 text-ink">
            <h4 className="mb-2 font-heading text-[18px] font-normal text-bottle">Your plates · Seat 2</h4>
            <ul className="flex flex-col gap-1.5">
              {PLATES.map((plate) => (
                <li key={plate.name} className="flex justify-between gap-3 text-[14px] leading-snug">
                  <span>{plate.name}</span>
                  <span className="font-bold">{naira(plate.price)}</span>
                </li>
              ))}
            </ul>
          </div>
          <Button tone="primary" big tabIndex={-1}>
            Send to the kitchen · {naira(TOTAL)}
          </Button>
        </div>
      </div>
      <figcaption className="mt-3 text-center">
        <Handwriting className="!text-[22px]">sample menu, not real data</Handwriting>
      </figcaption>
    </figure>
  );
}
