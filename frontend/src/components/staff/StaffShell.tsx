'use client';

import { useEffect, useMemo, type ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import Ground from '@/components/plately/Ground';
import { Brand, Loader } from '@/components/plately/Logo';
import { ConnectionBanner, ConnectionContext } from '@/components/plately/Connection';
import { useLiveStream } from '@/hooks/useLiveStream';
import { failureOf } from '@/lib/http';
import { staffApi } from '@/lib/staff';
import { homeFor, useAuthHydrated, useAuthStore, type StaffRole } from '@/store/authStore';

// The frame around every staff screen: the signed-in check, the live stream, the header and the way out.
export default function StaffShell({ allow, children }: { allow: StaffRole[]; children: ReactNode }) {
  const router = useRouter();
  const hydrated = useAuthHydrated();
  const profile = useAuthStore((state) => state.workerProfile);
  const logout = useAuthStore((state) => state.logout);

  const allowed = profile !== null && !profile.mustChangePassword && allow.includes(profile.role);
  const stream = useLiveStream('staff', allowed);

  // Today's count doubles as the check that the sign-in is still good.
  const today = useQuery({ queryKey: ['today'], queryFn: staffApi.today, enabled: allowed, retry: false });
  const expired = today.isError && failureOf(today.error).status === 401;

  useEffect(() => {
    if (!hydrated) return;
    if (!profile) {
      router.replace('/staff/login');
    } else if (profile.mustChangePassword) {
      router.replace('/staff/new-password');
    } else if (!allow.includes(profile.role)) {
      router.replace(homeFor(profile.role));
    }
  }, [hydrated, profile, allow, router]);

  useEffect(() => {
    if (expired) {
      logout();
      router.replace('/staff/login?ended=1');
    }
  }, [expired, logout, router]);

  const connection = useMemo(() => ({ lost: stream.lost }), [stream.lost]);

  if (!hydrated || !allowed || expired) {
    return (
      <Ground>
        <Loader message="Setting the pass…" />
      </Ground>
    );
  }

  const who = [profile.businessName, profile.fullName ?? profile.username].filter(Boolean).join(' · ');

  return (
    <ConnectionContext.Provider value={connection}>
      <Ground>
        <ConnectionBanner lost={stream.lost} caughtUp={stream.caughtUp} />
        <header className="mx-auto flex w-full max-w-6xl print:hidden flex-wrap items-center justify-between gap-x-4 gap-y-2 px-5 pb-1 pt-5">
          <Brand size={28} />
          <div className="flex items-center gap-4 text-right">
            <div>
              <div className="text-sm font-bold text-cream">{who}</div>
              {today.data ? <div className="text-[13px] text-soft">Orders today: {today.data.ordersToday}</div> : null}
            </div>
            <button
              type="button"
              onClick={() => {
                logout();
                router.replace('/staff/login');
              }}
              className="min-h-[44px] rounded-full border-[1.5px] border-cream/45 px-4 text-sm font-bold text-cream hover:bg-cream/10"
            >
              Sign out
            </button>
          </div>
        </header>
        {children}
      </Ground>
    </ConnectionContext.Provider>
  );
}
