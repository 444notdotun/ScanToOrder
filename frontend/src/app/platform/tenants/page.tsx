'use client';

import { useState } from 'react';
import { Button, FailureCard } from '@/components/plately/ui';
import PageHead, { Empty } from '@/components/staff/PageHead';
import { FAILURE_ID, useAction } from '@/hooks/useAction';
import { failureOf } from '@/lib/http';
import { platformApi, signedUp, useTenants, type TenantStatus } from '@/lib/platform';

const STATUS: Record<TenantStatus, { label: string; style: string }> = {
  PENDING_APPROVAL: { label: 'Waiting for approval', style: 'bg-mustard text-ink' },
  ACTIVE: { label: 'Live', style: 'bg-good text-white' },
  SUSPENDED: { label: 'Suspended', style: 'bg-[#7A2A18] text-white' },
  DECLINED: { label: 'Declined', style: 'bg-errbg text-errtext' },
};

export default function TenantsPage() {
  const tenants = useTenants();
  const act = useAction();
  const [result, setResult] = useState<string | null>(null);

  const run = async (key: string, action: () => Promise<{ message: string }>) => {
    setResult(null);
    let message = '';
    const worked = await act.run(key, async () => {
      message = (await action()).message;
    });
    if (worked) setResult(message);
  };

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-1 animate-rise flex-col gap-5 px-5 pb-6 pt-4">
      <PageHead title="Tenants" line="Everyone on Plately." />
      <div id={FAILURE_ID}>{act.failure ? <FailureCard title={act.failure.title} message={act.failure.message} /> : null}</div>
      {result ? (
        <p role="status" className="animate-fade text-[15px] leading-snug text-mustard">
          {result}
        </p>
      ) : null}
      {tenants.isError ? <FailureCard title="We couldn't load the tenants" message={failureOf(tenants.error).message} /> : null}
      {tenants.data && tenants.data.length === 0 ? <Empty>Nobody has signed up yet.</Empty> : null}

      <ul className="flex flex-col gap-2.5">
        {(tenants.data ?? []).map((tenant) => (
          <li key={tenant.tenantId} className="flex flex-wrap items-center gap-3 rounded-lg bg-paperlight p-3.5 text-ink shadow-[0_8px_20px_rgba(0,0,0,0.22)]">
            <div className="min-w-[220px] flex-1">
              <div className="font-bold leading-snug">
                {tenant.name} · {tenant.type === 'RESTAURANT' ? 'Restaurant' : 'Events'}
              </div>
              <div className="text-sm leading-snug text-quiet">
                {tenant.ownerName} · {tenant.email} · {signedUp(tenant.createdAt)}
              </div>
              {tenant.status === 'DECLINED' && tenant.declineReason ? <div className="text-sm font-bold leading-snug text-hand">Declined: {tenant.declineReason}</div> : null}
              {tenant.status === 'ACTIVE' ? <div className="text-sm text-quiet">Sign-in: {tenant.slug}.manager</div> : null}
            </div>
            <span className={`flex-none rounded-full px-3 py-1 text-sm font-bold ${STATUS[tenant.status].style}`}>{STATUS[tenant.status].label}</span>
            <div className="flex flex-wrap gap-2">
              {tenant.status === 'ACTIVE' ? (
                <>
                  <Button full={false} tone="outline" disabled={act.busy !== null} onClick={() => void run(tenant.tenantId, () => platformApi.suspend(tenant.tenantId))}>
                    Suspend
                  </Button>
                  <Button full={false} tone="outline" aria-label={`Send ${tenant.name} their sign-in again`} disabled={act.busy !== null} onClick={() => void run(tenant.tenantId, () => platformApi.resend(tenant.tenantId))}>
                    Resend sign-in
                  </Button>
                </>
              ) : null}
              {tenant.status === 'SUSPENDED' ? (
                <Button full={false} tone="outline" disabled={act.busy !== null} onClick={() => void run(tenant.tenantId, () => platformApi.reinstate(tenant.tenantId))}>
                  Reinstate
                </Button>
              ) : null}
            </div>
          </li>
        ))}
      </ul>
    </main>
  );
}
