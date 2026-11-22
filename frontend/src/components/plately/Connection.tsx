'use client';

import { createContext, useContext } from 'react';

// Whether the live connection is down: screens switch their buttons off while it is.
export const ConnectionContext = createContext<{ lost: boolean }>({ lost: false });

export function useConnection() {
  return useContext(ConnectionContext);
}

export function ConnectionBanner({ lost, caughtUp }: { lost: boolean; caughtUp: boolean }) {
  if (lost) {
    return (
      <div role="status" className="sticky top-[var(--head-h,0px)] z-30 animate-fade bg-mustard px-4 py-2.5 text-center text-sm font-bold text-bottle">
        Nothing can be changed until the connection is back. We are still trying.
      </div>
    );
  }
  if (caughtUp) {
    return (
      <div role="status" className="sticky top-[var(--head-h,0px)] z-30 animate-fade bg-good px-4 py-2.5 text-center text-sm font-bold text-white">
        Connection is back.
      </div>
    );
  }
  return null;
}
