'use client';

import React, { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/store/authStore';
import { Loader2 } from 'lucide-react';

interface ProtectedRouteProps {
  children: React.ReactNode;
  allowedRoles?: string[];
}

export default function ProtectedRoute({ children, allowedRoles }: ProtectedRouteProps) {
  const router = useRouter();
  const { isAuthenticated, workerProfile } = useAuthStore();
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    if (mounted) {
      if (!isAuthenticated) {
        router.replace('/staff/login');
      } else if (allowedRoles && workerProfile && !allowedRoles.includes(workerProfile.role)) {
        // Redirection based on actual role to avoid unauthorized layout flashes
        if (workerProfile.role === 'MANAGER') {
          router.replace('/admin/dashboard');
        } else if (workerProfile.role === 'CHEF') {
          router.replace('/kitchen/kds');
        } else if (workerProfile.role === 'WAITER') {
          router.replace('/waiter');
        } else {
          router.replace('/');
        }
      }
    }
  }, [mounted, isAuthenticated, workerProfile, allowedRoles, router]);

  if (!mounted || !isAuthenticated) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center bg-[#F8F6F2]">
        <Loader2 className="h-8 w-8 text-brand-deep animate-spin mb-3" />
        <p className="text-stone-500 text-xs font-semibold text-center">Verifying credentials...</p>
      </div>
    );
  }

  return <>{children}</>;
}
