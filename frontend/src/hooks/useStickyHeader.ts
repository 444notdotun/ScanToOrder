'use client';

import { useEffect, useState, type RefObject } from 'react';

// For a header pinned to the top of the page. It says whether the page has scrolled (so the header can take its solid look), and it
// publishes the header's height as --head-h, so anything that scrolls into view can stop below it.
export function useStickyHeader(bar: RefObject<HTMLElement | null>): boolean {
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const check = () => setScrolled(window.scrollY > 4);
    check();
    window.addEventListener('scroll', check, { passive: true });
    return () => window.removeEventListener('scroll', check);
  }, []);

  useEffect(() => {
    const element = bar.current;
    if (!element) return;
    const root = document.documentElement;
    const publish = () => root.style.setProperty('--head-h', `${element.offsetHeight}px`);
    publish();
    const watch = new ResizeObserver(publish);
    watch.observe(element);
    return () => {
      watch.disconnect();
      root.style.removeProperty('--head-h');
    };
  }, [bar]);

  return scrolled;
}
