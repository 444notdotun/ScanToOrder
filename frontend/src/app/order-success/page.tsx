'use client';

import React, { Suspense, useEffect } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Loader2 } from 'lucide-react';

function OrderSuccessRedirect() {
  const router = useRouter();
  const searchParams = useSearchParams();

  useEffect(() => {
    const reference = searchParams.get('reference') || '';
    const trxref = searchParams.get('trxref') || '';
    const ref = reference || trxref;
    
    // Redirect to the unified payment-success page preserving reference
    router.replace(`/payment-success?reference=${encodeURIComponent(ref)}`);
  }, [router, searchParams]);

  return (
    <div className="min-h-screen flex flex-col items-center justify-center p-6 bg-[#F8F6F2]">
      <Loader2 className="h-8 w-8 text-brand-deep animate-spin mb-3" />
      <p className="text-stone-500 text-xs font-semibold">Redirecting to verification page...</p>
    </div>
  );
}

export default function OrderSuccessPage() {
  return (
    <Suspense fallback={
      <div className="min-h-screen flex flex-col items-center justify-center p-6 bg-[#F8F6F2]">
        <Loader2 className="h-8 w-8 text-brand-deep animate-spin mb-3" />
        <p className="text-stone-500 text-xs font-semibold">Loading redirector...</p>
      </div>
    }>
      <OrderSuccessRedirect />
    </Suspense>
  );
}
