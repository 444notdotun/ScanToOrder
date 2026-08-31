"use client";
import React, { useEffect, useState, Suspense } from 'react';
import Link from 'next/link';
import { CheckCircle, Loader2 } from 'lucide-react';
import { useSearchParams, useRouter } from 'next/navigation';
import { useCustomerStore } from '@/store/customerStore';
import { api } from '@/lib/api';

function PaymentSuccessContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const reference = searchParams.get('reference') || searchParams.get('trxref');
  const clearCart = useCustomerStore(state => state.clearCart);
  
  const [orderId, setOrderId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    try {
      if (typeof clearCart === 'function') clearCart();
    } catch (e) {
      console.warn(e);
    }

    if (reference) {
      api.get(`/api/v1/payments/verify/${reference}`)
        .then((res: any) => {
          const payload = res.data?.data || res.data;
          setOrderId(payload?.orderId || payload?.id || 'UNKNOWN');
        })
        .catch(err => console.error(err))
        .finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
  }, [reference, clearCart]);

  if (loading) {
    return <div className="min-h-screen bg-orange-600 flex items-center justify-center text-white"><Loader2 className="animate-spin" size={48} /></div>;
  }

  return (
    <div className="min-h-screen bg-orange-600 text-white flex flex-col items-center justify-center p-6">
      <div className="w-24 h-24 bg-white/20 rounded-full flex items-center justify-center mb-6">
        <CheckCircle size={48} className="text-white" />
      </div>
      <h1 className="text-3xl font-bold mb-2">Payment Successful!</h1>
      <p className="text-orange-100 mb-8 text-center">Your order has been sent to the kitchen.</p>
      
      <div className="bg-white text-stone-800 p-6 rounded-3xl w-full max-w-sm mb-12 flex flex-col items-center shadow-xl">
        <p className="text-sm text-stone-500 uppercase tracking-wider mb-1">Order Ref / Reference</p>
        <p className="text-xl font-black text-orange-600 mb-4">{reference || 'Confirmed'}</p>
        <div className="w-full border-t border-dashed border-stone-200 my-4"></div>
      </div>
      
      <Link href={`/session?reference=${reference}`} className="block w-full max-w-sm text-center bg-white text-orange-600 py-4 rounded-full font-bold shadow-md hover:bg-stone-50 transition-colors">
        Enter Dining Session
      </Link>
    </div>
  );
}

export default function PaymentSuccess() {
  return (
    <Suspense fallback={<div className="min-h-screen bg-orange-600 flex items-center justify-center text-white"><Loader2 className="animate-spin" size={48} /></div>}>
      <PaymentSuccessContent />
    </Suspense>
  );
}
