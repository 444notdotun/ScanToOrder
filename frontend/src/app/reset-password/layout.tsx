import type { Metadata } from 'next';
import type { ReactNode } from 'react';

// The address of this page holds a one-time token while it loads: it is kept out of search and out of the Referer header.
export const metadata: Metadata = { title: 'Choose a new password', robots: { index: false, follow: false }, referrer: 'no-referrer' };

export default function Layout({ children }: { children: ReactNode }) {
  return children;
}
