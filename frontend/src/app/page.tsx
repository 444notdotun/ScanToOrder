import type { Metadata } from 'next';
import Link from 'next/link';
import type { ReactNode } from 'react';
import Backdrop from '@/components/marketing/Backdrop';
import Reveal from '@/components/marketing/Reveal';
import TopBar from '@/components/marketing/TopBar';
import { Brand, Watermark } from '@/components/plately/Logo';

export const metadata: Metadata = {
  title: 'Plately: scan, sit, order, eat',
  description:
    'Guests scan the code on their table, pick a seat, then order and pay from their phone. Paid orders go straight to the kitchen. For restaurants and event planners.',
};

const BUTTON = 'flex min-h-[52px] items-center justify-center rounded-full px-6 text-base font-bold leading-tight text-center';
const PRIMARY = `${BUTTON} bg-tomato text-white hover:bg-[#a23420]`;
const GHOST = `${BUTTON} border-[1.5px] border-cream/45 text-cream hover:bg-cream/10`;

function Section({ id, eyebrow, title, intro, children }: { id: string; eyebrow: string; title: string; intro?: string; children: ReactNode }) {
  return (
    <section id={id} aria-labelledby={`${id}-title`} className="relative mx-auto w-full max-w-6xl scroll-mt-6 px-5 py-14 md:py-20">
      <Reveal>
        <p className="text-xs font-bold uppercase tracking-[0.16em] text-mustard">{eyebrow}</p>
        <h2 id={`${id}-title`} className="mt-2 max-w-3xl font-heading text-[32px] font-normal leading-[1.1] md:text-[44px]">
          {title}
        </h2>
        {intro ? <p className="mt-3 max-w-2xl text-[17px] leading-[1.5] text-soft">{intro}</p> : null}
      </Reveal>
      <div className="mt-8">{children}</div>
    </section>
  );
}

function Tile({ number, title, children, delay = 0 }: { number?: number; title: string; children: ReactNode; delay?: number }) {
  return (
    <Reveal delay={delay} className="h-full">
      <article className="h-full rounded-[20px] bg-paper p-5 text-ink shadow-[0_10px_24px_rgba(0,0,0,0.28)]">
        {number ? (
          <span aria-hidden="true" className="flex h-9 w-9 items-center justify-center rounded-full bg-bottle font-heading text-lg text-mustard">
            {number}
          </span>
        ) : null}
        <h3 className={`font-heading text-[22px] font-normal leading-tight text-bottle ${number ? 'mt-3' : ''}`}>{title}</h3>
        <p className="mt-2 text-[16px] leading-[1.5] text-quiet">{children}</p>
      </article>
    </Reveal>
  );
}

function Role({ name, children }: { name: string; children: ReactNode }) {
  return (
    <li className="rounded-2xl border border-cream/20 bg-bottle/60 p-4">
      <h3 className="text-base font-bold text-mustard">{name}</h3>
      <p className="mt-1 text-[16px] leading-[1.5] text-cream">{children}</p>
    </li>
  );
}

