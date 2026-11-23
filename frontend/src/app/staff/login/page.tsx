'use client';

import Link from 'next/link';
import { Suspense, useState, type FormEvent } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Ground from '@/components/plately/Ground';
import { Brand, Loader } from '@/components/plately/Logo';
import { Button, Card, FailureCard, Field } from '@/components/plately/ui';
import { dataOf, failureOf, http, type ApiFailure } from '@/lib/http';
import { homeFor, useAuthStore, type WorkerProfile } from '@/store/authStore';

function SignIn() {
  const router = useRouter();
  const ended = useSearchParams().get('ended');
  const login = useAuthStore((state) => state.login);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [shown, setShown] = useState(false);
  const [failure, setFailure] = useState<ApiFailure | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setFailure(null);
    if (!username.trim() || !password) {
      setFailure({ status: null, title: 'Almost there', message: 'Type your username and your password, and we will let you in.', offline: false });
      return;
    }
    setBusy(true);
    try {
      const me = dataOf<WorkerProfile>(await http.post('/api/v1/auth/login', { username: username.trim(), password }));
      login({ ...me, mustChangePassword: Boolean(me.mustChangePassword) });
      router.replace(me.mustChangePassword ? '/staff/new-password' : homeFor(me));
    } catch (error) {
      setFailure(failureOf(error, "We couldn't sign you in"));
      setBusy(false);
    }
  };

  return (
    <Ground>
      <main className="mx-auto flex w-full max-w-md flex-1 animate-rise flex-col justify-center gap-5 px-5 pb-10 pt-8 md:max-w-[26rem]">
        <Brand />
        <h1 className="font-heading text-[38px] font-normal leading-[1.1]">Staff sign-in</h1>

        {ended ? (
          <p role="status" className="animate-fade text-[15px] leading-snug text-mustard">
            You were signed out. Sign in again to carry on.
          </p>
        ) : null}

        <Card>
          <form noValidate onSubmit={(event) => void submit(event)} className="flex flex-col gap-4">
            <Field
              label="Username"
              name="username"
              autoComplete="username"
              autoCapitalize="none"
              autoCorrect="off"
              spellCheck={false}
              value={username}
              onChange={(event) => setUsername(event.target.value)}
            />
            <Field
              label="Password"
              name="password"
              type={shown ? 'text' : 'password'}
              autoComplete="current-password"
              autoCapitalize="none"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              trailing={
                <button
                  type="button"
                  aria-pressed={shown}
                  aria-label={shown ? 'Hide password' : 'Show password'}
                  onClick={() => setShown(!shown)}
                  className="flex min-h-[44px] min-w-[44px] items-center justify-center rounded-full px-3 text-[13px] font-bold text-quiet hover:text-bottle"
                >
                  {shown ? 'Hide' : 'Show'}
                </button>
              }
            />
            <Link href="/forgot-password" className="-mt-2 flex min-h-[44px] items-center self-start text-[15px] font-bold text-bottle underline underline-offset-4">
              Forgot password?
            </Link>
            {failure ? <FailureCard title={failure.title} message={failure.message} /> : null}
            <Button type="submit" tone="primary" big disabled={busy}>
              {busy ? 'Signing you in…' : 'Sign in'}
            </Button>
          </form>
        </Card>

        <div className="flex flex-col items-start text-[15px]">
          <Link href="/scan" className="flex min-h-[44px] items-center border-b border-cream/40 pr-1 text-cream">
            Here to eat? Scan the card on your table
          </Link>
          <Link href="/signup" className="mt-1 flex min-h-[44px] items-center border-b border-mustard pr-1 text-cream">
            Own a place? Get Plately for your place
          </Link>
        </div>
      </main>
    </Ground>
  );
}

export default function StaffSignInPage() {
  return (
    <Suspense
      fallback={
        <Ground>
          <Loader message="One moment…" />
        </Ground>
      }
    >
      <SignIn />
    </Suspense>
  );
}
