import type { Metadata } from 'next';
import Link from 'next/link';
import type { ReactNode } from 'react';
import Backdrop from '@/components/marketing/Backdrop';
import HeroPhone from '@/components/marketing/HeroPhone';
import Reveal from '@/components/marketing/Reveal';
import TopBar from '@/components/marketing/TopBar';
import { Brand, Watermark } from '@/components/plately/Logo';

export const metadata: Metadata = {
  title: 'Plately: scan, sit, order, eat',
  description:
    'Guests scan the code on their table, pick a seat, then order and pay from their phone. Paid orders go straight to the kitchen. For restaurants and event planners.',
};

const X_URL = 'https://x.com/platelyy';
const PRIMARY = 'flex min-h-[52px] items-center justify-center rounded-full bg-tomato px-6 text-center text-base font-bold leading-tight text-white hover:bg-[#A23420]';
const QUIET_LINK = 'flex min-h-[52px] items-center justify-center rounded-full border-[1.5px] border-cream/45 px-6 text-center text-base font-bold leading-tight text-cream hover:bg-cream/10';
const SECTION = 'relative mx-auto w-full max-w-6xl px-5 py-12 md:py-24';

function Heading({ id, eyebrow, title, children }: { id: string; eyebrow: string; title: string; children?: ReactNode }) {
  return (
    <div>
      <p className="text-xs font-bold uppercase tracking-[0.16em] text-mustard">{eyebrow}</p>
      <h2 id={id} className="mt-2 font-heading text-[32px] font-normal leading-[1.1] md:text-[44px]">
        {title}
      </h2>
      {children ? <p className="mt-4 max-w-md text-[17px] leading-[1.5] text-soft">{children}</p> : null}
    </div>
  );
}

// The four steps as a paper receipt, with a torn bottom edge.
const STEPS = [
  ['Scan the table', 'One code on the table. The camera opens the page, with nothing to install.'],
  ['Pick your seat', 'Each guest takes a seat. Save seats for friends for ten minutes and give them a six-character code to join.'],
  ['Order and pay', 'Each seat pays for itself. The order goes to the kitchen once it is paid.'],
  ['The food finds you', 'Every order carries its table and seat, so it reaches the right person. The receipt stays on their phone.'],
];

function Receipt() {
  return (
    <div className="mx-auto w-full max-w-md">
      <div className="bg-paper px-6 pb-6 pt-6 text-ink shadow-[0_18px_36px_rgba(0,0,0,0.3)]">
        <div className="flex justify-between border-b border-dashed border-rule2 pb-3 text-[12px] font-bold uppercase tracking-[0.14em] text-quiet">
          <span>Table 7</span>
          <span>How it goes</span>
        </div>
        <ol className="mt-4 flex flex-col gap-4">
          {STEPS.map(([title, line], index) => (
            <li key={title} className="flex gap-4">
              <span aria-hidden="true" className="w-6 flex-none font-heading text-[26px] leading-none text-hand">
                {index + 1}
              </span>
              <div>
                <h3 className="font-heading text-[20px] font-normal leading-tight text-bottle">{title}</h3>
                <p className="mt-1 text-[15px] leading-[1.5] text-quiet">{line}</p>
              </div>
            </li>
          ))}
        </ol>
        <div className="mt-5 flex justify-between border-t border-dashed border-rule2 pt-3 text-[12px] font-bold uppercase tracking-[0.14em] text-quiet">
          <span>Kitchen has it</span>
          <span>Sit tight</span>
        </div>
      </div>
      <svg aria-hidden="true" focusable="false" width="100%" height="10" className="block drop-shadow-[0_8px_8px_rgba(0,0,0,0.2)]">
        <defs>
          <pattern id="tear" width="20" height="10" patternUnits="userSpaceOnUse">
            <polygon points="0,0 20,0 10,10" fill="#FBF6EA" />
          </pattern>
        </defs>
        <rect width="100%" height="10" fill="url(#tear)" />
      </svg>
    </div>
  );
}

// A ruled ledger: three rows, a rule between each.
const LEDGER = [
  ['Menu, tables and team', 'One short form to sign up. Print a code for each table, and go live once Plately approves you.'],
  ['Paid on every order', 'Each payment is split automatically, and your share goes straight to your bank.'],
  ['See how the night went', 'Sales, best plates, busiest hours and how fast calls were answered. Today, this week, this month.'],
];

