'use client';

import Link from 'next/link';
import { useEffect, useRef, useState } from 'react';
import { Brand } from '@/components/plately/Logo';
import { useStickyHeader } from '@/hooks/useStickyHeader';

const LINKS = [
  { href: '#how-it-works', label: 'How it works' },
  { href: '#restaurants', label: 'Restaurants' },
  { href: '#events', label: 'Events' },
];

const STAFF = 'flex min-h-[44px] items-center justify-center rounded-full border-[1.5px] border-cream/45 px-5 text-sm font-bold text-cream hover:bg-cream/10';
const CTA = 'flex min-h-[44px] items-center justify-center rounded-full bg-tomato px-5 text-sm font-bold text-white hover:bg-[#a23420]';

// The top bar, pinned while the page scrolls. At the very top it sits clear on the page; once scrolled it takes the deep green and a thin gold
// line. It clears the phone's status bar. On a phone the three links fold into a menu.
export default function TopBar() {
  const [open, setOpen] = useState(false);
  const bar = useRef<HTMLElement>(null);
  const scrolled = useStickyHeader(bar);

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open]);

  return (
    <header
      ref={bar}
      className={`sticky top-0 z-40 -mt-[env(safe-area-inset-top)] w-full border-b pt-[env(safe-area-inset-top)] transition-colors duration-200 ${
        scrolled ? 'border-[#F0B94A] bg-[#10281F]' : 'border-transparent'
      }`}
    >
      <div className="mx-auto w-full max-w-6xl px-5 pb-3 pt-5">
      <div className="flex items-center gap-4">
        <Brand />
        <nav aria-label="Sections" className="ml-auto hidden items-center gap-6 md:flex">
          {LINKS.map((link) => (
            <a key={link.href} href={link.href} className="text-[15px] font-bold text-soft hover:text-cream">
              {link.label}
            </a>
          ))}
          <Link href="/staff/login" className={STAFF}>
            Staff sign in
          </Link>
          <Link href="/signup" className={CTA}>
            Get Plately for your place
          </Link>
        </nav>
        <button
          type="button"
          aria-expanded={open}
          aria-controls="mk-menu"
          onClick={() => setOpen((value) => !value)}
          className="ml-auto flex min-h-[44px] items-center gap-2 rounded-full border-[1.5px] border-cream/45 px-4 text-sm font-bold text-cream md:hidden"
        >
          <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" focusable="false">
            {open ? <path d="M6 6l12 12M18 6L6 18" /> : <path d="M4 7h16M4 12h16M4 17h16" />}
          </svg>
          {open ? 'Close' : 'Menu'}
        </button>
      </div>
      {open ? (
        <nav id="mk-menu" aria-label="Sections" className="mt-3 flex max-h-[calc(100dvh-5.5rem-env(safe-area-inset-top))] animate-rise flex-col gap-1 overflow-y-auto rounded-2xl bg-paper p-3 text-bottle md:hidden">
          {LINKS.map((link) => (
            <a key={link.href} href={link.href} onClick={() => setOpen(false)} className="flex min-h-[48px] items-center rounded-xl px-3 text-base font-bold hover:bg-rule">
              {link.label}
            </a>
          ))}
          <Link href="/staff/login" className="flex min-h-[48px] items-center rounded-xl px-3 text-base font-bold hover:bg-rule">
            Staff sign in
          </Link>
          <Link href="/signup" className={`${CTA} mt-1`}>
            Get Plately for your place
          </Link>
        </nav>
      ) : null}
      </div>
    </header>
  );
}
