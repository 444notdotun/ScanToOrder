'use client';

import type { ReactNode } from 'react';
import ManagerNav from '@/components/staff/ManagerNav';
import { ManagerBar } from '@/components/staff/RoleBars';
import StaffShell from '@/components/staff/StaffShell';

export default function ManagerLayout({ children }: { children: ReactNode }) {
  return (
    <StaffShell allow={['MANAGER']} workspace="RESTAURANT" nav={<ManagerNav />}>
      {children}
      <ManagerBar />
    </StaffShell>
  );
}
