'use client';

import { useId, useState } from 'react';
import { useConnection } from '@/components/plately/Connection';
import { FailureCard, Switch } from '@/components/plately/ui';
import type { useAction } from '@/hooks/useAction';
import { failureOf } from '@/lib/http';
import { staffApi, useStock } from '@/lib/staff';

type Act = ReturnType<typeof useAction>;

// "What we have left": a narrow panel beside the orders (below them on a phone). Sold-out plates come first and are one tap to bring
// back; the rest are behind "Show all", or found by typing. Guests' menus follow at once.
export default function StockSection({ act }: { act: Act }) {
  const { lost } = useConnection();
  const stock = useStock();
  const { busy, run } = act;
  const searchId = useId();
  const [query, setQuery] = useState('');
  const [all, setAll] = useState(false);

  const items = [...(stock.data ?? [])].sort((a, b) => Number(a.available) - Number(b.available) || a.itemName.localeCompare(b.itemName));
  const soldOut = items.filter((item) => !item.available);
  const searching = query.trim().length > 0;
  const shown = searching ? items.filter((item) => item.itemName.toLowerCase().includes(query.trim().toLowerCase())) : all ? items : soldOut;

  return (
    <section aria-label="What we have left" className="flex flex-col gap-3 rounded-[20px] bg-paper p-4 text-ink shadow-[0_10px_28px_rgba(0,0,0,0.22)]">
      <div>
        <h2 className="font-heading text-[24px] font-normal leading-tight text-bottle">What we have left</h2>
        <p className="text-sm leading-snug text-quiet">Sold out plates leave guests&apos; menus straight away.</p>
      </div>
      {stock.isError ? <FailureCard title="We couldn't load the stock" message={failureOf(stock.error).message} /> : null}
      <div>
        <label htmlFor={searchId} className="sr-only">
          Find a plate
        </label>
        <input
          id={searchId}
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Find a plate"
          autoComplete="off"
          className="h-11 w-full rounded-xl border-[1.5px] border-rule2 bg-paperlight px-3 text-[16px] text-ink placeholder:text-quiet/70 focus:border-bottle"
        />
      </div>
      <ul className="divide-y divide-rule">
        {shown.map((item) => (
          <li key={item.itemId} className="flex min-h-[52px] items-center gap-3 py-2">
            <span id={`stock-${item.itemId}`} className="min-w-0 flex-1 break-words text-[15px] font-bold leading-snug">
              {item.itemName}
              {item.available ? null : <span className="ml-2 rounded-full bg-errbg px-2 py-0.5 align-middle text-xs font-bold text-errtext">Sold out</span>}
            </span>
            <Switch
              checked={item.available}
              labelledBy={`stock-${item.itemId}`}
              disabled={lost || busy !== null}
              onChange={(next) => void run(item.itemId, () => (next ? staffApi.backInStock(item.itemId) : staffApi.soldOut(item.itemId)))}
            />
          </li>
        ))}
        {shown.length === 0 && !stock.isError ? (
          <li className="py-3 text-[15px] text-quiet">
            {items.length === 0 ? 'No plates on the menu yet.' : searching ? 'No plate has that name.' : 'Nothing is sold out.'}
          </li>
        ) : null}
      </ul>
      {!searching && items.length > 0 ? (
        <button
          type="button"
          aria-expanded={all}
          onClick={() => setAll(!all)}
          className="min-h-[44px] rounded-full border-[1.5px] border-bottle px-4 text-[15px] font-bold text-bottle hover:bg-bottle/5"
        >
          {all ? 'Show only sold out' : `Show all ${items.length}`}
        </button>
      ) : null}
    </section>
  );
}
