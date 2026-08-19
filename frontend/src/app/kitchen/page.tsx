'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { kitchenService } from '@/services/kitchen.service';
import { Order, OrderItem } from '@/types';
import { 
  ChefHat, Flame, Check, Play, Clock, ArrowLeft, 
  AlertTriangle, RefreshCw, Layers 
} from 'lucide-react';

// Client-side Elapsed Timer component for each ticket
function ElapsedTimer({ createdAt }: { createdAt: string }) {
  const [elapsed, setElapsed] = useState('');
  const [isDelayed, setIsDelayed] = useState(false);

  useEffect(() => {
    const updateTimer = () => {
      const createdTime = new Date(createdAt).getTime();
      const now = Date.now();
      const diffMs = now - createdTime;
      const diffMins = Math.floor(diffMs / 60000);
      const diffSecs = Math.floor((diffMs % 60000) / 1000);
      
      setElapsed(`${diffMins}m ${diffSecs}s`);
      
      // If order sits in kitchen longer than 15 minutes, mark as delayed
      if (diffMins >= 15) {
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
    <div className={`flex items-center gap-1 text-[11px] font-black uppercase px-2 py-0.5 rounded-md ${
      isDelayed ? 'bg-red-100 text-red-700 animate-pulse' : 'bg-stone-100 text-stone-600'
    }`}>
      <Clock className="h-3 w-3" />
      <span>{elapsed}</span>
    </div>
  );
}

export default function KitchenPage() {
  const queryClient = useQueryClient();

  // Fetch kitchen orders, polling every 4 seconds
  const { data: orders = [], isLoading, refetch, isFetching } = useQuery<Order[]>({
    queryKey: ['kitchenOrders'],
    queryFn: () => kitchenService.getOrders(),
    refetchInterval: 4000,
  });

  // Order status update mutation
  const updateStatusMutation = useMutation({
    mutationFn: (variables: { id: string; status: 'PREPARING' | 'READY' }) => 
      kitchenService.updateOrderStatus(variables.id, variables.status),
    onSuccess: () => {
      // Invalidate query to trigger immediate refetch
      queryClient.invalidateQueries({ queryKey: ['kitchenOrders'] });
    },
    onError: (err) => {
      alert("Failed to update status: " + (err as any).message);
    }
  });

  const handleUpdateStatus = (id: string, currentStatus: string) => {
    if (currentStatus === 'PAID') {
      updateStatusMutation.mutate({ id, status: 'PREPARING' });
    } else if (currentStatus === 'PREPARING') {
      updateStatusMutation.mutate({ id, status: 'READY' });
    }
  };

  const paidOrders = orders.filter((o: Order) => o.status === 'PAID');
  const preparingOrders = orders.filter((o: Order) => o.status === 'PREPARING');
  const readyOrders = orders.filter((o: Order) => o.status === 'READY');

  return (
    <div className="flex-1 bg-kitchen-dark min-h-screen text-stone-100 flex flex-col">
      {/* KDS Header */}
      <header className="glass-panel-dark border-b border-stone-800 px-6 py-4 flex items-center justify-between sticky top-0 z-30">
        <div className="flex items-center gap-3">
          <Link href="/" className="p-2 hover:bg-stone-800 rounded-xl text-stone-400 hover:text-white transition">
            <ArrowLeft className="h-4.5 w-4.5" />
          </Link>
          <div className="flex items-center gap-2">
            <ChefHat className="h-5.5 w-5.5 text-brand-accent animate-pulse" />
            <h1 className="text-lg font-black tracking-tight uppercase">Kitchen Display System (KDS)</h1>
          </div>
        </div>

        <div className="flex items-center gap-4">
          <span className="text-xs font-semibold text-stone-400 flex items-center gap-1.5">
            <RefreshCw className={`h-3 w-3 ${isFetching ? 'animate-spin text-brand-accent' : ''}`} />
            Auto-Polling Active
          </span>
          <button 
            onClick={() => refetch()}
            className="px-3.5 py-1.5 rounded-lg bg-stone-800 hover:bg-stone-700 text-stone-200 font-semibold text-xs transition cursor-pointer"
          >
            Force Sync
          </button>
        </div>
      </header>

      {/* Metrics Stats Banner */}
      <section className="bg-stone-900 border-b border-stone-800/60 p-4 grid grid-cols-3 gap-4 max-w-7xl mx-auto w-full">
        <div className="bg-stone-950/40 border border-emerald-500/20 rounded-xl p-3.5 flex items-center justify-between">
          <div>
            <span className="text-[10px] font-bold text-stone-400 uppercase tracking-wider block">New Tickets</span>
            <span className="text-2xl font-black text-emerald-400 mt-1 block">{paidOrders.length}</span>
          </div>
          <Layers className="h-5 w-5 text-emerald-500/50" />
        </div>

        <div className="bg-stone-950/40 border border-brand-accent/20 rounded-xl p-3.5 flex items-center justify-between">
          <div>
            <span className="text-[10px] font-bold text-stone-400 uppercase tracking-wider block">Cooking</span>
            <span className="text-2xl font-black text-brand-accent mt-1 block">{preparingOrders.length}</span>
          </div>
          <Flame className="h-5 w-5 text-brand-accent/50" />
        </div>

        <div className="bg-stone-950/40 border border-yellow-500/20 rounded-xl p-3.5 flex items-center justify-between">
          <div>
            <span className="text-[10px] font-bold text-stone-400 uppercase tracking-wider block">Ready for Pickup</span>
            <span className="text-2xl font-black text-yellow-400 mt-1 block">{readyOrders.length}</span>
          </div>
          <Check className="h-5 w-5 text-yellow-500/50" />
        </div>
      </section>

      {/* KDS Columns Grid */}
      <main className="flex-1 max-w-7xl mx-auto w-full p-4 grid grid-cols-1 md:grid-cols-3 gap-6 items-stretch overflow-y-auto">
        
        {/* Column 1: NEW / PAID */}
        <div className="bg-stone-900/60 rounded-2xl p-4 flex flex-col gap-4 border border-stone-800/40">
          <div className="flex items-center justify-between border-b border-stone-800 pb-2 shrink-0">
            <h2 className="text-xs font-black uppercase text-stone-300 tracking-wider flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
              Incoming Queue
            </h2>
            <span className="bg-emerald-500/10 text-emerald-400 text-xs font-bold px-2 py-0.5 rounded-full">
              {paidOrders.length}
            </span>
          </div>

          <div className="flex-1 flex flex-col gap-4 overflow-y-auto max-h-[70vh] pr-1">
            {paidOrders.length === 0 ? (
              <div className="flex-1 flex flex-col items-center justify-center text-stone-600 text-center py-20">
                <ChefHat className="h-8 w-8 text-stone-700 mb-2" />
                <p className="text-xs font-semibold">Queue is clear</p>
              </div>
            ) : (
              paidOrders.map((order: Order) => (
                <div 
                  key={order.id}
                  className="bg-stone-950 border-l-4 border-emerald-500 rounded-xl p-4 space-y-3.5 shadow-sm"
                >
                  <div className="flex items-center justify-between gap-2 border-b border-stone-900 pb-2">
                    <span className="text-[10px] font-black text-emerald-400">T{order.tableNumber} &bull; Seat {order.seatNumber}</span>
                    <ElapsedTimer createdAt={order.createdAt} />
                  </div>

                  <div className="space-y-2">
                    {order.items.map((item: OrderItem) => (
                      <div key={item.id} className="text-xs">
                        <div className="flex items-center gap-2">
                          <span className="font-extrabold text-stone-200 bg-stone-800 px-1.5 py-0.5 rounded-sm shrink-0">
                            {item.quantity}
                          </span>
                          {item.imageUrl && (
                            <img src={item.imageUrl} alt={item.name} className="w-8 h-8 rounded-lg object-cover shrink-0" />
                          )}
                          <span className="font-semibold text-stone-100">{item.name}</span>
                        </div>
                        {item.notes && (
                          <div className="mt-1 ml-6 p-1.5 rounded-md bg-red-950/40 border border-red-900/30 text-[10px] text-brand-accent font-semibold leading-normal flex items-start gap-1">
                            <AlertTriangle className="h-3 w-3 text-brand-accent shrink-0 mt-0.5" />
                            <span>&ldquo;{item.notes}&rdquo;</span>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>

                  <button
                    disabled={updateStatusMutation.isPending}
                    onClick={() => handleUpdateStatus(order.id, order.status)}
                    className="w-full bg-emerald-600 hover:bg-emerald-500 text-white font-bold py-2.5 rounded-lg text-xs flex items-center justify-center gap-1.5 transition shadow-3xs cursor-pointer"
                  >
                    <Play className="h-3.5 w-3.5 fill-white" />
                    Accept Order &rarr;
                  </button>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Column 2: PREPARING */}
        <div className="bg-stone-900/60 rounded-2xl p-4 flex flex-col gap-4 border border-stone-800/40">
          <div className="flex items-center justify-between border-b border-stone-800 pb-2 shrink-0">
            <h2 className="text-xs font-black uppercase text-stone-300 tracking-wider flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-brand-accent"></span>
              Under Preparation
            </h2>
            <span className="bg-brand-accent/10 text-brand-accent text-xs font-bold px-2 py-0.5 rounded-full">
              {preparingOrders.length}
            </span>
          </div>

          <div className="flex-1 flex flex-col gap-4 overflow-y-auto max-h-[70vh] pr-1">
            {preparingOrders.length === 0 ? (
              <div className="flex-1 flex flex-col items-center justify-center text-stone-600 text-center py-20">
                <Flame className="h-8 w-8 text-stone-700 mb-2" />
                <p className="text-xs font-semibold">No cooking underway</p>
              </div>
            ) : (
              preparingOrders.map((order: Order) => (
                <div 
                  key={order.id}
                  className="bg-stone-950 border-l-4 border-brand-accent rounded-xl p-4 space-y-3.5 shadow-sm"
                >
                  <div className="flex items-center justify-between gap-2 border-b border-stone-900 pb-2">
                    <span className="text-[10px] font-black text-brand-accent">T{order.tableNumber} &bull; Seat {order.seatNumber}</span>
                    <ElapsedTimer createdAt={order.createdAt} />
                  </div>

                  <div className="space-y-2">
                    {order.items.map((item: OrderItem) => (
                      <div key={item.id} className="text-xs">
                        <div className="flex items-center gap-2">
                          <span className="font-extrabold text-stone-200 bg-stone-800 px-1.5 py-0.5 rounded-sm shrink-0">
                            {item.quantity}
                          </span>
                          {item.imageUrl && (
                            <img src={item.imageUrl} alt={item.name} className="w-8 h-8 rounded-lg object-cover shrink-0" />
                          )}
                          <span className="font-semibold text-stone-100">{item.name}</span>
                        </div>
                        {item.notes && (
                          <div className="mt-1 ml-6 p-1.5 rounded-md bg-red-950/40 border border-red-900/30 text-[10px] text-brand-accent font-semibold leading-normal flex items-start gap-1">
                            <AlertTriangle className="h-3 w-3 text-brand-accent shrink-0 mt-0.5" />
                            <span>&ldquo;{item.notes}&rdquo;</span>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>

                  <button
                    disabled={updateStatusMutation.isPending}
                    onClick={() => handleUpdateStatus(order.id, order.status)}
                    className="w-full bg-brand-deep hover:bg-brand-accent text-white font-bold py-2.5 rounded-lg text-xs flex items-center justify-center gap-1.5 transition shadow-3xs cursor-pointer"
                  >
                    <Check className="h-4 w-4" />
                    Complete Prep &rarr;
                  </button>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Column 3: READY */}
        <div className="bg-stone-900/60 rounded-2xl p-4 flex flex-col gap-4 border border-stone-800/40">
          <div className="flex items-center justify-between border-b border-stone-800 pb-2 shrink-0">
            <h2 className="text-xs font-black uppercase text-stone-300 tracking-wider flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-yellow-500"></span>
              Awaiting Pickup
            </h2>
            <span className="bg-yellow-500/10 text-yellow-400 text-xs font-bold px-2 py-0.5 rounded-full">
              {readyOrders.length}
            </span>
          </div>

          <div className="flex-1 flex flex-col gap-4 overflow-y-auto max-h-[70vh] pr-1">
            {readyOrders.length === 0 ? (
              <div className="flex-1 flex flex-col items-center justify-center text-stone-600 text-center py-20">
                <Check className="h-8 w-8 text-stone-700 mb-2" />
                <p className="text-xs font-semibold">No ready orders waiting</p>
              </div>
            ) : (
              readyOrders.map((order: Order) => (
                <div 
                  key={order.id}
                  className="bg-stone-950 border-l-4 border-yellow-500 rounded-xl p-4 space-y-3.5 shadow-sm opacity-80"
                >
                  <div className="flex items-center justify-between gap-2 border-b border-stone-900 pb-2">
                    <span className="text-[10px] font-black text-yellow-400">T{order.tableNumber} &bull; Seat {order.seatNumber}</span>
                    <span className="text-[9px] text-stone-500 font-bold">Ready</span>
                  </div>

                  <div className="space-y-2">
                    {order.items.map((item: OrderItem) => (
                      <div key={item.id} className="text-xs">
                        <div className="flex items-center gap-2">
                          <span className="font-extrabold text-stone-400 bg-stone-900 px-1.5 py-0.5 rounded-sm shrink-0">
                            {item.quantity}
                          </span>
                          {item.imageUrl && (
                            <img src={item.imageUrl} alt={item.name} className="w-8 h-8 rounded-lg object-cover shrink-0 animate-fade-in" />
                          )}
                          <span className="font-semibold text-stone-300">{item.name}</span>
                        </div>
                      </div>
                    ))}
                  </div>

                  <div className="text-center text-[10px] font-bold text-yellow-500 bg-yellow-950/40 p-2 rounded-lg border border-yellow-900/30">
                    Awaiting Waiter Pickup...
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

      </main>
    </div>
  );
}
