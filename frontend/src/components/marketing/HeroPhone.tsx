import { Handwriting } from '@/components/plately/ui';
import { naira } from '@/lib/money';

// A made-up menu, marked SAMPLE: three dishes that add up to the button.
const PLATES = [
  { name: 'Jollof rice & grilled chicken', price: 4500 },
  { name: 'Pounded yam & egusi', price: 5200 },
  { name: 'Chapman', price: 1800 },
];
const TOTAL = PLATES.reduce((sum, plate) => sum + plate.price, 0);
// Seat 1 is taken (dashed), seat 2 is the one being ordered for.
const SEATS = [
  { position: 1, look: 'border-[1.5px] border-dashed border-rule2 text-quiet' },
  { position: 2, look: 'border-[1.5px] border-bottle bg-bottle text-cream' },
  { position: 3, look: 'border-[1.5px] border-bottle text-bottle' },
  { position: 4, look: 'border-[1.5px] border-bottle text-bottle' },
];

// The guest's screen in a phone, tilted a little. It is for looking at, not tapping: it is a picture made of the page's own type, colours and money format.
export default function HeroPhone() {
  return (
    <figure className="relative mx-auto w-full max-w-[300px] lg:ml-auto lg:mr-6">
      <div aria-hidden="true" className="rotate-[2deg] rounded-[34px] border-[6px] border-[#10281F] bg-paper p-5 text-ink shadow-[0_30px_60px_rgba(0,0,0,0.45)]">
        <div className="flex items-center justify-between">
          <span className="text-[10px] font-bold uppercase tracking-[0.14em] text-quiet">Table 7</span>
          <span className="rounded border border-dashed border-hand px-1.5 py-px text-[9px] font-bold tracking-[0.12em] text-hand">SAMPLE</span>
        </div>
        <h3 className="mt-1 font-heading text-[23px] font-normal leading-[1.1] text-bottle">Where are you sitting?</h3>
        <div className="mt-3 grid grid-cols-4 gap-2">
          {SEATS.map((seat) => (
            <span key={seat.position} className={`flex h-9 items-center justify-center rounded-lg text-[13px] font-bold ${seat.look}`}>
              {seat.position}
            </span>
          ))}
        </div>
        <div className="mt-4 text-[10px] font-bold uppercase tracking-[0.14em] text-quiet">Your plates · Seat 2</div>
        <ul className="mt-1">
          {PLATES.map((plate) => (
            <li key={plate.name} className="flex justify-between gap-3 border-b border-dashed border-rule2 py-2 text-[13px] leading-snug">
              <span className="min-w-0">{plate.name}</span>
              <span className="flex-none font-bold">{naira(plate.price)}</span>
            </li>
          ))}
        </ul>
        <div className="mt-4 flex min-h-[44px] items-center justify-center rounded-full bg-tomato px-4 text-[14px] font-bold text-white">Send to the kitchen · {naira(TOTAL)}</div>
        <p className="mt-2 text-center text-[11px] leading-snug text-quiet">Pay for your seat only. Others pay for theirs.</p>
      </div>
      <figcaption className="mt-3 text-right">
        <Handwriting className="!text-[20px] -rotate-[3deg]">sample menu, not real data</Handwriting>
      </figcaption>
    </figure>
  );
}
