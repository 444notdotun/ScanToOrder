'use client';

import { useState } from 'react';
import { Button, Field, Handwriting } from '@/components/plately/ui';
import { spacedCode } from '@/lib/labels';

const CHOICE = 'flex min-h-[64px] w-full items-center justify-center rounded-2xl border-[1.5px] px-3 py-2 text-center text-[15px] font-bold leading-snug';

// What to do with a chair that is being kept for a friend: who orders and pays decides the next step.
export default function SavedChair({
  joinCode,
  disabled,
  onSeat,
  onLetGo,
}: {
  joinCode: string;
  disabled: boolean;
  onSeat: (name: string) => Promise<void>;
  onLetGo: () => void;
}) {
  const [choice, setChoice] = useState<'phone' | 'me' | null>(null);
  const [name, setName] = useState('');
  const [problem, setProblem] = useState<string | undefined>();

  const pick = (next: 'phone' | 'me') => {
    setChoice(choice === next ? null : next);
    setProblem(undefined);
  };
  const seat = async () => {
    if (!name.trim()) {
      setProblem('What should we call them?');
      return;
    }
    await onSeat(name.trim());
  };

  return (
    <div className="mt-2.5 flex flex-col gap-2.5">
      <div role="group" aria-label="Who orders and pays for this chair?" className="grid grid-cols-1 gap-2 sm:grid-cols-2">
        <button
          type="button"
          aria-pressed={choice === 'phone'}
          onClick={() => pick('phone')}
          className={`${CHOICE} ${choice === 'phone' ? 'border-bottle bg-bottle text-cream' : 'border-rule2 bg-paperlight text-bottle'}`}
        >
          They&apos;ll order and pay on their phone
        </button>
        <button
          type="button"
          aria-pressed={choice === 'me'}
          onClick={() => pick('me')}
          className={`${CHOICE} ${choice === 'me' ? 'border-bottle bg-bottle text-cream' : 'border-rule2 bg-paperlight text-bottle'}`}
        >
          I&apos;ll order and pay for them
        </button>
      </div>

      {choice === 'phone' ? (
        <div className="animate-pop rounded-2xl border-[1.5px] border-dashed border-hand/50 bg-paperlight p-4 text-center">
          <div className="text-sm font-bold text-quiet">Give your friend this code.</div>
          <div className="my-1 font-heading text-[40px] tracking-[0.08em] text-bottle">{spacedCode(joinCode)}</div>
          <Handwriting onPaper className="!text-[20px]">
            They scan the table, tap this chair and type it in.
          </Handwriting>
        </div>
      ) : null}

      {choice === 'me' ? (
        <form
          className="flex animate-pop flex-col gap-3 rounded-2xl bg-paperlight p-4"
          onSubmit={(event) => {
            event.preventDefault();
            void seat();
          }}
        >
          <Field label="Their name" value={name} onChange={(event) => setName(event.target.value)} error={problem} placeholder="Mama Ada" autoComplete="off" />
          <Button type="submit" tone="primary" disabled={disabled}>
            Seat them and order for them
          </Button>
        </form>
      ) : null}

      <Button tone="outline" disabled={disabled} onClick={onLetGo}>
        Let this chair go
      </Button>
    </div>
  );
}
