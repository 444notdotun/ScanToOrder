
'use client';

import React, { useEffect, useState, Suspense, useRef } from 'react';
import html2canvas from 'html2canvas';
import { useRouter, useSearchParams } from 'next/navigation';
import { useQuery, useMutation } from '@tanstack/react-query';
import { Clock, Bell, Receipt, CheckCircle2, Flame, ChefHat, ArrowRight, Download, Loader2 } from 'lucide-react';
import { api } from '@/lib/api';
import { customerService } from '@/services/customer.service';
import { useCustomerStore } from '@/store/customerStore';
import { toast } from 'sonner';

function LiveTimer({ startTime }: { startTime: number }) {
  const [elapsed, setElapsed] = useState('');
  useEffect(() => {
    const interval = setInterval(() => {
      const diff = Date.now() - startTime;
      const m = Math.floor(diff / 60000);
      const s = Math.floor((diff % 60000) / 1000);
      setElapsed(`${m}m ${s}s`);
    }, 1000);
    return () => clearInterval(interval);
  }, [startTime]);
  return <span>{elapsed || '0m 0s'}</span>;
}

const formatNaira = (amount: number) => '₦' + Number(amount).toLocaleString('en-NG');

function OrderTicket({ reference }: { reference: string }) {
  const { data: receipt, isLoading: receiptLoading } = useQuery({
    queryKey: ['receipt', reference],
    queryFn: async () => {
      const res: any = await api.get(`/api/v1/receipts/${reference}`);
      return res?.data?.data || res?.data || null;
    }
  });

  const orderId = receipt?.orderId;
  const { data: orderStatus } = useQuery({
    queryKey: ['orderStatus', orderId],
    queryFn: async () => {
      if (!orderId) return null;
      const res: any = await api.get(`/api/v1/orders/${orderId}/status`);
      return res?.data?.data || res?.data || 'PAID';
    },
    enabled: !!orderId,
    refetchInterval: 3000
  });

  const receiptRef = useRef<HTMLDivElement>(null);
  const [isDownloadingImage, setIsDownloadingImage] = useState(false);

  const handleDownloadPdf = async () => {
    if (!receiptRef.current) return;
    setIsDownloadingImage(true);
    
    try {
      const element = receiptRef.current;
      const canvas = await html2canvas(element, {
        scale: 2,
        backgroundColor: '#FDFBF7',
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

  if (receiptLoading) {
    return <div className="h-64 bg-stone-200/50 rounded-3xl animate-pulse mb-6" />;
  }
  if (!receipt) return null;

  const renderStepper = (status: string) => {
    const stages = ['PAID', 'PREPARING', 'READY', 'DELIVERED'];
    let currentIndex = stages.indexOf(status);
    if (currentIndex === -1) currentIndex = 0;

    const getStageText = () => {
      switch(stages[currentIndex]) {
        case 'PAID': return 'Order received. Waiting for kitchen...';
        case 'PREPARING': return 'Chefs are preparing your dish 🔥';
        case 'READY': return 'Ready for serving! 🍽️';
        case 'DELIVERED': return 'Delivered! Enjoy your meal ✓';
        default: return 'Processing...';
      }
    };

    return (
      <div className="mt-2">
        <p className="text-xs font-bold text-brand-deep mb-4 text-center">{getStageText()}</p>
        <div className="flex items-center justify-between relative px-2">
          <div className="absolute left-4 right-4 top-1/2 -translate-y-1/2 h-1 bg-stone-200 -z-10 rounded-full" />
          <div 
            className="absolute left-4 top-1/2 -translate-y-1/2 h-1 bg-brand-deep -z-10 rounded-full transition-all duration-700 ease-in-out" 
            style={{ width: `calc(${(currentIndex / 3) * 100}% - 2rem)` }}
          />
          {[
            { id: 'PAID', icon: CheckCircle2, label: 'Paid' },
            { id: 'PREPARING', icon: Flame, label: 'Cooking' },
            { id: 'READY', icon: ChefHat, label: 'Ready' },
            { id: 'DELIVERED', icon: CheckCircle2, label: 'Served' }
          ].map((stage, idx) => {
            const isCompleted = idx <= currentIndex;
            const Icon = stage.icon;
            return (
              <div key={stage.id} className="relative flex flex-col items-center gap-1.5 bg-white">
                <div className={`w-8 h-8 rounded-full flex items-center justify-center transition-all duration-500 ${isCompleted ? 'bg-brand-deep text-white shadow-md scale-110' : 'bg-stone-100 text-stone-400'}`}>
                  <Icon className="w-4 h-4" />
                </div>
                <span className={`text-[9px] font-black uppercase tracking-wider absolute -bottom-5 ${isCompleted ? 'text-brand-deep' : 'text-stone-400'}`}>{stage.label}</span>
              </div>
            );
          })}
        </div>
      </div>
    );
  };

  return (
    <div className="w-full flex flex-col h-full">
      <div className="bg-white rounded-3xl p-6 border border-stone-100 shadow-sm mb-4 pb-10">
        <h2 className="text-sm font-black text-stone-800 uppercase tracking-widest mb-6 text-center">Live Order Status</h2>
        {renderStepper(orderStatus || 'PAID')}
      </div>

      <div className="flex justify-center">
        <div className="w-full max-w-sm drop-shadow-md">
          {/* Top Jagged Edge */}
          <div className="h-3 w-full bg-[#FDFBF7]" style={{ backgroundImage: 'radial-gradient(circle at 5px 0, transparent 5px, #FDFBF7 6px)', backgroundSize: '10px 10px', backgroundRepeat: 'repeat-x' }}></div>
          
          {/* Actual Receipt Capture Area */}
          <div 
            ref={receiptRef}
            className="bg-[#FDFBF7] px-6 py-8 relative overflow-hidden text-stone-900"
          >
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
                  <p className="font-bold">{receipt.receiptNumber || orderId?.substring(0,6)}</p>
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

      <div className="mt-4 flex justify-center">
        <button 
          onClick={handleDownloadPdf}
          disabled={isDownloadingImage}
          className="w-full max-w-sm bg-white border-2 border-stone-200 hover:border-brand-deep hover:text-brand-deep text-stone-600 font-bold py-3.5 rounded-2xl flex items-center justify-center gap-2 text-sm transition cursor-pointer shadow-sm"
        >
          {isDownloadingImage ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />}
          {isDownloadingImage ? 'Generating Receipt...' : 'Download Digital Receipt'}
        </button>
      </div>
    </div>
  );
}

function DiningHallContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const urlReference = searchParams.get('reference');
  const [mounted, setMounted] = useState(false);
  const [sessionStartTime] = useState<number>(Date.now());

  useEffect(() => {
    setMounted(true);
  }, []);

  const { data: authSession, isLoading: authLoading } = useQuery({
    queryKey: ['authSession'],
    queryFn: async () => {
      const res: any = await api.get('/api/v1/sessions/me');
      return res?.data?.data || null;
    },
    retry: false
  });

  // Fetch array of payment references for this session
  const { data: sessionReferences = [], isLoading: refsLoading } = useQuery({
    queryKey: ['sessionReferences'],
    queryFn: async () => {
      const res: any = await api.get('/api/v1/sessions/me/references');
      return res?.data?.data || [];
    },
    refetchInterval: 10000 // Refresh list every 10s in case they order on another device
  });

  const requestWaiterMutation = useMutation({
    mutationFn: () => customerService.createServiceCall({
      requestType: 'ASSISTANCE' as any,
      note: 'Customer requested waiter from Dining Hall'
    }),
    onSuccess: () => toast.success('Waiter has been notified and is on the way!'),
    onError: () => toast.error('Failed to request waiter. Please try again.')
  });
  const closeSessionMutation = useMutation({
    mutationFn: async () => {
      if (!authSession?.sessionId) throw new Error("No session ID");
      return api.post(`/api/v1/sessions/${authSession.sessionId}/close`);
    },
    onSuccess: () => {
      toast.success('Session ended. Thank you for dining with us!');
      
      // Nuclear wipe of all browser storage to ensure a perfectly clean slate
      sessionStorage.clear();
      localStorage.removeItem('tableNumber');
      localStorage.removeItem('seatId');
      
      // Wipe Zustand store states
      useCustomerStore.getState().clearSeat();
      useCustomerStore.getState().clearCart();
      
      // Hard redirect to review page instead of the simulator
      window.location.href = '/review';
    },
    onError: (err) => {
      console.error(err);
      toast.error('Failed to end session. Please try again.');
    }
  });


  if (!mounted || authLoading || refsLoading) {
    return <div className="min-h-screen flex items-center justify-center "><Loader2 className="w-8 h-8 animate-spin text-brand-deep" /></div>;
  }

  // Combine references: always include the one from URL (if any) just in case the backend hasn't caught up, plus any from backend
  const allRefsSet = new Set<string>(sessionReferences);
  if (urlReference) allRefsSet.add(urlReference);
  const finalReferences = Array.from(allRefsSet);

  if (finalReferences.length === 0) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center  p-4 text-center">
        <h2 className="text-xl font-black mb-2">No Active Order</h2>
        <p className="text-stone-500 mb-6">We couldn't find any recent orders for your session.</p>
        <button onClick={() => router.push('/menu')} className="bg-brand-deep text-white px-6 py-3 rounded-xl font-bold">Browse Menu</button>
      </div>
    );
  }

  return (
    <div className="min-h-screen  pb-32 font-sans selection:bg-brand-light">
      <header className="bg-white px-5 py-6 shadow-[0_4px_20px_-10px_rgba(0,0,0,0.05)] border-b border-stone-100 sticky top-0 z-20">
        <div className="flex justify-between items-start mb-1">
          <div>
            <h1 className="text-2xl font-black text-stone-900 tracking-tight">Dining Hall</h1>
          </div>
          <div className="flex flex-col items-end gap-2">
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-emerald-50 border border-emerald-100 text-emerald-700 text-xs font-bold tracking-wide shadow-sm">
              <span className="w-1.5 h-1.5 bg-emerald-500 rounded-full animate-pulse" />
              Dining in Progress
            </span>
            <div className="flex items-center gap-1 text-xs text-stone-400 font-bold bg-stone-50 px-2 py-1 rounded-md">
              <Clock className="w-3 h-3" />
              <LiveTimer startTime={sessionStartTime} />
            </div>
          </div>
        </div>
      </header>

      <main className="mt-6 w-full max-w-md md:max-w-4xl lg:max-w-6xl mx-auto">
        <div className="flex overflow-x-auto snap-x snap-mandatory gap-4 px-5 pb-8 [&::-webkit-scrollbar]:hidden [-ms-overflow-style:none] [scrollbar-width:none] items-stretch">
          {finalReferences.map((ref, idx) => (
            <div key={ref} className="shrink-0 w-[92%] sm:w-[400px] md:w-[450px] snap-center">
              <OrderTicket reference={ref} />
            </div>
          ))}
        </div>
      
        <div className="mt-8 text-center pb-8 flex flex-col items-center gap-3">
          <p className="text-xs text-stone-400">Ready to go or leaving early?</p>
          <button 
            onClick={() => {
              if (confirm('Are you sure you want to end your dining session? This will release your seat.')) {
                closeSessionMutation.mutate();
              }
            }}
            disabled={closeSessionMutation.isPending}
            className="text-red-500 font-bold text-sm bg-red-50 hover:bg-red-100 px-6 py-2.5 rounded-xl transition-colors"
          >
            {closeSessionMutation.isPending ? 'Ending...' : 'End Session & Leave'}
          </button>
        </div>
      </main>

      <div className="fixed bottom-0 left-0 right-0 bg-white/95 backdrop-blur-md border-t border-stone-200 p-4 pb-safe z-30">
        <div className="max-w-md md:max-w-4xl lg:max-w-6xl mx-auto flex gap-3">
          <button 
            onClick={() => requestWaiterMutation.mutate()}
            disabled={requestWaiterMutation.isPending}
            className="flex flex-col items-center justify-center gap-1 w-[72px] bg-stone-100 hover:bg-stone-200 text-stone-700 rounded-2xl transition-colors font-bold text-[9px] uppercase tracking-wider"
          >
            <Bell className="w-5 h-5 text-amber-500" />
            Waiter
          </button>
          
          

          <button 
            onClick={() => router.push('/menu')}
            className="flex-1 flex items-center justify-center gap-2 bg-brand-deep hover:bg-brand-accent text-white rounded-2xl font-black transition-transform active:scale-95 shadow-lg shadow-brand-deep/20"
          >
            Craving Dessert? <ArrowRight className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
}

export default function DiningHall() {
  return (
    <Suspense fallback={<div className="min-h-screen flex items-center justify-center ">Loading...</div>}>
      <DiningHallContent />
    </Suspense>
  );
}
