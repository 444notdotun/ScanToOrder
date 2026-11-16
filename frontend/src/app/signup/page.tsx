'use client';

import Link from 'next/link';
import { useState, type FormEvent } from 'react';
import Ground from '@/components/plately/Ground';
import { Brand, Watermark } from '@/components/plately/Logo';
import { Button, Card, FailureCard, Field, SelectField } from '@/components/plately/ui';
import { failureOf, type ApiFailure } from '@/lib/http';
import { managerApi, useBanks } from '@/lib/manager';
import { platformApi, type SignupForm } from '@/lib/platform';

type Kind = SignupForm['type'];

const KINDS: Array<{ id: Kind; label: string; sub: string }> = [
  { id: 'RESTAURANT', label: 'A restaurant', sub: 'Guests scan, sit, order and pay' },
  { id: 'EVENT_PLANNER', label: 'Events', sub: 'Weddings, parties, a guest list and codes' },
];

// Where someone who runs a place asks to join. We take one look, then email them their sign-in.
export default function SignupPage() {
  const banks = useBanks();
  const [kind, setKind] = useState<Kind>('RESTAURANT');
  const [business, setBusiness] = useState('');
  const [owner, setOwner] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [bankCode, setBankCode] = useState('');
  const [account, setAccount] = useState('');
  const [accountName, setAccountName] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [failure, setFailure] = useState<ApiFailure | null>(null);
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState<{ email: string; message: string } | null>(null);

  const restaurant = kind === 'RESTAURANT';

  // A changed bank or number means the name shown no longer belongs to it.
  const changeBank = (value: string) => {
    setBankCode(value);
    setAccountName(null);
  };
  const changeAccount = (value: string) => {
    setAccount(value.replace(/\D/g, ''));
    setAccountName(null);
  };

  const check = async () => {
    const found: Record<string, string> = {};
    if (!bankCode) found.bank = 'Choose your bank.';
    if (!/^[0-9]{10}$/.test(account)) found.account = 'An account number has 10 digits.';
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    setBusy(true);
    setFailure(null);
    try {
      setAccountName((await managerApi.checkAccount(bankCode, account)).accountName);
    } catch (error) {
      setFailure(failureOf(error, "We couldn't check that account"));
    } finally {
      setBusy(false);
    }
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const found: Record<string, string> = {};
    if (!business.trim()) found.business = 'Tell us the name of your place.';
    if (!owner.trim()) found.owner = 'Tell us your name.';
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) found.email = "That email doesn't look right.";
    if (!/^[0-9+ ()-]{7,20}$/.test(phone.trim())) found.phone = "That phone number doesn't look right.";
    if (restaurant && !accountName) found.account = 'Choose your bank, add the account number and check the name.';
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    setBusy(true);
    setFailure(null);
    try {
      const done = await platformApi.signup({
        type: kind,
        businessName: business.trim(),
        ownerName: owner.trim(),
        email: email.trim(),
        phone: phone.trim(),
        ...(restaurant ? { bankCode, accountNumber: account, accountName: accountName ?? undefined } : {}),
      });
      setSent({ email: email.trim(), message: done.message });
    } catch (error) {
      setFailure(failureOf(error, "We couldn't send your details"));
    } finally {
      setBusy(false);
    }
  };

  if (sent) {
    return (
      <Ground>
        <Watermark className="-bottom-[70px] -right-24" />
        <header className="relative mx-auto flex w-full max-w-md items-center px-5 pt-5 md:max-w-xl">
          <Brand />
        </header>
        <main className="relative mx-auto flex w-full max-w-md flex-1 animate-rise flex-col justify-center gap-4 px-5 pb-8 md:max-w-xl">
          <h1 className="font-heading text-[38px] font-normal leading-[1.1]">We have your request.</h1>
          <p className="text-[17px] leading-snug text-soft">Sit tight. We will email you at {sent.email} the moment it is looked at.</p>
          <Card className="flex flex-col gap-2">
            <p className="text-[17px] leading-snug text-ink">If it is approved, the email holds your username and a temporary password. Sign in with them, then choose a password of your own.</p>
            <p className="text-[17px] leading-snug text-ink">If something needs fixing, the email says what, and you can send this form again.</p>
          </Card>
          <Link href="/staff/login" className="flex min-h-[52px] items-center justify-center rounded-full bg-cream px-5 text-base font-bold text-bottle">
            I have my sign-in
          </Link>
        </main>
      </Ground>
    );
  }

  return (
    <Ground>
      <header className="mx-auto flex w-full max-w-md items-center justify-between px-5 pt-5 md:max-w-xl">
        <Brand />
      </header>
      <main className="mx-auto flex w-full max-w-md flex-1 animate-rise flex-col gap-5 px-5 pb-8 pt-6 md:max-w-xl">
        <div>
          <h1 className="font-heading text-[38px] font-normal leading-[1.1]">Bring Plately to your place.</h1>
          <p className="mt-2 text-[17px] leading-snug text-soft">Guests scan, sit, order and pay. You cook. Tell us about your place and we take one look before your first guest orders.</p>
        </div>

        <Card>
          <form noValidate onSubmit={(event) => void submit(event)} className="flex flex-col gap-4">
            <div>
              <div id="kind-label" className="mb-1.5 text-sm font-bold text-bottle">
                What do you run?
              </div>
              <div role="group" aria-labelledby="kind-label" className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                {KINDS.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    aria-pressed={kind === item.id}
                    onClick={() => {
                      setKind(item.id);
                      setErrors({});
                    }}
                    className={`flex min-h-[64px] flex-col items-start justify-center rounded-2xl border-[1.5px] px-4 py-2 text-left ${
                      kind === item.id ? 'border-bottle bg-bottle text-cream' : 'border-rule2 text-bottle'
                    }`}
                  >
                    <span className="text-base font-bold">{item.label}</span>
                    <span className={`text-sm ${kind === item.id ? 'text-soft' : 'text-quiet'}`}>{item.sub}</span>
                  </button>
                ))}
              </div>
            </div>

            <Field label={restaurant ? 'Name of your restaurant' : 'Name of your business'} value={business} onChange={(e) => setBusiness(e.target.value)} error={errors.business} placeholder="The Corner Table" autoComplete="organization" />
            <Field label="Your name" value={owner} onChange={(e) => setOwner(e.target.value)} error={errors.owner} autoComplete="name" />
            <Field label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} error={errors.email} hint="Your sign-in is emailed here once you are approved." autoComplete="email" />
            <Field label="Phone number" type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} error={errors.phone} autoComplete="tel" />

            {restaurant ? (
              <fieldset className="flex flex-col gap-3.5 rounded-2xl border border-dashed border-rule2 p-4">
                <legend className="px-1 font-heading text-lg text-bottle">Where should your money go?</legend>
                <p className="text-sm leading-snug text-quiet">Every sale lands in this account, minus our small charge.</p>
                <SelectField label="Bank" value={bankCode} onChange={(e) => changeBank(e.target.value)} error={errors.bank}>
                  <option value="">{banks.isPending ? 'Fetching the banks…' : 'Choose your bank'}</option>
                  {(banks.data ?? []).map((bank) => (
                    <option key={bank.code} value={bank.code}>
                      {bank.name}
                    </option>
                  ))}
                </SelectField>
                <Field label="Account number" inputMode="numeric" maxLength={10} value={account} onChange={(e) => changeAccount(e.target.value)} autoComplete="off" />
                <Button tone="outline" disabled={busy} onClick={() => void check()}>
                  Check this account
                </Button>
                {accountName ? (
                  <p role="status" className="animate-fade text-[17px] font-bold text-good">
                    ✓ {accountName}
                  </p>
                ) : errors.account ? (
                  <p className="text-sm font-bold text-hand">{errors.account}</p>
                ) : null}
              </fieldset>
            ) : (
              <p className="text-sm leading-snug text-quiet">Events need no bank account: you pay a fee per event, and guests eat on their codes.</p>
            )}

            {failure ? <FailureCard title={failure.title} message={failure.message} /> : null}
            <Button type="submit" tone="primary" big disabled={busy}>
              {busy ? 'Sending…' : 'Create my account'}
            </Button>
          </form>
        </Card>

        <p className="text-center text-sm text-soft">
          Already approved?{' '}
          <Link href="/staff/login" className="underline underline-offset-4">
            Sign in
          </Link>
        </p>
      </main>
    </Ground>
  );
}
