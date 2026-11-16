import type { ReactNode } from 'react';
import SeatedShell from '@/components/guest/SeatedShell';

// An event guest is seated like any guest: the same live seat, the same stream, the same way out.
export default function EventSeatedLayout({ children }: { children: ReactNode }) {
  return <SeatedShell>{children}</SeatedShell>;
}
