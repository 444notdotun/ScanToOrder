'use client';

import React, { useEffect, useState, Suspense, useRef } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import { useCustomerStore } from '@/store/customerStore';
import { customerService } from '@/services/customer.service';
import { CheckCircle2, Loader2, ArrowRight, Download } from 'lucide-react';
import html2canvas from 'html2canvas';

function PaymentSuccessContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const { sessionId } = useCustomerStore();
  
  const reference = searchParams.get('reference') || searchParams.get('trxref');
  const [status, setStatus] = useState<'loading' | 'success' | 'failed'>('loading');
  const [receipt, setReceipt] = useState<any>(null);
  const [isDownloadingImage, setIsDownloadingImage] = useState(false);
  
  const receiptRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!reference) {
      setStatus('failed');
      return;
    }

    let isMounted = true;
    const verify = async () => {
      try {
        const response = await customerService.verifyPayment(reference);
        if (!isMounted) return;

        if (response?.paymentStatus === 'SUCCESSFUL' || response?.orderStatus === 'PAID') {
          setStatus('success');
          try {
            const receiptData = await customerService.getReceipt(reference);
            if (isMounted) setReceipt(receiptData);
          } catch (err) {
            console.error('Failed to load receipt details', err);
          }
        } else {
          setStatus('failed');
        }
      } catch (err) {
        if (isMounted) setStatus('failed');
      }
    };
    verify();
    return () => { isMounted = false; };
  }, [reference]);

  const handleDownloadPdf = async () => {
    if (!receiptRef.current) return;
    setIsDownloadingImage(true);
    
    try {
      // Temporarily ensure the receipt is fully visible and not rounded for the canvas capture
      const element = receiptRef.current;
      const canvas = await html2canvas(element, {
        scale: 2, // High resolution
        backgroundColor: '#FDFBF7', // Match the receipt paper color
        useCORS: true,
      });
      
      const image = canvas.toDataURL('image/png');
      const link = document.createElement('a');
      link.href = image;
      link.download = `ScanToOrder_Receipt_${receipt?.receiptNumber || '000'}.png`;
      link.click();
    } catch (err) {
      console.error('Failed to generate receipt image', err);
    } finally {
      setIsDownloadingImage(false);
    }
  };

  const handleFinish = () => {
    if (sessionId) {
      router.push('/session');
    } else {
      router.push('/');
    }
  };

  const formatNaira = (amount: number) => {
    return new Intl.NumberFormat('en-NG', { style: 'currency', currency: 'NGN' }).format(amount);
  };

  if (status === 'loading') {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center p-6 bg-[#F8F6F2]">
        <Loader2 className="h-8 w-8 text-brand-deep animate-spin mb-3" />
        <p className="text-stone-500 text-xs font-semibold">Verifying your payment...</p>
      </div>
    );
  }

  if (status === 'failed') {
    return (
      <div className="min-h-screen bg-[#F8F6F2] p-6 flex flex-col items-center justify-center text-center">
        <div className="bg-white p-8 rounded-3xl shadow-sm max-w-sm w-full space-y-4">
          <div className="mx-auto w-12 h-12 bg-red-100 text-red-500 rounded-full flex items-center justify-center mb-2">
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path></svg>
          </div>
          <h2 className="text-xl font-black text-stone-900">Payment Failed</h2>
          <p className="text-sm text-stone-500">We couldn't verify your payment. Please try again or speak to a waiter.</p>
          <button onClick={() => router.back()} className="w-full mt-4 bg-stone-900 text-white font-bold py-3.5 rounded-2xl">
            Go Back
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#F8F6F2] relative py-10 px-4 flex flex-col justify-center overflow-hidden">
      
      {/* WALLPAPER: Faint Repeating "SCAN TO ORDER" Background for the dining hall vibe */}
      <div 
        className="absolute inset-0 pointer-events-none opacity-[0.03] z-0" 
        style={{
          backgroundImage: `url("data:image/svg+xml,%3Csvg width='200' height='200' xmlns='http://www.w3.org/2000/svg'%3E%3Ctext x='50%25' y='50%25' font-size='24' font-weight='900' font-family='sans-serif' fill='%23000' text-anchor='middle' dominant-baseline='middle' transform='rotate(-45 100 100)'%3ESCAN TO ORDER%3C/text%3E%3C/svg%3E")`,
          backgroundSize: '200px 200px',
        }}
      />

      <div className="max-w-md md:max-w-2xl lg:max-w-3xl mx-auto w-full space-y-6 relative z-10">
        
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

        {/* Visual Invoice Card (Authentic Receipt Design) */}
        {receipt && (
          <div className="flex justify-center">
            {/* Wrapper for the receipt to give it some shadow and bounds in the UI */}
            <div className="w-full max-w-sm drop-shadow-md">
              
              {/* Top Jagged Edge (CSS trick) */}
              <div className="h-3 w-full bg-[#FDFBF7]" style={{ backgroundImage: 'radial-gradient(circle at 5px 0, transparent 5px, #FDFBF7 6px)', backgroundSize: '10px 10px', backgroundRepeat: 'repeat-x' }}></div>
              
              {/* Actual Receipt Capture Area */}
              <div 
                ref={receiptRef}
                className="bg-[#FDFBF7] px-6 py-8 relative overflow-hidden text-stone-900"
              >
                {/* Watermark inside the receipt itself */}
                <div 
                  className="absolute inset-0 pointer-events-none opacity-[0.04] z-0" 
                  style={{
                    backgroundImage: `url("data:image/svg+xml,%3Csvg width='150' height='150' xmlns='http://www.w3.org/2000/svg'%3E%3Ctext x='50%25' y='50%25' font-size='18' font-weight='900' font-family='sans-serif' fill='%23000' text-anchor='middle' dominant-baseline='middle' transform='rotate(-30 75 75)'%3ESCAN TO ORDER%3C/text%3E%3C/svg%3E")`,
                    backgroundSize: '150px 150px',
                  }}
                />

                <div className="relative z-10 space-y-6">
                  
                  {/* Receipt Header */}
                  <div className="text-center space-y-1">
                    <h1 className="text-2xl font-black tracking-tight text-brand-deep">SCAN TO ORDER</h1>
                    <p className="text-[10px] font-bold text-stone-500 uppercase tracking-widest">Digital Dining Receipt</p>
                  </div>

                  {/* Divider */}
                  <div className="border-b-2 border-dashed border-stone-300 w-full"></div>

                  {/* Meta Details */}
                  <div className="grid grid-cols-2 gap-4 text-xs font-mono">
                    <div>
                      <span className="text-[9px] text-stone-400 font-bold uppercase tracking-wider block font-sans">Receipt No</span>
                      <p className="font-bold">{receipt.receiptNumber}</p>
                    </div>
                    <div className="text-right">
                      <span className="text-[9px] text-stone-400 font-bold uppercase tracking-wider block font-sans">Table / Seat</span>
                      <p className="font-bold text-brand-deep">{receipt.tableNumber} - {receipt.seatLabel}</p>
                    </div>
                  </div>

                  {/* Divider */}
                  <div className="border-b-2 border-dashed border-stone-300 w-full"></div>

                  {/* Items List */}
                  <div className="space-y-3 font-mono text-xs">
                    {receipt.items && receipt.items.map((item: any, idx: number) => (
                      <div key={idx} className="flex flex-col">
                        <div className="flex justify-between items-start">
                          <span className="font-bold pr-2">{item.quantity}x {item.itemName}</span>
                          <span className="font-bold">{formatNaira(item.totalPrice)}</span>
                        </div>
                        {item.specialInstructions && (
                          <span className="text-[10px] italic text-stone-500 font-sans mt-0.5">
                            * {item.specialInstructions}
                          </span>
                        )}
                      </div>
                    ))}
                  </div>

                  {/* Divider */}
                  <div className="border-b-2 border-dashed border-stone-300 w-full"></div>

                  {/* Totals */}
                  <div className="space-y-2 font-mono text-sm">
                    <div className="flex justify-between text-stone-500">
                      <span>Subtotal</span>
                      <span>{formatNaira(receipt.subtotal)}</span>
                    </div>
                    <div className="flex justify-between font-black text-base pt-1">
                      <span>TOTAL PAID</span>
                      <span className="text-brand-deep">{formatNaira(receipt.totalPaid)}</span>
                    </div>
                  </div>

                  {/* Footer details */}
                  <div className="pt-6 text-center space-y-1">
                    <p className="text-[10px] font-mono text-stone-400">Ref: {receipt.paymentReference}</p>
                    <p className="text-[10px] font-mono text-stone-400">
                      {receipt.paymentDate ? new Date(receipt.paymentDate).toLocaleString() : new Date().toLocaleString()}
                    </p>
                    <p className="text-xs font-bold text-stone-900 mt-4 font-sans">Thank you for dining with us!</p>
                  </div>
                  
                </div>
              </div>

              {/* Bottom Jagged Edge */}
              <div className="h-3 w-full bg-[#FDFBF7] rotate-180" style={{ backgroundImage: 'radial-gradient(circle at 5px 0, transparent 5px, #FDFBF7 6px)', backgroundSize: '10px 10px', backgroundRepeat: 'repeat-x' }}></div>
            </div>
          </div>
        )}

        {/* Action Buttons */}
        <div className="space-y-3 pt-2">
          <button 
            onClick={handleDownloadPdf}
            disabled={isDownloadingImage}
            className="w-full bg-white border-2 border-stone-200 hover:border-brand-deep hover:text-brand-deep text-stone-600 font-bold py-4 rounded-2xl flex items-center justify-center gap-2 text-sm transition cursor-pointer shadow-sm"
          >
            {isDownloadingImage ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />}
            {isDownloadingImage ? 'Generating Receipt...' : 'Download Digital Receipt'}
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
