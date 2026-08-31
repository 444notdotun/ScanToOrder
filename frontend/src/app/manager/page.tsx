'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';

export default function ManagerRedirect() {
  const router = useRouter();
  useEffect(() => {
    router.replace('/admin/dashboard');
  }, [router]);

  return null;
}
