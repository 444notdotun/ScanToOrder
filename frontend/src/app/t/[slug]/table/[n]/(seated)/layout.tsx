import type { ReactNode } from 'react';
import SeatedShell from '@/components/guest/SeatedShell';

export default function SeatedLayout({ children }: { children: ReactNode }) {
  return <SeatedShell>{children}</SeatedShell>;
}
