'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useConnection } from '@/components/plately/Connection';
import { Button, FailureCard, Sheet } from '@/components/plately/ui';
import type { useAction } from '@/hooks/useAction';
import { staffApi, type FloorTable } from '@/lib/staff';

type Act = ReturnType<typeof useAction>;

// Closing a table frees every seat. If paid food has not been carried over, the sheet says so first.
export default function CloseTableSheet({ table, act, onDone }: { table: FloorTable | null; act: Act; onDone: () => void }) {
  const { lost } = useConnection();
  const [owedNow, setOwedNow] = useState<number | null>(null);
  // How many paid orders are still undelivered, read when the sheet opens.
  const sessions = useQuery({
    queryKey: ['table-sessions', table?.tableId],
    queryFn: () => staffApi.sessions(table!.tableId),
    enabled: table !== null,
  });
  const owed = owedNow ?? sessions.data?.paidUndeliveredOrders ?? 0;
  const label = table ? `Table ${table.displayNumber ?? table.tableNumber}` : '';

  const close = async () => {
    if (!table) return;
    const worked = await act.run(table.tableId, () => staffApi.closeTable(table.tableId, owed > 0));
    if (worked) {
      setOwedNow(null);
      onDone();
    } else {
      // A new paid order may have come in since the warning was read: ask again before closing.
      const fresh = await staffApi.sessions(table.tableId).catch(() => null);
      if (fresh) setOwedNow(fresh.paidUndeliveredOrders);
    }
  };

  return (
    <Sheet open={table !== null} onClose={onDone} label="Close the table">
      <h2 className="font-heading text-[28px] font-normal text-bottle">Close {label}?</h2>
      <p className="mb-3 mt-2 text-[15px] leading-snug text-quiet">
        Every seat at {label} will be freed and its groups closed. Guests who are still ordering will be told their visit has ended.
      </p>
      {owed > 0 ? (
        <div role="alert" className="mb-3 rounded-2xl border-[1.5px] border-errborder bg-errbg p-3.5 text-[15px] font-bold leading-snug text-errtext">
          {owed} paid order{owed === 1 ? ' has' : 's have'} not been delivered. The food stays with you and nothing is refunded.
        </div>
      ) : null}
      {act.failure ? <FailureCard className="mb-3" title={act.failure.title} message={act.failure.message} /> : null}
      <div className="flex flex-col gap-2.5">
        <Button big tone="primary" disabled={lost || act.busy !== null || sessions.isPending} onClick={() => void close()}>
          Close the table
        </Button>
        <Button big tone="outline" onClick={onDone}>
          Not yet
        </Button>
      </div>
    </Sheet>
  );
}
