import Link from 'next/link';

export default function BackLink({ href, children = 'Back to your table' }: { href: string; children?: string }) {
  return (
    <Link href={href} className="inline-flex min-h-[44px] w-fit items-center gap-1.5 text-[15px] font-bold text-cream">
      <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M15 18l-6-6 6-6" />
      </svg>
      {children}
    </Link>
  );
}
