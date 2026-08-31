'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { kitchenService } from '@/services/kitchen.service';
import { useAuthStore } from '@/store/authStore';
import ProtectedRoute from '@/components/ProtectedRoute';
import { 
  ArrowLeft, ChefHat, Clock, Loader2, LogOut, 
  RefreshCw, Flame, Check, Square, CheckSquare
} from 'lucide-react';

interface KitchenOrderItem {
  itemName: string;
  quantity: number;
  specialInstructions?: string;
}

interface KitchenOrder {
  orderId: string;
  tableNumber: string;
  seatId: string;
  createdAt: string;
  items: KitchenOrderItem[];
}

function ElapsedTimer({ createdAt }: { createdAt: string }) {
  const [elapsed, setElapsed] = useState('');
  const [isDelayed, setIsDelayed] = useState(false);

  useEffect(() => {
    const updateTimer = () => {
      if (!createdAt) return;
      const start = new Date(createdAt).getTime();
      const now = new Date().getTime();
      const diffMs = now - start;
      const diffMins = Math.floor(diffMs / 60000);
      const diffSecs = Math.floor((diffMs % 60000) / 1000);
      setElapsed(`${diffMins}m ${diffSecs}s`);
      if (diffMins >= 10) {
        setIsDelayed(true);
      } else {
        setIsDelayed(false);
      }
    };

    updateTimer();
    const interval = setInterval(updateTimer, 1000);
    return () => clearInterval(interval);
  }, [createdAt]);

  return (
    <div className={`flex items-center gap-1 text-[10px] font-black uppercase px-2 py-0.5 rounded-md ${
      isDelayed ? 'bg-red-950 text-red-400 animate-pulse' : 'bg-stone-800 text-stone-400'
    }`}>
      <Clock className="h-3 w-3" />
      <span>{elapsed}</span>
    </div>
  );
}

