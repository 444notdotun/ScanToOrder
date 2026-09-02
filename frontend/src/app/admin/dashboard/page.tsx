'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { api } from "@/lib/api";
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { managerService } from '@/services/manager.service';
import { useAuthStore } from '@/store/authStore';
import ProtectedRoute from '@/components/ProtectedRoute';
import { Worker, Order, RestaurantTable } from '@/types';
import { 
  TrendingUp, Users, Star, PlusCircle, Trash2, Settings, Power, 
  MapPin, LogOut, ArrowRight, TableProperties, ChefHat, UserCheck, Loader2
} from 'lucide-react';

function ManagerDashboardContent() {
  const queryClient = useQueryClient();
  const logout = useAuthStore((state) => state.logout);
  const workerProfile = useAuthStore((state) => state.workerProfile);

  // Workers state
  const [newWorkerName, setNewWorkerName] = useState('');
  const [newWorkerUsername, setNewWorkerUsername] = useState('');
  const [newWorkerRole, setNewWorkerRole] = useState<'WAITER' | 'CHEF' | 'MANAGER'>('WAITER');

  // Query database metrics
  const { data: workers = [] } = useQuery<Worker[]>({
    queryKey: ['adminWorkers'],
    queryFn: async () => {
      const res: any = await api.get('/api/v1/workers');
      const workersData = Array.isArray(res.data) ? res.data : (res.data?.data || res.data?.workers || []);
      return Array.isArray(workersData) ? workersData : [];
    },
  });

  const { data: activeSessionsCount = 0 } = useQuery<number>({
    queryKey: ['adminActiveSessions'],
    queryFn: async () => {
      const res: any = await api.get('/api/v1/sessions/active/count');
      return res?.data?.data ?? res?.data ?? 0;
    }
  });

  const { data: tables = [] } = useQuery<RestaurantTable[]>({
    queryKey: ['adminTables'],
    queryFn: () => managerService.getTables(),
  });

  const { data: menu = [] } = useQuery({ queryKey: ['adminMenu'], queryFn: () => managerService.getMenuAdmin() });
  const { data: orders = [] } = useQuery<Order[]>({
    queryKey: ['adminOrders'],
    queryFn: async () => {
      try {
        const [res1, res2, res3] = await Promise.all([
          api.get('/api/v1/kitchen/orders'),
          api.get('/api/v1/kitchen/orders?status=READY'),
          api.get('/api/v1/kitchen/orders?status=DELIVERED')
        ]);
        
        const flatten = (res: any) => {
           const data = Array.isArray(res.data) ? res.data : (res.data?.data || res.data?.orders || []);
           return Array.isArray(data) ? data : [];
        };

        return [...flatten(res1), ...flatten(res2), ...flatten(res3)];
      } catch (e) {
        // Fallback if APIs fail
        const res: any = await api.get('/api/v1/orders/paid').catch(() => ({ data: [] }));
        const data = Array.isArray(res.data) ? res.data : (res.data?.data || res.data?.orders || []);
        return Array.isArray(data) ? data : [];
      }
    },
  });

  // Workers Mutations
  const createWorkerMutation = useMutation({
    mutationFn: (data: { name: string; role: string; username: string }) => 
      managerService.createWorker(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adminWorkers'] });
      setNewWorkerName('');
      setNewWorkerUsername('');
      setNewWorkerRole('WAITER');
    },
    onError: (err: any) => {
      alert("Failed to create staff profile: " + err.message);
    }
  });

  const deleteWorkerMutation = useMutation({
    mutationFn: (id: string) => managerService.deleteWorker(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adminWorkers'] });
    },
    onError: (err: any) => {
      alert("Failed to remove staff profile: " + err.message);
    }
  });

  const handleCreateWorker = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newWorkerName.trim() || !newWorkerUsername.trim()) {
      alert("Please fill in all staff fields");
      return;
    }
    createWorkerMutation.mutate({
      name: newWorkerName,
      username: newWorkerUsername.replace('@', '').trim(),
      role: newWorkerRole
    });
  };

  const formatNaira = (amount: number) => {
    return '₦' + Number(amount).toLocaleString('en-NG');
  };

  // Compute operational statistics
  const completedOrders = orders.filter((o: Order) => o.status === 'DELIVERED');
  const todayRevenue = completedOrders.reduce((sum: number, o: Order) => {
    if (o.totalAmount) return sum + o.totalAmount;
    // Fallback: calculate from menu items
    const orderItemsTotal = (o.items || []).reduce((itemSum, item: any) => {
      const menuItem = menu.find(m => m.name === (item.itemName || item.name));
      return itemSum + ((menuItem?.price || 0) * (item.quantity || 1));
    }, 0);
    return sum + orderItemsTotal;
  }, 0);
  
  const cookingOrdersCount = orders.filter((o: Order) => o.status === 'PREPARING').length;

  return (
    <div className="flex-1 bg-manager-dashboard min-h-screen flex flex-col">
      {/* Header */}
      <header className="bg-white border-b border-stone-200/50 px-8 py-4 flex items-center justify-between sticky top-0 z-30 shadow-2xs">
        <div className="flex items-center gap-2">
          <Settings className="h-5.5 w-5.5 text-stone-800" />
          <h1 className="text-base font-black text-stone-900 uppercase tracking-tight">Manager Dashboard</h1>
        </div>

        <div className="flex items-center gap-4">
          <span className="text-[10px] font-black text-stone-500 bg-stone-100 px-3 py-1.5 rounded-lg border border-stone-200/20">
            Manager: @{workerProfile?.username || 'admin'}
          </span>
          <button 
            onClick={() => logout()}
            className="flex items-center gap-1 text-[10px] font-extrabold text-red-500 hover:text-red-700 bg-red-50 hover:bg-red-100 px-3 py-1.5 rounded-lg border border-red-200/30 transition cursor-pointer"
          >
            <LogOut className="h-3 w-3" />
            Sign Out
          </button>
        </div>
      </header>

      {/* Main Grid Content */}
      <main className="max-w-7xl mx-auto w-full p-6 space-y-6">
        
        {/* Welcome Greeting */}
        <div className="bg-white rounded-3xl p-6 border border-stone-200/50 shadow-2xs">
          <h2 className="text-sm font-black text-stone-900 uppercase tracking-tight">
            Welcome, @{workerProfile?.username || 'Manager'}
          </h2>
          <p className="text-[11px] text-stone-500 font-medium mt-1">Here is the overview of today's restaurant operations.</p>
        </div>

        {/* Row 1: KPI Statistics Cards */}
        <section className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4">
          <div className="bg-white rounded-2xl p-5 border border-stone-250/20 shadow-3xs">
            <span className="text-[10px] font-black text-stone-400 uppercase tracking-wider block">Today's Revenue</span>
            <span className="text-xl font-black text-emerald-600 mt-2 block">{formatNaira(todayRevenue)}</span>
          </div>

          <div className="bg-white rounded-2xl p-5 border border-stone-250/20 shadow-3xs">
            <span className="text-[10px] font-black text-stone-400 uppercase tracking-wider block">Active Sessions</span>
            <span className="text-xl font-black text-stone-800 mt-2 block">{activeSessionsCount}</span>
          </div>

          <div className="bg-white rounded-2xl p-5 border border-stone-250/20 shadow-3xs">
            <span className="text-[10px] font-black text-stone-400 uppercase tracking-wider block">Active Cooking</span>
            <span className="text-xl font-black text-brand-deep mt-2 block">{cookingOrdersCount}</span>
          </div>

          <div className="bg-white rounded-2xl p-5 border border-stone-250/20 shadow-3xs">
            <span className="text-[10px] font-black text-stone-400 uppercase tracking-wider block">Average Rating</span>
            <span className="text-xl font-black text-amber-500 mt-2 block flex items-center gap-1">
              <Star className="h-5 w-5 fill-amber-400 text-amber-400" />
              4.9 / 5
            </span>
          </div>
        </section>

        {/* Row 2: Navigation Links */}
        <section className="grid grid-cols-1 md:grid-cols-3 gap-6">
          
          {/* Seating manager navigation card */}
          <div className="bg-white rounded-3xl p-6 border border-stone-200/50 shadow-2xs flex flex-col justify-between space-y-4">
            <div className="space-y-2">
              <div className="inline-flex p-3 bg-brand-light text-brand-deep rounded-2xl">
                <TableProperties className="h-6 w-6" />
              </div>
              <h3 className="text-sm font-black text-stone-900 uppercase tracking-wider">Floor Seating</h3>
              <p className="text-xs text-stone-500 leading-normal">
                Register dining tables, view active capacity layout maps, and generate high-correction QR codes for print.
              </p>
            </div>
            <Link 
              href="/admin/tables"
              className="w-full bg-stone-950 hover:bg-stone-850 text-white font-bold py-3 rounded-2xl text-xs flex items-center justify-center gap-1.5 transition"
            >
              Open Floor Seating
              <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          </div>

          {/* Menu manager navigation card */}
          <div className="bg-white rounded-3xl p-6 border border-stone-200/50 shadow-2xs flex flex-col justify-between space-y-4">
            <div className="space-y-2">
              <div className="inline-flex p-3 bg-brand-light text-brand-deep rounded-2xl">
                <ChefHat className="h-6 w-6" />
              </div>
              <h3 className="text-sm font-black text-stone-900 uppercase tracking-wider">Menu Catalog</h3>
              <p className="text-xs text-stone-500 leading-normal">
                Create dish categories, manage menu item availability flags, edit prices, and describe culinary items.
              </p>
            </div>
            <Link 
              href="/admin/menu"
              className="w-full bg-stone-950 hover:bg-stone-850 text-white font-bold py-3 rounded-2xl text-xs flex items-center justify-center gap-1.5 transition"
            >
              Open Menu Catalog
              <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          </div>

          {/* KDS navigation card */}
          <div className="bg-white rounded-3xl p-6 border border-stone-200/50 shadow-2xs flex flex-col justify-between space-y-4">
            <div className="space-y-2">
              <div className="inline-flex p-3 bg-status-prep-bg text-status-prep-text rounded-2xl">
                <UserCheck className="h-6 w-6" />
              </div>
              <h3 className="text-sm font-black text-stone-900 uppercase tracking-wider">Kitchen Display (KDS)</h3>
              <p className="text-xs text-stone-500 leading-normal">
                Track tickets dispatch logs and push preparation states for incoming client dine-in tickets.
              </p>
            </div>
            <Link 
              href="/kitchen/kds"
              className="w-full bg-stone-950 hover:bg-stone-850 text-white font-bold py-3 rounded-2xl text-xs flex items-center justify-center gap-1.5 transition"
            >
              Open Kitchen Monitor
              <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          </div>

        </section>

        {/* Row 3: Staff Management & Live Logs */}
        <section className="grid grid-cols-1 lg:grid-cols-5 gap-8 items-start">
          
          {/* Add Staff form */}
          <div className="lg:col-span-2 bg-white border border-stone-200/50 rounded-3xl p-6 shadow-2xs space-y-4">
            <h3 className="text-xs font-black uppercase text-stone-400 tracking-wider">Create Staff Profile</h3>
            
            <form onSubmit={handleCreateWorker} className="space-y-4 text-xs">
              <div className="space-y-1.5">
                <label className="font-extrabold text-stone-700">Full Name:</label>
                <input
                  type="text"
                  required
                  value={newWorkerName}
                  onChange={(e) => setNewWorkerName(e.target.value)}
                  placeholder="e.g. John Doe"
                  className="w-full rounded-xl border border-stone-200 bg-white px-3 py-2.5 text-stone-950 focus:border-brand-accent focus:outline-none"
                />
              </div>

              <div className="space-y-1.5">
                <label className="font-extrabold text-stone-700">Username (Login credential):</label>
                <input
                  type="text"
                  required
                  value={newWorkerUsername}
                  onChange={(e) => setNewWorkerUsername(e.target.value)}
                  placeholder="e.g. johndoe"
                  className="w-full rounded-xl border border-stone-200 bg-white px-3 py-2.5 text-stone-950 focus:border-brand-accent focus:outline-none font-mono"
                />
              </div>

              <div className="space-y-1.5">
                <label className="font-extrabold text-stone-700">Service Role:</label>
                <select
                  value={newWorkerRole}
                  onChange={(e) => setNewWorkerRole(e.target.value as any)}
                  className="w-full rounded-xl border border-stone-200 bg-white px-3 py-2.5 text-stone-950 focus:border-brand-accent focus:outline-none"
                >
                  <option value="WAITER">Waiter</option>
                  <option value="CHEF">Chef</option>
                  <option value="MANAGER">Manager</option>
                </select>
              </div>

              <button
                type="submit"
                disabled={createWorkerMutation.isPending}
                className="w-full bg-brand-deep hover:bg-brand-accent text-white font-bold py-3.5 rounded-xl flex items-center justify-center gap-1.5 shadow-xs transition cursor-pointer"
              >
                {createWorkerMutation.isPending ? (
                  <Loader2 className="h-4.5 w-4.5 animate-spin" />
                ) : (
                  <>
                    <PlusCircle className="h-4.5 w-4.5" />
                    Register Staff Profile
                  </>
                )}
              </button>
            </form>
          </div>

          {/* Active Staff List */}
          <div className="lg:col-span-3 bg-white border border-stone-200/50 rounded-3xl p-6 shadow-2xs space-y-4">
            <h3 className="text-xs font-black uppercase text-stone-400 tracking-wider">Active Staff Profiles</h3>
            
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="border-b border-stone-100 text-stone-400 font-extrabold uppercase text-[10px]">
                    <th className="pb-3 pl-2">Name</th>
                    <th className="pb-3">Username</th>
                    <th className="pb-3">Role</th>
                    <th className="pb-3 pr-2 text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-stone-50">
                  {(!workers || workers.length === 0) && (
                    <tr>
                      <td colSpan={5} className="text-center py-6 text-stone-500 text-sm">
                        No staff/workers found.
                      </td>
                    </tr>
                  )}
                  {(Array.isArray(workers) ? workers : []).map((w, idx) => (
                    <tr key={w?.id || w?.username || idx} className="text-stone-700 font-semibold">
                      <td className="py-3.5 pl-2">{w.name}</td>
                      <td className="font-mono text-[11px] text-stone-500">@{w.username}</td>
                      <td>
                        <span className={`px-2.5 py-0.5 rounded-md text-[9px] font-bold uppercase ${
                          w.role === 'MANAGER' ? 'bg-stone-200 text-stone-700' :
                          w.role === 'CHEF' ? 'bg-status-prep-bg text-status-prep-text' :
                          'bg-status-success-bg text-status-success-text'
                        }`}>
                          {w.role}
                        </span>
                      </td>
                      <td className="text-right pr-2">
                        <button
                          onClick={() => deleteWorkerMutation.mutate((w.id || w.workerId) as string)}
                          disabled={deleteWorkerMutation.isPending}
                          className="text-red-500 hover:text-red-700 p-2 hover:bg-red-50 rounded-lg transition cursor-pointer"
                          title="Remove Worker"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

        </section>

      </main>
    </div>
  );
}

export default function ManagerDashboard() {
  return (
    <ProtectedRoute allowedRoles={['MANAGER']}>
      <ManagerDashboardContent />
    </ProtectedRoute>
  );
}
