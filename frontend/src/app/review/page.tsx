
"use client";
import React, { useState } from 'react';
import { Star, CheckCircle2 } from 'lucide-react';

export default function Review() {
  const [rating, setRating] = useState(5);
  const [submitted, setSubmitted] = useState(false);
  
  if (submitted) {
    return (
      <div className="min-h-screen bg-stone-50 flex flex-col p-6 items-center justify-center">
        <div className="w-16 h-16 bg-emerald-100 text-emerald-600 rounded-full flex items-center justify-center mb-6">
          <CheckCircle2 className="w-8 h-8" />
        </div>
        <h1 className="text-2xl font-black mb-2 text-center text-stone-900">Thank You!</h1>
        <p className="text-stone-500 text-center max-w-xs">Your feedback helps us provide a better dining experience. You may now safely close this page.</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-stone-50 flex flex-col p-6">
      <div className="flex-1 flex flex-col items-center justify-center max-w-sm mx-auto w-full">
        <h1 className="text-3xl font-bold mb-2 text-center">How was your meal?</h1>
        <p className="text-stone-500 text-center mb-8">Tap a star to rate your dining experience with us today.</p>
        
        <div className="flex gap-2 mb-8">
          {[1, 2, 3, 4, 5].map(star => (
            <button 
              key={star}
              onClick={() => setRating(star)}
              className={`w-12 h-12 rounded-full flex items-center justify-center transition-all ${star <= rating ? 'bg-brand-deep text-white scale-110 shadow-sm' : 'bg-stone-200 text-stone-400'}`}
            >
              <Star fill={star <= rating ? 'currentColor' : 'none'} size={24} />
            </button>
          ))}
        </div>

        <div className="w-full bg-white p-2 rounded-3xl shadow-sm border border-stone-100 mb-8">
          <textarea 
            className="w-full bg-transparent border-none p-4 focus:outline-none focus:ring-0 resize-none text-sm"
            rows={4}
            placeholder="Tell us what you loved or what we can improve..."
          ></textarea>
        </div>
        
        <button 
          onClick={() => setSubmitted(true)} 
          className="w-full bg-brand-deep text-white py-4 rounded-2xl font-bold shadow-md hover:bg-brand-accent transition-colors text-center"
        >
          Submit Review
        </button>
      </div>
    </div>
  );
}
