'use client';

import type { ReactNode } from 'react';
import ManagerNav from '@/components/staff/ManagerNav';
import StaffShell from '@/components/staff/StaffShell';

export default function ManagerLayout({ children }: { children: ReactNode }) {
  return (
    <StaffShell allow={['MANAGER']}>
      <ManagerNav />
      {children}
    </StaffShell>
  );
}
