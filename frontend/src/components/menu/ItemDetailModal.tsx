'use client';

import React, { useState, useRef, useEffect } from 'react';
import { motion, AnimatePresence, useAnimation, useMotionValue, useTransform } from 'framer-motion';
import { X, Star, Plus, Minus, Info } from 'lucide-react';
import { MenuItem } from '@/types';
import { toast } from 'sonner';

interface ItemDetailModalProps {
  isOpen: boolean;
  onClose: () => void;
  item: MenuItem | null;
  onAddToCart: (item: MenuItem, quantity: number, notes: string) => void;
}

export default function ItemDetailModal({ isOpen, onClose, item, onAddToCart }: ItemDetailModalProps) {
  const [quantity, setQuantity] = useState(1);
  const [notes, setNotes] = useState('');
  const [activeVariant, setActiveVariant] = useState('Regular');
  
  // Reset state when a new item opens
  useEffect(() => {
    if (isOpen) {
      setQuantity(1);
      setNotes('');
      setActiveVariant('Regular');
    }
  }, [isOpen, item]);

  // 3D Parallax logic
  const cardRef = useRef<HTMLDivElement>(null);
  const x = useMotionValue(0);
  const y = useMotionValue(0);
  
  const rotateX = useTransform(y, [-0.5, 0.5], [10, -10]);
  const rotateY = useTransform(x, [-0.5, 0.5], [-10, 10]);

  const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!cardRef.current) return;
    const rect = cardRef.current.getBoundingClientRect();
    
    const width = rect.width;
    const height = rect.height;
    
    const mouseX = e.clientX - rect.left;
    const mouseY = e.clientY - rect.top;
    
    const xPct = mouseX / width - 0.5;
    const yPct = mouseY / height - 0.5;
    
    x.set(xPct);
    y.set(yPct);
  };

  const handleMouseLeave = () => {
    x.set(0);
    y.set(0);
  };

  if (!item) return null;

  const isAvailable = item.available ?? true;
  const itemPrice = Number(item.price || 0);

  const handleAdd = () => {
    onAddToCart(item, quantity, notes);
    toast.success(`${quantity}x ${item.name} added to cart!`);
    onClose();
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          {/* Backdrop */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="fixed inset-0 z-50 bg-stone-900/60 backdrop-blur-sm"
          />

          {/* Modal Container */}
          <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center pointer-events-none p-0 sm:p-4">
            <motion.div
              initial={{ y: '100%', scale: 0.95 }}
              animate={{ y: 0, scale: 1 }}
              exit={{ y: '100%', scale: 0.95 }}
              transition={{ type: 'spring', damping: 25, stiffness: 300 }}
              className="bg-[#F9F6F0] w-full max-w-lg rounded-t-3xl sm:rounded-3xl shadow-2xl pointer-events-auto flex flex-col max-h-[90vh] overflow-hidden will-change-transform"
              style={{ transformStyle: 'preserve-3d', perspective: '1200px' }}
            >
              {/* Header / Dragger for mobile */}
              <div className="flex justify-center pt-3 pb-1 sm:hidden">
                <div className="w-12 h-1.5 bg-stone-300 rounded-full" />
              </div>
              
              <button 
                onClick={onClose}
                className="absolute top-4 right-4 z-10 w-8 h-8 flex items-center justify-center bg-white/50 backdrop-blur-md rounded-full text-stone-700 hover:bg-white transition-colors"
              >
                <X className="w-5 h-5" />
              </button>

              <div className="overflow-y-auto hide-scrollbar flex-1 pb-24 sm:pb-0">
                {/* 3D Image Showcase */}
                <div className="relative w-full h-64 sm:h-80 flex items-center justify-center bg-[#1C1917] overflow-hidden perspective-[1200px]">
                  {/* Decorative background glow */}
                  <div className="absolute inset-0 bg-gradient-to-tr from-yellow-900/20 to-orange-500/20" />
                  
                  <motion.div
                    ref={cardRef}
                    onMouseMove={handleMouseMove}
                    onMouseLeave={handleMouseLeave}
                    style={{ rotateX, rotateY, transformStyle: 'preserve-3d' }}
                    className="relative w-48 h-48 sm:w-64 sm:h-64 rounded-full flex items-center justify-center"
                    animate={{ y: [-6, 6] }}
                    transition={{ repeat: Infinity, repeatType: 'reverse', duration: 3, ease: 'easeInOut' }}
                  >
                    {/* The "Dish" */}
                    <div className="w-full h-full rounded-full bg-gradient-to-br from-orange-400 to-red-500 shadow-2xl flex items-center justify-center border-4 border-white/10" style={{ transform: 'translateZ(40px)' }}>
                       <span className="text-white font-black text-2xl tracking-wider text-center px-4" style={{ transform: 'translateZ(60px)' }}>{item.name}</span>
                    </div>

                    {/* Floating Badge */}
                    <div className="absolute -bottom-4 -right-4 backdrop-blur-md bg-white/70 border border-white/40 px-3 py-1.5 rounded-full flex items-center gap-1 shadow-lg" style={{ transform: 'translateZ(80px)' }}>
                      <Star className="w-3.5 h-3.5 fill-yellow-400 text-yellow-500" />
                      <span className="text-xs font-bold text-stone-800">4.9 · 128 Reviews</span>
                    </div>
                  </motion.div>
                </div>

                <div className="p-6 space-y-6">
                  {/* Title & Price */}
                  <div className="flex justify-between items-start gap-4">
                    <div>
                      <h2 className="text-2xl font-black text-stone-900">{item.name}</h2>
                      <p className="text-stone-500 text-sm mt-1">{item.description || 'A delicious culinary experience crafted with the finest ingredients.'}</p>
                    </div>
                    <div className="bg-yellow-100/50 border border-yellow-200/50 px-3 py-1.5 rounded-2xl flex shrink-0">
                       <span className="text-lg font-black text-yellow-700">₦{itemPrice.toLocaleString()}</span>
                    </div>
                  </div>

                  {/* Interactive Variant Carousel */}
                  <div className="space-y-3">
                    <h3 className="text-xs font-bold text-stone-400 uppercase tracking-wider">Select Variant</h3>
                    <div className="flex items-center gap-2 relative">
                      {['Regular', 'Large', 'Spicy'].map((variant) => (
                        <button
                          key={variant}
                          onClick={() => setActiveVariant(variant)}
                          className={`relative px-4 py-2 rounded-full text-sm font-bold transition-colors z-10 ${activeVariant === variant ? 'text-white' : 'text-stone-600 bg-stone-200/50 hover:bg-stone-200'}`}
                        >
                          {activeVariant === variant && (
                            <motion.div
                              layoutId="activeVariantIndicator"
                              className="absolute inset-0 bg-stone-900 rounded-full -z-10"
                              transition={{ type: 'spring', bounce: 0.2, duration: 0.6 }}
                            />
                          )}
                          {variant}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Special Instructions */}
                  <div className="space-y-3">
                    <h3 className="text-xs font-bold text-stone-400 uppercase tracking-wider">Special Instructions</h3>
                    <textarea 
                      value={notes}
                      onChange={(e) => setNotes(e.target.value)}
                      placeholder="e.g. No onions, extra spicy..."
                      className="w-full bg-white border border-stone-200 rounded-2xl p-4 text-sm text-stone-700 focus:outline-none focus:ring-2 focus:ring-yellow-500 focus:border-transparent resize-none h-24 shadow-sm"
                    />
                  </div>
                </div>
              </div>

              {/* Footer CTA */}
              <div className="absolute bottom-0 left-0 right-0 p-4 sm:relative bg-white/80 backdrop-blur-xl border-t border-stone-200/50 flex items-center gap-4">
                {/* Stepper */}
                <div className="flex items-center bg-stone-100 rounded-2xl p-1 shrink-0 border border-stone-200/50">
                  <motion.button 
                    whileTap={{ scale: 0.9 }}
                    onClick={() => setQuantity(Math.max(1, quantity - 1))}
                    className="w-10 h-10 flex items-center justify-center bg-white rounded-xl shadow-sm text-stone-600"
                  >
                    <Minus className="w-4 h-4" />
                  </motion.button>
                  <span className="w-8 text-center font-black text-stone-800">{quantity}</span>
                  <motion.button 
                    whileTap={{ scale: 0.9 }}
                    onClick={() => setQuantity(quantity + 1)}
                    className="w-10 h-10 flex items-center justify-center bg-white rounded-xl shadow-sm text-stone-600"
                  >
                    <Plus className="w-4 h-4" />
                  </motion.button>
                </div>

                {/* Add to Cart Button */}
                <motion.button
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                  onClick={handleAdd}
                  disabled={!isAvailable}
                  className={`flex-1 py-4 rounded-2xl font-black text-sm relative overflow-hidden group shadow-lg ${
                    isAvailable 
                      ? 'bg-stone-900 text-white shadow-stone-900/20' 
                      : 'bg-stone-300 text-stone-500 cursor-not-allowed shadow-none'
                  }`}
                >
                  {isAvailable && (
                    <div className="absolute inset-0 -translate-x-full group-hover:animate-[shimmer_1.5s_infinite] bg-gradient-to-r from-transparent via-white/10 to-transparent skew-x-12" />
                  )}
                  {isAvailable ? `Add to Cart — ₦${(itemPrice * quantity).toLocaleString()}` : 'Sold Out'}
                </motion.button>
              </div>
            </motion.div>
          </div>
        </>
      )}
    </AnimatePresence>
  );
}
