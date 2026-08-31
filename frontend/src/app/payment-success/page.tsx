'use client';

import React, { Suspense, useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { customerService } from '@/services/customer.service';
import { useCustomerStore } from '@/store/customerStore';
import { 
  CheckCircle2, Loader2, AlertCircle, ArrowRight, Download, FileText, 
  MapPin, User, Receipt, DollarSign 
} from 'lucide-react';

function PaymentSuccessContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const reference = searchParams.get('reference') || '';
  const clearSeat = useCustomerStore((state) => state.clearSeat);

  const [loading, setLoading] = useState(true);
  const [status, setStatus] = useState<'verifying' | 'successful' | 'failed'>('verifying');
  const [receipt, setReceipt] = useState<any>(null);
  const [errorMsg, setErrorMsg] = useState<string>('');

  useEffect(() => {
    if (!reference) {
      setStatus('failed');
      setErrorMsg('No payment reference found in URL.');
      setLoading(false);
      return;
    }

    let intervalId: any;

    const checkPayment = () => {
      customerService.verifyPayment(reference)
        .then((res: any) => {
          const payload = res?.data?.data || res?.data || res;
          const currentStatus = payload?.status || payload?.paymentStatus;

          if (['SUCCESS', 'SUCCESSFUL', 'PAID'].includes(currentStatus)) {
            clearInterval(intervalId);
            setStatus('successful');
            
            customerService.getReceipt(reference)
              .then((receiptRes: any) => {
                const recData = receiptRes?.data?.data || receiptRes?.data || receiptRes;
                setReceipt(recData);
              })
              .catch(err => console.error("Failed to fetch receipt:", err))
              .finally(() => {
                setLoading(false);
              });
          } else if (currentStatus === 'FAILED' || currentStatus === 'ABANDONED') {
            clearInterval(intervalId);
            setStatus('failed');
            setErrorMsg('The payment transaction failed.');
            setLoading(false);
          }
        })
        .catch((err) => {
          // Fail safely or keep polling
          console.error("Verification poll error: ", err);
        });
    };

    // Check immediately
    checkPayment();

    // Poll every 2 seconds
    intervalId = setInterval(checkPayment, 2000);

    return () => {
      if (intervalId) clearInterval(intervalId);
    };
  }, [reference]);

  const handleDownloadCsv = () => {
    if (reference) {
      customerService.downloadReceiptCsv(reference);
    }
  };

  const handleFinish = () => {
    // clearSeat(); // Do not clear seat since they are staying for the session!
    router.push(`/session?reference=${reference}`);
  };

  const formatNaira = (amount: number) => {
    return '₦' + Number(amount).toLocaleString('en-NG');
  };

  if (loading) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center p-6 bg-[#F8F6F2]">
        <Loader2 className="h-10 w-10 text-brand-deep animate-spin mb-4" />
        <h2 className="text-lg font-black text-stone-900">Verifying Payment</h2>
        <p className="text-stone-500 text-xs mt-1">Please wait while we confirm your transaction with Paystack...</p>
        {reference && (
          <span className="text-[10px] text-stone-400 bg-stone-100 px-2.5 py-1 rounded-md mt-3 font-mono">
            Ref: {reference}
          </span>
        )}
      </div>
    );
  }

  if (status === 'failed') {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center p-6 bg-[#F8F6F2] text-center">
        <div className="p-3 bg-red-100 text-red-600 rounded-full mb-4">
          <AlertCircle className="h-10 w-10" />
        </div>
        <h2 className="text-xl font-black text-stone-900">Payment Failed</h2>
        <p className="text-stone-600 text-sm max-w-sm md:max-w-md mx-auto mt-2">
          {errorMsg || 'We were unable to complete verification of your transaction reference.'}
        </p>
        <div className="flex flex-col gap-3 w-full max-w-xs mt-8">
          <button 
            onClick={() => router.push('/')}
            className="w-full bg-brand-deep hover:bg-brand-accent text-white font-bold py-3.5 rounded-xl text-sm transition"
          >
            Return to Scanning
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-customer-food py-10 px-4 flex flex-col justify-center">
      <div className="max-w-md md:max-w-2xl lg:max-w-3xl mx-auto w-full space-y-6">
        
        {/* Success Header Card */}
        <div className="bg-white rounded-3xl p-6 border border-stone-100 shadow-2xs text-center space-y-3">
          <div className="inline-flex p-3 bg-emerald-100 text-emerald-600 rounded-full">
            <CheckCircle2 className="h-8 w-8" />
          </div>
          <h2 className="text-2xl font-black text-stone-950">Payment Successful</h2>
          <p className="text-xs text-stone-500 max-w-xs mx-auto">
            Thank you! Your transaction has been verified. Your order has been dispatched to the kitchen.
          </p>
        </div>

        {/* Visual Invoice Card */}
        {receipt && (
          <div className="bg-white rounded-3xl border border-stone-100 shadow-2xs overflow-hidden">
            {/* Ticket Top */}
            <div className="p-5 border-b border-dashed border-stone-100 bg-stone-50/50 flex justify-between items-center text-xs">
              <div className="space-y-0.5">
                <span className="text-[10px] text-stone-400 font-extrabold uppercase tracking-widest">Receipt No</span>
                <p className="font-bold text-stone-900">{receipt.receiptNumber}</p>
              </div>
              <div className="text-right space-y-0.5">
                <span className="text-[10px] text-stone-400 font-extrabold uppercase tracking-widest">Table / Seat</span>
                <p className="font-bold text-brand-deep">{receipt.tableNumber} &bull; {receipt.seatLabel}</p>
              </div>
            </div>

            {/* Receipt Items */}
            <div className="p-5 space-y-4">
              <span className="text-[10px] text-stone-400 font-black uppercase tracking-wider block">Items Summary</span>
              <div className="divide-y divide-stone-100 text-xs">
                {receipt.items && receipt.items.map((item: any, idx: number) => (
                  <div key={idx} className="py-2.5 flex justify-between items-start">
                    <div>
                      <span className="font-bold text-stone-900">{item.itemName}</span>
                      <span className="text-stone-400 ml-1.5 font-semibold">&times; {item.quantity}</span>
                      {item.specialInstructions && (
                        <p className="text-[10px] text-brand-accent italic mt-0.5">
                          &ldquo;{item.specialInstructions}&rdquo;
                        </p>
                      )}
                    </div>
                    <span className="font-extrabold text-stone-700">
                      {formatNaira(item.totalPrice)}
                    </span>
                  </div>
                ))}
              </div>

              {/* Totals Section */}
              <div className="pt-4 border-t border-stone-100 space-y-2 text-xs">
                <div className="flex justify-between text-stone-500 font-bold">
                  <span>Subtotal</span>
                  <span>{formatNaira(receipt.subtotal)}</span>
                </div>
                <div className="flex justify-between items-center text-stone-900 font-black text-sm pt-1">
                  <span>Total Paid</span>
                  <span className="text-brand-deep">{formatNaira(receipt.totalPaid)}</span>
                </div>
              </div>
            </div>

            {/* Ticket Footer details */}
            <div className="px-5 py-4 bg-stone-50/30 border-t border-stone-100 flex flex-col gap-1.5 text-[10px] text-stone-400 font-semibold">
              <div className="flex justify-between">
                <span>Payment Ref:</span>
                <span className="font-mono text-stone-600">{receipt.paymentReference}</span>
              </div>
              <div className="flex justify-between">
                <span>Date / Time:</span>
                <span className="text-stone-600">
                  {receipt.paymentDate ? new Date(receipt.paymentDate).toLocaleString() : new Date().toLocaleString()}
                </span>
              </div>
            </div>
          </div>
        )}

        {/* Action Buttons */}
        <div className="space-y-3 pt-2">
          <button 
            onClick={handleDownloadCsv}
            className="w-full border-2 border-dashed border-stone-300 hover:border-brand-deep hover:text-brand-deep text-stone-600 font-bold py-3.5 rounded-2xl flex items-center justify-center gap-2 text-xs transition cursor-pointer"
          >
            <Download className="h-4 w-4" />
            Download CSV Receipt
          </button>

          <button 
            onClick={handleFinish}
            className="w-full bg-brand-deep hover:bg-brand-accent text-white font-bold py-4 rounded-2xl flex items-center justify-center gap-2 text-sm shadow-sm transition cursor-pointer"
          >
            Enter Active Session Hub
            <ArrowRight className="h-4 w-4" />
          </button>
        </div>

      </div>
    </div>
  );
}

export default function PaymentSuccessPage() {
  return (
    <Suspense fallback={
      <div className="min-h-screen flex flex-col items-center justify-center p-6 bg-[#F8F6F2]">
        <Loader2 className="h-8 w-8 text-brand-deep animate-spin mb-3" />
        <p className="text-stone-500 text-xs font-semibold">Loading Page...</p>
      </div>
    }>
      <PaymentSuccessContent />
    </Suspense>
  );
}
