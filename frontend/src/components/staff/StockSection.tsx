'use client';

import { useConnection } from '@/components/plately/Connection';
import { FailureCard } from '@/components/plately/ui';
import type { useAction } from '@/hooks/useAction';
import { failureOf } from '@/lib/http';
import { staffApi, useStock } from '@/lib/staff';

type Act = ReturnType<typeof useAction>;

// "What we have left": each plate is a switch between in stock and sold out. Guests' menus follow at once.
export default function StockSection({ act }: { act: Act }) {
  const { lost } = useConnection();
  const stock = useStock();
  const { busy, run } = act;

  return (
      <section aria-label="What we have left" className="flex flex-col gap-3 pt-2">
        <div>
          <h2 className="font-heading text-[26px] font-normal">What we have left</h2>
          <p className="text-[15px] text-soft">Sold out plates disappear from guests&apos; menus straight away.</p>
        </div>
        {stock.isError ? <FailureCard title="We couldn't load the stock" message={failureOf(stock.error).message} /> : null}
        <div className="flex flex-wrap gap-2.5">
          {(stock.data ?? []).map((item) => (
            <button
              key={item.itemId}
              type="button"
              aria-pressed={!item.available}
              disabled={lost || busy !== null}
              onClick={() => void run(item.itemId, () => (item.available ? staffApi.soldOut(item.itemId) : staffApi.backInStock(item.itemId)))}
              className={`min-h-[52px] rounded-full border-[1.5px] px-5 text-base font-bold ${
                item.available ? 'border-cream/45 bg-transparent text-cream hover:bg-cream/10' : 'border-cream bg-cream text-bottle'
              } disabled:opacity-60`}
            >
              {item.itemName} · {item.available ? 'in stock' : 'sold out'}
            </button>
          ))}
        </div>
      </section>
  );
}
