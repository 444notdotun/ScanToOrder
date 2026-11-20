'use client';

import { useEffect, useRef } from 'react';
import { EtchedPattern } from '@/components/plately/Ground';

const PLATE = (
  <svg viewBox="0 0 200 200" fill="none" stroke="#F4ECD8" strokeWidth="1.6" aria-hidden="true" focusable="false" className="h-full w-full">
    <circle cx="100" cy="100" r="96" />
    <circle cx="100" cy="100" r="78" />
    <circle cx="100" cy="100" r="52" strokeDasharray="3 7" />
    <path d="M100 4v18M100 178v18M4 100h18M178 100h18" />
  </svg>
);

const FORK = (
  <svg viewBox="0 0 24 64" fill="none" stroke="#F4ECD8" strokeWidth="1.5" strokeLinecap="round" aria-hidden="true" focusable="false" className="h-full w-full">
    <path d="M5 3v14a5 5 0 0 0 14 0V3M12 3v58M9 3v10M15 3v10" />
  </svg>
);

const SPOON = (
  <svg viewBox="0 0 24 64" fill="none" stroke="#F4ECD8" strokeWidth="1.5" strokeLinecap="round" aria-hidden="true" focusable="false" className="h-full w-full">
    <ellipse cx="12" cy="13" rx="7" ry="10" />
    <path d="M12 23v38" />
  </svg>
);

const KNIFE = (
  <svg viewBox="0 0 24 64" fill="none" stroke="#F4ECD8" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false" className="h-full w-full">
    <path d="M9 3c7 3 9 15 6 32H9V3zM12 35v26" />
  </svg>
);

const CUTLERY = [
  { icon: FORK, left: '8%', top: '78%', delay: '0s', size: 46 },
  { icon: SPOON, left: '78%', top: '84%', delay: '8s', size: 40 },
  { icon: KNIFE, left: '36%', top: '90%', delay: '15s', size: 48 },
  { icon: FORK, left: '90%', top: '62%', delay: '21s', size: 36 },
];

// The background behind the marketing page. Only this page has it. The pattern drifts, plates turn, cutlery floats up
// now and then, a gold glint passes like a scan, and the layers shift a little as the page scrolls. Faint on purpose:
// the text on top always stays easy to read. It all stands still when motion is reduced, and stops when the tab is hidden.
export default function Backdrop() {
  const root = useRef<HTMLDivElement>(null);
  const slow = useRef<HTMLDivElement>(null);
  const mid = useRef<HTMLDivElement>(null);
  const fast = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const calm = window.matchMedia('(prefers-reduced-motion: reduce)');
    let frame = 0;
    const place = () => {
      frame = 0;
      if (calm.matches) return;
      const y = window.scrollY;
      if (slow.current) slow.current.style.transform = `translate3d(0, ${-y * 0.04}px, 0)`;
      if (mid.current) mid.current.style.transform = `translate3d(0, ${-y * 0.09}px, 0)`;
      if (fast.current) fast.current.style.transform = `translate3d(0, ${-y * 0.16}px, 0)`;
    };
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(place);
    };
    const onVisible = () => root.current?.classList.toggle('mk-paused', document.hidden);
    window.addEventListener('scroll', onScroll, { passive: true });
    document.addEventListener('visibilitychange', onVisible);
    onVisible();
    place();
    return () => {
      window.removeEventListener('scroll', onScroll);
      document.removeEventListener('visibilitychange', onVisible);
      if (frame) cancelAnimationFrame(frame);
    };
  }, []);

  return (
    <div ref={root} aria-hidden="true" className="mk-layer">
      <div ref={slow} className="mk-parallax">
        <div className="mk-drift absolute -top-[120px] left-0 h-[calc(100%+120px)] w-[calc(100%+120px)]">
          {/* About 7% in all (13% pattern x 0.55), so text over it keeps its contrast. */}
          <EtchedPattern className="absolute inset-0 h-full w-full" opacity={0.55} />
        </div>
      </div>
      <div ref={mid} className="mk-parallax">
        <div className="mk-turn absolute -left-[180px] top-[8%] h-[520px] w-[520px] opacity-[0.07]">{PLATE}</div>
        <div className="mk-turn-back absolute -right-[240px] top-[48%] h-[680px] w-[680px] opacity-[0.06]">{PLATE}</div>
        <div className="mk-turn absolute left-[18%] top-[96%] h-[420px] w-[420px] opacity-[0.06]">{PLATE}</div>
      </div>
      <div ref={fast} className="mk-parallax">
        {CUTLERY.map((piece, index) => (
          <div key={index} className="mk-float absolute" style={{ left: piece.left, top: piece.top, width: piece.size / 2, height: piece.size, animationDelay: piece.delay }}>
            {piece.icon}
          </div>
        ))}
      </div>
      <div className="mk-glint absolute inset-y-0 left-0 w-[34%] bg-[linear-gradient(90deg,transparent,rgba(240,185,74,0.07),rgba(240,185,74,0.16),rgba(240,185,74,0.07),transparent)]" />
    </div>
  );
}
