import Link from 'next/link';
import Ground from '@/components/plately/Ground';
import { Brand, Watermark } from '@/components/plately/Logo';
import { Handwriting } from '@/components/plately/ui';
import type { Metadata } from 'next';

// Wrong address: say so kindly and point to the way back.
export const metadata: Metadata = { title: 'Not found' };

export default function NotFound() {
  return (
    <Ground>
      <Watermark className="-bottom-[70px] -right-24" />
      <header className="mx-auto flex w-full max-w-md items-center px-5 pt-5 md:max-w-xl">
        <Brand />
      </header>
      <main className="relative mx-auto flex w-full max-w-md flex-1 animate-rise flex-col items-center justify-center gap-4 px-7 pb-8 text-center md:max-w-xl">
        <Handwriting>This page isn&apos;t on the menu.</Handwriting>
        <h1 className="font-heading text-[38px] font-normal leading-[1.1]">We can&apos;t find that.</h1>
        <p className="text-[17px] leading-snug text-soft">The link may be old or mistyped. If you are at a table, scan the card on it again.</p>
        <Link href="/scan" className="mt-2 flex min-h-[52px] w-full max-w-xs items-center justify-center rounded-full bg-tomato px-5 text-base font-bold text-white">
          Back to the start
        </Link>
      </main>
    </Ground>
  );
}
