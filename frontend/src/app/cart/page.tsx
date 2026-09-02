"use client";
import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ArrowLeft, Trash2, Plus, Minus, MessageSquare } from 'lucide-react';
import { useCustomerStore } from '@/store/customerStore';

export default function Cart() {
  const router = useRouter();
  const cart = useCustomerStore(state => state.cart);
  const updateCartQuantity = useCustomerStore(state => state.updateCartQuantity);
  const updateCartNotes = useCustomerStore(state => state.updateCartNotes);
  const removeFromCart = useCustomerStore(state => state.removeFromCart);
  
  const [editingNotesId, setEditingNotesId] = useState<string | null>(null);
  const [tempNotes, setTempNotes] = useState('');
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  if (!mounted) return null;

  const subtotal = cart.reduce((sum, item) => sum + (item.menuItem.price * item.quantity), 0);

  const formatNaira = (amount: number) => {
    return '₦' + amount.toLocaleString(undefined, { minimumFractionDigits: 2 });
  };

  const handleSaveNotes = (itemId: string) => {
    updateCartNotes(itemId, tempNotes);
    setEditingNotesId(null);
  };

  return (
    <div className="min-h-screen flex flex-col pb-32">
      <header className="bg-white px-4 py-4 flex items-center gap-3 border-b border-stone-100 sticky top-0 z-10">
        <button onClick={() => router.back()} className="p-2 -ml-2 rounded-full hover:bg-stone-50">
          <ArrowLeft size={20} />
        </button>
        <h1 className="font-bold text-lg text-stone-900">Your Order</h1>
      </header>
      
      <div className="flex-1 p-4 space-y-4">
        {cart.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-stone-400">
            <p className="font-bold mb-4">Your cart is empty.</p>
            <Link href="/menu" className="bg-orange-600 text-white px-6 py-2 rounded-full font-bold shadow-sm">
              Browse Menu
            </Link>
          </div>
        ) : (
          cart.map((item) => (
            <div key={item.menuItem.id} className="bg-white rounded-3xl border border-stone-100 shadow-sm p-4">
              <div className="flex justify-between mb-1 items-start gap-2">
                <h3 className="font-bold text-stone-900 leading-tight">{item.menuItem.name}</h3>
                <span className="font-bold text-orange-600 shrink-0">{formatNaira(item.menuItem.price * item.quantity)}</span>
              </div>

              {editingNotesId === item.menuItem.id ? (
                <div className="my-3 flex gap-2">
                  <input 
                    type="text" 
                    value={tempNotes}
                    onChange={(e) => setTempNotes(e.target.value)}
                    placeholder="E.g. No onions, extra spicy..."
                    className="flex-1 text-xs border border-stone-200 rounded-xl px-3 py-2 outline-none focus:border-orange-500"
                    autoFocus
                  />
                  <button 
                    onClick={() => handleSaveNotes(item.menuItem.id)}
                    className="bg-stone-900 text-white text-xs font-bold px-4 rounded-xl"
                  >
                    Save
                  </button>
                </div>
              ) : (
                <div 
                  className="mb-4 flex items-center gap-1.5 cursor-pointer text-stone-500 hover:text-stone-700 transition"
                  onClick={() => {
                    setEditingNotesId(item.menuItem.id);
                    setTempNotes(item.notes || '');
                  }}
                >
                  <MessageSquare size={14} />
                  <p className="text-xs italic underline decoration-dotted underline-offset-2">
                    {item.notes ? `"${item.notes}"` : 'Add special instructions...'}
                  </p>
                </div>
              )}
              
              <div className="flex justify-between items-center mt-2">
                <button 
                  onClick={() => removeFromCart(item.menuItem.id)}
                  className="p-2 text-stone-400 hover:text-red-500 hover:bg-red-50 rounded-full transition-colors"
                >
                  <Trash2 size={18} />
                </button>
                <div className="flex items-center gap-3 bg-stone-100 rounded-full px-2 py-1">
                  <button 
                    onClick={() => {
                      if (item.quantity > 1) updateCartQuantity(item.menuItem.id, item.quantity - 1);
                      else removeFromCart(item.menuItem.id);
                    }}
                    className="w-8 h-8 rounded-full bg-white flex items-center justify-center shadow-sm text-stone-600 active:scale-95 transition"
                  >
                    <Minus size={14}/>
                  </button>
                  <span className="font-bold w-6 text-center text-sm">{item.quantity}</span>
                  <button 
                    onClick={() => updateCartQuantity(item.menuItem.id, item.quantity + 1)}
                    className="w-8 h-8 rounded-full bg-white flex items-center justify-center shadow-sm text-stone-600 active:scale-95 transition"
                  >
                    <Plus size={14}/>
                  </button>
                </div>
              </div>
            </div>
          ))
        )}
      </div>

      {cart.length > 0 && (
        <div className="fixed bottom-0 left-0 right-0 bg-white p-6 rounded-t-3xl border-t border-stone-200 shadow-[0_-10px_30px_-10px_rgba(0,0,0,0.1)] z-20">
          <div className="max-w-md md:max-w-3xl lg:max-w-5xl mx-auto">
            <div className="flex justify-between mb-2 text-stone-500 text-sm font-medium">
              <span>Subtotal</span>
              <span>{formatNaira(subtotal)}</span>
            </div>
            <div className="flex justify-between mb-4 font-black text-xl text-stone-900">
              <span>Total</span>
              <span className="text-orange-600">{formatNaira(subtotal)}</span>
            </div>
            <Link href="/payment" className="flex items-center justify-center w-full bg-[#F15927] text-white py-4 rounded-2xl font-bold shadow-lg shadow-orange-500/30 hover:bg-orange-700 active:scale-[0.98] transition-all text-lg">
              Proceed to Checkout
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}
