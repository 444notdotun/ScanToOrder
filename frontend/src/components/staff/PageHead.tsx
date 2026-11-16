import type { ReactNode } from 'react';

// The top of a manager page: a heading and one line in the voice of the place.
export default function PageHead({ title, line, children }: { title: string; line: string; children?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-3 print:hidden">
      <div>
        <h1 className="font-heading text-[38px] font-normal leading-[1.1]">{title}</h1>
        <p className="mt-1 text-[17px] text-soft">{line}</p>
      </div>
      {children}
    </div>
  );
}

export function Tile({ label, value, sub, large = false }: { label: string; value: string; sub?: string; large?: boolean }) {
  return (
    <div className="rounded-[20px] bg-paper p-4 text-ink shadow-[0_10px_28px_rgba(0,0,0,0.22)]">
      <div className="text-[13px] font-bold uppercase tracking-[0.1em] text-quiet">{label}</div>
      <div className={`mt-1 font-heading leading-none text-bottle ${large ? 'text-[52px] md:text-[34px]' : 'text-[34px]'}`}>{value}</div>
      {sub ? <div className="mt-1.5 text-sm text-quiet">{sub}</div> : null}
    </div>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <p className="rounded-lg border border-dashed border-cream/30 p-4 text-[15px] leading-snug text-soft">{children}</p>;
}
