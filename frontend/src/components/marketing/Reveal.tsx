'use client';

import { useEffect, useRef, type ReactNode } from 'react';

// A section that rises in as it arrives on screen. Without script, or with reduced motion, it is simply there.
export default function Reveal({ children, className = '', delay = 0 }: { children: ReactNode; className?: string; delay?: number }) {
  const box = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = box.current;
    if (!el || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const arrived = () => el.getBoundingClientRect().top < window.innerHeight * 0.88;
    // Already on screen when the page opens: leave it be.
    if (arrived()) return;
    el.setAttribute('data-armed', '');
    if (delay) el.style.transitionDelay = `${delay}ms`;
    const check = () => {
      if (!arrived()) return;
      el.classList.add('mk-in');
      window.removeEventListener('scroll', check);
      window.removeEventListener('resize', check);
    };
    window.addEventListener('scroll', check, { passive: true });
    window.addEventListener('resize', check);
    return () => {
      window.removeEventListener('scroll', check);
      window.removeEventListener('resize', check);
      el.removeAttribute('data-armed');
      el.style.transitionDelay = '';
    };
  }, [delay]);

  return (
    <div ref={box} className={className}>
      {children}
    </div>
  );
}
