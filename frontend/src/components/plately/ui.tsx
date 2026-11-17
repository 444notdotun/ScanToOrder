'use client';

import { useEffect, useId, useRef, useState, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from 'react';

type Tone = 'primary' | 'bottle' | 'outline' | 'light' | 'ghost';

const TONES: Record<Tone, string> = {
  // The one tomato primary per screen.
  primary: 'bg-tomato text-white hover:bg-[#a23420]',
  bottle: 'bg-bottle text-cream hover:bg-[#10281f]',
  outline: 'border-[1.5px] border-bottle bg-transparent text-bottle hover:bg-bottle/5',
  // On the green ground.
  light: 'bg-cream text-bottle hover:bg-white',
  ghost: 'border-[1.5px] border-cream/45 bg-transparent text-cream hover:bg-cream/10',
};

export function Button({
  tone = 'bottle',
  big = false,
  full = true,
  className = '',
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { tone?: Tone; big?: boolean; full?: boolean }) {
  // Pill shaped, at least 44px tall; 52px where hands are busy (kitchen and waiter).
  return (
    <button
      type="button"
      {...rest}
      className={`${full ? 'w-full' : ''} ${big ? 'min-h-[52px]' : 'min-h-[44px]'} rounded-full px-5 py-2.5 text-base font-bold leading-tight ${TONES[tone]} ${className}`}
    />
  );
}

// Paper, 20px radius, a soft shadow.
export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={`rounded-[20px] bg-paper p-5 text-ink shadow-[0_10px_28px_rgba(0,0,0,0.22)] ${className}`}>{children}</div>;
}

// Dashed divider for receipts and tickets.
export function Dashes({ className = '' }: { className?: string }) {
  return <div aria-hidden="true" className={`border-t border-dashed border-rule2 ${className}`} />;
}

export function Handwriting({ children, className = '', onPaper = false }: { children: ReactNode; className?: string; onPaper?: boolean }) {
  return <p className={`font-hand text-[25px] font-bold leading-[1.15] ${onPaper ? 'text-hand' : 'text-mustard'} ${className}`}>{children}</p>;
}

export function Field({
  label,
  error,
  hint,
  className = '',
  ...rest
}: InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string; hint?: string }) {
  const id = useId();
  return (
    <div className={`flex flex-col gap-1.5 ${className}`}>
      <label htmlFor={id} className="text-sm font-bold text-bottle">
        {label}
      </label>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined}
        {...rest}
        className="h-[50px] w-full rounded-xl border-[1.5px] border-rule2 bg-paperlight px-3.5 text-[17px] text-ink placeholder:text-quiet/70 focus:border-bottle aria-invalid:border-hand aria-invalid:border-2"
      />
      {error ? (
        <span id={`${id}-error`} className="text-sm font-bold text-hand">
          {error}
        </span>
      ) : hint ? (
        <span id={`${id}-hint`} className="text-sm text-quiet">
          {hint}
        </span>
      ) : null}
    </div>
  );
}

// A list to choose from, in the same dress as a Field.
export function SelectField({
  label,
  error,
  hint,
  className = '',
  children,
  ...rest
}: SelectHTMLAttributes<HTMLSelectElement> & { label: string; error?: string; hint?: string }) {
  const id = useId();
  return (
    <div className={`flex flex-col gap-1.5 ${className}`}>
      <label htmlFor={id} className="text-sm font-bold text-bottle">
        {label}
      </label>
      <select
        id={id}
        aria-invalid={error ? true : undefined}
        {...rest}
        className="h-[50px] w-full rounded-xl border-[1.5px] border-rule2 bg-paperlight px-3 text-[17px] text-ink focus:border-bottle"
      >
        {children}
      </select>
      {error ? <span className="text-sm font-bold text-hand">{error}</span> : hint ? <span className="text-sm text-quiet">{hint}</span> : null}
    </div>
  );
}

// "Saving a chair for a friend?", stock, and "Open for orders".
export function Switch({ checked, onChange, labelledBy, disabled }: { checked: boolean; onChange: (next: boolean) => void; labelledBy: string; disabled?: boolean }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-labelledby={labelledBy}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={`relative h-8 w-14 flex-none rounded-full before:absolute before:-inset-x-2 before:-inset-y-3 before:content-[''] ${checked ? 'bg-bottle' : 'bg-[#A99E84]'}`}
    >
      <span className={`absolute top-1 h-6 w-6 rounded-full bg-paperlight shadow transition-all ${checked ? 'left-[28px]' : 'left-1'}`} />
      <span className="sr-only">{checked ? 'On' : 'Off'}</span>
    </button>
  );
}

