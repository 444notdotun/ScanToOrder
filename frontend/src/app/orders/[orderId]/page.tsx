"use client";
import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { Check, Clock, ChefHat, ConciergeBell, ArrowLeft, X, Coffee, Loader2 } from 'lucide-react';
import { api } from '@/lib/api';

export default function OrderTracking({ params }: { params: { orderId: string } }) {
  const [showAssist, setShowAssist] = useState(false);
  const [orderStatus, setOrderStatus] = useState<string>('PENDING');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchStatus = () => {
      api.get(`/api/v1/orders/${params.orderId}/status`)
        .then((res: any) => {
          const status = res.data?.data || res.data || 'PENDING';
          setOrderStatus(status);
        })
        .catch(err => console.error('Failed to fetch status', err))
        .finally(() => setLoading(false));
    };

    fetchStatus();
    const interval = setInterval(fetchStatus, 5000);
    return () => clearInterval(interval);
  }, [params.orderId]);

  const steps = [
    { label: 'Paid', status: ['RECEIVED', 'PREPARING', 'READY', 'DELIVERED', 'COMPLETED'].includes(orderStatus) ? 'done' : 'active', time: '--', icon: <Check size={16}/> },
    { label: 'Preparing', status: ['PREPARING', 'READY', 'DELIVERED', 'COMPLETED'].includes(orderStatus) ? 'done' : (orderStatus === 'RECEIVED' ? 'pending' : (orderStatus === 'PREPARING' ? 'active' : 'pending')), time: '--', icon: <ChefHat size={16}/> },
    { label: 'Ready', status: ['READY', 'DELIVERED', 'COMPLETED'].includes(orderStatus) ? 'done' : (orderStatus === 'READY' ? 'active' : 'pending'), time: '--', icon: <Check size={16}/> },
    { label: 'Delivered', status: ['DELIVERED', 'COMPLETED'].includes(orderStatus) ? 'done' : 'pending', time: '--', icon: <Check size={16}/> }
  ];

  return (
    <div className="min-h-screen bg-stone-50 flex flex-col">
      <header className="bg-white px-4 py-4 flex items-center justify-between border-b border-stone-100">
        <div className="flex items-center gap-3">
          <Link href="/menu" className="p-2 -ml-2 rounded-full hover:bg-stone-50">
            <ArrowLeft size={20} />
          </Link>
          <h1 className="font-bold text-lg">Order #{params.orderId}</h1>
        </div>
        <Link href="/table-status" className="text-sm font-bold text-orange-600 bg-orange-50 px-3 py-1.5 rounded-full">
          Table Status
        </Link>
      </header>

      <div className="p-6 flex-1">
        <div className="bg-white rounded-3xl p-6 shadow-sm border border-stone-100 mb-6">
          <h2 className="font-bold mb-6 text-lg">Live Status</h2>
          
          <div className="relative pl-6 space-y-8 border-l-2 border-stone-100 ml-4">
            {steps.map((step, i) => (
              <div key={i} className="relative">
                <div className={`absolute -left-[35px] w-8 h-8 rounded-full flex items-center justify-center shadow-sm ${
                  step.status === 'done' ? 'bg-green-500 text-white' : 
                  step.status === 'active' ? 'bg-orange-600 text-white animate-pulse' : 
                  'bg-stone-100 text-stone-400'
                }`}>
                  {step.icon}
                </div>
                <div>
                  <p className={`font-bold ${step.status === 'pending' ? 'text-stone-400' : 'text-stone-800'}`}>{step.label}</p>
                  <p className="text-xs text-stone-400">{step.time}</p>
                </div>
              </div>
            ))}
          </div>
        </div>

        <Link href="/review" className="block text-center text-stone-500 underline font-medium">
          Skip to Review (Demo)
        </Link>
      </div>

      {/* Floating Assist Button */}
      <button 
        onClick={() => setShowAssist(true)}
        className="fixed bottom-6 right-6 bg-stone-800 text-white p-4 rounded-full shadow-lg flex items-center gap-2 hover:bg-stone-900 transition-all"
      >
        <ConciergeBell size={24} />
        <span className="font-bold pr-2">Need a waiter?</span>
      </button>

      {/* Assist Modal */}
      {showAssist && (
        <div className="fixed inset-0 bg-black/50 z-50 flex flex-col justify-end">
          <div className="bg-white rounded-t-3xl p-6 animate-in slide-in-from-bottom duration-300">
            <div className="flex justify-between items-center mb-6">
              <h2 className="text-xl font-bold">How can we help?</h2>
              <button onClick={() => setShowAssist(false)} className="p-2 bg-stone-100 rounded-full"><X size={20}/></button>
            </div>
            <div className="grid grid-cols-2 gap-3 mb-6">
              <button 
                onClick={async () => {
                  try {
                    const tableId = localStorage.getItem('tableNumber') || 'UNKNOWN';
                    const seatId = localStorage.getItem('seatId') || 'UNKNOWN';
                    await api.post('/api/v1/assistance', { tableId, seatId, reason: 'waiter' });
                    import('sonner').then(m => m.toast.success('Waiter notified – someone is on the way to your table.'));
                    setShowAssist(false);
                  } catch (err) {
                    import('sonner').then(m => m.toast.error('Could not alert staff. Please wave down a server.'));
                  }
                }}
                className="bg-orange-50 border border-orange-200 text-orange-600 rounded-2xl p-4 flex flex-col items-center justify-center text-center font-bold gap-2 active:bg-orange-100"
              >
                <ConciergeBell size={28}/> Call Waiter
              </button>
              <button 
                onClick={async () => {
                  try {
                    const tableId = localStorage.getItem('tableNumber') || 'UNKNOWN';
                    const seatId = localStorage.getItem('seatId') || 'UNKNOWN';
                    await api.post('/api/v1/assistance', { tableId, seatId, reason: 'items' });
                    import('sonner').then(m => m.toast.success('Waiter notified – someone is on the way to your table.'));
                    setShowAssist(false);
                  } catch (err) {
                    import('sonner').then(m => m.toast.error('Could not alert staff. Please wave down a server.'));
                  }
                }}
                className="bg-stone-50 border border-stone-200 text-stone-600 rounded-2xl p-4 flex flex-col items-center justify-center text-center font-bold gap-2 active:bg-stone-100"
              >
                <Coffee size={28}/> Request Items
              </button>
            </div>
            <button onClick={() => setShowAssist(false)} className="w-full bg-orange-600 text-white py-4 rounded-full font-bold shadow-md">
              Cancel
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
