import type { Metadata } from 'next';
import type { ReactNode } from 'react';
import { eventName, withName } from '@/lib/siteTitles';

export async function generateMetadata({ params }: { params: Promise<{ slug: string; eventCode: string; n: string }> }): Promise<Metadata> {
  const { slug, eventCode, n } = await params;
  return { title: withName('Plates', await eventName(slug, eventCode, n)) };
}

export default function Layout({ children }: { children: ReactNode }) {
  return children;
}
