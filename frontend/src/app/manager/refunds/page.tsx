'use client';

import { useState } from 'react';
import { useConnection } from '@/components/plately/Connection';
import { Button, Card, FailureCard, Field, Sheet } from '@/components/plately/ui';
import PageHead, { Empty } from '@/components/staff/PageHead';
import { FAILURE_ID, useAction } from '@/hooks/useAction';
import { failureOf } from '@/lib/http';
import { managerApi, useRefunds, type ManagerRefund, type RefundState } from '@/lib/manager';
import { naira } from '@/lib/money';
import { happened } from '@/lib/time';

const REASONS: Record<string, string> = {
  KITCHEN_UNAVAILABLE: 'The kitchen could not make it and the guest did not want a swap.',
  LATE_PAYMENT: 'The payment arrived after the guest had left.',
  DOUBLE_PAYMENT: 'The guest paid twice for the same order.',
  AMOUNT_CHANGED: 'The guest paid an amount that no longer matches the order.',
};

const STATUS: Record<string, string> = {
  DUE: 'To settle',
  SETTLED: 'Paid back. Waiting for the guest to confirm',
  CONFIRMED: 'Paid back and confirmed by the guest',
};

const CLOSED_BY: Record<string, string> = { MANAGER: 'you', GUEST: 'the guest', SYSTEM: 'Plately' };

function Line({ name, children }: { name: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-wrap items-baseline gap-x-3">
      <dt className="text-sm text-quiet">{name}</dt>
      <dd className="break-all text-[15px] font-bold text-ink">{children}</dd>
    </div>
  );
}

function RefundCard({ refund, disabled, onSettle }: { refund: ManagerRefund; disabled: boolean; onSettle: (refund: ManagerRefund) => void }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(refund.guestLink ?? '');
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };
  const open = refund.status === 'DUE' || refund.reportedMissing;

  return (
    <Card className={`flex flex-col gap-2 !p-4 ${refund.overdue ? 'border-2 border-tomato' : ''}`}>
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="font-heading text-[22px] font-normal text-bottle">
          {refund.orderNumber} · {naira(refund.amount)}
        </h3>
        <span className="text-sm font-bold text-quiet">{happened(refund.date)}</span>
      </div>
      {refund.items && refund.items.length > 0 ? <div className="font-bold leading-snug">{refund.items.join(', ')}</div> : null}
      <p className="text-[15px] leading-snug text-quiet">{REASONS[refund.reason ?? ''] ?? refund.reason}</p>
      {refund.overdue ? <p className="text-[15px] font-bold text-tomato">This has waited more than 48 hours. Please settle it.</p> : null}
      {refund.reportedMissing ? <p className="text-[15px] font-bold text-tomato">The guest says it has not reached them. Please check and settle again.</p> : null}

      <dl className="flex flex-col gap-1 border-t border-dashed border-rule2 pt-2">
        <Line name="Guest">
          {refund.guestName}
          {refund.tableDisplayNumber != null ? `, Table ${refund.tableDisplayNumber}` : ''}
          {refund.seatPosition != null ? `, Seat ${refund.seatPosition}` : ''}
        </Line>
        {refund.guestPhone ? <Line name="Phone">{refund.guestPhone}</Line> : null}
        {refund.guestEmail ? <Line name="Email">{refund.guestEmail}</Line> : <Line name="Email">Not given yet</Line>}
        {refund.guestAccountNumber ? (
          <Line name="Bank">
            {refund.guestBankName}, {refund.guestAccountName}, {refund.guestAccountNumber}
          </Line>
        ) : (
          <Line name="Bank">Not given. You can pay by cash at the table.</Line>
        )}
        <Line name="Status">{STATUS[refund.status] ?? refund.status}</Line>
        {refund.method ? (
          <Line name="Paid back by">
            {refund.method === 'CASH' ? 'Cash' : 'Transfer'}
            {refund.transferReference ? `, reference ${refund.transferReference}` : ''}
            {refund.settledAt ? `, ${happened(refund.settledAt)}` : ''}
            {refund.closedBy ? `, closed by ${CLOSED_BY[refund.closedBy] ?? refund.closedBy.toLowerCase()}` : ''}
          </Line>
        ) : null}
      </dl>

      <div className="mt-1 flex flex-col gap-2 sm:flex-row">
        {open ? (
          <Button big tone="primary" disabled={disabled} onClick={() => onSettle(refund)}>
            Mark as paid back
          </Button>
        ) : null}
        {refund.guestLink ? (
          <Button big tone="outline" aria-label={`Copy the guest's link for ${refund.orderNumber}`} onClick={() => void copy()}>
            {copied ? 'Link copied' : "Copy the guest's link"}
          </Button>
        ) : null}
      </div>
    </Card>
  );
}

