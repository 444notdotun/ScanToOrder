'use client';

import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useCallback, useEffect, useRef, useState } from 'react';
import Ground from '@/components/plately/Ground';
import { Brand, Watermark } from '@/components/plately/Logo';
import { Button, Sheet } from '@/components/plately/ui';
import { eventGuestApi, guestApi } from '@/lib/guest';
import { tableLinkFrom } from '@/lib/scanLink';

type Phase = 'idle' | 'starting' | 'scanning' | 'found';

const CORNERS = [
  'left-0 top-0 border-l-4 border-t-4 rounded-tl-2xl',
  'right-0 top-0 border-r-4 border-t-4 rounded-tr-2xl',
  'bottom-0 left-0 border-b-4 border-l-4 rounded-bl-2xl',
  'bottom-0 right-0 border-b-4 border-r-4 rounded-br-2xl',
];

// Four corners round whatever is being looked at: gold, or red when the code is not ours.
function Corners({ tone = 'gold', pulse = false }: { tone?: 'gold' | 'red'; pulse?: boolean }) {
  const colour = tone === 'gold' ? 'border-mustard' : 'border-tomato';
  return (
    <div aria-hidden="true" className={`pointer-events-none absolute inset-0 ${pulse ? 'animate-pulse-ring rounded-2xl' : ''} ${tone === 'red' ? 'animate-shake' : ''}`}>
      {CORNERS.map((position) => (
        <span key={position} className={`absolute h-9 w-9 ${position} ${colour}`} />
      ))}
    </div>
  );
}

