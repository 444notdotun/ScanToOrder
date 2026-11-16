'use client';

import { useParams } from 'next/navigation';
import { useState, type FormEvent } from 'react';
import { useConnection } from '@/components/plately/Connection';
import { Button, Card, FailureCard, Field, MoreMenu, Sheet, Switch } from '@/components/plately/ui';
import { Empty } from '@/components/staff/PageHead';
import { FAILURE_ID, useAction } from '@/hooks/useAction';
import { failureOf } from '@/lib/http';
import { plannerApi, useEventPlates, type EventPlate } from '@/lib/planner';

export default function PlatesPage() {
  const { id } = useParams<{ id: string }>();
  const { lost } = useConnection();
  const plates = useEventPlates(id);
  const act = useAction();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [problem, setProblem] = useState<string | null>(null);
  const [removing, setRemoving] = useState<EventPlate | null>(null);

  const add = async (e: FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setProblem('Describe the plate, for example: Jollof rice, chicken and Coke.');
      return;
    }
    setProblem(null);
    if (await act.run('add', () => plannerApi.addPlate(id, name, description))) {
      setName('');
      setDescription('');
    }
  };

  return (
    <div className="flex flex-col gap-5">
      <p className="text-[17px] text-soft">The plates this event serves. One order is one whole plate: guests tap one and it goes straight to the kitchen.</p>
      <div id={FAILURE_ID}>{act.failure && removing === null ? <FailureCard title={act.failure.title} message={act.failure.message} /> : null}</div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_2fr]">
        <Card className="h-fit">
          <h2 className="mb-3 font-heading text-[22px] font-normal text-bottle">Add a plate</h2>
          <form noValidate onSubmit={(e) => void add(e)} className="flex flex-col gap-3.5">
            <Field label="The plate" value={name} onChange={(e) => setName(e.target.value)} error={problem ?? undefined} placeholder="Jollof rice, chicken and Coke" />
            <Field label="A line about it (optional)" value={description} onChange={(e) => setDescription(e.target.value)} />
            <Button type="submit" tone="primary" disabled={lost || act.busy !== null}>
              Add the plate
            </Button>
          </form>
        </Card>

        <section aria-label="The plates" className="flex flex-col gap-2.5">
          {plates.isError ? <FailureCard title="We couldn't load the plates" message={failureOf(plates.error).message} /> : null}
          {plates.data && plates.data.length === 0 ? <Empty>No plates yet. Guests can only order what you list here.</Empty> : null}
          <ul className="flex flex-col gap-2.5">
            {(plates.data ?? []).map((plate) => (
              <li key={plate.id} className="flex flex-wrap items-center gap-3 rounded-lg bg-paperlight p-3.5 text-ink shadow-[0_8px_20px_rgba(0,0,0,0.22)]">
                <div className="min-w-[180px] flex-1">
                  <div id={`plate-${plate.id}`} className="font-bold leading-snug">{plate.name}</div>
                  {plate.description ? <div className="text-sm leading-snug text-quiet">{plate.description}</div> : null}
                </div>
                <div className="flex flex-wrap gap-2">
                  <div className="flex items-center gap-2.5">
                    <Switch
                      checked={plate.available}
                      labelledBy={`plate-${plate.id}`}
                      disabled={lost || act.busy !== null}
                      onChange={(next) => void act.run(plate.id, () => plannerApi.setPlate(id, plate, next))}
                    />
                    <span className="w-[5.5rem] text-sm font-bold text-quiet">{plate.available ? 'Serving' : 'Switched off'}</span>
                  </div>
                  <MoreMenu label={`More for ${plate.name}`} items={[{ label: `Delete ${plate.name}`, danger: true, onSelect: () => setRemoving(plate) }]} />
                </div>
              </li>
            ))}
          </ul>
        </section>
      </div>

      <Sheet open={removing !== null} onClose={() => { setRemoving(null); act.clear(); }} label="Remove this plate">
        <h2 className="font-heading text-[28px] font-normal text-bottle">Delete {removing?.name}?</h2>
        <p className="mt-2 text-[15px] leading-snug text-quiet">This can&apos;t be undone. To hide it for now, switch it off instead.</p>
        {act.failure ? <FailureCard className="my-3" title={act.failure.title} message={act.failure.message} /> : null}
        <div className="mt-4 flex flex-col gap-2.5">
          <Button
            big
            tone="primary"
            disabled={lost || act.busy !== null}
            onClick={async () => {
              if (removing && (await act.run(removing.id, () => plannerApi.removePlate(id, removing.id)))) setRemoving(null);
            }}
          >
            Yes, delete it
          </Button>
          <Button big tone="outline" onClick={() => { setRemoving(null); act.clear(); }}>
            Keep it
          </Button>
        </div>
      </Sheet>
    </div>
  );
}
