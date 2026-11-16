import type { Metadata } from 'next';
import type { ReactNode } from 'react';

export const metadata: Metadata = { title: 'New password' };

export default function Layout({ children }: { children: ReactNode }) {
  return children;
}
