import type { Metadata } from 'next';
import type { ReactNode } from 'react';
import { restaurantName, withName } from '@/lib/siteTitles';

export async function generateMetadata({ params }: { params: Promise<{ slug: string; n: string }> }): Promise<Metadata> {
  const { slug, n } = await params;
  return { title: withName('Menu', await restaurantName(slug, n)) };
}

export default function Layout({ children }: { children: ReactNode }) {
  return children;
}
