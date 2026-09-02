"use client";
import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ArrowLeft, CreditCard, Mail, Loader2 } from 'lucide-react';
import { useCustomerStore } from '@/store/customerStore';
import { api } from '@/lib/api';

export default function Payment() {
  const router = useRouter();
  const cart = useCustomerStore(state => state.cart);
  const clearCart = useCustomerStore(state => state.clearCart);
  
  const [method, setMethod] = useState('card');
  const email = 'adedortmahan@gmail.com';
  const [isProcessing, setIsProcessing] = useState(false);
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  const subtotal = cart.reduce((sum, item) => sum + (item.menuItem.price * item.quantity), 0);

  const formatNaira = (amount: number) => {
    return '₦' + amount.toLocaleString(undefined, { minimumFractionDigits: 2 });
  };

  const handlePayment = async () => {

    if (cart.length === 0) {
      import('sonner').then(m => m.toast.error('Your cart is empty.'));
      return;
    }

    try {
      setIsProcessing(true);

      // Step 1: Create the Order
      const orderRes: any = await api.post('/api/v1/orders', {
        orderItems: cart.map((item: any) => ({
          itemName: item.menuItem.name,
          quantity: item.quantity,
          specialInstructions: item.notes || ''
        }))
      });

      const orderData = orderRes.data?.data || orderRes.data;
      const orderId = orderData?.orderId || orderData?.id;

      if (!orderId) {
        throw new Error('Order creation succeeded but orderId was missing.');
      }

      // Step 2: Initialize Paystack
      const payRes: any = await api.post('/api/v1/payments/initialize', {
        orderId: orderId,
        customerEmail: email
      });

      const payData = payRes.data?.data || payRes.data;
      const authUrl = payData?.authorization_url || payData?.authorizationUrl || payData?.redirect_url;

      try {
        if (typeof clearCart === 'function') clearCart();
      } catch (e) {
        console.warn('Cart clear error:', e);
      }

      if (authUrl) {
        // Direct to Paystack gateway
        window.location.href = authUrl;
      } else {
        console.warn('No authorization_url found, routing to tracking directly.');
        router.push(`/orders/${orderId}`);
      }
    } catch (err: any) {
      console.error('Payment checkout error:', err);
      const message = err?.response?.data?.message || err?.message || 'Payment initiation failed.';
      import('sonner').then(m => m.toast.error(message));
    } finally {
      setIsProcessing(false);
    }
  };

  if (!mounted) return null;

  return (
    <div className="min-h-screen  flex flex-col">
      <header className="bg-white px-4 py-4 flex items-center gap-3 border-b border-stone-100">
        <button onClick={() => router.back()} className="p-2 -ml-2 rounded-full hover:">
          <ArrowLeft size={20} />
        </button>
        <h1 className="font-bold text-lg text-stone-900">Payment</h1>
      </header>

      <div className="p-4 space-y-6 flex-1">
        <div className="bg-white rounded-3xl border border-stone-100 p-6 shadow-sm text-center">
          <p className="text-sm font-semibold text-stone-500 mb-1">Total Amount</p>
          <p className="text-4xl font-black text-orange-600 tracking-tight">{formatNaira(subtotal)}</p>
        </div>

        <div>
          <h2 className="font-bold mb-3 px-1 text-stone-800">Select Payment Method</h2>
          <div className="space-y-3">
            <button 
              onClick={() => setMethod('card')}
              className={`w-full flex items-center p-4 rounded-2xl border-2 transition-all ${method === 'card' ? 'border-orange-600 bg-orange-50' : 'border-stone-200 bg-white hover:border-stone-300'}`}
            >
              <div className={`w-10 h-10 rounded-full flex items-center justify-center mr-4 ${method === 'card' ? 'bg-orange-600 text-white shadow-md shadow-orange-500/30' : 'bg-stone-100 text-stone-500'}`}>
                <CreditCard size={20} />
              </div>
              <span className="font-bold flex-1 text-left text-stone-800">Paystack Checkout</span>
              <div className={`w-5 h-5 rounded-full border-2 transition-colors ${method === 'card' ? 'border-orange-600 bg-orange-600' : 'border-stone-300'}`}></div>
            </button>
          </div>
        </div>
      </div>

      <div className="p-4 bg-white border-t border-stone-200">
        <button 
          onClick={handlePayment}
          disabled={isProcessing}
          className="flex items-center justify-center w-full bg-[#F15927] text-white py-4 rounded-2xl font-bold shadow-lg shadow-orange-500/30 hover:bg-orange-700 active:scale-[0.98] transition-all text-lg disabled:opacity-70 disabled:cursor-not-allowed"
        >
          {isProcessing ? (
            <><Loader2 className="animate-spin mr-2" size={20} /> Processing...</>
          ) : (
            `Pay ${formatNaira(subtotal)}`
          )}
        </button>
      </div>
    </div>
  );
}
