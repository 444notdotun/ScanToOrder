'use client';

import { useState } from 'react';
import { Button, Field } from '@/components/plately/ui';
import { spacedCode } from '@/lib/labels';

const CHOICE = 'flex min-h-[52px] w-full items-center justify-between gap-2 rounded-full border-2 px-5 py-2 text-left text-[15px] font-bold leading-snug';
// Chosen: filled ink with a tick. Not chosen: an outline. Neither ever looks switched off.
const CHOSEN = 'border-ink bg-ink text-paperlight';
const OUTLINED = 'border-ink bg-transparent text-ink hover:bg-ink/5';

function Tick() {
  return (
    <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" className="flex-none" focusable="false">
      <path d="M5 12.5l4.5 4.5L19 7.5" />
    </svg>
  );
}

// What to do with a chair that is being kept for a friend: who orders and pays decides the next step.
export default function SavedChair({
  joinCode,
  link,
  disabled,
  onSeat,
  onLetGo,
}: {
  joinCode: string;
  // Where the friend goes to scan: the table's own page.
  link?: string;
  disabled: boolean;
  onSeat: (name: string) => Promise<void>;
  onLetGo: () => void;
}) {
  const [choice, setChoice] = useState<'phone' | 'me' | null>(null);
  const [name, setName] = useState('');
  const [problem, setProblem] = useState<string | undefined>();
  const [copied, setCopied] = useState(false);

  const plain = joinCode.replace(/\s+/g, '');
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(plain);
    } catch {
      // An older browser: select the text the old way.
      const box = document.createElement('textarea');
      box.value = plain;
      document.body.appendChild(box);
      box.select();
      document.execCommand('copy');
      box.remove();
    }
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };
  const share = async () => {
    // A browser with no share sheet gets the code copied instead.
    if (typeof navigator.share !== 'function') {
      await copy();
      return;
    }
    try {
      await navigator.share({ title: 'A seat at my table', text: `I saved you a seat. Scan the table, tap the chair and type this code: ${spacedCode(joinCode)}`, url: link });
    } catch {
      // The person closed the share sheet. Nothing to do.
    }
  };

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
      <div role="group" aria-label="Who orders and pays for this chair?" className="flex flex-col gap-2">
        <button
          type="button"
          aria-pressed={choice === 'phone'}
          onClick={() => pick('phone')}
          className={`${CHOICE} ${choice === 'phone' ? CHOSEN : OUTLINED}`}
        >
          They&apos;ll order and pay on their phone
          {choice === 'phone' ? <Tick /> : null}
        </button>
        <button
          type="button"
          aria-pressed={choice === 'me'}
          onClick={() => pick('me')}
          className={`${CHOICE} ${choice === 'me' ? CHOSEN : OUTLINED}`}
        >
          I&apos;ll order and pay for them
          {choice === 'me' ? <Tick /> : null}
        </button>
      </div>

      {choice === 'phone' ? (
        <div className="animate-pop rounded-[20px] border-2 border-dashed border-rule2 px-3 pb-4 pt-4 text-center">
          <div className="text-[12px] font-bold uppercase tracking-[0.14em] text-quiet">Give your friend this code</div>
          <div aria-label={`Join code ${plain.split('').join(' ')}`} className="my-2 font-body text-[44px] font-bold leading-none tracking-[0.16em] text-ink">
            {spacedCode(joinCode)}
          </div>
          <div className="grid grid-cols-2 gap-2">
            <button type="button" onClick={() => void copy()} className="min-h-[48px] rounded-full border-[1.5px] border-ink px-4 text-[15px] font-bold text-ink hover:bg-ink/5">
              {copied ? 'Copied ✓' : 'Copy code'}
            </button>
            <button type="button" onClick={() => void share()} className="min-h-[48px] rounded-full bg-tomato px-4 text-[15px] font-bold text-white hover:bg-[#a23420]">
              Share
            </button>
          </div>
          <p className="mt-3 text-[14px] leading-snug text-quiet">They scan the table, tap this chair and type it in.</p>
          <span role="status" className="sr-only">
            {copied ? 'Code copied' : ''}
          </span>
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
