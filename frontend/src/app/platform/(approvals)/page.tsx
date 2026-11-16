'use client';

import { useState } from 'react';
import { Button, Card, FailureCard, Field, Sheet } from '@/components/plately/ui';
import PageHead, { Empty } from '@/components/staff/PageHead';
import { FAILURE_ID, useAction } from '@/hooks/useAction';
import { failureOf } from '@/lib/http';
import { platformApi, sent, SUGGESTED, useTenants, type Tenant } from '@/lib/platform';

export default function ApprovalsPage() {
  const waiting = useTenants('pending_approval');
  const act = useAction();
  const [declining, setDeclining] = useState<Tenant | null>(null);
  const [reason, setReason] = useState('');
  const [problem, setProblem] = useState<string | null>(null);
  const [result, setResult] = useState<string | null>(null);
  const list = waiting.data ?? [];

  const close = () => {
    setDeclining(null);
    setReason('');
    setProblem(null);
    act.clear();
  };

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-1 animate-rise flex-col gap-5 px-5 pb-6 pt-4">
      <PageHead
        title="Plately, the whole platform"
        line={list.length > 0 ? `${list.length} ${list.length === 1 ? 'business is' : 'businesses are'} waiting for you.` : 'Nobody is waiting on you.'}
      />

      <div id={FAILURE_ID}>{act.failure && declining === null ? <FailureCard title={act.failure.title} message={act.failure.message} /> : null}</div>
      {result ? (
        <p role="status" className="animate-fade text-[15px] leading-snug text-mustard">
          {result}
        </p>
      ) : null}

      <section aria-label="Waiting for your yes" className="flex flex-col gap-3">
        <h2 className="font-heading text-[26px] font-normal">Waiting for your yes</h2>
        {waiting.isError ? <FailureCard title="We couldn't load the applications" message={failureOf(waiting.error).message} /> : null}
        {waiting.data && list.length === 0 ? <Empty>Nobody is waiting. New businesses land here the moment they send their details.</Empty> : null}
        <ul className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          {list.map((tenant) => (
            <li key={tenant.tenantId}>
              <Card className="flex flex-col gap-2 !p-4">
                <div className="flex items-start justify-between gap-3">
                  <h3 className="font-heading text-[24px] font-normal leading-tight text-bottle">{tenant.name}</h3>
                  <span className="flex-none rounded-full bg-mustard px-3 py-1 text-sm font-bold text-ink">{tenant.type === 'RESTAURANT' ? 'Restaurant' : 'Events'}</span>
                </div>
                <div className="text-[15px] leading-snug text-quiet">
                  {tenant.ownerName} · {tenant.email} · {tenant.phone}
                </div>
                <div className="text-sm text-quiet">
                  {tenant.type === 'RESTAURANT' ? 'Payout account checked with the bank when they sent it' : 'No bank needed'} · {sent(tenant.createdAt)}
                </div>
                <div className="mt-1 flex flex-col gap-2 sm:flex-row">
                  <Button
                    big
                    tone="primary"
                    disabled={act.busy !== null}
                    onClick={async () => {
                      setResult(null);
                      let message = '';
                      const worked = await act.run(tenant.tenantId, async () => {
                        message = (await platformApi.approve(tenant.tenantId)).message;
                      });
                      if (worked) setResult(message);
                    }}
                  >
                    Approve
                  </Button>
                  <Button big tone="outline" disabled={act.busy !== null} onClick={() => setDeclining(tenant)}>
                    Decline
                  </Button>
                </div>
              </Card>
            </li>
          ))}
        </ul>
      </section>

      <Sheet open={declining !== null} onClose={close} label="Decline this application">
        <h2 className="font-heading text-[28px] font-normal text-bottle">Decline {declining?.name}?</h2>
        <p className="mb-3 mt-2 text-[15px] leading-snug text-quiet">{declining?.name} will not be created. They get your reason by email and can send the form again.</p>
        <div className="mb-3 flex flex-col gap-2">
          {SUGGESTED.map((line) => (
            <button
              key={line}
              type="button"
              aria-pressed={reason === line}
              onClick={() => setReason(line)}
              className={`min-h-[48px] rounded-2xl border-[1.5px] px-4 py-2 text-left text-[15px] font-bold ${reason === line ? 'border-bottle bg-bottle text-cream' : 'border-rule2 text-bottle'}`}
            >
              {line}
            </button>
          ))}
        </div>
        <Field label="Or say it in your own words" value={reason} onChange={(e) => setReason(e.target.value)} error={problem ?? undefined} maxLength={500} />
        {act.failure ? <FailureCard className="mt-3" title={act.failure.title} message={act.failure.message} /> : null}
        <div className="mt-4 flex flex-col gap-2.5">
          <Button
            big
            tone="primary"
            disabled={act.busy !== null}
            onClick={async () => {
              if (!reason.trim()) {
                setProblem('Say why, so they know what to fix.');
                return;
              }
              if (declining && (await act.run(declining.tenantId, () => platformApi.decline(declining.tenantId, reason)))) {
                setResult(`${declining.name} declined with a reason. No manager account was created.`);
                close();
              }
            }}
          >
            Decline with this reason
          </Button>
          <Button big tone="outline" onClick={close}>
            Never mind
          </Button>
        </div>
      </Sheet>
    </main>
  );
}
