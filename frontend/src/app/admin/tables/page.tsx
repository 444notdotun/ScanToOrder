'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { managerService } from '@/services/manager.service';
import ProtectedRoute from '@/components/ProtectedRoute';
import { RestaurantTable } from '@/types';
import { 
  ArrowLeft, PlusCircle, Trash2, QrCode, Loader2, ArrowRight, Settings
} from 'lucide-react';

function TablesManagerContent() {
  const queryClient = useQueryClient();
  const [seatCapacity, setSeatCapacity] = useState(4);

  // Fetch Tables
  const { data: tables = [], isLoading, error } = useQuery<RestaurantTable[]>({
    queryKey: ['adminTables'],
    queryFn: () => managerService.getTables(),
  });

  // Table Mutations
  const createTableMutation = useMutation({
    mutationFn: (capacity: number) => managerService.createTable({ tableNumber: 0, capacity }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adminTables'] });
      setSeatCapacity(4);
    },
    onError: (err: any) => {
      alert("Failed to create table: " + err.message);
    }
  });

  const deleteTableMutation = useMutation({
    mutationFn: (id: string) => managerService.deleteTable(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adminTables'] });
    },
    onError: (err: any) => {
      alert("Failed to delete table: " + err.message);
    }
  });

  const handleCreateTable = (e: React.FormEvent) => {
    e.preventDefault();
    if (seatCapacity < 4) {
      alert("Seat capacity must be at least 4");
      return;
    }
    createTableMutation.mutate(seatCapacity);
  };

  const triggerQrDownload = (tableNumber: string) => {
    // Construct QR code image endpoint directly
    const url = `${process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1'}/tables/${tableNumber}/qrcode`;
    
    // Create an anchor tag to download
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `table_${tableNumber}_qr.png`);
    document.body.appendChild(link);
    link.click();
    link.remove();
  };

  return (
    <div className="flex-1 bg-manager-dashboard min-h-screen flex flex-col">
      {/* Header */}
      <header className="bg-white border-b border-stone-200/50 px-8 py-4 flex items-center justify-between sticky top-0 z-30 shadow-2xs">
        <div className="flex items-center gap-4">
          <Link href="/admin/dashboard" className="p-2 hover:bg-stone-50 rounded-xl text-stone-500 transition">
            <ArrowLeft className="h-4.5 w-4.5" />
          </Link>
          <div className="flex items-center gap-2">
            <Settings className="h-5.5 w-5.5 text-stone-800" />
            <h1 className="text-base font-black text-stone-900 uppercase tracking-tight">Floor Seating Manager</h1>
          </div>
        </div>
      </header>

      {/* Content */}
      <main className="max-w-6xl mx-auto w-full p-6 grid grid-cols-1 lg:grid-cols-4 gap-8 items-start">
        
        {/* Creator Form */}
        <section className="lg:col-span-1 bg-white border border-stone-200/50 rounded-3xl p-6 shadow-2xs space-y-4">
          <h2 className="text-xs font-black uppercase text-stone-400 tracking-wider">Create New Table</h2>
          
          <form onSubmit={handleCreateTable} className="space-y-4 text-xs">
            <div className="space-y-1.5">
              <label className="font-extrabold text-stone-700">Seat Capacity:</label>
              <input
                type="number"
                min={4}
                required
                value={seatCapacity}
                onChange={(e) => setSeatCapacity(parseInt(e.target.value))}
                className="w-full rounded-xl border border-stone-200 bg-white px-3 py-2.5 text-stone-900 focus:border-brand-accent focus:ring-1 focus:ring-brand-accent focus:outline-none"
              />
              <span className="text-[10px] text-stone-400 font-semibold block leading-normal">
                Min capacity requirement is 4.
              </span>
            </div>

            <button
              type="submit"
              disabled={createTableMutation.isPending}
              className="w-full bg-brand-deep hover:bg-brand-accent text-white font-bold py-3 rounded-xl flex items-center justify-center gap-1.5 shadow-xs transition cursor-pointer"
            >
              {createTableMutation.isPending ? (
                <Loader2 className="h-4.5 w-4.5 animate-spin" />
              ) : (
                <>
                  <PlusCircle className="h-4.5 w-4.5" />
                  Create Table
                </>
              )}
            </button>
          </form>
        </section>

        {/* Seating Grid */}
        <section className="lg:col-span-3 bg-white border border-stone-200/50 rounded-3xl p-6 shadow-2xs space-y-4">
          <h2 className="text-xs font-black uppercase text-stone-400 tracking-wider">Active Tables Floor Plan</h2>
          
          {isLoading ? (
            <div className="py-12 flex justify-center">
              <Loader2 className="h-8 w-8 animate-spin text-brand-deep" />
            </div>
          ) : error ? (
            <p className="text-center text-xs text-red-500 font-bold py-12">Failed to load floor seating plan.</p>
          ) : tables.length === 0 ? (
            <p className="text-center text-xs text-stone-400 font-bold py-12">No active tables found on the floor. Create one to begin!</p>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-5">
              {tables.map((table, index) => {
                const qrEndpoint = `${process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1'}/tables/${table.tableNumber}/qrcode`;
                return (
                  <div 
                    key={table.id || table.tableNumber || index}
                    className="p-4 bg-stone-50/50 rounded-2xl border border-stone-100 text-center space-y-4 flex flex-col justify-between"
                  >
                    <div>
                      <h4 className="font-extrabold text-stone-850 text-sm">{table.tableNumber}</h4>
                      <p className="text-[10px] text-stone-400 font-semibold mt-1">Capacity: {table.capacity} Seats</p>
                    </div>

                    <div className="bg-white p-2 rounded-xl border border-stone-200/50 inline-block mx-auto flex items-center justify-center w-24 h-24">
                      {/* SVG Placeholder for QR Code to prevent NS_BINDING_ABORTED errors */}
                      <QrCode className="h-12 w-12 text-stone-300" />
                    </div>

                    <div className="flex items-center gap-2 pt-1.5">
                      <button
                        onClick={() => triggerQrDownload(table.tableNumber.toString())}
                        className="flex-1 bg-stone-900 hover:bg-stone-800 text-white font-bold py-2.5 rounded-lg text-[10px] transition text-center cursor-pointer"
                      >
                        Download QR
                      </button>
                      <button
                        onClick={() => deleteTableMutation.mutate(table.id)}
                        disabled={deleteTableMutation.isPending}
                        className="bg-red-50 hover:bg-red-100 text-red-500 border border-red-100 p-2.5 rounded-lg transition cursor-pointer"
                        title="Remove Table"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </section>

      </main>
    </div>
  );
}

export default function TablesManagerPage() {
  return (
    <ProtectedRoute allowedRoles={['MANAGER']}>
      <TablesManagerContent />
    </ProtectedRoute>
  );
}
