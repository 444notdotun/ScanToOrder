'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';

export default function KitchenRedirect() {
  const router = useRouter();
  useEffect(() => {
    router.replace('/kitchen/kds');
  }, [router]);

  return null;
}
