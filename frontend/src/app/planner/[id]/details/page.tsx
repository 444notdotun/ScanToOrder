'use client';

import { useParams } from 'next/navigation';
import { useState } from 'react';
import { useConnection } from '@/components/plately/Connection';
import { Button, Card, FailureCard, Sheet } from '@/components/plately/ui';
import EventDetails from '@/components/staff/EventDetails';
import { useAction } from '@/hooks/useAction';
import { plannerApi, useEvent } from '@/lib/planner';

export default function EventDetailsPage() {
  const { id } = useParams<{ id: string }>();
  const { lost } = useConnection();
  const event = useEvent(id);
  const act = useAction();
  const [ending, setEnding] = useState(false);
  const data = event.data;

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
      <div className="flex flex-col gap-6">
        {data ? (
          <Card>
            <h2 className="mb-3 font-heading text-[22px] font-normal text-bottle">About the event</h2>
            {act.failure ? <FailureCard className="mb-3" title={act.failure.title} message={act.failure.message} /> : null}
            {data.status === 'ENDED' ? (
              <p className="text-[17px] leading-snug text-quiet">This event is over, so its details can no longer change.</p>
            ) : (
              <EventDetails
                key={`${data.id}-${data.expectedGuests}-${data.seatingMode}-${data.platesPerGuest}`}
                initial={data}
                submitLabel="Save the changes"
                busy={lost || act.busy !== null}
                onSubmit={(form) => act.run('save', () => plannerApi.update(data.id, form))}
              />
            )}
          </Card>
        ) : null}
      </div>

      <div className="flex flex-col gap-6">
        {data && data.status !== 'ENDED' ? (
          <Card className="flex flex-col gap-2">
            <h2 className="font-heading text-[22px] font-normal text-bottle">Finished early?</h2>
            <p className="text-[15px] leading-snug text-quiet">Ending the event closes every guest&apos;s seat and stops all the codes. It ends by itself at the time you set.</p>
            <Button tone="outline" disabled={lost || act.busy !== null} onClick={() => setEnding(true)}>
              End the event now
            </Button>
          </Card>
        ) : null}
      </div>

      <Sheet open={ending} onClose={() => setEnding(false)} label="End the event">
        <h2 className="font-heading text-[28px] font-normal text-bottle">End {data?.name} now?</h2>
        <p className="mb-4 mt-2 text-[15px] leading-snug text-quiet">Every seat is closed and every code stops working. This cannot be undone.</p>
        {act.failure ? <FailureCard className="mb-3" title={act.failure.title} message={act.failure.message} /> : null}
        <div className="flex flex-col gap-2.5">
          <Button
            big
            tone="primary"
            disabled={lost || act.busy !== null}
            onClick={async () => {
              if (await act.run('end', () => plannerApi.end(id))) setEnding(false);
            }}
          >
            Yes, end it
          </Button>
          <Button big tone="outline" onClick={() => setEnding(false)}>
            Keep it going
          </Button>
        </div>
      </Sheet>
    </div>
  );
}
