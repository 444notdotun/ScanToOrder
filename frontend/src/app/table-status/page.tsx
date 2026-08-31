
import React from 'react';
import Link from 'next/link';
import { ArrowLeft, Users, CheckCircle2, Clock } from 'lucide-react';

export default function TableStatus() {
  return (
    <div className="min-h-screen bg-stone-50">
      <header className="bg-white px-4 py-4 flex items-center gap-3 border-b border-stone-100">
        <Link href="/orders/082" className="p-2 -ml-2 rounded-full hover:bg-stone-50">
          <ArrowLeft size={20} />
        </Link>
        <div className="flex-1">
          <h1 className="font-bold text-lg flex items-center gap-2"><Users size={20}/> Table 4 Status</h1>
        </div>
      </header>

      <div className="p-4 space-y-4">
        {/* My Seat */}
        <div className="bg-white rounded-3xl p-4 border-2 border-orange-600 shadow-sm relative overflow-hidden">
          <div className="absolute top-0 left-0 w-2 h-full bg-orange-600"></div>
          <div className="flex justify-between items-center pl-4">
            <div>
              <p className="text-xs text-orange-600 font-bold mb-1">YOUR SEAT (Seat 1)</p>
              <h3 className="font-bold text-lg">Preparing</h3>
            </div>
            <div className="w-10 h-10 bg-orange-100 rounded-full flex items-center justify-center text-orange-600">
              <Clock size={20} />
            </div>
          </div>
        </div>

        {/* Other Seats */}
        <div className="bg-white rounded-3xl p-4 border border-stone-100 shadow-sm relative overflow-hidden">
          <div className="absolute top-0 left-0 w-2 h-full bg-green-500"></div>
          <div className="flex justify-between items-center pl-4">
            <div>
              <p className="text-xs text-stone-500 font-bold mb-1">Seat 3</p>
              <h3 className="font-bold text-lg text-stone-800">Delivered</h3>
            </div>
            <div className="w-10 h-10 bg-green-100 rounded-full flex items-center justify-center text-green-600">
              <CheckCircle2 size={20} />
            </div>
          </div>
        </div>
        
        <div className="bg-white rounded-3xl p-4 border border-stone-100 shadow-sm relative overflow-hidden opacity-50">
          <div className="flex justify-between items-center">
            <div>
              <p className="text-xs text-stone-500 font-bold mb-1">Seat 2</p>
              <h3 className="font-bold text-stone-500">Empty</h3>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