// The opening page: where someone lands before they have a table.
function Scan() {
  const router = useRouter();
  const params = useSearchParams();
  const ended = params.get('ended');

  const videoRef = useRef<HTMLVideoElement>(null);
  const scannerRef = useRef<{ stop: () => void; destroy: () => void } | null>(null);
  const lastWrong = useRef<{ text: string; at: number } | null>(null);
  const slowTimer = useRef<number | null>(null);
  const resetWrongTimer = useRef<number | null>(null);

  const [phase, setPhase] = useState<Phase>('idle');
  const [wrong, setWrong] = useState(false);
  const [slow, setSlow] = useState(false);
  const [noCamera, setNoCamera] = useState(false);
  const [where, setWhere] = useState<string | null>(null);

  const clearTimers = () => {
    if (slowTimer.current) window.clearTimeout(slowTimer.current);
    if (resetWrongTimer.current) window.clearTimeout(resetWrongTimer.current);
  };

  const shutCamera = useCallback(() => {
    scannerRef.current?.destroy();
    scannerRef.current = null;
  }, []);

  useEffect(
    () => () => {
      scannerRef.current?.destroy();
      scannerRef.current = null;
      if (slowTimer.current) window.clearTimeout(slowTimer.current);
      if (resetWrongTimer.current) window.clearTimeout(resetWrongTimer.current);
    },
    [],
  );

  const armSlowHint = () => {
    if (slowTimer.current) window.clearTimeout(slowTimer.current);
    setSlow(false);
    slowTimer.current = window.setTimeout(() => setSlow(true), 6000);
  };

  const found = useCallback(
    async (link: NonNullable<ReturnType<typeof tableLinkFrom>>) => {
      clearTimers();
      shutCamera();
      setSlow(false);
      setWrong(false);
      setPhase('found');
      navigator.vibrate?.(40);
      // The business and table, so the guest can see it is the right one. If it does not come quickly, go on without it.
      const name = async () => {
        try {
          if (link.eventCode) return (await eventGuestApi.preview(link.slug, link.eventCode, link.n)).eventName;
          return (await guestApi.seatMap(link.slug, link.n)).businessName;
        } catch {
          return undefined;
        }
      };
      const business = await Promise.race([name(), new Promise<undefined>((resolve) => window.setTimeout(() => resolve(undefined), 1500))]);
      setWhere(business ? `${business} · Table ${link.n}` : `Table ${link.n}`);
      window.setTimeout(() => router.push(link.path), 1300);
    },
    [router, shutCamera],
  );

  const onRead = useCallback(
    (text: string) => {
      const link = tableLinkFrom(text, window.location.hostname);
      if (link) {
        void found(link);
        return;
      }
      // The same wrong code is read many times a second: answer it once, then again only after a pause.
      const now = Date.now();
      if (lastWrong.current && lastWrong.current.text === text && now - lastWrong.current.at < 3000) return;
      lastWrong.current = { text, at: now };
      setWrong(true);
      armSlowHint();
      if (resetWrongTimer.current) window.clearTimeout(resetWrongTimer.current);
      resetWrongTimer.current = window.setTimeout(() => setWrong(false), 3000);
    },
    [found],
  );

  const start = async () => {
    setNoCamera(false);
    setWrong(false);
    setPhase('starting');
    try {
      const { default: QrScanner } = await import('qr-scanner');
      if (!navigator.mediaDevices || !(await QrScanner.hasCamera()) || !videoRef.current) throw new Error('no camera');
      const scanner = new QrScanner(videoRef.current, (result) => onRead(result.data), {
        preferredCamera: 'environment',
        returnDetailedScanResult: true,
        highlightScanRegion: false,
        highlightCodeOutline: false,
        maxScansPerSecond: 12,
      });
      scannerRef.current = scanner;
      await scanner.start();
      setPhase('scanning');
      armSlowHint();
    } catch {
      shutCamera();
      setPhase('idle');
      setNoCamera(true);
    }
  };

  const stop = () => {
    clearTimers();
    shutCamera();
    setSlow(false);
    setWrong(false);
    setPhase('idle');
  };

  const looking = phase === 'starting' || phase === 'scanning' || phase === 'found';

  return (
    <Ground>
      <Watermark className="-bottom-[70px] -right-24" />
      <main className="relative mx-auto flex w-full max-w-md md:max-w-xl flex-1 flex-col items-center gap-5 px-7 pb-7 pt-10">
        <div className="animate-pop">
          <Brand />
        </div>
        <h1 className="mt-3 animate-rise text-center font-heading text-[40px] font-normal leading-[1.08]">Pull up a chair.</h1>
        <p className="animate-rise text-center text-[17px] leading-[1.45] text-soft [animation-delay:90ms]">
          Find the little card on your table and point your camera at it. That&apos;s all it takes.
        </p>

        <div className="relative mt-2 w-full max-w-[300px]">
          {/* The table card, until the camera is asked for */}
          {!looking ? (
            <div className="relative mx-auto w-[236px] p-3">
              <Corners pulse />
              <div className="flex animate-drop flex-col items-center gap-2.5 rounded-xl bg-paper px-5 pb-4 pt-5 text-bottle shadow-[0_18px_36px_rgba(0,0,0,0.35)]">
                <div className="text-xs font-bold uppercase tracking-[0.14em] text-quiet">Table 12</div>
                <svg aria-hidden="true" width="132" height="132" viewBox="0 0 66 66" fill="currentColor" focusable="false">
                  <path d="M3 3h18v18H3zM7 7v10h10V7zM45 3h18v18H45zM49 7v10h10V7zM3 45h18v18H3zM7 49v10h10V49z" fillRule="evenodd" />
                  <path d="M10 10h4v4h-4zM52 10h4v4h-4zM10 52h4v4h-4z" />
                  <path d="M27 3h4v4h-4zM35 7h4v8h-4zM27 13h4v8h-4zM27 27h8v4h-8zM39 25h4v8h-4zM3 27h8v4H3zM15 31h4v8h-4zM23 35h4v4h-4zM47 27h8v4h-8zM59 31h4v8h-4zM27 43h4v8h-4zM35 39h8v4h-8zM47 43h4v4h-4zM55 47h8v4h-8zM39 51h4v12h-4zM27 59h8v4h-8zM47 55h4v8h-4zM59 59h4v4h-4zM31 19h4v4h-4zM7 35h4v4H7z" />
                </svg>
                <div className="text-center font-hand text-2xl font-bold leading-[1.05] text-hand">Scan me. I&apos;ll take your order.</div>
              </div>
            </div>
          ) : null}

          {/* The camera, only after the tap */}
          <div className={looking ? 'relative mx-auto aspect-square w-full max-w-[300px] p-3' : 'sr-only'} aria-hidden={!looking}>
            <div className="relative h-full w-full overflow-hidden rounded-xl bg-black/40">
              <video ref={videoRef} playsInline muted className="h-full w-full object-cover" aria-label="Your camera" />
              {phase === 'scanning' ? <span aria-hidden="true" className="absolute left-3 right-3 h-[3px] animate-sweep rounded-full bg-mustard shadow-[0_0_14px_3px_rgba(240,185,74,0.7)]" /> : null}
              {phase === 'found' ? (
                <>
                  <span aria-hidden="true" className="absolute inset-0 animate-flash bg-white" />
                  <div className="absolute inset-0 flex items-center justify-center bg-bottle/55">
                    <svg aria-hidden="true" width="84" height="84" viewBox="0 0 24 24" fill="none" stroke="#F0B94A" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" focusable="false">
                      <path d="M5 12.5l4.5 4.5L19 7.5" strokeDasharray="30" className="animate-tick" />
                    </svg>
                  </div>
                </>
              ) : null}
            </div>
            {looking ? <Corners tone={wrong ? 'red' : 'gold'} pulse={phase === 'scanning' && !wrong} /> : null}
          </div>
        </div>

        {ended && !looking ? (
          <p role="status" className="animate-fade text-center text-[15px] leading-snug text-mustard">
            {ended === 'visit'
              ? 'Your table was closed by the staff. Scan again whenever you sit back down.'
              : 'Your seat has been freed. Scan again whenever you sit back down.'}
          </p>
        ) : null}

        <div role="status" aria-live="polite" className="min-h-[3.4rem] text-center">
          {phase === 'found' && where ? (
            <p className="animate-rise font-heading text-[26px] leading-tight text-cream">{where}</p>
          ) : wrong ? (
            <p className="animate-fade text-[16px] font-bold leading-snug text-[#FFB4A6]">That code isn&apos;t one of our tables. Look for the card with the Plately logo.</p>
          ) : slow && phase === 'scanning' ? (
            <p className="animate-fade text-[16px] leading-snug text-mustard">Hold steady, a little closer.</p>
          ) : phase === 'starting' ? (
            <p className="text-[16px] text-soft">Opening your camera…</p>
          ) : null}
        </div>

        <div className="flex-1" />
        {phase === 'scanning' ? (
          <Button tone="ghost" onClick={stop}>
            Stop scanning
          </Button>
        ) : (
          <Button tone="primary" big onClick={() => void start()} disabled={phase !== 'idle'}>
            Scan your table
          </Button>
        )}
        <p className="text-center text-sm text-soft">No app to download. No account to make.</p>
        <p className="text-center text-sm text-soft">
          <Link href="/staff/login" className="underline underline-offset-4">
            Staff sign in
          </Link>
          {' · '}
          <Link href="/signup" className="underline underline-offset-4">
            Get Plately for your place
          </Link>
        </p>
      </main>

      <Sheet open={noCamera} onClose={() => setNoCamera(false)} label="No camera">
        <h2 className="font-heading text-2xl font-normal text-bottle">No camera here</h2>
        <p className="mt-2 text-[15px] leading-snug text-quiet">
          Open your phone&apos;s camera app and point it at the card on your table. It will open the menu.
        </p>
        <div className="mt-5 flex flex-col gap-2.5">
          <Button
            tone="bottle"
            onClick={() => {
              setNoCamera(false);
              void start();
            }}
          >
            Try again
          </Button>
        </div>
      </Sheet>
    </Ground>
  );
}

export default function ScanPage() {
  return (
    <Suspense fallback={null}>
      <Scan />
    </Suspense>
  );
}
