'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useFloor } from '@/lib/manager';

const TABS = [
  { href: '/manager', label: 'Floor now' },
  { href: '/manager/insights', label: 'Insights' },
  { href: '/manager/menu', label: 'Menu' },
  { href: '/manager/tables', label: 'Tables' },
  { href: '/manager/team', label: 'Team' },
  { href: '/manager/refunds', label: 'Refunds' },
  { href: '/manager/settings', label: 'Settings' },
];

// The manager's sections. Refunds carry a count while any are waiting.
export default function ManagerNav() {
  const path = usePathname();
  const floor = useFloor();
  const open = floor.data?.openRefunds ?? 0;

  return (
    <nav aria-label="Manager sections" className="mx-auto w-full max-w-6xl overflow-x-auto px-5 pt-3 print:hidden">
      <ul className="flex w-max gap-2 pb-1">
        {TABS.map((tab) => {
          const here = tab.href === '/manager' ? path === '/manager' : path.startsWith(tab.href);
          return (
            <li key={tab.href}>
              <Link
                href={tab.href}
                aria-current={here ? 'page' : undefined}
                className={`flex min-h-[48px] items-center rounded-full border-[1.5px] px-5 text-base font-bold ${
                  here ? 'border-cream bg-cream text-bottle' : 'border-cream/45 text-cream hover:bg-cream/10'
                }`}
              >
                {tab.label}
                {tab.href === '/manager/refunds' && open > 0 ? ` (${open})` : ''}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
