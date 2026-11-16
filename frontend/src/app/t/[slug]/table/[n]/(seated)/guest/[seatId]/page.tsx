'use client';

import { useParams, useRouter } from 'next/navigation';
import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import BackLink from '@/components/guest/BackLink';
import { useConnection } from '@/components/plately/Connection';
import { Button, Eyebrow, FailureCard, Field } from '@/components/plately/ui';
import { useSession } from '@/lib/guest';
import { failureOf, http, type ApiFailure } from '@/lib/http';

// A friend who has no phone: give them a name and order for them from yours.
export default function GuestWithoutPhonePage() {
  const { slug, n, seatId } = useParams<{ slug: string; n: string; seatId: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();
  const { lost } = useConnection();
  const session = useSession().data!;
  const [name, setName] = useState('');
  const [failure, setFailure] = useState<ApiFailure | null>(null);
  const [busy, setBusy] = useState(false);
  const seat = session.seats.find((candidate) => candidate.seatId === seatId);

  const seatThem = async () => {
    if (!name.trim()) {
      setFailure({ status: null, title: '', message: 'What should we call them?', offline: false });
      return;
    }
    setBusy(true);
    setFailure(null);
    try {
      await http.post('/api/v1/sessions/me/guests', { seatId, guestName: name.trim() });
      await queryClient.invalidateQueries();
      router.replace(`/t/${slug}/table/${n}/home`);
    } catch (error) {
      const failed = failureOf(error, 'We could not seat them');
      setFailure(failed);
      if (failed.status === 409 || failed.status === 400) void queryClient.invalidateQueries();
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="mx-auto flex w-full max-w-md md:max-w-xl flex-1 animate-rise flex-col gap-3 px-5 pb-8 pt-6">
      <BackLink href={`/t/${slug}/table/${n}/home`} />
      <Eyebrow>{seat ? `Seat ${seat.position}` : 'A saved chair'}</Eyebrow>
      <h1 className="font-heading text-[34px] font-normal leading-[1.1]">Who&apos;s joining you?</h1>
      <p className="text-[17px] leading-snug text-soft">They don&apos;t need a phone. Give us a name and you can order for them from yours.</p>
      <form
        className="mt-2 flex flex-1 flex-col gap-4"
        onSubmit={(event) => {
          event.preventDefault();
          void seatThem();
        }}
      >
        <div className="rounded-[20px] bg-paper p-5">
          <Field label="Their name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Mama Ada" autoComplete="off" />
        </div>
        {failure ? <FailureCard title={failure.title} message={failure.message} /> : null}
        <div className="flex-1" />
        <Button type="submit" tone="primary" big disabled={busy || lost}>
          {busy ? 'One moment…' : 'Seat them'}
        </Button>
      </form>
    </main>
  );
}
