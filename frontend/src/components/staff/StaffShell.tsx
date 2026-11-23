'use client';

import { useEffect, useMemo, type ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import Ground from '@/components/plately/Ground';
import { Loader } from '@/components/plately/Logo';
import StaffHeader from '@/components/staff/StaffHeader';
import { ConnectionBanner, ConnectionContext } from '@/components/plately/Connection';
import { useLiveStream } from '@/hooks/useLiveStream';
import { failureOf, http } from '@/lib/http';
import { staffApi } from '@/lib/staff';
import { homeFor, useAuthHydrated, useAuthStore, type StaffRole } from '@/store/authStore';

// The frame around every staff screen: the signed-in check, the live stream, the header and the way out.
export default function StaffShell({
  allow,
  children,
  workspace,
  nav,
}: {
  allow: StaffRole[];
  children: ReactNode;
  workspace?: 'RESTAURANT' | 'EVENT_PLANNER';
  nav?: ReactNode;
}) {
  const router = useRouter();
  const hydrated = useAuthHydrated();
  const profile = useAuthStore((state) => state.workerProfile);
  const logout = useAuthStore((state) => state.logout);

  // A manager of events belongs in /planner, a restaurant's manager in /manager: each is sent to the other's door.
  const rightWorkspace = !workspace || profile?.role !== 'MANAGER' || (profile.tenantType ?? 'RESTAURANT') === workspace;
  const allowed = profile !== null && !profile.mustChangePassword && allow.includes(profile.role) && rightWorkspace;
  // The platform owner has no business of their own: no staff stream and no day's count, but the same checks.
  const platform = profile?.role === 'PLATFORM_ADMIN';
  const stream = useLiveStream('staff', allowed && !platform);

  // Today's count doubles as the check that the sign-in is still good.
  const today = useQuery({
    queryKey: ['today', platform],
    queryFn: () => (platform ? http.get('/api/v1/platform/tenants', { params: { status: 'pending_approval' } }).then(() => null) : staffApi.today()),
    enabled: allowed,
    retry: false,
  });
  const failedWith = today.isError ? failureOf(today.error) : null;
  const expired = failedWith?.status === 401;
  // A suspended business's staff are signed out by the server with its own words; the sign-in page repeats them.
  const suspended = expired && /suspended on Plately/i.test(failedWith?.message ?? '');

  useEffect(() => {
    if (!hydrated) return;
    // A sign-in that ran out is sent to the sign-in page with its own reason by the effect below; this one must not overwrite it.
    if (!profile) {
      if (!expired) router.replace('/staff/login');
    } else if (profile.mustChangePassword) {
      router.replace('/staff/new-password');
    } else if (!allow.includes(profile.role) || !rightWorkspace) {
      router.replace(homeFor(profile));
    }
  }, [hydrated, profile, allow, rightWorkspace, router, expired]);

  useEffect(() => {
    if (expired) {
      logout();
      router.replace(suspended ? '/staff/login?ended=suspended' : '/staff/login?ended=1');
    }
  }, [expired, suspended, logout, router]);

  const connection = useMemo(() => ({ lost: stream.lost }), [stream.lost]);

  if (!hydrated || !allowed || expired) {
    return (
      <Ground faint>
        <Loader message="Setting the pass…" />
      </Ground>
    );
  }

  return (
    <ConnectionContext.Provider value={connection}>
      <Ground faint>
        <ConnectionBanner lost={stream.lost} caughtUp={stream.caughtUp} />
        <StaffHeader
          profile={profile}
          ordersToday={today.data ? today.data.ordersToday : undefined}
          nav={nav}
          onSignOut={() => {
            logout();
            router.replace('/staff/login');
          }}
        />
        {children}
      </Ground>
    </ConnectionContext.Provider>
  );
}
