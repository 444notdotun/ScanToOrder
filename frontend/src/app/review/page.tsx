
"use client";
import React, { useState } from 'react';
import Link from 'next/link';
import { Star } from 'lucide-react';

export default function Review() {
  const [rating, setRating] = useState(4);
  
  return (
    <div className="min-h-screen bg-stone-50 flex flex-col p-6">
      <div className="flex-1 flex flex-col items-center justify-center max-w-sm mx-auto w-full">
        <h1 className="text-3xl font-bold mb-2 text-center">How was your meal?</h1>
        <p className="text-stone-500 text-center mb-8">Tap a star to rate your experience at Table 4.</p>
        
        <div className="flex gap-2 mb-8">
          {[1, 2, 3, 4, 5].map(star => (
            <button 
              key={star}
              onClick={() => setRating(star)}
              className={`w-14 h-14 rounded-full flex items-center justify-center transition-all ${star <= rating ? 'bg-orange-100 text-orange-500 scale-110 shadow-sm' : 'bg-stone-200 text-stone-400'}`}
            >
              <Star fill={star <= rating ? 'currentColor' : 'none'} size={32} />
            </button>
          ))}
        </div>

        <div className="w-full bg-white p-2 rounded-3xl shadow-sm border border-stone-100 mb-8">
          <textarea 
            className="w-full bg-transparent border-none p-4 focus:ring-0 resize-none"
            rows={4}
            placeholder="Tell us what you loved or what we can improve..."
          ></textarea>
        </div>
        
        <Link href="/" className="w-full bg-orange-600 text-white py-4 rounded-full font-bold shadow-md hover:bg-orange-700 transition-colors text-center">
          Submit Review
        </Link>
      </div>
    </div>
  );
}