function Ledger() {
  return (
    <dl className="border-y border-rule2">
      {LEDGER.map(([name, line]) => (
        <div key={name} className="grid gap-1 border-b border-rule2 py-5 last:border-b-0 md:grid-cols-[200px_1fr] md:gap-6">
          <dt className="font-heading text-[22px] font-normal leading-tight">{name}</dt>
          <dd className="text-[16px] leading-[1.5] text-soft">{line}</dd>
        </div>
      ))}
    </dl>
  );
}

function Role({ name, children }: { name: string; children: ReactNode }) {
  return (
    <li className="border-t border-cream/20 py-4 first:border-t-0 first:pt-0">
      <h4 className="text-base font-bold text-mustard">{name}</h4>
      <p className="mt-1 text-[16px] leading-[1.5] text-cream">{children}</p>
    </li>
  );
}

function EventCard() {
  return (
    <div className="mx-auto w-full max-w-sm -rotate-1 rounded-[20px] bg-paper p-6 text-ink shadow-[0_18px_36px_rgba(0,0,0,0.3)]">
      <div className="flex justify-between text-[12px] font-bold uppercase tracking-[0.14em] text-quiet">
        <span>Event guest card</span>
        <span className="rounded-full border border-hand px-2 text-hand">Sample</span>
      </div>
      <h3 className="mt-3 font-heading text-[40px] font-normal leading-none text-bottle">
        Seat 12 <span className="text-[18px] text-hand">· 2 plates</span>
      </h3>
      <p className="mt-1 text-[15px] text-quiet">Sample guest · Table 3</p>
      <ul className="mt-4 divide-y divide-dashed divide-rule2 border-y border-dashed border-rule2 text-[15px]">
        <li className="flex justify-between py-2"><span>Plate 1 · Jollof &amp; small chops</span><span className="font-bold">left</span></li>
        <li className="flex justify-between py-2"><span>Plate 2 · Jollof &amp; small chops</span><span className="font-bold">left</span></li>
      </ul>
      <div className="mt-4 flex items-baseline justify-between">
        <span className="text-[12px] font-bold uppercase tracking-[0.14em] text-quiet">Your code</span>
        <span className="text-[22px] font-bold tracking-[0.2em] text-ink">K7M4QX</span>
      </div>
    </div>
  );
}

const EVENT_POINTS = [
  ['A code is a seat and its plates', "Each guest's code is their seat and every plate they're meant to have. You set plates per guest, and can raise it for anyone."],
  ['Seat them your way', 'Assigned seats, or sit where you like. Put together the plates your guests will choose from.'],
  ['Has everyone eaten?', 'One view answers it.'],
  ['One fee per event', 'Set by how many guests you expect. Simple per-order and per-event pricing.'],
];

function Answer({ question, children }: { question: string; children: ReactNode }) {
  return (
    <div>
      <h3 className="text-[13px] font-bold uppercase tracking-[0.14em] text-bottle">{question}</h3>
      <div className="mt-3 text-[16px] leading-[1.5] text-ink">{children}</div>
    </div>
  );
}

const CHIP = 'rounded-full border-[1.5px] border-rule2 bg-paper px-3 py-1 text-[14px] font-bold text-bottle';
const XLINK = 'font-bold underline underline-offset-4';

