'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import type { ReactNode } from 'react';
import StaffShell from '@/components/staff/StaffShell';

function Nav() {
  const path = usePathname();
  return (
    <nav aria-label="Planner sections">
      <Link
        href="/planner"
        aria-current={path === '/planner' ? 'page' : undefined}
        className={`inline-flex min-h-[44px] items-center rounded-full border-[1.5px] px-4 text-[15px] font-bold ${
          path === '/planner' ? 'border-cream bg-cream text-bottle' : 'border-cream/45 text-cream hover:bg-cream/10'
        }`}
      >
        Your events
      </Link>
    </nav>
  );
}

export default function PlannerLayout({ children }: { children: ReactNode }) {
  return (
    <StaffShell allow={['MANAGER']} workspace="EVENT_PLANNER" nav={<Nav />}>
      {children}
    </StaffShell>
  );
}
