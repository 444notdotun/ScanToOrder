import type { ReactNode } from 'react';

// One line of "Your table": the seat's number in a circle, who is in it, and a note. A saved chair's circle is gold.
export default function SeatRow({ position, held = false, title, note, children }: { position: number; held?: boolean; title: string; note: string; children?: ReactNode }) {
  return (
    <li className="py-3 first:pt-0">
      <div className="flex items-center gap-3">
        <div className={`flex h-10 w-10 flex-none items-center justify-center rounded-full text-base font-bold ${held ? 'border-2 border-mustard bg-mustard/20 text-bottle' : 'bg-bottle text-cream'}`}>{position}</div>
        <div>
          <div className="text-base font-bold text-ink">{title}</div>
          <div className="text-sm text-quiet">{note}</div>
        </div>
      </div>
      {children}
    </li>
  );
}
