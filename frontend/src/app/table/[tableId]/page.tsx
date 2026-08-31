'use client';

import React, { useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { useQuery, useMutation } from '@tanstack/react-query';
import { customerService } from '@/services/customer.service';
import { useCustomerStore } from '@/store/customerStore';
import { Loader2, Flame, Lock, X } from 'lucide-react';

type SeatStatus = 'VACANT' | 'HELD' | 'OCCUPIED';

interface SeatFromMap {
  seatNumber: string;
  status: SeatStatus;
}

export default function TablePage() {
  const router = useRouter();
  const { tableId } = useParams() as { tableId: string };
  const setSeat = useCustomerStore((state) => state.setSeat);

  const [selectedSeatNumber, setSelectedSeatNumber] = useState<string | null>(null);
  
  // Modal state for customer details
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [formData, setFormData] = useState({
    customerName: '',
    customerPhoneNumber: '',
    customerEmail: ''
  });

  // Single call — GET /api/v1/tables/{tableNumber}/seatMap
  const { data: seatMap, isLoading, error, refetch } = useQuery<any>({
    queryKey: ['seatMap', tableId],
    queryFn: () => customerService.getSeatMap(tableId),
    refetchInterval: 5000,
  });

  const claimSeatMutation = useMutation({
    mutationFn: (payload: { tableId: string; seatId: string; customerEmail?: string; customerName: string; customerPhoneNumber: string }) => 
      customerService.claimSeat(payload),
    onSuccess: (data) => {
      setSeat(data.seatId, data.tableId, data.sessionId);
      // Navigate to the Customer Menu (Screen 02)
      router.push(`/menu?table=${encodeURIComponent(data?.tableId || tableId || '1')}&seat=${encodeURIComponent(data?.seatId || selectedSeatNumber || '1')}`);
    },
    onError: (err) => {
      // Error handled by global API interceptor via sonner toast
      console.error('Failed to claim seat', err);
    },
  });

  if (isLoading) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center p-6 bg-[#FAF9F6]">
        <Loader2 className="h-8 w-8 text-[#F15927] animate-spin mb-3" />
        <p className="text-stone-500 text-sm font-medium">Loading seat map...</p>
      </div>
    );
  }

  if (error || !seatMap) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center p-6 bg-[#FAF9F6] text-center">
        <Flame className="h-10 w-10 text-[#F15927] mb-3 opacity-50" />
        <p className="text-stone-700 font-bold mb-4">Failed to load table details</p>
        <button
          onClick={() => refetch()}
          className="bg-[#F15927] hover:bg-[#D94819] text-white px-6 py-3 rounded-xl text-sm font-bold shadow-lg shadow-orange-500/20"
        >
          Retry Scan
        </button>
      </div>
    );
  }

  const seats: SeatFromMap[] = Array.isArray(seatMap?.seats) && seatMap.seats.length > 0 
    ? seatMap.seats 
    : Array.from({ length: seatMap?.Capacity || seatMap?.capacity || 4 }, (_, i) => ({
        seatNumber: `Mock-Seat-${i + 1}`,
        status: 'VACANT' as SeatStatus,
      }));
  const tableNumberDisplay = seatMap.tableNumber || tableId.replace('TABLE-', '');
  const selectedSeat = seats.find(s => s.seatNumber === selectedSeatNumber);

  const handleConfirmSeat = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedSeatNumber) return;
    claimSeatMutation.mutate({
      tableId: seatMap.tableNumber || tableId,
      seatId: selectedSeatNumber,
      customerName: formData.customerName.trim(),
      customerPhoneNumber: formData.customerPhoneNumber.trim(),
      customerEmail: formData.customerEmail?.trim() || undefined,
    });
  };

  return (
    <div className="flex-1 bg-[#FAF9F6] min-h-screen py-8 px-5 flex flex-col justify-between font-sans relative overflow-hidden">
      <div className="max-w-md md:max-w-2xl lg:max-w-4xl mx-auto w-full flex-1 flex flex-col relative z-10">

        {/* 1. Header Branding */}
        <div className="text-center mt-4 mb-4">
          <Flame className="mx-auto text-[#F15927] mb-3" size={38} strokeWidth={2.5} />
          <p className="italic text-[#F15927] text-lg mb-1" style={{ fontFamily: 'Georgia, serif' }}>Welcome!</p>
          <h1 className="text-4xl font-extrabold text-stone-900 tracking-tight mb-2"> {tableNumberDisplay} </h1>
          <p className="text-stone-500 font-medium">Choose your seat to get started.</p>
        </div>

        {/* 2. Circular Dining Table & Realistic Furniture Visual */}
        <div className="relative w-72 h-72 md:w-80 md:h-80 mx-auto my-6 flex items-center justify-center">

          {/* Wooden Table (Top-down circular wooden table) */}
          <div className="absolute inset-8 rounded-full bg-gradient-to-br from-[#9c663b] to-[#5C3A21] shadow-[0_15px_35px_rgba(0,0,0,0.35)] border-4 border-[#4a2e15] flex items-center justify-center z-10 overflow-hidden">
             {/* Subtle wood rings for texture */}
             <div className="w-[85%] h-[85%] rounded-full border-[1.5px] border-[#8a552b]/40" />
             <div className="absolute w-[60%] h-[60%] rounded-full border-[1.5px] border-[#8a552b]/30" />
             <div className="absolute w-[30%] h-[30%] rounded-full border border-[#8a552b]/20" />
          </div>

          {/* Dynamic Seat Nodes over Wooden Chair Backs */}
          {seats.map((seat, index) => {
            const angle = (index * 2 * Math.PI) / seats.length - Math.PI / 2; // Starts at -90deg (12 o'clock)
            const radius = 120; // Distance from center
            const x = Math.cos(angle) * radius;
            const y = Math.sin(angle) * radius;
            const rotation = angle * (180 / Math.PI) + 90; // Chair back faces out

            const isOccupied = seat.status === 'OCCUPIED' || seat.status === 'HELD';
            const isSelected = selectedSeatNumber === seat.seatNumber;

            // Determine badge label
            let statusLabel = 'Available';
            if (isSelected) statusLabel = 'Selected';
            else if (isOccupied) statusLabel = 'Occupied';

            // Determine badge styling based on design requirements
            let nodeStyle = 'bg-white text-[#10B981] border-2 border-[#10B981] shadow-md hover:bg-emerald-50'; // Available (Green accent)
            if (isSelected) {
              nodeStyle = 'bg-[#F15927] text-white border-2 border-[#F15927] shadow-lg shadow-orange-500/40 scale-105'; // Selected (Orange, scaled)
            } else if (isOccupied) {
              nodeStyle = 'bg-[#E5E5EA] text-[#8E8E93] border-2 border-[#D1D1D6] opacity-80 cursor-not-allowed'; // Occupied (Muted stone)
            }

            return (
              <div key={seat.seatNumber || `seat-${index}`} className="absolute z-20 flex flex-col items-center justify-center" style={{ transform: `translate(${x}px, ${y}px)` }}>
                 
                 {/* Wooden Chair Back Graphic (Underneath the badge) */}
                 <div 
                   className="absolute w-12 h-10 bg-gradient-to-b from-[#7c4d29] to-[#5C3A21] rounded-lg shadow-inner border border-[#4a2e15] -z-10" 
                   style={{ transform: `rotate(${rotation}deg)` }} 
                 />

                 {/* Circle node displaying Seat Number */}
                 <button
                    disabled={isOccupied}
                    onClick={() => !isOccupied && setSelectedSeatNumber(seat.seatNumber)}
                    className={`w-11 h-11 rounded-full flex items-center justify-center font-bold text-sm transition-all duration-300 relative ${nodeStyle}`}
                 >
                    {index + 1}
                 </button>

                 {/* Small label underneath */}
                 <div className={`absolute top-12 text-[10px] font-bold tracking-wide uppercase whitespace-nowrap bg-[#FAF9F6]/90 px-1.5 py-0.5 rounded shadow-sm
                    ${isSelected ? 'text-[#F15927]' : isOccupied ? 'text-[#8E8E93]' : 'text-[#10B981]'}
                 `}>
                    {statusLabel}
                 </div>
              </div>
            );
          })}
        </div>

        {/* 3. Bottom CTA & Footnote */}
        <div className="mt-auto pt-6 w-full space-y-4">
          <button
            disabled={!selectedSeatNumber}
            onClick={() => selectedSeatNumber && setIsModalOpen(true)}
            className="bg-[#F15927] hover:bg-[#D94819] disabled:bg-stone-300 disabled:shadow-none text-white font-bold py-4 rounded-2xl w-full shadow-[0_8px_20px_rgba(241,89,39,0.25)] transition-all flex items-center justify-center gap-2 text-base"
          >
            {selectedSeat ? `Claim Seat ${selectedSeat.seatNumber} & View Menu` : 'Select an Available Seat'}
          </button>

          <div className="flex items-center justify-center gap-1.5 text-stone-400 text-xs font-medium pb-2">
            <Lock size={14} />
            <span>No account required</span>
          </div>
        </div>

      </div>

      {/* 4. Customer Details Bottom Sheet Modal */}
      {isModalOpen && selectedSeat && (
        <div className="fixed inset-0 bg-black/40 z-50 backdrop-blur-sm flex items-end sm:items-center justify-center p-0 sm:p-4">
          <div className="bg-white w-full max-w-md md:max-w-2xl lg:max-w-4xl rounded-t-3xl sm:rounded-3xl p-6 shadow-2xl animate-in slide-in-from-bottom-full sm:slide-in-from-bottom-10 duration-300">
            <div className="flex justify-between items-center mb-6">
              <div>
                <h2 className="text-xl font-bold text-stone-900">Join {tableNumberDisplay}</h2>
                <p className="text-sm text-stone-500 mt-0.5">Enter your contact details to start ordering for {selectedSeat.seatNumber}</p>
              </div>
              <button 
                onClick={() => setIsModalOpen(false)} 
                className="p-2 text-stone-400 hover:text-stone-600 bg-stone-100 rounded-full self-start"
              >
                <X size={20} />
              </button>
            </div>

            <form onSubmit={handleConfirmSeat} className="space-y-4">
              <div>
                <label className="block text-sm font-semibold text-stone-700 mb-1.5">Full Name <span className="text-red-500">*</span></label>
                <input 
                  type="text" 
                  required 
                  placeholder="e.g. John Doe" 
                  value={formData.customerName}
                  onChange={(e) => setFormData({...formData, customerName: e.target.value})}
                  className="w-full px-4 py-3 rounded-xl border border-stone-200 focus:outline-none focus:ring-2 focus:ring-[#F15927]/20 focus:border-[#F15927] transition-all bg-stone-50"
                />
              </div>
              <div>
                <label className="block text-sm font-semibold text-stone-700 mb-1.5">Phone Number <span className="text-red-500">*</span></label>
                <input 
                  type="tel" 
                  required 
                  placeholder="e.g. 08012345678" 
                  value={formData.customerPhoneNumber}
                  onChange={(e) => setFormData({...formData, customerPhoneNumber: e.target.value})}
                  className="w-full px-4 py-3 rounded-xl border border-stone-200 focus:outline-none focus:ring-2 focus:ring-[#F15927]/20 focus:border-[#F15927] transition-all bg-stone-50"
                />
              </div>
              <div>
                <label className="block text-sm font-semibold text-stone-700 mb-1.5">Email Address <span className="text-stone-400 font-normal">(Optional)</span></label>
                <input 
                  type="email" 
                  placeholder="e.g. john@example.com (optional)" 
                  value={formData.customerEmail}
                  onChange={(e) => setFormData({...formData, customerEmail: e.target.value})}
                  className="w-full px-4 py-3 rounded-xl border border-stone-200 focus:outline-none focus:ring-2 focus:ring-[#F15927]/20 focus:border-[#F15927] transition-all bg-stone-50"
                />
              </div>

              <button
                type="submit"
                disabled={!formData.customerName.trim() || !formData.customerPhoneNumber.trim() || claimSeatMutation.isPending}
                className="mt-6 bg-[#F15927] hover:bg-[#D94819] disabled:bg-stone-300 disabled:shadow-none text-white font-bold py-4 rounded-2xl w-full shadow-lg shadow-orange-500/20 transition-all flex items-center justify-center gap-2 text-base"
              >
                {claimSeatMutation.isPending ? (
                  <>
                    <Loader2 className="h-5 w-5 animate-spin" />
                    Confirming...
                  </>
                ) : (
                  'Confirm & View Menu'
                )}
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
