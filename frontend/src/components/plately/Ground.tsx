import type { ReactNode } from 'react';

// The etched eating pattern: fork, glass, spoon, knife and wheat in fine cream lines, 13% opacity,
// on a 120px tile turned 12 degrees. One component, behind every Plately screen.
export function EtchedPattern() {
  return (
    <svg aria-hidden="true" className="pointer-events-none fixed inset-0 h-full w-full" focusable="false">
      <defs>
        <pattern id="plately-etched" width="120" height="120" patternUnits="userSpaceOnUse" patternTransform="rotate(-12)">
          <g fill="none" stroke="#F4ECD8" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round" opacity="0.13">
            <path d="M16 12v10a4 4 0 0 0 8 0V12M20 12v34" />
            <path d="M80 14h16c0 9-3 14-8 14s-8-5-8-14zM88 28v14M82 42h12" />
            <ellipse cx="24" cy="74" rx="5" ry="7" />
            <path d="M24 81v24" />
            <path d="M52 52c5 5 6 14 3 22h-3V52zM52 74v16" />
            <path d="M78 106c8-8 14-18 18-30" />
            <path d="M84 99c-5-1-7-4-7-8c4 0 7 3 7 8zM89 91c1-5 4-7 8-7c0 4-3 7-8 7zM91 84c-5-1-7-4-7-8c4 0 7 3 7 8z" />
            <path d="M50 20c3-3 7-3 10 0M50 26c3-3 7-3 10 0" />
          </g>
        </pattern>
      </defs>
      <rect width="100%" height="100%" fill="url(#plately-etched)" />
    </svg>
  );
}

// Every Plately screen sits on this: bottle green, the pattern, and room for content above it.
export default function Ground({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div className={`plately relative flex min-h-dvh w-full flex-col bg-bottle font-body text-cream ${className}`}>
      <EtchedPattern />
      <div className="relative flex min-h-dvh w-full flex-1 flex-col">{children}</div>
    </div>
  );
}
