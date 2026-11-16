'use client';

import Image from 'next/image';
import { useQuery } from '@tanstack/react-query';
import { managerApi, type TableRow } from '@/lib/manager';

// One printed card: the table's number, its code and a line for the guest. The code never changes, so a lost card is reprinted.
export default function QrCard({ table }: { table: TableRow }) {
  const label = `Table ${table.displayNumber ?? table.tableNumber}`;
  const code = useQuery({
    queryKey: ['qr', table.tableNumber],
    queryFn: async () => URL.createObjectURL(await managerApi.qr(table.tableNumber)),
    staleTime: Infinity,
  });

  return (
    <figure className="flex break-inside-avoid flex-col items-center gap-2 rounded-xl bg-paper p-4 text-center text-bottle shadow-[0_10px_28px_rgba(0,0,0,0.22)] print:border print:border-black print:shadow-none">
      <figcaption className="text-[13px] font-bold uppercase tracking-[0.14em] text-quiet">{label}</figcaption>
      {code.data ? (
        <Image unoptimized src={code.data} alt={`The code to scan at ${label}`} width={200} height={200} className="h-[200px] w-[200px]" />
      ) : (
        <div role="status" className="flex h-[200px] w-[200px] items-center justify-center text-sm text-quiet">
          {code.isError ? 'No code yet' : 'Making the code…'}
        </div>
      )}
      <div className="font-hand text-2xl font-bold leading-[1.05] text-hand">Scan me to order</div>
      {code.data ? (
        <a
          href={code.data}
          download={`table-${table.displayNumber ?? table.tableNumber}.png`}
          className="flex min-h-[44px] items-center rounded-full border-[1.5px] border-bottle px-4 text-[15px] font-bold text-bottle print:hidden"
        >
          Download {label}
        </a>
      ) : null}
    </figure>
  );
}
