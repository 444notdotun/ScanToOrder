'use client';

import { useConnection } from '@/components/plately/Connection';
import { FailureCard, Switch } from '@/components/plately/ui';
import type { useAction } from '@/hooks/useAction';
import { failureOf } from '@/lib/http';
import { staffApi, useStock } from '@/lib/staff';

type Act = ReturnType<typeof useAction>;

// "What we have left": each plate is a switch between in stock and sold out. Guests' menus follow at once.
export default function StockSection({ act }: { act: Act }) {
  const { lost } = useConnection();
  const stock = useStock();
  const { busy, run } = act;
  const items = [...(stock.data ?? [])].sort((a, b) => Number(a.available) - Number(b.available));

  return (
      <section aria-label="What we have left" className="flex flex-col gap-3 pt-2">
        <div>
          <h2 className="font-heading text-[26px] font-normal">What we have left</h2>
          <p className="text-[15px] text-soft">Sold out plates disappear from guests&apos; menus straight away.</p>
        </div>
        {stock.isError ? <FailureCard title="We couldn't load the stock" message={failureOf(stock.error).message} /> : null}
        {/* One paper card, a plain list: sold-out plates first, then the rest. */}
        <ul className="divide-y divide-rule overflow-hidden rounded-[20px] bg-paper text-ink shadow-[0_10px_28px_rgba(0,0,0,0.22)]">
          {items.map((item) => (
            <li key={item.itemId} className="flex min-h-[56px] items-center gap-3 px-4 py-2.5">
              <span id={`stock-${item.itemId}`} className="min-w-0 flex-1 break-words text-base font-bold leading-snug">
                {item.itemName}
                {item.available ? null : <span className="ml-2 rounded-full bg-errbg px-2.5 py-0.5 align-middle text-xs font-bold text-errtext">Sold out</span>}
              </span>
              <Switch
                checked={item.available}
                labelledBy={`stock-${item.itemId}`}
                disabled={lost || busy !== null}
                onChange={(next) => void run(item.itemId, () => (next ? staffApi.backInStock(item.itemId) : staffApi.soldOut(item.itemId)))}
              />
            </li>
          ))}
          {items.length === 0 && !stock.isError ? <li className="px-4 py-4 text-[15px] text-quiet">No plates on the menu yet.</li> : null}
        </ul>
      </section>
  );
}