export default function MarketingPage() {
  return (
    <div className="plately relative isolate flex min-h-dvh w-full flex-col overflow-clip bg-bottle font-body text-cream">
      <Backdrop />
      <div className="relative z-10 flex flex-1 flex-col pt-[env(safe-area-inset-top)]">
        <TopBar />

        <main>
          <section aria-labelledby="hero-title" className="relative mx-auto w-full max-w-6xl px-5 pb-12 pt-12 md:pb-24 md:pt-16">
            <Watermark className="-right-28 top-6 hidden opacity-50 lg:block" opacity={0.05} />
            <div className="relative grid items-center gap-12 lg:grid-cols-2">
              <div className="animate-rise">
                <h1 id="hero-title" className="font-heading text-[52px] font-normal leading-[1.02] md:text-[84px]">
                  Scan. Order. Eat.
                </h1>
                <p className="mt-4 font-hand text-[30px] font-bold leading-tight text-mustard md:text-[36px]">No app to download. No account to make.</p>
                <p className="mt-6 max-w-xl text-[17px] leading-[1.55] text-soft">
                  Guests scan the code on their table, pick a seat, then order and pay from their phone. Paid orders go straight to the kitchen and the food arrives at
                  the right seat. At events, each guest&apos;s code is their seat and their plates.
                </p>
                <div className="mt-8 flex flex-col gap-3 sm:flex-row">
                  <Link href="/signup" className={PRIMARY}>
                    Get Plately for your place
                  </Link>
                  <Link href="/scan" className={QUIET_LINK}>
                    I&apos;m at a table: scan to order
                  </Link>
                </div>
                <p className="mt-4 max-w-xl text-[15px] leading-snug text-soft">Restaurants and event planners: tell us about your place and we&apos;ll get the tables ready.</p>
              </div>
              <HeroPhone />
            </div>
          </section>

          <section id="how-it-works" aria-labelledby="guests-title" className={`${SECTION} scroll-mt-6`}>
            <div className="grid items-center gap-10 lg:grid-cols-2">
              <Reveal>
                <Heading id="guests-title" eyebrow="For your guests" title="Sit down. That's most of the work done.">
                  Four steps, and the first three are the guest&apos;s.
                </Heading>
              </Reveal>
              <Reveal delay={80}>
                <Receipt />
              </Reveal>
            </div>
          </section>

          <section id="restaurants" aria-labelledby="restaurants-title" className={`${SECTION} scroll-mt-6`}>
            <Reveal>
              <Heading id="restaurants-title" eyebrow="For restaurants" title="Run your place on it.">
                Menu, tables, team and money, in one place.
              </Heading>
            </Reveal>
            <div className="mt-10 grid gap-10 lg:grid-cols-[minmax(0,1fr)_300px] lg:gap-12">
              <Reveal>
                <Ledger />
              </Reveal>
              <Reveal delay={80}>
                <h3 className="mb-4 font-heading text-[20px] font-normal leading-tight">Everyone sees only what they need.</h3>
                <ul aria-label="Who does what">
                  <Role name="The chef">Sees an order only once it is paid, starts it, marks it ready, and marks a dish sold out.</Role>
                  <Role name="The waiter">Carries ready plates to the right seat, answers calls, and frees seats or closes a table.</Role>
                  <Role name="The manager">Sets the menu, tables and team, opens and closes for orders, and settles refunds.</Role>
                </ul>
              </Reveal>
            </div>
          </section>

          <section id="events" aria-labelledby="events-title" className={`${SECTION} scroll-mt-6`}>
            <div className="grid items-center gap-10 lg:grid-cols-2 lg:gap-16">
              <Reveal className="order-2 lg:order-1">
                <EventCard />
              </Reveal>
              <Reveal delay={80} className="order-1 lg:order-2">
                <Heading id="events-title" eyebrow="For event planners" title="Feed every guest, and know that you did." />
                <ul className="mt-8 flex flex-col gap-5">
                  {EVENT_POINTS.map(([title, line]) => (
                    <li key={title} className="flex gap-3">
                      <span aria-hidden="true" className="mt-2.5 h-2 w-2 flex-none rounded-full bg-mustard" />
                      <div>
                        <h3 className="font-heading text-[22px] font-normal leading-tight">{title}</h3>
                        <p className="mt-1 text-[16px] leading-[1.5] text-soft">{line}</p>
                      </div>
                    </li>
                  ))}
                </ul>
                <p className="mt-6 text-[15px] text-soft">
                  <span className="font-bold text-mustard">Coming soon:</span> sending codes to guests from Plately.
                </p>
              </Reveal>
            </div>
          </section>

          <section aria-labelledby="answers-title" className="bg-cream text-ink">
            <div className="mx-auto w-full max-w-6xl px-5 py-12 md:py-24">
              <p className="text-xs font-bold uppercase tracking-[0.16em] text-quiet">Straight answers</p>
              <h2 id="answers-title" className="mt-2 max-w-3xl font-heading text-[32px] font-normal leading-[1.1] text-bottle md:text-[44px]">
                What it costs, how you get paid, and who to call.
              </h2>
              <div className="mt-10 grid gap-8 sm:grid-cols-2 lg:grid-cols-4">
                <Answer question="Guests can pay with">
                  <ul className="flex flex-wrap gap-2">
                    {['Card', 'Bank transfer', 'Opay', 'Moniepoint', 'USSD'].map((way) => (
                      <li key={way} className={CHIP}>
                        {way}
                      </li>
                    ))}
                  </ul>
                </Answer>
                <Answer question="What it costs">Pricing: coming soon.</Answer>
                <Answer question="Talk to us">
                  <p>
                    On X:{' '}
                    <a href={X_URL} target="_blank" rel="noopener noreferrer" className={`${XLINK} text-bottle`}>
                      @platelyy
                    </a>
                  </p>
                  <p className="mt-1 text-quiet">WhatsApp and email: coming soon.</p>
                </Answer>
                <Answer question="Built in Lagos">
                  <p className="text-quiet">Our story: coming soon.</p>
                </Answer>
              </div>
            </div>
          </section>

          <section aria-labelledby="closing-title" className="relative mx-auto w-full max-w-6xl px-5 py-12 md:py-24">
            <Reveal>
              <div className="rounded-[28px] border border-cream/20 bg-bottle/70 px-6 py-12 text-center md:py-16">
                <Brand className="justify-center" />
                <h2 id="closing-title" className="mt-4 font-heading text-[38px] font-normal leading-[1.08] md:text-[56px]">
                  Pull up a chair.
                </h2>
                <p className="mx-auto mt-3 max-w-xl text-[17px] leading-[1.5] text-soft">Tell us about your place and we&apos;ll get the tables ready.</p>
                <div className="mx-auto mt-8 max-w-sm">
                  <Link href="/signup" className={PRIMARY}>
                    Get Plately for your place
                  </Link>
                </div>
                <p className="mt-5 text-[15px] text-soft">
                  Prefer to talk? Find us on X:{' '}
                  <a href={X_URL} target="_blank" rel="noopener noreferrer" className={`${XLINK} text-cream`}>
                    @platelyy
                  </a>
                </p>
              </div>
            </Reveal>
          </section>
        </main>

        <footer className="mt-auto border-t border-cream/20 bg-[#10281F]">
          <div className="mx-auto grid w-full max-w-6xl gap-10 px-5 py-12 md:grid-cols-[1.4fr_1fr_1fr_1fr]">
            <div>
              <Brand />
              <p className="mt-3 max-w-xs text-[15px] leading-snug text-soft">Scan the table, pick a seat, order and eat. Built in Lagos.</p>
            </div>
            <nav aria-label="Product">
              <h2 className="text-[13px] font-bold uppercase tracking-[0.14em] text-mustard">Product</h2>
              <ul className="mt-3 flex flex-col text-[15px] text-soft">
                {[['#how-it-works', 'How it works'], ['#restaurants', 'For restaurants'], ['#events', 'For events']].map(([href, label]) => (
                  <li key={href}>
                    <a href={href} className="flex min-h-[44px] items-center underline-offset-4 hover:text-cream hover:underline">
                      {label}
                    </a>
                  </li>
                ))}
              </ul>
            </nav>
            <div>
              <h2 className="text-[13px] font-bold uppercase tracking-[0.14em] text-mustard">Talk to us</h2>
              <ul className="mt-3 flex flex-col text-[15px] text-soft">
                <li className="flex min-h-[44px] items-center">
                  <span>
                    X:{' '}
                    <a href={X_URL} target="_blank" rel="noopener noreferrer" className="underline underline-offset-4 hover:text-cream">
                      @platelyy
                    </a>
                  </span>
                </li>
                <li className="flex min-h-[44px] items-center">WhatsApp: coming soon</li>
                <li className="flex min-h-[44px] items-center">Email: coming soon</li>
                <li>
                  <Link href="/staff/login" className="flex min-h-[44px] items-center underline underline-offset-4 hover:text-cream">
                    Staff sign in
                  </Link>
                </li>
              </ul>
            </div>
            <div>
              <h2 className="text-[13px] font-bold uppercase tracking-[0.14em] text-mustard">Good to know</h2>
              <ul className="mt-3 flex flex-col text-[15px] text-soft">
                <li className="flex min-h-[44px] items-center">Terms: coming soon</li>
                <li className="flex min-h-[44px] items-center">Privacy: coming soon</li>
              </ul>
            </div>
          </div>
          <p className="mx-auto w-full max-w-6xl px-5 pb-8 text-sm text-soft">© Plately</p>
        </footer>
      </div>
    </div>
  );
}
