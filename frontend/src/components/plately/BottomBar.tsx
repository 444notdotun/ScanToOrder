'use client';

import Link from 'next/link';
import { useEffect, useState, type ReactNode } from 'react';

export interface BarItem {
  key: string;
  label: string;
  icon: ReactNode;
  // A link, or a button that opens a sheet.
  href?: string;
  onSelect?: () => void;
  current?: boolean;
  count?: number;
}

// A cream pill floating above the bottom edge, on phones only. The current item is filled bottle green with cream text,
// and a count sits in a small tomato circle. On a tablet or laptop the same items are the header's section links.
export default function BottomBar({ items, label = 'Main' }: { items: BarItem[]; label?: string }) {
  // While a field is being typed in, the on-screen keyboard takes the bottom of the screen: the bar steps aside so it
  // never sits over the keyboard or covers the button under the field.
  const [typing, setTyping] = useState(false);
  useEffect(() => {
    const isField = (target: EventTarget | null) => target instanceof HTMLElement && /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName);
    const on = (event: FocusEvent) => setTyping(isField(event.target));
    const off = () => setTyping(false);
    document.addEventListener('focusin', on);
    document.addEventListener('focusout', off);
    return () => {
      document.removeEventListener('focusin', on);
      document.removeEventListener('focusout', off);
    };
  }, []);

  return (
    <>
      {/* Keeps the end of every page clear of the bar: its height plus 16px. */}
      <div aria-hidden="true" className="h-[var(--bar-clear)] flex-none md:hidden print:hidden" />
      <nav
        aria-label={label}
        hidden={typing}
        className="fixed inset-x-[14px] bottom-[calc(18px+env(safe-area-inset-bottom))] z-40 flex items-stretch gap-1 rounded-[26px] [&[hidden]]:hidden bg-paper p-1.5 shadow-[0_10px_30px_rgba(0,0,0,0.38)] md:hidden print:hidden"
      >
        {items.map((item) => {
          const inside = (
            <>
              <span className="relative">
                {item.icon}
                {item.count && item.count > 0 ? (
                  <span className="absolute -right-2.5 -top-2 flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-tomato px-1 text-[11px] font-bold leading-none text-white">
                    {item.count > 99 ? '99+' : item.count}
                    <span className="sr-only"> waiting</span>
                  </span>
                ) : null}
              </span>
              <span className="text-[11.5px] font-bold leading-none">{item.label}</span>
            </>
          );
          const style = `flex min-h-[52px] min-w-0 flex-1 flex-col items-center justify-center gap-1 rounded-[20px] px-1 ${
            item.current ? 'bg-bottle text-cream' : 'text-bottle hover:bg-bottle/5'
          }`;
          return item.href ? (
            <Link key={item.key} href={item.href} aria-current={item.current ? 'page' : undefined} className={style}>
              {inside}
            </Link>
          ) : (
            <button key={item.key} type="button" onClick={item.onSelect} className={style}>
              {inside}
            </button>
          );
        })}
      </nav>
    </>
  );
}

// The same items as links in the header, from a tablet up.
export function NavPills({ items, label = 'Sections' }: { items: BarItem[]; label?: string }) {
  return (
    <nav aria-label={label} className="[scrollbar-width:none] overflow-x-auto print:hidden">
      <ul className="flex w-max gap-2 pb-1">
        {items.map((item) => (
          <li key={item.key}>
            {item.href ? (
              <Link
                href={item.href}
                aria-current={item.current ? 'page' : undefined}
                className={`flex min-h-[44px] items-center gap-2 whitespace-nowrap rounded-full border-[1.5px] px-4 text-[15px] font-bold xl:px-3 xl:text-[14px] ${
                  item.current ? 'border-cream bg-cream text-bottle' : 'border-cream/45 text-cream hover:bg-cream/10'
                }`}
              >
                {item.label}
                {item.count && item.count > 0 ? <span className="flex h-[20px] min-w-[20px] items-center justify-center rounded-full bg-tomato px-1 text-xs font-bold text-white">{item.count}</span> : null}
              </Link>
            ) : (
              <button
                type="button"
                onClick={item.onSelect}
                aria-current={item.current ? 'page' : undefined}
                className={`flex min-h-[44px] items-center gap-2 whitespace-nowrap rounded-full border-[1.5px] px-4 text-[15px] font-bold xl:px-3 xl:text-[14px] ${
                  item.current ? 'border-cream bg-cream text-bottle' : 'border-cream/45 text-cream hover:bg-cream/10'
                }`}
              >
                {item.label}
                {item.count && item.count > 0 ? <span className="flex h-[20px] min-w-[20px] items-center justify-center rounded-full bg-tomato px-1 text-xs font-bold text-white">{item.count}</span> : null}
              </button>
            )}
          </li>
        ))}
      </ul>
    </nav>
  );
}
