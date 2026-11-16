'use client';

import { useEffect, useState, type FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import Ground from '@/components/plately/Ground';
import { Brand, Loader } from '@/components/plately/Logo';
import { Button, Card, FailureCard, Field } from '@/components/plately/ui';
import { dataOf, failureOf, http, type ApiFailure } from '@/lib/http';
import { homeFor, useAuthHydrated, useAuthStore, type WorkerProfile } from '@/store/authStore';

// A temporary password only opens this page. The new one is the person's own.
export default function NewPasswordPage() {
  const router = useRouter();
  const hydrated = useAuthHydrated();
  const profile = useAuthStore((state) => state.workerProfile);
  const login = useAuthStore((state) => state.login);
  const logout = useAuthStore((state) => state.logout);
  const [password, setPassword] = useState('');
  const [again, setAgain] = useState('');
  const [problem, setProblem] = useState<string | null>(null);
  const [failure, setFailure] = useState<ApiFailure | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!hydrated) return;
    if (!profile) router.replace('/staff/login');
    else if (!profile.mustChangePassword) router.replace(homeFor(profile));
  }, [hydrated, profile, router]);

  if (!hydrated || !profile || !profile.mustChangePassword) {
    return (
      <Ground>
        <Loader message="One moment…" />
      </Ground>
    );
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setFailure(null);
    if (password.length < 8) {
      setProblem('Your new password needs at least 8 characters.');
      return;
    }
    if (password !== again) {
      setProblem('The two passwords are not the same. Type it once more.');
      return;
    }
    setProblem(null);
    setBusy(true);
    try {
      const me = dataOf<WorkerProfile>(await http.post('/api/v1/auth/change-password', { newPassword: password }));
      login({ ...me, mustChangePassword: false });
      router.replace(homeFor(me));
    } catch (error) {
      setFailure(failureOf(error, "We couldn't save your password"));
      setBusy(false);
    }
  };

  return (
    <Ground>
      <header className="mx-auto flex w-full max-w-md items-center justify-between px-5 pt-5 md:max-w-xl">
        <Brand />
      </header>
      <main className="mx-auto flex w-full max-w-md flex-1 animate-rise flex-col gap-5 px-5 pb-8 pt-8 md:max-w-xl">
        <div>
          <h1 className="font-heading text-[38px] font-normal leading-[1.1]">Choose your own password.</h1>
          <p className="mt-2 text-[17px] leading-snug text-soft">
            {profile.fullName ? `${profile.fullName.split(/\s+/)[0]}, the` : 'The'} password you signed in with was only to get you through the door. Pick one only you know.
          </p>
        </div>

        <Card>
          <form noValidate onSubmit={(event) => void submit(event)} className="flex flex-col gap-4">
            <Field
              label="New password"
              name="new-password"
              type="password"
              autoComplete="new-password"
              hint="At least 8 characters, and not the temporary one."
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              error={problem && password.length < 8 ? problem : undefined}
            />
            <Field
              label="Type it once more"
              name="repeat-password"
              type="password"
              autoComplete="new-password"
              value={again}
              onChange={(event) => setAgain(event.target.value)}
              error={problem && password.length >= 8 ? problem : undefined}
            />
            {failure ? <FailureCard title={failure.title} message={failure.message} /> : null}
            <Button type="submit" tone="primary" big disabled={busy}>
              {busy ? 'Saving…' : 'Save and carry on'}
            </Button>
          </form>
        </Card>

        <p className="text-center text-sm text-soft">
          Not you?{' '}
          <button
            type="button"
            className="underline underline-offset-4"
            onClick={() => {
              logout();
              router.replace('/staff/login');
            }}
          >
            Sign out
          </button>
        </p>
      </main>
    </Ground>
  );
}
