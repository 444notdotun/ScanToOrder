import Link from 'next/link';
import Ground from '@/components/plately/Ground';
import { Brand, Watermark } from '@/components/plately/Logo';
import { Handwriting } from '@/components/plately/ui';

const SUPPORT = 'support@platelyy.com';

// Terms and Privacy are not written yet. Until they are, each page says so plainly and says who to write to.
export default function LegalPage({ title }: { title: 'Terms' | 'Privacy' }) {
  return (
    <Ground>
      <Watermark className="-bottom-[70px] -right-24" />
      <header className="mx-auto flex w-full max-w-md items-center px-5 pt-5 md:max-w-xl">
        <Brand />
      </header>
      <main className="relative mx-auto flex w-full max-w-md flex-1 animate-rise flex-col justify-center gap-4 px-5 pb-10 pt-6 md:max-w-xl">
        <h1 className="font-heading text-[38px] font-normal leading-[1.1]">{title}</h1>
        <Handwriting>Coming soon</Handwriting>
        <p className="text-[17px] leading-snug text-soft">
          Questions?{' '}
          <a href={`mailto:${SUPPORT}`} className="font-bold text-cream underline underline-offset-4">
            Write to {SUPPORT}
          </a>
        </p>
        <Link href="/" className="mt-2 flex min-h-[52px] w-full max-w-xs items-center justify-center rounded-full border-[1.5px] border-cream/45 px-5 text-base font-bold text-cream hover:bg-cream/10">
          Back to Plately
        </Link>
      </main>
    </Ground>
  );
}
