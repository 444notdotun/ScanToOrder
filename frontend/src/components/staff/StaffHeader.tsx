'use client';

import { useRef, type ReactNode } from 'react';
import { Brand } from '@/components/plately/Logo';
import { useStickyHeader } from '@/hooks/useStickyHeader';
import type { WorkerProfile } from '@/store/authStore';

export function roleLabel(profile: Pick<WorkerProfile, 'role' | 'tenantType'>): string {
  if (profile.role === 'CHEF') return 'Chef';
  if (profile.role === 'WAITER') return 'Waiter';
  if (profile.role === 'PLATFORM_ADMIN') return 'Platform owner';
  return profile.tenantType === 'EVENT_PLANNER' ? 'Planner' : 'Manager';
}

// "Kemi Balogun" is KB; a username alone gives its first two letters.
export function initialsOf(name: string): string {
  const words = name.trim().split(/[\s._-]+/).filter(Boolean);
  const letters = words.length > 1 ? words[0][0] + words[1][0] : (words[0] ?? '?').slice(0, 2);
  return letters.toUpperCase();
}

function Door() {
  return (
    <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" focusable="false">
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
      <path d="M16 17l5-5-5-5" />
      <path d="M21 12H9" />
    </svg>
  );
}

// The one header for every signed-in screen. Who is using it is always on show, and Sign out is never hidden.
// On a laptop it is one bar (logo, section links, name card, Sign out); on a phone the name card drops to its own row.
export default function StaffHeader({
  profile,
  ordersToday,
  nav,
  onSignOut,
}: {
  profile: WorkerProfile;
  ordersToday?: number;
  nav?: ReactNode;
  onSignOut: () => void;
}) {
  const bar = useRef<HTMLElement>(null);
  const scrolled = useStickyHeader(bar);
  const name = profile.fullName?.trim() || profile.username;
  const busy = profile.role === 'CHEF' || profile.role === 'WAITER';
  const where = profile.role === 'PLATFORM_ADMIN' ? 'Plately' : (profile.businessName ?? 'Plately');

  return (
    <header
      ref={bar}
      className={`sticky top-0 z-40 -mt-[env(safe-area-inset-top)] w-full border-b pt-[env(safe-area-inset-top)] transition-colors duration-200 print:hidden ${
        scrolled ? 'border-[#F0B94A] bg-[#10281F]' : 'border-transparent'
      }`}
    >
    <div className="mx-auto flex w-full max-w-6xl flex-wrap xl:max-w-[1360px] items-center gap-x-4 gap-y-3 xl:gap-x-3 px-5 pb-2 pt-5 xl:flex-nowrap">
      <div className="order-1 flex-none xl:mr-2">
        <Brand size={28} />
      </div>

      <button
        type="button"
        onClick={onSignOut}
        className={`order-2 ml-auto flex flex-none items-center gap-2 whitespace-nowrap rounded-full bg-cream px-5 text-sm font-bold text-bottle shadow-[0_4px_12px_rgba(0,0,0,0.25)] hover:bg-white xl:order-4 xl:ml-0 ${
          busy ? 'min-h-[52px]' : 'min-h-[44px]'
        }`}
      >
        <Door />
        Sign out
      </button>

      <div className="order-3 flex basis-full items-center gap-3 rounded-2xl bg-cream/10 py-2 pl-2 pr-4 xl:ml-auto xl:max-w-[340px] xl:flex-none xl:basis-auto">
        <span aria-hidden="true" className="flex h-11 w-11 flex-none items-center justify-center rounded-full bg-mustard font-heading text-lg text-bottle">
          {initialsOf(name)}
        </span>
        <div className="min-w-0 flex-1">
          <div className="break-words text-base font-bold leading-tight text-cream">{name}</div>
          <div className="break-words text-[13px] leading-tight text-soft">
            {roleLabel(profile)} · {where}
          </div>
        </div>
        {busy && ordersToday !== undefined ? (
          <span className="flex-none rounded-full bg-paper px-3 py-1 text-[13px] font-bold text-bottle">Orders today {ordersToday}</span>
        ) : null}
      </div>

      {nav ? <div className="order-4 hidden basis-full md:block xl:order-2 xl:min-w-0 xl:basis-auto xl:flex-1">{nav}</div> : null}
    </div>
    </header>
  );
}
