'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { managerService } from '@/services/manager.service';
import { MenuItem, RestaurantTable, Worker, Order } from '@/types';
import { 
  TrendingUp, Users, ChefHat, Star, Plus, Trash2, Edit3, 
  QrCode, Power, ArrowLeft, Settings, PlusCircle 
} from 'lucide-react';

export default function ManagerPage() {
  const queryClient = useQueryClient();
  const [activeSection, setActiveSection] = useState<'kpis' | 'menu' | 'tables' | 'staff'>('kpis');

  // Menu Modals state
  const [isMenuModalOpen, setIsMenuModalOpen] = useState(false);
  const [editingMenuItem, setEditingMenuItem] = useState<MenuItem | null>(null);
  const [menuForm, setMenuForm] = useState({
    name: '',
    description: '',
    price: 0,
    imageUrl: '',
    categoryId: 'c1',
    categoryName: 'Mains',
    available: true
  });

  // Table Modal state
  const [isTableModalOpen, setIsTableModalOpen] = useState(false);
  const [tableForm, setTableForm] = useState({
    tableNumber: 1,
    capacity: 4
  });

  // Worker Modal state
  const [isWorkerModalOpen, setIsWorkerModalOpen] = useState(false);
  const [workerForm, setWorkerForm] = useState({
    name: '',
    role: 'WAITER',
    username: ''
  });

  // 1. Fetch Admin Menu
  const { data: menuItems = [] } = useQuery<MenuItem[]>({
    queryKey: ['adminMenu'],
    queryFn: () => managerService.getMenuAdmin(),
  });

  // 2. Fetch Tables
  const { data: tables = [] } = useQuery<RestaurantTable[]>({
    queryKey: ['adminTables'],
    queryFn: () => managerService.getTables(),
  });

  // 3. Fetch Staff
  const { data: workers = [] } = useQuery<Worker[]>({
    queryKey: ['adminWorkers'],
    queryFn: () => managerService.getWorkers(),
  });

  // 4. Fetch All Orders (to compute analytics)
  const { data: orders = [] } = useQuery<Order[]>({
    queryKey: ['adminOrders'],
    queryFn: () => managerService.getAllOrders(),
  });

  // --- Mutations ---
  
  // Menu Mutations
  const createMenuMutation = useMutation({
    mutationFn: (data: Partial<MenuItem>) => managerService.createMenuItem(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adminMenu'] });
      setIsMenuModalOpen(false);
      resetMenuForm();
    }
  });

  const updateMenuMutation = useMutation({
    mutationFn: (variables: { id: string; data: Partial<MenuItem> }) => 
      managerService.updateMenuItem(variables.id, variables.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adminMenu'] });
      setIsMenuModalOpen(false);
      resetMenuForm();
    }
  });

  const deleteMenuMutation = useMutation({
    mutationFn: (id: string) => managerService.deleteMenuItem(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adminMenu'] });
    }
  });

  const toggleMenuMutation = useMutation({
    mutationFn: (id: string) => managerService.toggleMenuItemAvailability(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adminMenu'] });
    }
  });

  // Table Mutations
  const createTableMutation = useMutation({
    mutationFn: (data: { tableNumber: number; capacity: number }) => managerService.createTable(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adminTables'] });
      setIsTableModalOpen(false);
      setTableForm({ tableNumber: tables.length + 2, capacity: 4 });
    }
  });

  const deleteTableMutation = useMutation({
    mutationFn: (id: string) => managerService.deleteTable(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adminTables'] });
    }
  });

  // Worker Mutations
  const createWorkerMutation = useMutation({
    mutationFn: (data: { name: string; role: string; username: string }) => managerService.createWorker(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adminWorkers'] });
      setIsWorkerModalOpen(false);
      setWorkerForm({ name: '', role: 'WAITER', username: '' });
    }
  });

  const deleteWorkerMutation = useMutation({
    mutationFn: (id: string) => managerService.deleteWorker(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adminWorkers'] });
    }
  });

  // Forms Helpers
  const resetMenuForm = () => {
    setEditingMenuItem(null);
    setMenuForm({
      name: '',
      description: '',
      price: 0,
      imageUrl: '',
      categoryId: 'c1',
      categoryName: 'Mains',
      available: true
    });
  };

  const handleOpenEditMenu = (item: MenuItem) => {
    setEditingMenuItem(item);
    setMenuForm({
      name: item.name,
      description: item.description,
      price: item.price,
      imageUrl: item.imageUrl || '',
      categoryId: item.categoryId,
      categoryName: item.categoryName || 'Mains',
      available: item.available
    });
    setIsMenuModalOpen(true);
  };

  const handleSaveMenu = (e: React.FormEvent) => {
    e.preventDefault();
    if (editingMenuItem) {
      updateMenuMutation.mutate({ id: editingMenuItem.id, data: menuForm });
    } else {
      createMenuMutation.mutate(menuForm);
    }
  };

  const formatNaira = (amount: number) => {
    return '₦' + Number(amount).toLocaleString('en-NG');
  };

  // Compute stats
  const completedOrders = orders.filter((o: Order) => o.status === 'DELIVERED');
  const todayRevenue = completedOrders.reduce((sum: number, o: Order) => sum + o.totalAmount, 0);
  const activeSessionsCount = tables.filter((t: RestaurantTable) => t.currentSessionId).length;
  const cookingOrdersCount = orders.filter((o: Order) => o.status === 'PREPARING').length;

  return (
    <div className="flex-1 bg-manager-dashboard min-h-screen flex flex-col">
      {/* Top Header */}
      <header className="bg-white border-b border-stone-200/50 px-8 py-4 flex items-center justify-between sticky top-0 z-30 shadow-2xs">
        <div className="flex items-center gap-4">
          <Link href="/" className="p-2 hover:bg-stone-50 rounded-xl text-stone-500 transition">
            <ArrowLeft className="h-4.5 w-4.5" />
          </Link>
          <div className="flex items-center gap-2">
            <Settings className="h-5.5 w-5.5 text-stone-800" />
            <h1 className="text-base font-black text-stone-900 uppercase tracking-tight">Manager Dashboard</h1>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <span className="text-[10px] font-black text-stone-500 bg-stone-100 px-3 py-1.5 rounded-lg border border-stone-200/20">
            Role: Administrator
          </span>
        </div>
      </header>

      {/* Main Grid: Nav Side & Main View */}
      <div className="flex-1 max-w-7xl mx-auto w-full p-6 grid grid-cols-1 lg:grid-cols-4 gap-8 items-start">
        
        {/* Navigation Sidebar */}
        <aside className="lg:col-span-1 bg-white border border-stone-200/50 rounded-3xl p-5 shadow-2xs space-y-4">
          <h2 className="text-xs font-black uppercase text-stone-400 tracking-wider px-2">Control Panel</h2>
          <div className="flex flex-col gap-1.5">
            {[
              { id: 'kpis', label: 'Operations KPI' },
              { id: 'menu', label: 'Menu Catalog' },
              { id: 'tables', label: 'Floor Seating' },
              { id: 'staff', label: 'Staff Management' },
            ].map(tab => (
              <button
                key={tab.id}
                onClick={() => setActiveSection(tab.id as any)}
                className={`w-full text-left py-2.5 px-4 rounded-xl text-xs font-bold transition cursor-pointer ${
                  activeSection === tab.id
                    ? 'bg-brand-deep text-white shadow-2xs'
                    : 'text-stone-600 hover:bg-stone-50'
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>
        </aside>

        {/* Main Content Area */}
        <main className="lg:col-span-3 space-y-6">

          {/* SECTION 1: OPERATIONS KPI */}
          {activeSection === 'kpis' && (
            <div className="space-y-6 animate-fade-in">
              
              {/* KPI Cards Grid */}
              <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <div className="bg-white rounded-2xl p-4 border border-stone-200/40 shadow-3xs">
                  <span className="text-[10px] font-black text-stone-400 uppercase tracking-wider block">Today's Revenue</span>
                  <span className="text-lg font-black text-emerald-600 mt-2 block">{formatNaira(todayRevenue)}</span>
                </div>

                <div className="bg-white rounded-2xl p-4 border border-stone-200/40 shadow-3xs">
                  <span className="text-[10px] font-black text-stone-400 uppercase tracking-wider block">Active Sessions</span>
                  <span className="text-lg font-black text-stone-800 mt-2 block">{activeSessionsCount}</span>
                </div>

                <div className="bg-white rounded-2xl p-4 border border-stone-200/40 shadow-3xs">
                  <span className="text-[10px] font-black text-stone-400 uppercase tracking-wider block">Active Cooking</span>
                  <span className="text-lg font-black text-brand-deep mt-2 block">{cookingOrdersCount}</span>
                </div>

                <div className="bg-white rounded-2xl p-4 border border-stone-200/40 shadow-3xs">
                  <span className="text-[10px] font-black text-stone-400 uppercase tracking-wider block">Average Rating</span>
                  <span className="text-lg font-black text-amber-500 mt-2 block flex items-center gap-1">
                    <Star className="h-4.5 w-4.5 fill-amber-400 text-amber-400" />
                    4.8 / 5
                  </span>
                </div>
              </div>

              {/* Traffic Index Card */}
              <div className="bg-white rounded-3xl p-6 border border-stone-200/50 shadow-2xs space-y-4">
                <div className="flex justify-between items-center border-b border-stone-100 pb-3">
                  <h3 className="text-sm font-black text-stone-900 uppercase tracking-wider">Restaurant Traffic Index</h3>
                  <span className="text-[10px] text-emerald-600 font-bold bg-emerald-50 px-2 py-0.5 rounded-md">Live Analytics</span>
                </div>
                <div className="h-40 bg-stone-50 rounded-2xl border border-stone-100 p-4 flex flex-col justify-end gap-2">
                  <div className="flex items-end justify-between h-full px-4">
                    {[20, 35, 45, 90, 75, 40, 60, 85, 30].map((val, i) => (
                      <div key={i} className="flex flex-col items-center gap-1 flex-1">
                        <div 
                          className="w-4 sm:w-6 bg-brand-deep rounded-t-xs hover:bg-brand-accent transition-all duration-300"
                          style={{ height: `${val}%` }}
                        ></div>
                        <span className="text-[9px] text-stone-400 font-semibold">{12 + i}:00</span>
                      </div>
                    ))}
                  </div>
                </div>
                <p className="text-[10px] text-stone-500 font-medium leading-relaxed">
                  Peak hour traffic peaks around 15:00. Operations are running within standard timelines (average delivery wait: 12 minutes).
                </p>
              </div>

              {/* Recent Orders Table */}
              <div className="bg-white rounded-3xl p-6 border border-stone-200/50 shadow-2xs space-y-4">
                <h3 className="text-sm font-black text-stone-900 uppercase tracking-wider">Recent Orders Log</h3>
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs border-collapse">
                    <thead>
                      <tr className="border-b border-stone-100 text-stone-400 font-extrabold uppercase text-[10px]">
                        <th className="pb-3 pl-2">Order ID</th>
                        <th className="pb-3">Table/Seat</th>
                        <th className="pb-3">Subtotal</th>
                        <th className="pb-3">Status</th>
                        <th className="pb-3 pr-2 text-right">Time</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-stone-50">
                      {orders.length === 0 ? (
                        <tr>
                          <td colSpan={5} className="py-8 text-center text-stone-400 font-semibold">No orders logged today.</td>
                        </tr>
                      ) : (
                        orders.slice(-5).map((o: Order) => (
                          <tr key={o.id} className="text-stone-700 font-semibold">
                            <td className="py-3 pl-2 text-brand-deep">#{o.id}</td>
                            <td>T{o.tableNumber} - S{o.seatNumber}</td>
                            <td className="font-extrabold">{formatNaira(o.totalAmount)}</td>
                            <td>
                              <span className={`px-2 py-0.5 rounded-md text-[9px] font-bold uppercase ${
                                o.status === 'DELIVERED' ? 'bg-status-delivered-bg text-status-delivered-text' :
                                o.status === 'PAID' ? 'bg-status-success-bg text-status-success-text' :
                                o.status === 'PREPARING' ? 'bg-status-prep-bg text-status-prep-text' :
                                o.status === 'READY' ? 'bg-status-ready-bg text-status-ready-text' :
                                'bg-red-50 text-red-600'
                              }`}>
                                {o.status}
                              </span>
                            </td>
                            <td className="text-right pr-2 text-[10px] text-stone-400">
                              {new Date(o.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              </div>

            </div>
          )}

          {/* SECTION 2: MENU CATALOG CRUD */}
          {activeSection === 'menu' && (
            <div className="bg-white rounded-3xl p-6 border border-stone-200/50 shadow-2xs space-y-4 animate-fade-in">
              <div className="flex justify-between items-center pb-2 border-b border-stone-100">
                <h3 className="text-sm font-black text-stone-900 uppercase tracking-wider">Menu Catalog</h3>
                <button
                  onClick={() => { resetMenuForm(); setIsMenuModalOpen(true); }}
                  className="bg-brand-deep hover:bg-brand-accent text-white font-bold px-3.5 py-2 rounded-xl text-xs flex items-center gap-1 shadow-3xs cursor-pointer"
                >
                  <PlusCircle className="h-4 w-4" /> Add Menu Item
                </button>
              </div>

              {/* Menu Grid */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {menuItems.map((item: MenuItem) => (
                  <div 
                    key={item.id}
                    className="p-3 bg-stone-50/50 rounded-2xl border border-stone-100 flex items-start justify-between gap-3"
                  >
                    <div className="flex items-start gap-2.5">
                      {item.imageUrl && (
                        <div className="w-12 h-12 rounded-xl overflow-hidden bg-stone-100 shrink-0">
                          <img src={item.imageUrl} alt={item.name} className="w-full h-full object-cover" />
                        </div>
                      )}
                      <div>
                        <h4 className="text-xs font-bold text-stone-900 leading-tight">{item.name}</h4>
                        <span className="text-[9px] font-black text-brand-deep uppercase block mt-0.5">{formatNaira(item.price)}</span>
                        <p className="text-[10px] text-stone-400 mt-1 line-clamp-1 leading-normal font-medium">{item.description}</p>
                      </div>
                    </div>

                    <div className="flex items-center gap-2 shrink-0">
                      {/* Availability Toggle */}
                      <button
                        onClick={() => toggleMenuMutation.mutate(item.id)}
                        className={`p-1.5 rounded-lg border transition ${
                          item.available 
                            ? 'bg-status-success-bg border-status-success-text text-status-success-text'
                            : 'bg-red-50 border-red-200 text-red-500'
                        }`}
                        title="Toggle availability (86 toggle)"
                      >
                        <Power className="h-3.5 w-3.5" />
                      </button>

                      {/* Edit */}
                      <button
                        onClick={() => handleOpenEditMenu(item)}
                        className="p-1.5 rounded-lg border border-stone-200 hover:bg-white text-stone-600 transition"
                      >
                        <Edit3 className="h-3.5 w-3.5" />
                      </button>

                      {/* Delete */}
                      <button
                        onClick={() => deleteMenuMutation.mutate(item.id)}
                        className="p-1.5 rounded-lg border border-red-100 bg-red-50 hover:bg-red-100 text-red-500 transition"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* SECTION 3: FLOOR SEATING & QR GENERATION */}
          {activeSection === 'tables' && (
            <div className="bg-white rounded-3xl p-6 border border-stone-200/50 shadow-2xs space-y-4 animate-fade-in">
              <div className="flex justify-between items-center pb-2 border-b border-stone-100">
                <h3 className="text-sm font-black text-stone-900 uppercase tracking-wider">Tables Floor Seating</h3>
                <button
                  onClick={() => setIsTableModalOpen(true)}
                  className="bg-brand-deep hover:bg-brand-accent text-white font-bold px-3.5 py-2 rounded-xl text-xs flex items-center gap-1 shadow-3xs cursor-pointer"
                >
                  <PlusCircle className="h-4 w-4" /> Add Table
                </button>
              </div>

              {/* Table Grid */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
                {tables.map((table: RestaurantTable) => (
                  <div 
                    key={table.id}
                    className="p-4 bg-stone-50/50 rounded-2xl border border-stone-100 text-center space-y-4 flex flex-col justify-between"
                  >
                    <div>
                      <h4 className="font-extrabold text-stone-800 text-sm">Table {table.tableNumber}</h4>
                      <p className="text-[10px] text-stone-400 font-semibold mt-1">Capacity: {table.capacity} Seats</p>
                    </div>

                    {/* QR Code Container */}
                    <div className="bg-white p-2 rounded-xl border border-stone-200/50 inline-block mx-auto">
                      {table.qrCodeUrl ? (
                        <img src={table.qrCodeUrl} alt={`QR Code Table ${table.tableNumber}`} className="w-24 h-24 mx-auto" />
                      ) : (
                        <QrCode className="w-24 h-24 text-stone-300 mx-auto" />
                      )}
                    </div>

                    <div className="flex items-center gap-2 pt-2">
                      <a
                        href={table.qrCodeUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="flex-1 bg-stone-900 hover:bg-stone-800 text-white font-bold py-2 rounded-lg text-[10px] transition text-center"
                      >
                        Download QR
                      </a>
                      <button
                        onClick={() => deleteTableMutation.mutate(table.id)}
                        className="bg-red-50 hover:bg-red-100 text-red-500 border border-red-100 p-2 rounded-lg transition cursor-pointer"
                        title="Remove Table"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* SECTION 4: STAFF MANAGEMENT */}
          {activeSection === 'staff' && (
            <div className="bg-white rounded-3xl p-6 border border-stone-200/50 shadow-2xs space-y-4 animate-fade-in">
              <div className="flex justify-between items-center pb-2 border-b border-stone-100">
                <h3 className="text-sm font-black text-stone-900 uppercase tracking-wider">Staff Management</h3>
                <button
                  onClick={() => setIsWorkerModalOpen(true)}
                  className="bg-brand-deep hover:bg-brand-accent text-white font-bold px-3.5 py-2 rounded-xl text-xs flex items-center gap-1 shadow-3xs cursor-pointer"
                >
                  <PlusCircle className="h-4 w-4" /> Add Staff Profile
                </button>
              </div>

              {/* Workers Table */}
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
                    {workers.map((w: Worker) => (
                      <tr key={w.id} className="text-stone-700 font-semibold">
                        <td className="py-3 pl-2">{w.name}</td>
                        <td className="font-mono text-[11px] text-stone-500">@{w.username}</td>
                        <td>
                          <span className={`px-2 py-0.5 rounded-md text-[9px] font-bold uppercase ${
                            w.role === 'MANAGER' ? 'bg-stone-200 text-stone-700' :
                            w.role === 'CHEF' ? 'bg-status-prep-bg text-status-prep-text' :
                            'bg-status-success-bg text-status-success-text'
                          }`}>
                            {w.role}
                          </span>
                        </td>
                        <td className="text-right pr-2">
                          <button
                            onClick={() => deleteWorkerMutation.mutate(w.id)}
                            className="text-red-500 hover:text-red-700 p-1.5 hover:bg-red-50 rounded-lg transition cursor-pointer"
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
          )}

        </main>
      </div>

      {/* --- Modals Dialogs --- */}

      {/* Menu Item Form Modal */}
      {isMenuModalOpen && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs flex items-center justify-center z-50 p-4">
          <form onSubmit={handleSaveMenu} className="bg-white rounded-3xl w-full max-w-md p-6 space-y-4 shadow-xl">
            <h3 className="text-sm font-black text-stone-900 uppercase tracking-wider pb-1 border-b border-stone-100">
              {editingMenuItem ? 'Edit Menu Item' : 'Create Menu Item'}
            </h3>
            
            <div className="space-y-3 text-xs">
              <div className="space-y-1">
                <label className="font-bold text-stone-700">Item Name:</label>
                <input
                  type="text"
                  required
                  value={menuForm.name}
                  onChange={(e) => setMenuForm({...menuForm, name: e.target.value})}
                  className="w-full rounded-xl border border-stone-200 px-3 py-2 text-stone-900 focus:outline-none focus:border-brand-accent transition"
                />
              </div>

              <div className="space-y-1">
                <label className="font-bold text-stone-700">Description:</label>
                <textarea
                  required
                  value={menuForm.description}
                  onChange={(e) => setMenuForm({...menuForm, description: e.target.value})}
                  rows={2}
                  className="w-full rounded-xl border border-stone-200 px-3 py-2 text-stone-900 focus:outline-none focus:border-brand-accent transition"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <label className="font-bold text-stone-700">Price (₦):</label>
                  <input
                    type="number"
                    required
                    value={menuForm.price}
                    onChange={(e) => setMenuForm({...menuForm, price: parseFloat(e.target.value)})}
                    className="w-full rounded-xl border border-stone-200 px-3 py-2 text-stone-900 focus:outline-none focus:border-brand-accent transition"
                  />
                </div>

                <div className="space-y-1">
                  <label className="font-bold text-stone-700">Category:</label>
                  <select
                    value={menuForm.categoryId}
                    onChange={(e) => {
                      const sel = e.target.value;
                      const catName = sel === 'c1' ? 'Mains' : sel === 'c2' ? 'Appetizers' : sel === 'c3' ? 'Drinks' : 'Desserts';
                      setMenuForm({...menuForm, categoryId: sel, categoryName: catName});
                    }}
                    className="w-full rounded-xl border border-stone-200 px-3 py-2 text-stone-900 focus:outline-none focus:border-brand-accent transition"
                  >
                    <option value="c1">Mains</option>
                    <option value="c2">Appetizers</option>
                    <option value="c3">Drinks</option>
                    <option value="c4">Desserts</option>
                  </select>
                </div>
              </div>

              <div className="space-y-1">
                <label className="font-bold text-stone-700">Image URL:</label>
                <input
                  type="text"
                  value={menuForm.imageUrl}
                  onChange={(e) => setMenuForm({...menuForm, imageUrl: e.target.value})}
                  placeholder="https://example.com/food.jpg"
                  className="w-full rounded-xl border border-stone-200 px-3 py-2 text-stone-900 focus:outline-none focus:border-brand-accent transition"
                />
              </div>
            </div>

            <div className="flex justify-end gap-3 pt-3">
              <button
                type="button"
                onClick={() => setIsMenuModalOpen(false)}
                className="px-4 py-2 border border-stone-200 hover:bg-stone-50 rounded-xl text-stone-600 font-bold text-xs"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="bg-brand-deep hover:bg-brand-accent text-white font-bold px-4 py-2 rounded-xl text-xs shadow-xs"
              >
                Save Item
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Table Form Modal */}
      {isTableModalOpen && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-3xl w-full max-w-sm p-6 space-y-4 shadow-xl">
            <h3 className="text-sm font-black text-stone-900 uppercase tracking-wider pb-1 border-b border-stone-100">
              Create Table
            </h3>
            
            <div className="space-y-3 text-xs">
              <div className="space-y-1">
                <label className="font-bold text-stone-700">Table Number:</label>
                <input
                  type="number"
                  required
                  value={tableForm.tableNumber}
                  onChange={(e) => setTableForm({...tableForm, tableNumber: parseInt(e.target.value)})}
                  className="w-full rounded-xl border border-stone-200 px-3 py-2 text-stone-900 focus:outline-none focus:border-brand-accent transition"
                />
              </div>

              <div className="space-y-1">
                <label className="font-bold text-stone-700">Capacity (Seats):</label>
                <input
                  type="number"
                  required
                  value={tableForm.capacity}
                  onChange={(e) => setTableForm({...tableForm, capacity: parseInt(e.target.value)})}
                  className="w-full rounded-xl border border-stone-200 px-3 py-2 text-stone-900 focus:outline-none focus:border-brand-accent transition"
                />
              </div>
            </div>

            <div className="flex justify-end gap-3 pt-3">
              <button
                onClick={() => setIsTableModalOpen(false)}
                className="px-4 py-2 border border-stone-200 hover:bg-stone-50 rounded-xl text-stone-600 font-bold text-xs"
              >
                Cancel
              </button>
              <button
                onClick={() => createTableMutation.mutate(tableForm)}
                className="bg-brand-deep hover:bg-brand-accent text-white font-bold px-4 py-2 rounded-xl text-xs shadow-xs"
              >
                Create
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Staff Form Modal */}
      {isWorkerModalOpen && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-3xl w-full max-w-sm p-6 space-y-4 shadow-xl">
            <h3 className="text-sm font-black text-stone-900 uppercase tracking-wider pb-1 border-b border-stone-100">
              Create Worker Profile
            </h3>
            
            <div className="space-y-3 text-xs">
              <div className="space-y-1">
                <label className="font-bold text-stone-700">Name:</label>
                <input
                  type="text"
                  required
                  value={workerForm.name}
                  onChange={(e) => setWorkerForm({...workerForm, name: e.target.value})}
                  className="w-full rounded-xl border border-stone-200 px-3 py-2 text-stone-900 focus:outline-none focus:border-brand-accent transition"
                />
              </div>

              <div className="space-y-1">
                <label className="font-bold text-stone-700">Username:</label>
                <input
                  type="text"
                  required
                  value={workerForm.username}
                  onChange={(e) => setWorkerForm({...workerForm, username: e.target.value})}
                  className="w-full rounded-xl border border-stone-200 px-3 py-2 text-stone-900 focus:outline-none focus:border-brand-accent transition font-mono"
                />
              </div>

              <div className="space-y-1">
                <label className="font-bold text-stone-700">Role:</label>
                <select
                  value={workerForm.role}
                  onChange={(e) => setWorkerForm({...workerForm, role: e.target.value})}
                  className="w-full rounded-xl border border-stone-200 px-3 py-2 text-stone-900 focus:outline-none focus:border-brand-accent transition"
                >
                  <option value="WAITER">Waiter</option>
                  <option value="CHEF">Chef</option>
                  <option value="MANAGER">Manager</option>
                </select>
              </div>
            </div>

            <div className="flex justify-end gap-3 pt-3">
              <button
                onClick={() => setIsWorkerModalOpen(false)}
                className="px-4 py-2 border border-stone-200 hover:bg-stone-50 rounded-xl text-stone-600 font-bold text-xs"
              >
                Cancel
              </button>
              <button
                onClick={() => createWorkerMutation.mutate(workerForm)}
                className="bg-brand-deep hover:bg-brand-accent text-white font-bold px-4 py-2 rounded-xl text-xs shadow-xs"
              >
                Add Staff
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}