export default function MarketingPage() {
  return (
    <div className="plately relative isolate flex min-h-dvh w-full flex-col overflow-clip bg-bottle font-body text-cream">
      <Backdrop />
      <div className="relative z-10 flex flex-1 flex-col pt-[env(safe-area-inset-top)]">
        <TopBar />

        <main>
          <section aria-labelledby="hero-title" className="relative mx-auto w-full max-w-6xl px-5 pb-12 pt-14 md:pb-20 md:pt-24">
            <Watermark className="-right-28 top-6 hidden md:block" opacity={0.07} />
            <div className="relative max-w-3xl animate-rise">
              <h1 id="hero-title" className="font-heading text-[52px] font-normal leading-[1.02] md:text-[84px]">
                Scan. Order. Eat.
              </h1>
              <p className="mt-4 font-hand text-[30px] font-bold leading-tight text-mustard md:text-[36px]">No app to download. No account to make.</p>
              <p className="mt-5 max-w-2xl text-[18px] leading-[1.55] text-soft md:text-[20px]">
                Guests scan the code on their table, pick a seat, then order and pay from their phone. Paid orders go straight to the kitchen and the food arrives at
                the right seat. At events, each guest&apos;s code is their seat and their plates.
              </p>
              <div className="mt-8 flex flex-col gap-3 sm:flex-row">
                <Link href="/signup" className={PRIMARY}>
                  Bring Plately to your business
                </Link>
                <Link href="/scan" className={GHOST}>
                  Here to eat? Scan your table
                </Link>
              </div>
            </div>
          </section>

          <Section id="how-it-works" eyebrow="For your guests" title="Sit down. That's most of the work done.">
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <Tile number={1} title="Scan the table">
                One code on the table. The camera opens the page, with nothing to install.
              </Tile>
              <Tile number={2} title="Pick your seat" delay={80}>
                Each guest takes a seat. Save seats for friends for ten minutes and give them a six-character code to join.
              </Tile>
              <Tile number={3} title="Order and pay" delay={160}>
                Each seat pays for itself. The order goes to the kitchen once it is paid.
              </Tile>
              <Tile number={4} title="The food finds you" delay={240}>
                Every order carries its table and seat, so it reaches the right person. The receipt stays on their phone.
              </Tile>
            </div>
          </Section>

          <Section id="restaurants" eyebrow="For restaurants" title="Run your place on it.">
            <div className="grid gap-4 md:grid-cols-3">
              <Tile title="Menu, tables and team">
                One short form to sign up. Print a code for each table, and go live once Plately approves you.
              </Tile>
              <Tile title="Paid on every order" delay={80}>
                Each payment is split automatically, and your share goes straight to your bank.
              </Tile>
              <Tile title="See how the night went" delay={160}>
                Sales, best plates, busiest hours and how fast calls were answered. Today, this week, this month.
              </Tile>
            </div>
            <Reveal className="mt-6">
              <ul className="grid gap-3 md:grid-cols-3">
                <Role name="The chef">Sees an order only once it is paid, starts it, marks it ready, and marks a dish sold out.</Role>
                <Role name="The waiter">Carries ready plates to the right seat, answers calls, and frees seats or closes a table.</Role>
                <Role name="The manager">Sets the menu, tables and team, opens and closes for orders, and settles refunds.</Role>
              </ul>
            </Reveal>
          </Section>

          <Section id="events" eyebrow="For event planners" title="Feed every guest, and know that you did.">
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              <Tile title="A code is a seat and its plates">
                Each guest&apos;s code is their seat and every plate they&apos;re meant to have. You set plates per guest, and can raise it for anyone.
              </Tile>
              <Tile title="Seat them your way" delay={80}>
                Assigned seats, or sit where you like.
              </Tile>
              <Tile title="Plates are your own combinations" delay={160}>
                Put together the plates your guests will choose from.
              </Tile>
              <Tile title="Has everyone eaten?" delay={240}>
                One view answers it.
              </Tile>
              <Tile title="One fee per event" delay={320}>
                Set by how many guests you expect. Simple per-order and per-event pricing.
              </Tile>
              <Tile title="Codes to your guests" delay={400}>
                Sending codes to guests from Plately: coming soon.
              </Tile>
            </div>
          </Section>

          <section aria-labelledby="closing-title" className="relative mx-auto w-full max-w-6xl px-5 pb-20 pt-10 md:pb-28">
            <Reveal>
              <div className="rounded-[28px] border border-cream/20 bg-bottle/70 px-6 py-12 text-center md:py-16">
                <Brand className="justify-center" />
                <h2 id="closing-title" className="mt-4 font-heading text-[38px] font-normal leading-[1.08] md:text-[56px]">
                  Pull up a chair.
                </h2>
                <p className="mx-auto mt-3 max-w-xl text-[18px] leading-[1.5] text-soft">Tell us about your place and we&apos;ll get the tables ready.</p>
                <div className="mx-auto mt-7 max-w-sm">
                  <Link href="/signup" className={PRIMARY}>
                    Bring Plately to your business
                  </Link>
                </div>
              </div>
            </Reveal>
          </section>
        </main>

        <footer className="mx-auto mt-auto flex w-full max-w-6xl flex-wrap items-center justify-between gap-3 px-5 pb-8 text-sm text-soft">
          <span>© Plately</span>
          <Link href="/staff/login" className="underline underline-offset-4">
            Staff sign in
          </Link>
        </footer>
      </div>
    </div>
  );
}
