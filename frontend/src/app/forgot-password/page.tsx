'use client';

import Link from 'next/link';
import { useState, type FormEvent } from 'react';
import Ground from '@/components/plately/Ground';
import { Brand } from '@/components/plately/Logo';
import { Button, Card, FailureCard, Field } from '@/components/plately/ui';
import { dataOf, failureOf, http, type ApiFailure } from '@/lib/http';

// For the person who runs a business. Staff have no email: their manager gives them a new password.
export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [problem, setProblem] = useState<string | undefined>();
  const [failure, setFailure] = useState<ApiFailure | null>(null);
  const [busy, setBusy] = useState(false);
  // What the server said. It is the same whoever asks, so the page never says whether the email is known.
  const [answer, setAnswer] = useState<string | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setFailure(null);
    if (!email.trim()) {
      setProblem('Type the email you signed up with.');
      return;
    }
    setProblem(undefined);
    setBusy(true);
    try {
      setAnswer(dataOf<string>(await http.post('/api/v1/auth/forgot-password', { email: email.trim() })));
    } catch (error) {
      setFailure(failureOf(error, "We couldn't send that"));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Ground>
      <main className="mx-auto flex w-full max-w-md flex-1 animate-rise flex-col justify-center gap-5 px-5 pb-10 pt-8 md:max-w-[26rem]">
        <Brand />
        <h1 className="font-heading text-[38px] font-normal leading-[1.1]">Forgot your password?</h1>
        {answer ? (
          <>
            <Card>
              <p role="status" className="text-[17px] leading-snug text-ink">
                {answer}
              </p>
            </Card>
            <Link href="/staff/login" className="flex min-h-[52px] items-center justify-center rounded-full bg-cream px-6 text-base font-bold text-bottle hover:bg-white">
              Back to sign in
            </Link>
          </>
        ) : (
          <>
            <p className="text-[17px] leading-snug text-soft">Type the email you signed up with and we&apos;ll send you a link to choose a new one.</p>
            <Card>
              <form noValidate onSubmit={(event) => void submit(event)} className="flex flex-col gap-4">
                <Field
                  label="Email"
                  name="email"
                  type="email"
                  autoComplete="email"
                  autoCapitalize="none"
                  autoCorrect="off"
                  spellCheck={false}
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  error={problem}
                />
                {failure ? <FailureCard title={failure.title} message={failure.message} /> : null}
                <Button type="submit" tone="primary" big disabled={busy}>
                  {busy ? 'Sending…' : 'Send me a link'}
                </Button>
              </form>
            </Card>
            <Link href="/staff/login" className="flex min-h-[44px] items-center self-start border-b border-cream/40 text-[15px] text-cream">
              Back to sign in
            </Link>
          </>
        )}
      </main>
    </Ground>
  );
}
