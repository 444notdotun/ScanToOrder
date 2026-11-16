'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import type { ReactNode } from 'react';
import StaffShell from '@/components/staff/StaffShell';
import { useTenants } from '@/lib/platform';

const TABS = [
  { href: '/platform', label: 'Approvals' },
  { href: '/platform/tenants', label: 'Tenants' },
  { href: '/platform/refunds', label: 'Refunds to chase' },
  { href: '/platform/earnings', label: 'Earnings' },
];

function Nav() {
  const path = usePathname();
  const waiting = useTenants('pending_approval').data?.length ?? 0;
  return (
    <nav aria-label="Platform sections" className="mx-auto w-full max-w-6xl overflow-x-auto px-5 pt-3">
      <ul className="flex w-max gap-2 pb-1">
        {TABS.map((tab) => {
          const here = tab.href === '/platform' ? path === '/platform' : path.startsWith(tab.href);
          return (
            <li key={tab.href}>
              <Link
                href={tab.href}
                aria-current={here ? 'page' : undefined}
                className={`flex min-h-[48px] items-center rounded-full border-[1.5px] px-5 text-base font-bold ${here ? 'border-cream bg-cream text-bottle' : 'border-cream/45 text-cream hover:bg-cream/10'}`}
              >
                {tab.label}
                {tab.href === '/platform' && waiting > 0 ? ` (${waiting})` : ''}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}

export default function PlatformLayout({ children }: { children: ReactNode }) {
  return (
    <StaffShell allow={['PLATFORM_ADMIN']}>
      <Nav />
      {children}
    </StaffShell>
  );
}
