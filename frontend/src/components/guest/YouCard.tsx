import { initialsOf } from '@/components/staff/StaffHeader';
import type { MySession } from '@/lib/guest';

// Who is sitting here, pinned at the top of every seated screen as a slim bar: the seat first, then the name and the place.
export default function YouCard({ session }: { session: MySession }) {
  const mine = session.seats.find((seat) => seat.mine);
  const name = mine?.guestName?.trim() || 'You';
  const place = session.businessName;

  return (
    <div className="sticky top-0 z-30 flex min-h-[var(--you-h)] items-end bg-bottle/95 pb-2 backdrop-blur-sm print:hidden">
      <div className="mx-auto w-full max-w-md px-5 md:max-w-xl">
        <section aria-label="You" className="flex items-center gap-3 rounded-2xl bg-cream/10 py-1.5 pl-1.5 pr-4">
          <span aria-hidden="true" className="flex h-9 w-9 flex-none items-center justify-center rounded-full bg-mustard font-heading text-[15px] text-bottle">
            {initialsOf(name)}
          </span>
          <div className="min-w-0 flex-1 leading-tight">
            <div className="text-[15px] font-bold text-cream">
              Table {session.displayNumber}
              {mine ? ` · Seat ${mine.position}` : ''}
            </div>
            <div className="truncate text-[13px] text-soft">
              {name}
              {place ? ` · ${place}` : ''}
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}
