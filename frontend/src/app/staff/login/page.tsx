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
      <header className="mx-auto flex w-full max-w-md items-center justify-between px-5 pt-5 md:max-w-xl">
        <Brand />
      </header>
      <main className="mx-auto flex w-full max-w-md flex-1 animate-rise flex-col gap-5 px-5 pb-8 pt-8 md:max-w-xl">
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
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
            {failure ? <FailureCard title={failure.title} message={failure.message} /> : null}
            <Button type="submit" tone="primary" big disabled={busy}>
              {busy ? 'Signing you in…' : 'Sign in'}
            </Button>
          </form>
        </Card>

        <div className="flex flex-wrap justify-center gap-x-6 gap-y-2 text-sm text-soft">
          <Link href="/scan" className="underline underline-offset-4">
            Find your table
          </Link>
          <Link href="/signup" className="underline underline-offset-4">
            Bring Plately to your business
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
