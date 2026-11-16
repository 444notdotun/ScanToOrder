'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useState } from 'react';
import BottomBar, { NavPills, type BarItem } from '@/components/plately/BottomBar';
import { icons } from '@/components/plately/Icons';
import { Button, Sheet } from '@/components/plately/ui';
import StaffHeaderCard from '@/components/staff/StaffHeaderCard';
import { useFloor } from '@/lib/manager';
import { useCalls, useReady, useVisits } from '@/lib/staff';
import { useAuthStore } from '@/store/authStore';
import { useWaiterTab, type WaiterTab } from '@/store/waiterTab';

// ---- the waiter: To carry, Visits, Calls, Tables. Counts come from the lists the screen already loads.

export function useWaiterItems(): BarItem[] {
  const { tab, setTab } = useWaiterTab();
  const carry = useReady().data?.length ?? 0;
  const visits = useVisits().data?.length ?? 0;
  const calls = (useCalls().data ?? []).filter((call) => call.state === 'OPEN').length;
  const item = (key: WaiterTab, label: string, icon: BarItem['icon'], count?: number): BarItem => ({ key, label, icon, count, current: tab === key, onSelect: () => setTab(key) });
  return [item('carry', 'To carry', icons.carry, carry), item('visits', 'Visits', icons.visit, visits), item('calls', 'Calls', icons.bell, calls), item('tables', 'Tables', icons.table)];
}

export function WaiterNav() {
  return <NavPills items={useWaiterItems()} label="Waiter sections" />;
}

export function WaiterBar() {
  return <BottomBar items={useWaiterItems()} label="Waiter sections" />;
}

// ---- the manager: Floor, Menu, Insights, Refunds, More (Tables, Team, Settings).

export function ManagerBar() {
  const path = usePathname();
  const open = useFloor().data?.openRefunds ?? 0;
  const [more, setMore] = useState(false);
  const inMore = ['/manager/tables', '/manager/team', '/manager/settings'].some((prefix) => path.startsWith(prefix));
  const items: BarItem[] = [
    { key: 'floor', label: 'Floor', icon: icons.floor, href: '/manager', current: path === '/manager' },
    { key: 'menu', label: 'Menu', icon: icons.menu, href: '/manager/menu', current: path.startsWith('/manager/menu') },
    { key: 'insights', label: 'Insights', icon: icons.chart, href: '/manager/insights', current: path.startsWith('/manager/insights') },
    { key: 'refunds', label: 'Refunds', icon: icons.tag, href: '/manager/refunds', current: path.startsWith('/manager/refunds'), count: open },
    { key: 'more', label: 'More', icon: icons.more, onSelect: () => setMore(true), current: inMore },
  ];
  return (
    <>
      <BottomBar items={items} label="Manager sections" />
      <Sheet open={more} onClose={() => setMore(false)} label="More">
        <h2 className="mb-3 font-heading text-[28px] font-normal text-bottle">More</h2>
        <ul className="flex flex-col gap-2">
          {[
            ['/manager/tables', 'Tables', 'Add tables and print their cards'],
            ['/manager/team', 'Team', 'Chefs and waiters'],
            ['/manager/settings', 'Settings', 'Open for orders, saving seats, payout account'],
          ].map(([href, label, line]) => (
            <li key={href}>
              <Link href={href} onClick={() => setMore(false)} className="flex min-h-[56px] flex-col justify-center rounded-2xl border-[1.5px] border-rule2 px-4 text-bottle">
                <span className="text-base font-bold">{label}</span>
                <span className="text-sm text-quiet">{line}</span>
              </Link>
            </li>
          ))}
        </ul>
      </Sheet>
    </>
  );
}

// ---- the planner: Events, Guests, Coverage, Account. Guests and Coverage open the current event's sections.

const LAST_EVENT = 'plately-last-event';

function currentEventId(path: string): string | null {
  const here = /^\/planner\/([^/]+)/.exec(path)?.[1];
  if (here && here !== 'new' && here !== 'payment') {
    try {
      window.sessionStorage.setItem(LAST_EVENT, here);
    } catch {
      // Without storage the last event is simply not remembered.
    }
    return here;
  }
  try {
    return window.sessionStorage.getItem(LAST_EVENT);
  } catch {
    return null;
  }
}

export function usePlannerItems(account?: () => void): BarItem[] {
  const path = usePathname();
  const id = currentEventId(path);
  const base = id ? `/planner/${id}` : '/planner';
  const inEvent = Boolean(id) && path.startsWith(`/planner/${id}`);
  const items: BarItem[] = [
    { key: 'events', label: 'Events', icon: icons.calendar, href: '/planner', current: path === '/planner' || path === '/planner/new' },
    { key: 'guests', label: 'Guests', icon: icons.guests, href: base, current: inEvent && !path.includes('/coverage') },
    { key: 'coverage', label: 'Coverage', icon: icons.coverage, href: id ? `${base}/coverage` : '/planner', current: inEvent && path.includes('/coverage') },
  ];
  if (account) items.push({ key: 'account', label: 'Account', icon: icons.account, onSelect: account });
  return items;
}

export function PlannerNav() {
  return <NavPills items={usePlannerItems()} label="Planner sections" />;
}

export function PlannerBar() {
  const [open, setOpen] = useState(false);
  const items = usePlannerItems(() => setOpen(true));
  return (
    <>
      <BottomBar items={items} label="Planner sections" />
      <AccountSheet open={open} onClose={() => setOpen(false)} />
    </>
  );
}

// Who is signed in, and the way out, in one place.
function AccountSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const router = useRouter();
  const profile = useAuthStore((state) => state.workerProfile);
  const logout = useAuthStore((state) => state.logout);
  return (
    <Sheet open={open} onClose={onClose} label="Account">
      <h2 className="mb-3 font-heading text-[28px] font-normal text-bottle">Your account</h2>
      {profile ? <StaffHeaderCard profile={profile} /> : null}
      <Button
        className="mt-4"
        big
        tone="bottle"
        onClick={() => {
          logout();
          router.replace('/staff/login');
        }}
      >
        Sign out
      </Button>
    </Sheet>
  );
}
