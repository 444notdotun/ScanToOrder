
"use client";
import React, { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { api } from '@/lib/api';
import { toast } from 'sonner';

export default function SeatSelector({ params }: { params: { tableId: string } }) {
  const [seats, setSeats] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const router = useRouter();

  const fetchSeats = async () => {
    try {
      // Use the actual existing seatMap endpoint from TableController
      const response = await api.get(`/api/v1/tables/${params.tableId}/seatMap`);
      const payload = response.data?.data?.seats || response.data?.seats || [];
      setSeats(Array.isArray(payload) ? payload : []);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSeats();
  }, [params.tableId]);


  const handleClaimSeat = async (seatNumber: string) => {
    try {
      // Use existing endpoint structure that expects tableNumber and seatNumber
      await api.post('/api/v1/seats/claim', {
        tableId: params.tableId, // Expected to be tableNumber string
        seatId: seatNumber,      // Expected to be seatNumber string
        customerName: 'Guest Diner',
        customerEmail: 'guest@example.com',
        customerPhoneNumber: '0000000000'
      });
      
      // Cookie is set automatically via HttpOnly
      localStorage.setItem('tableNumber', params.tableId as string);
      localStorage.setItem('seatId', seatNumber);
      toast.success('Seat claimed successfully!');
      router.push(`/menu?table=${encodeURIComponent(params.tableId as string || '1')}&seat=${encodeURIComponent(seatNumber || '1')}`);
    } catch (error: any) {
      console.error(error);
      // Let global interceptor handle the toast, no need to manually toast here unless it's a specific fallback
    }
  };

  if (loading) return <div className="min-h-screen flex items-center justify-center">Loading seats...</div>;

  return (
    <div className="min-h-screen bg-stone-50 p-6 flex flex-col items-center justify-center">
      <h1 className="text-2xl font-bold mb-8">{params.tableId} - Select Your Seat ({seats.length} Seats)</h1>
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-6 w-full max-w-sm md:max-w-3xl lg:max-w-5xl">
        {seats.map((seat, i) => (
          <button 
            key={seat.seatId}
            disabled={seat.status !== 'VACANT'}
            onClick={() => handleClaimSeat(seat.seatId)}
            className={`h-24 rounded-2xl flex flex-col items-center justify-center text-lg font-bold shadow transition-colors ${seat.status === 'VACANT' ? 'bg-green-500 text-white hover:bg-green-600' : 'bg-gray-300 text-gray-500 cursor-not-allowed'}`}
          >
            {seat.seatNumber || i + 1}
            <span className="text-xs font-normal mt-1">{seat.status}</span>
          </button>
        ))}
        {seats.length === 0 && <p className="col-span-2 text-center text-stone-500">No seats found. Or using a generic mock.</p>}
      </div>
    </div>
  );
}
