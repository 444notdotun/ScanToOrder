'use client';

import Link from 'next/link';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import BackLink from '@/components/guest/BackLink';
import ReceiptTicket from '@/components/guest/ReceiptTicket';
import { Loader, Mark, Wordmark } from '@/components/plately/Logo';
import { Button, Card, FailureCard, Field } from '@/components/plately/ui';
import { guestApi, rememberedTable, useSession } from '@/lib/guest';
import { failureOf, type ApiFailure } from '@/lib/http';
import { PICTURE_WIDTH, pictureOf, saveOrShare } from '@/lib/receiptPicture';

// Where a refund link points inside this app, whatever address the email used.
function insidePath(link: string): string {
  try {
    const url = new URL(link);
    return url.pathname + url.search;
  } catch {
    return link;
  }
}

export default function ReceiptPage() {
  const { slug, n, reference } = useParams<{ slug: string; n: string; reference: string }>();
  const session = useSession().data!;
  const receipt = useQuery({ queryKey: ['receipt', reference], queryFn: () => guestApi.receipt(reference), retry: false });
  const picture = useRef<HTMLDivElement>(null);
  const emailPanel = useRef<HTMLDivElement>(null);
  const [saving, setSaving] = useState(false);
  const [saveProblem, setSaveProblem] = useState<string | null>(null);
  const [emailing, setEmailing] = useState(false);
  const [email, setEmail] = useState('');
  const [emailProblem, setEmailProblem] = useState<string | undefined>();
  const [sending, setSending] = useState(false);
  const [failure, setFailure] = useState<ApiFailure | null>(null);
  const [sentAnswer, setSentAnswer] = useState<string | null>(null);

  // The field is brought to the middle of the screen, so the Send button is never left under the floating tab bar.
  useEffect(() => {
    if (emailing) emailPanel.current?.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }, [emailing]);

  if (receipt.isPending) return <Loader message="Finding your receipt…" />;
  if (receipt.isError) {
    const failed = failureOf(receipt.error, "We can't open that receipt");
    return (
      <main className="mx-auto flex w-full max-w-md md:max-w-xl flex-1 flex-col gap-4 px-5 pt-6">
        <BackLink href={`/t/${slug}/table/${n}/home`} />
        <FailureCard title={failed.title} message={failed.message} />
      </main>
    );
  }

  const r = receipt.data;
  const business = session.businessName ?? '';
  const table = session.displayNumber ? String(session.displayNumber) : r.tableNumber;

  const save = async () => {
    if (!picture.current) return;
    setSaving(true);
    setSaveProblem(null);
    try {
      const blob = await pictureOf(picture.current);
      await saveOrShare(blob, `plately-receipt-${r.receiptNumber}.png`, business ? `Receipt from ${business}` : 'Receipt');
    } catch {
      setSaveProblem("We couldn't make the picture. Try again.");
    } finally {
      setSaving(false);
    }
  };

  const openEmail = () => {
    setEmailing(true);
    setSentAnswer(null);
    // The email the guest typed when they sat down, if they gave one.
    setEmail((current) => current || rememberedTable()?.email || '');
  };

  const send = async (event: FormEvent) => {
    event.preventDefault();
    setFailure(null);
    if (!email.trim()) {
      setEmailProblem('Type the email to send it to.');
      return;
    }
    setEmailProblem(undefined);
    setSending(true);
    try {
      setSentAnswer(await guestApi.emailReceipt(r.paymentReference, email.trim()));
      setEmailing(false);
    } catch (error) {
      setFailure(failureOf(error, "We couldn't send that"));
    } finally {
      setSending(false);
    }
  };

  return (
    <main className="mx-auto flex w-full max-w-md md:max-w-xl flex-1 animate-rise flex-col gap-4 px-5 pb-8 pt-6">
      <BackLink href={`/t/${slug}/table/${n}/home`} />
      <div className="mx-auto w-full max-w-[420px] drop-shadow-[0_18px_30px_rgba(0,0,0,0.35)]">
        <ReceiptTicket receipt={r} businessName={business} tableName={table} />
      </div>

      {r.refundMessage ? (
        <div className="rounded-md border border-dashed border-hand/60 bg-errbg p-3 text-[15px] text-errtext">
          <div className="font-bold">{r.refundMessage}</div>
          {r.refundLink ? (
            <Link href={insidePath(r.refundLink)} className="mt-1 inline-block font-bold underline underline-offset-4">
              Open your refund page
            </Link>
          ) : null}
        </div>
      ) : null}

      <div className="mx-auto flex w-full max-w-[420px] flex-col gap-3">
        <Button big tone="light" disabled={saving} onClick={() => void save()}>
          {saving ? 'Making the picture…' : 'Save receipt'}
        </Button>
        {saveProblem ? <FailureCard message={saveProblem} /> : null}
        {sentAnswer ? (
          <p role="status" className="text-center text-[16px] font-bold text-mustard">
            {sentAnswer}
          </p>
        ) : null}
        {!emailing && !sending ? (
          <Button tone="ghost" onClick={openEmail}>
            Email me this receipt
          </Button>
        ) : null}
        {emailing || sending ? (
          <div ref={emailPanel}>
          <Card>
            <form noValidate onSubmit={(event) => void send(event)} className="flex flex-col gap-3">
              <Field
                label="Send it to"
                name="email"
                type="email"
                autoComplete="email"
                autoCapitalize="none"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                error={emailProblem}
              />
              {failure ? <FailureCard title={failure.title} message={failure.message} /> : null}
              {sending ? (
                <p role="status" className="text-center text-[15px] font-bold text-quiet">
                  Sending…
                </p>
              ) : (
                <Button type="submit" tone="primary">
                  Send
                </Button>
              )}
            </form>
          </Card>
          </div>
        ) : null}
      </div>

      {/* The picture that is saved: the same ticket, with the Plately logo above it on the deep green. It is drawn off screen. */}
      <div aria-hidden="true" style={{ position: 'fixed', left: -10000, top: 0, pointerEvents: 'none' }}>
        <div ref={picture} style={{ width: PICTURE_WIDTH, background: '#10281F', padding: '40px 60px 44px', boxSizing: 'border-box' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, color: '#F4ECD8', marginBottom: 26 }}>
            <Mark size={40} />
            <Wordmark size={36} />
          </div>
          <ReceiptTicket receipt={r} businessName={business} tableName={table} width={PICTURE_WIDTH - 120} />
        </div>
      </div>
    </main>
  );
}
