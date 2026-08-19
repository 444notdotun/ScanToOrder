'use client';

import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery, useMutation } from '@tanstack/react-query';
import { customerService } from '@/services/customer.service';
import { useCustomerStore } from '@/store/customerStore';
import { MenuItem, ServiceCallType } from '@/types';
import { 
  ShoppingBag, Plus, Minus, Trash2, X, BellRing, Utensils, 
  CreditCard, Landmark, CircleDollarSign, Loader2, MessageSquare, Info 
} from 'lucide-react';

export default function MenuPage() {
  const router = useRouter();
  
  // Zustand Store
  const { seatId, tableId, sessionId, cart, addToCart, removeFromCart, updateCartQuantity, clearCart } = useCustomerStore();

  // Redirect if no seat claimed
  useEffect(() => {
    if (!seatId || !tableId) {
      router.push('/');
    }
  }, [seatId, tableId, router]);

  // Page States
  const [selectedCategory, setSelectedCategory] = useState<string>('all');
  const [activeItem, setActiveItem] = useState<MenuItem | null>(null);
  const [customInstructions, setCustomInstructions] = useState<string>('');
  const [quantity, setQuantity] = useState<number>(1);
  const [isCartOpen, setIsCartOpen] = useState<boolean>(false);
  const [isWaiterModalOpen, setIsWaiterModalOpen] = useState<boolean>(false);
  const [waiterRequestType, setWaiterRequestType] = useState<ServiceCallType>('WAITER');
  const [waiterNote, setWaiterNote] = useState<string>('');
  const [paymentMethod, setPaymentMethod] = useState<'CARD' | 'BANK_TRANSFER' | 'CASH'>('CARD');

  // React Query Fetch Menu
  const { data: menuItems = [], isLoading, error } = useQuery<MenuItem[]>({
    queryKey: ['menu'],
    queryFn: () => customerService.getMenu(),
  });

  // Service Call Mutation
  const createServiceCallMutation = useMutation({
    mutationFn: (payload: { seatId: string; tableId: string; requestType: ServiceCallType; note?: string }) => 
      customerService.createServiceCall(payload),
    onSuccess: () => {
      alert("Service call submitted. A waiter has been notified!");
      setIsWaiterModalOpen(false);
      setWaiterNote('');
    },
    onError: (err) => {
      alert("Failed to call waiter: " + (err as any).message);
    }
  });

  // Create Order Mutation
  const createOrderMutation = useMutation({
    mutationFn: (payload: { seatId: string; items: { menuItemId: string; quantity: number; notes?: string }[] }) => 
      customerService.createOrder(payload),
    onSuccess: (data) => {
      // Initiate payment
      payOrderMutation.mutate({ orderId: data.orderId, paymentMethod });
    },
    onError: (err) => {
      alert("Failed to submit order: " + (err as any).message);
    }
  });

  // Pay Order Mutation
  const payOrderMutation = useMutation({
    mutationFn: (payload: { orderId: string; paymentMethod: string }) => 
      customerService.payOrder(payload.orderId, payload.paymentMethod),
    onSuccess: (data, variables) => {
      // Clear cart
      clearCart();
      setIsCartOpen(false);
      // Redirect to status tracker
      router.push(`/order-status/${variables.orderId}`);
    },
    onError: (err) => {
      alert("Payment initiation failed: " + (err as any).message);
    }
  });

  // Formatting currency
  const formatNaira = (amount: number) => {
    return '₦' + Number(amount).toLocaleString('en-NG');
  };

  const categories = [
    { id: 'all', name: 'All Items' },
    { id: 'c1', name: 'Mains' },
    { id: 'c2', name: 'Appetizers' },
    { id: 'c3', name: 'Drinks' },
    { id: 'c4', name: 'Desserts' },
  ];

  const filteredItems = selectedCategory === 'all' 
    ? menuItems 
    : menuItems.filter((item: MenuItem) => item.categoryId === selectedCategory);

  const cartTotal = cart.reduce((total, item) => total + (item.menuItem.price * item.quantity), 0);
  const totalItemsCount = cart.reduce((count, item) => count + item.quantity, 0);

  const handleOpenCustomize = (item: MenuItem) => {
    setActiveItem(item);
    setQuantity(1);
    setCustomInstructions('');
  };

  const handleAddToCart = () => {
    if (activeItem) {
      addToCart(activeItem, quantity, customInstructions);
      setActiveItem(null);
    }
  };

  const handleCheckout = () => {
    if (!seatId) return;
    const orderItems = cart.map(item => ({
      menuItemId: item.menuItem.id,
      quantity: item.quantity,
      notes: item.notes
    }));

    createOrderMutation.mutate({
      seatId,
      items: orderItems
    });
  };

  const handleCallWaiter = () => {
    if (seatId && tableId) {
      createServiceCallMutation.mutate({
        seatId,
        tableId,
        requestType: waiterRequestType,
        note: waiterNote
      });
    }
  };

  if (!seatId || !tableId) {
    return null;
  }

  return (
    <div className="flex-1 bg-customer-food min-h-screen pb-24 flex flex-col">
      {/* Top Banner / Table Info */}
      <div className="bg-white border-b border-stone-200/50 sticky top-0 z-40 px-4 py-3 shadow-2xs">
        <div className="max-w-md mx-auto flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="bg-brand-light text-brand-deep p-1.5 rounded-lg">
              <Utensils className="h-4 w-4" />
            </div>
            <div>
              <p className="text-[10px] font-black text-stone-400 uppercase tracking-widest leading-none">Dining Session</p>
              <h2 className="text-sm font-bold text-stone-800">
                Table {tableId.replace('t', '')} • Seat {seatId.split('-s')[1]}
              </h2>
            </div>
          </div>
          
          <button 
            onClick={() => setIsWaiterModalOpen(true)}
            className="flex items-center gap-1.5 bg-brand-light text-brand-deep hover:bg-orange-100 px-3 py-1.5 rounded-xl text-xs font-bold transition duration-150 cursor-pointer shadow-3xs"
          >
            <BellRing className="h-3.5 w-3.5 animate-bounce" />
            Need Waiter?
          </button>
        </div>
      </div>

      <div className="max-w-md mx-auto w-full px-4 pt-6 space-y-6 flex-1 flex flex-col">
        
        {/* Search & Intro */}
        <div className="space-y-1">
          <h1 className="text-2xl font-black text-stone-900 leading-tight">Order Dinner</h1>
          <p className="text-xs text-stone-500 font-medium">Select delicious Nigerian meals cooked fresh</p>
        </div>

        {/* Category Carousel */}
        <div className="flex gap-2 overflow-x-auto no-scrollbar pb-1 -mx-4 px-4">
          {categories.map((cat) => (
            <button
              key={cat.id}
              onClick={() => setSelectedCategory(cat.id)}
              className={`px-4 py-2 rounded-xl text-xs font-semibold whitespace-nowrap border transition duration-150 shrink-0 cursor-pointer ${
                selectedCategory === cat.id
                  ? 'bg-brand-deep text-white border-brand-deep shadow-2xs'
                  : 'bg-white text-stone-600 border-stone-200/50 hover:bg-stone-50'
              }`}
            >
              {cat.name}
            </button>
          ))}
        </div>

        {/* Menu Items List */}
        {isLoading ? (
          <div className="flex-1 flex flex-col items-center justify-center py-20">
            <Loader2 className="h-8 w-8 text-brand-deep animate-spin mb-2" />
            <p className="text-stone-500 text-xs font-medium">Fetching culinary menu...</p>
          </div>
        ) : error ? (
          <div className="flex-1 flex flex-col items-center justify-center text-center py-20">
            <Info className="h-8 w-8 text-stone-400 mb-2" />
            <p className="text-stone-600 text-xs font-semibold">Failed to fetch menu items.</p>
          </div>
        ) : filteredItems.length === 0 ? (
          <div className="flex-1 flex flex-col items-center justify-center text-center py-20">
            <Info className="h-8 w-8 text-stone-400 mb-2" />
            <p className="text-stone-600 text-xs font-semibold">No items available in this category.</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4">
            {filteredItems.map((item: MenuItem) => (
              <div 
                key={item.id} 
                className="bg-white rounded-2xl p-3 flex border border-stone-100 shadow-2xs hover:shadow-xs transition duration-200"
              >
                {/* Image */}
                {item.imageUrl && (
                  <div className="w-20 h-20 rounded-xl overflow-hidden bg-stone-100 relative shrink-0">
                    <img 
                      src={item.imageUrl} 
                      alt={item.name}
                      className="w-full h-full object-cover"
                    />
                  </div>
                )}
                
                {/* Details */}
                <div className="ml-3.5 flex flex-col justify-between flex-1">
                  <div>
                    <h3 className="text-sm font-bold text-stone-900 leading-tight">{item.name}</h3>
                    <p className="text-[10px] text-stone-400 line-clamp-2 mt-1 leading-normal font-medium">{item.description}</p>
                  </div>
                  <div className="flex items-center justify-between pt-1">
                    <span className="text-xs font-black text-brand-deep">{formatNaira(item.price)}</span>
                    
                    <button
                      onClick={() => handleOpenCustomize(item)}
                      className="bg-brand-deep hover:bg-brand-accent text-white p-1 rounded-lg transition duration-150 cursor-pointer"
                    >
                      <Plus className="h-4 w-4" />
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Sticky Cart Footer */}
      {totalItemsCount > 0 && (
        <div className="fixed bottom-0 left-0 right-0 p-4 sticky-footer z-40 bg-transparent pointer-events-none">
          <div className="max-w-md mx-auto pointer-events-auto">
            <button
              onClick={() => setIsCartOpen(true)}
              className="w-full bg-brand-deep hover:bg-brand-accent text-white font-bold px-6 py-4 rounded-2xl flex items-center justify-between shadow-lg transition duration-200"
            >
              <div className="flex items-center gap-2">
                <ShoppingBag className="h-5 w-5" />
                <span className="bg-brand-light text-brand-deep text-xs font-extrabold px-2 py-0.5 rounded-full">
                  {totalItemsCount}
                </span>
                <span className="text-xs font-semibold text-brand-light/95">View Cart</span>
              </div>
              <span className="text-sm tracking-wide">{formatNaira(cartTotal)} &rarr;</span>
            </button>
          </div>
        </div>
      )}

      {/* Modal: Customize & Add to Cart */}
      {activeItem && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs flex items-end justify-center z-50 p-4">
          <div className="bg-white rounded-3xl w-full max-w-md p-6 space-y-6 shadow-xl animate-slide-up">
            
            {/* Header */}
            <div className="flex items-start justify-between">
              <div>
                <span className="text-[9px] font-black text-brand-deep tracking-wider bg-brand-light px-2.5 py-0.5 rounded-md uppercase">
                  {activeItem.categoryName}
                </span>
                <h3 className="text-lg font-black text-stone-900 mt-2 leading-tight">{activeItem.name}</h3>
                <p className="text-xs text-stone-500 mt-1 leading-normal">{activeItem.description}</p>
              </div>
              <button 
                onClick={() => setActiveItem(null)}
                className="p-1 rounded-full bg-stone-100 hover:bg-stone-200 text-stone-500"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            {/* Custom Instructions */}
            <div className="space-y-2">
              <label className="text-xs font-bold text-stone-700 flex items-center gap-1.5">
                <MessageSquare className="h-3.5 w-3.5 text-stone-400" />
                Special Preparation Notes:
              </label>
              <textarea
                value={customInstructions}
                onChange={(e) => setCustomInstructions(e.target.value)}
                placeholder="e.g. Extra spicy, no onions, sauce on the side..."
                rows={3}
                className="w-full text-xs rounded-xl border border-stone-200 bg-stone-50 px-3 py-2.5 text-stone-900 focus:bg-white focus:border-brand-accent focus:ring-1 focus:ring-brand-accent focus:outline-none transition"
              />
            </div>

            {/* Quantity Selector & Confirm */}
            <div className="flex items-center justify-between pt-2">
              <div className="flex items-center gap-3.5 bg-stone-100 p-1.5 rounded-xl border border-stone-200/50">
                <button
                  disabled={quantity <= 1}
                  onClick={() => setQuantity(q => q - 1)}
                  className="p-1 rounded-lg bg-white shadow-2xs hover:bg-stone-50 text-stone-700 disabled:opacity-50"
                >
                  <Minus className="h-4.5 w-4.5" />
                </button>
                <span className="text-sm font-black text-stone-800 w-4 text-center">{quantity}</span>
                <button
                  onClick={() => setQuantity(q => q + 1)}
                  className="p-1 rounded-lg bg-white shadow-2xs hover:bg-stone-50 text-stone-700"
                >
                  <Plus className="h-4.5 w-4.5" />
                </button>
              </div>

              <button
                onClick={handleAddToCart}
                className="bg-brand-deep hover:bg-brand-accent text-white font-bold px-6 py-3.5 rounded-2xl flex items-center gap-2 shadow-md transition duration-150 text-sm"
              >
                Add to Cart &bull; {formatNaira(activeItem.price * quantity)}
              </button>
            </div>

          </div>
        </div>
      )}

      {/* Drawer: Cart & Checkout */}
      {isCartOpen && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs flex items-end justify-center z-50">
          <div className="bg-white rounded-t-3xl w-full max-w-md max-h-[85vh] overflow-y-auto p-6 space-y-6 shadow-2xl flex flex-col justify-between">
            
            {/* Header */}
            <div>
              <div className="flex items-center justify-between border-b border-stone-100 pb-4">
                <div className="flex items-center gap-2">
                  <ShoppingBag className="h-5 w-5 text-brand-deep" />
                  <h3 className="text-lg font-black text-stone-900">Your Basket</h3>
                </div>
                <button 
                  onClick={() => setIsCartOpen(false)}
                  className="p-1 rounded-full bg-stone-100 hover:bg-stone-200 text-stone-500"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>

              {/* Items List */}
              <div className="divide-y divide-stone-100 max-h-[30vh] overflow-y-auto pr-1">
                {cart.map((item) => (
                  <div key={item.menuItem.id} className="py-4 flex justify-between gap-3">
                    <div className="flex-1">
                      <h4 className="text-xs font-bold text-stone-900">{item.menuItem.name}</h4>
                      {item.notes && (
                        <p className="text-[10px] text-brand-accent font-semibold mt-0.5">&ldquo;{item.notes}&rdquo;</p>
                      )}
                      <p className="text-xs font-black text-stone-500 mt-1">{formatNaira(item.menuItem.price * item.quantity)}</p>
                    </div>

                    <div className="flex items-center gap-2.5 shrink-0">
                      <div className="flex items-center gap-2 bg-stone-100 p-1 rounded-lg border border-stone-200/50">
                        <button
                          onClick={() => {
                            if (item.quantity === 1) {
                              removeFromCart(item.menuItem.id);
                            } else {
                              updateCartQuantity(item.menuItem.id, item.quantity - 1);
                            }
                          }}
                          className="p-0.5 rounded-md bg-white text-stone-600 shadow-3xs"
                        >
                          <Minus className="h-3.5 w-3.5" />
                        </button>
                        <span className="text-xs font-bold text-stone-800 w-3 text-center">{item.quantity}</span>
                        <button
                          onClick={() => updateCartQuantity(item.menuItem.id, item.quantity + 1)}
                          className="p-0.5 rounded-md bg-white text-stone-600 shadow-3xs"
                        >
                          <Plus className="h-3.5 w-3.5" />
                        </button>
                      </div>

                      <button
                        onClick={() => removeFromCart(item.menuItem.id)}
                        className="text-stone-400 hover:text-red-500 p-1"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Payment & Checkout Section */}
            <div className="space-y-6 pt-4 border-t border-stone-100">
              {/* Payment Methods */}
              <div className="space-y-2.5">
                <span className="text-xs font-extrabold text-stone-700">Select Payment Method:</span>
                <div className="grid grid-cols-3 gap-2.5">
                  <button
                    onClick={() => setPaymentMethod('CARD')}
                    className={`flex flex-col items-center justify-center p-3 rounded-xl border text-center transition duration-150 cursor-pointer ${
                      paymentMethod === 'CARD'
                        ? 'border-brand-deep bg-brand-light text-brand-deep font-bold'
                        : 'border-stone-200 hover:bg-stone-50 text-stone-600'
                    }`}
                  >
                    <CreditCard className="h-4.5 w-4.5 mb-1" />
                    <span className="text-[10px]">Card</span>
                  </button>

                  <button
                    onClick={() => setPaymentMethod('BANK_TRANSFER')}
                    className={`flex flex-col items-center justify-center p-3 rounded-xl border text-center transition duration-150 cursor-pointer ${
                      paymentMethod === 'BANK_TRANSFER'
                        ? 'border-brand-deep bg-brand-light text-brand-deep font-bold'
                        : 'border-stone-200 hover:bg-stone-50 text-stone-600'
                    }`}
                  >
                    <Landmark className="h-4.5 w-4.5 mb-1" />
                    <span className="text-[10px]">Transfer</span>
                  </button>

                  <button
                    onClick={() => setPaymentMethod('CASH')}
                    className={`flex flex-col items-center justify-center p-3 rounded-xl border text-center transition duration-150 cursor-pointer ${
                      paymentMethod === 'CASH'
                        ? 'border-brand-deep bg-brand-light text-brand-deep font-bold'
                        : 'border-stone-200 hover:bg-stone-50 text-stone-600'
                    }`}
                  >
                    <CircleDollarSign className="h-4.5 w-4.5 mb-1" />
                    <span className="text-[10px]">Cash / Waiter</span>
                  </button>
                </div>
              </div>

              {/* Total Info */}
              <div className="bg-stone-50 rounded-xl p-3.5 border border-stone-200/40 flex justify-between items-center text-xs">
                <span className="font-semibold text-stone-500">Order Subtotal:</span>
                <span className="font-extrabold text-stone-900 text-sm">{formatNaira(cartTotal)}</span>
              </div>

              {/* Action Buttons */}
              <div className="space-y-2">
                <button
                  disabled={createOrderMutation.isPending || payOrderMutation.isPending}
                  onClick={handleCheckout}
                  className="w-full bg-brand-deep hover:bg-brand-accent text-white font-bold py-3.5 rounded-2xl flex items-center justify-center gap-2 shadow-md transition duration-200 text-sm cursor-pointer"
                >
                  {createOrderMutation.isPending || payOrderMutation.isPending ? (
                    <>
                      <Loader2 className="h-4 w-4 animate-spin" />
                      Processing Order...
                    </>
                  ) : (
                    <>
                      Submit Order &bull; {formatNaira(cartTotal)}
                    </>
                  )}
                </button>
                <button
                  onClick={() => setIsCartOpen(false)}
                  className="w-full text-center text-xs font-bold text-stone-500 hover:text-stone-700 py-1"
                >
                  Continue Adding Items
                </button>
              </div>
            </div>

          </div>
        </div>
      )}

      {/* Modal: Call Waiter / Assistance */}
      {isWaiterModalOpen && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-3xl w-full max-w-md p-6 space-y-6 shadow-xl animate-fade-in">
            
            {/* Header */}
            <div className="flex items-start justify-between">
              <div className="flex items-center gap-2">
                <BellRing className="h-5 w-5 text-brand-deep animate-swing" />
                <h3 className="text-lg font-black text-stone-900">Request Waiter Assistance</h3>
              </div>
              <button 
                onClick={() => setIsWaiterModalOpen(false)}
                className="p-1 rounded-full bg-stone-100 hover:bg-stone-200 text-stone-500"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            {/* Assistance Type */}
            <div className="space-y-2.5">
              <span className="text-xs font-extrabold text-stone-700">What do you need help with?</span>
              <div className="grid grid-cols-2 gap-2.5">
                {[
                  { type: 'WAITER', label: 'Call Waiter' },
                  { type: 'BILL', label: 'Bring Bill' },
                  { type: 'CLEANING', label: 'Clear/Clean Table' },
                  { type: 'HELP', label: 'Query Menu' },
                ].map((item) => (
                  <button
                    key={item.type}
                    onClick={() => setWaiterRequestType(item.type as ServiceCallType)}
                    className={`p-3 rounded-xl border text-center text-xs font-bold transition duration-150 cursor-pointer ${
                      waiterRequestType === item.type
                        ? 'border-brand-deep bg-brand-light text-brand-deep'
                        : 'border-stone-200 hover:bg-stone-50 text-stone-600'
                    }`}
                  >
                    {item.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Request Notes */}
            <div className="space-y-2">
              <label className="text-xs font-bold text-stone-700">Optional Note / Extra Info:</label>
              <input
                type="text"
                value={waiterNote}
                onChange={(e) => setWaiterNote(e.target.value)}
                placeholder="e.g. Need extra water cup, allergy details..."
                className="w-full text-xs rounded-xl border border-stone-200 bg-stone-50 px-3 py-2.5 text-stone-900 focus:bg-white focus:border-brand-accent focus:ring-1 focus:ring-brand-accent focus:outline-none transition"
              />
            </div>

            {/* Action */}
            <button
              disabled={createServiceCallMutation.isPending}
              onClick={handleCallWaiter}
              className="w-full bg-brand-deep hover:bg-brand-accent text-white font-bold py-3.5 rounded-2xl flex items-center justify-center gap-2 shadow-md transition duration-150 text-sm cursor-pointer"
            >
              {createServiceCallMutation.isPending ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Sending Request...
                </>
              ) : (
                'Send Request to Waiter'
              )}
            </button>

          </div>
        </div>
      )}

    </div>
  );
}
