'use client';

import React from 'react';
import { api, publicApi } from "@/lib/api";
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { managerService } from '@/services/manager.service';
import { customerService } from '@/services/customer.service';
import { RestaurantTable } from '@/types';
import {
  QrCode, ChefHat, UserCheck, Settings, Utensils,
  Sparkles, Loader2, CircleDot, CheckCircle2, AlertCircle,
} from 'lucide-react';

/* ─── Table availability badge ─────────────────────────────────────── */
function StatusBadge({ status }: { status?: string }) {
  const isOccupied = status === 'OCCUPIED';
  return (
    <span
      className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider ${
        isOccupied
          ? 'bg-red-50 text-red-500 border border-red-100'
          : 'bg-emerald-50 text-emerald-600 border border-emerald-100'
      }`}
    >
      {isOccupied ? (
        <CircleDot className="h-2.5 w-2.5" />
      ) : (
        <CheckCircle2 className="h-2.5 w-2.5" />
      )}
      {isOccupied ? 'Occupied' : 'Available'}
    </span>
  );
}

/* ─── Individual table card — live status from /tables/{num}/status ─── */
function TableCard({ table, index }: { table: RestaurantTable; index: number }) {
  const tableNum = table.tableNumber?.toString() ?? `${index + 1}`;

  // GET /api/v1/tables/{tableNumber}/status → 'AVAILABLE' | 'OCCUPIED'
  // Backend runs the real seat-check: if all seats are HELD/OCCUPIED → OCCUPIED
  const { data: liveStatus, isLoading: statusLoading } = useQuery<string>({
    queryKey: ['tableStatus', tableNum],
    queryFn: () => customerService.syncTableStatus(tableNum),
    refetchInterval: 10_000,
    staleTime: 5_000,
  });

  // Use live status from backend; fall back to static field while loading
  const resolvedStatus = liveStatus ?? table.status ?? (table.isOccupied ? 'OCCUPIED' : 'AVAILABLE');
  const isOccupied = resolvedStatus === 'OCCUPIED';

  return (
    <div
      className={`flex flex-col justify-between gap-3 p-4 rounded-2xl border text-left transition-all ${
        isOccupied
          ? 'bg-stone-50 border-stone-200 opacity-70'
          : 'bg-white border-brand-light/60 hover:border-brand-accent hover:shadow-sm'
      }`}
    >
      {/* Top row */}
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="text-xs text-stone-500 font-semibold">Table</p>
          <h4 className="font-extrabold text-stone-900 text-base leading-tight truncate">
            {tableNum}
          </h4>
          <p className="text-[10px] text-stone-400 mt-0.5">
            {table.capacity > 0 ? `${table.capacity} seats` : 'N/A'}
          </p>
        </div>
        {/* Show spinner while fetching live status, then show badge */}
        {statusLoading ? (
          <Loader2 className="h-3.5 w-3.5 animate-spin text-stone-300 mt-0.5 flex-shrink-0" />
        ) : (
          <StatusBadge status={resolvedStatus} />
        )}
      </div>

      {/* Action */}
      <Link
        href={isOccupied ? '#' : `/table/${tableNum}`}
        onClick={(e) => {
          if (isOccupied) {
            e.preventDefault();
            alert(`Table ${tableNum} is currently occupied.`);
          }
        }}
        className={`flex items-center justify-center gap-1.5 w-full font-bold py-2 rounded-xl text-xs transition ${
          isOccupied
            ? 'bg-stone-100 text-stone-400 cursor-not-allowed'
            : 'bg-brand-deep hover:bg-brand-accent text-white shadow-xs'
        }`}
        aria-disabled={isOccupied}
        tabIndex={isOccupied ? -1 : 0}
      >
        <Utensils className="h-3.5 w-3.5" />
        {isOccupied ? 'Table Occupied' : 'Scan & Order'}
      </Link>
    </div>
  );
}


/* ─── Main page ─────────────────────────────────────────────────────── */
export default function Home() {
  const {
    data: tables = [],
    isLoading,
    isError,
  } = useQuery<RestaurantTable[]>({
    queryKey: ['tables-public'],
    queryFn: async () => {
      const res = await publicApi.get('/api/v1/tables');
      const tablesData = Array.isArray(res.data?.data) 
        ? res.data.data 
        : Array.isArray(res.data) ? res.data : [];
      return tablesData.map((t: any) => ({
        ...t,
        capacity: t.capacity || t.Capacity
      }));
    },
    refetchInterval: 30_000,
    staleTime: 15_000,
  });

  const availableCount = tables.filter(
    (t) => !(t.isOccupied ?? t.status === 'OCCUPIED'),
  ).length;

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
        <p className="text-stone-600 max-w-md md:max-w-3xl lg:max-w-5xl mx-auto text-sm sm:text-base">
          Experience an interactive, real-time dine-in workflow connecting diners,
          kitchen staff, waiters, and managers.
        </p>
      </header>

      {/* Main Grid */}
      <main className="max-w-5xl mx-auto w-full grid grid-cols-1 md:grid-cols-2 gap-8 flex-1 items-stretch">

        {/* ── Diner Simulator ──────────────────────────────────────── */}
        <section className="glass-panel rounded-3xl p-8 flex flex-col gap-6 shadow-sm transition-all duration-300 hover:shadow-md">
          {/* Section header */}
          <div className="flex items-center gap-3">
            <div className="p-3 bg-brand-light text-brand-deep rounded-2xl">
              <QrCode className="h-6 w-6" />
            </div>
            <div>
              <h2 className="text-xl font-bold text-stone-950">Diner Simulator</h2>
              <p className="text-xs text-stone-500">Scan QR to claim a seat &amp; order</p>
            </div>
          </div>

          <p className="text-stone-600 text-sm leading-relaxed">
            Simulate a customer scanning a table&apos;s QR code. Select any available
            table below to load the seat layout and open the digital menu.
          </p>

          {/* ── Table grid ─────────────────────────────────────────── */}
          <div className="flex-1">
            {isLoading ? (
              <div className="flex items-center justify-center gap-2 py-10 text-stone-400">
                <Loader2 className="h-5 w-5 animate-spin" />
                <span className="text-xs font-semibold">Loading tables…</span>
              </div>
            ) : isError ? (
              <div className="flex flex-col items-center justify-center gap-2 py-10 text-red-400">
                <AlertCircle className="h-5 w-5" />
                <p className="text-xs font-semibold text-center">
                  Could not reach the backend. Start the Spring Boot server and refresh.
                </p>
              </div>
            ) : tables.length === 0 ? (
              <div className="flex flex-col items-center justify-center gap-2 py-10 text-stone-400">
                <Utensils className="h-5 w-5" />
                <p className="text-xs font-semibold text-center">
                  No tables yet. Open <strong>Manager Operations</strong> to create tables.
                </p>
              </div>
            ) : (
              <>
                {/* Summary pill */}
                <p className="text-[11px] text-stone-400 font-semibold mb-3">
                  {availableCount} of {tables.length} table
                  {tables.length !== 1 ? 's' : ''} available
                </p>

                {/* Dynamic table cards grid */}
                <div className="grid grid-cols-2 gap-3">
                  {tables.map((table, index) => (
                    <TableCard
                      key={table.id || `table-${index}`}
                      table={table}
                      index={index}
                    />
                  ))}
                </div>
              </>
            )}
          </div>
        </section>

        {/* ── Staff & Admin ─────────────────────────────────────────── */}
        <section className="space-y-6 flex flex-col justify-between">
          <div className="grid grid-cols-1 gap-4">

            {/* KDS */}
            <Link
              href="/kitchen/kds"
              className="group glass-panel rounded-2xl p-5 flex items-center justify-between border border-stone-100 hover:border-brand-light hover:bg-white transition-all shadow-2xs hover:shadow-xs"
            >
              <div className="flex items-center gap-4">
                <div className="p-3 bg-status-prep-bg text-status-prep-text rounded-xl group-hover:scale-105 transition-transform">
                  <ChefHat className="h-5 w-5" />
                </div>
                <div>
                  <h3 className="font-bold text-stone-900 text-sm">Kitchen Display (KDS)</h3>
                  <p className="text-xs text-stone-500">Track tickets &amp; push prep states</p>
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
                  <p className="text-xs text-stone-500">Handle deliveries &amp; service calls</p>
                </div>
              </div>
              <span className="text-xs font-semibold text-brand-deep group-hover:translate-x-1 transition-transform">
                Open Floor &rarr;
              </span>
            </Link>

            {/* Manager */}
            <Link
              href="/admin/dashboard"
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

