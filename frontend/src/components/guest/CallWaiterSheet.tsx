'use client';

import { useState } from 'react';
import { useConnection } from '@/components/plately/Connection';
import { Button, FailureCard, Field, Sheet } from '@/components/plately/ui';
import { failureOf, http, type ApiFailure } from '@/lib/http';
import type { MySession } from '@/lib/guest';

const REASONS = ['Someone to come over', 'Water, please', 'Cutlery or napkins', 'Something is wrong with my order'];

// Calls a waiter to this seat. The waiter sees the table, the seat and the guest's name with what was asked.
export default function CallWaiterSheet({ open, onClose, session }: { open: boolean; onClose: () => void; session: MySession }) {
  const { lost } = useConnection();
  const [reason, setReason] = useState(REASONS[0]);
  const [more, setMore] = useState('');
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);
  const [failure, setFailure] = useState<ApiFailure | null>(null);
  const mine = session.seats.find((seat) => seat.mine);

  const close = () => {
    setSent(false);
    setFailure(null);
    onClose();
  };

  const call = async () => {
    setBusy(true);
    setFailure(null);
    const who = [mine?.guestName?.split(/\s+/)[0], mine ? `seat ${mine.position}` : null].filter(Boolean).join(', ');
    try {
      await http.post('/api/v1/service-calls', {
        requestType: 'ASSISTANCE',
        note: `${who ? `${who}: ` : ''}${reason}${more.trim() ? `. ${more.trim()}` : ''}`,
      });
      setSent(true);
      setMore('');
    } catch (error) {
      setFailure(failureOf(error, "We couldn't call a waiter"));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Sheet open={open} onClose={close} label="Call a waiter">
      {sent ? (
        <div className="flex animate-pop flex-col gap-3">
          <h2 className="font-heading text-[28px] font-normal text-bottle">A waiter is on the way.</h2>
          <p className="text-[15px] leading-snug text-quiet">They know it is Table {session.displayNumber}{mine ? `, Seat ${mine.position}` : ''}. You can keep ordering while you wait.</p>
          <Button big tone="bottle" onClick={close}>
            Done
          </Button>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          <h2 className="font-heading text-[28px] font-normal text-bottle">Call a waiter</h2>
          <p className="text-[15px] leading-snug text-quiet">
            To Table {session.displayNumber}
            {mine ? `, Seat ${mine.position}` : ''}. What do you need?
          </p>
          <div className="flex flex-col gap-2">
            {REASONS.map((line) => (
              <button
                key={line}
                type="button"
                aria-pressed={reason === line}
                onClick={() => setReason(line)}
                className={`min-h-[48px] rounded-2xl border-[1.5px] px-4 py-2 text-left text-[15px] font-bold ${reason === line ? 'border-bottle bg-bottle text-cream' : 'border-rule2 text-bottle'}`}
              >
                {line}
              </button>
            ))}
          </div>
          <Field label="Anything to add? (optional)" value={more} onChange={(e) => setMore(e.target.value)} maxLength={120} />
          {failure ? <FailureCard title={failure.title} message={failure.message} /> : null}
          <Button big tone="primary" disabled={busy || lost} onClick={() => void call()}>
            {busy ? 'Calling…' : 'Call a waiter'}
          </Button>
          <Button big tone="outline" onClick={close}>
            Never mind
          </Button>
        </div>
      )}
    </Sheet>
  );
}
