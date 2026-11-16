'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useConnection } from '@/components/plately/Connection';
import { Card, FailureCard } from '@/components/plately/ui';
import EventDetails from '@/components/staff/EventDetails';
import PageHead from '@/components/staff/PageHead';
import { useAction } from '@/hooks/useAction';
import { plannerApi } from '@/lib/planner';

export default function NewEventPage() {
  const router = useRouter();
  const { lost } = useConnection();
  const act = useAction();

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 animate-rise flex-col gap-5 px-5 pb-6 pt-4">
      <PageHead title="Create an event" line="Fill in the basics. You pay once you know the numbers; until then it is a draft.">
        <Link href="/planner" className="flex min-h-[48px] items-center rounded-full border-[1.5px] border-cream/45 px-5 font-bold text-cream hover:bg-cream/10">
          Back to your events
        </Link>
      </PageHead>
      <Card>
        {act.failure ? <FailureCard className="mb-4" title={act.failure.title} message={act.failure.message} /> : null}
        <EventDetails
          submitLabel="Create the event"
          busy={lost || act.busy !== null}
          onSubmit={async (form) => {
            let id: string | null = null;
            const worked = await act.run('create', async () => {
              id = (await plannerApi.create(form)).id;
            });
            if (worked && id) router.push(`/planner/${id}`);
            return worked;
          }}
        />
      </Card>
    </main>
  );
}
