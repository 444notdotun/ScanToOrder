'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/store/authStore';
import { api } from '@/lib/api';
import { ChefHat, ShieldAlert, Loader2, ArrowLeft } from 'lucide-react';
import Link from 'next/link';

export default function StaffLoginPage() {
  const router = useRouter();
  const login = useAuthStore((state) => state.login);

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg('');
    setIsLoading(true);
    try {
      const res = await api.post('/api/v1/auth/login', { username, password });
      if (res.status === 200 || res.data?.status === 'SUCCESS') {
        const data = res.data?.data || res.data;
        if (data && data.username) {
          login({
            username: data.username,
            role: data.role
          });
        }
        router.push('/admin/dashboard');
      }
    } catch (err: any) {
      setErrorMsg(err.response?.data?.message || 'Invalid username or password');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex flex-col justify-between py-12 px-6 sm:px-12 bg-[#F8F6F2]">
      {/* Top Navigation */}
      <header className="max-w-md mx-auto w-full flex items-center justify-start mb-6">
        <Link 
          href="/" 
          className="flex items-center gap-1.5 text-xs font-bold text-stone-500 hover:text-stone-700 transition"
        >
          <ArrowLeft className="h-3.5 w-3.5" />
          Back to Diner scan
        </Link>
      </header>

      {/* Main Login Box */}
      <main className="max-w-md mx-auto w-full bg-white rounded-3xl p-8 border border-stone-200/50 shadow-sm space-y-6">
        <div className="text-center space-y-2">
          <div className="inline-flex p-3 bg-brand-light text-brand-deep rounded-2xl">
            <ChefHat className="h-6 w-6" />
          </div>
          <h1 className="text-xl font-black text-stone-900 uppercase tracking-tight">Staff Service Desk</h1>
          <p className="text-xs text-stone-500">Sign in to access protected operations dashboards</p>
        </div>

        {errorMsg && (
          <div className="bg-red-50 border border-red-100 rounded-2xl p-3 flex items-start gap-2.5 text-xs text-red-600 font-semibold leading-normal">
            <ShieldAlert className="h-4 w-4 shrink-0 text-red-500 mt-0.5" />
            <span>{errorMsg}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4 text-xs">
          <div className="space-y-1.5">
            <label className="font-extrabold text-stone-700">Username:</label>
            <input
              type="text"
              required
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="e.g. admin"
              className="w-full rounded-xl border border-stone-200 bg-white px-3.5 py-3 text-stone-950 focus:border-brand-accent focus:ring-1 focus:ring-brand-accent focus:outline-none transition text-sm"
            />
          </div>

          <div className="space-y-1.5">
            <label className="font-extrabold text-stone-700">Password:</label>
            <input
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              className="w-full rounded-xl border border-stone-200 bg-white px-3.5 py-3 text-stone-950 focus:border-brand-accent focus:ring-1 focus:ring-brand-accent focus:outline-none transition text-sm"
            />
          </div>

          <button
            type="submit"
            disabled={isLoading}
            className="w-full bg-brand-deep hover:bg-brand-accent text-white font-bold py-3.5 rounded-2xl transition duration-150 shadow-sm flex items-center justify-center gap-2 text-sm mt-6 cursor-pointer"
          >
            {isLoading ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              'Sign In'
            )}
          </button>
        </form>
      </main>

      {/* Footer */}
      <footer className="text-center text-xs text-stone-400 pt-8 mt-6">
        &copy; 2026 Scan to Order Restaurant Systems.
      </footer>
    </div>
  );
}
