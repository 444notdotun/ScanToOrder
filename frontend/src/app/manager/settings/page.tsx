'use client';

import { useId, useState, type FormEvent } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useConnection } from '@/components/plately/Connection';
import { Button, Card, FailureCard, Field, SelectField, Switch } from '@/components/plately/ui';
import PageHead from '@/components/staff/PageHead';
import { failureOf, type ApiFailure } from '@/lib/http';
import { managerApi, useBanks, useTenant } from '@/lib/manager';

// Open for orders and saving seats are switches. A refusal (no payout set up yet, say) is shown in the backend's words.
function Switches() {
  const { lost } = useConnection();
  const queryClient = useQueryClient();
  const tenant = useTenant();
  const openLabel = useId();
  const holdsLabel = useId();
  const [failure, setFailure] = useState<ApiFailure | null>(null);
  const [busy, setBusy] = useState(false);

  const change = async (action: () => Promise<unknown>) => {
    setBusy(true);
    setFailure(null);
    try {
      await action();
    } catch (error) {
      setFailure(failureOf(error, "That didn't change"));
    } finally {
      await queryClient.invalidateQueries({ queryKey: ['tenant'] });
      setBusy(false);
    }
  };

  const data = tenant.data;
  return (
    <Card className="flex flex-col gap-4">
      <h2 className="font-heading text-[22px] font-normal text-bottle">At the door</h2>
      {tenant.isError ? <FailureCard title="We couldn't load your settings" message={failureOf(tenant.error).message} /> : null}
      {data ? (
        <>
          <div className="flex items-center justify-between gap-4">
            <div>
              <div id={openLabel} className="text-base font-bold text-bottle">
                Open for orders
              </div>
              <p className="text-sm leading-snug text-quiet">
                {data.openForOrders ? 'Guests who scan a card can order.' : 'Guests who scan a card are told you are not taking orders yet.'}
              </p>
            </div>
            <Switch checked={data.openForOrders} labelledBy={openLabel} disabled={lost || busy} onChange={(next) => void change(() => managerApi.setOpen(next))} />
          </div>
          <div className="flex items-center justify-between gap-4 border-t border-dashed border-rule2 pt-4">
            <div>
              <div id={holdsLabel} className="text-base font-bold text-bottle">
                Let guests save seats for friends
              </div>
              <p className="text-sm leading-snug text-quiet">A saved seat is kept for 10 minutes.</p>
            </div>
            <Switch checked={data.holdsEnabled} labelledBy={holdsLabel} disabled={lost || busy} onChange={(next) => void change(() => managerApi.setHolds(next))} />
          </div>
        </>
      ) : null}
      {failure ? <FailureCard title={failure.title} message={failure.message} /> : null}
    </Card>
  );
}

// Changing where the money goes needs the manager's password, and the account is checked with the bank first.
function Payout() {
  const { lost } = useConnection();
  const queryClient = useQueryClient();
  const tenant = useTenant();
  const banks = useBanks();
  const [bankCode, setBankCode] = useState('');
  const [account, setAccount] = useState('');
  const [password, setPassword] = useState('');
  const [checked, setChecked] = useState<string | null>(null);
  const [problems, setProblems] = useState<Record<string, string>>({});
  const [failure, setFailure] = useState<ApiFailure | null>(null);
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);

  const edit = (set: (value: string) => void) => (value: string) => {
    set(value);
    setChecked(null);
    setSaved(false);
  };

  const check = async () => {
    const found: Record<string, string> = {};
    if (!bankCode) found.bank = 'Choose your bank.';
    if (!/^[0-9]{10}$/.test(account)) found.account = 'An account number has 10 digits.';
    setProblems(found);
    if (Object.keys(found).length > 0) return;
    setBusy(true);
    setFailure(null);
    try {
      setChecked((await managerApi.checkAccount(bankCode, account)).accountName);
    } catch (error) {
      setFailure(failureOf(error, "We couldn't check that account"));
    } finally {
      setBusy(false);
    }
  };

  const save = async (event: FormEvent) => {
    event.preventDefault();
    if (!password) {
      setProblems({ password: 'Type your password to confirm it is you.' });
      return;
    }
    setProblems({});
    setBusy(true);
    setFailure(null);
    try {
      await managerApi.changePayout({ currentPassword: password, bankCode, accountNumber: account });
      setSaved(true);
      setPassword('');
      setAccount('');
      setChecked(null);
      await queryClient.invalidateQueries({ queryKey: ['tenant'] });
    } catch (error) {
      setFailure(failureOf(error, "We couldn't change the account"));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card className="flex flex-col gap-3.5">
      <h2 className="font-heading text-[22px] font-normal text-bottle">Where your money goes</h2>
      <p className="text-[15px] leading-snug text-quiet">
        {tenant.data?.bankName ? `Now: ${tenant.data.bankName}, account ending ${tenant.data.accountLast4}.` : 'No account is set up yet.'} Every sale lands in this account, minus our small charge.
      </p>
      <form noValidate onSubmit={(event) => void save(event)} className="flex flex-col gap-3.5">
        <SelectField label="Bank" value={bankCode} onChange={(event) => edit(setBankCode)(event.target.value)} error={problems.bank}>
          <option value="">{banks.isPending ? 'Fetching the banks…' : 'Choose your bank'}</option>
          {(banks.data ?? []).map((bank) => (
            <option key={bank.code} value={bank.code}>
              {bank.name}
            </option>
          ))}
        </SelectField>
        <Field label="Account number" inputMode="numeric" maxLength={10} value={account} onChange={(event) => edit(setAccount)(event.target.value.replace(/\D/g, ''))} error={problems.account} autoComplete="off" />
        <div className="flex flex-col gap-2.5">
          <Button tone="outline" disabled={lost || busy} onClick={() => void check()}>
            Check this account
          </Button>
          {checked ? (
            <p role="status" className="animate-fade text-[17px] font-bold text-good">
              ✓ {checked}
            </p>
          ) : null}
        </div>
        {checked ? (
          <>
            <Field label="Your password" type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} error={problems.password} hint="We ask so nobody else can send your money somewhere new." />
            <Button type="submit" tone="primary" disabled={lost || busy}>
              Use this account
            </Button>
          </>
        ) : null}
      </form>
      {failure ? <FailureCard title={failure.title} message={failure.message} /> : null}
      {saved ? (
        <p role="status" className="animate-pop text-[17px] font-bold text-good">
          Done. Your money now goes to the new account, and we have emailed the owner to say so.
        </p>
      ) : null}
    </Card>
  );
}

export default function SettingsPage() {
  const tenant = useTenant();
  return (
    <main className="mx-auto flex w-full max-w-6xl flex-1 animate-rise flex-col gap-5 px-5 pb-6 pt-4">
      <PageHead title="Settings" line={tenant.data ? `${tenant.data.name} · /t/${tenant.data.slug}` : 'Your place on Plately.'} />
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Switches />
        <Payout />
      </div>
    </main>
  );
}