export default function RefundsPage() {
  const { lost } = useConnection();
  const [state, setState] = useState<RefundState>('open');
  const refunds = useRefunds(state);
  const act = useAction();
  const [settling, setSettling] = useState<ManagerRefund | null>(null);
  const [method, setMethod] = useState<'CASH' | 'TRANSFER'>('CASH');
  const [reference, setReference] = useState('');

  const close = () => {
    setSettling(null);
    setReference('');
    act.clear();
  };

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-1 animate-rise flex-col gap-5 px-5 pb-6 pt-4">
      <PageHead title="Refunds" line="When the kitchen cannot make a paid order, you pay the guest back and mark it here." />

      <div role="group" aria-label="Which refunds" className="flex gap-2">
        {(['open', 'closed'] as RefundState[]).map((item) => (
          <button
            key={item}
            type="button"
            aria-pressed={state === item}
            onClick={() => setState(item)}
            className={`min-h-[48px] rounded-full border-[1.5px] px-5 text-base font-bold ${state === item ? 'border-cream bg-cream text-bottle' : 'border-cream/45 text-cream hover:bg-cream/10'}`}
          >
            {item === 'open' ? 'To settle' : 'Closed'}
          </button>
        ))}
      </div>

      <div id={FAILURE_ID}>{act.failure && settling === null ? <FailureCard title={act.failure.title} message={act.failure.message} /> : null}</div>
      {refunds.isError ? <FailureCard title="We couldn't load the refunds" message={failureOf(refunds.error).message} /> : null}
      {refunds.isPending ? (
        <p role="status" className="text-soft">
          Looking them up…
        </p>
      ) : null}
      {refunds.data && refunds.data.length === 0 ? (
        <Empty>
          {state === 'open'
            ? 'Nothing to settle. Refunds only come here when the kitchen cannot make a paid order, or a payment arrives after the guest has left.'
            : 'No closed refunds yet.'}
        </Empty>
      ) : null}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        {(refunds.data ?? []).map((refund) => (
          <RefundCard key={refund.refundId} refund={refund} disabled={lost || act.busy !== null} onSettle={(r) => setSettling(r)} />
        ))}
      </div>

      <Sheet open={settling !== null} onClose={close} label="Mark this refund as paid back">
        <h2 className="font-heading text-[28px] font-normal text-bottle">Paid {settling ? naira(settling.amount) : ''} back?</h2>
        <p className="mb-3 mt-2 text-[15px] leading-snug text-quiet">
          Say how you paid {settling?.guestName ?? 'the guest'}. Plately does not move this money: it stays between you and the guest.
        </p>
        <div role="group" aria-label="How you paid" className="mb-3 flex gap-2">
          {(['CASH', 'TRANSFER'] as const).map((item) => (
            <button
              key={item}
              type="button"
              aria-pressed={method === item}
              onClick={() => setMethod(item)}
              className={`min-h-[48px] flex-1 rounded-full border-[1.5px] px-4 font-bold ${method === item ? 'border-bottle bg-bottle text-cream' : 'border-rule2 text-bottle'}`}
            >
              {item === 'CASH' ? 'Cash' : 'Bank transfer'}
            </button>
          ))}
        </div>
        {method === 'TRANSFER' ? (
          <Field label="The bank's reference" value={reference} onChange={(event) => setReference(event.target.value)} hint="So the payment can be found later." className="mb-3" />
        ) : null}
        {act.failure ? <FailureCard className="mb-3" title={act.failure.title} message={act.failure.message} /> : null}
        <div className="flex flex-col gap-2.5">
          <Button
            big
            tone="primary"
            disabled={lost || act.busy !== null}
            onClick={async () => {
              if (settling && (await act.run(settling.refundId, () => managerApi.settle(settling.refundId, method, reference)))) close();
            }}
          >
            Yes, they have been paid back
          </Button>
          <Button big tone="outline" onClick={close}>
            Not yet
          </Button>
        </div>
      </Sheet>
    </main>
  );
}
