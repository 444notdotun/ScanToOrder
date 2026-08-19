'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { QrCode, ChefHat, UserCheck, Settings, Utensils, Sparkles, MapPin } from 'lucide-react';

export default function Home() {
  const [selectedTable, setSelectedTable] = useState('t1');

  const tables = [
    { id: 't1', name: 'Table 1 (4 Seats)' },
    { id: 't2', name: 'Table 2 (4 Seats)' },
    { id: 't3', name: 'Table 3 (6 Seats)' },
    { id: 't4', name: 'Table 4 (4 Seats)' },
    { id: 't5', name: 'Table 5 (4 Seats)' },
    { id: 't6', name: 'Table 6 (6 Seats)' },
    { id: 't7', name: 'Table 7 (4 Seats)' },
    { id: 't8', name: 'Table 8 (4 Seats)' },
  ];

  return (
    <div className="flex-1 flex flex-col justify-between py-12 px-6 sm:px-12 bg-[#F8F6F2]">
      {/* Header */}
      <header className="max-w-5xl mx-auto w-full text-center space-y-3 mb-10">
        <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-brand-light text-brand-deep font-semibold text-xs tracking-wider uppercase">
          <Sparkles className="h-3.5 w-3.5 animate-pulse" />
          QR-Based Restaurant System
        </div>
        <h1 className="text-4xl sm:text-5xl font-extrabold tracking-tight text-stone-900">
          Scan to <span className="text-brand-deep">Order</span>
        </h1>
        <p className="text-stone-600 max-w-md mx-auto text-sm sm:text-base">
          Experience an interactive, real-time dine-in workflow connecting diners, kitchen staff, waiters, and managers.
        </p>
      </header>

      {/* Main Grid */}
      <main className="max-w-5xl mx-auto w-full grid grid-cols-1 md:grid-cols-2 gap-8 flex-1 items-stretch">
        
        {/* Customer Simulator */}
        <section className="glass-panel rounded-3xl p-8 flex flex-col justify-between shadow-sm transition-all duration-300 hover:shadow-md">
          <div className="space-y-6">
            <div className="flex items-center gap-3">
              <div className="p-3 bg-brand-light text-brand-deep rounded-2xl">
                <QrCode className="h-6 w-6" />
              </div>
              <div>
                <h2 className="text-xl font-bold text-stone-950">Diner Simulator</h2>
                <p className="text-xs text-stone-500">Scan QR to claim a seat & order</p>
              </div>
            </div>
            
            <p className="text-stone-600 text-sm leading-relaxed">
              Simulate a customer scanning a table's QR code. Choose a table below to load the seat layout and open the digital menu.
            </p>

            <div className="space-y-2">
              <label className="text-xs font-semibold text-stone-700 flex items-center gap-1.5">
                <MapPin className="h-3 w-3 text-brand-accent" /> Select Table to Scan:
              </label>
              <select
                value={selectedTable}
                onChange={(e) => setSelectedTable(e.target.value)}
                className="w-full rounded-xl border border-stone-200 bg-white px-3.5 py-2.5 text-stone-900 focus:border-brand-accent focus:ring-1 focus:ring-brand-accent focus:outline-none text-sm transition"
              >
                {tables.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="pt-6">
            <Link
              href={`/table/${selectedTable}`}
              className="flex items-center justify-center gap-2 w-full bg-brand-deep hover:bg-brand-accent text-white font-semibold py-3 rounded-2xl transition duration-200 shadow-sm"
            >
              <Utensils className="h-4 w-4" />
              Simulate Table Scan
            </Link>
          </div>
        </section>

        {/* Staff & Admin Section */}
        <section className="space-y-6 flex flex-col justify-between">
          <div className="grid grid-cols-1 gap-4">
            
            {/* KDS */}
            <Link
              href="/kitchen"
              className="group glass-panel rounded-2xl p-5 flex items-center justify-between border border-stone-100 hover:border-brand-light hover:bg-white transition-all shadow-2xs hover:shadow-xs"
            >
              <div className="flex items-center gap-4">
                <div className="p-3 bg-status-prep-bg text-status-prep-text rounded-xl group-hover:scale-105 transition-transform">
                  <ChefHat className="h-5 w-5" />
                </div>
                <div>
                  <h3 className="font-bold text-stone-900 text-sm">Kitchen Display (KDS)</h3>
                  <p className="text-xs text-stone-500">Track tickets & push prep states</p>
                </div>
              </div>
              <span className="text-xs font-semibold text-brand-deep group-hover:translate-x-1 transition-transform">
                Open KDS &rarr;
              </span>
            </Link>

            {/* Waiter */}
            <Link
              href="/waiter"
              className="group glass-panel rounded-2xl p-5 flex items-center justify-between border border-stone-100 hover:border-brand-light hover:bg-white transition-all shadow-2xs hover:shadow-xs"
            >
              <div className="flex items-center gap-4">
                <div className="p-3 bg-status-success-bg text-status-success-text rounded-xl group-hover:scale-105 transition-transform">
                  <UserCheck className="h-5 w-5" />
                </div>
                <div>
                  <h3 className="font-bold text-stone-900 text-sm">Waiter Service Desk</h3>
                  <p className="text-xs text-stone-500">Handle deliveries & service calls</p>
                </div>
              </div>
              <span className="text-xs font-semibold text-brand-deep group-hover:translate-x-1 transition-transform">
                Open Floor &rarr;
              </span>
            </Link>

            {/* Manager */}
            <Link
              href="/manager"
              className="group glass-panel rounded-2xl p-5 flex items-center justify-between border border-stone-100 hover:border-brand-light hover:bg-white transition-all shadow-2xs hover:shadow-xs"
            >
              <div className="flex items-center gap-4">
                <div className="p-3 bg-stone-200 text-stone-700 rounded-xl group-hover:scale-105 transition-transform">
                  <Settings className="h-5 w-5" />
                </div>
                <div>
                  <h3 className="font-bold text-stone-900 text-sm">Manager Operations</h3>
                  <p className="text-xs text-stone-500">Menu CRUD, analytics, QR codes</p>
                </div>
              </div>
              <span className="text-xs font-semibold text-stone-600 group-hover:translate-x-1 transition-transform">
                Open Admin &rarr;
              </span>
            </Link>

          </div>

          <div className="text-center text-xs text-stone-400">
            System utilizes synchronized local state context for fast offline simulation.
          </div>
        </section>
      </main>

      {/* Footer */}
      <footer className="text-center text-xs text-stone-400 pt-8 mt-6">
        &copy; 2026 Scan to Order Restaurant Systems.
      </footer>
    </div>
  );
}