// Client-side checklist state component for ticket items
function TicketItems({ items, orderId }: { items: KitchenOrderItem[], orderId: string }) {
  // Store checked state in local state (resets if page refreshed, which is fine for KDS)
  const [checkedItems, setCheckedItems] = useState<Record<number, boolean>>({});

  const toggleItem = (idx: number) => {
    setCheckedItems(prev => ({ ...prev, [idx]: !prev[idx] }));
  };

  return (
    <div className="space-y-3 flex-1 min-h-[120px]">
      {items.map((item, idx) => {
        const isChecked = checkedItems[idx];
        return (
          <div 
            key={idx} 
            className={`text-xs space-y-1 p-2 rounded-lg cursor-pointer transition-colors ${
              isChecked ? 'bg-emerald-950/30 opacity-60' : 'hover:bg-stone-800'
            }`}
            onClick={() => toggleItem(idx)}
          >
            <div className="flex items-start gap-2">
              <div className="mt-0.5 text-stone-400">
                {isChecked ? <CheckSquare className="h-4 w-4 text-emerald-500" /> : <Square className="h-4 w-4" />}
              </div>
              <div className="flex-1">
                <div className="flex justify-between font-bold text-stone-250">
                  <span className={isChecked ? 'line-through text-stone-500' : ''}>{item.itemName}</span>
                  <span className={`font-black ${isChecked ? 'text-stone-600' : 'text-brand-accent'}`}>x {item.quantity}</span>
                </div>
                {item.specialInstructions && (
                  <p className={`text-[10px] font-semibold italic px-2 py-1 rounded-lg mt-1 ${
                    isChecked ? 'text-stone-600 bg-stone-900/50' : 'text-amber-500 bg-amber-950/20 border border-amber-900/20'
                  }`}>
                    &ldquo;{item.specialInstructions}&rdquo;
                  </p>
                )}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}

function KitchenKdsContent() {
  const queryClient = useQueryClient();
  const logout = useAuthStore((state) => state.logout);
  const workerProfile = useAuthStore((state) => state.workerProfile);

  // Poll Lane 1: New / Paid Orders
  const { data: newOrders = [], isLoading: loadingNew, isFetching: fetchingNew } = useQuery<KitchenOrder[]>({
    queryKey: ['kitchenOrders', 'PAID'],
    queryFn: () => kitchenService.getOrders('PAID'),
    refetchInterval: 4000,
  });

  // Poll Lane 2: In-Progress Orders
  const { data: prepOrders = [], isLoading: loadingPrep, isFetching: fetchingPrep } = useQuery<KitchenOrder[]>({
    queryKey: ['kitchenOrders', 'PREPARING'],
    queryFn: () => kitchenService.getOrders('PREPARING'),
    refetchInterval: 4000,
  });

  const isLoading = loadingNew || loadingPrep;
  const isFetching = fetchingNew || fetchingPrep;

  const updateStatusMutation = useMutation({
    mutationFn: (variables: { id: string; status: 'PREPARING' | 'READY' }) => 
      kitchenService.updateOrderStatus(variables.id, variables.status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['kitchenOrders'] });
    },
    onError: (err: any) => {
      alert("Failed to update ticket: " + err.message);
    }
  });

  return (
    <div className="flex-1 bg-kitchen-dark min-h-screen text-stone-100 flex flex-col">
      {/* Header */}
      <header className="glass-panel-dark border-b border-stone-800 px-6 py-4 flex items-center justify-between sticky top-0 z-30">
        <div className="flex items-center gap-3">
          <Link href="/admin/dashboard" className="p-2 hover:bg-stone-800 rounded-xl text-stone-400 hover:text-white transition">
            <ArrowLeft className="h-4.5 w-4.5" />
          </Link>
          <div className="flex items-center gap-2">
            <ChefHat className="h-5.5 w-5.5 text-brand-accent animate-pulse" />
            <h1 className="text-sm font-black tracking-tight uppercase">Kitchen Display System (KDS)</h1>
          </div>
        </div>

        <div className="flex items-center gap-4">
          <div className="flex items-center gap-1 text-[10px] text-stone-400 font-extrabold uppercase">
            <RefreshCw className={`h-3.5 w-3.5 ${isFetching ? 'animate-spin text-brand-accent' : ''}`} />
            {isFetching ? 'Syncing...' : 'Live'}
          </div>

          <span className="text-[10px] font-black text-stone-400 bg-stone-900 border border-stone-800 px-2.5 py-1 rounded-md">
            Staff: @{workerProfile?.username || 'chef'}
          </span>

          <button 
            onClick={() => logout()}
            className="flex items-center gap-1 text-[10px] font-extrabold text-red-400 hover:text-red-500 bg-red-950/20 px-2.5 py-1 rounded-md border border-red-900/30 transition cursor-pointer"
          >
            <LogOut className="h-3 w-3" />
            Log Out
          </button>
        </div>
      </header>

      {/* Main 2-Lane Board */}
      <main className="flex-1 p-6 grid grid-cols-1 md:grid-cols-2 gap-6 w-full max-w-[1600px] mx-auto overflow-hidden">
        
        {/* Lane 1: New / Paid Orders */}
        <div className="flex flex-col bg-stone-900/30 rounded-3xl border border-stone-800/50 p-5 overflow-y-auto max-h-[calc(100vh-100px)] custom-scrollbar">
          <div className="flex items-center justify-between mb-4 sticky top-0 bg-kitchen-dark/90 backdrop-blur-md p-2 rounded-xl z-10">
            <div className="flex items-center gap-2">
              <span className="h-2.5 w-2.5 rounded-full bg-brand-accent"></span>
              <h2 className="text-sm font-black uppercase tracking-wider text-stone-300">New Tickets</h2>
            </div>
            <span className="text-xs font-bold bg-stone-800 text-stone-400 px-2.5 py-0.5 rounded-full">{newOrders.length}</span>
          </div>

          {isLoading && newOrders.length === 0 ? (
            <div className="py-24 flex justify-center"><Loader2 className="h-6 w-6 text-brand-accent animate-spin" /></div>
          ) : newOrders.length === 0 ? (
            <div className="py-24 text-center text-stone-600 text-xs font-bold uppercase tracking-widest">No New Tickets</div>
          ) : (
            <div className="flex flex-col gap-4">
              {newOrders.map((order) => (
                <div key={order.orderId} className="bg-stone-900/80 border border-stone-700/50 rounded-3xl p-5 shadow-sm space-y-4">
                  <div className="flex justify-between items-start border-b border-stone-800 pb-3">
                    <div className="space-y-0.5">
                      <span className="text-[10px] font-black text-brand-accent uppercase tracking-widest">
                        Ticket #{order.orderId.substring(0, 8)}
                      </span>
                      <h3 className="font-bold text-stone-200 text-xs">{order.tableNumber}</h3>
                    </div>
                    <ElapsedTimer createdAt={order.createdAt} />
                  </div>
                  
                  <TicketItems items={order.items} orderId={order.orderId} />

                  <div className="pt-3 border-t border-stone-800">
                    <button
                      onClick={() => updateStatusMutation.mutate({ id: order.orderId, status: 'PREPARING' })}
                      disabled={updateStatusMutation.isPending}
                      className="w-full bg-brand-accent hover:bg-emerald-500 text-kitchen-dark font-black py-3 rounded-xl text-xs transition cursor-pointer flex items-center justify-center gap-2"
                    >
                      <Flame className="h-4 w-4" />
                      Start Preparing
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Lane 2: In-Progress Cooking Queue */}
        <div className="flex flex-col bg-stone-900/30 rounded-3xl border border-stone-800/50 p-5 overflow-y-auto max-h-[calc(100vh-100px)] custom-scrollbar">
          <div className="flex items-center justify-between mb-4 sticky top-0 bg-kitchen-dark/90 backdrop-blur-md p-2 rounded-xl z-10">
            <div className="flex items-center gap-2">
              <span className="h-2.5 w-2.5 rounded-full bg-amber-500 animate-pulse"></span>
              <h2 className="text-sm font-black uppercase tracking-wider text-stone-300">In Progress</h2>
            </div>
            <span className="text-xs font-bold bg-amber-950 text-amber-500 px-2.5 py-0.5 rounded-full">{prepOrders.length}</span>
          </div>

          {isLoading && prepOrders.length === 0 ? (
            <div className="py-24 flex justify-center"><Loader2 className="h-6 w-6 text-amber-500 animate-spin" /></div>
          ) : prepOrders.length === 0 ? (
            <div className="py-24 text-center text-stone-600 text-xs font-bold uppercase tracking-widest">No Active Prep</div>
          ) : (
            <div className="flex flex-col gap-4">
              {prepOrders.map((order) => (
                <div key={order.orderId} className="bg-stone-900/80 border border-amber-900/30 rounded-3xl p-5 shadow-sm space-y-4">
                  <div className="flex justify-between items-start border-b border-stone-800 pb-3">
                    <div className="space-y-0.5">
                      <span className="text-[10px] font-black text-amber-500 uppercase tracking-widest">
                        Ticket #{order.orderId.substring(0, 8)}
                      </span>
                      <h3 className="font-bold text-stone-200 text-xs">{order.tableNumber}</h3>
                    </div>
                    <ElapsedTimer createdAt={order.createdAt} />
                  </div>
                  
                  <TicketItems items={order.items} orderId={order.orderId} />

                  <div className="pt-3 border-t border-stone-800">
                    <button
                      onClick={() => updateStatusMutation.mutate({ id: order.orderId, status: 'READY' })}
                      disabled={updateStatusMutation.isPending}
                      className="w-full bg-stone-800 hover:bg-emerald-950 hover:text-emerald-400 hover:border-emerald-900 border border-stone-700 text-stone-300 font-black py-3 rounded-xl text-xs transition cursor-pointer flex items-center justify-center gap-2"
                    >
                      <Check className="h-4 w-4" />
                      ✓ Mark as Ready
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

      </main>
    </div>
  );
}

export default function KitchenKdsPage() {
  return (
    <ProtectedRoute allowedRoles={['CHEF', 'MANAGER']}>
      <KitchenKdsContent />
    </ProtectedRoute>
  );
}
