import { initialsOf } from '@/components/staff/StaffHeader';
import type { MySession } from '@/lib/guest';

// Who is sitting here: always on every seated screen, so a guest handing over a phone can see it is theirs.
export default function YouCard({ session }: { session: MySession }) {
  const mine = session.seats.find((seat) => seat.mine);
  const name = mine?.guestName?.trim() || 'You';

  return (
    <div className="sticky top-0 z-20 bg-bottle/95 pb-2 pt-[max(1rem,env(safe-area-inset-top))] backdrop-blur-sm print:hidden">
      <div className="mx-auto w-full max-w-md px-5 md:max-w-xl">
      <section aria-label="You" className="flex items-center gap-3 rounded-2xl bg-cream/10 py-2 pl-2 pr-4">
        <span aria-hidden="true" className="flex h-11 w-11 flex-none items-center justify-center rounded-full bg-mustard font-heading text-lg text-bottle">
          {initialsOf(name)}
        </span>
        <div className="min-w-0 flex-1">
          <div className="text-[12px] font-bold uppercase leading-tight tracking-[0.12em] text-soft">You</div>
          <div className="truncate text-base font-bold leading-tight text-cream">{name}</div>
        </div>
        <div className="flex-none text-right text-[15px] font-bold leading-tight text-cream">
          Table {session.displayNumber}
          {mine ? <div className="text-[13px] font-normal text-soft">Seat {mine.position}</div> : null}
        </div>
      </section>
      </div>
    </div>
  );
}
