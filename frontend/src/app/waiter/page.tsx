'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { waiterService } from '@/services/waiter.service';
import { managerService } from '@/services/manager.service';
import { ServiceCall, Order, RestaurantTable, Seat, OrderItem } from '@/types';
import { 
  BellRing, PackageCheck, Map, UserCheck, ArrowLeft, 
  Loader2, CheckCircle, Clock, Armchair 
} from 'lucide-react';

export default function WaiterPage() {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<'calls' | 'deliveries' | 'tables'>('calls');
  const [selectedTableId, setSelectedTableId] = useState<string | null>(null);

  // Mock waiter logged-in profile
  const currentWaiterId = 'w1';

  // 1. Fetch Service Calls (Polling)
  const { data: serviceCalls = [], isLoading: loadingCalls } = useQuery<ServiceCall[]>({
    queryKey: ['waiterCalls'],
    queryFn: () => waiterService.getServiceCalls(),
    refetchInterval: 4000,
  });

  // 2. Fetch Ready Orders (Polling)
  const { data: readyOrders = [], isLoading: loadingOrders } = useQuery<Order[]>({
    queryKey: ['waiterOrders'],
    queryFn: () => waiterService.getReadyOrders(),
    refetchInterval: 4000,
  });

  // 3. Fetch Tables / Floor Plan (Polling)
  const { data: tables = [], isLoading: loadingTables } = useQuery<RestaurantTable[]>({
    queryKey: ['waiterTables'],
    queryFn: () => managerService.getTables(),
    refetchInterval: 5000,
  });

  // Mutations
  const updateCallMutation = useMutation({
    mutationFn: (variables: { id: string; status: 'IN_PROGRESS' | 'RESOLVED'; waiterId: string }) => 
      waiterService.updateServiceCall(variables.id, { status: variables.status, waiterId: variables.waiterId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['waiterCalls'] });
    }
  });

  const deliverOrderMutation = useMutation({
    mutationFn: (id: string) => waiterService.markDelivered(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['waiterOrders'] });
    }
  });

  const releaseSeatMutation = useMutation({
    mutationFn: (id: string) => waiterService.releaseSeat(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['waiterTables'] });
      alert("Seat successfully vacated and released.");
    }
  });

  const closeSessionMutation = useMutation({
    mutationFn: (id: string) => waiterService.closeSession(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['waiterTables'] });
      alert("Table session closed. All seats are now vacant.");
    }
  });

  const handleClaimCall = (id: string) => {
    updateCallMutation.mutate({ id, status: 'IN_PROGRESS', waiterId: currentWaiterId });
  };

  const handleResolveCall = (id: string) => {
    updateCallMutation.mutate({ id, status: 'RESOLVED', waiterId: currentWaiterId });
  };

  const handleDeliver = (id: string) => {
    deliverOrderMutation.mutate(id);
  };

  const getRequestLabel = (type: string) => {
    switch (type) {
      case 'WAITER': return 'Call Waiter';
      case 'BILL': return 'Bring Bill';
      case 'CLEANING': return 'Clean Table';
      case 'HELP': return 'Menu Query';
      default: return 'Assistance';
    }
  };

  const activeCalls = serviceCalls.filter((c: ServiceCall) => c.status !== 'RESOLVED');

  return (
    <div className="flex-1 bg-waiter-floor min-h-screen flex flex-col">
      {/* Header */}
      <header className="bg-white border-b border-stone-200/50 px-6 py-4 flex items-center justify-between sticky top-0 z-30 shadow-2xs">
        <div className="flex items-center gap-3">
          <Link href="/" className="p-2 hover:bg-stone-50 rounded-xl text-stone-500 transition">
            <ArrowLeft className="h-4.5 w-4.5" />
          </Link>
          <div className="flex items-center gap-2">
            <UserCheck className="h-5.5 w-5.5 text-brand-deep" />
            <h1 className="text-base font-black text-stone-900 uppercase tracking-tight">Floor Operations</h1>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <span className="text-[10px] font-black text-stone-400 bg-stone-100 px-2.5 py-1 rounded-md">
            Waiter: Funmi
          </span>
        </div>
      </header>

      {/* Navigation Sub-Header */}
      <section className="bg-white border-b border-stone-200/50 p-2 sticky top-[61px] z-20 shadow-3xs flex justify-around">
        <button
          onClick={() => setActiveTab('calls')}
          className={`flex items-center gap-2 py-2 px-4 rounded-xl text-xs font-bold transition cursor-pointer ${
            activeTab === 'calls'
              ? 'bg-brand-light text-brand-deep'
              : 'text-stone-600 hover:bg-stone-50'
          }`}
        >
          <BellRing className={`h-4 w-4 ${activeCalls.length > 0 ? 'animate-swing text-brand-deep' : ''}`} />
          Service Calls
          {activeCalls.length > 0 && (
            <span className="bg-brand-deep text-white text-[9px] font-black px-1.5 py-0.5 rounded-full">
              {activeCalls.length}
            </span>
          )}
        </button>

        <button
          onClick={() => setActiveTab('deliveries')}
          className={`flex items-center gap-2 py-2 px-4 rounded-xl text-xs font-bold transition cursor-pointer ${
            activeTab === 'deliveries'
              ? 'bg-brand-light text-brand-deep'
              : 'text-stone-600 hover:bg-stone-50'
          }`}
        >
          <PackageCheck className="h-4 w-4" />
          Ready Foods
          {readyOrders.length > 0 && (
            <span className="bg-brand-deep text-white text-[9px] font-black px-1.5 py-0.5 rounded-full">
              {readyOrders.length}
            </span>
          )}
        </button>

        <button
          onClick={() => setActiveTab('tables')}
          className={`flex items-center gap-2 py-2 px-4 rounded-xl text-xs font-bold transition cursor-pointer ${
            activeTab === 'tables'
              ? 'bg-brand-light text-brand-deep'
              : 'text-stone-600 hover:bg-stone-50'
          }`}
        >
          <Map className="h-4 w-4" />
          Table Map
        </button>
      </section>

      {/* Content Area */}
      <main className="flex-1 max-w-md mx-auto w-full p-4 space-y-4">
        
        {/* TAB 1: SERVICE CALLS */}
        {activeTab === 'calls' && (
          <div className="space-y-4">
            <div className="flex justify-between items-center px-1">
              <span className="text-xs font-extrabold text-stone-500 uppercase tracking-wider">Assistance Request Feed</span>
            </div>

            {loadingCalls ? (
              <div className="text-center py-20">
                <Loader2 className="h-6 w-6 animate-spin text-brand-deep mx-auto mb-2" />
                <p className="text-xs text-stone-500">Loading requests...</p>
              </div>
            ) : activeCalls.length === 0 ? (
              <div className="bg-white rounded-2xl p-8 border border-stone-100 text-center space-y-2 shadow-2xs">
                <CheckCircle className="h-8 w-8 text-emerald-500 mx-auto" />
                <h3 className="font-bold text-stone-900 text-sm">No Active Calls</h3>
                <p className="text-xs text-stone-400">Diners are sitting comfortably.</p>
              </div>
            ) : (
              <div className="space-y-3">
                {activeCalls.map((call: ServiceCall) => (
                  <div 
                    key={call.id} 
                    className={`bg-white rounded-2xl p-4 border border-stone-100 shadow-2xs flex justify-between gap-4 items-start ${
                      call.status === 'IN_PROGRESS' ? 'border-l-4 border-l-brand-accent' : 'border-l-4 border-l-brand-deep'
                    }`}
                  >
                    <div className="space-y-1.5">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-extrabold text-stone-900">
                          Table {call.tableNumber} &bull; Seat {call.seatNumber}
                        </span>
                        <span className={`text-[9px] font-bold px-1.5 py-0.5 rounded-md ${
                          call.status === 'IN_PROGRESS' 
                            ? 'bg-status-prep-bg text-status-prep-text' 
                            : 'bg-status-success-bg text-status-success-text'
                        }`}>
                          {getRequestLabel(call.requestType)}
                        </span>
                      </div>
                      
                      {call.note && (
                        <p className="text-xs text-stone-600 bg-stone-50 p-2 rounded-xl border border-stone-100 font-medium">
                          &ldquo;{call.note}&rdquo;
                        </p>
                      )}

                      <div className="flex items-center gap-1 text-[9px] text-stone-400 font-semibold">
                        <Clock className="h-3 w-3" />
                        <span>{new Date(call.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                        {call.waiterName && <span>&bull; Claimed by {call.waiterName}</span>}
                      </div>
                    </div>

                    <div className="shrink-0 pt-0.5">
                      {call.status === 'PENDING' ? (
                        <button
                          onClick={() => handleClaimCall(call.id)}
                          className="bg-brand-deep hover:bg-brand-accent text-white font-bold px-3 py-2 rounded-xl text-[10px] shadow-3xs cursor-pointer"
                        >
                          Claim
                        </button>
                      ) : (
                        <button
                          onClick={() => handleResolveCall(call.id)}
                          className="bg-emerald-600 hover:bg-emerald-500 text-white font-bold px-3 py-2 rounded-xl text-[10px] shadow-3xs cursor-pointer"
                        >
                          Resolve
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* TAB 2: READY FOODS */}
        {activeTab === 'deliveries' && (
          <div className="space-y-4">
            <div className="flex justify-between items-center px-1">
              <span className="text-xs font-extrabold text-stone-500 uppercase tracking-wider">Ready for Pick Up</span>
            </div>

            {loadingOrders ? (
              <div className="text-center py-20">
                <Loader2 className="h-6 w-6 animate-spin text-brand-deep mx-auto mb-2" />
                <p className="text-xs text-stone-500">Loading deliveries...</p>
              </div>
            ) : readyOrders.length === 0 ? (
              <div className="bg-white rounded-2xl p-8 border border-stone-100 text-center space-y-2 shadow-2xs">
                <CheckCircle className="h-8 w-8 text-emerald-500 mx-auto" />
                <h3 className="font-bold text-stone-900 text-sm">No Ready Orders</h3>
                <p className="text-xs text-stone-400">Kitchen is preparing active tickets.</p>
              </div>
            ) : (
              <div className="space-y-3">
                {readyOrders.map((order: Order) => (
                  <div 
                    key={order.id} 
                    className="bg-white rounded-2xl p-4 border border-stone-100 shadow-2xs space-y-3 border-l-4 border-l-yellow-500"
                  >
                    <div className="flex justify-between items-start">
                      <div>
                        <span className="text-xs font-extrabold text-stone-900 block">
                          Table {order.tableNumber} &bull; Seat {order.seatNumber}
                        </span>
                        <span className="text-[10px] text-stone-400 font-bold block mt-0.5">Order #{order.id}</span>
                      </div>
                      
                      <button
                        onClick={() => handleDeliver(order.id)}
                        className="bg-emerald-600 hover:bg-emerald-500 text-white font-bold px-3 py-2 rounded-xl text-[10px] flex items-center gap-1 shadow-3xs cursor-pointer"
                      >
                        <CheckCircle className="h-3.5 w-3.5" />
                        Delivered
                      </button>
                    </div>

                    <div className="bg-stone-50 rounded-xl p-2.5 border border-stone-100/50 space-y-1.5">
                      {order.items.map((item: OrderItem) => (
                        <div key={item.id} className="text-xs flex justify-between font-semibold">
                          <span className="text-stone-800">{item.name} <span className="text-stone-400">&times; {item.quantity}</span></span>
                          {item.notes && <span className="text-[10px] text-brand-accent font-semibold">&ldquo;{item.notes}&rdquo;</span>}
                        </div>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* TAB 3: TABLE MAP & GHOST SEAT RELEASE */}
        {activeTab === 'tables' && (
          <div className="space-y-4">
            <div className="flex justify-between items-center px-1">
              <span className="text-xs font-extrabold text-stone-500 uppercase tracking-wider">Restaurant Floor Overview</span>
            </div>

            {loadingTables ? (
              <div className="text-center py-20">
                <Loader2 className="h-6 w-6 animate-spin text-brand-deep mx-auto mb-2" />
                <p className="text-xs text-stone-500">Loading tables...</p>
              </div>
            ) : (
              <div className="grid grid-cols-1 gap-3">
                {tables.map((table: RestaurantTable) => {
                  const isExpanded = selectedTableId === table.id;
                  const occupiedCount = table.seats?.filter((s: Seat) => s.status === 'OCCUPIED').length || 0;
                  const capacity = table.capacity || 4;

                  return (
                    <div 
                      key={table.id} 
                      className="bg-white rounded-2xl border border-stone-100 shadow-2xs overflow-hidden"
                    >
                      {/* Summary Row */}
                      <button
                        onClick={() => setSelectedTableId(isExpanded ? null : table.id)}
                        className="w-full p-4 flex items-center justify-between text-left hover:bg-stone-50/50 transition cursor-pointer"
                      >
                        <div className="flex items-center gap-3">
                          <div className={`p-2 rounded-xl text-xs font-black ${
                            occupiedCount > 0 ? 'bg-brand-light text-brand-deep' : 'bg-stone-100 text-stone-400'
                          }`}>
                            T{table.tableNumber}
                          </div>
                          <div>
                            <span className="text-xs font-bold text-stone-900 block">Table {table.tableNumber}</span>
                            <span className="text-[10px] text-stone-400 font-semibold block mt-0.5">
                              {occupiedCount} of {capacity} seats claimed
                            </span>
                          </div>
                        </div>
                        <span className="text-xs font-bold text-brand-deep">
                          {isExpanded ? 'Hide Details' : 'Manage Seats'}
                        </span>
                      </button>

                      {/* Expandable Seats List */}
                      {isExpanded && (
                        <div className="bg-stone-50/40 p-4 border-t border-stone-100 space-y-3">
                          
                          {/* Seat Grid */}
                          <div className="grid grid-cols-2 gap-2">
                            {table.seats?.map((seat: Seat) => {
                              const isOccupied = seat.status === 'OCCUPIED';
                              return (
                                <div 
                                  key={seat.id}
                                  className="bg-white rounded-xl p-3 border border-stone-100 flex items-center justify-between gap-2"
                                >
                                  <div className="flex items-center gap-2">
                                    <Armchair className={`h-4 w-4 ${isOccupied ? 'text-brand-deep' : 'text-stone-300'}`} />
                                    <span className="text-[11px] font-bold text-stone-800">Seat {seat.seatNumber}</span>
                                  </div>

                                  {isOccupied ? (
                                    <button
                                      disabled={releaseSeatMutation.isPending}
                                      onClick={() => releaseSeatMutation.mutate(seat.id)}
                                      className="text-[9px] font-black text-red-600 bg-red-50 hover:bg-red-100 px-2 py-1 rounded-md transition cursor-pointer border border-red-100"
                                    >
                                      Force Free
                                    </button>
                                  ) : (
                                    <span className="text-[9px] font-bold text-emerald-600 bg-emerald-50 px-2 py-1 rounded-md border border-emerald-100">
                                      Vacant
                                    </span>
                                  )}
                                </div>
                              );
                            })}
                          </div>

                          {/* Table Session Action */}
                          {table.currentSessionId && (
                            <button
                              disabled={closeSessionMutation.isPending}
                              onClick={() => closeSessionMutation.mutate(table.currentSessionId!)}
                              className="w-full bg-stone-900 hover:bg-stone-800 text-white font-bold py-2.5 rounded-xl text-xs mt-2 transition shadow-3xs cursor-pointer"
                            >
                              Reset & Close Table Session
                            </button>
                          )}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

      </main>
    </div>
  );
}
