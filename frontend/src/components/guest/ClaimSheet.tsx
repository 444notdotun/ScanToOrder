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

// One plain sentence per field that needs fixing, each shown directly under its own field.
type Problems = Partial<Record<'code' | 'name' | 'phone' | 'email', string>>;

function problemsWith(entered: Entered, needCode: boolean, code: string): Problems {
  const found: Problems = {};
  if (needCode && !code.trim()) found.code = 'Type the six letters your friend has on their phone.';
  if (!entered.name.trim()) found.name = 'Tell us your name so the waiter knows who to look for.';
  if (!PHONE.test(entered.phone.trim())) found.phone = 'Enter your 11-digit phone number, like 08012345678.';
  if (entered.email.trim() && !/^\S+@\S+\.\S+$/.test(entered.email.trim())) found.email = "That email doesn't look right. Leave it empty if you'd rather not.";
  return found;
}

// The server names its own fields; each lands under the field it belongs to.
const SERVER_FIELD: Record<string, keyof Problems> = {
  joinCode: 'code',
  customerName: 'name',
  customerPhoneNumber: 'phone',
  customerEmail: 'email',
};

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
  const [problems, setProblems] = useState<Problems>({});
  const [busy, setBusy] = useState(false);
  const joining = mode === 'join';

  const submit = async () => {
    const found = problemsWith(entered, joining, code);
    setProblems(found);
    if (Object.keys(found).length > 0) {
      setFailure(null);
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
      const named: Problems = {};
      for (const [field, sentence] of Object.entries(failed.fieldErrors ?? {})) {
        if (SERVER_FIELD[field]) named[SERVER_FIELD[field]] = sentence;
      }
      if (Object.keys(named).length > 0) {
        setProblems(named);
        setFailure(null);
      } else {
        setFailure(joining && failed.status === 403 ? { ...failed, title: "That's not the code" } : failed);
      }
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
          <Field
            label="Their code"
            need="needed"
            value={code}
            onChange={(e) => {
              setCode(e.target.value);
              setProblems({ ...problems, code: undefined });
            }}
            error={problems.code}
            placeholder="K7M 2QD"
            autoComplete="off"
            autoCapitalize="characters"
          />
        ) : null}
        <Field
          label="Your name"
          need="needed"
          name="name"
          value={entered.name}
          onChange={(e) => {
            setEntered({ ...entered, name: e.target.value });
            setProblems({ ...problems, name: undefined });
          }}
          error={problems.name}
          autoComplete="off"
        />
        <Field
          label="Phone"
          need="needed"
          name="phone"
          type="tel"
          inputMode="numeric"
          value={entered.phone}
          onChange={(e) => {
            setEntered({ ...entered, phone: e.target.value });
            setProblems({ ...problems, phone: undefined });
          }}
          error={problems.phone}
          autoComplete="off"
          hint="Only for your receipt. We won't call."
        />
        <Field
          label="Email"
          need="optional"
          name="email"
          type="email"
          value={entered.email}
          onChange={(e) => {
            setEntered({ ...entered, email: e.target.value });
            setProblems({ ...problems, email: undefined });
          }}
          error={problems.email}
          autoComplete="off"
        />
        {failure ? <FailureCard title={failure.title} message={failure.message} /> : null}
        {/* The buttons stay at the bottom of the sheet while the fields scroll, so the main one is never out of reach. */}
        <div className="sticky bottom-0 -mx-6 -mb-1 flex flex-col gap-2.5 bg-paper px-6 pb-2 pt-3 shadow-[0_-10px_12px_-10px_rgba(0,0,0,0.25)]">
          <Button type="submit" tone="primary" big disabled={busy}>
            {busy ? 'One moment…' : joining ? 'Join the table' : 'Take this seat'}
          </Button>
          <Button tone="outline" onClick={onClose}>
            Pick a different chair
          </Button>
        </div>
      </form>
    </Sheet>
  );
}
