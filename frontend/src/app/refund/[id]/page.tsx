'use client';

import { Suspense, useState } from 'react';
import { useParams, useSearchParams } from 'next/navigation';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import Ground from '@/components/plately/Ground';
import { Brand, Loader, Watermark } from '@/components/plately/Logo';
import { Button, Card, Dashes, FailureCard, Field, Handwriting } from '@/components/plately/ui';
import type { Bank, GuestRefund } from '@/lib/guest';
import { dataOf, failureOf, http, type ApiFailure } from '@/lib/http';
import { naira } from '@/lib/money';

// The link a guest is sent when a restaurant owes them money. The link's own token is the only way in.
function RefundScreen() {
  const { id } = useParams<{ id: string }>();
  const token = useSearchParams().get('t') ?? '';
  const queryClient = useQueryClient();
  const base = `/api/v1/refunds/${id}/guest`;
  const query = { params: { t: token } };

  const refund = useQuery({
    queryKey: ['refund', id, token],
    enabled: Boolean(token),
    retry: false,
    queryFn: async () => dataOf<GuestRefund>(await http.get(base, query)),
  });
  const banks = useQuery({
    queryKey: ['banks'],
    retry: false,
    queryFn: async () => dataOf<Bank[]>(await http.get('/api/v1/signup/banks')),
  });

  const [email, setEmail] = useState('');
  const [changingEmail, setChangingEmail] = useState(false);
  const [bankCode, setBankCode] = useState('');
  const [account, setAccount] = useState('');
  const [failure, setFailure] = useState<ApiFailure | { title?: string; message: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const act = async (work: () => Promise<unknown>, title: string) => {
    setBusy(true);
    setFailure(null);
    try {
      await work();
      await queryClient.invalidateQueries({ queryKey: ['refund', id, token] });
      return true;
    } catch (error) {
      setFailure(failureOf(error, title));
      return false;
    } finally {
      setBusy(false);
    }
  };

  const shell = (children: React.ReactNode, mark = false) => (
    <Ground>
      {mark ? <Watermark className="-bottom-[70px] -right-24" /> : null}
      <main className="relative mx-auto flex w-full max-w-md flex-1 animate-rise flex-col gap-4 px-5 pb-10 pt-8 md:max-w-lg">
        <Brand size={24} />
        {children}
      </main>
    </Ground>
  );

  if (!token) {
    return shell(
      <>
        <h1 className="font-heading text-[34px] font-normal leading-tight">We can&apos;t open this refund.</h1>
        <FailureCard title="The link is incomplete" message="Open the link from your email or receipt exactly as it was sent." />
      </>,
    );
  }
  if (refund.isPending) {
    return (
      <Ground>
        <Loader message="Finding your refund…" />
      </Ground>
    );
  }
  if (refund.isError) {
    const failed = failureOf(refund.error, "We can't find that refund");
    return shell(
      <>
        <h1 className="font-heading text-[34px] font-normal leading-tight">We can&apos;t find that refund.</h1>
        <FailureCard title={failed.offline ? failed.title : 'Check the link you were sent'} message={failed.offline ? failed.message : "This link doesn't match a refund. Open it again from your email or your receipt."} />
        {failed.offline ? <Button tone="light" onClick={() => void refund.refetch()}>Try again</Button> : null}
      </>,
    );
  }

  const r = refund.data;

  const saveEmail = async () => {
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) {
      setFailure({ message: 'That email does not look right. Check it and try again.' });
      return;
    }
    if (await act(() => http.post(`${base}/email`, { email: email.trim() }, query), "We couldn't save your email")) {
      setChangingEmail(false);
      setEmail('');
    }
  };

  const saveBank = async () => {
    if (!bankCode) {
      setFailure({ message: 'Choose your bank first.' });
      return;
    }
    if (!/^\d{10}$/.test(account.trim())) {
      setFailure({ message: 'An account number has 10 digits.' });
      return;
    }
    if (await act(() => http.post(`${base}/bank-details`, { bankCode, accountNumber: account.trim() }, query), "We couldn't check that account")) {
      setAccount('');
    }
  };

  // ---- asked first: where to reach the guest
  if (r.needsEmail || changingEmail) {
    return shell(
      <>
        <h1 className="font-heading text-[34px] font-normal leading-[1.1]">Where should we tell you about your refund?</h1>
        <p className="text-[17px] text-soft">
          {r.restaurant} owes you {naira(r.amount)} for order {r.orderNumber}. Give us an email and we&apos;ll keep you posted.
        </p>
        <Card>
          <form
            className="flex flex-col gap-4"
            onSubmit={(e) => {
              e.preventDefault();
              void saveEmail();
            }}
          >
            <Field label="Your email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder={r.guestEmail ?? 'you@example.com'} autoComplete="email" />
            {failure ? <FailureCard title={failure.title} message={failure.message} /> : null}
            <Button type="submit" tone="primary" big disabled={busy}>
              {busy ? 'One moment…' : 'Save my email'}
            </Button>
            {changingEmail ? (
              <Button tone="outline" onClick={() => { setChangingEmail(false); setFailure(null); }}>
                Keep {r.guestEmail}
              </Button>
            ) : null}
          </form>
        </Card>
      </>,
    );
  }

  const closed = r.status === 'CONFIRMED';

  return shell(
    <>
      <div className="text-[13px] font-bold uppercase tracking-[0.12em] text-soft">{r.restaurant} · Order {r.orderNumber}</div>
      <h1 className="font-heading text-[36px] font-normal leading-[1.08]">
        {closed ? 'All sorted. Thank you.' : r.status === 'SETTLED' ? 'Your refund is on its way.' : 'Your refund.'}
      </h1>

      <Card>
        <div className="flex items-end justify-between gap-3">
          <div>
            <div className="text-sm text-quiet">{closed ? 'Refunded' : 'Owed to you'}</div>
            <div className="font-heading text-[40px] leading-none text-bottle">{naira(r.amount)}</div>
          </div>
          {closed ? <div className="-rotate-6 animate-pop rounded-md border-[3px] border-good px-2.5 py-0.5 text-lg font-bold tracking-[0.12em] text-good">PAID</div> : null}
        </div>
        {r.items.length > 0 ? (
          <>
            <Dashes className="my-3" />
            <ul className="flex flex-col gap-1 text-[15px] text-ink">
              {r.items.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </>
        ) : null}
        <Dashes className="my-3" />
        <p className="text-[15px] leading-snug text-ink">{r.whatToDo}</p>
        {r.reportedMissing ? <Handwriting onPaper className="mt-2 !text-[21px]">We have let {r.restaurant} and Plately know.</Handwriting> : null}
      </Card>

      {failure ? <FailureCard title={failure.title} message={failure.message} /> : null}

      {r.canAddBankDetails ? (
        <Card>
          <h2 className="font-heading text-[22px] font-normal text-bottle">Where should it go?</h2>
          {r.bankName ? (
            <div className="mt-2 rounded-xl bg-paperlight p-3 text-[15px] text-ink">
              <div className="font-bold">{r.accountName}</div>
              <div className="text-quiet">
                {r.bankName} · account ending {r.accountLast4}
              </div>
            </div>
          ) : (
            <p className="mt-1 text-sm text-quiet">Optional. If you were paid in cash at the table, you can skip this.</p>
          )}
          <form
            className="mt-3 flex flex-col gap-3"
            onSubmit={(e) => {
              e.preventDefault();
              void saveBank();
            }}
          >
            <div className="flex flex-col gap-1.5">
              <label htmlFor="bank" className="text-sm font-bold text-bottle">
                Your bank
              </label>
              <select
                id="bank"
                value={bankCode}
                onChange={(e) => setBankCode(e.target.value)}
                className="h-[50px] w-full rounded-xl border-[1.5px] border-rule2 bg-paperlight px-3 text-[17px] text-ink"
              >
                <option value="">{banks.isPending ? 'Loading banks…' : 'Choose your bank'}</option>
                {(banks.data ?? []).map((bank) => (
                  <option key={bank.code} value={bank.code}>
                    {bank.name}
                  </option>
                ))}
              </select>
            </div>
            <Field label="Account number" inputMode="numeric" value={account} onChange={(e) => setAccount(e.target.value)} autoComplete="off" hint="We check it with your bank, so the name below is yours." />
            <Button type="submit" tone="bottle" disabled={busy}>
              {r.bankName ? 'Change my account' : 'Save my account'}
            </Button>
          </form>
        </Card>
      ) : null}

      {r.canConfirmReceived ? (
        <Button tone="primary" big disabled={busy} onClick={() => void act(() => http.post(`${base}/received`, null, query), "We couldn't record that")}>
          {r.status === 'SETTLED' ? 'It has reached me' : "I've been paid"}
        </Button>
      ) : null}
      {r.canReportMissing ? (
        <Button tone="ghost" disabled={busy} onClick={() => void act(() => http.post(`${base}/not-received`, null, query), "We couldn't record that")}>
          It hasn&apos;t reached me
        </Button>
      ) : null}

      {!closed ? (
        <p className="text-center text-sm text-soft">
          We&apos;ll write to {r.guestEmail}.{' '}
          <button type="button" onClick={() => { setChangingEmail(true); setFailure(null); }} className="font-bold underline underline-offset-4">
            Change email
          </button>
        </p>
      ) : null}
    </>,
    closed,
  );
}

export default function RefundPage() {
  return (
    <Suspense fallback={null}>
      <RefundScreen />
    </Suspense>
  );
}
