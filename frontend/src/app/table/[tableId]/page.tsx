'use client';

import React, { useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { useQuery, useMutation } from '@tanstack/react-query';
import { customerService } from '@/services/customer.service';
import { useCustomerStore } from '@/store/customerStore';
import { Seat } from '@/types';
import { Loader2, Armchair, HelpCircle } from 'lucide-react';

export default function TablePage() {
  const router = useRouter();
  const { tableId } = useParams() as { tableId: string };
  const setSeat = useCustomerStore((state) => state.setSeat);

  const [selectedSeatId, setSelectedSeatId] = useState<string | null>(null);

  // Fetch Table details & Seat map
  const { data: seatMap, isLoading, error, refetch } = useQuery<any>({
    queryKey: ['seatMap', tableId],
    queryFn: () => customerService.getSeatMap(tableId),
    refetchInterval: 5000, // Poll seat map changes
  });

  // Seat claim mutation
  const claimSeatMutation = useMutation({
    mutationFn: (seatId: string) => customerService.claimSeat(seatId),
    onSuccess: (data) => {
      // Store session data in Zustand
      setSeat(data.seatId, data.tableId, data.sessionId);
      // Redirect to menu page
      router.push('/menu');
    },
    onError: (err) => {
      alert("Failed to claim seat: " + (err as any).message);
    }
  });

  const handleClaim = () => {
    if (selectedSeatId) {
      claimSeatMutation.mutate(selectedSeatId);
    }
  };

  if (isLoading) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center p-6 bg-[#F8F6F2]">
        <Loader2 className="h-8 w-8 text-brand-deep animate-spin mb-3" />
        <p className="text-stone-500 text-sm font-medium">Loading seat map...</p>
      </div>
    );
  }

  if (error || !seatMap) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center p-6 bg-[#F8F6F2] text-center">
        <HelpCircle className="h-10 w-10 text-red-500 mb-3" />
        <p className="text-stone-700 font-bold mb-4">Failed to load table details</p>
        <button 
          onClick={() => refetch()}
          className="bg-brand-deep text-white px-5 py-2.5 rounded-xl text-sm font-semibold"
        >
          Retry Scan
        </button>
      </div>
    );
  }

  const seats = seatMap.seats || [];

  return (
    <div className="flex-1 bg-customer-food min-h-screen py-8 px-4 flex flex-col justify-between">
      <div className="max-w-md mx-auto w-full space-y-8 flex-1 flex flex-col justify-center">
        
        {/* Header */}
        <div className="text-center space-y-1.5">
          <h1 className="text-2xl font-black text-stone-900">
            Welcome to Table {seatMap.tableNumber}
          </h1>
          <p className="text-xs text-stone-500 font-medium">
            Please choose a seat to start your order
          </p>
        </div>

        {/* Circular Table Component */}
        <div className="relative aspect-square w-full max-w-[280px] mx-auto flex items-center justify-center">
          
          {/* Physical Table (Center Circle) */}
          <div className="w-[140px] h-[140px] rounded-full bg-white border border-stone-200/60 shadow-xs flex flex-col items-center justify-center z-10 text-center">
            <span className="text-xs font-black text-stone-400 uppercase tracking-widest">Table</span>
            <span className="text-3xl font-black text-brand-deep">{seatMap.tableNumber}</span>
          </div>

          {/* Seats placed around the circle */}
          {seats.map((seat: Seat, index: number) => {
            // Position seat using trigonometry
            const angle = (index * 2 * Math.PI) / seats.length - Math.PI / 2; // start at top (offset by 90deg)
            const radius = 95; // px from center
            const x = Math.cos(angle) * radius;
            const y = Math.sin(angle) * radius;

            const isOccupied = seat.status === 'OCCUPIED';
            const isSelected = selectedSeatId === seat.id;

            let seatClass = "bg-white border-stone-200 text-stone-700 hover:bg-stone-50 shadow-2xs";
            if (isOccupied) {
              seatClass = "bg-stone-200 border-stone-300 text-stone-400 cursor-not-allowed opacity-60";
            } else if (isSelected) {
              seatClass = "bg-brand-light border-brand-deep text-brand-deep ring-2 ring-brand-deep scale-110 shadow-md";
            }

            return (
              <button
                key={seat.id}
                disabled={isOccupied}
                onClick={() => setSelectedSeatId(seat.id)}
                className={`absolute w-12 h-12 rounded-full border flex flex-col items-center justify-center text-xs font-bold transition-all duration-200 ${seatClass}`}
                style={{
                  transform: `translate(${x}px, ${y}px)`
                }}
              >
                <Armchair className={`h-4.5 w-4.5 ${isSelected ? 'text-brand-deep' : isOccupied ? 'text-stone-400' : 'text-stone-500'}`} />
                <span className="text-[9px] -mt-0.5">{seat.seatNumber}</span>
              </button>
            );
          })}
        </div>

        {/* Legend */}
        <div className="flex justify-center items-center gap-6 text-[11px] font-semibold text-stone-600 bg-white/50 py-2.5 px-4 rounded-xl max-w-xs mx-auto border border-stone-200/30">
          <div className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-full bg-white border border-stone-300"></span>
            <span>Vacant</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-full bg-stone-200 border border-stone-300"></span>
            <span>Occupied</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-full bg-brand-light border border-brand-deep"></span>
            <span>Selected</span>
          </div>
        </div>

        {/* Action Button */}
        <div className="pt-4 px-4 space-y-3">
          <button
            disabled={!selectedSeatId || claimSeatMutation.isPending}
            onClick={handleClaim}
            className="flex items-center justify-center gap-2 w-full bg-brand-deep hover:bg-brand-accent disabled:bg-stone-300 disabled:cursor-not-allowed text-white font-bold py-3.5 rounded-2xl shadow-md transition duration-200 text-sm cursor-pointer"
          >
            {claimSeatMutation.isPending ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin" />
                Claiming seat...
              </>
            ) : (
              'Confirm Seating & Order'
            )}
          </button>
          
          <button
            onClick={() => router.push('/')}
            className="w-full text-center text-xs font-bold text-stone-500 hover:text-stone-700 py-1"
          >
            Back to main hub
          </button>
        </div>

      </div>
    </div>
  );
}
