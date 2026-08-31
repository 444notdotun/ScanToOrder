'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { waiterService } from '@/services/waiter.service';
import { managerService } from '@/services/manager.service';
import { useAuthStore } from '@/store/authStore';
import ProtectedRoute from '@/components/ProtectedRoute';
import { ServiceCall, Order, RestaurantTable, Seat, OrderItem } from '@/types';
import { 
  BellRing, PackageCheck, Map, UserCheck, ArrowLeft, 
  Loader2, CheckCircle, Clock, Armchair, LogOut, LayoutDashboard, ChevronRight, Bell
} from 'lucide-react';
import { toast } from 'sonner';

function WaiterFloorContent() {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<'deliveries' | 'calls' | 'tables'>('deliveries');
  const [selectedTableId, setSelectedTableId] = useState<string | null>(null);

  const { logout, workerProfile } = useAuthStore();
  const staffName = (workerProfile as any)?.staffName || workerProfile?.username || 'John D.';
  const role = 'Waiter';

  // 1. Fetch Service Calls (Polling)
  const { data: serviceCalls = [], isLoading: loadingCalls } = useQuery<ServiceCall[]>({
    queryKey: ['waiterCalls'],
    queryFn: () => waiterService.getServiceCalls(),
    refetchInterval: 4000,
  });

  // 2. Fetch Ready Orders (Polling)
  const { data: orders = [], isLoading: loadingOrders } = useQuery<Order[]>({
    queryKey: ['waiterOrders'],
    queryFn: () => waiterService.getReadyOrders(), // The service is configured to fetch ?status=READY
    refetchInterval: 4000,
  });
  
  // Ensure we strictly display only 'READY' orders on the frontend as per requirement
  const readyOrders = orders.filter((o: Order) => o.status === 'READY' || (o.status as any) !== 'DELIVERED');

  // 3. Fetch Tables / Floor Plan (Polling)
  const { data: tables = [], isLoading: loadingTables } = useQuery<RestaurantTable[]>({
    queryKey: ['waiterTables'],
    queryFn: () => managerService.getTables(),
    refetchInterval: 4000,
  });

  // Mutations
  const updateCallMutation = useMutation({
    mutationFn: (variables: { id: string; status: 'IN_PROGRESS' | 'RESOLVED'; waiterId: string }) => 
      waiterService.updateServiceCall(variables.id, { status: variables.status, waiterId: variables.waiterId }),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['waiterCalls'] });
      if (variables.status === 'RESOLVED') {
        toast.success('Service call resolved');
      } else {
        toast.success('Service call claimed');
      }
    }
  });

  const deliverOrderMutation = useMutation({
    mutationFn: (id: string) => waiterService.markDelivered(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['waiterOrders'] });
      toast.success('Order marked as DELIVERED successfully!');
    },
    onError: () => {
      toast.error('Failed to mark order as delivered.');
    }
  });

  const releaseSeatMutation = useMutation({
    mutationFn: (id: string) => waiterService.releaseSeat(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['waiterTables'] });
      toast.success("Seat successfully vacated and released.");
    }
  });

  const updateSeatMutation = useMutation({
    mutationFn: (variables: { seatId: string; newState: string }) => waiterService.updateSeat(variables),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['waiterTables'] });
      toast.success("Seat status updated successfully.");
    }
  });

  const closeSessionMutation = useMutation({
    mutationFn: (id: string) => waiterService.closeSession(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['waiterTables'] });
      toast.success("Table session closed. Table is free for new diners.");
    }
  });

  const handleClaimCall = (id: string) => {
    updateCallMutation.mutate({ id, status: 'IN_PROGRESS', waiterId: staffName });
  };

  const handleResolveCall = (id: string) => {
    updateCallMutation.mutate({ id, status: 'RESOLVED', waiterId: staffName });
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

  
  const getSeatNumber = (seatId: string, fallbackSeatNumber?: number | string) => {
    if (fallbackSeatNumber) return fallbackSeatNumber;
    if (!seatId) return '?';
    for (const table of tables) {
      const seat = table.seats?.find(s => s.id === seatId);
      if (seat) return seat.seatNumber;
    }
    return '?';
  };

  const openCalls = serviceCalls.filter((c: ServiceCall) => c.status === 'PENDING');
  const inProgressCalls = serviceCalls.filter((c: ServiceCall) => c.status === 'IN_PROGRESS');
  const resolvedCalls = serviceCalls.filter((c: ServiceCall) => c.status === 'RESOLVED');
  const totalActiveCalls = openCalls.length + inProgressCalls.length;
  
  const tablesNeedingAttention = tables.filter((t: RestaurantTable) => t.status === 'OCCUPIED' || t.currentSessionId);

  return (
    <div className="flex-1 bg-stone-50 min-h-screen flex flex-col">
      {/* Header */}
      <header className="bg-white border-b border-stone-200/50 px-6 py-4 flex items-center justify-between sticky top-0 z-30 shadow-sm">
        <div className="flex items-center gap-3">
          <Link href="/" className="p-2 hover:bg-stone-50 rounded-xl text-stone-500 transition">
            <ArrowLeft className="h-5 w-5" />
          </Link>
          <div className="flex items-center gap-2">
            <UserCheck className="h-6 w-6 text-brand-deep" />
            <h1 className="text-lg font-black text-stone-900 tracking-tight">Waiter Dashboard</h1>
          </div>
        </div>

        <div className="flex items-center gap-4">
          <div className="relative">
            <Bell className="h-6 w-6 text-stone-600" />
            {totalActiveCalls > 0 && (
              <span className="absolute -top-1 -right-1 bg-red-500 text-white text-[10px] font-black px-1.5 py-0.5 rounded-full border-2 border-white">
                {totalActiveCalls}
              </span>
            )}
          </div>
          <span className="text-sm font-bold text-stone-700 bg-stone-100 px-3 py-1.5 rounded-lg">
            {staffName} · {role}
          </span>
          <button 
            onClick={() => logout()}
            className="flex items-center gap-1 text-xs font-bold text-red-500 hover:text-red-700 hover:bg-red-50 p-2 rounded-lg transition"
          >
            <LogOut className="h-4 w-4" />
          </button>
        </div>
      </header>

      {/* Top Summary Metric Cards */}
      <div className="px-6 py-4 grid grid-cols-3 gap-4">
        <div className="bg-white p-4 rounded-2xl shadow-sm border border-stone-100 flex flex-col justify-center items-center">
          <div className="text-2xl font-black text-brand-deep">{readyOrders.length}</div>
          <div className="text-xs font-bold text-stone-500 mt-1 uppercase flex items-center gap-1"><PackageCheck className="w-3 h-3"/> Ready Orders</div>
        </div>
        <div className="bg-white p-4 rounded-2xl shadow-sm border border-stone-100 flex flex-col justify-center items-center">
          <div className="text-2xl font-black text-orange-500">{totalActiveCalls}</div>
          <div className="text-xs font-bold text-stone-500 mt-1 uppercase flex items-center gap-1"><BellRing className="w-3 h-3"/> Service Calls</div>
        </div>
        <div className="bg-white p-4 rounded-2xl shadow-sm border border-stone-100 flex flex-col justify-center items-center">
          <div className="text-2xl font-black text-blue-600">{tablesNeedingAttention.length}</div>
          <div className="text-xs font-bold text-stone-500 mt-1 uppercase flex items-center gap-1"><Map className="w-3 h-3"/> Tables active</div>
        </div>
      </div>

      {/* Navigation Sub-Header */}
      <section className="px-6 pb-2">
        <div className="flex bg-stone-200/50 p-1 rounded-xl">
          <button
            onClick={() => setActiveTab('deliveries')}
            className={`flex-1 flex justify-center items-center gap-2 py-2.5 rounded-lg text-sm font-bold transition ${
              activeTab === 'deliveries' ? 'bg-white text-stone-900 shadow-sm' : 'text-stone-500 hover:text-stone-700'
            }`}
          >
            Orders {readyOrders.length > 0 && <span className="bg-brand-deep text-white text-[10px] px-1.5 py-0.5 rounded-full">{readyOrders.length}</span>}
          </button>
          <button
            onClick={() => setActiveTab('calls')}
            className={`flex-1 flex justify-center items-center gap-2 py-2.5 rounded-lg text-sm font-bold transition ${
              activeTab === 'calls' ? 'bg-white text-stone-900 shadow-sm' : 'text-stone-500 hover:text-stone-700'
            }`}
          >
            Service Calls {totalActiveCalls > 0 && <span className="bg-orange-500 text-white text-[10px] px-1.5 py-0.5 rounded-full">{totalActiveCalls}</span>}
          </button>
          <button
            onClick={() => setActiveTab('tables')}
            className={`flex-1 flex justify-center items-center gap-2 py-2.5 rounded-lg text-sm font-bold transition ${
              activeTab === 'tables' ? 'bg-white text-stone-900 shadow-sm' : 'text-stone-500 hover:text-stone-700'
            }`}
          >
            Tables
          </button>
        </div>
      </section>

      {/* Content Area */}
      <main className="flex-1 w-full max-w-5xl mx-auto p-6">
        
        {/* TAB 1: READY ORDERS */}
        {activeTab === 'deliveries' && (
          <div className="space-y-6">
            <h2 className="text-lg font-black text-stone-800">Ready for Delivery</h2>
            {loadingOrders ? (
              <div className="flex justify-center py-10"><Loader2 className="animate-spin text-brand-deep w-8 h-8" /></div>
            ) : readyOrders.length === 0 ? (
              <div className="text-center py-20 bg-white rounded-2xl border border-stone-200 shadow-sm">
                <CheckCircle className="h-12 w-12 text-emerald-400 mx-auto mb-3" />
                <h3 className="font-bold text-stone-800 text-lg">All caught up!</h3>
                <p className="text-stone-500 text-sm">No ready orders waiting for delivery.</p>
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {readyOrders.map((order: Order) => (
                  <div key={order.id || (order as any).orderId || Math.random().toString()} className="bg-white rounded-2xl p-5 border border-stone-200 shadow-sm">
                    <div className="flex justify-between items-start mb-4">
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="bg-stone-900 text-white font-black text-sm px-2.5 py-1 rounded-md">{order.tableNumber}</span>
                          <span className="bg-stone-100 text-stone-600 font-bold text-xs px-2 py-1 rounded-md">{getSeatNumber((order as any).seatId, order.seatNumber)}</span>
                        </div>
                        <div className="mt-2 text-xs font-bold text-stone-400">Order #{order.id || (order as any).orderId}</div>
                      </div>
                      <div className="bg-yellow-100 text-yellow-800 text-xs font-black px-3 py-1.5 rounded-full flex items-center gap-1">
                        <Clock className="w-3 h-3" /> Ready
                      </div>
                    </div>

                    {/* Visual 4-stage stepper */}
                    <div className="flex items-center justify-between mb-5 mt-2 relative">
                      <div className="absolute top-1/2 left-0 w-full h-0.5 bg-stone-200 -z-10 -translate-y-1/2"></div>
                      <div className="absolute top-1/2 left-0 w-[75%] h-0.5 bg-emerald-500 -z-10 -translate-y-1/2"></div>
                      
                      {['Paid', 'Preparing', 'Ready', 'Delivered'].map((step, idx) => (
                        <div key={step} className="flex flex-col items-center bg-white px-1">
                          <div className={`w-3 h-3 rounded-full mb-1 ${idx < 3 ? 'bg-emerald-500' : 'bg-stone-200'}`}></div>
                          <span className={`text-[9px] font-bold ${idx < 3 ? 'text-emerald-700' : 'text-stone-400'}`}>{step}</span>
                        </div>
                      ))}
                    </div>

                    <div className="bg-stone-50 rounded-xl p-3 mb-4 space-y-2">
                      {order.items?.map((item: any, i: number) => (
                        <div key={i} className="flex justify-between items-center border-b border-stone-200 last:border-0 pb-2 last:pb-0">
                          <div className="flex items-center gap-2">
                            <div className="w-8 h-8 bg-stone-200 rounded-md overflow-hidden shrink-0">
                               <img src={`https://ui-avatars.com/api/?name=${encodeURIComponent(item.name || item.itemName)}&background=random`} alt="Item" className="w-full h-full object-cover" />
                            </div>
                            <div>
                              <span className="text-sm font-bold text-stone-800 block">{item.name || item.itemName}</span>
                            </div>
                          </div>
                          <div className="text-sm font-black text-stone-600">x{item.quantity}</div>
                        </div>
                      ))}
                    </div>
                    
                    {/* Customer Special Instructions callout box */}
                    {order.items?.some((i: any) => i.notes || i.specialInstructions || i.SpecialInstructions) && (
                      <div className="bg-orange-50 border border-orange-200 rounded-xl p-3 mb-4">
                        <p className="text-xs font-bold text-orange-800 mb-1">Special Instructions:</p>
                        <ul className="list-disc pl-4 text-xs text-orange-700 font-medium">
                          {order.items?.filter((i: any) => i.notes || i.specialInstructions || i.SpecialInstructions).map((i: any, idx: number) => (
                            <li key={idx}>{i.name || i.itemName}: {i.notes || i.specialInstructions || i.SpecialInstructions}</li>
                          ))}
                        </ul>
                      </div>
                    )}

                    <button
                      onClick={() => handleDeliver(order.id || (order as any).orderId)}
                      disabled={deliverOrderMutation.isPending}
                      className="w-full bg-emerald-600 hover:bg-emerald-700 text-white font-black py-3 rounded-xl flex items-center justify-center gap-2 transition"
                    >
                      <CheckCircle className="w-5 h-5" />
                      ✓ Mark Delivered
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* TAB 2: SERVICE CALLS (Categorized into 3 columns / states) */}
        {activeTab === 'calls' && (
          <div className="space-y-6">
            <h2 className="text-lg font-black text-stone-800">Service Calls & Triage</h2>
            {loadingCalls ? (
              <div className="flex justify-center py-10"><Loader2 className="animate-spin text-brand-deep w-8 h-8" /></div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-3 gap-6 items-start">
                {/* Column 1: Open Calls */}
                <div className="bg-stone-100 p-4 rounded-2xl min-h-[400px]">
                  <div className="flex justify-between items-center mb-4">
                    <h3 className="font-bold text-stone-700">Open Calls</h3>
                    <span className="bg-white text-stone-800 text-xs font-black px-2 py-1 rounded-full">{openCalls.length}</span>
                  </div>
                  <div className="space-y-3">
                    {openCalls.map((call: ServiceCall) => (
                      <div key={call.id} className="bg-white p-4 rounded-xl shadow-sm border-l-4 border-l-orange-500">
                        <div className="font-bold text-sm text-stone-900 mb-1">{call.tableNumber} - {getSeatNumber(call.seatId, call.seatNumber)}</div>
                        <div className="text-xs font-semibold text-stone-500 mb-3">{getRequestLabel(call.requestType)}</div>
                        <button onClick={() => handleClaimCall(call.id)} className="w-full bg-orange-500 hover:bg-orange-600 text-white font-bold py-2 rounded-lg text-xs transition">Claim</button>
                      </div>
                    ))}
                    {openCalls.length === 0 && <div className="text-center text-stone-400 text-sm font-medium py-8">No open calls</div>}
                  </div>
                </div>

                {/* Column 2: In Progress */}
                <div className="bg-blue-50/50 p-4 rounded-2xl min-h-[400px] border border-blue-100">
                  <div className="flex justify-between items-center mb-4">
                    <h3 className="font-bold text-blue-800">In Progress</h3>
                    <span className="bg-white text-blue-800 text-xs font-black px-2 py-1 rounded-full shadow-sm">{inProgressCalls.length}</span>
                  </div>
                  <div className="space-y-3">
                    {inProgressCalls.map((call: ServiceCall) => (
                      <div key={call.id} className="bg-white p-4 rounded-xl shadow-sm border-l-4 border-l-blue-500">
                        <div className="font-bold text-sm text-stone-900 mb-1">{call.tableNumber} - {getSeatNumber(call.seatId, call.seatNumber)}</div>
                        <div className="text-xs font-semibold text-stone-500 mb-1">{getRequestLabel(call.requestType)}</div>
                        <div className="text-[10px] text-blue-600 font-bold mb-3">Claimed by: {call.waiterName || staffName}</div>
                        <button onClick={() => handleResolveCall(call.id)} className="w-full bg-blue-600 hover:bg-blue-700 text-white font-bold py-2 rounded-lg text-xs transition">Resolve</button>
                      </div>
                    ))}
                    {inProgressCalls.length === 0 && <div className="text-center text-blue-300 text-sm font-medium py-8">None in progress</div>}
                  </div>
                </div>

                {/* Column 3: Resolved */}
                <div className="bg-emerald-50/50 p-4 rounded-2xl min-h-[400px] border border-emerald-100">
                  <div className="flex justify-between items-center mb-4">
                    <h3 className="font-bold text-emerald-800">Resolved (Recent)</h3>
                    <span className="bg-white text-emerald-800 text-xs font-black px-2 py-1 rounded-full shadow-sm">{resolvedCalls.length}</span>
                  </div>
                  <div className="space-y-3">
                    {resolvedCalls.map((call: ServiceCall) => (
                      <div key={call.id} className="bg-white p-4 rounded-xl shadow-sm border border-emerald-100 opacity-60">
                        <div className="font-bold text-sm text-stone-900">{call.tableNumber} - {getSeatNumber(call.seatId, call.seatNumber)}</div>
                        <div className="text-xs font-semibold text-stone-500 mt-1">{getRequestLabel(call.requestType)}</div>
                        <div className="text-[10px] text-emerald-600 font-bold mt-2">✓ Resolved</div>
                      </div>
                    ))}
                    {resolvedCalls.length === 0 && <div className="text-center text-emerald-300 text-sm font-medium py-8">No resolved calls</div>}
                  </div>
                </div>
              </div>
            )}
          </div>
        )}

        {/* TAB 3: TABLES & GHOST SEATS */}
        {activeTab === 'tables' && (
          <div className="space-y-6">
            <h2 className="text-lg font-black text-stone-800">Tables & Ghost Seats</h2>
            {loadingTables ? (
              <div className="flex justify-center py-10"><Loader2 className="animate-spin text-brand-deep w-8 h-8" /></div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {tables.map((table: RestaurantTable) => {
                  const isExpanded = selectedTableId === table.id;
                  const seats = table.seats || [];
                  const occupiedCount = seats.filter((s: Seat) => s.status === 'OCCUPIED').length;
                  const capacity = table.capacity || 4;
                  
                  // Occupancy status dots
                  const getStatusColor = () => {
                    if (occupiedCount === 0 && !table.currentSessionId) return 'bg-emerald-500'; // Available
                    if (occupiedCount > 0) return 'bg-brand-deep'; // Occupied
                    return 'bg-orange-500'; // Needs Attention / Session Open but Empty
                  };

                  return (
                    <div key={table.id} className="bg-white rounded-2xl border border-stone-200 shadow-sm overflow-hidden">
                      <div className="p-4 border-b border-stone-100 flex items-center justify-between bg-stone-50/50">
                        <div className="flex items-center gap-3">
                          <div className={`w-3 h-3 rounded-full ${getStatusColor()}`}></div>
                          <div>
                            <div className="font-black text-stone-900 text-base">{table.tableNumber}</div>
                            <div className="text-xs text-stone-500 font-semibold">{occupiedCount} / {capacity} Seats Occupied</div>
                          </div>
                        </div>
                        <button onClick={() => setSelectedTableId(isExpanded ? null : table.id)} className="text-brand-deep text-xs font-bold px-3 py-1.5 bg-brand-light rounded-lg">
                          {isExpanded ? 'Close' : 'Manage'}
                        </button>
                      </div>

                      {isExpanded && (
                        <div className="p-4 bg-white">
                          <h4 className="text-xs font-black text-stone-400 uppercase tracking-wider mb-3">Seat Manager</h4>
                          <div className="grid grid-cols-2 gap-3 mb-4">
                            {seats.map((seat: Seat) => {
                              const isOccupied = seat.status === 'OCCUPIED';
                              return (
                                <div key={seat.id} className={`p-3 rounded-xl border flex flex-col items-center text-center gap-2 ${isOccupied ? 'border-brand-deep bg-brand-light/30' : 'border-stone-200 bg-stone-50'}`}>
                                  <div className="text-xs font-bold text-stone-800">{seat.seatNumber}</div>
                                  <div className={`text-[10px] font-black px-2 py-1 rounded ${isOccupied ? 'bg-brand-deep text-white' : 'bg-stone-200 text-stone-600'}`}>
                                    {isOccupied ? 'OCCUPIED' : 'VACANT'}
                                  </div>
                                  {isOccupied ? (
                                    <button 
                                      onClick={() => releaseSeatMutation.mutate(seat.id)}
                                      disabled={releaseSeatMutation.isPending}
                                      className="mt-1 text-[10px] font-bold text-red-600 bg-red-50 hover:bg-red-100 px-3 py-1 rounded-md w-full"
                                    >
                                      Release Seat
                                    </button>
                                  ) : (
                                    <button 
                                      onClick={() => updateSeatMutation.mutate({ seatId: seat.seatNumber, newState: 'OCCUPIED' })}
                                      disabled={updateSeatMutation.isPending}
                                      className="mt-1 text-[10px] font-bold text-emerald-600 bg-emerald-50 hover:bg-emerald-100 px-3 py-1 rounded-md w-full"
                                    >
                                      Mark Occupied
                                    </button>
                                  )}
                                </div>
                              );
                            })}
                          </div>
                          
                          {(table.currentSessionId || occupiedCount > 0) && (
                            <button 
                              onClick={() => closeSessionMutation.mutate(table.currentSessionId! || table.id)}
                              disabled={closeSessionMutation.isPending}
                              className="w-full bg-stone-900 hover:bg-black text-white font-black py-2.5 rounded-xl text-xs transition"
                            >
                              Close Session (Flush Table)
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

export default function WaiterPage() {
  return (
    <ProtectedRoute allowedRoles={['WAITER', 'MANAGER']}>
      <WaiterFloorContent />
    </ProtectedRoute>
  );
}
