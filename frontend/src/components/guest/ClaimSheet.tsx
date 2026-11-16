'use client';

import { useState } from 'react';
import { Button, Field, FailureCard, Sheet } from '@/components/plately/ui';
import { failureOf, http, type ApiFailure } from '@/lib/http';

export interface Entered {
  name: string;
  phone: string;
  email: string;
}

const PHONE = /^\d{11}$/;

function problemWith(entered: Entered, needCode: boolean, code: string): string | null {
  if (!entered.name.trim()) return 'Tell us your name so the waiter knows who to look for.';
  if (!PHONE.test(entered.phone.trim())) return 'Your phone number needs 11 digits, like 08012345678.';
  if (entered.email.trim() && !/^\S+@\S+\.\S+$/.test(entered.email.trim())) return 'That email does not look right. Leave it empty if you would rather not.';
  if (needCode && !code.trim()) return 'Type the six letters your friend has on their phone.';
  return null;
}

// "Lovely. Who's sitting here?" and "Someone saved this for you?": the same form, with the code on the second.
export default function ClaimSheet({
  mode,
  open,
  onClose,
  seatLabel,
  seatId,
  tableNumber,
  onSeated,
}: {
  mode: 'claim' | 'join';
  open: boolean;
  onClose: () => void;
  seatLabel: string;
  seatId: string;
  tableNumber: string;
  onSeated: (email: string) => void;
}) {
  const [entered, setEntered] = useState<Entered>({ name: '', phone: '', email: '' });
  const [code, setCode] = useState('');
  const [failure, setFailure] = useState<ApiFailure | { title?: string; message: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const joining = mode === 'join';

  const submit = async () => {
    const problem = problemWith(entered, joining, code);
    if (problem) {
      setFailure({ message: problem });
      return;
    }
    setBusy(true);
    setFailure(null);
    try {
      const person = {
        customerName: entered.name.trim(),
        customerPhoneNumber: entered.phone.trim(),
        customerEmail: entered.email.trim() || undefined,
      };
      if (joining) {
        await http.post(`/api/v1/seats/${seatId}/join`, { ...person, joinCode: code.trim().replace(/\s+/g, '').toUpperCase() });
      } else {
        await http.post('/api/v1/seats/claim', { ...person, seatId, tableId: tableNumber });
      }
      onSeated(entered.email.trim());
    } catch (error) {
      const failed = failureOf(error, joining ? "That didn't work" : 'Someone got there first');
      setFailure(joining && failed.status === 403 ? { ...failed, title: "That's not the code" } : failed);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Sheet open={open} onClose={onClose} label={joining ? 'Join the table' : 'Take this seat'}>
      <div className="text-xs font-bold uppercase tracking-[0.14em] text-quiet">{seatLabel}</div>
      <h2 className="mb-1 mt-1 font-heading text-[28px] font-normal leading-tight text-bottle">
        {joining ? 'Someone saved this for you?' : "Lovely. Who's sitting here?"}
      </h2>
      {joining ? <p className="mb-3 text-[15px] text-quiet">Ask your friend for the six letters on their phone.</p> : null}
      <form
            noValidate
        className="flex flex-col gap-3"
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        {joining ? (
          <Field label="Their code" value={code} onChange={(e) => setCode(e.target.value)} placeholder="K7M 2QD" autoComplete="off" autoCapitalize="characters" />
        ) : null}
        <Field label="Your name" value={entered.name} onChange={(e) => setEntered({ ...entered, name: e.target.value })} autoComplete="off" />
        <Field
          label="Phone number"
          type="tel"
          inputMode="numeric"
          value={entered.phone}
          onChange={(e) => setEntered({ ...entered, phone: e.target.value })}
          autoComplete="off"
          hint="Only so we can send your receipt. We won't call."
        />
        <Field
          label="Email (if you'd like a receipt there)"
          type="email"
          value={entered.email}
          onChange={(e) => setEntered({ ...entered, email: e.target.value })}
          autoComplete="off"
        />
        {failure ? <FailureCard title={failure.title} message={failure.message} /> : null}
        <Button type="submit" tone="primary" big disabled={busy}>
          {busy ? 'One moment…' : joining ? 'Join the table' : 'Take this seat'}
        </Button>
        <Button tone="outline" onClick={onClose}>
          Pick a different chair
        </Button>
      </form>
    </Sheet>
  );
}
