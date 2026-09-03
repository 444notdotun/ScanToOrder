'use client';

import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { useMutation } from '@tanstack/react-query';
import { managerService } from '@/services/manager.service';
import { api } from '@/lib/api';
import ProtectedRoute from '@/components/ProtectedRoute';
import { MenuItem } from '@/types';
import { 
  ArrowLeft, PlusCircle, Trash2, Edit3, Power, Loader2, Settings, FolderPlus
} from 'lucide-react';

// ─── Types ────────────────────────────────────────────────────────────────────
interface CatalogCategory {
  id: string;
  name: string;
}

// ─── Helper ───────────────────────────────────────────────────────────────────
const formatNaira = (amount: any): string => {
  const parsed = Number(amount ?? 0);
  return '₦' + (isNaN(parsed) ? 0 : parsed).toLocaleString(undefined, { minimumFractionDigits: 2 });
};

// ─── Component ────────────────────────────────────────────────────────────────
function MenuCatalogContent() {
  // ── Catalog data ──
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [catalogTree, setCatalogTree] = useState<any[]>([]);
  const [categories, setCategories]           = useState<CatalogCategory[]>([]);
  const [isLoadingCatalog, setIsLoadingCatalog] = useState(true);
  const [availabilityOverrides, setAvailabilityOverrides] = useState<Record<string, boolean>>({});
  const [catalogError, setCatalogError]       = useState<string | null>(null);

  // ── Category form ──
  const [newCategoryName, setNewCategoryName] = useState('');

  // ── Item form ──
  const [isEditing, setIsEditing]             = useState(false);
  const [editingItemId, setEditingItemId]     = useState<string | null>(null);
  const [itemName, setItemName]               = useState('');
  const [itemDescription, setItemDescription] = useState('');
  const [itemPrice, setItemPrice]             = useState<string>('');
  const [selectedCategoryId, setSelectedCategoryId] = useState('');

  // ── Isolated category fetcher — GET /api/v1/categories ──────────────────────
  const fetchCategories = useCallback(async (): Promise<CatalogCategory[]> => {
    try {
      const res: any = await api.get('/api/v1/categories');
      // Safely unwrap: interceptor returns res.data.data for ApiResponse, or raw array
      const payload = Array.isArray(res)
        ? res
        : Array.isArray(res?.data)
          ? res.data
          : Array.isArray(res?.data?.data)
            ? res.data.data
            : [];

      const list: CatalogCategory[] = payload
        .filter(Boolean)
        .map((cat: any) => ({
          id:   cat.categoryId || cat.id   || cat.categoryName || cat.name || '',
          name: cat.categoryName || cat.name || cat.title       || '',
        }))
        .filter((c: CatalogCategory) => c.name);

      setCategories(list);
      return list;
    } catch (err) {
      console.error('Failed to fetch categories:', err);
      return [];
    }
  }, []);

  // ── Master loader (categories + items from /menu) ─────────────────────────
  const refreshCatalogData = useCallback(async () => {
    setIsLoadingCatalog(true);
    setCatalogError(null);
    try {
      const res: any = await api.get('/api/v1/menu');

      // Safely unwrap
      const menuData: any = res?.categoryAndItemResponse
        ? res
        : res?.data?.categoryAndItemResponse
          ? res.data
          : res?.data?.data?.categoryAndItemResponse
            ? res.data.data
            : res;

      const catItems: any[] = Array.isArray(menuData?.categoryAndItemResponse)
        ? menuData.categoryAndItemResponse
        : [];
      
      setCatalogTree(catItems);

      // Keep fetchedCategories for dropdowns
      const fetchedCategories: CatalogCategory[] = [];
      catItems.forEach((cat: any) => {
        const catName: string = cat.categoryName || cat.CategoryName || cat.name || '';
        if (catName && !fetchedCategories.find(c => c.name === catName)) {
          fetchedCategories.push({
            id: cat.categoryId || cat.id || catName,
            name: catName,
          });
        }
      });
      if (fetchedCategories.length > 0) {
        setCategories(prev => prev.length === 0 ? fetchedCategories : prev);
      }
    } catch (err: any) {
      setCatalogError(err?.message || 'Failed to load catalog');
    } finally {
      setIsLoadingCatalog(false);
    }
  }, []);

  // Run both on mount
  useEffect(() => {
    fetchCategories();
    refreshCatalogData();
  }, [fetchCategories, refreshCatalogData]);

  // ── Mutations ─────────────────────────────────────────────────────────────
  const createCategoryMutation = useMutation({
    mutationFn: (categoryName: string) =>
      managerService.createCategory({ categoryName }),
    onSuccess: async (_, categoryName) => {
      setNewCategoryName('');
      // Re-poll only categories (fast path) so dropdown updates immediately
      const updated = await fetchCategories();
      // Auto-select the newly created category
      const match = updated.find(c => c.name === categoryName);
      if (match) setSelectedCategoryId(match.id || match.name);
    },
    onError: (err: any) => {
      console.error('Failed to create category:', err);
    },
  });

  const createItemMutation = useMutation({
    mutationFn: (data: Partial<MenuItem>) => managerService.createMenuItem(data),
    onSuccess: async () => {
      resetItemForm();
      await refreshCatalogData();
    },
    onError: (err: any) => {
      console.error('Failed to create item:', err);
    },
  });

  const updateItemMutation = useMutation({
    mutationFn: (vars: { id: string; data: Partial<MenuItem> }) =>
      managerService.updateMenuItem(vars.id, vars.data),
    onSuccess: async () => {
      resetItemForm();
      await refreshCatalogData();
    },
    onError: (err: any) => {
      console.error('Failed to update item:', err);
    },
  });

  const deleteItemMutation = useMutation({
    mutationFn: (id: string) => managerService.deleteMenuItem(id),
    onSuccess: async () => {
      import('sonner').then(m => m.toast.success('Item deleted successfully'));
      await refreshCatalogData();
    },
    onError: (err: any) => {
      console.error('Failed to delete item:', err);
    },
  });

  const toggleAvailabilityMutation = useMutation({
    mutationFn: (id: string) => managerService.toggleMenuItemAvailability(id),
    onSuccess: async (res: any, variables) => {
      // res might be wrapped in ApiResponse, so we check for available in data or directly
      const data = res?.data?.data || res?.data || res;
      const newStatus = data?.available;
      
      if (newStatus !== undefined) {
        setAvailabilityOverrides(prev => ({ ...prev, [variables]: newStatus }));
        if (newStatus) {
           import('sonner').then(m => m.toast.success('Item is now active!'));
        } else {
           import('sonner').then(m => m.toast.info('Item is now inactive (grayed out)'));
        }
      } else {
        import('sonner').then(m => m.toast.success('Item availability toggled!'));
      }
      // No need to await refreshCatalogData here since we updated local state, 
      // but we can call it in background
      refreshCatalogData();
    },
    onError: (err: any) => {
      console.error('Failed to toggle availability:', err);
    },
  });

  // ── Handlers ──────────────────────────────────────────────────────────────
  const handleCreateCategory = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newCategoryName.trim()) return;
    createCategoryMutation.mutate(newCategoryName.trim());
  };

  const handleSaveItem = (e: React.FormEvent) => {
    e.preventDefault();
    if (!itemName.trim() || Number(itemPrice) <= 0 || !selectedCategoryId) {
      import('sonner').then(m => m.toast.error('Please fill in all fields with valid information'));
      return;
    }
    const payload: Partial<MenuItem> = {
      name: itemName,
      description: itemDescription,
      price: Number(itemPrice),
      categoryName: selectedCategoryId,
      available: true,
    };
    if (isEditing && editingItemId) {
      updateItemMutation.mutate({ id: editingItemId, data: payload });
    } else {
      createItemMutation.mutate(payload);
    }
  };

  const handleEditClick = (item: MenuItem) => {
    setIsEditing(true);
    setEditingItemId(item.id);
    setItemName(item.name);
    setItemDescription(item.description);
    setItemPrice(item.price.toString());
    setSelectedCategoryId(item.categoryName || item.categoryId || '');
  };

  const resetItemForm = () => {
    setIsEditing(false);
    setEditingItemId(null);
    setItemName('');
    setItemDescription('');
    setItemPrice('');
    setSelectedCategoryId('');
  };

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div className="flex-1 bg-manager-dashboard min-h-screen flex flex-col">
      {/* Header */}
      <header className="bg-white border-b border-stone-200/50 px-8 py-4 flex items-center justify-between sticky top-0 z-30 shadow-2xs">
        <div className="flex items-center gap-4">
          <Link href="/admin/dashboard" className="p-2 hover:bg-stone-50 rounded-xl text-stone-500 transition">
            <ArrowLeft className="h-4.5 w-4.5" />
          </Link>
          <div className="flex items-center gap-2">
            <Settings className="h-5.5 w-5.5 text-stone-800" />
            <h1 className="text-base font-black text-stone-900 uppercase tracking-tight">Menu Catalog Manager</h1>
          </div>
        </div>
      </header>

      {/* Content */}
      <main className="max-w-6xl mx-auto w-full p-6 grid grid-cols-1 lg:grid-cols-4 gap-8 items-start">

        {/* Creator Panel */}
        <section className="lg:col-span-1 space-y-6">

          {/* Category Form */}
          <div className="bg-white border border-stone-200/50 rounded-3xl p-6 shadow-2xs space-y-4">
            <h2 className="text-xs font-black uppercase text-stone-400 tracking-wider flex items-center gap-1.5">
              <FolderPlus className="h-4 w-4 text-stone-500" />
              Add Category
            </h2>
            <form onSubmit={handleCreateCategory} className="space-y-4 text-xs">
              <div className="space-y-1.5">
                <label className="font-extrabold text-stone-700">Category Name:</label>
                <input
                  type="text"
                  required
                  value={newCategoryName}
                  onChange={(e) => setNewCategoryName(e.target.value)}
                  placeholder="e.g. African Meals"
                  className="w-full rounded-xl border border-stone-200 bg-white px-3 py-2.5 text-stone-950 focus:border-brand-accent focus:outline-none"
                />
              </div>
              <button
                type="submit"
                disabled={createCategoryMutation.isPending}
                className="w-full bg-stone-900 hover:bg-stone-850 text-white font-bold py-2.5 rounded-xl flex items-center justify-center gap-1 shadow-xs transition cursor-pointer"
              >
                {createCategoryMutation.isPending ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  'Create Category'
                )}
              </button>
            </form>
          </div>

          {/* Item Form */}
          <div className="bg-white border border-stone-200/50 rounded-3xl p-6 shadow-2xs space-y-4">
            <h2 className="text-xs font-black uppercase text-stone-400 tracking-wider">
              {isEditing ? 'Edit Menu Item' : 'Add Menu Item'}
            </h2>
            <form onSubmit={handleSaveItem} className="space-y-4 text-xs">
              <div className="space-y-1.5">
                <label className="font-extrabold text-stone-700">Item Name:</label>
                <input
                  type="text"
                  required
                  value={itemName}
                  onChange={(e) => setItemName(e.target.value)}
                  placeholder="e.g. Jollof Rice"
                  className="w-full rounded-xl border border-stone-200 bg-white px-3 py-2.5 text-stone-950 focus:border-brand-accent focus:outline-none"
                />
              </div>

              <div className="space-y-1.5">
                <label className="font-extrabold text-stone-700">Description:</label>
                <textarea
                  required
                  value={itemDescription}
                  onChange={(e) => setItemDescription(e.target.value)}
                  placeholder="e.g. Spicy rice with chicken"
                  rows={2}
                  className="w-full rounded-xl border border-stone-200 bg-white px-3 py-2.5 text-stone-950 focus:border-brand-accent focus:outline-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <label className="font-extrabold text-stone-700">Price (₦):</label>
                  <input
                    type="number"
                    min={1}
                    required
                    value={itemPrice}
                    onChange={(e) => setItemPrice(e.target.value)}
                    className="w-full rounded-xl border border-stone-200 bg-white px-3 py-2.5 text-stone-950 focus:border-brand-accent focus:outline-none"
                  />
                </div>

                <div className="space-y-1.5">
                  <label className="font-extrabold text-stone-700">Category:</label>
                  <select
                    value={selectedCategoryId}
                    onChange={(e) => setSelectedCategoryId(e.target.value)}
                    required
                    className="w-full rounded-xl border border-stone-200 bg-white px-3 py-2.5 text-stone-950 focus:border-brand-accent focus:outline-none"
                  >
                    <option value="">Select Category</option>
                    {(categories || []).filter(Boolean).map((cat, idx) => (
                      <option
                        key={cat.id || cat.name || 'cat-' + idx}
                        value={cat.name || ''}
                      >
                        {cat.name || ('Category ' + (idx + 1))}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="flex gap-2.5 pt-2">
                {isEditing && (
                  <button
                    type="button"
                    onClick={resetItemForm}
                    className="flex-1 border border-stone-300 hover:bg-stone-50 text-stone-600 font-bold py-3 rounded-xl transition cursor-pointer"
                  >
                    Cancel
                  </button>
                )}
                <button
                  type="submit"
                  disabled={createItemMutation.isPending || updateItemMutation.isPending}
                  className="flex-1 bg-[#F15927] hover:bg-[#D94819] text-white font-bold py-3 rounded-xl flex items-center justify-center gap-1.5 shadow-md shadow-orange-500/20 transition cursor-pointer"
                >
                  {createItemMutation.isPending || updateItemMutation.isPending ? (
                    <Loader2 className="h-4.5 w-4.5 animate-spin" />
                  ) : (
                    <>
                      <PlusCircle className="h-4.5 w-4.5" />
                      {isEditing ? 'Update Item' : 'Create Item'}
                    </>
                  )}
                </button>
              </div>
            </form>
          </div>

        </section>

        {/* Catalog List */}
        <section className="lg:col-span-3 bg-white border border-stone-200/50 rounded-3xl p-6 shadow-2xs space-y-4">
          <h2 className="text-xs font-black uppercase text-stone-400 tracking-wider">Active Menu Catalog</h2>

          {isLoadingCatalog ? (
            <div className="py-12 flex justify-center">
              <Loader2 className="h-8 w-8 animate-spin text-[#F15927]" />
            </div>
          ) : catalogError ? (
            <p className="text-center text-xs text-red-500 font-bold py-12">Failed to load menu catalog.</p>
          ) : catalogTree.length === 0 ? (
            <p className="text-center text-xs text-stone-400 font-bold py-12">No categories found. Create one to begin!</p>
          ) : (
            <div className="space-y-6">
              {catalogTree.map((cat, index) => {
                const catName = (cat as any).CategoryName || (cat as any).categoryName || (cat as any).name;
                const catId = (cat as any).categoryId || (cat as any).id || catName;
                const items = (cat as any).itemResponse || (cat as any).items || [];
                
                return (
                  <div key={catId || 'cat-' + index} className="border border-stone-200 rounded-2xl overflow-hidden">
                    <div className="bg-stone-50 px-4 py-3 border-b border-stone-200 flex justify-between items-center">
                      <h3 className="font-bold text-stone-800 uppercase tracking-wide text-sm">{catName}</h3>
                      <button
                        onClick={async () => {
                          if (items.length > 0) {
                            alert('Cannot delete category: All items inside this category must be deleted or reassigned first.');
                            return;
                          }
                          if (window.confirm(`Are you sure you want to delete the empty category "${catName}"?`)) {
                            try {
                              // Find the real category UUID from the categories query
                              const realCategory = categories.find((c: any) => c.name === catName);
                              const realCatId = (realCategory as any)?.id || catId;
                              
                              await api.delete(`/api/v1/categories/${realCatId}`);
                              import('sonner').then(m => m.toast.success('Category deleted successfully'));
                              fetchCategories();
                              refreshCatalogData();
                            } catch (e: any) {
                              const errMessage = e.response?.data?.message || 'Failed to delete category';
                              import('sonner').then(m => m.toast.error(errMessage));
                            }
                          }
                        }}
                        className={`text-xs px-3 py-1 rounded-full font-bold transition-colors ${items.length > 0 ? 'bg-stone-200 text-stone-400 cursor-not-allowed' : 'bg-red-50 text-red-600 hover:bg-red-100 cursor-pointer'}`}
                        title={items.length > 0 ? 'Cannot delete category with items' : 'Delete Category'}
                      >
                        Delete Category
                      </button>
                    </div>
                    
                    <div className="p-4">
                      {items.length === 0 ? (
                        <p className="text-center text-sm text-stone-400 py-4 italic">No items in this category yet. Click Add Item to start.</p>
                      ) : (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                          {items.map((item: any, itemIdx: number) => {
                            const itemName = item.ItemName || item.itemName || item.name;
                            const itemDesc = item.ItemDescription || item.itemDescription || item.description;
                            const itemPrice = item.ItemPrice || item.itemPrice || item.price || item.amount;
                            const itemId = item.itemId || item.id || item.ItemName || item.itemName;
                            const isAvailable = availabilityOverrides[itemId] ?? (item.isAvailable ?? item.available ?? true);

                            const mappedItem: MenuItem = {
                              id: itemId,
                              name: itemName,
                              description: itemDesc,
                              price: Number(itemPrice ?? 0),
                              available: isAvailable,
                              categoryId: catId,
                              categoryName: catName
                            };

                            return (
                              <div
                                key={itemId || 'item-' + itemIdx}
                                className={`bg-white rounded-xl border shadow-sm p-4 hover:shadow-md transition-all flex flex-col sm:flex-row sm:items-start justify-between gap-4 ${isAvailable ? "border-stone-100" : "opacity-50 grayscale border-stone-200"}`}
                              >
                                <div className="space-y-1 flex-1">
                                  <h4 className="font-bold text-stone-900 text-sm md:text-base leading-tight">
                                    {itemName}
                                  </h4>
                                  <p className="text-stone-500 text-xs line-clamp-2 mt-0.5">
                                    {itemDesc}
                                  </p>
                                  <div className="flex items-center gap-2 mt-2 pt-1">
                                    <span className="font-bold text-orange-600 text-sm">
                                      {formatNaira(itemPrice)}
                                    </span>
                                  </div>
                                </div>

                                <div className="flex flex-row sm:flex-col items-center sm:items-end justify-between sm:justify-start w-full sm:w-auto mt-3 sm:mt-0 shrink-0 gap-2">
                                  {/* Availability Toggle Pill */}
                                  <button
                                    onClick={() => toggleAvailabilityMutation.mutate(itemId)}
                                    disabled={toggleAvailabilityMutation.isPending}
                                    className={
                                      isAvailable
                                        ? 'bg-emerald-50 text-emerald-700 border border-emerald-200 text-xs font-semibold px-2.5 py-1 rounded-full transition-colors cursor-pointer hover:bg-emerald-100'
                                        : 'bg-stone-100 text-stone-500 border border-stone-200 text-xs font-medium px-2.5 py-1 rounded-full transition-colors cursor-pointer hover:bg-stone-200'
                                    }
                                    title="Toggle Availability"
                                  >
                                    {isAvailable ? 'Available' : 'Unavailable'}
                                  </button>

                                  {/* Compact Action Cluster */}
                                  <div className="flex items-center gap-0.5 bg-stone-50 border border-stone-100 rounded-xl p-0.5">
                                    <button
                                      onClick={() => handleEditClick(mappedItem)}
                                      className="p-1.5 rounded-lg text-stone-400 hover:text-stone-900 hover:bg-white hover:shadow-sm transition-all cursor-pointer"
                                      title="Edit Item"
                                    >
                                      <Edit3 className="h-4 w-4" />
                                    </button>
                                    <button
                                      onClick={() => {
                                        if (window.confirm('Are you sure you want to delete this menu item?')) {
                                          deleteItemMutation.mutate(itemId);
                                        }
                                      }}
                                      disabled={deleteItemMutation.isPending}
                                      className="p-1.5 rounded-lg text-stone-400 hover:text-red-600 hover:bg-red-50 hover:shadow-sm transition-all cursor-pointer"
                                      title="Remove Item"
                                    >
                                      <Trash2 className="h-4 w-4" />
                                    </button>
                                  </div>
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </section>

      </main>
    </div>
  );
}

export default function MenuCatalogPage() {
  return (
    <ProtectedRoute allowedRoles={['MANAGER']}>
      <MenuCatalogContent />
    </ProtectedRoute>
  );
}