// A title, a plain sentence on what to do next, and a small shake on arrival.
export function FailureCard({ title, message, className = '' }: { title?: string; message: string; className?: string }) {
  const card = useRef<HTMLDivElement>(null);
  // A card that arrives below the fold is brought into view. On a phone it is kept clear of the floating bottom bar.
  useEffect(() => {
    // After the card has taken its place, so what is measured is where it really is.
    const bring = () => card.current?.scrollIntoView({ block: 'nearest' });
    const frame = requestAnimationFrame(bring);
    // Once more when the page has settled: a form that was still re-drawing can move the card a little.
    const settled = setTimeout(bring, 300);
    return () => {
      cancelAnimationFrame(frame);
      clearTimeout(settled);
    };
  }, [title, message]);
  return (
    <div ref={card} role="alert" className={`flex scroll-mb-28 scroll-mt-4 animate-shake md:scroll-mb-4 gap-3 rounded-2xl border-[1.5px] border-errborder bg-errbg p-3.5 text-errtext ${className}`}>
      <svg aria-hidden="true" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="mt-0.5 flex-none">
        <circle cx="12" cy="12" r="10" />
        <path d="M12 7v6M12 17h.01" />
      </svg>
      <div className="text-[15px] leading-snug">
        {title ? <div className="mb-0.5 font-bold">{title}</div> : null}
        <div>{message}</div>
      </div>
    </div>
  );
}

// Slides up from the bottom. Escape or a tap outside closes it.
export function Sheet({ open, onClose, children, label }: { open: boolean; onClose: () => void; children: ReactNode; label: string }) {
  const panel = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const previous = document.activeElement as HTMLElement | null;
    panel.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('keydown', onKey);
      previous?.focus?.();
    };
  }, [open, onClose]);
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/55 animate-fade sm:items-center" onClick={onClose}>
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-label={label}
        tabIndex={-1}
        onClick={(event) => event.stopPropagation()}
        className="max-h-[92dvh] w-full max-w-md animate-sheet outline-none overflow-y-auto rounded-t-[28px] bg-paper p-6 pb-[calc(1.5rem+env(safe-area-inset-bottom))] text-ink shadow-2xl sm:rounded-[28px] sm:pb-6"
      >
        {children}
      </div>
    </div>
  );
}

export function Eyebrow({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={`text-[13px] font-bold uppercase tracking-[0.12em] text-soft ${className}`}>{children}</div>;
}

// A "…" button with a short list under it, for the things that should not sit next to everyday buttons.
export function MoreMenu({ label, items }: { label: string; items: Array<{ label: string; onSelect: () => void; danger?: boolean }> }) {
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const away = (event: MouseEvent) => {
      if (!box.current?.contains(event.target as Node)) setOpen(false);
    };
    const key = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', away);
    document.addEventListener('keydown', key);
    return () => {
      document.removeEventListener('mousedown', away);
      document.removeEventListener('keydown', key);
    };
  }, [open]);

  return (
    <div ref={box} className="relative">
      <button
        type="button"
        aria-label={label}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen(!open)}
        className="flex h-11 w-11 items-center justify-center rounded-full border-[1.5px] border-bottle text-xl font-bold leading-none text-bottle hover:bg-bottle/5"
      >
        <span aria-hidden="true">…</span>
      </button>
      {open ? (
        <div role="menu" className="absolute right-0 top-full z-30 mt-1.5 min-w-[200px] rounded-2xl bg-paper p-1.5 shadow-[0_12px_30px_rgba(0,0,0,0.3)]">
          {items.map((item) => (
            <button
              key={item.label}
              type="button"
              role="menuitem"
              onClick={() => {
                setOpen(false);
                item.onSelect();
              }}
              className={`flex min-h-[44px] w-full items-center rounded-xl px-3 text-left text-[15px] font-bold hover:bg-bottle/5 ${item.danger ? 'text-hand' : 'text-bottle'}`}
            >
              {item.label}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}
