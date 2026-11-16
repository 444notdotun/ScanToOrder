'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import BackLink from '@/components/guest/BackLink';
import { Brand, Loader, Watermark } from '@/components/plately/Logo';
import { Dashes, FailureCard } from '@/components/plately/ui';
import { useSession } from '@/lib/guest';
import { apiUrl, dataOf, failureOf, http } from '@/lib/http';
import { naira } from '@/lib/money';
import { happened } from '@/lib/time';

interface Receipt {
  receiptNumber: string;
  paymentReference: string;
  orderId: string;
  tableNumber: string;
  seatLabel: string;
  paymentDate?: string;
  items: Array<{ itemName: string; quantity: number; unitPrice: number; totalPrice: number; specialInstructions?: string }>;
  subtotal: number;
  totalPaid: number;
  status: string;
  refundMessage?: string;
  refundLink?: string;
}

// Where a refund link points inside this app, whatever address the email used.
function insidePath(link: string): string {
  try {
    const url = new URL(link);
    return url.pathname + url.search;
  } catch {
    return link;
  }
}

export default function ReceiptPage() {
  const { slug, n, reference } = useParams<{ slug: string; n: string; reference: string }>();
  const session = useSession().data!;
  const receipt = useQuery({
    queryKey: ['receipt', reference],
    queryFn: async () => dataOf<Receipt>(await http.get(`/api/v1/receipts/${reference}`)),
    retry: false,
  });

  if (receipt.isPending) return <Loader message="Finding your receipt…" />;
  if (receipt.isError) {
    const failure = failureOf(receipt.error, "We can't open that receipt");
    return (
      <main className="mx-auto flex w-full max-w-md md:max-w-xl flex-1 flex-col gap-4 px-5 pt-6">
        <BackLink href={`/t/${slug}/table/${n}/home`} />
        <FailureCard title={failure.title} message={failure.message} />
      </main>
    );
  }

  const r = receipt.data;
  // The receipt carries the seat's code; the guest reads "Seat 2".
  const seat = session.seats.find((candidate) => candidate.seatNumber === r.seatLabel);
  const seatName = seat ? `Seat ${seat.position}` : r.seatLabel;
  const rows: Array<[string, string]> = [
    ['Receipt', r.receiptNumber],
    ['Date', happened(r.paymentDate)],
    ['Table', session.displayNumber ? String(session.displayNumber) : r.tableNumber],
    ['Seat', seatName],
  ];

  return (
    <main className="mx-auto flex w-full max-w-md md:max-w-xl flex-1 animate-rise flex-col gap-4 px-5 pb-8 pt-6">
      <BackLink href={`/t/${slug}/table/${n}/home`} />
      <div className="relative overflow-hidden rounded-lg bg-paperlight p-6 text-ink shadow-[0_18px_36px_rgba(0,0,0,0.3)]">
        <Watermark className="-bottom-10 -right-16" color="#183A2E" opacity={0.06} />
        <div className="relative flex flex-col gap-3">
          <div className="flex flex-col items-center gap-1 text-center">
            <Brand size={26} body="#183A2E" ring="#B93C24" />
            <div className="text-base font-bold">{session.businessName ?? ''}</div>
            <div className="text-xs font-bold uppercase tracking-[0.16em] text-quiet">Receipt</div>
          </div>
          <Dashes />
          <dl className="flex flex-col gap-1">
            {rows.map(([k, v]) => (
              <div key={k} className="flex justify-between gap-3 text-[15px]">
                <dt className="text-quiet">{k}</dt>
                <dd className="text-right font-bold">{v}</dd>
              </div>
            ))}
          </dl>
          <Dashes />
          <ul className="flex flex-col gap-1.5">
            {r.items.map((item, index) => (
              <li key={index} className="flex justify-between gap-3 text-[15px]">
                <span>
                  {item.quantity} × {item.itemName}
                  {item.specialInstructions ? <span className="block text-sm text-quiet">{item.specialInstructions}</span> : null}
                </span>
                <span className="font-bold">{naira(item.totalPrice)}</span>
              </li>
            ))}
          </ul>
          <Dashes />
          <div className="flex justify-between text-[15px]">
            <span>Subtotal</span>
            <span>{naira(r.subtotal)}</span>
          </div>
          <div className="flex justify-between text-lg font-bold">
            <span>Total paid</span>
            <span>{naira(r.totalPaid)}</span>
          </div>
          {r.refundMessage ? (
            <div className="rounded-md border border-dashed border-hand/60 bg-errbg/60 p-3 text-[15px] text-errtext">
              <div className="font-bold">{r.refundMessage}</div>
              {r.refundLink ? (
                <Link href={insidePath(r.refundLink)} className="mt-1 inline-block font-bold underline underline-offset-4">
                  Open your refund page
                </Link>
              ) : null}
            </div>
          ) : null}
          <div className="mt-1 flex items-center justify-between gap-3">
            <div className="font-hand text-[24px] font-bold leading-tight text-hand">Thank you. Come hungry again.</div>
            <div className="-rotate-6 animate-pop rounded-md border-[3px] border-good px-2.5 py-0.5 text-lg font-bold tracking-[0.12em] text-good">PAID</div>
          </div>
        </div>
      </div>
      <a
        href={apiUrl(`/api/v1/receipts/${r.paymentReference}/download`)}
        download={`receipt_${r.paymentReference}.csv`}
        className="flex min-h-[48px] items-center justify-center gap-2 rounded-full border-[1.5px] border-cream/45 px-5 text-base font-bold text-cream"
      >
        <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M12 3v12M7 11l5 5 5-5M5 21h14" />
        </svg>
        Download receipt
      </a>
    </main>
  );
}
