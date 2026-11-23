'use client';

import Link from 'next/link';
import { Suspense, useEffect, useRef, useState, type FormEvent } from 'react';
import { useSearchParams } from 'next/navigation';
import Ground from '@/components/plately/Ground';
import { Brand, Loader } from '@/components/plately/Logo';
import { Button, Card, FailureCard, Field } from '@/components/plately/ui';
import { dataOf, failureOf, http, type ApiFailure } from '@/lib/http';

// What the server says about a link that cannot be used. A message that is about the password, not the link, stays on the form.
const DEAD_LINK = /expired or was already used/i;

function ResetPassword() {
  const fromAddress = useSearchParams().get('token');
  // The token is taken once, then the address is cleaned, so it is not left in the history, a bookmark or a screenshot.
  const token = useRef<string | null>(fromAddress);
  const [password, setPassword] = useState('');
  const [again, setAgain] = useState('');
  const [shown, setShown] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [failure, setFailure] = useState<ApiFailure | null>(null);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState<string | null>(null);
  const [dead, setDead] = useState<string | null>(null);

  useEffect(() => {
    if (fromAddress !== null) {
      token.current = fromAddress;
      window.history.replaceState(null, '', window.location.pathname);
    }
  }, [fromAddress]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setFailure(null);
    const found: Record<string, string> = {};
    if (password.length < 8) found.password = 'Your new password needs at least 8 characters.';
    if (again !== password) found.again = "The two don't match.";
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    setBusy(true);
    try {
      setDone(dataOf<string>(await http.post('/api/v1/auth/reset-password', { token: token.current, newPassword: password })));
      token.current = null;
    } catch (error) {
      const failed = failureOf(error, "We couldn't save your password");
      if (DEAD_LINK.test(failed.message)) {
        setDead(failed.message);
        token.current = null;
      } else {
        setFailure(failed);
      }
    } finally {
      setBusy(false);
    }
  };

  // No token at all is the same dead end as a used one.
  const deadMessage = dead ?? (token.current ? null : 'This link has expired or was already used. Ask for a new one.');

  return (
    <Ground>
      <main className="mx-auto flex w-full max-w-md flex-1 animate-rise flex-col justify-center gap-5 px-5 pb-10 pt-8 md:max-w-[26rem]">
        <Brand />
        {done ? (
          <>
            <h1 className="font-heading text-[38px] font-normal leading-[1.1]">All set.</h1>
            <Card>
              <p role="status" className="text-[17px] leading-snug text-ink">
                {done}
              </p>
            </Card>
            <Link href="/staff/login" className="flex min-h-[52px] items-center justify-center rounded-full bg-tomato px-6 text-base font-bold text-white hover:bg-[#A23420]">
              Sign in
            </Link>
          </>
        ) : deadMessage ? (
          <>
            <h1 className="font-heading text-[38px] font-normal leading-[1.1]">This link has run out.</h1>
            <Card>
              <p role="status" className="text-[17px] leading-snug text-ink">
                {deadMessage}
              </p>
            </Card>
            <Link href="/forgot-password" className="flex min-h-[52px] items-center justify-center rounded-full bg-tomato px-6 text-base font-bold text-white hover:bg-[#A23420]">
              Ask for a new link
            </Link>
          </>
        ) : (
          <>
            <h1 className="font-heading text-[38px] font-normal leading-[1.1]">Choose a new password.</h1>
            <Card>
              <form noValidate onSubmit={(event) => void submit(event)} className="flex flex-col gap-4">
                <Field
                  label="New password"
                  name="password"
                  type={shown ? 'text' : 'password'}
                  autoComplete="new-password"
                  autoCapitalize="none"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  error={errors.password}
                  hint="At least 8 characters."
                  trailing={
                    <button
                      type="button"
                      aria-pressed={shown}
                      aria-label={shown ? 'Hide passwords' : 'Show passwords'}
                      onClick={() => setShown(!shown)}
                      className="flex min-h-[44px] min-w-[44px] items-center justify-center rounded-full px-3 text-[13px] font-bold text-quiet hover:text-bottle"
                    >
                      {shown ? 'Hide' : 'Show'}
                    </button>
                  }
                />
                <Field
                  label="Type it once more"
                  name="again"
                  type={shown ? 'text' : 'password'}
                  autoComplete="new-password"
                  autoCapitalize="none"
                  value={again}
                  onChange={(event) => setAgain(event.target.value)}
                  error={errors.again}
                />
                {failure ? <FailureCard title={failure.title} message={failure.message} /> : null}
                <Button type="submit" tone="primary" big disabled={busy}>
                  {busy ? 'Saving…' : 'Save my new password'}
                </Button>
              </form>
            </Card>
          </>
        )}
      </main>
    </Ground>
  );
}

export default function ResetPasswordPage() {
  return (
    <Suspense
      fallback={
        <Ground>
          <Loader message="One moment…" />
        </Ground>
      }
    >
      <ResetPassword />
    </Suspense>
  );
}
