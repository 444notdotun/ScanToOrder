"use client";
import React, { useState, useEffect } from 'react';
import { Utensils, ShoppingCart } from 'lucide-react';
import Link from 'next/link';
import { api } from '@/lib/api';
import { useCustomerStore } from '@/store/customerStore';

import { useSearchParams, useRouter } from 'next/navigation';
import { Suspense } from 'react';
import ItemDetailModal from '@/components/menu/ItemDetailModal';

function MenuContent() {
  const searchParams = useSearchParams();
  const [categories, setCategories] = useState<any[]>([]);
  const [activeCategory, setActiveCategory] = useState<string>('All');
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);
  const cartCount = useCustomerStore(state => state.cart.reduce((sum, item) => sum + item.quantity, 0));
  const addToCart = useCustomerStore(state => state.addToCart);
  const [loading, setLoading] = useState(true);
  
  const tableParam = searchParams.get('table');
  const seatParam = searchParams.get('seat');
  const [tableInfo, setTableInfo] = useState({ table: '?', seat: '?' });
  const [selectedItem, setSelectedItem] = useState<any>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  useEffect(() => {
    setTableInfo({
      table: (tableParam && tableParam !== 'undefined') ? tableParam : (localStorage.getItem('tableNumber') || '?'),
      seat: (seatParam && seatParam !== 'undefined') ? seatParam : (localStorage.getItem('seatId') || '?')
    });
    fetchMenu();
  }, [tableParam, seatParam]);

  const fetchMenu = async () => {
    try {
      const res: any = await api.get('/api/v1/menu'); 
      const responseData = res.data?.data?.categoryAndItemResponse 
         || res.data?.categoryAndItemResponse 
         || res.data?.data 
         || (Array.isArray(res.data) ? res.data : []);

      const parsedCategories = Array.isArray(responseData) ? responseData.map((cat: any) => ({
        categoryName: cat.categoryName || cat.name || cat.CategoryName,
        itemResponse: cat.itemResponse || cat.items || []
      })) : [];

      setCategories(parsedCategories);
    } catch (err) {
      console.error('Failed to fetch menu:', err);
    } finally {
      setLoading(false);
    }
  };

  const scrollToCategory = (catName: string) => {
    setActiveCategory(catName);
    if (catName === 'All') {
      window.scrollTo({ top: 0, behavior: 'smooth' });
      return;
    }
    const safeId = catName.replace(/\s+/g, '-');
    const el = document.getElementById(`category-${safeId}`);
    if (el) {
      // Offset by header height (approx 140px)
      const y = el.getBoundingClientRect().top + window.scrollY - 140;
      window.scrollTo({ top: y, behavior: 'smooth' });
    }
  };

  if (loading) return <div className="min-h-screen flex items-center justify-center">Loading menu...</div>;

  return (
    <div className="min-h-screen pb-24 relative">
      <header className="sticky top-0 bg-white shadow-sm z-20 px-4 py-3 flex justify-between items-center border-b border-stone-100">
        <div>
          <h1 className="font-bold text-lg">Menu</h1>
          <p className="text-xs text-stone-500">{tableInfo.table} • {tableInfo.seat}</p>
        </div>
        <Link href="/cart" className="relative p-2 bg-orange-50 text-orange-600 rounded-full">
          <ShoppingCart size={20} />
          {mounted && cartCount > 0 && (
            <span className="absolute -top-1 -right-1 bg-red-500 text-white text-[10px] w-4 h-4 rounded-full flex items-center justify-center font-bold">
              {cartCount}
            </span>
          )}
        </Link>
      </header>

      <div className="sticky top-[60px] z-10 bg-stone-50/95 backdrop-blur overflow-x-auto whitespace-nowrap px-4 py-3 hide-scrollbar border-b border-stone-200">
        <button onClick={() => scrollToCategory('All')} className={`inline-block px-5 py-2 rounded-full mr-2 text-sm font-medium transition-colors ${activeCategory === 'All' ? 'bg-orange-600 text-white' : 'bg-white text-stone-600 border border-stone-200'}`}>All</button>
        {categories.map((cat) => {
          const catName = cat.categoryName || cat.CategoryName; 
          return (
            <button 
              key={catName} 
              onClick={() => scrollToCategory(catName)} 
              className={`inline-block px-5 py-2 rounded-full mr-2 text-sm font-medium transition-colors ${activeCategory === catName ? 'bg-orange-600 text-white' : 'bg-white text-stone-600 border border-stone-200'}`}
            >
              {catName}
            </button>
          );
        })}
      </div>

      <div className="px-4 mt-4 space-y-8">
        {(() => {
          // Flatten or filter items based on activeCategory
          let itemsToShow: any[] = [];
          if (activeCategory === 'All') {
            categories.forEach(cat => {
              const items = cat.itemResponse || [];
              const catName = cat.categoryName || cat.CategoryName;
              items.forEach((item: any) => {
                itemsToShow.push({ ...item, _catName: catName });
              });
            });
          } else {
            const selectedCat = categories.find(c => (c.categoryName || c.CategoryName) === activeCategory);
            if (selectedCat) {
              const catName = selectedCat.categoryName || selectedCat.CategoryName;
              const items = selectedCat.itemResponse || [];
              items.forEach((item: any) => {
                itemsToShow.push({ ...item, _catName: catName });
              });
            }
          }

          if (itemsToShow.length === 0) {
             return <div className="text-center text-stone-500 py-10">No items found.</div>;
          }

          return (
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
              {itemsToShow.map((item: any, idx: number) => {
                const name = item.itemName || item.ItemName;
                const desc = item.itemDescription || item.ItemDescription;
                const price = item.itemPrice || item.ItemPrice;
                const catName = item._catName;
                
                return (
                  <div 
                    key={idx} 
                    onClick={() => {
                      setSelectedItem({
                         id: item.itemId || item.id || item.ItemName || name,
                         name: name,
                         description: desc,
                         price: Number(price || 0),
                         categoryId: catName,
                         categoryName: catName,
                         available: item.isAvailable ?? item.available ?? true
                      });
                      setIsModalOpen(true);
                    }}
                    className="bg-white rounded-3xl border border-stone-100 shadow-sm p-4 flex gap-4 items-center cursor-pointer hover:shadow-md transition-shadow"
                  >
                    <div className="w-20 h-20 bg-orange-50 text-orange-600 border border-orange-100 rounded-2xl flex items-center justify-center shrink-0">
                      <Utensils size={24} />
                    </div>
                    <div className="flex-1">
                      <h3 className="font-bold text-stone-800">{name}</h3>
                      <p className="text-xs text-stone-500 line-clamp-1 mb-1">{desc}</p>
                      <div className="flex justify-between items-center mt-2">
                        <span className="font-bold text-orange-600">₦{Number(price || 0).toLocaleString(undefined, { minimumFractionDigits: 2 })}</span>
                        <button 
                          onClick={(e) => {
                            e.stopPropagation();
                            setSelectedItem({
                              id: item.itemId || item.id || item.ItemName || name,
                              name: name,
                              description: desc,
                              price: Number(price || 0),
                              categoryId: catName,
                              categoryName: catName,
                              available: item.isAvailable ?? item.available ?? true
                            });
                            setIsModalOpen(true);
                          }} 
                          className={`px-3 py-1 rounded-full text-sm font-bold shadow-sm transition-colors ${item.isAvailable ?? item.available ?? true ? "bg-orange-600 text-white active:bg-orange-700" : "bg-stone-300 text-stone-500 cursor-not-allowed"}`} disabled={!(item.isAvailable ?? item.available ?? true)}
                        >
                          {item.isAvailable ?? item.available ?? true ? "+ Add" : "Sold Out"}
                        </button>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          );
        })()}
      </div>
      <ItemDetailModal 
        isOpen={isModalOpen} 
        onClose={() => setIsModalOpen(false)} 
        item={selectedItem} 
        onAddToCart={addToCart} 
      />
    </div>
  );
}

export default function Menu() {
  return (
    <Suspense fallback={<div className="min-h-screen flex items-center justify-center">Loading menu...</div>}>
      <MenuContent />
    </Suspense>
  );
}
