'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import Ground from '@/components/plately/Ground';
import { Brand, Loader, Watermark } from '@/components/plately/Logo';
import { Button, Eyebrow, FailureCard, Field } from '@/components/plately/ui';
import { eventGuestApi, rememberTable, useSession } from '@/lib/guest';
import { failureOf, http, type ApiFailure } from '@/lib/http';
import { clock } from '@/lib/money';

// At an event the code is the seat and the supper: scan the table, type the code.
export default function EventCodePage() {
  const { slug, eventCode, n } = useParams<{ slug: string; eventCode: string; n: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();
  const [code, setCode] = useState('');
  const [failure, setFailure] = useState<ApiFailure | null>(null);
  const [busy, setBusy] = useState(false);

  // Someone who is already seated goes straight to their plates.
  const session = useSession();
  useEffect(() => {
    if (session.data) router.replace(`/t/${slug}/e/${eventCode}/table/${n}/plates`);
  }, [session.data, router, slug, eventCode, n]);

  const preview = useQuery({ queryKey: ['event-preview', slug, eventCode, n], queryFn: () => eventGuestApi.preview(slug, eventCode, n), retry: false });

  if (preview.isPending || session.isPending || session.data) {
    return (
      <Ground>
        <Loader message="Finding your table…" />
      </Ground>
    );
  }

  if (preview.isError) {
    const failed = failureOf(preview.error, "We couldn't find this table");
    return (
      <Ground>
        <main className="mx-auto flex w-full max-w-md flex-1 animate-rise flex-col gap-4 px-6 pb-8 pt-10 md:max-w-lg">
          <Brand />
          <h1 className="font-heading text-[34px] font-normal leading-tight">We can&apos;t seat you here yet.</h1>
          <FailureCard title={failed.title} message={failed.message} />
          <p className="text-[15px] text-soft">Ask a host, or scan the card on your table again.</p>
        </main>
      </Ground>
    );
  }

  const event = preview.data;
  // The doors and the end are known before a code is typed, so say so first.
  const waiting = !event.open && !event.over;

  const redeem = async () => {
    if (!code.trim()) {
      setFailure({ status: null, title: '', message: 'Type the letters on your invitation.', offline: false });
      return;
    }
    setBusy(true);
    setFailure(null);
    try {
      await http.post(`/api/v1/t/${slug}/e/${eventCode}/tables/${n}/code`, { code: code.trim() });
      rememberTable({ slug, n });
      await queryClient.invalidateQueries();
      router.replace(`/t/${slug}/e/${eventCode}/table/${n}/plates`);
    } catch (error) {
      const failed = failureOf(error, 'That code did not work');
      setFailure(failed);
      // A table that moved on while the guest typed: show the doors as they are now.
      void preview.refetch();
    } finally {
      setBusy(false);
    }
  };

  return (
    <Ground>
      <Watermark className="-bottom-[70px] -right-24" />
      <main className="relative mx-auto flex w-full max-w-md flex-1 animate-rise flex-col gap-3 px-6 pb-8 pt-8 md:max-w-lg md:justify-center">
        <Brand size={24} />
        <Eyebrow className="mt-3">
          {event.eventName}
          {event.venue ? ` · ${event.venue}` : ''} · Table {event.tableNumber}
        </Eyebrow>
        <h1 className="font-heading text-[38px] font-normal leading-[1.08]">Welcome. Got your code?</h1>
        <p className="text-[17px] text-soft">It is your seat and your supper.</p>

        {event.over ? (
          <FailureCard title="This event is over" message="Thank you for coming. Codes stop working when the event ends." />
        ) : waiting ? (
          <FailureCard title="Not open yet" message={`Doors open at ${clock(event.startsAt)}. Come back then and your code will work.`} />
        ) : null}

        <form
          className="mt-2 flex flex-col gap-4 rounded-[20px] bg-paper p-5"
          onSubmit={(e) => {
            e.preventDefault();
            void redeem();
          }}
        >
          <Field
            label="Your code"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            placeholder="K7M 2QD"
            autoComplete="off"
            autoCapitalize="characters"
            hint="Six letters and numbers, from your invitation."
          />
          {failure ? <FailureCard title={failure.title} message={failure.message} /> : null}
          <Button type="submit" tone="primary" big disabled={busy || event.over}>
            {busy ? 'Finding your seat…' : 'Find my seat'}
          </Button>
        </form>
      </main>
    </Ground>
  );
}
