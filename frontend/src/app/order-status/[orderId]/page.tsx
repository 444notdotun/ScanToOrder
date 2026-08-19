'use client';

import React, { useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { useQuery, useMutation } from '@tanstack/react-query';
import { customerService } from '@/services/customer.service';
import { useCustomerStore } from '@/store/customerStore';
import { OrderStatus, Order, OrderItem } from '@/types';
import { 
  CheckCircle2, Clock, ChefHat, Package, Check, Star, 
  Smile, ArrowLeft, Loader2, AlertCircle 
} from 'lucide-react';

export default function OrderStatusPage() {
  const router = useRouter();
  const { orderId } = useParams() as { orderId: string };
  const clearSeat = useCustomerStore((state) => state.clearSeat);

  // States
  const [rating, setRating] = useState<number>(5);
  const [reviewText, setReviewText] = useState<string>('');
  const [isReviewed, setIsReviewed] = useState<boolean>(false);

  // Poll Order status every 4 seconds
  const { data: order, isLoading, error } = useQuery<Order>({
    queryKey: ['orderStatus', orderId],
    queryFn: () => customerService.getOrderStatus(orderId),
    refetchInterval: (query) => {
      const state = query.state.data;
      // Stop polling once order is DELIVERED or CANCELLED
      if (state && ['DELIVERED', 'CANCELLED'].includes(state.status)) {
        return false;
      }
      return 4000; // 4s polling
    }
  });

  // Review submission mutation
  const reviewMutation = useMutation({
    mutationFn: (payload: { sessionId: string; rating: number; reviewText: string }) => 
      customerService.submitReview(payload),
    onSuccess: () => {
      setIsReviewed(true);
    },
    onError: (err) => {
      alert("Failed to submit review: " + (err as any).message);
    }
  });

  const handleSubmitReview = () => {
    if (order) {
      reviewMutation.mutate({
        sessionId: order.sessionId,
        rating,
        reviewText
      });
    }
  };

  const handleLeaveTable = () => {
    // Clear seat information in store
    clearSeat();
    // Redirect to home/hub
    router.push('/');
  };

  const formatNaira = (amount: number) => {
    return '₦' + Number(amount).toLocaleString('en-NG');
  };

  if (isLoading) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center p-6 bg-[#F8F6F2]">
        <Loader2 className="h-8 w-8 text-brand-deep animate-spin mb-3" />
        <p className="text-stone-500 text-sm font-medium">Tracking your order...</p>
      </div>
    );
  }

  if (error || !order) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center p-6 bg-[#F8F6F2] text-center">
        <AlertCircle className="h-10 w-10 text-red-500 mb-3" />
        <p className="text-stone-700 font-bold mb-4">Order tracking unavailable</p>
        <button 
          onClick={() => router.push('/')}
          className="bg-brand-deep text-white px-5 py-2.5 rounded-xl text-sm font-semibold shadow-xs"
        >
          Return to Hub
        </button>
      </div>
    );
  }

  // Determine active stepper index
  const statusSteps: { status: OrderStatus; label: string; description: string; icon: any }[] = [
    { status: 'PAID', label: 'Paid', description: 'Order sent to chef', icon: CheckCircle2 },
    { status: 'PREPARING', label: 'Preparing', description: 'Chef is cooking', icon: ChefHat },
    { status: 'READY', label: 'Ready', description: 'Food on the way', icon: Package },
    { status: 'DELIVERED', label: 'Delivered', description: 'Enjoy your meal!', icon: Check },
  ];

  const getStatusIndex = (status: OrderStatus) => {
    if (status === 'PENDING_PAYMENT') return -1;
    if (status === 'PAID') return 0;
    if (status === 'PREPARING') return 1;
    if (status === 'READY') return 2;
    if (status === 'DELIVERED') return 3;
    return -1; // Cancelled / unknown
  };

  const currentIndex = getStatusIndex(order.status);

  return (
    <div className="flex-1 bg-customer-food min-h-screen py-8 px-4 flex flex-col justify-between">
      <div className="max-w-md mx-auto w-full space-y-6 flex-1 flex flex-col justify-center">
        
        {/* Header */}
        <div className="bg-white rounded-3xl p-5 border border-stone-100 shadow-2xs space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-[10px] font-black text-brand-deep uppercase tracking-widest bg-brand-light px-2.5 py-1 rounded-md">
              Order #{order.id}
            </span>
            <span className={`text-[10px] font-bold px-2.5 py-1 rounded-md uppercase ${
              order.status === 'PAID' ? 'bg-status-success-bg text-status-success-text' :
              order.status === 'PREPARING' ? 'bg-status-prep-bg text-status-prep-text' :
              order.status === 'READY' ? 'bg-status-ready-bg text-status-ready-text' :
              order.status === 'DELIVERED' ? 'bg-status-delivered-bg text-status-delivered-text' :
              'bg-red-50 text-red-600'
            }`}>
              {order.status}
            </span>
          </div>

          <div className="flex justify-between items-center text-xs font-semibold text-stone-500 pt-1">
            <span>Table {order.tableNumber} &bull; Seat {order.seatNumber}</span>
            <span>{new Date(order.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
          </div>
        </div>

        {/* Stepper Timeline */}
        {order.status === 'CANCELLED' ? (
          <div className="bg-white rounded-3xl p-6 border border-stone-100 shadow-2xs text-center space-y-2">
            <AlertCircle className="h-8 w-8 text-red-500 mx-auto" />
            <h3 className="font-bold text-stone-900 text-sm">Order Cancelled</h3>
            <p className="text-xs text-stone-500">This order has been cancelled by operations.</p>
          </div>
        ) : (
          <div className="bg-white rounded-3xl p-6 border border-stone-100 shadow-2xs space-y-6">
            <h3 className="font-black text-stone-900 text-sm">Cooking Timeline</h3>
            
            <div className="relative pl-6 space-y-6 border-l-2 border-stone-100">
              {statusSteps.map((step, idx) => {
                const isCompleted = currentIndex >= idx;
                const isActive = currentIndex === idx;
                const IconComponent = step.icon;

                return (
                  <div key={step.status} className="relative">
                    {/* Circle Pin */}
                    <div className={`absolute -left-[33px] top-0 w-5 h-5 rounded-full border-2 flex items-center justify-center transition-all ${
                      isCompleted 
                        ? 'bg-brand-deep border-brand-deep text-white shadow-2xs' 
                        : 'bg-white border-stone-200 text-stone-300'
                    }`}>
                      <IconComponent className="h-2.5 w-2.5" />
                    </div>

                    <div className="space-y-0.5">
                      <h4 className={`text-xs font-bold transition-colors ${
                        isActive ? 'text-brand-deep' : isCompleted ? 'text-stone-900' : 'text-stone-400'
                      }`}>
                        {step.label}
                      </h4>
                      <p className={`text-[10px] transition-colors ${
                        isActive ? 'text-brand-accent' : isCompleted ? 'text-stone-500' : 'text-stone-400'
                      }`}>
                        {step.description}
                      </p>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* Order Items Summary */}
        <div className="bg-white rounded-3xl p-5 border border-stone-100 shadow-2xs space-y-3">
          <h4 className="text-xs font-black text-stone-900">Items Ordered</h4>
          <div className="divide-y divide-stone-100 text-xs">
            {order.items.map((item: OrderItem) => (
              <div key={item.id} className="py-2.5 flex justify-between">
                <div>
                  <span className="font-bold text-stone-900">{item.name}</span>
                  <span className="text-stone-400 font-semibold ml-1.5">&times; {item.quantity}</span>
                  {item.notes && (
                    <p className="text-[10px] text-brand-accent font-semibold mt-0.5">&ldquo;{item.notes}&rdquo;</p>
                  )}
                </div>
                <span className="font-extrabold text-stone-700">{formatNaira(item.price * item.quantity)}</span>
              </div>
            ))}
          </div>
          <div className="flex justify-between items-center pt-3 border-t border-stone-100 text-xs">
            <span className="font-bold text-stone-500">Amount Paid</span>
            <span className="font-black text-stone-900 text-sm">{formatNaira(order.totalAmount)}</span>
          </div>
        </div>

        {/* Review Form after Delivery */}
        {order.status === 'DELIVERED' && (
          <div className="bg-brand-light/30 rounded-3xl p-6 border border-brand-light/60 shadow-2xs space-y-5">
            {!isReviewed ? (
              <>
                <div className="text-center space-y-1">
                  <div className="inline-flex p-2 bg-brand-light text-brand-deep rounded-full mb-1">
                    <Smile className="h-5 w-5" />
                  </div>
                  <h4 className="text-sm font-black text-stone-900">How was your dining experience?</h4>
                  <p className="text-[10px] text-stone-500 leading-normal">Help us improve by leaving a quick rating</p>
                </div>

                {/* Stars */}
                <div className="flex justify-center items-center gap-2">
                  {[1, 2, 3, 4, 5].map((star) => (
                    <button
                      key={star}
                      onClick={() => setRating(star)}
                      className="p-1 cursor-pointer focus:outline-none"
                    >
                      <Star 
                        className={`h-7 w-7 transition-colors ${
                          rating >= star ? 'fill-amber-400 text-amber-400' : 'text-stone-300'
                        }`} 
                      />
                    </button>
                  ))}
                </div>

                {/* Review Text */}
                <div className="space-y-1.5">
                  <textarea
                    value={reviewText}
                    onChange={(e) => setReviewText(e.target.value)}
                    placeholder="Tell us what you liked, or how we can improve..."
                    rows={2}
                    className="w-full text-xs rounded-xl border border-stone-200 bg-white px-3 py-2.5 text-stone-900 focus:border-brand-accent focus:ring-1 focus:ring-brand-accent focus:outline-none transition"
                  />
                </div>

                <button
                  disabled={reviewMutation.isPending}
                  onClick={handleSubmitReview}
                  className="w-full bg-brand-deep hover:bg-brand-accent text-white font-bold py-3 rounded-xl flex items-center justify-center gap-2 text-xs shadow-sm cursor-pointer"
                >
                  {reviewMutation.isPending ? (
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  ) : (
                    'Submit Feedback'
                  )}
                </button>
              </>
            ) : (
              <div className="text-center space-y-3 py-3">
                <div className="inline-flex p-2 bg-emerald-100 text-emerald-600 rounded-full">
                  <Check className="h-5 w-5" />
                </div>
                <h4 className="text-sm font-black text-stone-900">Thank you for your review!</h4>
                <p className="text-xs text-stone-500">Your feedback helps us cook better Jollof.</p>
              </div>
            )}

            <button
              onClick={handleLeaveTable}
              className="w-full border border-stone-300 hover:bg-stone-50 text-stone-700 font-bold py-3 rounded-xl text-xs transition duration-150 cursor-pointer shadow-3xs"
            >
              Finish Meal & Leave Table
            </button>
          </div>
        )}

        {/* Back Link if not delivered */}
        {order.status !== 'DELIVERED' && (
          <button
            onClick={() => router.push('/menu')}
            className="w-full flex items-center justify-center gap-1.5 text-xs font-bold text-stone-500 hover:text-stone-700 py-2 cursor-pointer"
          >
            <ArrowLeft className="h-3.5 w-3.5" />
            Back to Menu / Order More
          </button>
        )}

      </div>
    </div>
  );
}
