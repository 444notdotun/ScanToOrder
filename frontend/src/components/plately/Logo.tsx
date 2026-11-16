// The mark is a letter P made of a handle and a plate, with an inner ring in mustard on green or tomato on paper.

export function Mark({ size = 34, ring = '#F0B94A', body = '#F4ECD8', spin = false }: { size?: number; ring?: string; body?: string; spin?: boolean }) {
  return (
    <svg aria-hidden="true" width={size} height={size} viewBox="0 0 120 120" fill="none" focusable="false">
      <rect x="20" y="12" width="18" height="96" rx="9" fill={body} />
      <circle cx="70" cy="48" r="31" stroke={body} strokeWidth="10" />
      <circle
        cx="70"
        cy="48"
        r="16"
        stroke={ring}
        strokeWidth="7"
        strokeLinecap="round"
        strokeDasharray={spin ? '62 39' : undefined}
        className={spin ? 'animate-spin-slow' : undefined}
        style={spin ? { transformOrigin: '70px 48px', transformBox: 'view-box' } : undefined}
      />
    </svg>
  );
}

export function Wordmark({ size = 30, className = '' }: { size?: number; className?: string }) {
  return (
    <span className={`font-wordmark font-semibold leading-none tracking-[-0.03em] ${className}`} style={{ fontSize: size }}>
      plately
    </span>
  );
}

// The mark with the wordmark: in every header.
export function Brand({ size = 30, ring = '#F0B94A', body = '#F4ECD8', className = '' }: { size?: number; ring?: string; body?: string; className?: string }) {
  return (
    <span className={`inline-flex items-center gap-2 ${className}`} style={{ color: body }}>
      <Mark size={Math.round(size * 1.1)} ring={ring} body={body} />
      <Wordmark size={size} />
    </span>
  );
}

// A faint mark behind the opening page, the paid page and the receipt.
export function Watermark({ className = '', color = '#F4ECD8', opacity = 0.08 }: { className?: string; color?: string; opacity?: number }) {
  return (
    <svg
      aria-hidden="true"
      focusable="false"
      width="380"
      height="380"
      viewBox="0 0 120 120"
      fill="none"
      className={`pointer-events-none absolute ${className}`}
      style={{ opacity }}
    >
      <rect x="20" y="12" width="18" height="96" rx="9" fill={color} />
      <circle cx="70" cy="48" r="31" stroke={color} strokeWidth="10" />
      <circle cx="70" cy="48" r="16" stroke={color} strokeWidth="7" />
    </svg>
  );
}

// The loader: the mark large, the inner ring spinning, and a line in the waiter's hand.
export function Loader({ message = 'One moment…' }: { message?: string }) {
  return (
    <div role="status" aria-live="polite" className="relative flex flex-1 animate-fade flex-col items-center justify-center gap-5 p-7">
      <div className="animate-pop">
        <Mark size={144} spin />
      </div>
      <Wordmark size={34} />
      <p className="text-center font-hand text-[27px] font-bold leading-[1.15] text-mustard">{message}</p>
    </div>
  );
}
