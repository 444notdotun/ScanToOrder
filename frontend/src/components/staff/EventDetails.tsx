'use client';

import { useState, type FormEvent } from 'react';
import { Button, Field } from '@/components/plately/ui';
import type { EventForm, PlannerEvent, SeatingMode } from '@/lib/planner';

const MODES: Array<{ id: SeatingMode; label: string }> = [
  { id: 'FREE', label: 'Sit anywhere' },
  { id: 'ASSIGNED', label: 'Assigned seats' },
];

// What the event is, where and when, how guests sit and how many plates each may have.
export default function EventDetails({
  initial,
  submitLabel,
  busy,
  onSubmit,
}: {
  initial?: PlannerEvent;
  submitLabel: string;
  busy: boolean;
  onSubmit: (form: EventForm) => Promise<boolean>;
}) {
  const [name, setName] = useState(initial?.name ?? '');
  const [venue, setVenue] = useState(initial?.venue ?? '');
  const [startsAt, setStartsAt] = useState(initial?.startsAt?.slice(0, 16) ?? '');
  const [endsAt, setEndsAt] = useState(initial?.endsAt?.slice(0, 16) ?? '');
  const [mode, setMode] = useState<SeatingMode>(initial?.seatingMode ?? 'FREE');
  const [limit, setLimit] = useState<number | null>(initial ? (initial.platesPerGuest ?? null) : 2);
  const [expected, setExpected] = useState(initial ? String(initial.expectedGuests) : '');
  const [errors, setErrors] = useState<Record<string, string>>({});

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const found: Record<string, string> = {};
    if (!name.trim()) found.name = 'What is the event called?';
    if (!startsAt) found.startsAt = 'When do the doors open?';
    if (!endsAt) found.endsAt = 'When does it end?';
    else if (startsAt && endsAt <= startsAt) found.endsAt = 'It has to end after it starts.';
    const guests = Number(expected);
    if (!Number.isInteger(guests) || guests < 1) found.expected = 'How many guests do you expect?';
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    await onSubmit({ name, venue, startsAt, endsAt, seatingMode: mode, platesPerGuest: limit, expectedGuests: guests });
  };

  return (
    <form noValidate onSubmit={(event) => void submit(event)} className="flex flex-col gap-3.5">
      <Field label="Name of the event" value={name} onChange={(event) => setName(event.target.value)} error={errors.name} placeholder="Ada and Tobi, the wedding" />
      <Field label="Venue" value={venue} onChange={(event) => setVenue(event.target.value)} placeholder="The Garden Hall, Ikeja" />
      <div className="grid grid-cols-1 gap-3.5 sm:grid-cols-2">
        <Field label="Doors open" type="datetime-local" value={startsAt} onChange={(event) => setStartsAt(event.target.value)} error={errors.startsAt} />
        <Field label="It ends" type="datetime-local" value={endsAt} onChange={(event) => setEndsAt(event.target.value)} error={errors.endsAt} />
      </div>
      <Field
        label="Guests you expect"
        inputMode="numeric"
        value={expected}
        onChange={(event) => setExpected(event.target.value.replace(/\D/g, ''))}
        error={errors.expected}
        placeholder="200"
        hint="This sets the price. You can pay for more later."
      />
      <div>
        <div id="seating-label" className="mb-1.5 text-sm font-bold text-bottle">
          Seating
        </div>
        <div role="group" aria-labelledby="seating-label" className="flex gap-2">
          {MODES.map((item) => (
            <button
              key={item.id}
              type="button"
              aria-pressed={mode === item.id}
              onClick={() => setMode(item.id)}
              className={`min-h-[48px] flex-1 rounded-full border-[1.5px] px-4 font-bold ${mode === item.id ? 'border-bottle bg-bottle text-cream' : 'border-rule2 text-bottle'}`}
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>
      <div>
        <div id="limit-label" className="mb-1.5 text-sm font-bold text-bottle">
          Plates per guest
        </div>
        <div role="group" aria-labelledby="limit-label" className="flex items-center gap-3">
          <button
            type="button"
            aria-label="One plate fewer"
            disabled={limit === 1}
            onClick={() => setLimit(limit === null ? 9 : Math.max(1, limit - 1))}
            className="h-12 w-12 rounded-full border-[1.5px] border-bottle text-2xl font-bold text-bottle disabled:opacity-40"
          >
            −
          </button>
          <div aria-live="polite" className="min-w-[6.5rem] text-center text-lg font-bold text-ink">
            {limit === null ? 'No limit' : `${limit} ${limit === 1 ? 'plate' : 'plates'}`}
          </div>
          <button
            type="button"
            aria-label="One plate more"
            disabled={limit === null}
            onClick={() => setLimit(limit !== null && limit >= 9 ? null : (limit ?? 0) + 1)}
            className="h-12 w-12 rounded-full bg-bottle text-2xl font-bold text-cream disabled:opacity-40"
          >
            +
          </button>
        </div>
      </div>
      <Button type="submit" tone="primary" disabled={busy}>
        {submitLabel}
      </Button>
    </form>
  );
}
