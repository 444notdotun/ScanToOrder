'use client';

import { useEffect, type ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import Ground from '@/components/plately/Ground';
import { Loader } from '@/components/plately/Logo';
import { homeFor, useAuthHydrated, useAuthStore, type StaffRole } from '@/store/authStore';

// Keeps a screen to the roles that may see it. Anyone else is sent to their own start, or to sign in.
export default function ProtectedRoute({ children, allowedRoles }: { children: ReactNode; allowedRoles?: StaffRole[] }) {
  const router = useRouter();
  const hydrated = useAuthHydrated();
  const profile = useAuthStore((state) => state.workerProfile);
  const allowed = profile !== null && !profile.mustChangePassword && (!allowedRoles || allowedRoles.includes(profile.role));

  useEffect(() => {
    if (!hydrated) return;
    if (!profile) router.replace('/staff/login');
    else if (profile.mustChangePassword) router.replace('/staff/new-password');
    else if (!allowed) router.replace(homeFor(profile.role));
  }, [hydrated, profile, allowed, router]);

  if (!hydrated || !allowed) {
    return (
      <Ground>
        <Loader message="Checking it's you…" />
      </Ground>
    );
  }
  return <>{children}</>;
}
